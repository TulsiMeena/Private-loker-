package com.example.feature.vault.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.lifecycle.AutoCleanRetention
import com.example.core.lifecycle.DataLifecycleManager
import com.example.core.lifecycle.EmptyTrashReport
import com.example.core.lifecycle.OrphanScanResult
import com.example.core.lifecycle.RestoreConflictResolution
import com.example.core.lifecycle.RestoreOutcome
import com.example.core.lifecycle.StorageSummary
import com.example.core.security.LockState
import com.example.core.security.SessionSecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Display model combining the vault item with its original folder name for instant visual comprehension.
 */
data class VaultTrashItem(
    val item: VaultItemEntity,
    val originalFolderName: String
)

/**
 * Complete UI state for the Encrypted Secure Trash screen.
 */
data class TrashUiState(
    val items: List<VaultTrashItem> = emptyList(),
    val selectedItemIds: Set<Long> = emptySet(),
    val storageSummary: StorageSummary = StorageSummary(),
    val autoCleanPolicy: AutoCleanRetention = AutoCleanRetention.DAYS_30,
    val orphanScanResult: OrphanScanResult = OrphanScanResult(),
    val conflictPair: Pair<VaultItemEntity, VaultItemEntity>? = null,
    val itemsToPermanentDelete: List<VaultItemEntity>? = null,
    val showEmptyTrashDialog: Boolean = false,
    val showAutoCleanDialog: Boolean = false,
    val showOrphanReviewDialog: Boolean = false,
    val statusMessage: String? = null,
    val isProcessing: Boolean = false,
    val isUnlocked: Boolean = false
) {
    val isSelectionMode: Boolean get() = selectedItemIds.isNotEmpty()
    val totalTrashBytes: Long get() = items.sumOf { it.item.sizeBytes }
}

