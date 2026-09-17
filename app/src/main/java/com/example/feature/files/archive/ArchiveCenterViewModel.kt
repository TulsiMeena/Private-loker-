package com.example.feature.files.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.security.SessionSecurityManager
import com.example.core.storage.VaultCategory
import com.example.feature.files.operations.FileOperation
import com.example.feature.files.operations.FileOperationManager
import com.example.feature.files.operations.FileOperationStatus
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
import java.io.InputStream
import java.util.Locale

enum class ArchiveCenterSection(val title: String) {
    ALL("All Archives"),
    RECENT("Recent"),
    FAVORITES("Favorites"),
    FOLDERS("Folders"),
    TAGS("Tags")
}

enum class ArchiveSortOption(val title: String) {
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    DATE_DESC("Date (Newest)"),
    DATE_ASC("Date (Oldest)"),
    SIZE_DESC("Size (Largest)"),
    SIZE_ASC("Size (Smallest)")
}

enum class ArchiveViewMode {
    LIST,
    GRID
}

data class ArchiveDashboardStats(
    val totalArchives: Int = 0,
    val totalEncryptedSizeBytes: Long = 0L,
    val totalFavorites: Int = 0,
    val storageSavedBytes: Long = 0L
)

class ArchiveCenterViewModel(
    private val repository: VaultRepository,
    private val sessionManager: SessionSecurityManager,
    val zipManager: ZipVaultManager,
    val operationManager: FileOperationManager
) : ViewModel() {

    private val _currentSection = MutableStateFlow(ArchiveCenterSection.ALL)
    val currentSection: StateFlow<ArchiveCenterSection> = _currentSection.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFolderId = MutableStateFlow<Long?>(null)
    val selectedFolderId: StateFlow<Long?> = _selectedFolderId.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val _sortOption = MutableStateFlow(ArchiveSortOption.DATE_DESC)
    val sortOption: StateFlow<ArchiveSortOption> = _sortOption.asStateFlow()

    private val _viewMode = MutableStateFlow(ArchiveViewMode.LIST)
    val viewMode: StateFlow<ArchiveViewMode> = _viewMode.asStateFlow()

    private val _selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedItemIds: StateFlow<Set<Long>> = _selectedItemIds.asStateFlow()

    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val folders: StateFlow<List<VaultFolderEntity>> = repository.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTags: StateFlow<List<String>> = repository.getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val operations: StateFlow<List<FileOperation>> = operationManager.operations

    // Raw archive items flow
    private val rawArchivesFlow = repository.getItemsByCategory(VaultCategory.ZIP)

    // Filtered archives
    val filteredArchives: StateFlow<List<VaultItemEntity>> = combine(
        combine(rawArchivesFlow, _currentSection, _searchQuery) { archives, section, query ->
            Triple(archives, section, query)
        },
        combine(_selectedFolderId, _selectedTag, _sortOption) { folderId, tag, sort ->
            Triple(folderId, tag, sort)
        }
    ) { (archives, section, query), (folderId, tag, sort) ->
        val bySection = when (section) {
            ArchiveCenterSection.ALL -> archives
            ArchiveCenterSection.RECENT -> archives.sortedByDescending { it.modifiedAt }.take(30)
            ArchiveCenterSection.FAVORITES -> archives.filter { it.isFavorite }
            ArchiveCenterSection.FOLDERS -> {
                if (folderId == null) archives.filter { it.folderId == null }
                else archives.filter { it.folderId == folderId }
            }
            ArchiveCenterSection.TAGS -> {
                if (tag.isNullOrBlank()) archives
                else archives.filter { it.tags.split(",").map { t -> t.trim().lowercase(Locale.ROOT) }.contains(tag.lowercase(Locale.ROOT)) }
            }
        }

        val byQuery = if (query.isBlank()) {
            bySection
        } else {
            bySection.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.tags.contains(query, ignoreCase = true)
            }
        }

        when (sort) {
            ArchiveSortOption.NAME_ASC -> byQuery.sortedBy { it.title.lowercase(Locale.ROOT) }
            ArchiveSortOption.NAME_DESC -> byQuery.sortedByDescending { it.title.lowercase(Locale.ROOT) }
            ArchiveSortOption.DATE_DESC -> byQuery.sortedByDescending { it.modifiedAt }
            ArchiveSortOption.DATE_ASC -> byQuery.sortedBy { it.modifiedAt }
            ArchiveSortOption.SIZE_DESC -> byQuery.sortedByDescending { it.sizeBytes }
            ArchiveSortOption.SIZE_ASC -> byQuery.sortedBy { it.sizeBytes }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<ArchiveDashboardStats> = rawArchivesFlow.combine(_sortOption) { archives, _ ->
        val total = archives.size
        val totalEncrypted = archives.sumOf { it.encryptedSizeBytes.coerceAtLeast(it.sizeBytes) }
        val favorites = archives.count { it.isFavorite }
        val estimatedSaved = (totalEncrypted * 0.25).toLong() // approximate compression savings
        ArchiveDashboardStats(
            totalArchives = total,
            totalEncryptedSizeBytes = totalEncrypted,
            totalFavorites = favorites,
            storageSavedBytes = estimatedSaved
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ArchiveDashboardStats())

    fun selectSection(section: ArchiveCenterSection) {
        _currentSection.value = section
        if (section != ArchiveCenterSection.FOLDERS) _selectedFolderId.value = null
        if (section != ArchiveCenterSection.TAGS) _selectedTag.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFolder(folderId: Long?) {
        _selectedFolderId.value = folderId
        _currentSection.value = ArchiveCenterSection.FOLDERS
    }

    fun selectTag(tag: String?) {
        _selectedTag.value = tag
        _currentSection.value = ArchiveCenterSection.TAGS
    }

    fun setSortOption(option: ArchiveSortOption) {
        _sortOption.value = option
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == ArchiveViewMode.LIST) ArchiveViewMode.GRID else ArchiveViewMode.LIST
    }

    fun toggleMultiSelect() {
        val next = !_isMultiSelectMode.value
        _isMultiSelectMode.value = next
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
        if (current.isNotEmpty()) {
            _isMultiSelectMode.value = true
        }
    }

    fun selectAll() {
        val allIds = filteredArchives.value.map { it.id }.toSet()
        _selectedItemIds.value = allIds
        _isMultiSelectMode.value = true
    }

    fun clearSelection() {
        _selectedItemIds.value = emptySet()
        _isMultiSelectMode.value = false
    }

    fun toggleFavorite(item: VaultItemEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(item)
        }
    }

    fun deleteArchive(item: VaultItemEntity) {
        viewModelScope.launch {
            val res = repository.moveToTrash(item)
            if (res.isSuccess) {
                _statusMessage.value = "Moved ${item.title} to trash"
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    /**
     * Extracts an archive with full operation tracking.
     */
    fun extractArchive(
        item: VaultItemEntity,
        option: ArchiveExtractionOption = ArchiveExtractionOption.EXTRACT_HERE,
        customFolderName: String? = null,
        targetFolderId: Long? = null
    ) {
        viewModelScope.launch {
            var isCancelledFlag = false
            val opId = operationManager.enqueueOperation(
                type = FileOperationType.EXTRACTION,
                title = "Extracting ${item.title}",
                totalItems = 1,
                cancelAction = { isCancelledFlag = true }
            )

            try {
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
                    val count = result.getOrDefault(0)
                    operationManager.completeOperation(opId)
                    _statusMessage.value = "Extracted $count files from ${item.title} into vault"
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Extraction failed"
                    operationManager.failOperation(opId, err)
                    _statusMessage.value = err
                }
            } catch (e: Exception) {
                operationManager.failOperation(opId, e.message ?: "Extraction failed")
                _statusMessage.value = e.message
            }
        }
    }

    /**
     * Creates an archive from vault items with progress tracking.
     */
    fun createArchive(
        items: List<VaultItemEntity>,
        archiveName: String,
        targetFolderId: Long?,
        compressionLevel: ArchiveCompressionLevel = ArchiveCompressionLevel.NORMAL,
        conflictOption: DuplicateConflictOption = DuplicateConflictOption.KEEP_BOTH
    ) {
        viewModelScope.launch {
            var isCancelledFlag = false
            val opId = operationManager.enqueueOperation(
                type = FileOperationType.ARCHIVE,
                title = "Creating $archiveName",
                totalItems = items.size,
                cancelAction = { isCancelledFlag = true }
            )

            try {
                val result = zipManager.createArchiveFromItems(
                    items = items,
                    archiveName = archiveName,
                    targetFolderId = targetFolderId,
                    compressionLevel = compressionLevel,
                    conflictOption = conflictOption,
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
                    _statusMessage.value = "Created archive ${result.getOrNull()?.title}"
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Archive creation failed"
                    operationManager.failOperation(opId, err)
                    _statusMessage.value = err
                }
            } catch (e: Exception) {
                operationManager.failOperation(opId, e.message ?: "Archive creation failed")
                _statusMessage.value = e.message
            }
        }
    }

    /**
     * Bulk Extract selected archive items.
     */
    fun bulkExtractSelected(extractSeparately: Boolean = true, targetFolderId: Long? = null) {
        val selectedIds = _selectedItemIds.value
        val itemsToExtract = filteredArchives.value.filter { selectedIds.contains(it.id) }
        if (itemsToExtract.isEmpty()) return

        clearSelection()

        viewModelScope.launch {
            var isCancelledFlag = false
            val opId = operationManager.enqueueOperation(
                type = FileOperationType.EXTRACTION,
                title = "Bulk Extracting ${itemsToExtract.size} Archives",
                totalItems = itemsToExtract.size,
                cancelAction = { isCancelledFlag = true }
            )

            val result = zipManager.bulkExtract(
                items = itemsToExtract,
                targetFolderId = targetFolderId,
                extractSeparately = extractSeparately,
                onProgress = { state ->
                    operationManager.updateProgress(
                        id = opId,
                        progress = state.progress,
                        currentStep = "Archive ${state.currentIndex}/${state.totalCount}: ${state.currentFile}",
                        itemsProcessed = state.currentIndex,
                        totalItems = state.totalCount
                    )
                },
                isCancelled = { isCancelledFlag }
            )

            if (result.isSuccess) {
                operationManager.completeOperation(opId)
                _statusMessage.value = "Bulk extracted ${result.getOrDefault(0)} files into vault"
            } else {
                val err = result.exceptionOrNull()?.message ?: "Bulk extraction failed"
                operationManager.failOperation(opId, err)
                _statusMessage.value = err
            }
        }
    }

    /**
     * Bulk Archive selected items.
     */
    fun bulkArchiveSelected(archiveName: String, compressionLevel: ArchiveCompressionLevel) {
        val selectedIds = _selectedItemIds.value
        val items = filteredArchives.value.filter { selectedIds.contains(it.id) }
        if (items.isEmpty()) return

        clearSelection()
        createArchive(
            items = items,
            archiveName = archiveName,
            targetFolderId = _selectedFolderId.value,
            compressionLevel = compressionLevel
        )
    }

    /**
     * Bulk delete (move to trash).
     */
    fun bulkDeleteSelected() {
        val selectedIds = _selectedItemIds.value
        val items = filteredArchives.value.filter { selectedIds.contains(it.id) }
        if (items.isEmpty()) return

        clearSelection()
        viewModelScope.launch {
            var deleted = 0
            items.forEach { item ->
                if (repository.moveToTrash(item).isSuccess) {
                    deleted++
                }
            }
            _statusMessage.value = "Moved $deleted archives to trash"
        }
    }

    /**
     * Bulk favorite/unfavorite.
     */
    fun bulkFavoriteSelected(favorite: Boolean = true) {
        val selectedIds = _selectedItemIds.value
        val items = filteredArchives.value.filter { selectedIds.contains(it.id) }
        if (items.isEmpty()) return

        clearSelection()
        viewModelScope.launch {
            items.forEach { item ->
                if (item.isFavorite != favorite) {
                    repository.toggleFavorite(item)
                }
            }
            _statusMessage.value = "Updated favorites for ${items.size} items"
        }
    }

    /**
     * Bulk move to folder.
     */
    fun bulkMoveSelected(targetFolderId: Long?) {
        val selectedIds = _selectedItemIds.value
        val items = filteredArchives.value.filter { selectedIds.contains(it.id) }
        if (items.isEmpty()) return

        clearSelection()
        viewModelScope.launch {
            var moved = 0
            items.forEach { item ->
                if (repository.moveItemToFolder(item, targetFolderId).isSuccess) {
                    moved++
                }
            }
            _statusMessage.value = "Moved $moved archives to folder"
        }
    }

    /**
     * Imports an external archive from InputStream.
     */
    fun importArchiveStream(
        title: String,
        inputStream: InputStream,
        expectedSizeBytes: Long = -1L
    ) {
        viewModelScope.launch {
            var isCancelledFlag = false
            val opId = operationManager.enqueueOperation(
                type = FileOperationType.IMPORT,
                title = "Importing $title",
                cancelAction = { isCancelledFlag = true }
            )

            try {
                val cleanTitle = if (title.endsWith(".zip", ignoreCase = true)) title else "$title.zip"
                val res = repository.importItem(
                    title = cleanTitle,
                    category = VaultCategory.ZIP,
                    mimeType = "application/zip",
                    inputStream = inputStream,
                    folderId = _selectedFolderId.value,
                    expectedSizeBytes = expectedSizeBytes,
                    onProgress = { processed, total, phase ->
                        val prog = if (total > 0) processed.toFloat() / total.toFloat() else 0.5f
                        operationManager.updateProgress(opId, prog, "$phase: ${(processed / 1024)} KB")
                    },
                    isCancelled = { isCancelledFlag }
                )

                if (res.isSuccess) {
                    operationManager.completeOperation(opId)
                    _statusMessage.value = "Imported $cleanTitle into vault"
                } else {
                    val err = res.exceptionOrNull()?.message ?: "Import failed"
                    operationManager.failOperation(opId, err)
                    _statusMessage.value = err
                }
            } catch (e: Exception) {
                operationManager.failOperation(opId, e.message ?: "Import failed")
                _statusMessage.value = e.message
            }
        }
    }
}
