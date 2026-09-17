package com.example.core.lifecycle

import com.example.core.database.VaultItemEntity
import java.io.File

/**
 * Retention duration policy for automatic secure trash cleanup.
 */
enum class AutoCleanRetention(val days: Int, val label: String) {
    NEVER(0, "Never (Manual Only)"),
    DAYS_7(7, "7 Days"),
    DAYS_30(30, "30 Days (Default)"),
    DAYS_90(90, "90 Days");

    val retentionMillis: Long
        get() = days * 24L * 60 * 60 * 1000

    companion object {
        fun fromDays(days: Int): AutoCleanRetention {
            return entries.find { it.days == days } ?: DAYS_30
        }
    }
}

/**
 * Resolution strategies when a filename collision occurs during trash restoration.
 */
enum class RestoreConflictResolution {
    KEEP_EXISTING, // Abort restore for this file, keeps it in trash
    REPLACE,       // Overwrite destination file (moves existing to trash, restores new)
    KEEP_BOTH      // Renames restored item to avoid conflict (e.g. "Name (restored).ext")
}

/**
 * Result of a file restoration operation from trash.
 */
sealed class RestoreOutcome {
    data class Success(
        val item: VaultItemEntity,
        val renamed: Boolean = false,
        val restoredToRoot: Boolean = false
    ) : RestoreOutcome()

    data class Conflict(
        val itemToRestore: VaultItemEntity,
        val existingConflictItem: VaultItemEntity
    ) : RestoreOutcome()

    data class Failure(
        val item: VaultItemEntity,
        val reason: String
    ) : RestoreOutcome()
}

/**
 * Detailed report of an Empty Trash batch operation.
 */
data class EmptyTrashReport(
    val deletedCount: Int,
    val failedCount: Int,
    val freedBytes: Long,
    val failedItemTitles: List<String> = emptyList()
) {
    val isFullSuccess: Boolean get() = failedCount == 0
}

/**
 * Integrity scan results identifying unlinked encrypted containers,
 * broken metadata pointers, or abandoned working files.
 */
data class OrphanScanResult(
    val unlinkedContainers: List<File> = emptyList(),
    val missingPhysicalFiles: List<VaultItemEntity> = emptyList(),
    val incompleteOperations: List<File> = emptyList(),
    val abandonedTransientFiles: List<File> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) {
    val totalIssuesCount: Int
        get() = unlinkedContainers.size + missingPhysicalFiles.size +
                incompleteOperations.size + abandonedTransientFiles.size

    val hasIssues: Boolean
        get() = totalIssuesCount > 0
}

/**
 * Verifiable storage statistics across vault partitions and host storage.
 * Note: Never contains fabricated percentages or security scores.
 */
data class StorageSummary(
    val vaultBytes: Long = 0L,
    val trashBytes: Long = 0L,
    val temporaryBytes: Long = 0L,
    val availableDeviceBytes: Long = 0L,
    val activeItemCount: Int = 0,
    val trashItemCount: Int = 0
)

/**
 * Startup and crash recovery diagnostic report.
 */
data class CrashRecoveryReport(
    val cleanedTransientCount: Int = 0,
    val incompleteOperationsDetected: Int = 0,
    val isLockedGuaranteed: Boolean = true,
    val details: String = ""
)