class TrashViewModel(
    private val repository: VaultRepository,
    private val lifecycleManager: DataLifecycleManager,
    private val sessionManager: SessionSecurityManager
) : ViewModel() {

    private val _selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _storageSummary = MutableStateFlow(StorageSummary())
    private val _autoCleanPolicy = MutableStateFlow(lifecycleManager.getAutoCleanPolicy())
    private val _orphanScanResult = MutableStateFlow(OrphanScanResult())
    private val _conflictPair = MutableStateFlow<Pair<VaultItemEntity, VaultItemEntity>?>(null)
    private val _itemsToPermanentDelete = MutableStateFlow<List<VaultItemEntity>?>(null)
    private val _showEmptyTrashDialog = MutableStateFlow(false)
    private val _showAutoCleanDialog = MutableStateFlow(false)
    private val _showOrphanReviewDialog = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _isProcessing = MutableStateFlow(false)

    init {
        // Refresh storage summary and orphan detection
        refreshStorageAndIntegrity()

        // Clear selections and dismiss dialogs when locked
        viewModelScope.launch {
            sessionManager.lockState.collect { lockState ->
                if (lockState !is LockState.Unlocked) {
                    _selectedItemIds.value = emptySet()
                    _conflictPair.value = null
                    _itemsToPermanentDelete.value = null
                    _showEmptyTrashDialog.value = false
                    _showAutoCleanDialog.value = false
                    _showOrphanReviewDialog.value = false
                    _statusMessage.value = null
                }
            }
        }
    }

    val uiState: StateFlow<TrashUiState> = combine(
        sessionManager.lockState,
        repository.trashItems,
        repository.getAllFolders(),
        _selectedItemIds,
        _storageSummary,
        _autoCleanPolicy,
        _orphanScanResult,
        _conflictPair,
        _itemsToPermanentDelete,
        _showEmptyTrashDialog,
        _showAutoCleanDialog,
        _showOrphanReviewDialog,
        _statusMessage,
        _isProcessing
    ) { args ->
        val lockState = args[0] as LockState
        val isUnlocked = lockState is LockState.Unlocked

        if (!isUnlocked) {
            return@combine TrashUiState(isUnlocked = false)
        }

        @Suppress("UNCHECKED_CAST")
        val rawTrashItems = args[1] as List<VaultItemEntity>
        @Suppress("UNCHECKED_CAST")
        val allFolders = args[2] as List<VaultFolderEntity>
        val folderMap = allFolders.associateBy { it.id }

        val displayItems = rawTrashItems.map { item ->
            val folderName = item.originalFolderId?.let { folderId ->
                folderMap[folderId]?.name ?: "Deleted Folder"
            } ?: "Root Vault"
            VaultTrashItem(item = item, originalFolderName = folderName)
        }

        @Suppress("UNCHECKED_CAST")
        val selectedIds = args[3] as Set<Long>
        val storageSummary = args[4] as StorageSummary
        val policy = args[5] as AutoCleanRetention
        val orphanResult = args[6] as OrphanScanResult
        @Suppress("UNCHECKED_CAST")
        val conflictPair = args[7] as Pair<VaultItemEntity, VaultItemEntity>?
        @Suppress("UNCHECKED_CAST")
        val itemsToPermDelete = args[8] as List<VaultItemEntity>?
        val showEmpty = args[9] as Boolean
        val showAutoClean = args[10] as Boolean
        val showOrphanReview = args[11] as Boolean
        val statusMsg = args[12] as String?
        val isProc = args[13] as Boolean

        TrashUiState(
            items = displayItems,
            selectedItemIds = selectedIds,
            storageSummary = storageSummary,
            autoCleanPolicy = policy,
            orphanScanResult = orphanResult,
            conflictPair = conflictPair,
            itemsToPermanentDelete = itemsToPermDelete,
            showEmptyTrashDialog = showEmpty,
            showAutoCleanDialog = showAutoClean,
            showOrphanReviewDialog = showOrphanReview,
            statusMessage = statusMsg,
            isProcessing = isProc,
            isUnlocked = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TrashUiState()
    )

    fun toggleSelection(itemId: Long) {
        val current = _selectedItemIds.value.toMutableSet()
        if (current.contains(itemId)) {
            current.remove(itemId)
        } else {
            current.add(itemId)
        }
        _selectedItemIds.value = current
    }

    fun selectAll() {
        val allIds = uiState.value.items.map { it.item.id }.toSet()
        _selectedItemIds.value = allIds
    }

    fun clearSelection() {
        _selectedItemIds.value = emptySet()
    }

    fun refreshStorageAndIntegrity() {
        viewModelScope.launch {
            val summary = lifecycleManager.getStorageSummary()
            _storageSummary.value = summary
            val orphans = lifecycleManager.scanForOrphansAndInconsistencies()
            _orphanScanResult.value = orphans
        }
    }

    fun restoreItem(item: VaultItemEntity) {
        if (sessionManager.lockState.value !is LockState.Unlocked) return

        viewModelScope.launch {
            _isProcessing.value = true
            when (val outcome = repository.restoreFromTrash(item)) {
                is RestoreOutcome.Success -> {
                    _statusMessage.value = if (outcome.renamed) {
                        "Restored '${outcome.item.title}' (renamed to avoid conflict)"
                    } else if (outcome.restoredToRoot) {
                        "Restored '${outcome.item.title}' to Root (original folder no longer exists)"
                    } else {
                        "Restored '${outcome.item.title}'"
                    }
                    refreshStorageAndIntegrity()
                }
                is RestoreOutcome.Conflict -> {
                    _conflictPair.value = Pair(outcome.itemToRestore, outcome.existingConflictItem)
                }
                is RestoreOutcome.Failure -> {
                    _statusMessage.value = "Failed to restore: ${outcome.reason}"
                }
            }
            _isProcessing.value = false
        }
    }

    fun resolveConflictAndRestore(resolution: RestoreConflictResolution) {
        val pair = _conflictPair.value ?: return
        _conflictPair.value = null

        viewModelScope.launch {
            _isProcessing.value = true
            when (val outcome = repository.restoreFromTrash(pair.first, resolution)) {
                is RestoreOutcome.Success -> {
                    _statusMessage.value = when (resolution) {
                        RestoreConflictResolution.KEEP_EXISTING -> "Restore cancelled: existing file kept"
                        RestoreConflictResolution.REPLACE -> "Restored and replaced '${outcome.item.title}'"
                        RestoreConflictResolution.KEEP_BOTH -> "Restored '${outcome.item.title}' alongside existing file"
                    }
                    refreshStorageAndIntegrity()
                }
                is RestoreOutcome.Conflict -> {}
                is RestoreOutcome.Failure -> {
                    _statusMessage.value = "Failed to restore: ${outcome.reason}"
                }
            }
            _isProcessing.value = false
        }
    }

    fun dismissConflictDialog() {
        _conflictPair.value = null
    }

    fun batchRestoreSelected(resolution: RestoreConflictResolution = RestoreConflictResolution.KEEP_BOTH) {
        val selectedIds = _selectedItemIds.value
        if (selectedIds.isEmpty()) return

        val itemsToRestore = uiState.value.items
            .filter { selectedIds.contains(it.item.id) }
            .map { it.item }

        viewModelScope.launch {
            _isProcessing.value = true
            val outcomes = repository.batchRestore(itemsToRestore, resolution)
            val successCount = outcomes.count { it is RestoreOutcome.Success }
            _statusMessage.value = "Restored $successCount of ${itemsToRestore.size} item(s)"
            clearSelection()
            refreshStorageAndIntegrity()
            _isProcessing.value = false
        }
    }

    fun requestPermanentDelete(items: List<VaultItemEntity>) {
        _itemsToPermanentDelete.value = items
    }

    fun dismissPermanentDeleteDialog() {
        _itemsToPermanentDelete.value = null
    }

    fun executePermanentDelete() {
        val items = _itemsToPermanentDelete.value ?: return
        _itemsToPermanentDelete.value = null

        viewModelScope.launch {
            _isProcessing.value = true
            val result = repository.batchPermanentDelete(items)
            result.onSuccess { report ->
                if (report.isFullSuccess) {
                    _statusMessage.value = "Permanently shredded ${report.deletedCount} item(s)"
                } else {
                    _statusMessage.value = "Shredded ${report.deletedCount} item(s). Failed: ${report.failedCount}"
                }
            }.onFailure { e ->
                _statusMessage.value = "Delete error: ${e.message}"
            }
            clearSelection()
            refreshStorageAndIntegrity()
            _isProcessing.value = false
        }
    }

    fun requestEmptyTrash() {
        _showEmptyTrashDialog.value = true
    }

    fun dismissEmptyTrashDialog() {
        _showEmptyTrashDialog.value = false
    }

    fun executeEmptyTrash() {
        _showEmptyTrashDialog.value = false
        viewModelScope.launch {
            _isProcessing.value = true
            val result = repository.emptyTrash()
            result.onSuccess { report ->
                if (report.isFullSuccess) {
                    _statusMessage.value = "Secure Trash emptied: ${report.deletedCount} items shredded"
                } else {
                    _statusMessage.value = "Partially emptied: ${report.deletedCount} purged, ${report.failedCount} retained"
                }
            }.onFailure { e ->
                _statusMessage.value = "Failed to empty trash: ${e.message}"
            }
            clearSelection()
            refreshStorageAndIntegrity()
            _isProcessing.value = false
        }
    }

    fun showAutoCleanSettings() {
        _showAutoCleanDialog.value = true
    }

    fun dismissAutoCleanSettings() {
        _showAutoCleanDialog.value = false
    }

    fun updateAutoCleanPolicy(policy: AutoCleanRetention) {
        lifecycleManager.setAutoCleanPolicy(policy)
        _autoCleanPolicy.value = policy
        _showAutoCleanDialog.value = false
        _statusMessage.value = "Auto-Clean set to ${policy.label}"

        // Run auto-clean check immediately if retention policy is enabled
        viewModelScope.launch {
            val result = lifecycleManager.executeAutoClean()
            result.onSuccess { cleaned ->
                if (cleaned > 0) {
                    _statusMessage.value = "Auto-cleaned $cleaned expired item(s)"
                }
                refreshStorageAndIntegrity()
            }
        }
    }

    fun showOrphanReview() {
        _showOrphanReviewDialog.value = true
    }

    fun dismissOrphanReview() {
        _showOrphanReviewDialog.value = false
    }

    fun recoverOrphanContainers() {
        val files = _orphanScanResult.value.unlinkedContainers
        if (files.isEmpty()) return

        viewModelScope.launch {
            _isProcessing.value = true
            val result = lifecycleManager.recoverOrphanedContainers(files)
            result.onSuccess { count ->
                _statusMessage.value = "Recovered $count unlinked container(s) into Root Vault"
            }.onFailure { e ->
                _statusMessage.value = "Recovery failed: ${e.message}"
            }
            refreshStorageAndIntegrity()
            _showOrphanReviewDialog.value = false
            _isProcessing.value = false
        }
    }

    fun purgeIncompleteOperations() {
        viewModelScope.launch {
            _isProcessing.value = true
            val result = lifecycleManager.purgeIncompleteOperations()
            result.onSuccess { count ->
                _statusMessage.value = "Purged $count incomplete temporary file(s)"
            }.onFailure { e ->
                _statusMessage.value = "Purge error: ${e.message}"
            }
            refreshStorageAndIntegrity()
            _showOrphanReviewDialog.value = false
            _isProcessing.value = false
        }
    }

    fun purgeMissingMetadata() {
        val items = _orphanScanResult.value.missingPhysicalFiles
        if (items.isEmpty()) return

        viewModelScope.launch {
            _isProcessing.value = true
            val result = lifecycleManager.purgeMissingMetadata(items)
            result.onSuccess { count ->
                _statusMessage.value = "Cleaned $count orphaned metadata record(s)"
            }.onFailure { e ->
                _statusMessage.value = "Clean error: ${e.message}"
            }
            refreshStorageAndIntegrity()
            _showOrphanReviewDialog.value = false
            _isProcessing.value = false
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
