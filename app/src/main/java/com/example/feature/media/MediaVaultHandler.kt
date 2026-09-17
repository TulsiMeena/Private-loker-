package com.example.feature.media

import com.example.core.database.VaultItemEntity
import java.io.InputStream

/**
 * Extension point contract for Media Vault management (Encrypted Photos, RAW, SVG).
 */
interface MediaVaultHandler {
    suspend fun importMedia(name: String, mimeType: String, stream: InputStream): Result<VaultItemEntity>
    suspend fun loadEncryptedThumbnail(item: VaultItemEntity): Result<ByteArray>
    suspend fun secureViewerStream(item: VaultItemEntity): Result<InputStream>
}

class DefaultMediaVaultHandler : MediaVaultHandler {
    override suspend fun importMedia(name: String, mimeType: String, stream: InputStream): Result<VaultItemEntity> {
        return Result.failure(NotImplementedError("Media import handler reserved for next iteration"))
    }

    override suspend fun loadEncryptedThumbnail(item: VaultItemEntity): Result<ByteArray> {
        return Result.failure(NotImplementedError("Media thumbnail handler reserved for next iteration"))
    }

    override suspend fun secureViewerStream(item: VaultItemEntity): Result<InputStream> {
        return Result.failure(NotImplementedError("Media stream handler reserved for next iteration"))
    }
}
