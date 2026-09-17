package com.example.feature.code.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.security.LockState
import com.example.core.security.SessionSecurityManager
import com.example.core.settings.AppSettingsManager
import com.example.feature.code.CodeLanguage
import com.example.feature.code.formatters.JsonFormatter
import com.example.feature.code.formatters.XmlFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class CodeEditorViewModel(
    private val repository: VaultRepository,
    private val sessionManager: SessionSecurityManager,
    private val appSettingsManager: AppSettingsManager? = null
) : ViewModel() {

    companion object {
        const val LARGE_FILE_THRESHOLD_BYTES = 1_500_000L // 1.5MB
    }

    val tabManager = CodeMultiTabManager()

    // Active Editor State
    private val _textFieldValue = MutableStateFlow(TextFieldValue())
    val textFieldValue: StateFlow<TextFieldValue> = _textFieldValue.asStateFlow()

    private val _saveStatus = MutableStateFlow(SaveStatus.SAVED)
    val saveStatus: StateFlow<SaveStatus> = _saveStatus.asStateFlow()

    private val _isReadOnly = MutableStateFlow(false)
    val isReadOnly: StateFlow<Boolean> = _isReadOnly.asStateFlow()

    private val _isLargeFile = MutableStateFlow(false)
    val isLargeFile: StateFlow<Boolean> = _isLargeFile.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Preferences
    private val _fontSize = MutableStateFlow(EditorFontSize.MEDIUM)
    val fontSize: StateFlow<EditorFontSize> = _fontSize.asStateFlow()

    private val _indentOption = MutableStateFlow(IndentOption.FOUR_SPACES)
    val indentOption: StateFlow<IndentOption> = _indentOption.asStateFlow()

    private val _isWordWrap = MutableStateFlow(false)
    val isWordWrap: StateFlow<Boolean> = _isWordWrap.asStateFlow()

    // Mode: Edit vs Preview
    private val _isPreviewMode = MutableStateFlow(false)
    val isPreviewMode: StateFlow<Boolean> = _isPreviewMode.asStateFlow()

    // Search & Replace
    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen: StateFlow<Boolean> = _isSearchOpen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _replaceText = MutableStateFlow("")
    val replaceText: StateFlow<String> = _replaceText.asStateFlow()

    private val _matchCase = MutableStateFlow(false)
    val matchCase: StateFlow<Boolean> = _matchCase.asStateFlow()

    private val _wholeWord = MutableStateFlow(false)
    val wholeWord: StateFlow<Boolean> = _wholeWord.asStateFlow()

    private val _useRegex = MutableStateFlow(false)
    val useRegex: StateFlow<Boolean> = _useRegex.asStateFlow()

    private val _regexError = MutableStateFlow<String?>(null)
    val regexError: StateFlow<String?> = _regexError.asStateFlow()

    private val _searchMatches = MutableStateFlow<List<SearchResultMatch>>(emptyList())
    val searchMatches: StateFlow<List<SearchResultMatch>> = _searchMatches.asStateFlow()

    private val _currentMatchIndex = MutableStateFlow(-1)
    val currentMatchIndex: StateFlow<Int> = _currentMatchIndex.asStateFlow()

    // Document Statistics
    private val _stats = MutableStateFlow(DocumentStats())
    val stats: StateFlow<DocumentStats> = _stats.asStateFlow()

    // Undo / Redo history
    private val editHistory = CodeEditHistory(maxCapacity = 50)
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var currentItem: VaultItemEntity? = null
    private var searchDebounceJob: Job? = null
    private var statsDebounceJob: Job? = null

    init {
        // Observe vault lock state: purge in-memory text immediately on lock
        viewModelScope.launch {
            sessionManager.lockState.collect { lockState ->
                if (lockState !is LockState.Unlocked) {
                    purgeSensitiveState()
                }
            }
        }

        if (appSettingsManager != null) {
            val sz = appSettingsManager.editorFontSize.value
            _fontSize.value = when {
                sz <= 11 -> EditorFontSize.SMALL
                sz <= 14 -> EditorFontSize.MEDIUM
                sz <= 17 -> EditorFontSize.LARGE
                else -> EditorFontSize.EXTRA_LARGE
            }
            _isWordWrap.value = appSettingsManager.editorWordWrap.value
            val tabSz = appSettingsManager.editorTabSize.value
            val spaces = appSettingsManager.editorUseSpaces.value
            _indentOption.value = if (!spaces) IndentOption.TAB else if (tabSz == 2) IndentOption.TWO_SPACES else IndentOption.FOUR_SPACES

            viewModelScope.launch {
                appSettingsManager.editorFontSize.collect { sizeSp ->
                    _fontSize.value = when {
                        sizeSp <= 11 -> EditorFontSize.SMALL
                        sizeSp <= 14 -> EditorFontSize.MEDIUM
                        sizeSp <= 17 -> EditorFontSize.LARGE
                        else -> EditorFontSize.EXTRA_LARGE
                    }
                }
            }
            viewModelScope.launch {
                appSettingsManager.editorWordWrap.collect { wrap ->
                    _isWordWrap.value = wrap
                }
            }
            viewModelScope.launch {
                appSettingsManager.editorTabSize.collect { tSz ->
                    val sp = appSettingsManager.editorUseSpaces.value
                    _indentOption.value = if (!sp) IndentOption.TAB else if (tSz == 2) IndentOption.TWO_SPACES else IndentOption.FOUR_SPACES
                }
            }
        }
    }

    /**
     * Initializes editor by loading item from encrypted vault.
     */
    fun loadFile(itemId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val item = repository.getItemById(itemId)
                if (item == null) {
                    _errorMessage.value = "Item not found in encrypted vault"
                    _isLoading.value = false
                    return@launch
                }
                currentItem = item
                repository.recordFileAccess(item)

                val isLarge = item.sizeBytes > LARGE_FILE_THRESHOLD_BYTES
                _isLargeFile.value = isLarge
                if (isLarge) {
                    _isReadOnly.value = true
                    _saveStatus.value = SaveStatus.READ_ONLY
                }

                // Decrypt content into memory
                val decryptResult = repository.decryptItemBytes(item)
                if (decryptResult.isFailure) {
                    _errorMessage.value = "Failed decrypting file: ${decryptResult.exceptionOrNull()?.message}"
                    _isLoading.value = false
                    return@launch
                }

                val bytes = decryptResult.getOrNull() ?: ByteArray(0)
                val text = String(bytes, Charsets.UTF_8)

                // Setup tabs
                tabManager.openOrSwitchTab(
                    item = item,
                    content = text,
                    isReadOnly = _isReadOnly.value,
                    isLargeFile = isLarge
                )

                val language = CodeLanguage.detect(item.title)
                _isWordWrap.value = language.defaultWordWrap

                _textFieldValue.value = TextFieldValue(text = text, selection = TextRange.Zero)
                editHistory.initialize(text)
                updateUndoRedoStatus()
                updateStats(text)
                _saveStatus.value = if (isLarge) SaveStatus.READ_ONLY else SaveStatus.SAVED
                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Error loading file: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    /**
     * Handles user text input and updates history.
     */
    fun onTextChanged(newValue: TextFieldValue) {
        if (_isReadOnly.value) return

        val oldText = _textFieldValue.value.text
        val newText = newValue.text

        _textFieldValue.value = newValue
        tabManager.updateCurrentTabContent(newText)

        if (oldText != newText) {
            editHistory.push(newText, newValue.selection.start, newValue.selection.end)
            updateUndoRedoStatus()
            _saveStatus.value = SaveStatus.UNSAVED

            // Re-run search if active
            if (_isSearchOpen.value && _searchQuery.value.isNotEmpty()) {
                scheduleSearch(_searchQuery.value)
            }
            scheduleStatsUpdate(newText)
        }
    }

    fun onSelectionChanged(range: TextRange) {
        val current = _textFieldValue.value
        if (current.selection != range) {
            _textFieldValue.value = current.copy(selection = range)
            val selectedText = if (!range.collapsed && range.start < range.end && range.end <= current.text.length) {
                current.text.substring(range.start, range.end)
            } else ""

            _stats.value = _stats.value.copy(
                selectedChars = selectedText.length,
                selectedLines = if (selectedText.isEmpty()) 0 else selectedText.lines().size
            )
        }
    }

    // --- Undo / Redo ---

    fun undo() {
        if (_isReadOnly.value) return
        val snapshot = editHistory.undo() ?: return
        _textFieldValue.value = TextFieldValue(
            text = snapshot.text,
            selection = TextRange(snapshot.selectionStart, snapshot.selectionEnd)
        )
        tabManager.updateCurrentTabContent(snapshot.text)
        updateUndoRedoStatus()
        checkSavedStatus(snapshot.text)
        scheduleStatsUpdate(snapshot.text)
    }

    fun redo() {
        if (_isReadOnly.value) return
        val snapshot = editHistory.redo() ?: return
        _textFieldValue.value = TextFieldValue(
            text = snapshot.text,
            selection = TextRange(snapshot.selectionStart, snapshot.selectionEnd)
        )
        tabManager.updateCurrentTabContent(snapshot.text)
        updateUndoRedoStatus()
        checkSavedStatus(snapshot.text)
        scheduleStatsUpdate(snapshot.text)
    }

    private fun updateUndoRedoStatus() {
        _canUndo.value = editHistory.canUndo
        _canRedo.value = editHistory.canRedo
    }

    private fun checkSavedStatus(currentText: String) {
        val saved = tabManager.currentTab?.savedContent ?: ""
        _saveStatus.value = if (currentText == saved) SaveStatus.SAVED else SaveStatus.UNSAVED
    }

    // --- Auto-Indent & Brackets & Comments ---

    /**
     * Auto-indents after open braces/parentheses when Enter is pressed.
     */
    fun insertNewLineWithAutoIndent() {
        if (_isReadOnly.value) return
        val current = _textFieldValue.value
        val text = current.text
        val cursor = current.selection.start.coerceIn(0, text.length)

        val beforeCursor = text.substring(0, cursor)
        val afterCursor = text.substring(cursor)

        val currentLine = beforeCursor.substringAfterLast('\n')
        val leadingSpaces = currentLine.takeWhile { it == ' ' || it == '\t' }

        val opensBlock = currentLine.trimEnd().endsWith("{") ||
                currentLine.trimEnd().endsWith(":") ||
                currentLine.trimEnd().endsWith("(") ||
                currentLine.trimEnd().endsWith("[")

        val indentUnit = _indentOption.value.text
        val newIndent = if (opensBlock) leadingSpaces + indentUnit else leadingSpaces

        val closesBlock = afterCursor.trimStart().startsWith("}") ||
                afterCursor.trimStart().startsWith(")") ||
                afterCursor.trimStart().startsWith("]")

        val insertedText = if (opensBlock && closesBlock) {
            "\n$newIndent\n$leadingSpaces"
        } else {
            "\n$newIndent"
        }

        val newText = beforeCursor + insertedText + afterCursor
        val newCursor = beforeCursor.length + 1 + newIndent.length

        onTextChanged(TextFieldValue(text = newText, selection = TextRange(newCursor)))
    }

    /**
     * Inserts symbol or wraps selected text with matching pair.
     */
    fun insertSymbolOrPair(openChar: String, closeChar: String? = null) {
        if (_isReadOnly.value) return
        val current = _textFieldValue.value
        val text = current.text
        val selection = current.selection

        val (newText, newSelection) = if (!selection.collapsed && closeChar != null) {
            val selected = text.substring(selection.start, selection.end)
            val replaced = "$openChar$selected$closeChar"
            val updated = text.replaceRange(selection.start, selection.end, replaced)
            updated to TextRange(selection.start + openChar.length, selection.end + openChar.length)
        } else if (closeChar != null) {
            val pair = "$openChar$closeChar"
            val updated = text.replaceRange(selection.start, selection.end, pair)
            updated to TextRange(selection.start + openChar.length)
        } else {
            val updated = text.replaceRange(selection.start, selection.end, openChar)
            updated to TextRange(selection.start + openChar.length)
        }

        onTextChanged(TextFieldValue(text = newText, selection = newSelection))
    }

    /**
     * Toggles line comment for selected lines or current line.
     */
    fun toggleComment() {
        if (_isReadOnly.value) return
        val language = tabManager.currentTab?.language ?: CodeLanguage.PLAIN_TEXT
        val prefix = language.commentPrefix ?: return // Language has no comment support

        val current = _textFieldValue.value
        val text = current.text
        val selection = current.selection

        val lineStart = text.lastIndexOf('\n', (selection.start - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', selection.end).let { if (it == -1) text.length else it }

        val block = text.substring(lineStart, lineEnd)
        val lines = block.lines()

        val allCommented = lines.all { it.trim().isEmpty() || it.trimStart().startsWith(prefix) }

        val modifiedLines = lines.map { line ->
            if (line.isBlank()) line
            else if (allCommented) {
                val idx = line.indexOf(prefix)
                if (idx != -1) {
                    val after = line.substring(idx + prefix.length)
                    val cleanAfter = if (after.startsWith(" ")) after.substring(1) else after
                    line.substring(0, idx) + cleanAfter
                } else line
            } else {
                val indent = line.takeWhile { it == ' ' || it == '\t' }
                val content = line.substring(indent.length)
                "$indent$prefix $content"
            }
        }

        val newBlock = modifiedLines.joinToString("\n")
        val newText = text.replaceRange(lineStart, lineEnd, newBlock)
        onTextChanged(TextFieldValue(text = newText, selection = TextRange(lineStart, lineStart + newBlock.length)))
    }

    /**
     * Indents or unindents the current line(s).
     */
    fun indentLines(isUnindent: Boolean = false) {
        if (_isReadOnly.value) return
        val current = _textFieldValue.value
        val text = current.text
        val selection = current.selection
        val indentStr = _indentOption.value.text

        val lineStart = text.lastIndexOf('\n', (selection.start - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', selection.end).let { if (it == -1) text.length else it }

        val block = text.substring(lineStart, lineEnd)
        val lines = block.lines()

        val modifiedLines = lines.map { line ->
            if (isUnindent) {
                if (line.startsWith(indentStr)) line.removePrefix(indentStr)
                else if (line.startsWith("\t")) line.removePrefix("\t")
                else if (line.startsWith("  ")) line.removePrefix("  ")
                else line.trimStart()
            } else {
                indentStr + line
            }
        }

        val newBlock = modifiedLines.joinToString("\n")
        val newText = text.replaceRange(lineStart, lineEnd, newBlock)
        onTextChanged(TextFieldValue(text = newText, selection = TextRange(lineStart, lineStart + newBlock.length)))
    }

    // --- Search & Replace ---

    fun toggleSearch() {
        _isSearchOpen.value = !_isSearchOpen.value
        if (!_isSearchOpen.value) {
            _searchMatches.value = emptyList()
            _currentMatchIndex.value = -1
        } else if (_searchQuery.value.isNotEmpty()) {
            executeSearch()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        scheduleSearch(query)
    }

    fun setReplaceText(text: String) {
        _replaceText.value = text
    }

    fun toggleMatchCase() {
        _matchCase.value = !_matchCase.value
        executeSearch()
    }

    fun toggleWholeWord() {
        _wholeWord.value = !_wholeWord.value
        executeSearch()
    }

    fun toggleUseRegex() {
        _useRegex.value = !_useRegex.value
        executeSearch()
    }

    private fun scheduleSearch(query: String) {
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            withContext(Dispatchers.Default) {
                executeSearchInternal(query)
            }
        }
    }

    fun executeSearch() {
        executeSearchInternal(_searchQuery.value)
    }

    private fun executeSearchInternal(query: String) {
        if (query.isBlank()) {
            _searchMatches.value = emptyList()
            _currentMatchIndex.value = -1
            _regexError.value = null
            return
        }

        val text = _textFieldValue.value.text
        val matches = mutableListOf<SearchResultMatch>()

        try {
            if (_useRegex.value) {
                val validation = SafeRegexValidator.validate(query, _matchCase.value)
                if (!validation.isValid) {
                    _regexError.value = validation.errorMessage
                    _searchMatches.value = emptyList()
                    _currentMatchIndex.value = -1
                    return
                }
                _regexError.value = null
                val pattern = validation.pattern ?: return
                val matcher = pattern.matcher(text)
                while (matcher.find() && matches.size < 500) {
                    val start = matcher.start()
                    val end = matcher.end()
                    val lineIdx = text.substring(0, start).count { it == '\n' }
                    val snippet = text.substring((start - 20).coerceAtLeast(0), (end + 20).coerceAtMost(text.length))
                    matches.add(SearchResultMatch(start, end, lineIdx, snippet))
                }
            } else {
                _regexError.value = null
                var startIndex = 0
                val patternStr = if (_wholeWord.value) "\\b${Pattern.quote(query)}\\b" else Pattern.quote(query)
                val flags = if (_matchCase.value) 0 else Pattern.CASE_INSENSITIVE
                val pattern = Pattern.compile(patternStr, flags)
                val matcher = pattern.matcher(text)

                while (matcher.find() && matches.size < 500) {
                    val start = matcher.start()
                    val end = matcher.end()
                    val lineIdx = text.substring(0, start).count { it == '\n' }
                    val snippet = text.substring((start - 20).coerceAtLeast(0), (end + 20).coerceAtMost(text.length))
                    matches.add(SearchResultMatch(start, end, lineIdx, snippet))
                }
            }

            _searchMatches.value = matches
            if (matches.isNotEmpty()) {
                val curCursor = _textFieldValue.value.selection.start
                val closestIdx = matches.indexOfFirst { it.startIndex >= curCursor }.let { if (it == -1) 0 else it }
                _currentMatchIndex.value = closestIdx
            } else {
                _currentMatchIndex.value = -1
            }
        } catch (e: Exception) {
            _regexError.value = e.message
            _searchMatches.value = emptyList()
            _currentMatchIndex.value = -1
        }
    }

    fun nextMatch() {
        val list = _searchMatches.value
        if (list.isEmpty()) return
        val nextIdx = (_currentMatchIndex.value + 1) % list.size
        _currentMatchIndex.value = nextIdx
        jumpToMatch(list[nextIdx])
    }

    fun prevMatch() {
        val list = _searchMatches.value
        if (list.isEmpty()) return
        val prevIdx = if (_currentMatchIndex.value - 1 < 0) list.size - 1 else _currentMatchIndex.value - 1
        _currentMatchIndex.value = prevIdx
        jumpToMatch(list[prevIdx])
    }

    private fun jumpToMatch(match: SearchResultMatch) {
        val current = _textFieldValue.value
        _textFieldValue.value = current.copy(selection = TextRange(match.startIndex, match.endIndex))
    }

    fun replaceCurrentMatch() {
        if (_isReadOnly.value) return
        val list = _searchMatches.value
        val idx = _currentMatchIndex.value
        if (idx !in list.indices) return

        val match = list[idx]
        val text = _textFieldValue.value.text
        val newText = text.replaceRange(match.startIndex, match.endIndex, _replaceText.value)
        val newCursor = match.startIndex + _replaceText.value.length

        onTextChanged(TextFieldValue(text = newText, selection = TextRange(newCursor)))
        executeSearch()
    }

    fun replaceAllMatches() {
        if (_isReadOnly.value) return
        val query = _searchQuery.value
        if (query.isBlank()) return

        val text = _textFieldValue.value.text
        val replaceWith = _replaceText.value

        val newText = if (_useRegex.value) {
            val flags = if (_matchCase.value) 0 else Pattern.CASE_INSENSITIVE
            val pattern = Pattern.compile(query, flags)
            pattern.matcher(text).replaceAll(replaceWith)
        } else if (_wholeWord.value) {
            val flags = if (_matchCase.value) 0 else Pattern.CASE_INSENSITIVE
            val pattern = Pattern.compile("\\b${Pattern.quote(query)}\\b", flags)
            pattern.matcher(text).replaceAll(replaceWith)
        } else {
            text.replace(query, replaceWith, ignoreCase = !_matchCase.value)
        }

        onTextChanged(TextFieldValue(text = newText, selection = TextRange(0)))
        executeSearch()
    }

    // --- Formatters ---

    fun prettyPrintJson() {
        if (_isReadOnly.value) return
        val currentText = _textFieldValue.value.text
        val result = JsonFormatter.prettyPrint(currentText, _indentOption.value.spaceCount)
        if (result.isSuccess) {
            onTextChanged(TextFieldValue(text = result.getOrThrow(), selection = TextRange(0)))
        } else {
            _errorMessage.value = "Failed formatting JSON: ${result.exceptionOrNull()?.message}"
        }
    }

    fun minifyJson() {
        if (_isReadOnly.value) return
        val currentText = _textFieldValue.value.text
        val result = JsonFormatter.minify(currentText)
        if (result.isSuccess) {
            onTextChanged(TextFieldValue(text = result.getOrThrow(), selection = TextRange(0)))
        } else {
            _errorMessage.value = "Failed minifying JSON: ${result.exceptionOrNull()?.message}"
        }
    }

    fun formatXml() {
        if (_isReadOnly.value) return
        val currentText = _textFieldValue.value.text
        val result = XmlFormatter.prettyPrint(currentText, _indentOption.value.spaceCount)
        if (result.isSuccess) {
            onTextChanged(TextFieldValue(text = result.getOrThrow(), selection = TextRange(0)))
        } else {
            _errorMessage.value = "Failed formatting XML: ${result.exceptionOrNull()?.message}"
        }
    }

    // --- Save System ---

    fun saveContent(onSuccess: (() -> Unit)? = null) {
        val item = currentItem ?: return
        if (_isReadOnly.value) return

        viewModelScope.launch {
            _saveStatus.value = SaveStatus.SAVING
            try {
                val currentText = _textFieldValue.value.text

                // Validate JSON if applicable
                val lang = tabManager.currentTab?.language
                if (lang == CodeLanguage.JSON) {
                    val validation = JsonFormatter.validate(currentText)
                    if (!validation.isValid) {
                        _saveStatus.value = SaveStatus.ERROR
                        _errorMessage.value = "JSON validation warning: ${validation.errorMessage}"
                        // Allow user to still proceed if intended or show error
                    }
                }

                val updateResult = repository.updateFileContent(item, currentText)
                if (updateResult.isSuccess) {
                    currentItem = updateResult.getOrNull()
                    tabManager.markCurrentTabSaved(currentText)
                    _saveStatus.value = SaveStatus.SAVED
                    onSuccess?.invoke()
                } else {
                    _saveStatus.value = SaveStatus.ERROR
                    _errorMessage.value = "Save failed: ${updateResult.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _saveStatus.value = SaveStatus.ERROR
                _errorMessage.value = "Save failed: ${e.message}"
            }
        }
    }

    // --- Preferences & Toggles ---

    fun setFontSize(size: EditorFontSize) {
        _fontSize.value = size
    }

    fun setIndentOption(option: IndentOption) {
        _indentOption.value = option
    }

    fun toggleWordWrap() {
        _isWordWrap.value = !_isWordWrap.value
    }

    fun togglePreviewMode() {
        _isPreviewMode.value = !_isPreviewMode.value
    }

    fun toggleReadOnly() {
        _isReadOnly.value = !_isReadOnly.value
        _saveStatus.value = if (_isReadOnly.value) SaveStatus.READ_ONLY else SaveStatus.SAVED
    }

    fun setLanguage(language: CodeLanguage) {
        tabManager.updateCurrentTabLanguage(language)
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    // --- Statistics ---

    private fun scheduleStatsUpdate(text: String) {
        statsDebounceJob?.cancel()
        statsDebounceJob = viewModelScope.launch {
            withContext(Dispatchers.Default) {
                updateStats(text)
            }
        }
    }

    private fun updateStats(text: String) {
        val lines = text.lines().size
        val chars = text.length
        val words = if (text.isBlank()) 0 else text.split(Regex("\\s+")).filter { it.isNotBlank() }.size

        val currentSel = _textFieldValue.value.selection
        val selLength = if (currentSel.collapsed) 0 else (currentSel.end - currentSel.start).coerceAtLeast(0)

        _stats.value = DocumentStats(
            lines = lines,
            words = words,
            characters = chars,
            selectedChars = selLength,
            selectedLines = 0
        )
    }

    /**
     * Wipes volatile editor memory immediately when vault locks.
     */
    private fun purgeSensitiveState() {
        _textFieldValue.value = TextFieldValue()
        tabManager.clearAll()
        editHistory.clear()
        _searchMatches.value = emptyList()
        currentItem = null
    }

    override fun onCleared() {
        super.onCleared()
        purgeSensitiveState()
    }
}
