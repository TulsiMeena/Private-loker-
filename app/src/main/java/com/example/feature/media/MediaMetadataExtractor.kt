package com.example.feature.media

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.storage.FileShredder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Metadata extractor for encrypted media objects.
 * Never persists unencrypted temporary metadata to disk.
 */
object MediaMetadataExtractor {

    suspend fun extractImageMetadata(
        repository: VaultRepository,
        item: VaultItemEntity
    ): ImageMetadataInfo = withContext(Dispatchers.IO) {
        try {
            val bytesResult = repository.decryptItemBytes(item)
            val bytes = bytesResult.getOrNull()
            if (bytes == null || bytes.isEmpty()) {
                return@withContext ImageMetadataInfo(
                    mimeType = item.mimeType,
                    sizeBytes = item.sizeBytes
                )
            }

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            ImageMetadataInfo(
                width = if (options.outWidth > 0) options.outWidth else null,
                height = if (options.outHeight > 0) options.outHeight else null,
                mimeType = options.outMimeType ?: item.mimeType,
                sizeBytes = item.sizeBytes,
                colorSpace = options.outColorSpace?.name
            )
        } catch (_: Exception) {
            ImageMetadataInfo(mimeType = item.mimeType, sizeBytes = item.sizeBytes)
        }
    }

    suspend fun extractVideoMetadata(
        repository: VaultRepository,
        item: VaultItemEntity
    ): VideoMetadataInfo = withContext(Dispatchers.IO) {
        val previewResult = repository.createTransientPreview(item)
        val transientFile = previewResult.getOrNull()
            ?: return@withContext VideoMetadataInfo(mimeType = item.mimeType, sizeBytes = item.sizeBytes)

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(transientFile.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val mimeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

            VideoMetadataInfo(
                width = widthStr?.toIntOrNull(),
                height = heightStr?.toIntOrNull(),
                durationMs = durationStr?.toLongOrNull() ?: 0L,
                mimeType = mimeStr ?: item.mimeType,
                sizeBytes = item.sizeBytes,
                rotation = rotationStr?.toIntOrNull() ?: 0,
                bitrate = bitrateStr?.toLongOrNull()
            )
        } catch (_: Exception) {
            VideoMetadataInfo(mimeType = item.mimeType, sizeBytes = item.sizeBytes)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
            FileShredder.shredAndPurge(transientFile)
        }
    }

    suspend fun extractAudioMetadata(
        repository: VaultRepository,
        item: VaultItemEntity
    ): AudioMetadataInfo = withContext(Dispatchers.IO) {
        val previewResult = repository.createTransientPreview(item)
        val transientFile = previewResult.getOrNull()
            ?: return@withContext AudioMetadataInfo(mimeType = item.mimeType, sizeBytes = item.sizeBytes)

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(transientFile.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val mimeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

            AudioMetadataInfo(
                durationMs = durationStr?.toLongOrNull() ?: 0L,
                title = title?.ifBlank { null },
                artist = artist?.ifBlank { null },
                album = album?.ifBlank { null },
                bitrate = bitrateStr?.toLongOrNull(),
                mimeType = mimeStr ?: item.mimeType,
                sizeBytes = item.sizeBytes
            )
        } catch (_: Exception) {
            AudioMetadataInfo(mimeType = item.mimeType, sizeBytes = item.sizeBytes)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
            FileShredder.shredAndPurge(transientFile)
        }
    }
}
