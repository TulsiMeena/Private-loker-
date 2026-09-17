package com.example.core.database

import android.content.Context
import android.net.Uri
import com.example.core.lifecycle.EmptyTrashReport
import com.example.core.lifecycle.RestoreConflictResolution
import com.example.core.lifecycle.RestoreOutcome
import com.example.core.storage.SecureThumbnailProvider
import com.example.core.storage.StorageImportResult
import com.example.core.storage.VaultCategory
import com.example.core.storage.VaultStorageManager
import com.example.feature.documents.DocumentThumbnailHelper
import com.example.feature.media.MediaThumbnailHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

sealed class DuplicateCheckResult {
    data object None : DuplicateCheckResult()
    data class ContentDuplicate(val existingItem: VaultItemEntity) : DuplicateCheckResult()
    data class NameConflict(val existingItem: VaultItemEntity) : DuplicateCheckResult()
}

enum class DuplicateResolution {
    KEEP_BOTH,
    REPLACE,
    SKIP,
    CANCEL
}

/**
 * Clean MVVM repository coordinating encrypted physical storage, Room database metadata,
 * and immutable security audit logs.
 */
class VaultRepository(
    private val vaultDao: VaultDao,
    private val vaultFolderDao: VaultFolderDao,
    private val securityAuditDao: SecurityAuditDao,
    val storageManager: VaultStorageManager,
    private val documentBookmarkDao: DocumentBookmarkDao? = null,
    val backupHistoryDao: BackupHistoryDao? = null
) {
    val allItems: Flow<List<VaultItemEntity>> = vaultDao.getAllItems().flowOn(Dispatchers.IO)
    val allDocuments: Flow<List<VaultItemEntity>> = vaultDao.getAllDocuments().flowOn(Dispatchers.IO)
    val allMedia: Flow<List<VaultItemEntity>> = vaultDao.getAllMedia().flowOn(Dispatchers.IO)
    val imageCount: Flow<Int> = vaultDao.getItemCountByCategory(VaultCategory.IMAGE.name).flowOn(Dispatchers.IO)
    val videoCount: Flow<Int> = vaultDao.getItemCountByCategory(VaultCategory.VIDEO.name).flowOn(Dispatchers.IO)
    val audioCount: Flow<Int> = vaultDao.getItemCountByCategory(VaultCategory.AUDIO.name).flowOn(Dispatchers.IO)
    val recentItems: Flow<List<VaultItemEntity>> = vaultDao.getRecentItems(5).flowOn(Dispatchers.IO)
    val recentlyAccessedItems: Flow<List<VaultItemEntity>> = vaultDao.getRecentlyAccessedItems(15).flowOn(Dispatchers.IO)
    val totalItemCount: Flow<Int> = vaultDao.getTotalItemCount().flowOn(Dispatchers.IO)
    val totalSizeBytes: Flow<Long?> = vaultDao.getTotalSizeBytes().flowOn(Dispatchers.IO)
    val favoriteItems: Flow<List<VaultItemEntity>> = vaultDao.getFavoriteItems().flowOn(Dispatchers.IO)
    val trashItems: Flow<List<VaultItemEntity>> = vaultDao.getTrashItems().flowOn(Dispatchers.IO)
    val trashItemCount: Flow<Int> = vaultDao.getTrashItemCount().flowOn(Dispatchers.IO)
    val auditLogs: Flow<List<SecurityAuditLogEntity>> = securityAuditDao.getRecentLogs(50).flowOn(Dispatchers.IO)
    val backupHistory: Flow<List<BackupHistoryEntity>> = backupHistoryDao?.getAllBackupHistory() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun getLatestBackup(): BackupHistoryEntity? = withContext(Dispatchers.IO) {
        backupHistoryDao?.getLatestBackup()
    }

    suspend fun getLatestVerifiedBackup(): BackupHistoryEntity? = withContext(Dispatchers.IO) {
        backupHistoryDao?.getLatestVerifiedBackup()
    }

    suspend fun getAllBookmarksList(): List<DocumentBookmarkEntity> = withContext(Dispatchers.IO) {
        documentBookmarkDao?.getAllBookmarksList() ?: emptyList()
    }

    fun getRecentItems(limit: Int = 30): Flow<List<VaultItemEntity>> {
        return vaultDao.getRecentItems(limit).flowOn(Dispatchers.IO)
    }

    fun getAllTags(): Flow<List<String>> {
        return vaultDao.getAllTags().flowOn(Dispatchers.IO)
    }

    suspend fun trashItems(itemIds: List<Long>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            itemIds.forEach { id -> vaultDao.moveToTrash(id) }
            logSecurityEvent(
                action = "BATCH_TRASH",
                details = "Moved ${itemIds.size} items to trash",
                isSuccess = true
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getItemsByCategory(category: VaultCategory): Flow<List<VaultItemEntity>> {
        return vaultDao.getItemsByCategory(category.name).flowOn(Dispatchers.IO)
    }

    fun getItemCountByCategory(category: VaultCategory): Flow<Int> {
        return vaultDao.getItemCountByCategory(category.name).flowOn(Dispatchers.IO)
    }

    fun getItemsInFolder(folderId: Long?): Flow<List<VaultItemEntity>> {
        return vaultDao.getItemsInFolder(folderId).flowOn(Dispatchers.IO)
    }

    fun getFolders(parentId: Long?): Flow<List<VaultFolderEntity>> {
        return vaultFolderDao.getFolders(parentId).flowOn(Dispatchers.IO)
    }

    fun getAllFolders(): Flow<List<VaultFolderEntity>> {
        return vaultFolderDao.getAllFolders().flowOn(Dispatchers.IO)
    }

    suspend fun getAllFoldersList(): List<VaultFolderEntity> = withContext(Dispatchers.IO) {
        vaultFolderDao.getAllFoldersList()
    }

    suspend fun getAllItemsList(): List<VaultItemEntity> = withContext(Dispatchers.IO) {
        vaultDao.getAllItemsList()
    }

    fun searchItems(query: String): Flow<List<VaultItemEntity>> {
        return vaultDao.searchItems(query).flowOn(Dispatchers.IO)
    }

    /**
     * Checks if a file with identical checksum or title already exists in the target directory.
     */
    suspend fun checkForDuplicate(
        checksumSha256: String,
        title: String,
        folderId: Long? = null
    ): DuplicateCheckResult = withContext(Dispatchers.IO) {
        if (checksumSha256.isNotEmpty()) {
            val contentMatch = vaultDao.findByChecksum(checksumSha256)
            if (contentMatch != null) {
                return@withContext DuplicateCheckResult.ContentDuplicate(contentMatch)
            }
        }
        val nameMatch = vaultDao.findByTitleAndFolder(title, folderId)
        if (nameMatch != null) {
            return@withContext DuplicateCheckResult.NameConflict(nameMatch)
        }
        DuplicateCheckResult.None
    }

    /**
     * Imports and encrypts a file with progress reporting and atomic finalization.
     */
    suspend fun importItem(
        title: String,
        category: VaultCategory,
        mimeType: String,
        inputStream: InputStream,
        folderId: Long? = null,
        expectedSizeBytes: Long = -1L,
        resolution: DuplicateResolution = DuplicateResolution.KEEP_BOTH,
        onProgress: ((bytesProcessed: Long, totalBytes: Long, phase: String) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ): Result<VaultItemEntity> = withContext(Dispatchers.IO) {
        try {
            if (resolution == DuplicateResolution.CANCEL) {
                return@withContext Result.failure(IllegalStateException("Import cancelled by user"))
            }

            val importResult: StorageImportResult = storageManager.importAndEncrypt(
                inputStream = inputStream,
                category = category,
                expectedSizeBytes = expectedSizeBytes,
                onProgress = onProgress,
                isCancelled = isCancelled
            )

            var finalTitle = title
            if (resolution == DuplicateResolution.KEEP_BOTH) {
                // If title conflict exists, make unique
                val existing = vaultDao.findByTitleAndFolder(title, folderId)
                if (existing != null) {
                    val dotIdx = title.lastIndexOf('.')
                    finalTitle = if (dotIdx > 0) {
                        "${title.substring(0, dotIdx)} (1).${title.substring(dotIdx + 1)}"
                    } else {
                        "$title (1)"
                    }
                }
            } else if (resolution == DuplicateResolution.REPLACE) {
                // Remove existing conflict
                val existingName = vaultDao.findByTitleAndFolder(title, folderId)
                if (existingName != null) {
                    storageManager.deleteEncryptedFile(existingName.encryptedPath)
                    vaultDao.deleteItemById(existingName.id)
                }
            }

            val entity = VaultItemEntity(
                title = finalTitle,
                category = category.name,
                encryptedPath = importResult.relativePath,
                mimeType = mimeType,
                sizeBytes = importResult.originalSizeBytes,
                encryptedSizeBytes = importResult.encryptedSizeBytes,
                checksumSha256 = importResult.checksumSha256,
                folderId = folderId
            )
            val id = vaultDao.insertItem(entity)
            logSecurityEvent(
                action = "ENCRYPT_IMPORT",
                details = "Securely imported $finalTitle ($mimeType, ${importResult.originalSizeBytes} bytes)",
                isSuccess = true
            )
            Result.success(entity.copy(id = id))
        } catch (e: Exception) {
            logSecurityEvent(
                action = "ENCRYPT_IMPORT_FAILED",
                details = "Failed to import item: ${e.message}",
                isSuccess = false
            )
            Result.failure(e)
        }
    }

    suspend fun getFolderById(id: Long): VaultFolderEntity? = withContext(Dispatchers.IO) {
        vaultFolderDao.getFolderById(id)
    }

    /**
     * Moves a file to the secure Trash. Physical encrypted container remains in the vault.
     */
    suspend fun moveToTrash(item: VaultItemEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            vaultDao.moveToTrash(item.id)
            logSecurityEvent(
                action = "MOVE_TO_TRASH",
                details = "Moved #${item.id} (${item.title}) to encrypted trash",
                isSuccess = true
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks if restoring an item would cause a filename collision in the destination folder.
     */
    suspend fun checkRestoreConflict(item: VaultItemEntity): VaultItemEntity? = withContext(Dispatchers.IO) {
        val targetFolderId = if (item.originalFolderId != null && vaultFolderDao.getFolderById(item.originalFolderId) != null) {
            item.originalFolderId
        } else {
            null
        }
        val existing = vaultDao.findByTitleAndFolder(item.title, targetFolderId)
        if (existing != null && !existing.isTrash && existing.id != item.id) {
            existing
        } else {
            null
        }
    }

    /**
     * Restores a file from Trash back to active vault partition.
     * Verifies original location, checks for filename conflicts, and applies conflict resolution if requested.
     */
    suspend fun restoreFromTrash(
        item: VaultItemEntity,
        resolution: RestoreConflictResolution? = null
    ): RestoreOutcome = withContext(Dispatchers.IO) {
        try {
            // 1. Verify if original folder still exists
            val originalFolderExists = item.originalFolderId != null && vaultFolderDao.getFolderById(item.originalFolderId) != null
            val targetFolderId = if (originalFolderExists) item.originalFolderId else null
            val restoredToRoot = item.originalFolderId != null && !originalFolderExists

            // 2. Check for filename collision
            val existing = vaultDao.findByTitleAndFolder(item.title, targetFolderId)
            val hasConflict = existing != null && !existing.isTrash && existing.id != item.id

            if (hasConflict && existing != null) {
                when (resolution) {
                    null -> {
                        return@withContext RestoreOutcome.Conflict(
                            itemToRestore = item,
                            existingConflictItem = existing
                        )
                    }
                    RestoreConflictResolution.KEEP_EXISTING -> {
                        // Keep existing active item, do not restore this item from trash
                        return@withContext RestoreOutcome.Success(
                            item = item,
                            renamed = false,
                            restoredToRoot = restoredToRoot
                        )
                    }
                    RestoreConflictResolution.REPLACE -> {
                        // Move existing conflicting item to trash, restore this item
                        vaultDao.moveToTrash(existing.id)
                        vaultDao.restoreItemWithDetails(
                            id = item.id,
                            targetFolderId = targetFolderId,
                            title = item.title,
                            modifiedAt = System.currentTimeMillis()
                        )
                        logSecurityEvent(
                            action = "RESTORE_FROM_TRASH",
                            details = "Restored #${item.id} (${item.title}), replaced active item #${existing.id}",
                            isSuccess = true
                        )
                        val updated = vaultDao.getItemById(item.id) ?: item
                        return@withContext RestoreOutcome.Success(
                            item = updated,
                            renamed = false,
                            restoredToRoot = restoredToRoot
                        )
                    }
                    RestoreConflictResolution.KEEP_BOTH -> {
                        val nonConflictingTitle = generateNonConflictingTitle(item.title, targetFolderId)
                        vaultDao.restoreItemWithDetails(
                            id = item.id,
                            targetFolderId = targetFolderId,
                            title = nonConflictingTitle,
                            modifiedAt = System.currentTimeMillis()
                        )
                        logSecurityEvent(
                            action = "RESTORE_FROM_TRASH",
                            details = "Restored #${item.id} renamed to '$nonConflictingTitle' to avoid collision",
                            isSuccess = true
                        )
                        val updated = vaultDao.getItemById(item.id) ?: item
                        return@withContext RestoreOutcome.Success(
                            item = updated,
                            renamed = true,
                            restoredToRoot = restoredToRoot
                        )
                    }
                }
            } else {
                // No conflict, restore directly
                vaultDao.restoreItemWithDetails(
                    id = item.id,
                    targetFolderId = targetFolderId,
                    title = item.title,
                    modifiedAt = System.currentTimeMillis()
                )
                logSecurityEvent(
                    action = "RESTORE_FROM_TRASH",
                    details = "Restored #${item.id} (${item.title}) from encrypted trash",
                    isSuccess = true
                )
                val updated = vaultDao.getItemById(item.id) ?: item
                return@withContext RestoreOutcome.Success(
                    item = updated,
                    renamed = false,
                    restoredToRoot = restoredToRoot
                )
            }
        } catch (e: Exception) {
            logSecurityEvent(
                action = "RESTORE_FAILED",
                details = "Failed to restore #${item.id}: ${e.message}",
                isSuccess = false
            )
            RestoreOutcome.Failure(item, e.message ?: "Restore failed")
        }
    }

    /**
     * Batch restore a list of items from trash.
     */
    suspend fun batchRestore(
        items: List<VaultItemEntity>,
        defaultResolution: RestoreConflictResolution = RestoreConflictResolution.KEEP_BOTH
    ): List<RestoreOutcome> = withContext(Dispatchers.IO) {
        items.map { restoreFromTrash(it, defaultResolution) }
    }

    /**
     * Permanently shreds an encrypted vault object and deletes its metadata, bookmarks, thumbnails, and temporary files.
     * Note: Wear-leveling on modern flash storage cannot physically guarantee sector-level overwriting.
     */
    suspend fun permanentDeleteItem(item: VaultItemEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            storageManager.deleteEncryptedFile(item.encryptedPath)
            documentBookmarkDao?.deleteBookmarksForDocument(item.id)
            vaultDao.deleteItemById(item.id)

            // Evict in-memory thumbnail caches
            SecureThumbnailProvider.clearCache()
            MediaThumbnailHelper.clearCache()
            DocumentThumbnailHelper.clearCache()

            // Purge temporary files
            storageManager.purgeTemporaryFiles()

            logSecurityEvent(
                action = "PERMANENT_DELETE",
                details = "Permanently shredded #${item.id} (${item.title}) and purged all caches",
                isSuccess = true
            )
            Result.success(Unit)
        } catch (e: Exception) {
            logSecurityEvent(
                action = "PERMANENT_DELETE_FAILED",
                details = "Failed permanent deletion for #${item.id}: ${e.message}",
                isSuccess = false
            )
            Result.failure(e)
        }
    }

    /**
     * Batch permanent deletion for selected items.
     */
    suspend fun batchPermanentDelete(items: List<VaultItemEntity>): Result<EmptyTrashReport> = withContext(Dispatchers.IO) {
        var deletedCount = 0
        var failedCount = 0
        var freedBytes = 0L
        val failedTitles = mutableListOf<String>()

        for (item in items) {
            try {
                storageManager.deleteEncryptedFile(item.encryptedPath)
                documentBookmarkDao?.deleteBookmarksForDocument(item.id)
                vaultDao.deleteItemById(item.id)
                deletedCount++
                freedBytes += item.sizeBytes
            } catch (e: Exception) {
                failedCount++
                failedTitles.add(item.title)
                android.util.Log.e("VaultRepository", "Failed deleting #${item.id}: ${e.message}")
            }
        }

        SecureThumbnailProvider.clearCache()
        MediaThumbnailHelper.clearCache()
        DocumentThumbnailHelper.clearCache()
        storageManager.purgeTemporaryFiles()

        val report = EmptyTrashReport(deletedCount, failedCount, freedBytes, failedTitles)
        logSecurityEvent(
            action = "BATCH_PERMANENT_DELETE",
            details = "Batch delete: $deletedCount purged, $failedCount failed ($freedBytes bytes released)",
            isSuccess = report.isFullSuccess
        )
        Result.success(report)
    }

    /**
     * Shreds and clears all items currently in the Trash.
     * Reports partial failures while preserving metadata for failed items so files are not silently lost.
     */
    suspend fun emptyTrash(): Result<EmptyTrashReport> = withContext(Dispatchers.IO) {
        try {
            val trashList = vaultDao.getTrashItemsList()
            if (trashList.isEmpty()) {
                return@withContext Result.success(EmptyTrashReport(0, 0, 0L))
            }

            var deletedCount = 0
            var failedCount = 0
            var freedBytes = 0L
            val failedTitles = mutableListOf<String>()

            for (item in trashList) {
                try {
                    storageManager.deleteEncryptedFile(item.encryptedPath)
                    documentBookmarkDao?.deleteBookmarksForDocument(item.id)
                    vaultDao.deleteItemById(item.id)
                    deletedCount++
                    freedBytes += item.sizeBytes
                } catch (e: Exception) {
                    failedCount++
                    failedTitles.add(item.title)
                    android.util.Log.e("VaultRepository", "Empty trash item error #${item.id}: ${e.message}")
                }
            }

            SecureThumbnailProvider.clearCache()
            MediaThumbnailHelper.clearCache()
            DocumentThumbnailHelper.clearCache()
            storageManager.purgeTemporaryFiles()

            val report = EmptyTrashReport(deletedCount, failedCount, freedBytes, failedTitles)
            logSecurityEvent(
                action = "EMPTY_TRASH",
                details = "Empty Trash: $deletedCount purged, $failedCount failed ($freedBytes bytes released)",
                isSuccess = report.isFullSuccess
            )
            Result.success(report)
        } catch (e: Exception) {
            logSecurityEvent(
                action = "EMPTY_TRASH_FAILED",
                details = "Empty trash failed: ${e.message}",
                isSuccess = false
            )
            Result.failure(e)
        }
    }

    private suspend fun generateNonConflictingTitle(title: String, folderId: Long?): String {
        val extension = if (title.contains('.')) ".${title.substringAfterLast('.')}" else ""
        val baseName = title.substringBeforeLast('.')
        var counter = 1
        var candidate = "$baseName (restored)$extension"
        while (vaultDao.findByTitleAndFolder(candidate, folderId) != null) {
            candidate = "$baseName (restored $counter)$extension"
            counter++
        }
        return candidate
    }

    // Deprecated alias for backwards compatibility
    suspend fun deleteItem(item: VaultItemEntity): Result<Unit> = moveToTrash(item)

    suspend fun moveItemToFolder(itemId: Long, targetFolderId: Long?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            vaultDao.moveItemToFolder(itemId, targetFolderId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun moveItemToFolder(item: VaultItemEntity, targetFolderId: Long?): Result<Unit> =
        moveItemToFolder(item.id, targetFolderId)

    suspend fun toggleFavorite(item: VaultItemEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            vaultDao.setFavorite(item.id, !item.isFavorite)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setFavorite(item: VaultItemEntity, isFavorite: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            vaultDao.setFavorite(item.id, isFavorite)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun moveToFolder(item: VaultItemEntity, targetFolderId: Long?): Result<Unit> =
        moveItemToFolder(item.id, targetFolderId)

    suspend fun updateTags(item: VaultItemEntity, tags: String): Result<Unit> =
        updateItemTags(item.id, tags)

    suspend fun renameItem(itemId: Long, newTitle: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            vaultDao.renameItem(itemId, newTitle)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameItem(item: VaultItemEntity, newTitle: String): Result<Unit> =
        renameItem(item.id, newTitle)

    suspend fun updateItemTags(itemId: Long, tags: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            vaultDao.updateTags(itemId, tags)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateItemTags(item: VaultItemEntity, tags: String): Result<Unit> =
        updateItemTags(item.id, tags)

    // Folders
    suspend fun createFolder(name: String, parentId: Long? = null): Result<VaultFolderEntity> = withContext(Dispatchers.IO) {
        try {
            val folder = VaultFolderEntity(name = name, parentId = parentId)
            val id = vaultFolderDao.insertFolder(folder)
            logSecurityEvent(
                action = "CREATE_FOLDER",
                details = "Created virtual folder '$name' (ID: $id)",
                isSuccess = true
            )
            Result.success(folder.copy(id = id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameFolder(id: Long, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            vaultFolderDao.renameFolder(id, newName)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameFolder(folder: VaultFolderEntity, newName: String): Result<Unit> =
        renameFolder(folder.id, newName)

    suspend fun deleteFolder(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            vaultFolderDao.deleteFolderById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFolder(folder: VaultFolderEntity): Result<Unit> =
        deleteFolder(folder.id)

    // Decryption & Export
    suspend fun decryptItemBytes(item: VaultItemEntity): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val bytes = storageManager.decryptToMemory(item.encryptedPath)
            logSecurityEvent(
                action = "DECRYPT_READ",
                details = "Decrypted record #${item.id} (${bytes.size} bytes) in memory",
                isSuccess = true
            )
            Result.success(bytes)
        } catch (e: Exception) {
            logSecurityEvent(
                action = "DECRYPT_FAILED",
                details = "Decryption failure on #${item.id}: ${e.message}",
                isSuccess = false
            )
            Result.failure(e)
        }
    }

    suspend fun createTransientPreview(item: VaultItemEntity): Result<File> = withContext(Dispatchers.IO) {
        try {
            val ext = item.title.substringAfterLast('.', "tmp")
            val tempFile = storageManager.createTemporaryDecryptedFile(item.encryptedPath, ext)
            Result.success(tempFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportItemToStream(
        item: VaultItemEntity,
        outputStream: OutputStream,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val count = storageManager.engine.decryptToStream(
                relativePath = item.encryptedPath,
                outputStream = outputStream,
                onProgress = onProgress
            )
            logSecurityEvent(
                action = "EXPORT_FILE",
                details = "Exported #${item.id} (${item.title}) to external destination ($count bytes)",
                isSuccess = true
            )
            Result.success(count)
        } catch (e: Exception) {
            logSecurityEvent(
                action = "EXPORT_FAILED",
                details = "Failed export for #${item.id}: ${e.message}",
                isSuccess = false
            )
            Result.failure(e)
        }
    }

    suspend fun exportItemToFile(item: VaultItemEntity, destFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            FileOutputStream(destFile).use { fos ->
                val result = exportItemToStream(item, fos)
                if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Export stream failed"))
            }
            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun queryFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) name = cursor.getString(idx)
                    }
                }
            } catch (_: Exception) {}
        }
        return name ?: uri.lastPathSegment
    }

    suspend fun importFileFromStream(
        stream: InputStream,
        fileName: String,
        folderId: Long? = null,
        mimeType: String? = null,
        duplicateResolution: DuplicateResolution = DuplicateResolution.KEEP_BOTH
    ): Result<VaultItemEntity> {
        val resolvedMime = mimeType ?: "application/octet-stream"
        val cat = VaultCategory.fromFileNameAndMime(fileName, resolvedMime)
        return importItem(
            title = fileName,
            category = cat,
            mimeType = resolvedMime,
            inputStream = stream,
            folderId = folderId,
            resolution = duplicateResolution
        )
    }

    suspend fun getItemById(id: Long): VaultItemEntity? = withContext(Dispatchers.IO) {
        vaultDao.getItemById(id)
    }

    suspend fun recordFileAccess(item: VaultItemEntity) = withContext(Dispatchers.IO) {
        vaultDao.updateLastAccessed(item.id)
    }

    suspend fun clearRecentHistory() = withContext(Dispatchers.IO) {
        vaultDao.clearRecentHistory()
        logSecurityEvent(
            action = "CLEAR_RECENT_HISTORY",
            details = "Cleared recently accessed metadata history",
            isSuccess = true
        )
    }

    suspend fun copyItem(item: VaultItemEntity, targetFolderId: Long? = item.folderId): Result<VaultItemEntity> = withContext(Dispatchers.IO) {
        try {
            val decryptedBytes = storageManager.decryptToMemory(item.encryptedPath)
            val baseName = item.title.substringBeforeLast('.')
            val extension = if (item.title.contains('.')) ".${item.title.substringAfterLast('.')}" else ""
            val copyTitle = "$baseName (Copy)$extension"
            val inputStream = java.io.ByteArrayInputStream(decryptedBytes)
            val category = try {
                VaultCategory.valueOf(item.category)
            } catch (_: Exception) {
                VaultCategory.OTHER
            }
            val result = importItem(
                title = copyTitle,
                category = category,
                mimeType = item.mimeType,
                inputStream = inputStream,
                folderId = targetFolderId,
                expectedSizeBytes = decryptedBytes.size.toLong(),
                resolution = DuplicateResolution.KEEP_BOTH
            )
            if (result.isSuccess) {
                logSecurityEvent(
                    action = "COPY_FILE",
                    details = "Secure copy created for #${item.id} -> '$copyTitle'",
                    isSuccess = true
                )
            }
            result
        } catch (e: Exception) {
            logSecurityEvent(
                action = "COPY_FAILED",
                details = "Copy failed for #${item.id}: ${e.message}",
                isSuccess = false
            )
            Result.failure(e)
        }
    }

    suspend fun duplicateItem(item: VaultItemEntity): Result<VaultItemEntity> = copyItem(item, item.folderId)

    fun getBookmarksForDocument(documentId: Long): Flow<List<DocumentBookmarkEntity>> {
        return documentBookmarkDao?.getBookmarksForDocument(documentId)?.flowOn(Dispatchers.IO)
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    suspend fun addBookmark(documentId: Long, title: String, pageNumber: Int): Result<DocumentBookmarkEntity> = withContext(Dispatchers.IO) {
        try {
            val dao = documentBookmarkDao ?: return@withContext Result.failure(IllegalStateException("Bookmark DAO not configured"))
            val entity = DocumentBookmarkEntity(
                documentId = documentId,
                title = title.ifBlank { "Page $pageNumber" },
                pageNumber = pageNumber
            )
            val id = dao.insertBookmark(entity)
            Result.success(entity.copy(id = id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBookmark(bookmark: DocumentBookmarkEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            documentBookmarkDao?.deleteBookmark(bookmark)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateFileContent(item: VaultItemEntity, newContent: String): Result<VaultItemEntity> = withContext(Dispatchers.IO) {
        try {
            val bytes = newContent.toByteArray(Charsets.UTF_8)
            val category = try { VaultCategory.valueOf(item.category) } catch (_: Exception) { VaultCategory.TEXT }
            val importResult = storageManager.importAndEncrypt(
                inputStream = java.io.ByteArrayInputStream(bytes),
                category = category,
                expectedSizeBytes = bytes.size.toLong()
            )
            // Atomically shred/delete old encrypted file
            storageManager.engine.deleteEncryptedFile(item.encryptedPath)

            val updatedEntity = item.copy(
                encryptedPath = importResult.relativePath,
                sizeBytes = importResult.originalSizeBytes,
                encryptedSizeBytes = importResult.encryptedSizeBytes,
                checksumSha256 = importResult.checksumSha256,
                modifiedAt = System.currentTimeMillis()
            )
            vaultDao.updateItem(updatedEntity)
            logSecurityEvent(
                action = "UPDATE_FILE_CONTENT",
                details = "Atomic content update and re-encryption for #${item.id} (${item.title})",
                isSuccess = true
            )
            Result.success(updatedEntity)
        } catch (e: Exception) {
            logSecurityEvent(
                action = "UPDATE_FILE_FAILED",
                details = "Failed saving content for #${item.id}: ${e.message}",
                isSuccess = false
            )
            Result.failure(e)
        }
    }

    suspend fun createNewFile(
        title: String,
        content: String,
        category: VaultCategory,
        folderId: Long? = null
    ): Result<VaultItemEntity> = withContext(Dispatchers.IO) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val mimeType = when (category) {
            VaultCategory.CODE -> "text/plain"
            VaultCategory.TEXT -> "text/plain"
            else -> "text/plain"
        }
        importItem(
            title = title,
            category = category,
            mimeType = mimeType,
            inputStream = java.io.ByteArrayInputStream(bytes),
            folderId = folderId,
            expectedSizeBytes = bytes.size.toLong(),
            resolution = DuplicateResolution.KEEP_BOTH
        )
    }

    suspend fun batchMove(items: List<VaultItemEntity>, targetFolderId: Long?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            items.forEach { item ->
                vaultDao.moveItemToFolder(item.id, targetFolderId)
            }
            logSecurityEvent(
                action = "BATCH_MOVE",
                details = "Moved ${items.size} items to folder ID $targetFolderId",
                isSuccess = true
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun batchDelete(items: List<VaultItemEntity>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            items.forEach { item ->
                vaultDao.moveToTrash(item.id)
            }
            logSecurityEvent(
                action = "BATCH_DELETE",
                details = "Moved ${items.size} items to encrypted trash",
                isSuccess = true
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun batchFavorite(items: List<VaultItemEntity>, isFavorite: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            items.forEach { item ->
                vaultDao.setFavorite(item.id, isFavorite)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun batchAddTag(items: List<VaultItemEntity>, tag: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanTag = tag.trim().replace(",", "")
            if (cleanTag.isEmpty()) return@withContext Result.success(Unit)

            items.forEach { item ->
                val currentTags = item.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
                currentTags.add(cleanTag)
                vaultDao.updateTags(item.id, currentTags.joinToString(", "))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun batchRemoveTag(items: List<VaultItemEntity>, tag: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val targetTag = tag.trim().lowercase()
            items.forEach { item ->
                val remainingTags = item.tags.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.equals(targetTag, ignoreCase = true) }
                vaultDao.updateTags(item.id, remainingTags.joinToString(", "))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameTag(oldTag: String, newTag: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val oldClean = oldTag.trim()
            val newClean = newTag.trim().replace(",", "")
            if (oldClean.isBlank() || newClean.isBlank()) return@withContext Result.success(0)

            val allItems = vaultDao.getAllItemsList()
            var modifiedCount = 0
            allItems.forEach { item ->
                val tagsList = item.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (tagsList.any { it.equals(oldClean, ignoreCase = true) }) {
                    val updatedTags = tagsList.map {
                        if (it.equals(oldClean, ignoreCase = true)) newClean else it
                    }.distinct()
                    vaultDao.updateTags(item.id, updatedTags.joinToString(", "))
                    modifiedCount++
                }
            }
            logSecurityEvent(
                action = "RENAME_TAG",
                details = "Renamed tag '$oldClean' to '$newClean' across $modifiedCount items",
                isSuccess = true
            )
            Result.success(modifiedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTag(tagToDelete: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val target = tagToDelete.trim()
            if (target.isBlank()) return@withContext Result.success(0)

            val allItems = vaultDao.getAllItemsList()
            var modifiedCount = 0
            allItems.forEach { item ->
                val tagsList = item.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (tagsList.any { it.equals(target, ignoreCase = true) }) {
                    val updatedTags = tagsList.filter { !it.equals(target, ignoreCase = true) }
                    vaultDao.updateTags(item.id, updatedTags.joinToString(", "))
                    modifiedCount++
                }
            }
            logSecurityEvent(
                action = "DELETE_TAG",
                details = "Deleted tag '$target' from $modifiedCount items",
                isSuccess = true
            )
            Result.success(modifiedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun mergeTags(sourceTag: String, targetTag: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val src = sourceTag.trim()
            val dst = targetTag.trim().replace(",", "")
            if (src.isBlank() || dst.isBlank() || src.equals(dst, ignoreCase = true)) {
                return@withContext Result.success(0)
            }

            val allItems = vaultDao.getAllItemsList()
            var modifiedCount = 0
            allItems.forEach { item ->
                val tagsList = item.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (tagsList.any { it.equals(src, ignoreCase = true) }) {
                    val updatedTags = tagsList.map {
                        if (it.equals(src, ignoreCase = true)) dst else it
                    }.distinct()
                    vaultDao.updateTags(item.id, updatedTags.joinToString(", "))
                    modifiedCount++
                }
            }
            logSecurityEvent(
                action = "MERGE_TAGS",
                details = "Merged tag '$src' into '$dst' across $modifiedCount items",
                isSuccess = true
            )
            Result.success(modifiedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun moveFolder(folderId: Long, newParentId: Long?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (newParentId == folderId) {
                return@withContext Result.failure(IllegalArgumentException("Cannot move a folder into itself"))
            }
            // Circular hierarchy check
            if (newParentId != null) {
                var currentAncestorId: Long? = newParentId
                val allFolders = vaultFolderDao.getAllFoldersList().associateBy { it.id }
                while (currentAncestorId != null) {
                    if (currentAncestorId == folderId) {
                        return@withContext Result.failure(IllegalArgumentException("Cannot move a folder into its own subfolder"))
                    }
                    currentAncestorId = allFolders[currentAncestorId]?.parentId
                }
            }

            val folder = vaultFolderDao.getFolderById(folderId)
                ?: return@withContext Result.failure(IllegalArgumentException("Folder not found"))
            vaultFolderDao.updateFolder(folder.copy(parentId = newParentId))
            logSecurityEvent(
                action = "MOVE_FOLDER",
                details = "Moved folder '${folder.name}' (ID $folderId) to parent ID $newParentId",
                isSuccess = true
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * On-demand in-memory content search across encrypted code and text files.
     *
     * Security tradeoff:
     * - No unencrypted permanent search index is maintained on disk or in SQLite.
     * - Search decrypts files only into volatile memory during the active authenticated session.
     */
    suspend fun searchCodeAndTextContent(
        query: String,
        maxResults: Int = 30
    ): List<CodeContentSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val results = mutableListOf<CodeContentSearchResult>()
        val allItems = vaultDao.getAllItemsList().filter {
            !it.isTrash && (it.category == VaultCategory.CODE.name || it.category == VaultCategory.TEXT.name)
        }

        for (item in allItems) {
            if (results.size >= maxResults) break
            try {
                val bytes = storageManager.decryptToMemory(item.encryptedPath)
                val text = String(bytes, Charsets.UTF_8)
                val lines = text.lines()
                var matchCount = 0
                var firstMatchLine = -1
                var firstMatchSnippet = ""

                lines.forEachIndexed { idx, line ->
                    if (line.contains(query, ignoreCase = true)) {
                        matchCount++
                        if (firstMatchLine == -1) {
                            firstMatchLine = idx + 1
                            firstMatchSnippet = line.trim()
                        }
                    }
                }

                if (matchCount > 0) {
                    results.add(
                        CodeContentSearchResult(
                            item = item,
                            lineIndex = firstMatchLine,
                            snippet = firstMatchSnippet,
                            matchCount = matchCount
                        )
                    )
                }
            } catch (_: Exception) {
                // Ignore any unreadable files gracefully
            }
        }
        results
    }

    suspend fun logSecurityEvent(action: String, details: String, isSuccess: Boolean) = withContext(Dispatchers.IO) {
        securityAuditDao.insertLog(
            SecurityAuditLogEntity(
                action = action,
                details = details,
                isSuccess = isSuccess
            )
        )
    }

    suspend fun purgeAllVaultData() = withContext(Dispatchers.IO) {
        storageManager.purgeAllVaultFiles()
        vaultDao.clearAll()
        vaultFolderDao.clearAll()
        logSecurityEvent(
            action = "VAULT_PURGE",
            details = "Total vault metadata and session cache wiped",
            isSuccess = true
        )
    }

    suspend fun performConsistencyCheck(deep: Boolean = false): VaultConsistencyReport = withContext(Dispatchers.IO) {
        val allItems = vaultDao.getAllItemsList()
        val encryptedDir = storageManager.encryptedDir
        val physicalFiles = encryptedDir.listFiles()?.filter { it.isFile } ?: emptyList()
        val physicalFileNames = physicalFiles.map { it.name }.toSet()

        val missingPhysical = mutableListOf<Long>()
        var healthy = 0

        allItems.forEach { item ->
            val expectedFileName = java.io.File(item.encryptedPath).name
            if (physicalFileNames.contains(expectedFileName)) {
                healthy++
            } else {
                missingPhysical.add(item.id)
            }
        }

        val metadataFileNames = allItems.map { java.io.File(it.encryptedPath).name }.toSet()
        val orphanedFiles = physicalFiles.map { it.name }.filter { !metadataFileNames.contains(it) }

        val report = VaultConsistencyReport(
            totalMetadataRecords = allItems.size,
            totalPhysicalFiles = physicalFiles.size,
            healthyCount = healthy,
            missingPhysicalFiles = missingPhysical,
            orphanedPhysicalFiles = orphanedFiles,
            isConsistent = missingPhysical.isEmpty() && orphanedFiles.isEmpty()
        )

        logSecurityEvent(
            action = "CONSISTENCY_CHECK",
            details = "Verified ${allItems.size} records. Consistent: ${report.isConsistent}, Missing: ${missingPhysical.size}, Orphaned: ${orphanedFiles.size}",
            isSuccess = report.isConsistent
        )

        report
    }

    suspend fun clearSecurityLogs(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            securityAuditDao.clearLogs()
            logSecurityEvent(
                action = "SECURITY_LOGS_CLEARED",
                details = "Security activity logs were cleared by user",
                isSuccess = true
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun purgeTemporaryFiles() {
        storageManager.purgeTemporaryFiles()
    }

    fun getTemporaryFilesCount(): Int = storageManager.getTemporaryFilesCount()

    fun getTemporaryFilesSizeBytes(): Long = storageManager.getTemporaryFilesSizeBytes()

    fun getEncryptedStorageSizeBytes(): Long = storageManager.getEncryptedStorageSizeBytes()
}

data class VaultConsistencyReport(
    val totalMetadataRecords: Int,
    val totalPhysicalFiles: Int,
    val healthyCount: Int,
    val missingPhysicalFiles: List<Long>,
    val orphanedPhysicalFiles: List<String>,
    val isConsistent: Boolean
)

data class CodeContentSearchResult(
    val item: VaultItemEntity,
    val lineIndex: Int,
    val snippet: String,
    val matchCount: Int
)
