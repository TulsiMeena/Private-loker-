package com.example.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.security.SessionSecurityManager
import com.example.core.storage.VaultCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class ViewMode {
    GRID, LIST
}

enum class SortOption(val displayName: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First"),
    TYPE("File Category")
}

enum class SizeFilter(val displayName: String) {
    ALL("All Sizes"),
    SMALL("< 1 MB"),
    MEDIUM("1 MB - 50 MB"),
    LARGE("> 50 MB")
}

enum class DateFilter(val displayName: String) {
    ALL("All Time"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month")
}

enum class StatusFilter(val displayName: String) {
    ALL("All Files"),
    FAVORITES("Favorites Only"),
    TAGGED("Tagged Only")
}

data class FileBrowserUiState(
    val currentFolderId: Long? = null,
    val breadcrumbs: List<VaultFolderEntity> = emptyList(),
    val folders: List<VaultFolderEntity> = emptyList(),
    val items: List<VaultItemEntity> = emptyList(),
    val viewMode: ViewMode = ViewMode.GRID,
    val searchQuery: String = "",
    val selectedCategory: VaultCategory? = null,
    val sizeFilter: SizeFilter = SizeFilter.ALL,
    val dateFilter: DateFilter = DateFilter.ALL,
    val statusFilter: StatusFilter = StatusFilter.ALL,
    val sortOption: SortOption = SortOption.DATE_DESC,
    val isMultiSelectMode: Boolean = false,
    val selectedItemIds: Set<Long> = emptySet(),
    val isFilterSheetOpen: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class VaultFileBrowserViewModel(
    val repository: VaultRepository,
    private val sessionManager: SessionSecurityManager
) : ViewModel() {

    private val _currentFolderId = MutableStateFlow<Long?>(null)
    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<VaultCategory?>(null)
    private val _sizeFilter = MutableStateFlow(SizeFilter.ALL)
    private val _dateFilter = MutableStateFlow(DateFilter.ALL)
    private val _statusFilter = MutableStateFlow(StatusFilter.ALL)
    private val _sortOption = MutableStateFlow(SortOption.DATE_DESC)
    private val _isMultiSelectMode = MutableStateFlow(false)
    private val _selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isFilterSheetOpen = MutableStateFlow(false)

    // Dynamic folders in current folder
    private val foldersFlow = _currentFolderId.flatMapLatest { folderId ->
        repository.getFolders(folderId)
    }

    // Dynamic items in current folder
    private val rawItemsFlow = _currentFolderId.flatMapLatest { folderId ->
        repository.getItemsInFolder(folderId)
    }

    val uiState: StateFlow<FileBrowserUiState> = combine(
        _currentFolderId,
        foldersFlow,
        rawItemsFlow,
        repository.getAllFolders(),
        _viewMode,
        _searchQuery,
        _selectedCategory,
        _sizeFilter,
        _dateFilter,
        _statusFilter,
        _sortOption,
        _isMultiSelectMode,
        _selectedItemIds,
        _isFilterSheetOpen
    ) { args: Array<Any?> ->
        val currentFolderId = args[0] as Long?
        @Suppress("UNCHECKED_CAST")
        val folders = args[1] as List<VaultFolderEntity>
        @Suppress("UNCHECKED_CAST")
        val rawItems = args[2] as List<VaultItemEntity>
        @Suppress("UNCHECKED_CAST")
        val allFolders = args[3] as List<VaultFolderEntity>
        val viewMode = args[4] as ViewMode
        val searchQuery = args[5] as String
        val selectedCategory = args[6] as VaultCategory?
        val sizeFilter = args[7] as SizeFilter
        val dateFilter = args[8] as DateFilter
        val statusFilter = args[9] as StatusFilter
        val sortOption = args[10] as SortOption
        val isMultiSelect = args[11] as Boolean
        @Suppress("UNCHECKED_CAST")
        val selectedIds = args[12] as Set<Long>
        val isFilterOpen = args[13] as Boolean

        // Build breadcrumb trail
        val breadcrumbs = mutableListOf<VaultFolderEntity>()
        var curr = currentFolderId
        while (curr != null) {
            val f = allFolders.find { it.id == curr }
            if (f != null) {
                breadcrumbs.add(0, f)
                curr = f.parentId
            } else {
                break
            }
        }

        // Apply filters
        var filtered = rawItems

        // Search
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            filtered = filtered.filter {
                it.title.lowercase().contains(q) || it.tags.lowercase().contains(q)
            }
        }

        // Category
        if (selectedCategory != null) {
            filtered = filtered.filter { it.category == selectedCategory.name }
        }

        // Size filter
        filtered = when (sizeFilter) {
            SizeFilter.ALL -> filtered
            SizeFilter.SMALL -> filtered.filter { it.sizeBytes < 1024 * 1024 }
            SizeFilter.MEDIUM -> filtered.filter { it.sizeBytes in (1024 * 1024)..(50 * 1024 * 1024) }
            SizeFilter.LARGE -> filtered.filter { it.sizeBytes > 50 * 1024 * 1024 }
        }

        // Date filter
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        filtered = when (dateFilter) {
            DateFilter.ALL -> filtered
            DateFilter.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val startOfDay = cal.timeInMillis
                filtered.filter { it.modifiedAt >= startOfDay }
            }
            DateFilter.THIS_WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                val startOfWeek = cal.timeInMillis
                filtered.filter { it.modifiedAt >= startOfWeek }
            }
            DateFilter.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                val startOfMonth = cal.timeInMillis
                filtered.filter { it.modifiedAt >= startOfMonth }
            }
        }

        // Status filter
        filtered = when (statusFilter) {
            StatusFilter.ALL -> filtered
            StatusFilter.FAVORITES -> filtered.filter { it.isFavorite }
            StatusFilter.TAGGED -> filtered.filter { it.tags.isNotBlank() }
        }

        // Sort
        filtered = when (sortOption) {
            SortOption.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOption.NAME_DESC -> filtered.sortedByDescending { it.title.lowercase() }
            SortOption.DATE_DESC -> filtered.sortedByDescending { it.modifiedAt }
            SortOption.DATE_ASC -> filtered.sortedBy { it.modifiedAt }
            SortOption.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
            SortOption.SIZE_ASC -> filtered.sortedBy { it.sizeBytes }
            SortOption.TYPE -> filtered.sortedWith(compareBy({ it.category }, { it.title.lowercase() }))
        }

        FileBrowserUiState(
            currentFolderId = currentFolderId,
            breadcrumbs = breadcrumbs,
            folders = folders,
            items = filtered,
            viewMode = viewMode,
            searchQuery = searchQuery,
            selectedCategory = selectedCategory,
            sizeFilter = sizeFilter,
            dateFilter = dateFilter,
            statusFilter = statusFilter,
            sortOption = sortOption,
            isMultiSelectMode = isMultiSelect,
            selectedItemIds = selectedIds,
            isFilterSheetOpen = isFilterOpen
        )
    }.flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FileBrowserUiState()
    )

    fun navigateToFolder(folderId: Long?) {
        _currentFolderId.value = folderId
        clearSelection()
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: VaultCategory?) {
        _selectedCategory.value = category
    }

    fun setSizeFilter(filter: SizeFilter) {
        _sizeFilter.value = filter
    }

    fun setDateFilter(filter: DateFilter) {
        _dateFilter.value = filter
    }

    fun setStatusFilter(filter: StatusFilter) {
        _statusFilter.value = filter
    }

    fun setSortOption(sort: SortOption) {
        _sortOption.value = sort
    }

    fun toggleFilterSheet(isOpen: Boolean) {
        _isFilterSheetOpen.value = isOpen
    }

    fun resetFilters() {
        _selectedCategory.value = null
        _sizeFilter.value = SizeFilter.ALL
        _dateFilter.value = DateFilter.ALL
        _statusFilter.value = StatusFilter.ALL
        _sortOption.value = SortOption.DATE_DESC
    }

    // Multi-Select
    fun enterMultiSelectMode(initialItemId: Long? = null) {
        _isMultiSelectMode.value = true
        if (initialItemId != null) {
            _selectedItemIds.value = setOf(initialItemId)
        }
    }

    fun toggleItemSelection(itemId: Long) {
        val current = _selectedItemIds.value.toMutableSet()
        if (current.contains(itemId)) {
            current.remove(itemId)
        } else {
            current.add(itemId)
        }
        _selectedItemIds.value = current
        if (current.isEmpty()) {
            _isMultiSelectMode.value = false
        }
    }

    fun selectAll() {
        val allIds = uiState.value.items.map { it.id }.toSet()
        _selectedItemIds.value = allIds
        _isMultiSelectMode.value = true
    }

    fun clearSelection() {
        _selectedItemIds.value = emptySet()
        _isMultiSelectMode.value = false
    }

    // Batch operations
    fun batchMove(targetFolderId: Long?, onComplete: () -> Unit) {
        val selectedIds = _selectedItemIds.value
        val itemsToMove = uiState.value.items.filter { selectedIds.contains(it.id) }
        viewModelScope.launch {
            repository.batchMove(itemsToMove, targetFolderId)
            clearSelection()
            onComplete()
        }
    }

    fun batchDelete(onComplete: () -> Unit) {
        val selectedIds = _selectedItemIds.value
        val itemsToDelete = uiState.value.items.filter { selectedIds.contains(it.id) }
        viewModelScope.launch {
            repository.batchDelete(itemsToDelete)
            clearSelection()
            onComplete()
        }
    }

    fun batchFavorite(isFavorite: Boolean) {
        val selectedIds = _selectedItemIds.value
        val items = uiState.value.items.filter { selectedIds.contains(it.id) }
        viewModelScope.launch {
            repository.batchFavorite(items, isFavorite)
            clearSelection()
        }
    }

    fun batchAddTag(tag: String) {
        val selectedIds = _selectedItemIds.value
        val items = uiState.value.items.filter { selectedIds.contains(it.id) }
        viewModelScope.launch {
            repository.batchAddTag(items, tag)
            clearSelection()
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            repository.createFolder(name, _currentFolderId.value)
        }
    }
}
