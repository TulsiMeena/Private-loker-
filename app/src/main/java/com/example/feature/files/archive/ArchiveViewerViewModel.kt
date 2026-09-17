package com.example.feature.files.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.security.SessionSecurityManager
import com.example.feature.files.operations.FileOperationManager
import com.example.feature.files.operations.FileOperationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class ArchiveViewerViewModel(
    val itemId: Long,
    private val repository: VaultRepository,
    private val sessionManager: SessionSecurityManager,
    private val zipManager: ZipVaultManager,
    private val operationManager: FileOperationManager
) : ViewModel() {

    private val _archiveItem = MutableStateFlow<VaultItemEntity?>(null)
    val archiveItem: StateFlow<VaultItemEntity?> = _archiveItem.asStateFlow()

    private val _metadata = MutableStateFlow<ArchiveMetadata?>(null)
    val metadata: StateFlow<ArchiveMetadata?> = _metadata.asStateFlow()

    private val _allEntries = MutableStateFlow<List<ArchiveEntryItem>>(emptyList())

    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(ArchiveSortOption.NAME_ASC)
    val sortOption: StateFlow<ArchiveSortOption> = _sortOption.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isUnsupportedFormat = MutableStateFlow(false)
    val isUnsupportedFormat: StateFlow<Boolean> = _isUnsupportedFormat.asStateFlow()

    private val _isPasswordProtected = MutableStateFlow(false)
    val isPasswordProtected: StateFlow<Boolean> = _isPasswordProtected.asStateFlow()

    // Preview state
    private val _previewEntry = MutableStateFlow<ArchiveEntryItem?>(null)
    val previewEntry: StateFlow<ArchiveEntryItem?> = _previewEntry.asStateFlow()

    private val _previewFile = MutableStateFlow<File?>(null)
    val previewFile: StateFlow<File?> = _previewFile.asStateFlow()

    private val _isPreviewLoading = MutableStateFlow(false)
    val isPreviewLoading: StateFlow<Boolean> = _isPreviewLoading.asStateFlow()

    private val _previewTextContent = MutableStateFlow<String?>(null)
    val previewTextContent: StateFlow<String?> = _previewTextContent.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val folders: StateFlow<List<VaultFolderEntity>> = repository.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVaultItems: StateFlow<List<VaultItemEntity>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Displayed entries (filtered by current folder or search query, directories first)
    val displayedEntries: StateFlow<List<ArchiveEntryItem>> = combine(
        _allEntries,
        _currentPath,
        _searchQuery,
        _sortOption
    ) { entries, currentFolder, query, sort ->
        val filtered = if (query.isNotBlank()) {
            entries.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.fullPath.contains(query, ignoreCase = true)
            }
        } else {
            entries.filter { it.parentPath == currentFolder }
        }

        // Sort: directories first, then files by sort option
        val (dirs, files) = filtered.partition { it.isDirectory }

        val sortedDirs = when (sort) {
            ArchiveSortOption.NAME_DESC -> dirs.sortedByDescending { it.name.lowercase(Locale.ROOT) }
            else -> dirs.sortedBy { it.name.lowercase(Locale.ROOT) }
        }

        val sortedFiles = when (sort) {
            ArchiveSortOption.NAME_ASC -> files.sortedBy { it.name.lowercase(Locale.ROOT) }
            ArchiveSortOption.NAME_DESC -> files.sortedByDescending { it.name.lowercase(Locale.ROOT) }
            ArchiveSortOption.DATE_DESC -> files.sortedByDescending { it.lastModifiedTime }
            ArchiveSortOption.DATE_ASC -> files.sortedBy { it.lastModifiedTime }
            ArchiveSortOption.SIZE_DESC -> files.sortedByDescending { it.uncompressedSize }
            ArchiveSortOption.SIZE_ASC -> files.sortedBy { it.uncompressedSize }
        }

        sortedDirs + sortedFiles
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Breadcrumb path pairs: e.g. [("Root", ""), ("src", "src"), ("main", "src/main")]
    val breadcrumbs: StateFlow<List<Pair<String, String>>> = _currentPath.combine(_archiveItem) { path, item ->
        val list = mutableListOf<Pair<String, String>>()
        list.add(Pair(item?.title ?: "Archive Root", ""))
        if (path.isNotBlank()) {
            val segments = path.split('/').filter { it.isNotEmpty() }
            var accumulated = ""
            for (seg in segments) {
                accumulated = if (accumulated.isEmpty()) seg else "$accumulated/$seg"
                list.add(Pair(seg, accumulated))
            }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(Pair("Archive Root", "")))

    init {
        loadArchive()
    }

    fun loadArchive() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _isUnsupportedFormat.value = false
            _isPasswordProtected.value = false

            val item = repository.getItemById(itemId)
            if (item == null) {
                _errorMessage.value = "Archive not found in vault"
                _isLoading.value = false
                return@launch
            }
            _archiveItem.value = item

            val result = zipManager.openArchive(item)
            if (result.isSuccess) {
                val (meta, entries) = result.getOrThrow()
                _metadata.value = meta
                _allEntries.value = entries
                _isPasswordProtected.value = meta.isPasswordProtected
            } else {
                val ex = result.exceptionOrNull()
                when (ex) {
                    is UnsupportedArchiveException -> {
                        _isUnsupportedFormat.value = true
                        _errorMessage.value = ex.message ?: "Unsupported Archive Format"
                    }
                    is ZipPasswordProtectedException -> {
                        _isPasswordProtected.value = true
                        _errorMessage.value = "Password-protected archive not supported"
                    }
                    else -> {
                        _errorMessage.value = ex?.message ?: "Archive integrity check failed"
                    }
                }
            }
            _isLoading.value = false
        }
    }

    fun navigateInto(dirPath: String) {
        _searchQuery.value = ""
        _currentPath.value = dirPath
    }

    fun navigateTo(path: String) {
        _searchQuery.value = ""
        _currentPath.value = path
    }

    fun navigateUp() {
        _searchQuery.value = ""
        val cur = _currentPath.value
        if (cur.contains('/')) {
            _currentPath.value = cur.substringBeforeLast('/')
        } else {
            _currentPath.value = ""
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(option: ArchiveSortOption) {
        _sortOption.value = option
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    /**
     * Previews a single entry in safe temporary storage.
     */
    fun previewEntry(entry: ArchiveEntryItem) {
        if (entry.isDirectory) {
            navigateInto(entry.fullPath)
            return
        }

        viewModelScope.launch {
            _isPreviewLoading.value = true
            _previewEntry.value = entry
            _previewTextContent.value = null

            val item = _archiveItem.value ?: return@launch
            val res = zipManager.previewArchiveEntry(item, entry.fullPath)

            if (res.isSuccess) {
                val (_, file) = res.getOrThrow()
                _previewFile.value = file

                // If text or code, also read small content for quick inspection
                val ext = entry.extension
                if (ext in listOf("txt", "md", "json", "xml", "kt", "java", "py", "js", "html", "css", "sql", "sh", "yaml", "csv", "log")) {
                    if (file.length() <= 2L * 1024 * 1024) {
                        try {
                            _previewTextContent.value = file.readText(Charsets.UTF_8)
                        } catch (_: Exception) {
                            _previewTextContent.value = "Unable to decode text (binary encoding)"
                        }
                    } else {
                        _previewTextContent.value = "File is too large for inline text preview (${file.length() / 1024} KB)"
                    }
                }
            } else {
                _statusMessage.value = "Preview failed: ${res.exceptionOrNull()?.message}"
            }
            _isPreviewLoading.value = false
        }
    }

    fun dismissPreview() {
        val file = _previewFile.value
        if (file != null) {
            repository.storageManager.releaseTemporaryFile(file)
            _previewFile.value = null
        }
        _previewEntry.value = null
        _previewTextContent.value = null
        _isPreviewLoading.value = false
    }

    /**
     * Extracts entire archive.
     */
    fun extractArchive(
        option: ArchiveExtractionOption = ArchiveExtractionOption.EXTRACT_HERE,
        customFolderName: String? = null,
        targetFolderId: Long? = null
    ) {
        val item = _archiveItem.value ?: return
        viewModelScope.launch {
            var isCancelledFlag = false
            val opId = operationManager.enqueueOperation(
                type = FileOperationType.EXTRACTION,
                title = "Extracting ${item.title}",
                cancelAction = { isCancelledFlag = true }
            )

            val result = zipManager.extractArchive(
                item = item,
                targetFolderId = targetFolderId ?: item.folderId,
                option = option,
                customFolderName = customFolderName,
                onProgress = { state ->
                    operationManager.updateProgress(
                        id = opId,
                        progress = state.progress,
                        currentStep = "${state.phase}: ${state.currentFile}",
                        itemsProcessed = state.currentIndex,
                        totalItems = state.totalCount
                    )
                },
                isCancelled = { isCancelledFlag }
            )

            if (result.isSuccess) {
                operationManager.completeOperation(opId)
                _statusMessage.value = "Extracted ${result.getOrDefault(0)} files into vault"
            } else {
                val err = result.exceptionOrNull()?.message ?: "Extraction failed"
                operationManager.failOperation(opId, err)
                _statusMessage.value = err
            }
        }
    }

    /**
     * Deletes an entry from the archive with atomic rebuild.
     */
    fun deleteEntry(entryPath: String) {
        val item = _archiveItem.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = zipManager.modifyArchive(
                item = item,
                entriesToDelete = setOf(entryPath)
            )
            if (result.isSuccess) {
                _archiveItem.value = result.getOrNull()
                _statusMessage.value = "Deleted $entryPath from archive"
                loadArchive()
            } else {
                _statusMessage.value = "Failed to delete entry: ${result.exceptionOrNull()?.message}"
                _isLoading.value = false
            }
        }
    }

    /**
     * Renames an entry within the archive with atomic rebuild.
     */
    fun renameEntry(oldPath: String, newName: String) {
        val item = _archiveItem.value ?: return
        if (newName.isBlank() || newName.contains('/') || newName.contains('\\') || newName.contains("..")) {
            _statusMessage.value = "Invalid filename"
            return
        }

        val parent = if (oldPath.contains('/')) oldPath.substringBeforeLast('/') else ""
        val newPath = if (parent.isEmpty()) newName else "$parent/$newName"

        viewModelScope.launch {
            _isLoading.value = true
            val result = zipManager.modifyArchive(
                item = item,
                entriesToRename = mapOf(oldPath to newPath)
            )
            if (result.isSuccess) {
                _archiveItem.value = result.getOrNull()
                _statusMessage.value = "Renamed entry to $newName"
                loadArchive()
            } else {
                _statusMessage.value = "Failed to rename: ${result.exceptionOrNull()?.message}"
                _isLoading.value = false
            }
        }
    }

    /**
     * Adds files from vault into the archive with atomic rebuild.
     */
    fun addVaultFiles(itemsToAdd: List<VaultItemEntity>) {
        val item = _archiveItem.value ?: return
        if (itemsToAdd.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            val result = zipManager.modifyArchive(
                item = item,
                vaultItemsToAdd = itemsToAdd
            )
            if (result.isSuccess) {
                _archiveItem.value = result.getOrNull()
                _statusMessage.value = "Added ${itemsToAdd.size} files to archive"
                loadArchive()
            } else {
                _statusMessage.value = "Failed to add files: ${result.exceptionOrNull()?.message}"
                _isLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        dismissPreview()
    }
}
