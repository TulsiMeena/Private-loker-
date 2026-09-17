package com.example.feature.code.studio

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.CodeContentSearchResult
import com.example.core.database.DuplicateResolution
import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.security.LockState
import com.example.core.security.SessionSecurityManager
import com.example.core.storage.VaultCategory
import com.example.feature.code.CodeLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StudioSection(val title: String) {
    ALL_FILES("All Files"),
    CODE("Code"),
    TEXT("Text"),
    RECENT("Recent"),
    FAVORITES("Favorites"),
    FOLDERS("Folders"),
    TAGS("Tags"),
    SEARCH("Search")
}

enum class StudioViewMode {
    LIST,
    GRID
}

private data class AllItemsBucket(
    val codeItems: List<VaultItemEntity>,
    val textItems: List<VaultItemEntity>,
    val recentItems: List<VaultItemEntity>,
    val favItems: List<VaultItemEntity>
)

private data class FilterCriteria(
    val section: StudioSection,
    val folderId: Long?,
    val tag: String?,
    val query: String
)

class CodeTextStudioViewModel(
    private val repository: VaultRepository,
    private val sessionManager: SessionSecurityManager
) : ViewModel() {

    private val _currentSection = MutableStateFlow(StudioSection.ALL_FILES)
    val currentSection: StateFlow<StudioSection> = _currentSection.asStateFlow()

    private val _viewMode = MutableStateFlow(StudioViewMode.LIST)
    val viewMode: StateFlow<StudioViewMode> = _viewMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isContentSearchEnabled = MutableStateFlow(false)
    val isContentSearchEnabled: StateFlow<Boolean> = _isContentSearchEnabled.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val _currentFolderId = MutableStateFlow<Long?>(null)
    val currentFolderId: StateFlow<Long?> = _currentFolderId.asStateFlow()

    // Multi-select state
    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode.asStateFlow()

    private val _selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedItemIds: StateFlow<Set<Long>> = _selectedItemIds.asStateFlow()

    // Data streams
    private val _items = MutableStateFlow<List<VaultItemEntity>>(emptyList())
    val items: StateFlow<List<VaultItemEntity>> = _items.asStateFlow()

    private val _folders = MutableStateFlow<List<VaultFolderEntity>>(emptyList())
    val folders: StateFlow<List<VaultFolderEntity>> = _folders.asStateFlow()

    private val _availableTags = MutableStateFlow<List<String>>(emptyList())
    val availableTags: StateFlow<List<String>> = _availableTags.asStateFlow()

    private val _contentSearchResults = MutableStateFlow<List<CodeContentSearchResult>>(emptyList())
    val contentSearchResults: StateFlow<List<CodeContentSearchResult>> = _contentSearchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Navigation event to immediately open newly created file
    private val _navigateToFileEditor = MutableSharedFlow<Long>()
    val navigateToFileEditor: SharedFlow<Long> = _navigateToFileEditor.asSharedFlow()

    private var contentSearchJob: Job? = null

    init {
        loadData()

        viewModelScope.launch {
            sessionManager.lockState.collect { lockState ->
                if (lockState !is LockState.Unlocked) {
                    _items.value = emptyList()
                    _contentSearchResults.value = emptyList()
                    _selectedItemIds.value = emptySet()
                    _isMultiSelectMode.value = false
                }
            }
        }
    }

    fun loadData() {
        val itemsFlow = combine(
            repository.getItemsByCategory(VaultCategory.CODE),
            repository.getItemsByCategory(VaultCategory.TEXT),
            repository.getRecentItems(30),
            repository.favoriteItems
        ) { codeItems, textItems, recentItems, favItems ->
            AllItemsBucket(codeItems, textItems, recentItems, favItems)
        }

        val criteriaFlow = combine(
            _currentSection,
            _currentFolderId,
            _selectedTag,
            _searchQuery
        ) { section, folderId, tag, query ->
            FilterCriteria(section, folderId, tag, query)
        }

        viewModelScope.launch {
            _isLoading.value = true
            combine(itemsFlow, criteriaFlow) { bucket, criteria ->
                val combinedCodeAndText = (bucket.codeItems + bucket.textItems).distinctBy { it.id }

                val filteredBySection = when (criteria.section) {
                    StudioSection.ALL_FILES -> combinedCodeAndText
                    StudioSection.CODE -> bucket.codeItems
                    StudioSection.TEXT -> bucket.textItems
                    StudioSection.RECENT -> bucket.recentItems.filter { item ->
                        item.category == VaultCategory.CODE.name || item.category == VaultCategory.TEXT.name
                    }
                    StudioSection.FAVORITES -> bucket.favItems.filter { item ->
                        item.category == VaultCategory.CODE.name || item.category == VaultCategory.TEXT.name
                    }
                    StudioSection.FOLDERS -> {
                        combinedCodeAndText.filter { it.folderId == criteria.folderId }
                    }
                    StudioSection.TAGS -> {
                        val activeTag = criteria.tag
                        if (activeTag == null) combinedCodeAndText
                        else combinedCodeAndText.filter { item ->
                            item.tags.split(",").map { it.trim() }.contains(activeTag)
                        }
                    }
                    StudioSection.SEARCH -> {
                        val q = criteria.query
                        if (q.isBlank()) combinedCodeAndText
                        else combinedCodeAndText.filter { item ->
                            item.title.contains(q, ignoreCase = true) ||
                                    item.tags.contains(q, ignoreCase = true)
                        }
                    }
                }

                if (criteria.section != StudioSection.SEARCH && criteria.query.isNotBlank()) {
                    filteredBySection.filter { item ->
                        item.title.contains(criteria.query, ignoreCase = true) ||
                                item.tags.contains(criteria.query, ignoreCase = true)
                    }
                } else {
                    filteredBySection
                }
            }.collect { filtered ->
                _items.value = filtered
                _isLoading.value = false
            }
        }

        // Collect folders
        viewModelScope.launch {
            repository.getFolders(null).collect {
                _folders.value = it
            }
        }

        // Collect tags
        viewModelScope.launch {
            repository.getAllTags().collect { allTagsList ->
                val parsed = allTagsList.flatMap { it.split(",") }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                _availableTags.value = parsed
            }
        }
    }

    fun setSection(section: StudioSection) {
        _currentSection.value = section
        if (section == StudioSection.SEARCH && _isContentSearchEnabled.value && _searchQuery.value.isNotBlank()) {
            triggerContentSearch(_searchQuery.value)
        }
    }

    fun setViewMode(mode: StudioViewMode) {
        _viewMode.value = mode
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (_isContentSearchEnabled.value && query.isNotBlank()) {
            triggerContentSearch(query)
        } else {
            _contentSearchResults.value = emptyList()
        }
    }

    fun toggleContentSearch() {
        _isContentSearchEnabled.value = !_isContentSearchEnabled.value
        if (_isContentSearchEnabled.value && _searchQuery.value.isNotBlank()) {
            triggerContentSearch(_searchQuery.value)
        } else {
            _contentSearchResults.value = emptyList()
        }
    }

    private fun triggerContentSearch(query: String) {
        contentSearchJob?.cancel()
        contentSearchJob = viewModelScope.launch {
            _isLoading.value = true
            val results = repository.searchCodeAndTextContent(query)
            _contentSearchResults.value = results
            _isLoading.value = false
        }
    }

    fun setSelectedTag(tag: String?) {
        _selectedTag.value = tag
        _currentSection.value = StudioSection.TAGS
    }

    fun setCurrentFolder(folderId: Long?) {
        _currentFolderId.value = folderId
        _currentSection.value = StudioSection.FOLDERS
    }

    // --- Selection & Batch Operations ---

    fun toggleMultiSelect(item: VaultItemEntity? = null) {
        _isMultiSelectMode.value = !_isMultiSelectMode.value
        if (!_isMultiSelectMode.value) {
            _selectedItemIds.value = emptySet()
        } else if (item != null) {
            _selectedItemIds.value = setOf(item.id)
        }
    }

    fun toggleSelectItem(itemId: Long) {
        val current = _selectedItemIds.value.toMutableSet()
        if (current.contains(itemId)) {
            current.remove(itemId)
        } else {
            current.add(itemId)
        }
        _selectedItemIds.value = current
    }

    fun selectAll() {
        _selectedItemIds.value = _items.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedItemIds.value = emptySet()
        _isMultiSelectMode.value = false
    }

    fun batchMove(targetFolderId: Long?) {
        val ids = _selectedItemIds.value
        val itemsToMove = _items.value.filter { ids.contains(it.id) }
        viewModelScope.launch {
            val res = repository.batchMove(itemsToMove, targetFolderId)
            if (res.isSuccess) {
                _statusMessage.value = "Moved ${itemsToMove.size} item(s)"
                clearSelection()
            } else {
                _statusMessage.value = "Batch move failed"
            }
        }
    }

    fun batchDelete() {
        val ids = _selectedItemIds.value
        val itemsToDelete = _items.value.filter { ids.contains(it.id) }
        viewModelScope.launch {
            val res = repository.batchDelete(itemsToDelete)
            if (res.isSuccess) {
                _statusMessage.value = "Moved ${itemsToDelete.size} item(s) to trash"
                clearSelection()
            } else {
                _statusMessage.value = "Batch delete failed"
            }
        }
    }

    fun batchFavorite(isFav: Boolean) {
        val ids = _selectedItemIds.value
        val itemsToFav = _items.value.filter { ids.contains(it.id) }
        viewModelScope.launch {
            val res = repository.batchFavorite(itemsToFav, isFav)
            if (res.isSuccess) {
                clearSelection()
            }
        }
    }

    fun batchAddTag(tag: String) {
        val ids = _selectedItemIds.value
        val itemsToTag = _items.value.filter { ids.contains(it.id) }
        viewModelScope.launch {
            val res = repository.batchAddTag(itemsToTag, tag)
            if (res.isSuccess) {
                _statusMessage.value = "Added tag '$tag' to ${itemsToTag.size} item(s)"
                clearSelection()
            }
        }
    }

    // --- File Creation inside Encrypted Architecture ---

    fun createNewFile(
        fileName: String,
        language: CodeLanguage,
        folderId: Long? = null,
        initialTemplate: String = ""
    ) {
        viewModelScope.launch {
            val cleanName = if (fileName.contains('.')) fileName else "$fileName.${language.defaultExtension}"
            val category = if (language.isCode) VaultCategory.CODE else VaultCategory.TEXT

            val result = repository.createNewFile(
                title = cleanName,
                content = initialTemplate,
                category = category,
                folderId = folderId
            )

            if (result.isSuccess) {
                val created = result.getOrThrow()
                _statusMessage.value = "Created $cleanName"
                _navigateToFileEditor.emit(created.id)
            } else {
                _statusMessage.value = "Failed creating file: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun createFolder(name: String, parentId: Long? = null) {
        viewModelScope.launch {
            val res = repository.createFolder(name, parentId)
            if (res.isSuccess) {
                _statusMessage.value = "Created folder '$name'"
            } else {
                _statusMessage.value = "Failed creating folder"
            }
        }
    }

    // --- Single File Actions ---

    fun renameItem(item: VaultItemEntity, newTitle: String) {
        viewModelScope.launch {
            val res = repository.renameItem(item.id, newTitle)
            if (res.isSuccess) {
                _statusMessage.value = "Renamed to $newTitle"
            }
        }
    }

    fun toggleFavorite(item: VaultItemEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(item)
        }
    }

    fun updateItemTags(item: VaultItemEntity, tags: String) {
        viewModelScope.launch {
            repository.updateItemTags(item.id, tags)
        }
    }

    fun deleteItem(item: VaultItemEntity) {
        viewModelScope.launch {
            val res = repository.moveToTrash(item)
            if (res.isSuccess) {
                _statusMessage.value = "Moved ${item.title} to trash"
            }
        }
    }

    fun duplicateItem(item: VaultItemEntity) {
        viewModelScope.launch {
            val res = repository.copyItem(item, item.folderId)
            if (res.isSuccess) {
                _statusMessage.value = "Duplicated ${item.title}"
            }
        }
    }

    fun moveItem(item: VaultItemEntity, targetFolderId: Long?) {
        viewModelScope.launch {
            val res = repository.moveItemToFolder(item.id, targetFolderId)
            if (res.isSuccess) {
                _statusMessage.value = "Moved ${item.title}"
            }
        }
    }

    // --- Import Pipeline ---

    fun importFiles(
        context: Context,
        uris: List<Uri>,
        resolution: DuplicateResolution = DuplicateResolution.KEEP_BOTH
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            var importedCount = 0

            for (uri in uris) {
                try {
                    val contentResolver = context.contentResolver
                    val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "imported_file.txt"
                    val mimeType = contentResolver.getType(uri) ?: "text/plain"
                    val category = VaultCategory.fromFileNameAndMime(fileName, mimeType)

                    contentResolver.openInputStream(uri)?.use { stream ->
                        val result = repository.importItem(
                            title = fileName,
                            category = category,
                            mimeType = mimeType,
                            inputStream = stream,
                            folderId = _currentFolderId.value,
                            resolution = resolution
                        )
                        if (result.isSuccess) importedCount++
                    }
                } catch (_: Exception) {}
            }

            _isLoading.value = false
            _statusMessage.value = "Successfully imported $importedCount file(s)"
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
