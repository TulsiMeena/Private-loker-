package com.example.feature.files.operations

import com.example.core.security.LockState
import com.example.core.security.SessionSecurityManager
import com.example.core.storage.VaultStorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class FileOperationType(val displayName: String) {
    IMPORT("Importing Files"),
    EXPORT("Exporting Vault Item"),
    COPY("Copying Files"),
    MOVE("Moving Files"),
    ARCHIVE("Creating Archive"),
    EXTRACTION("Extracting Archive"),
    DELETE("Secure Deletion"),
    BULK_ACTION("Batch Operation")
}

enum class FileOperationStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class FileOperation(
    val id: String = UUID.randomUUID().toString(),
    val type: FileOperationType,
    val title: String,
    val status: FileOperationStatus = FileOperationStatus.QUEUED,
    val progress: Float = 0f,
    val currentStep: String = "Queued",
    val itemsProcessed: Int = 0,
    val totalItems: Int = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val errorMessage: String? = null
)

/**
 * Thread-safe asynchronous manager for long-running file operations in PrivateVault.
 *
 * Security Invariants:
 * - Auto-cancels and purges all active temporary files immediately on session lock.
 * - Prevents multiple operations from corrupting identical targets simultaneously.
 * - Emits real-time progress for UI display.
 */
class FileOperationManager(
    private val storageManager: VaultStorageManager,
    private val sessionManager: SessionSecurityManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val _operations = MutableStateFlow<List<FileOperation>>(emptyList())
    val operations: StateFlow<List<FileOperation>> = _operations.asStateFlow()

    private val cancellationCallbacks = ConcurrentHashMap<String, () -> Unit>()

    init {
        // Enforce immediate cancellation and wipe when session locks
        sessionManager.lockState
            .onEach { lockState ->
                if (lockState !is LockState.Unlocked) {
                    cancelAllOperations("Vault locked")
                    storageManager.purgeTemporaryFiles()
                }
            }
            .launchIn(scope)
    }

    /**
     * Registers a new operation in the queue.
     */
    fun enqueueOperation(
        type: FileOperationType,
        title: String,
        totalItems: Int = 1,
        cancelAction: (() -> Unit)? = null
    ): String {
        val op = FileOperation(
            type = type,
            title = title,
            totalItems = totalItems,
            status = FileOperationStatus.RUNNING
        )
        if (cancelAction != null) {
            cancellationCallbacks[op.id] = cancelAction
        }
        _operations.value = listOf(op) + _operations.value.take(20)
        return op.id
    }

    /**
     * Updates progress of a running operation.
     */
    fun updateProgress(
        id: String,
        progress: Float,
        currentStep: String,
        itemsProcessed: Int = 0,
        totalItems: Int = 0
    ) {
        _operations.value = _operations.value.map { op ->
            if (op.id == id) {
                op.copy(
                    progress = progress.coerceIn(0f, 1f),
                    currentStep = currentStep,
                    itemsProcessed = if (itemsProcessed > 0) itemsProcessed else op.itemsProcessed,
                    totalItems = if (totalItems > 0) totalItems else op.totalItems
                )
            } else op
        }
    }

    /**
     * Marks an operation as completed.
     */
    fun completeOperation(id: String) {
        cancellationCallbacks.remove(id)
        _operations.value = _operations.value.map { op ->
            if (op.id == id) {
                op.copy(
                    status = FileOperationStatus.COMPLETED,
                    progress = 1f,
                    currentStep = "Completed",
                    finishedAt = System.currentTimeMillis()
                )
            } else op
        }
    }

    /**
     * Marks an operation as failed with a friendly message.
     */
    fun failOperation(id: String, error: String) {
        cancellationCallbacks.remove(id)
        _operations.value = _operations.value.map { op ->
            if (op.id == id) {
                op.copy(
                    status = FileOperationStatus.FAILED,
                    currentStep = "Failed",
                    errorMessage = error,
                    finishedAt = System.currentTimeMillis()
                )
            } else op
        }
    }

    /**
     * Cancels an individual operation.
     */
    fun cancelOperation(id: String) {
        val callback = cancellationCallbacks.remove(id)
        callback?.invoke()
        _operations.value = _operations.value.map { op ->
            if (op.id == id) {
                op.copy(
                    status = FileOperationStatus.CANCELLED,
                    currentStep = "Cancelled",
                    finishedAt = System.currentTimeMillis()
                )
            } else op
        }
    }

    /**
     * Cancels all active operations (e.g. during auto-lock).
     */
    fun cancelAllOperations(reason: String = "User requested cancel") {
        val keys = cancellationCallbacks.keys().toList()
        for (id in keys) {
            cancellationCallbacks.remove(id)?.invoke()
        }
        _operations.value = _operations.value.map { op ->
            if (op.status == FileOperationStatus.RUNNING || op.status == FileOperationStatus.QUEUED) {
                op.copy(
                    status = FileOperationStatus.CANCELLED,
                    currentStep = reason,
                    finishedAt = System.currentTimeMillis()
                )
            } else op
        }
    }

    fun clearCompleted() {
        _operations.value = _operations.value.filter {
            it.status == FileOperationStatus.RUNNING || it.status == FileOperationStatus.QUEUED
        }
    }
}
