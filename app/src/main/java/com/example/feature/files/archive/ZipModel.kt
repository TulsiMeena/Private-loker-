package com.example.feature.files.archive

import com.example.core.storage.VaultCategory
import java.io.File
import java.util.Locale

/**
 * High-level descriptor for a single entry inside an archive.
 */
data class ArchiveEntryItem(
    val fullPath: String,
    val name: String,
    val isDirectory: Boolean,
    val parentPath: String,
    val uncompressedSize: Long,
    val compressedSize: Long,
    val crc: Long,
    val lastModifiedTime: Long,
    val category: VaultCategory,
    val mimeType: String,
    val isEncrypted: Boolean = false
) {
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    val isExecutableOrScript: Boolean
        get() = extension in EXECUTABLE_EXTENSIONS

    companion object {
        val EXECUTABLE_EXTENSIONS = setOf(
            "exe", "bat", "cmd", "sh", "bash", "apk", "jar", "dex", "so", "py", "js", "vbs", "ps1", "elf"
        )
    }
}

/**
 * Metadata analysis of an opened archive.
 */
data class ArchiveMetadata(
    val fileName: String,
    val vaultItemId: Long,
    val format: String,
    val encryptedSizeBytes: Long,
    val uncompressedTotalBytes: Long,
    val totalEntries: Int,
    val totalFiles: Int,
    val totalDirectories: Int,
    val isPasswordProtected: Boolean,
    val compressionRatio: Float,
    val isSafeToExtract: Boolean,
    val warningMessage: String? = null
)

/**
 * Supported compression levels for ZIP creation.
 */
enum class ArchiveCompressionLevel(val displayName: String, val level: Int) {
    STORE("Store (Fastest, No Compression)", java.util.zip.Deflater.NO_COMPRESSION),
    NORMAL("Normal (Balanced)", java.util.zip.Deflater.DEFAULT_COMPRESSION),
    MAXIMUM("Maximum (Highest Ratio)", java.util.zip.Deflater.BEST_COMPRESSION)
}

/**
 * Extraction target options.
 */
enum class ArchiveExtractionOption {
    EXTRACT_HERE,
    EXTRACT_TO_FOLDER,
    CREATE_NEW_FOLDER_AND_EXTRACT
}

/**
 * Duplicate handling options.
 */
enum class DuplicateConflictOption {
    KEEP_BOTH,
    REPLACE,
    CANCEL
}

/**
 * Real-time operation progress descriptor for long-running archive tasks.
 */
data class OperationProgressState(
    val phase: String = "IDLE",
    val progress: Float = 0f,
    val currentFile: String = "",
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val bytesProcessed: Long = 0L,
    val totalBytes: Long = 0L,
    val elapsedTimeMs: Long = 0L,
    val canCancel: Boolean = true,
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Exception thrown when an archive violates decompression security limits.
 */
class ZipBombException(message: String) : SecurityException(message)

/**
 * Exception thrown when a path traversal attempt is detected.
 */
class ZipPathTraversalException(message: String) : SecurityException(message)

/**
 * Exception thrown when an archive format is unsupported.
 */
class UnsupportedArchiveException(message: String) : IllegalArgumentException(message)

/**
 * Exception thrown when an archive is password protected.
 */
class ZipPasswordProtectedException(message: String) : IllegalStateException(message)

/**
 * Exception thrown when device storage is insufficient for extraction.
 */
class InsufficientStorageException(message: String) : IllegalStateException(message)
