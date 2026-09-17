package com.example.feature.documents

import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.storage.VaultCategory
import java.io.InputStream

/**
 * Production-ready Document Vault handler implementing secure document lifecycle.
 */
interface DocumentVaultHandler {
    suspend fun importDocument(name: String, mimeType: String, stream: InputStream): Result<VaultItemEntity>
    suspend fun previewDocument(item: VaultItemEntity): Result<ByteArray>
}

class DefaultDocumentVaultHandler(
    private val repository: VaultRepository
) : DocumentVaultHandler {
    override suspend fun importDocument(name: String, mimeType: String, stream: InputStream): Result<VaultItemEntity> {
        return repository.importItem(
            title = name,
            category = VaultCategory.DOCUMENT,
            mimeType = mimeType,
            inputStream = stream
        )
    }

    override suspend fun previewDocument(item: VaultItemEntity): Result<ByteArray> {
        return repository.decryptItemBytes(item)
    }
}
