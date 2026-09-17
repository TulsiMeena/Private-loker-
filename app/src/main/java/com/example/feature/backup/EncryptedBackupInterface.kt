package com.example.feature.backup

import java.io.InputStream
import java.io.OutputStream

data class BackupManifest(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val itemCount: Int,
    val totalSizeBytes: Long,
    val backupChecksumSha256: String
)

data class BackupProgress(
    val currentItemIndex: Int,
    val totalItems: Int,
    val bytesProcessed: Long,
    val totalBytes: Long,
    val phase: String
)

/**
 * Local-First Encrypted Backup Engine Interface.
 *
 * Security Requirements:
 * - Backups are fully encrypted containers exportable strictly via user-directed SAF.
 * - Local-First: Zero cloud upload, zero network telemetry, zero remote transmission.
 */
interface EncryptedBackupManager {
    suspend fun createEncryptedBackup(
        destinationStream: OutputStream,
        onProgress: ((BackupProgress) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ): Result<BackupManifest>

    suspend fun inspectBackupManifest(
        backupStream: InputStream
    ): Result<BackupManifest>

    suspend fun restoreFromEncryptedBackup(
        backupStream: InputStream,
        onProgress: ((BackupProgress) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ): Result<Int>
}
