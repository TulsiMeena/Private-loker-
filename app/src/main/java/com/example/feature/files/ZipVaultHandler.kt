package com.example.feature.files

import com.example.core.database.DuplicateResolution
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.storage.VaultCategory
import com.example.feature.files.archive.ZipArchiveEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Extension point contract for Compressed Archive Vault management (ZIP, TAR, GZ, 7Z).
 */
interface ZipVaultHandler {
    suspend fun importArchive(name: String, stream: InputStream, folderId: Long? = null): Result<VaultItemEntity>
    suspend fun listArchiveEntries(item: VaultItemEntity): Result<List<String>>
    suspend fun extractEntryToMemory(item: VaultItemEntity, entryPath: String): Result<ByteArray>
}

class DefaultZipVaultHandler(
    private val repository: VaultRepository
) : ZipVaultHandler {

    override suspend fun importArchive(
        name: String,
        stream: InputStream,
        folderId: Long?
    ): Result<VaultItemEntity> = withContext(Dispatchers.IO) {
        val cleanName = if (name.endsWith(".zip", ignoreCase = true)) name else "$name.zip"
        repository.importItem(
            title = cleanName,
            category = VaultCategory.ZIP,
            mimeType = "application/zip",
            inputStream = stream,
            folderId = folderId,
            resolution = DuplicateResolution.KEEP_BOTH
        )
    }

    override suspend fun listArchiveEntries(item: VaultItemEntity): Result<List<String>> = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            tempFile = repository.storageManager.createTemporaryDecryptedFile(item.encryptedPath, "${item.id}.zip")
            val (_, entries) = ZipArchiveEngine.readArchiveStructure(tempFile, item.id)
            Result.success(entries.map { it.fullPath })
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempFile?.let { repository.storageManager.releaseTemporaryFile(it) }
        }
    }

    override suspend fun extractEntryToMemory(item: VaultItemEntity, entryPath: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        var tempArchiveFile: File? = null
        var tempTargetFile: File? = null
        try {
            tempArchiveFile = repository.storageManager.createTemporaryDecryptedFile(item.encryptedPath, "${item.id}.zip")
            tempTargetFile = repository.storageManager.tempFileManager.createTransientFile("entry")
            ZipArchiveEngine.extractSingleEntry(tempArchiveFile, entryPath, tempTargetFile)
            val bytes = tempTargetFile.readBytes()
            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempArchiveFile?.let { repository.storageManager.releaseTemporaryFile(it) }
            tempTargetFile?.let { repository.storageManager.releaseTemporaryFile(it) }
        }
    }
}
