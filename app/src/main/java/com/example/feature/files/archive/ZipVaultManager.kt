package com.example.feature.files.archive

import com.example.core.database.DuplicateResolution
import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.storage.FileShredder
import com.example.core.storage.VaultCategory
import com.example.core.storage.VaultStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.CancellationException

/**
 * High-level orchestration manager for secure archive operations in PrivateVault.
 *
 * Adheres strictly to security mandates:
 * - Never leaves plaintext files in public storage.
 * - Enforces streaming and memory-bounded extraction.
 * - Handles auto-lock and user cancellation gracefully.
 * - Implements atomic commit and safe rollback patterns.
 */
class ZipVaultManager(
    private val repository: VaultRepository,
    private val storageManager: VaultStorageManager
) {

    /**
     * Authenticates and opens an archive for secure browsing.
     * Decrypts the archive temporarily to an app-private transient workspace.
     */
    suspend fun openArchive(item: VaultItemEntity): Result<Pair<ArchiveMetadata, List<ArchiveEntryItem>>> =
        withContext(Dispatchers.IO) {
            var tempArchiveFile: File? = null
            try {
                // Decrypt to transient file
                tempArchiveFile = storageManager.createTemporaryDecryptedFile(item.encryptedPath, "${item.id}.zip")
                val (metadata, entries) = ZipArchiveEngine.readArchiveStructure(tempArchiveFile, item.id)
                Result.success(Pair(metadata, entries))
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                tempArchiveFile?.let { storageManager.releaseTemporaryFile(it) }
            }
        }

    /**
     * Extracts a single entry from an encrypted archive for instant, secure preview.
     */
    suspend fun previewArchiveEntry(
        item: VaultItemEntity,
        entryPath: String
    ): Result<Pair<ArchiveEntryItem, File>> = withContext(Dispatchers.IO) {
        var tempArchiveFile: File? = null
        var previewFile: File? = null
        try {
            tempArchiveFile = storageManager.createTemporaryDecryptedFile(item.encryptedPath, "${item.id}.zip")
            val (_, entries) = ZipArchiveEngine.readArchiveStructure(tempArchiveFile, item.id)
            val entryItem = entries.find { it.fullPath == entryPath }
                ?: return@withContext Result.failure(IllegalArgumentException("Entry not found: $entryPath"))

            val ext = entryItem.name.substringAfterLast('.', "tmp")
            previewFile = storageManager.tempFileManager.createTransientFile(ext)

            ZipArchiveEngine.extractSingleEntry(tempArchiveFile, entryPath, previewFile)
            Result.success(Pair(entryItem, previewFile))
        } catch (e: Exception) {
            previewFile?.let { storageManager.releaseTemporaryFile(it) }
            Result.failure(e)
        } finally {
            tempArchiveFile?.let { storageManager.releaseTemporaryFile(it) }
        }
    }

    /**
     * Executes the secure extraction pipeline.
     */
    suspend fun extractArchive(
        item: VaultItemEntity,
        targetFolderId: Long?,
        option: ArchiveExtractionOption = ArchiveExtractionOption.EXTRACT_HERE,
        customFolderName: String? = null,
        onProgress: ((OperationProgressState) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ): Result<Int> = withContext(Dispatchers.IO) {
        var tempArchiveFile: File? = null
        val extractionDirName = "extract_${UUID.randomUUID()}"
        val extractionBaseDir = File(storageManager.tempFileManager.createTransientFile("dir").parentFile, extractionDirName).apply {
            mkdirs()
        }

        try {
            onProgress?.invoke(OperationProgressState(phase = "PREPARING", progress = 0.05f))

            // 1. Authenticated decryption
            tempArchiveFile = storageManager.createTemporaryDecryptedFile(item.encryptedPath, "${item.id}.zip")

            // 2. Validate format and compute entries
            val (metadata, _) = ZipArchiveEngine.readArchiveStructure(tempArchiveFile, item.id)
            if (metadata.isPasswordProtected) {
                throw ZipPasswordProtectedException("Password-protected archives are not supported")
            }

            // 3. Resolve destination folder in Vault
            val effectiveFolderId: Long? = when (option) {
                ArchiveExtractionOption.EXTRACT_HERE -> targetFolderId ?: item.folderId
                ArchiveExtractionOption.EXTRACT_TO_FOLDER -> targetFolderId
                ArchiveExtractionOption.CREATE_NEW_FOLDER_AND_EXTRACT -> {
                    val folderName = customFolderName ?: item.title.substringBeforeLast('.')
                    repository.createFolder(folderName, targetFolderId ?: item.folderId).getOrNull()?.id
                }
            }

            // 4. Extract into temporary protected workspace
            val extractedFiles = ZipArchiveEngine.extractArchive(
                archiveFile = tempArchiveFile,
                destinationDir = extractionBaseDir,
                onProgress = { state ->
                    // Scale progress from 0.1 to 0.7 during extraction
                    onProgress?.invoke(
                        state.copy(
                            progress = 0.1f + (state.progress * 0.6f),
                            phase = "EXTRACTING"
                        )
                    )
                },
                isCancelled = isCancelled
            )

            // 5. Encrypt extracted files into vault
            var importedCount = 0
            val totalToEncrypt = extractedFiles.size
            val startTime = System.currentTimeMillis()

            for ((index, file) in extractedFiles.withIndex()) {
                if (isCancelled()) {
                    throw CancellationException("Extraction cancelled during vault import")
                }

                val relativePathFromExtracted = file.relativeTo(extractionBaseDir).path.replace('\\', '/')
                val fileName = file.name
                val ext = fileName.substringAfterLast('.', "")
                val category = VaultCategory.fromFileNameAndMime(fileName, "application/octet-stream")

                FileInputStream(file).use { fis ->
                    val importResult = repository.importItem(
                        title = fileName,
                        category = category,
                        mimeType = category.defaultMime,
                        inputStream = fis,
                        folderId = effectiveFolderId,
                        expectedSizeBytes = file.length(),
                        resolution = DuplicateResolution.KEEP_BOTH
                    )

                    if (importResult.isSuccess) {
                        importedCount++
                    }
                }

                val encryptProgress = 0.7f + ((index + 1).toFloat() / totalToEncrypt.toFloat() * 0.28f)
                onProgress?.invoke(
                    OperationProgressState(
                        phase = "ENCRYPTING",
                        progress = encryptProgress,
                        currentFile = fileName,
                        currentIndex = index + 1,
                        totalCount = totalToEncrypt,
                        elapsedTimeMs = System.currentTimeMillis() - startTime,
                        canCancel = true
                    )
                )
            }

            onProgress?.invoke(
                OperationProgressState(
                    phase = "COMPLETED",
                    progress = 1.0f,
                    currentIndex = totalToEncrypt,
                    totalCount = totalToEncrypt,
                    isCompleted = true
                )
            )

            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempArchiveFile?.let { storageManager.releaseTemporaryFile(it) }
            // Always clean up transient extraction workspace
            if (extractionBaseDir.exists()) {
                extractionBaseDir.walkBottomUp().forEach { FileShredder.shredAndPurge(it) }
            }
        }
    }

    /**
     * Creates a new encrypted ZIP archive from selected vault items.
     */
    suspend fun createArchiveFromItems(
        items: List<VaultItemEntity>,
        archiveName: String,
        targetFolderId: Long?,
        compressionLevel: ArchiveCompressionLevel = ArchiveCompressionLevel.NORMAL,
        conflictOption: DuplicateConflictOption = DuplicateConflictOption.KEEP_BOTH,
        onProgress: ((OperationProgressState) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ): Result<VaultItemEntity> = withContext(Dispatchers.IO) {
        val sanitizedTitle = validateAndSanitizeArchiveTitle(archiveName)
        val tempZipFile = storageManager.tempFileManager.createTransientFile("zip")
        val transientFilesToClean = mutableListOf<File>()

        try {
            onProgress?.invoke(OperationProgressState(phase = "PREPARING", progress = 0.05f))

            // Build source list: decrypt items to transient streams
            val sourceFiles = mutableListOf<ArchiveSourceFile>()

            for (item in items) {
                if (isCancelled()) throw CancellationException("Cancelled by user")
                val itemTransient = storageManager.createTemporaryDecryptedFile(item.encryptedPath, item.title)
                transientFilesToClean.add(itemTransient)

                sourceFiles.add(
                    ArchiveSourceFile(
                        entryPath = item.title,
                        sizeBytes = item.sizeBytes,
                        openStream = { FileInputStream(itemTransient) }
                    )
                )
            }

            // Create ZIP archive
            ZipArchiveEngine.createArchive(
                filesToCompress = sourceFiles,
                destinationZipFile = tempZipFile,
                compressionLevel = compressionLevel,
                onProgress = { state ->
                    onProgress?.invoke(
                        state.copy(
                            progress = 0.1f + (state.progress * 0.7f),
                            phase = "COMPRESSING"
                        )
                    )
                },
                isCancelled = isCancelled
            )

            onProgress?.invoke(OperationProgressState(phase = "ENCRYPTING", progress = 0.85f))

            // Encrypt and commit into vault
            val resolution = when (conflictOption) {
                DuplicateConflictOption.KEEP_BOTH -> DuplicateResolution.KEEP_BOTH
                DuplicateConflictOption.REPLACE -> DuplicateResolution.REPLACE
                DuplicateConflictOption.CANCEL -> DuplicateResolution.CANCEL
            }

            val importResult = FileInputStream(tempZipFile).use { fis ->
                repository.importItem(
                    title = sanitizedTitle,
                    category = VaultCategory.ZIP,
                    mimeType = "application/zip",
                    inputStream = fis,
                    folderId = targetFolderId,
                    expectedSizeBytes = tempZipFile.length(),
                    resolution = resolution
                )
            }

            onProgress?.invoke(OperationProgressState(phase = "COMPLETED", progress = 1.0f, isCompleted = true))
            importResult
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            storageManager.releaseTemporaryFile(tempZipFile)
            transientFilesToClean.forEach { storageManager.releaseTemporaryFile(it) }
        }
    }

    /**
     * Atomically modifies an archive (add items, delete entries, rename entries).
     */
    suspend fun modifyArchive(
        item: VaultItemEntity,
        entriesToDelete: Set<String> = emptySet(),
        vaultItemsToAdd: List<VaultItemEntity> = emptyList(),
        entriesToRename: Map<String, String> = emptyMap(),
        onProgress: ((OperationProgressState) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ): Result<VaultItemEntity> = withContext(Dispatchers.IO) {
        var originalTemp: File? = null
        val rebuiltTemp = storageManager.tempFileManager.createTransientFile("zip")
        val transientFilesToClean = mutableListOf<File>()

        try {
            originalTemp = storageManager.createTemporaryDecryptedFile(item.encryptedPath, "${item.id}.zip")

            // Prepare files to add
            val sourceFilesToAdd = mutableListOf<ArchiveSourceFile>()
            for (toAdd in vaultItemsToAdd) {
                val tempFile = storageManager.createTemporaryDecryptedFile(toAdd.encryptedPath, toAdd.title)
                transientFilesToClean.add(tempFile)
                sourceFilesToAdd.add(
                    ArchiveSourceFile(
                        entryPath = toAdd.title,
                        sizeBytes = toAdd.sizeBytes,
                        openStream = { FileInputStream(tempFile) }
                    )
                )
            }

            // Rebuild
            ZipArchiveEngine.rebuildArchive(
                originalArchive = originalTemp,
                targetZipFile = rebuiltTemp,
                entriesToDelete = entriesToDelete,
                entriesToAdd = sourceFilesToAdd,
                entriesToRename = entriesToRename,
                onProgress = onProgress,
                isCancelled = isCancelled
            )

            // Encrypt and replace
            val importResult = FileInputStream(rebuiltTemp).use { fis ->
                repository.importItem(
                    title = item.title,
                    category = VaultCategory.ZIP,
                    mimeType = "application/zip",
                    inputStream = fis,
                    folderId = item.folderId,
                    expectedSizeBytes = rebuiltTemp.length(),
                    resolution = DuplicateResolution.REPLACE
                )
            }

            importResult
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            originalTemp?.let { storageManager.releaseTemporaryFile(it) }
            storageManager.releaseTemporaryFile(rebuiltTemp)
            transientFilesToClean.forEach { storageManager.releaseTemporaryFile(it) }
        }
    }

    /**
     * Extracts multiple archives in sequence.
     */
    suspend fun bulkExtract(
        items: List<VaultItemEntity>,
        targetFolderId: Long?,
        extractSeparately: Boolean = true,
        onProgress: ((OperationProgressState) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ): Result<Int> = withContext(Dispatchers.IO) {
        var totalExtracted = 0
        val count = items.size

        for ((index, item) in items.withIndex()) {
            if (isCancelled()) throw CancellationException("Bulk extraction cancelled")

            val option = if (extractSeparately) {
                ArchiveExtractionOption.CREATE_NEW_FOLDER_AND_EXTRACT
            } else {
                ArchiveExtractionOption.EXTRACT_HERE
            }

            val result = extractArchive(
                item = item,
                targetFolderId = targetFolderId,
                option = option,
                onProgress = { state ->
                    val overallProgress = (index.toFloat() + state.progress) / count.toFloat()
                    onProgress?.invoke(
                        state.copy(
                            progress = overallProgress,
                            currentIndex = index + 1,
                            totalCount = count
                        )
                    )
                },
                isCancelled = isCancelled
            )

            if (result.isSuccess) {
                totalExtracted += result.getOrDefault(0)
            }
        }

        Result.success(totalExtracted)
    }

    private fun validateAndSanitizeArchiveTitle(title: String): String {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            return "Archive_${System.currentTimeMillis()}.zip"
        }
        val clean = trimmed.replace(Regex("[/\\\\:?*\"<>|]"), "_")
        return if (clean.endsWith(".zip", ignoreCase = true)) clean else "$clean.zip"
    }
}
