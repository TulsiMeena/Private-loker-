package com.example.feature.files.archive

import com.example.core.storage.VaultCategory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Data provider interface for creating or updating archive entries.
 */
data class ArchiveSourceFile(
    val entryPath: String,
    val sizeBytes: Long,
    val openStream: () -> InputStream
)

/**
 * Complete, secure archive manipulation engine.
 *
 * Designed with:
 * - Streaming I/O (no out-of-memory errors on large archives)
 * - Atomic output creation
 * - Robust cancellation handling
 * - Comprehensive Zip Slip & Zip Bomb defense
 */
object ZipArchiveEngine {

    private const val BUFFER_SIZE = 64 * 1024 // 64 KB streaming buffer

    /**
     * Reads and parses an archive's entry structure and computes metadata.
     */
    fun readArchiveStructure(archiveFile: File, vaultItemId: Long = 0L): Pair<ArchiveMetadata, List<ArchiveEntryItem>> {
        ZipSecurityEngine.detectAndValidateArchiveFormat(archiveFile)

        val entries = mutableListOf<ArchiveEntryItem>()
        var uncompressedTotalBytes = 0L
        var compressedTotalBytes = 0L
        var totalFiles = 0
        var totalDirectories = 0
        var isPasswordProtected = false

        try {
            ZipFile(archiveFile).use { zipFile ->
                val enumEntries = zipFile.entries()
                var entryCount = 0

                while (enumEntries.hasMoreElements()) {
                    entryCount++
                    if (entryCount > ZipSecurityEngine.MAX_ENTRY_COUNT) {
                        throw ZipBombException("Archive exceeds safe extraction limits: entry count exceeds ${ZipSecurityEngine.MAX_ENTRY_COUNT}")
                    }

                    val entry = enumEntries.nextElement()
                    val sanitizedPath = try {
                        ZipSecurityEngine.sanitizeEntryPath(entry.name)
                    } catch (e: Exception) {
                        // Skip or fail on invalid path
                        throw e
                    }

                    val isDir = entry.isDirectory || entry.name.endsWith("/")
                    val name = if (isDir) {
                        sanitizedPath.trimEnd('/').substringAfterLast('/')
                    } else {
                        sanitizedPath.substringAfterLast('/')
                    }

                    val parentPath = if (sanitizedPath.contains('/')) {
                        sanitizedPath.substringBeforeLast('/')
                    } else {
                        ""
                    }

                    val uncompressedSize = if (entry.size >= 0) entry.size else 0L
                    val compressedSize = if (entry.compressedSize >= 0) entry.compressedSize else 0L

                    if (isDir) {
                        totalDirectories++
                    } else {
                        totalFiles++
                        uncompressedTotalBytes += uncompressedSize
                        compressedTotalBytes += compressedSize
                    }

                    // Guess category and MIME
                    val ext = name.substringAfterLast('.', "").lowercase()
                    val mimeType = when (ext) {
                        "png" -> "image/png"
                        "jpg", "jpeg" -> "image/jpeg"
                        "webp" -> "image/webp"
                        "gif" -> "image/gif"
                        "pdf" -> "application/pdf"
                        "txt", "md", "csv", "log" -> "text/plain"
                        "mp3" -> "audio/mpeg"
                        "wav" -> "audio/wav"
                        "m4a" -> "audio/mp4"
                        "mp4" -> "video/mp4"
                        "mkv" -> "video/x-matroska"
                        "zip" -> "application/zip"
                        else -> "application/octet-stream"
                    }
                    val category = VaultCategory.fromFileNameAndMime(name, mimeType)

                    entries.add(
                        ArchiveEntryItem(
                            fullPath = sanitizedPath,
                            name = name,
                            isDirectory = isDir,
                            parentPath = parentPath,
                            uncompressedSize = uncompressedSize,
                            compressedSize = compressedSize,
                            crc = entry.crc,
                            lastModifiedTime = entry.time,
                            category = category,
                            mimeType = mimeType,
                            isEncrypted = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            if (e is ZipBombException || e is ZipPathTraversalException || e is UnsupportedArchiveException) {
                throw e
            }
            // Check if password protected using ZipInputStream
            if (checkIfPasswordProtected(archiveFile)) {
                isPasswordProtected = true
            } else {
                throw IllegalArgumentException("Archive integrity check failed: ${e.message}", e)
            }
        }

        val compressionRatio = if (compressedTotalBytes > 0) {
            (uncompressedTotalBytes.toFloat() / compressedTotalBytes.toFloat())
        } else {
            1.0f
        }

        val metadata = ArchiveMetadata(
            fileName = archiveFile.name,
            vaultItemId = vaultItemId,
            format = "ZIP",
            encryptedSizeBytes = archiveFile.length(),
            uncompressedTotalBytes = uncompressedTotalBytes,
            totalEntries = entries.size,
            totalFiles = totalFiles,
            totalDirectories = totalDirectories,
            isPasswordProtected = isPasswordProtected,
            compressionRatio = compressionRatio,
            isSafeToExtract = uncompressedTotalBytes <= ZipSecurityEngine.MAX_TOTAL_EXTRACTED_BYTES && !isPasswordProtected,
            warningMessage = if (isPasswordProtected) "Password-protected archive not supported" else null
        )

        return Pair(metadata, entries)
    }

    private fun checkIfPasswordProtected(archiveFile: File): Boolean {
        try {
            FileInputStream(archiveFile).use { fis ->
                ZipInputStream(BufferedInputStream(fis)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        // Attempting to read a password-protected zip will fail or flag is set
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("encrypted") || msg.contains("password")) {
                return true
            }
        }
        return false
    }

    /**
     * Extracts a single entry to target file (for secure in-memory preview).
     */
    fun extractSingleEntry(
        archiveFile: File,
        entryPath: String,
        targetFile: File,
        maxSizeBytes: Long = 50L * 1024 * 1024
    ) {
        ZipFile(archiveFile).use { zipFile ->
            val entry = zipFile.getEntry(entryPath)
                ?: zipFile.entries().asSequence().find {
                    ZipSecurityEngine.sanitizeEntryPath(it.name) == entryPath
                }
                ?: throw IllegalArgumentException("Archive entry not found: $entryPath")

            if (entry.isDirectory) {
                throw IllegalArgumentException("Cannot extract directory entry as single file: $entryPath")
            }

            if (entry.size > maxSizeBytes) {
                throw ZipBombException("Entry exceeds safe preview limit ($maxSizeBytes bytes)")
            }

            zipFile.getInputStream(entry).use { inStream ->
                FileOutputStream(targetFile).use { fileOut ->
                    BufferedOutputStream(fileOut, BUFFER_SIZE).use { outStream ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        var totalWritten = 0L

                        while (inStream.read(buffer).also { bytesRead = it } != -1) {
                            totalWritten += bytesRead
                            if (totalWritten > maxSizeBytes) {
                                throw ZipBombException("Entry stream exceeded safe preview limit")
                            }
                            outStream.write(buffer, 0, bytesRead)
                        }
                    }
                }
            }
        }
    }

    /**
     * Securely extracts an entire archive into [destinationDir].
     * Enforces Zip Slip and Zip Bomb protections at each step.
     */
    fun extractArchive(
        archiveFile: File,
        destinationDir: File,
        onProgress: ((OperationProgressState) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ): List<File> {
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        val startTime = System.currentTimeMillis()
        var totalBytesWritten = 0L
        var totalCompressedRead = 0L
        var entriesProcessed = 0
        val extractedFiles = mutableListOf<File>()

        // 1. First pass: count total entries for accurate progress reporting
        val totalCount = try {
            ZipFile(archiveFile).use { it.size() }
        } catch (_: Exception) {
            -1
        }

        onProgress?.invoke(
            OperationProgressState(
                phase = "PREPARING",
                progress = 0f,
                currentFile = "",
                currentIndex = 0,
                totalCount = if (totalCount > 0) totalCount else 1,
                canCancel = true
            )
        )

        // Check storage margin
        ZipSecurityEngine.checkStorageAvailable(destinationDir, archiveFile.length() * 2)

        ZipFile(archiveFile).use { zipFile ->
            val entries = zipFile.entries()

            while (entries.hasMoreElements()) {
                if (isCancelled()) {
                    throw CancellationException("Extraction cancelled by user")
                }

                val entry = entries.nextElement()
                entriesProcessed++

                val sanitizedPath = ZipSecurityEngine.sanitizeEntryPath(entry.name)
                if (sanitizedPath.isEmpty()) continue

                val targetFile = File(destinationDir, sanitizedPath)

                // Critical: verify destination containment
                if (!ZipSecurityEngine.verifySafeDestination(targetFile, destinationDir)) {
                    throw ZipPathTraversalException("Zip slip detected: path $sanitizedPath escapes destination")
                }

                val isDir = entry.isDirectory || entry.name.endsWith("/")

                if (isDir) {
                    if (!targetFile.exists()) {
                        targetFile.mkdirs()
                    }
                    continue
                }

                // Ensure parent directories exist
                targetFile.parentFile?.mkdirs()

                val entryCompressed = if (entry.compressedSize >= 0) entry.compressedSize else 0L
                totalCompressedRead += entryCompressed

                var currentFileWritten = 0L

                zipFile.getInputStream(entry).use { inStream ->
                    FileOutputStream(targetFile).use { fos ->
                        BufferedOutputStream(fos, BUFFER_SIZE).use { bos ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var read: Int
                            while (inStream.read(buffer).also { read = it } != -1) {
                                if (isCancelled()) {
                                    throw CancellationException("Extraction cancelled by user")
                                }

                                bos.write(buffer, 0, read)
                                currentFileWritten += read
                                totalBytesWritten += read

                                ZipSecurityEngine.validateCumulativeDecompression(
                                    totalBytesWritten = totalBytesWritten,
                                    totalEntriesProcessed = entriesProcessed,
                                    totalCompressedBytesRead = totalCompressedRead,
                                    currentFileBytesWritten = currentFileWritten
                                )
                            }
                        }
                    }
                }

                extractedFiles.add(targetFile)

                val progressFrac = if (totalCount > 0) {
                    (entriesProcessed.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
                } else {
                    0.5f
                }

                onProgress?.invoke(
                    OperationProgressState(
                        phase = "EXTRACTING",
                        progress = progressFrac,
                        currentFile = targetFile.name,
                        currentIndex = entriesProcessed,
                        totalCount = if (totalCount > 0) totalCount else entriesProcessed,
                        bytesProcessed = totalBytesWritten,
                        elapsedTimeMs = System.currentTimeMillis() - startTime,
                        canCancel = true
                    )
                )
            }
        }

        onProgress?.invoke(
            OperationProgressState(
                phase = "COMPLETED",
                progress = 1.0f,
                currentIndex = entriesProcessed,
                totalCount = entriesProcessed,
                bytesProcessed = totalBytesWritten,
                elapsedTimeMs = System.currentTimeMillis() - startTime,
                isCompleted = true
            )
        )

        return extractedFiles
    }

    /**
     * Creates a new ZIP archive from a list of [ArchiveSourceFile].
     */
    fun createArchive(
        filesToCompress: List<ArchiveSourceFile>,
        destinationZipFile: File,
        compressionLevel: ArchiveCompressionLevel = ArchiveCompressionLevel.NORMAL,
        onProgress: ((OperationProgressState) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ) {
        val startTime = System.currentTimeMillis()
        val totalFiles = filesToCompress.size
        var filesProcessed = 0
        var totalBytesWritten = 0L

        onProgress?.invoke(
            OperationProgressState(
                phase = "PREPARING",
                progress = 0f,
                currentIndex = 0,
                totalCount = totalFiles,
                canCancel = true
            )
        )

        // Temporary file for atomic writing
        val tempZip = File(destinationZipFile.parentFile, "${destinationZipFile.name}.tmp_${System.currentTimeMillis()}")

        try {
            FileOutputStream(tempZip).use { fos ->
                BufferedOutputStream(fos, BUFFER_SIZE).use { bos ->
                    ZipOutputStream(bos).use { zos ->
                        if (compressionLevel == ArchiveCompressionLevel.STORE) {
                            zos.setMethod(ZipOutputStream.DEFLATED)
                            zos.setLevel(0)
                        } else {
                            zos.setMethod(ZipOutputStream.DEFLATED)
                            zos.setLevel(compressionLevel.level)
                        }

                        for (source in filesToCompress) {
                            if (isCancelled()) {
                                throw CancellationException("Archive creation cancelled by user")
                            }

                            val cleanPath = ZipSecurityEngine.sanitizeEntryPath(source.entryPath)
                            if (cleanPath.isEmpty()) continue

                            val entry = ZipEntry(cleanPath).apply {
                                time = System.currentTimeMillis()
                            }
                            zos.putNextEntry(entry)

                            source.openStream().use { input ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    if (isCancelled()) {
                                        throw CancellationException("Archive creation cancelled by user")
                                    }
                                    zos.write(buffer, 0, read)
                                    totalBytesWritten += read
                                }
                            }

                            zos.closeEntry()
                            filesProcessed++

                            val progressFrac = if (totalFiles > 0) {
                                (filesProcessed.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f)
                            } else 1f

                            onProgress?.invoke(
                                OperationProgressState(
                                    phase = "COMPRESSING",
                                    progress = progressFrac,
                                    currentFile = cleanPath.substringAfterLast('/'),
                                    currentIndex = filesProcessed,
                                    totalCount = totalFiles,
                                    bytesProcessed = totalBytesWritten,
                                    elapsedTimeMs = System.currentTimeMillis() - startTime,
                                    canCancel = true
                                )
                            )
                        }
                    }
                }
            }

            // Atomic rename
            if (destinationZipFile.exists()) {
                destinationZipFile.delete()
            }
            if (!tempZip.renameTo(destinationZipFile)) {
                tempZip.copyTo(destinationZipFile, overwrite = true)
                tempZip.delete()
            }

            onProgress?.invoke(
                OperationProgressState(
                    phase = "COMPLETED",
                    progress = 1.0f,
                    currentIndex = filesProcessed,
                    totalCount = totalFiles,
                    bytesProcessed = totalBytesWritten,
                    elapsedTimeMs = System.currentTimeMillis() - startTime,
                    isCompleted = true
                )
            )
        } catch (e: Throwable) {
            if (tempZip.exists()) tempZip.delete()
            throw e
        }
    }

    /**
     * Rebuilds an archive atomically to add, delete, or rename entries.
     */
    fun rebuildArchive(
        originalArchive: File,
        targetZipFile: File,
        entriesToDelete: Set<String> = emptySet(),
        entriesToAdd: List<ArchiveSourceFile> = emptyList(),
        entriesToRename: Map<String, String> = emptyMap(),
        onProgress: ((OperationProgressState) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ) {
        val tempZip = File(targetZipFile.parentFile, "${targetZipFile.name}.rebuild_${System.currentTimeMillis()}")

        try {
            FileOutputStream(tempZip).use { fos ->
                BufferedOutputStream(fos, BUFFER_SIZE).use { bos ->
                    ZipOutputStream(bos).use { zos ->
                        zos.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION)

                        // 1. Copy existing entries from original archive (skipping deleted ones, renaming if needed)
                        ZipFile(originalArchive).use { zipFile ->
                            val entries = zipFile.entries()
                            while (entries.hasMoreElements()) {
                                if (isCancelled()) throw CancellationException("Rebuild cancelled")
                                val entry = entries.nextElement()
                                val rawPath = ZipSecurityEngine.sanitizeEntryPath(entry.name)

                                // Check if this entry or its parent folder is marked for deletion
                                val isDeleted = entriesToDelete.any { delPath ->
                                    rawPath == delPath || rawPath.startsWith("$delPath/")
                                }
                                if (isDeleted) continue

                                // Check if entry should be renamed
                                val finalPath = entriesToRename[rawPath] ?: rawPath

                                val newEntry = ZipEntry(finalPath).apply {
                                    time = entry.time
                                }
                                zos.putNextEntry(newEntry)

                                if (!entry.isDirectory) {
                                    zipFile.getInputStream(entry).use { inStream ->
                                        val buffer = ByteArray(BUFFER_SIZE)
                                        var read: Int
                                        while (inStream.read(buffer).also { read = it } != -1) {
                                            zos.write(buffer, 0, read)
                                        }
                                    }
                                }
                                zos.closeEntry()
                            }
                        }

                        // 2. Append new entries
                        for (newFile in entriesToAdd) {
                            if (isCancelled()) throw CancellationException("Rebuild cancelled")
                            val cleanPath = ZipSecurityEngine.sanitizeEntryPath(newFile.entryPath)
                            if (cleanPath.isEmpty()) continue

                            val entry = ZipEntry(cleanPath).apply {
                                time = System.currentTimeMillis()
                            }
                            zos.putNextEntry(entry)

                            newFile.openStream().use { inStream ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                var read: Int
                                while (inStream.read(buffer).also { read = it } != -1) {
                                    zos.write(buffer, 0, read)
                                }
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }

            if (targetZipFile.exists()) {
                targetZipFile.delete()
            }
            if (!tempZip.renameTo(targetZipFile)) {
                tempZip.copyTo(targetZipFile, overwrite = true)
                tempZip.delete()
            }
        } catch (e: Throwable) {
            if (tempZip.exists()) tempZip.delete()
            throw e
        }
    }
}
