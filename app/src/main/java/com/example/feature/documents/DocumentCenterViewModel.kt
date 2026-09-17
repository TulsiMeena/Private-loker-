package com.example.feature.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.DuplicateResolution
import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.security.LockState
import com.example.core.security.SessionSecurityManager
import com.example.core.storage.VaultCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.Locale

class DocumentCenterViewModel(
    val repository: VaultRepository,
    private val sessionManager: SessionSecurityManager
) : ViewModel() {

    private val _currentFolder = MutableStateFlow<VaultFolderEntity?>(null)
    private val _folderBreadcrumbs = MutableStateFlow<List<VaultFolderEntity>>(emptyList())
    private val _currentTab = MutableStateFlow(DocumentCenterTab.ALL)
    private val _viewMode = MutableStateFlow(DocumentViewMode.GRID)
    private val _sortOption = MutableStateFlow(DocumentSortOption.DATE_NEWEST)
    private val _searchQuery = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)
    private val _filterState = MutableStateFlow(DocumentFilterState())
    private val _isFilterSheetOpen = MutableStateFlow(false)
    private val _isMultiSelectActive = MutableStateFlow(false)
    private val _selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    // Listen for vault lock to purge thumbnails and cancel operations
    init {
        viewModelScope.launch {
            sessionManager.lockState.collect { lockState ->
                if (lockState !is LockState.Unlocked) {
                    DocumentThumbnailHelper.clearCache()
                    _selectedItemIds.value = emptySet()
                    _isMultiSelectActive.value = false
                }
            }
        }
    }

    // Unified Reactive UI State
    val uiState: StateFlow<DocumentCenterUiState> = combine(
        repository.allDocuments,
        repository.getAllFolders(),
        _currentFolder,
        _folderBreadcrumbs,
        _currentTab,
        _viewMode,
        _sortOption,
        _searchQuery,
        _isSearchActive,
        _filterState,
        _isFilterSheetOpen,
        _isMultiSelectActive,
        _selectedItemIds,
        _isLoading,
        _errorMessage
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val rawDocs = args[0] as List<VaultItemEntity>
        @Suppress("UNCHECKED_CAST")
        val folders = args[1] as List<VaultFolderEntity>
        val currentFolder = args[2] as? VaultFolderEntity
        @Suppress("UNCHECKED_CAST")
        val breadcrumbs = args[3] as List<VaultFolderEntity>
        val currentTab = args[4] as DocumentCenterTab
        val viewMode = args[5] as DocumentViewMode
        val sortOption = args[6] as DocumentSortOption
        val searchQuery = args[7] as String
        val isSearchActive = args[8] as Boolean
        val filterState = args[9] as DocumentFilterState
        val isFilterSheetOpen = args[10] as Boolean
        val isMultiSelectActive = args[11] as Boolean
        @Suppress("UNCHECKED_CAST")
        val selectedItemIds = args[12] as Set<Long>
        val isLoading = args[13] as Boolean
        val errorMessage = args[14] as? String

        // Extract available unique tags across all documents
        val tagsSet = mutableSetOf<String>()
        rawDocs.forEach { doc ->
            if (doc.tags.isNotBlank()) {
                doc.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { tagsSet.add(it) }
            }
        }

        // Apply Tab Filter
        var filtered = when (currentTab) {
            DocumentCenterTab.ALL -> rawDocs
            DocumentCenterTab.RECENT -> rawDocs.filter { it.lastAccessedAt != null }.sortedByDescending { it.lastAccessedAt }
            DocumentCenterTab.FAVORITES -> rawDocs.filter { it.isFavorite }
            DocumentCenterTab.FOLDERS -> {
                if (currentFolder == null) {
                    rawDocs.filter { it.folderId == null }
                } else {
                    rawDocs.filter { it.folderId == currentFolder.id }
                }
            }
            DocumentCenterTab.TAGS -> {
                if (filterState.selectedTag != null) {
                    rawDocs.filter { doc ->
                        doc.tags.split(",").map { it.trim().lowercase(Locale.ROOT) }
                            .contains(filterState.selectedTag.lowercase(Locale.ROOT))
                    }
                } else {
                    rawDocs.filter { it.tags.isNotBlank() }
                }
            }
        }

        // Apply Search Query
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            filtered = filtered.filter { doc ->
                doc.title.lowercase(Locale.ROOT).contains(q) ||
                doc.tags.lowercase(Locale.ROOT).contains(q) ||
                doc.mimeType.lowercase(Locale.ROOT).contains(q)
            }
        }

        // Apply Type Filter
        if (filterState.typeFilter != DocumentTypeFilter.ALL) {
            val exts = filterState.typeFilter.extensions
            filtered = filtered.filter { doc ->
                val docExt = doc.title.substringAfterLast('.', "").lowercase(Locale.ROOT)
                exts.contains(docExt)
            }
        }

        // Apply Status Filter
        when (filterState.statusFilter) {
            DocumentStatusFilter.ALL -> {}
            DocumentStatusFilter.FAVORITE -> filtered = filtered.filter { it.isFavorite }
            DocumentStatusFilter.RECENT -> filtered = filtered.filter { it.lastAccessedAt != null }
            DocumentStatusFilter.TAGGED -> filtered = filtered.filter { it.tags.isNotBlank() }
        }

        // Apply Size Filter
        if (filterState.sizeFilter != DocumentSizeFilter.ALL) {
            filtered = filtered.filter { doc ->
                doc.sizeBytes in filterState.sizeFilter.minBytes..filterState.sizeFilter.maxBytes
            }
        }

        // Apply Date Filter
        if (filterState.dateFilter != DocumentDateFilter.ALL) {
            val now = System.currentTimeMillis()
            val threshold = when (filterState.dateFilter) {
                DocumentDateFilter.TODAY -> now - (24 * 60 * 60 * 1000L)
                DocumentDateFilter.THIS_WEEK -> now - (7 * 24 * 60 * 60 * 1000L)
                DocumentDateFilter.THIS_MONTH -> now - (30 * 24 * 60 * 60 * 1000L)
                DocumentDateFilter.ALL -> 0L
            }
            filtered = filtered.filter { doc ->
                doc.modifiedAt >= threshold || doc.createdAt >= threshold
            }
        }

        // Apply Tag Filter (if specified directly in filterState)
        if (filterState.selectedTag != null && currentTab != DocumentCenterTab.TAGS) {
            filtered = filtered.filter { doc ->
                doc.tags.split(",").map { it.trim().lowercase(Locale.ROOT) }
                    .contains(filterState.selectedTag.lowercase(Locale.ROOT))
            }
        }

        // Apply Sorting
        val sorted = when (sortOption) {
            DocumentSortOption.NAME_ASC -> filtered.sortedBy { it.title.lowercase(Locale.ROOT) }
            DocumentSortOption.NAME_DESC -> filtered.sortedByDescending { it.title.lowercase(Locale.ROOT) }
            DocumentSortOption.DATE_NEWEST -> filtered.sortedByDescending { it.modifiedAt }
            DocumentSortOption.DATE_OLDEST -> filtered.sortedBy { it.modifiedAt }
            DocumentSortOption.SIZE_LARGEST -> filtered.sortedByDescending { it.sizeBytes }
            DocumentSortOption.SIZE_SMALLEST -> filtered.sortedBy { it.sizeBytes }
            DocumentSortOption.FILE_TYPE -> filtered.sortedWith(
                compareBy<VaultItemEntity> { it.title.substringAfterLast('.', "").lowercase(Locale.ROOT) }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
            )
        }

        // Filter folders if in Folders tab
        val relevantFolders = if (currentTab == DocumentCenterTab.FOLDERS) {
            folders.filter { it.parentId == currentFolder?.id }
        } else {
            emptyList()
        }

        DocumentCenterUiState(
            documents = sorted,
            folders = relevantFolders,
            currentFolder = currentFolder,
            folderBreadcrumbs = breadcrumbs,
            currentTab = currentTab,
            viewMode = viewMode,
            sortOption = sortOption,
            searchQuery = searchQuery,
            isSearchActive = isSearchActive,
            filterState = filterState,
            isFilterSheetOpen = isFilterSheetOpen,
            isMultiSelectActive = isMultiSelectActive,
            selectedItemIds = selectedItemIds,
            availableTags = tagsSet.sorted(),
            isLoading = isLoading,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DocumentCenterUiState(isLoading = true)
    )

    fun selectTab(tab: DocumentCenterTab) {
        _currentTab.value = tab
        if (tab != DocumentCenterTab.FOLDERS) {
            _currentFolder.value = null
            _folderBreadcrumbs.value = emptyList()
        }
        if (tab != DocumentCenterTab.TAGS) {
            _filterState.value = _filterState.value.copy(selectedTag = null)
        }
    }

    fun setViewMode(mode: DocumentViewMode) {
        _viewMode.value = mode
    }

    fun setSortOption(sort: DocumentSortOption) {
        _sortOption.value = sort
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
        if (!active) {
            _searchQuery.value = ""
        }
    }

    fun setFilterSheetOpen(isOpen: Boolean) {
        _isFilterSheetOpen.value = isOpen
    }

    fun updateFilters(newFilterState: DocumentFilterState) {
        _filterState.value = newFilterState
    }

    fun clearFilters() {
        _filterState.value = DocumentFilterState()
    }

    fun selectTag(tag: String?) {
        _filterState.value = _filterState.value.copy(selectedTag = tag)
    }

    // Folder navigation
    fun openFolder(folder: VaultFolderEntity) {
        val updatedBreadcrumbs = _folderBreadcrumbs.value + folder
        _folderBreadcrumbs.value = updatedBreadcrumbs
        _currentFolder.value = folder
        _currentTab.value = DocumentCenterTab.FOLDERS
    }

    fun navigateToBreadcrumb(targetFolder: VaultFolderEntity?) {
        if (targetFolder == null) {
            _currentFolder.value = null
            _folderBreadcrumbs.value = emptyList()
        } else {
            val index = _folderBreadcrumbs.value.indexOfFirst { it.id == targetFolder.id }
            if (index != -1) {
                _folderBreadcrumbs.value = _folderBreadcrumbs.value.subList(0, index + 1)
                _currentFolder.value = targetFolder
            }
        }
    }

    fun navigateUpFolder(): Boolean {
        if (_folderBreadcrumbs.value.isNotEmpty()) {
            val newList = _folderBreadcrumbs.value.dropLast(1)
            _folderBreadcrumbs.value = newList
            _currentFolder.value = newList.lastOrNull()
            return true
        }
        return false
    }

    // Multi-Select
    fun toggleMultiSelect() {
        val next = !_isMultiSelectActive.value
        _isMultiSelectActive.value = next
        if (!next) {
            _selectedItemIds.value = emptySet()
        }
    }

    fun toggleItemSelection(id: Long) {
        val current = _selectedItemIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedItemIds.value = current
        if (current.isNotEmpty() && !_isMultiSelectActive.value) {
            _isMultiSelectActive.value = true
        }
    }

    fun selectAll() {
        val allIds = uiState.value.documents.map { it.id }.toSet()
        _selectedItemIds.value = allIds
        _isMultiSelectActive.value = true
    }

    fun clearSelection() {
        _selectedItemIds.value = emptySet()
        _isMultiSelectActive.value = false
    }

    // Document Actions
    fun toggleFavorite(item: VaultItemEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(item)
        }
    }

    fun renameDocument(item: VaultItemEntity, newTitle: String) {
        viewModelScope.launch {
            val validTitle = newTitle.trim()
            if (validTitle.isNotBlank()) {
                repository.renameItem(item, validTitle)
            }
        }
    }

    fun moveDocument(item: VaultItemEntity, targetFolderId: Long?) {
        viewModelScope.launch {
            repository.moveItemToFolder(item, targetFolderId)
        }
    }

    fun copyDocument(item: VaultItemEntity) {
        viewModelScope.launch {
            repository.copyItem(item, item.folderId)
        }
    }

    fun duplicateDocument(item: VaultItemEntity) {
        viewModelScope.launch {
            repository.duplicateItem(item)
        }
    }

    fun deleteDocument(item: VaultItemEntity) {
        viewModelScope.launch {
            repository.moveToTrash(item)
        }
    }

    fun updateTags(item: VaultItemEntity, tags: String) {
        viewModelScope.launch {
            repository.updateItemTags(item, tags)
        }
    }

    fun createDocument(
        fileName: String,
        mimeType: String,
        initialContent: String,
        onCreated: (VaultItemEntity) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val bytes = initialContent.toByteArray(Charsets.UTF_8)
                val targetFolderId = _currentFolder.value?.id
                val result = repository.importItem(
                    title = fileName,
                    category = VaultCategory.DOCUMENT,
                    mimeType = mimeType,
                    inputStream = ByteArrayInputStream(bytes),
                    folderId = targetFolderId,
                    expectedSizeBytes = bytes.size.toLong(),
                    resolution = DuplicateResolution.KEEP_BOTH
                )
                if (result.isSuccess) {
                    val created = result.getOrNull()
                    if (created != null) {
                        onCreated(created)
                    }
                } else {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed creating document"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val valid = name.trim()
            if (valid.isNotBlank()) {
                repository.createFolder(valid, _currentFolder.value?.id)
            }
        }
    }

    fun clearRecentHistory() {
        viewModelScope.launch {
            repository.clearRecentHistory()
        }
    }

    // Batch Multi-Select Actions
    fun batchDeleteSelected() {
        val ids = _selectedItemIds.value.toList()
        viewModelScope.launch {
            ids.forEach { id ->
                val item = repository.getItemById(id)
                if (item != null) {
                    repository.moveToTrash(item)
                }
            }
            clearSelection()
        }
    }

    fun batchFavoriteSelected(favorite: Boolean) {
        val ids = _selectedItemIds.value.toList()
        viewModelScope.launch {
            ids.forEach { id ->
                val item = repository.getItemById(id)
                if (item != null && item.isFavorite != favorite) {
                    repository.toggleFavorite(item)
                }
            }
            clearSelection()
        }
    }

    fun batchMoveSelected(targetFolderId: Long?) {
        val ids = _selectedItemIds.value.toList()
        viewModelScope.launch {
            ids.forEach { id ->
                repository.moveItemToFolder(id, targetFolderId)
            }
            clearSelection()
        }
    }

    fun batchAddTag(tag: String) {
        val ids = _selectedItemIds.value.toList()
        viewModelScope.launch {
            val cleanTag = tag.trim()
            if (cleanTag.isNotBlank()) {
                ids.forEach { id ->
                    val item = repository.getItemById(id)
                    if (item != null) {
                        val currentTags = item.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        if (!currentTags.contains(cleanTag)) {
                            val newTags = (currentTags + cleanTag).joinToString(", ")
                            repository.updateItemTags(item, newTags)
                        }
                    }
                }
            }
            clearSelection()
        }
    }
}
