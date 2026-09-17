package com.example.feature.organization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface SmartOrganizationUiState {
    data object Locked : SmartOrganizationUiState
    data class Success(
        val selectedTab: Int = 0,
        val activeCollection: SmartCollectionType? = null,
        val collectionItems: List<VaultItemEntity> = emptyList(),
        val insights: List<OrganizationInsight> = emptyList(),
        val tagSummaries: List<TagSummary> = emptyList(),
        val folderTree: List<FolderTreeNode> = emptyList(),
        val allFolders: List<VaultFolderEntity> = emptyList(),
        val duplicateGroups: List<DuplicateFileGroup> = emptyList(),
        val largeFileThresholdBytes: Long = 25L * 1024 * 1024, // 25 MB default
        val showEmptyFolders: Boolean = true,
        val activeProposal: OrganizationProposal? = null,
        val canUndo: Boolean = false,
        val selectedItemIds: Set<Long> = emptySet(),
        val statusMessage: String? = null
    ) : SmartOrganizationUiState
}

class SmartOrganizationViewModel(
    val repository: VaultRepository,
    private val sessionManager: SessionSecurityManager
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _activeCollection = MutableStateFlow<SmartCollectionType?>(null)
    val activeCollection: StateFlow<SmartCollectionType?> = _activeCollection.asStateFlow()

    private val _largeFileThreshold = MutableStateFlow(25L * 1024 * 1024) // 25 MB
    val largeFileThreshold: StateFlow<Long> = _largeFileThreshold.asStateFlow()

    private val _showEmptyFolders = MutableStateFlow(true)
    val showEmptyFolders: StateFlow<Boolean> = _showEmptyFolders.asStateFlow()

    private val _activeProposal = MutableStateFlow<OrganizationProposal?>(null)
    val activeProposal: StateFlow<OrganizationProposal?> = _activeProposal.asStateFlow()

    private val _undoStack = MutableStateFlow<List<BatchOperationUndo>>(emptyList())

    private val _selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedItemIds: StateFlow<Set<Long>> = _selectedItemIds.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val uiState: StateFlow<SmartOrganizationUiState> = combine(
        sessionManager.lockState,
        _selectedTab,
        _activeCollection,
        repository.allItems,
        repository.getAllFolders(),
        _largeFileThreshold,
        _showEmptyFolders,
        _activeProposal,
        _undoStack,
        _selectedItemIds,
        _statusMessage
    ) { args: Array<Any?> ->
        val lockState = args[0] as LockState
        if (lockState is LockState.Locked) {
            return@combine SmartOrganizationUiState.Locked
        }

        val tab = args[1] as Int
        val activeCol = args[2] as SmartCollectionType?
        @Suppress("UNCHECKED_CAST")
        val allItems = (args[3] as List<VaultItemEntity>).filter { !it.isTrash }
        @Suppress("UNCHECKED_CAST")
        val allFolders = args[4] as List<VaultFolderEntity>
        val threshold = args[5] as Long
        val showEmpty = args[6] as Boolean
        val proposal = args[7] as OrganizationProposal?
        @Suppress("UNCHECKED_CAST")
        val undoList = args[8] as List<BatchOperationUndo>
        @Suppress("UNCHECKED_CAST")
        val selectedIds = args[9] as Set<Long>
        val statusMsg = args[10] as String?

        val folderMap = allFolders.associateBy { it.id }

        // Compute Rule-based Insights (Local metadata analysis, NOT AI)
        val untaggedCount = allItems.count { it.tags.isBlank() }
        val unfiledCount = allItems.count { it.folderId == null }
        val largeCount = allItems.count { it.sizeBytes >= threshold }
        val now = System.currentTimeMillis()
        val recentImportCount = allItems.count { (now - it.createdAt) < TimeUnit.DAYS.toMillis(7) }

        // Compute Duplicate Groups (Cryptographic SHA-256 match)
        val duplicateGroups = allItems
            .filter { it.checksumSha256.isNotBlank() }
            .groupBy { it.checksumSha256 }
            .filter { it.value.size > 1 }
            .map { (hash, items) ->
                DuplicateFileGroup(
                    checksumSha256 = hash,
                    sizeBytes = items.first().sizeBytes,
                    items = items
                )
            }
            .sortedByDescending { it.sizeBytes * it.items.size }

        val insights = mutableListOf<OrganizationInsight>()
        if (untaggedCount > 0) {
            insights.add(
                OrganizationInsight(
                    type = InsightType.UNTAGGED,
                    title = "$untaggedCount Untagged Files",
                    count = untaggedCount,
                    description = "Assign tags to organize and find files quickly",
                    actionLabel = "Review Untagged"
                )
            )
        }
        if (unfiledCount > 0) {
            insights.add(
                OrganizationInsight(
                    type = InsightType.UNFILED,
                    title = "$unfiledCount Unfiled Files",
                    count = unfiledCount,
                    description = "Loose files in vault root can be moved into folders",
                    actionLabel = "Organize Unfiled"
                )
            )
        }
        if (duplicateGroups.isNotEmpty()) {
            val duplicateFileCount = duplicateGroups.sumOf { it.items.size - 1 }
            insights.add(
                OrganizationInsight(
                    type = InsightType.DUPLICATES,
                    title = "$duplicateFileCount Duplicate Copies",
                    count = duplicateFileCount,
                    description = "Identical SHA-256 content hashes found across ${duplicateGroups.size} groups",
                    actionLabel = "Review Duplicates"
                )
            )
        }
        if (largeCount > 0) {
            insights.add(
                OrganizationInsight(
                    type = InsightType.LARGE_FILES,
                    title = "$largeCount Large Files",
                    count = largeCount,
                    description = "Files larger than ${threshold / (1024 * 1024)} MB consuming storage",
                    actionLabel = "View Large Files"
                )
            )
        }
        if (recentImportCount > 0) {
            insights.add(
                OrganizationInsight(
                    type = InsightType.RECENT_IMPORT,
                    title = "$recentImportCount Recently Imported",
                    count = recentImportCount,
                    description = "Files added within the last 7 days",
                    actionLabel = "View Recent"
                )
            )
        }

        // Tag Summaries
        val tagCountMap = mutableMapOf<String, Int>()
        allItems.forEach { item ->
            item.tags.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { tag ->
                    tagCountMap[tag] = (tagCountMap[tag] ?: 0) + 1
                }
        }
        val tagSummaries = tagCountMap.entries.mapIndexed { idx, (tag, count) ->
            TagSummary(tag = tag, count = count, colorIndex = idx % 6)
        }.sortedByDescending { it.count }

        // Folder Tree
        val folderTree = buildFolderTree(
            folders = allFolders,
            items = allItems,
            showEmpty = showEmpty
        )

        // Active collection items
        val collectionItems = if (activeCol != null) {
            when (activeCol) {
                SmartCollectionType.RECENTLY_ADDED -> allItems.sortedByDescending { it.createdAt }
                SmartCollectionType.RECENTLY_OPENED -> allItems.filter { it.lastAccessedAt != null }
                    .sortedByDescending { it.lastAccessedAt }
                SmartCollectionType.FAVORITES -> allItems.filter { it.isFavorite }
                SmartCollectionType.LARGE_FILES -> allItems.filter { it.sizeBytes >= threshold }
                    .sortedByDescending { it.sizeBytes }
                SmartCollectionType.UNTAGGED -> allItems.filter { it.tags.isBlank() }
                SmartCollectionType.UNFILED -> allItems.filter { it.folderId == null }
                SmartCollectionType.DOCUMENTS -> allItems.filter {
                    it.category == VaultCategory.DOCUMENT.name || it.title.endsWith(".pdf", ignoreCase = true)
                }
                SmartCollectionType.IMAGES -> allItems.filter { it.category == VaultCategory.IMAGE.name }
                SmartCollectionType.VIDEOS -> allItems.filter { it.category == VaultCategory.VIDEO.name }
                SmartCollectionType.AUDIO -> allItems.filter { it.category == VaultCategory.AUDIO.name }
                SmartCollectionType.ARCHIVES -> allItems.filter {
                    it.category == VaultCategory.ZIP.name || it.title.endsWith(".zip", ignoreCase = true)
                }
                SmartCollectionType.CODE -> allItems.filter { it.category == VaultCategory.CODE.name }
                SmartCollectionType.NOTES -> allItems.filter { it.category == VaultCategory.TEXT.name }
            }
        } else {
            emptyList()
        }

        SmartOrganizationUiState.Success(
            selectedTab = tab,
            activeCollection = activeCol,
            collectionItems = collectionItems,
            insights = insights,
            tagSummaries = tagSummaries,
            folderTree = folderTree,
            allFolders = allFolders,
            duplicateGroups = duplicateGroups,
            largeFileThresholdBytes = threshold,
            showEmptyFolders = showEmpty,
            activeProposal = proposal,
            canUndo = undoList.isNotEmpty(),
            selectedItemIds = selectedIds,
            statusMessage = statusMsg
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SmartOrganizationUiState.Success()
    )

    fun selectTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
        _activeCollection.value = null
        _selectedItemIds.value = emptySet()
    }

    fun openCollection(collectionType: SmartCollectionType) {
        _activeCollection.value = collectionType
        _selectedItemIds.value = emptySet()
    }

    fun closeCollection() {
        _activeCollection.value = null
        _selectedItemIds.value = emptySet()
    }

    fun setLargeFileThreshold(bytes: Long) {
        _largeFileThreshold.value = bytes
    }

    fun toggleShowEmptyFolders() {
        _showEmptyFolders.value = !_showEmptyFolders.value
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    // ====================================================================
    // Tag Management Operations
    // ====================================================================

    fun createTag(tagName: String) {
        // Ready for use in assignment
        _statusMessage.value = "Tag '$tagName' created"
    }

    fun renameTag(oldTag: String, newTag: String) {
        viewModelScope.launch {
            val result = repository.renameTag(oldTag, newTag)
            if (result.isSuccess) {
                _statusMessage.value = "Renamed '$oldTag' to '$newTag' across ${result.getOrNull()} files"
            }
        }
    }

    fun deleteTag(tagToDelete: String) {
        viewModelScope.launch {
            val result = repository.deleteTag(tagToDelete)
            if (result.isSuccess) {
                _statusMessage.value = "Deleted tag '$tagToDelete' from ${result.getOrNull()} files"
            }
        }
    }

    fun mergeTags(sourceTag: String, targetTag: String) {
        viewModelScope.launch {
            val result = repository.mergeTags(sourceTag, targetTag)
            if (result.isSuccess) {
                _statusMessage.value = "Merged '$sourceTag' into '$targetTag' across ${result.getOrNull()} files"
            }
        }
    }

    fun assignTagToItems(itemIds: Set<Long>, tag: String) {
        viewModelScope.launch {
            val items = repository.getAllItemsList().filter { itemIds.contains(it.id) }
            repository.batchAddTag(items, tag)
            _statusMessage.value = "Assigned tag '$tag' to ${items.size} files"
            _selectedItemIds.value = emptySet()
        }
    }

    fun removeTagFromItems(itemIds: Set<Long>, tag: String) {
        viewModelScope.launch {
            val items = repository.getAllItemsList().filter { itemIds.contains(it.id) }
            repository.batchRemoveTag(items, tag)
            _statusMessage.value = "Removed tag '$tag' from ${items.size} files"
            _selectedItemIds.value = emptySet()
        }
    }

    // ====================================================================
    // Folder Management Operations
    // ====================================================================

    fun createFolder(name: String, parentId: Long?) {
        viewModelScope.launch {
            val result = repository.createFolder(name, parentId)
            if (result.isSuccess) {
                _statusMessage.value = "Folder '$name' created"
            }
        }
    }

    fun renameFolder(folderId: Long, newName: String) {
        viewModelScope.launch {
            val result = repository.renameFolder(folderId, newName)
            if (result.isSuccess) {
                _statusMessage.value = "Folder renamed to '$newName'"
            }
        }
    }

    fun moveFolder(folderId: Long, newParentId: Long?) {
        viewModelScope.launch {
            val result = repository.moveFolder(folderId, newParentId)
            if (result.isSuccess) {
                _statusMessage.value = "Folder moved successfully"
            } else {
                _statusMessage.value = result.exceptionOrNull()?.message ?: "Failed to move folder"
            }
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            val result = repository.deleteFolder(folderId)
            if (result.isSuccess) {
                _statusMessage.value = "Folder deleted"
            }
        }
    }

    // ====================================================================
    // Bulk Organization Tools & Rollback
    // ====================================================================

    fun generateOrganizeByTypeProposal() {
        viewModelScope.launch(Dispatchers.IO) {
            val unfiledItems = repository.getAllItemsList().filter { !it.isTrash && it.folderId == null }
            if (unfiledItems.isEmpty()) {
                _statusMessage.value = "No unfiled files found to organize"
                return@launch
            }

            val proposedMoves = unfiledItems.map { item ->
                val targetName = when (item.category) {
                    VaultCategory.DOCUMENT.name -> "Documents"
                    VaultCategory.IMAGE.name -> "Images"
                    VaultCategory.VIDEO.name -> "Videos"
                    VaultCategory.AUDIO.name -> "Audio"
                    VaultCategory.ZIP.name -> "Archives"
                    VaultCategory.CODE.name -> "Code"
                    VaultCategory.TEXT.name -> "Notes"
                    else -> "Other"
                }

                ProposedMove(
                    item = item,
                    currentFolderName = "Vault Root",
                    targetFolderName = targetName,
                    targetParentId = null
                )
            }

            _activeProposal.value = OrganizationProposal(
                title = "Organize by File Type",
                description = "Move ${proposedMoves.size} unfiled files into category folders (Documents, Images, Code, etc.)",
                moves = proposedMoves
            )
        }
    }

    fun generateOrganizeByDateProposal() {
        viewModelScope.launch(Dispatchers.IO) {
            val unfiledItems = repository.getAllItemsList().filter { !it.isTrash && it.folderId == null }
            if (unfiledItems.isEmpty()) {
                _statusMessage.value = "No unfiled files found to organize"
                return@launch
            }

            val sdf = SimpleDateFormat("yyyy/MMMM", Locale.getDefault())
            val proposedMoves = unfiledItems.map { item ->
                val dateFolder = sdf.format(Date(item.createdAt))
                ProposedMove(
                    item = item,
                    currentFolderName = "Vault Root",
                    targetFolderName = dateFolder,
                    targetParentId = null
                )
            }

            _activeProposal.value = OrganizationProposal(
                title = "Organize by Date Created",
                description = "Move ${proposedMoves.size} unfiled files into Year/Month folders (e.g. 2026/September)",
                moves = proposedMoves
            )
        }
    }

    fun dismissProposal() {
        _activeProposal.value = null
    }

    fun applyProposal(proposal: OrganizationProposal) {
        viewModelScope.launch(Dispatchers.IO) {
            val allFolders = repository.getAllFoldersList().toMutableList()
            val movesList = mutableListOf<Pair<Long, Long?>>()

            proposal.moves.forEach { move ->
                // Ensure target folder exists
                var targetFolder = allFolders.firstOrNull { it.name == move.targetFolderName && it.parentId == move.targetParentId }
                if (targetFolder == null) {
                    val createResult = repository.createFolder(move.targetFolderName, move.targetParentId)
                    if (createResult.isSuccess) {
                        targetFolder = repository.getAllFoldersList().firstOrNull {
                            it.name == move.targetFolderName && it.parentId == move.targetParentId
                        }
                        targetFolder?.let { allFolders.add(it) }
                    }
                }

                if (targetFolder != null) {
                    movesList.add(Pair(move.item.id, move.item.folderId))
                    repository.moveItemToFolder(move.item, targetFolder.id)
                }
            }

            // Save undo entry
            val undo = BatchOperationUndo(
                description = proposal.title,
                moves = movesList
            )
            val stack = _undoStack.value.toMutableList()
            stack.add(undo)
            _undoStack.value = stack

            _activeProposal.value = null
            _statusMessage.value = "Successfully organized ${movesList.size} files"
        }
    }

    fun rollbackLastBatchAction() {
        viewModelScope.launch(Dispatchers.IO) {
            val stack = _undoStack.value.toMutableList()
            if (stack.isEmpty()) return@launch

            val lastAction = stack.removeAt(stack.size - 1)
            _undoStack.value = stack

            val allItemsMap = repository.getAllItemsList().associateBy { it.id }
            var restoredCount = 0

            lastAction.moves.forEach { (itemId, oldFolderId) ->
                val item = allItemsMap[itemId]
                if (item != null) {
                    repository.moveItemToFolder(item, oldFolderId)
                    restoredCount++
                }
            }

            _statusMessage.value = "Rolled back ${lastAction.description} ($restoredCount files restored)"
        }
    }

    // ====================================================================
    // Duplicate Review & Safe Deletion
    // ====================================================================

    fun deleteDuplicateItem(item: VaultItemEntity) {
        viewModelScope.launch {
            repository.moveToTrash(item)
            _statusMessage.value = "Moved duplicate '${item.title}' to trash"
        }
    }

    // ====================================================================
    // Selection in Smart Collection
    // ====================================================================

    fun toggleItemSelection(itemId: Long) {
        val current = _selectedItemIds.value.toMutableSet()
        if (current.contains(itemId)) {
            current.remove(itemId)
        } else {
            current.add(itemId)
        }
        _selectedItemIds.value = current
    }

    fun selectAll(items: List<VaultItemEntity>) {
        _selectedItemIds.value = items.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedItemIds.value = emptySet()
    }

    fun batchMoveSelected(targetFolderId: Long?) {
        val ids = _selectedItemIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val items = repository.getAllItemsList().filter { ids.contains(it.id) }
            repository.batchMove(items, targetFolderId)
            clearSelection()
            _statusMessage.value = "Moved ${items.size} files"
        }
    }

    fun batchTrashSelected() {
        val ids = _selectedItemIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val items = repository.getAllItemsList().filter { ids.contains(it.id) }
            repository.batchDelete(items)
            clearSelection()
            _statusMessage.value = "Moved ${items.size} files to trash"
        }
    }

    fun batchFavoriteSelected(isFav: Boolean) {
        val ids = _selectedItemIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val items = repository.getAllItemsList().filter { ids.contains(it.id) }
            repository.batchFavorite(items, isFav)
            clearSelection()
        }
    }

    // ====================================================================
    // Folder Tree Builder
    // ====================================================================

    private fun buildFolderTree(
        folders: List<VaultFolderEntity>,
        items: List<VaultItemEntity>,
        showEmpty: Boolean
    ): List<FolderTreeNode> {
        val itemsByFolder = items.groupBy { it.folderId }
        val foldersByParent = folders.groupBy { it.parentId }

        fun buildNode(folder: VaultFolderEntity, level: Int): FolderTreeNode? {
            val directItems = itemsByFolder[folder.id] ?: emptyList()
            val directCount = directItems.size
            val directSize = directItems.sumOf { it.sizeBytes }

            val childFolders = foldersByParent[folder.id] ?: emptyList()
            val childNodes = childFolders.mapNotNull { buildNode(it, level + 1) }

            val totalCount = directCount + childNodes.sumOf { it.totalItemCount }
            val totalSize = directSize + childNodes.sumOf { it.totalSizeBytes }

            if (!showEmpty && totalCount == 0) {
                return null
            }

            return FolderTreeNode(
                folder = folder,
                level = level,
                children = childNodes,
                directItemCount = directCount,
                directSizeBytes = directSize,
                totalItemCount = totalCount,
                totalSizeBytes = totalSize
            )
        }

        val rootFolders = foldersByParent[null] ?: emptyList()
        return rootFolders.mapNotNull { buildNode(it, 0) }
    }
}
