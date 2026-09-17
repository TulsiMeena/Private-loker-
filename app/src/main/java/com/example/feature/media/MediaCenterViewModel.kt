package com.example.feature.media

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.DuplicateResolution
import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.security.LockState
import com.example.core.security.SessionSecurityManager
import com.example.feature.media.audio.AudioPlaybackEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

/**
 * Clean MVVM ViewModel for Advanced Media Center.
 * Coordinates reactive streams, filtering, batch actions, and secure hardware crypto pipeline.
 */
class MediaCenterViewModel(
    private val repository: VaultRepository,
    private val sessionManager: SessionSecurityManager,
    val audioEngine: AudioPlaybackEngine
) : ViewModel() {

    private val _currentTab = MutableStateFlow(MediaCategoryTab.OVERVIEW)
    private val _viewMode = MutableStateFlow(MediaViewMode.GRID)
    private val _searchQuery = MutableStateFlow("")
    private val _isSearching = MutableStateFlow(false)
    private val _sortOption = MutableStateFlow(MediaSortOption.NEWEST)
    private val _sizeFilter = MutableStateFlow(MediaSizeFilter.ALL)
    private val _dateFilter = MutableStateFlow(MediaDateFilter.ALL)
    private val _statusFilter = MutableStateFlow(MediaStatusFilter.ALL)
    private val _selectedTag = MutableStateFlow<String?>(null)
    private val _selectedFolderId = MutableStateFlow<Long?>(null)

    private val _isMultiSelectMode = MutableStateFlow(false)
    private val _selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())

    private val _bulkImportStatus = MutableStateFlow<BulkImportStatus>(BulkImportStatus.Idle)
    val bulkImportStatus: StateFlow<BulkImportStatus> = _bulkImportStatus.asStateFlow()

    private var importJob: Job? = null

    // Pending duplicate resolution state
    private val _pendingDuplicate = MutableStateFlow<Pair<Uri, String>?>(null)
    val pendingDuplicate: StateFlow<Pair<Uri, String>?> = _pendingDuplicate.asStateFlow()

    init {
        // Vault Lock Interruption Watchdog
        viewModelScope.launch {
            sessionManager.lockState.collect { lockState ->
                if (lockState !is LockState.Unlocked) {
                    clearAllEphemeralState()
                }
            }
        }
    }

    private fun clearAllEphemeralState() {
        _isMultiSelectMode.value = false
        _selectedItemIds.value = emptySet()
        _searchQuery.value = ""
        _isSearching.value = false
        _pendingDuplicate.value = null
        _bulkImportStatus.value = BulkImportStatus.Idle
        importJob?.cancel()
        importJob = null
        MediaThumbnailHelper.clearCache()
        audioEngine.stopAndPurge()
    }

    // Unified UI state combining database flows with filter state
    val uiState: StateFlow<MediaUiState> = combine(
        repository.allMedia,
        repository.imageCount,
        repository.videoCount,
        repository.audioCount,
        repository.getAllFolders(),
        _currentTab,
        _viewMode,
        _searchQuery,
        _sortOption,
        _sizeFilter,
        _dateFilter,
        _statusFilter,
        _selectedTag,
        _isMultiSelectMode,
        _selectedItemIds,
        _isSearching
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val allMedia = args[0] as List<VaultItemEntity>
        val imgCount = args[1] as Int
        val vidCount = args[2] as Int
        val audCount = args[3] as Int
        @Suppress("UNCHECKED_CAST")
        val folders = args[4] as List<VaultFolderEntity>
        val tab = args[5] as MediaCategoryTab
        val mode = args[6] as MediaViewMode
        val query = args[7] as String
        val sort = args[8] as MediaSortOption
        val sizeF = args[9] as MediaSizeFilter
        val dateF = args[10] as MediaDateFilter
        val statF = args[11] as MediaStatusFilter
        val tag = args[12] as String?
        val multiMode = args[13] as Boolean
        @Suppress("UNCHECKED_CAST")
        val selectedIds = args[14] as Set<Long>
        val searching = args[15] as Boolean

        // Calculate sizes per category
        var imgSize = 0L
        var vidSize = 0L
        var audSize = 0L
        val tagsSet = mutableSetOf<String>()

        allMedia.forEach { item ->
            when (item.category) {
                "IMAGE" -> imgSize += item.sizeBytes
                "VIDEO" -> vidSize += item.sizeBytes
                "AUDIO" -> audSize += item.sizeBytes
            }
            if (item.tags.isNotBlank()) {
                item.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { tagsSet.add(it) }
            }
        }

        // Apply Tab Filter
        val tabFiltered = when (tab) {
            MediaCategoryTab.OVERVIEW -> allMedia
            MediaCategoryTab.IMAGES -> allMedia.filter { it.category == "IMAGE" }
            MediaCategoryTab.VIDEOS -> allMedia.filter { it.category == "VIDEO" }
            MediaCategoryTab.AUDIO -> allMedia.filter { it.category == "AUDIO" }
        }

        // Apply Search
        val searchFiltered = if (query.isBlank()) {
            tabFiltered
        } else {
            val q = query.trim().lowercase()
            tabFiltered.filter {
                val ext = it.title.substringAfterLast('.', "")
                it.title.lowercase().contains(q) ||
                        ext.lowercase().contains(q) ||
                        it.tags.lowercase().contains(q)
            }
        }

        // Apply Status Filter
        val now = System.currentTimeMillis()
        val statusFiltered = when (statF) {
            MediaStatusFilter.ALL -> searchFiltered
            MediaStatusFilter.FAVORITES -> searchFiltered.filter { it.isFavorite }
            MediaStatusFilter.TAGGED -> searchFiltered.filter { it.tags.isNotBlank() }
            MediaStatusFilter.RECENT -> searchFiltered.filter {
                val accessed = it.lastAccessedAt ?: it.createdAt
                (now - accessed) < 7 * 24 * 3600 * 1000L
            }
        }

        // Apply Tag Filter
        val tagFiltered = if (tag != null) {
            statusFiltered.filter { it.tags.split(',').map { t -> t.trim() }.contains(tag) }
        } else statusFiltered

        // Apply Size Filter
        val sizeFiltered = when (sizeF) {
            MediaSizeFilter.ALL -> tagFiltered
            MediaSizeFilter.SMALL -> tagFiltered.filter { it.sizeBytes < 5 * 1024 * 1024L }
            MediaSizeFilter.MEDIUM -> tagFiltered.filter { it.sizeBytes in (5 * 1024 * 1024L)..(50 * 1024 * 1024L) }
            MediaSizeFilter.LARGE -> tagFiltered.filter { it.sizeBytes > 50 * 1024 * 1024L }
        }

        // Apply Date Filter
        val cal = Calendar.getInstance()
        val dateFiltered = when (dateF) {
            MediaDateFilter.ALL -> sizeFiltered
            MediaDateFilter.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val startOfDay = cal.timeInMillis
                sizeFiltered.filter { it.createdAt >= startOfDay }
            }
            MediaDateFilter.THIS_WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val startOfWeek = cal.timeInMillis
                sizeFiltered.filter { it.createdAt >= startOfWeek }
            }
            MediaDateFilter.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val startOfMonth = cal.timeInMillis
                sizeFiltered.filter { it.createdAt >= startOfMonth }
            }
        }

        // Apply Sorting
        val sortedList = when (sort) {
            MediaSortOption.NEWEST -> dateFiltered.sortedByDescending { it.createdAt }
            MediaSortOption.OLDEST -> dateFiltered.sortedBy { it.createdAt }
            MediaSortOption.NAME_ASC -> dateFiltered.sortedBy { it.title.lowercase() }
            MediaSortOption.NAME_DESC -> dateFiltered.sortedByDescending { it.title.lowercase() }
            MediaSortOption.SIZE_DESC -> dateFiltered.sortedByDescending { it.sizeBytes }
            MediaSortOption.SIZE_ASC -> dateFiltered.sortedBy { it.sizeBytes }
            MediaSortOption.TYPE -> dateFiltered.sortedWith(compareBy({ it.category }, { it.title.lowercase() }))
        }

        MediaUiState(
            currentTab = tab,
            viewMode = mode,
            searchQuery = query,
            sortOption = sort,
            sizeFilter = sizeF,
            dateFilter = dateF,
            statusFilter = statF,
            selectedTag = tag,
            isMultiSelectMode = multiMode,
            selectedItemIds = selectedIds,
            allMediaItems = allMedia,
            displayedItems = sortedList,
            imageCount = imgCount,
            videoCount = vidCount,
            audioCount = audCount,
            totalMediaSizeBytes = imgSize + vidSize + audSize,
            imageTotalSizeBytes = imgSize,
            videoTotalSizeBytes = vidSize,
            audioTotalSizeBytes = audSize,
            availableTags = tagsSet.sorted(),
            folders = folders,
            recentMediaItems = allMedia.sortedByDescending { it.lastAccessedAt }.take(10),
            favoriteMediaItems = allMedia.filter { it.isFavorite },
            isSearching = searching
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MediaUiState()
    )

    // User Intent Handlers
    fun setTab(tab: MediaCategoryTab) { _currentTab.value = tab }
    fun setViewMode(mode: MediaViewMode) { _viewMode.value = mode }
    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun toggleSearching() {
        _isSearching.value = !_isSearching.value
        if (!_isSearching.value) _searchQuery.value = ""
    }
    fun setSortOption(option: MediaSortOption) { _sortOption.value = option }
    fun setSizeFilter(filter: MediaSizeFilter) { _sizeFilter.value = filter }
    fun setDateFilter(filter: MediaDateFilter) { _dateFilter.value = filter }
    fun setStatusFilter(filter: MediaStatusFilter) { _statusFilter.value = filter }
    fun setSelectedTag(tag: String?) { _selectedTag.value = tag }

    // Multi-select management
    fun toggleMultiSelectMode() {
        val newMode = !_isMultiSelectMode.value
        _isMultiSelectMode.value = newMode
        if (!newMode) _selectedItemIds.value = emptySet()
    }

    fun toggleItemSelection(itemId: Long) {
        val set = _selectedItemIds.value.toMutableSet()
        if (set.contains(itemId)) set.remove(itemId) else set.add(itemId)
        _selectedItemIds.value = set
        if (set.isEmpty() && _isMultiSelectMode.value) {
            // Keep in multi select or cancel if desired
        }
    }

    fun selectAll(items: List<VaultItemEntity>) {
        _selectedItemIds.value = items.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedItemIds.value = emptySet()
        _isMultiSelectMode.value = false
    }

    // Batch Actions
    fun toggleFavorite(item: VaultItemEntity) {
        viewModelScope.launch {
            repository.setFavorite(item, !item.isFavorite)
        }
    }

    fun batchToggleFavorite() {
        val selectedIds = _selectedItemIds.value
        viewModelScope.launch {
            val all = uiState.value.allMediaItems.filter { selectedIds.contains(it.id) }
            val anyNotFav = all.any { !it.isFavorite }
            all.forEach { repository.setFavorite(it, anyNotFav) }
            clearSelection()
        }
    }

    fun batchMoveToFolder(folderId: Long?) {
        val selectedIds = _selectedItemIds.value
        viewModelScope.launch {
            val all = uiState.value.allMediaItems.filter { selectedIds.contains(it.id) }
            all.forEach { repository.moveToFolder(it, folderId) }
            clearSelection()
        }
    }

    fun batchAddTag(tag: String) {
        if (tag.isBlank()) return
        val cleanTag = tag.trim().removePrefix("#")
        val selectedIds = _selectedItemIds.value
        viewModelScope.launch {
            val all = uiState.value.allMediaItems.filter { selectedIds.contains(it.id) }
            all.forEach { item ->
                val currentTags = item.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
                currentTags.add(cleanTag)
                repository.updateTags(item, currentTags.joinToString(","))
            }
            clearSelection()
        }
    }

    fun batchDeleteToTrash() {
        val selectedIds = _selectedItemIds.value
        viewModelScope.launch {
            val all = uiState.value.allMediaItems.filter { selectedIds.contains(it.id) }
            all.forEach { repository.moveToTrash(it) }
            clearSelection()
        }
    }

    fun deleteItemToTrash(item: VaultItemEntity) {
        viewModelScope.launch {
            repository.moveToTrash(item)
        }
    }

    // Export Pipeline
    fun exportItem(context: Context, item: VaultItemEntity, onFinished: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val exportDir = File(context.cacheDir, "vault_export").apply { mkdirs() }
            val destFile = File(exportDir, item.title)
            val exportResult = repository.exportItemToFile(item, destFile)
            withContext(Dispatchers.Main) {
                if (exportResult.isSuccess) {
                    onFinished(true, destFile.absolutePath)
                } else {
                    onFinished(false, exportResult.exceptionOrNull()?.message ?: "Export failed")
                }
            }
        }
    }

    // Bulk Import Pipeline
    fun importUris(context: Context, uris: List<Uri>, folderId: Long? = null) {
        if (uris.isEmpty()) return

        importJob?.cancel()
        importJob = viewModelScope.launch(Dispatchers.IO) {
            _bulkImportStatus.value = BulkImportStatus.Preparing(uris.size)

            var imported = 0
            var failed = 0

            for ((index, uri) in uris.withIndex()) {
                val fileName = repository.queryFileNameFromUri(context, uri) ?: "media_${System.currentTimeMillis()}"
                _bulkImportStatus.value = BulkImportStatus.Importing(index + 1, uris.size, fileName)

                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    if (stream != null) {
                        val result = repository.importFileFromStream(
                            stream = stream,
                            fileName = fileName,
                            folderId = folderId,
                            mimeType = context.contentResolver.getType(uri),
                            duplicateResolution = DuplicateResolution.KEEP_BOTH
                        )
                        if (result.isSuccess) imported++ else failed++
                    } else {
                        failed++
                    }
                } catch (_: Exception) {
                    failed++
                }
            }

            _bulkImportStatus.value = BulkImportStatus.Completed(imported, failed)
        }
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _bulkImportStatus.value = BulkImportStatus.Cancelled
    }

    fun dismissImportStatus() {
        _bulkImportStatus.value = BulkImportStatus.Idle
    }
}
