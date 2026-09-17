package com.example.feature.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.storage.FileShredder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ephemeral In-Memory Media Thumbnail Provider.
 *
 * Security Requirements:
 * - NEVER creates persistent or unencrypted disk cache outside the encrypted vault container.
 * - Extracts frames from transient decrypted buffers/files and immediately shreds transient files.
 * - Stores downsampled bitmaps in memory-bounded LruCache.
 * - Clears all cached bitmaps immediately upon vault lock or session expiration.
 */
object MediaThumbnailHelper {

    // 40-item in-memory LRU cache
    private val memoryCache = object : LruCache<String, ImageBitmap>(40) {}

    fun getCached(itemKey: String): ImageBitmap? {
        return synchronized(memoryCache) {
            memoryCache.get(itemKey)
        }
    }

    suspend fun loadThumbnail(
        item: VaultItemEntity,
        repository: VaultRepository,
        targetWidth: Int = 256,
        targetHeight: Int = 256
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${item.id}_${item.modifiedAt}"
        synchronized(memoryCache) {
            memoryCache.get(cacheKey)?.let { return@withContext it }
        }

        try {
            when (item.category) {
                "IMAGE" -> {
                    val bytesResult = repository.decryptItemBytes(item)
                    if (bytesResult.isFailure) return@withContext null
                    val bytes = bytesResult.getOrNull() ?: return@withContext null
                    if (bytes.isEmpty()) return@withContext null

                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

                    options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
                    options.inJustDecodeBounds = false
                    options.inPreferredConfig = Bitmap.Config.RGB_565

                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        ?: return@withContext null

                    val imageBitmap = bitmap.asImageBitmap()
                    synchronized(memoryCache) {
                        memoryCache.put(cacheKey, imageBitmap)
                    }
                    imageBitmap
                }

                "VIDEO" -> {
                    // Ephemeral preview file for MediaMetadataRetriever
                    val previewResult = repository.createTransientPreview(item)
                    if (previewResult.isFailure) return@withContext null
                    val transientFile = previewResult.getOrNull() ?: return@withContext null

                    var frameBitmap: Bitmap? = null
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(transientFile.absolutePath)
                        // Retrieve first sync frame at 1s or start
                        val rawFrame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.frameAtTime

                        if (rawFrame != null) {
                            // Scale down to bounded thumbnail
                            val scaled = Bitmap.createScaledBitmap(
                                rawFrame,
                                targetWidth,
                                (targetWidth * rawFrame.height) / rawFrame.width.coerceAtLeast(1),
                                true
                            )
                            if (scaled != rawFrame) {
                                rawFrame.recycle()
                            }
                            frameBitmap = scaled
                        }
                    } catch (_: Exception) {
                        frameBitmap = null
                    } finally {
                        try {
                            retriever.release()
                        } catch (_: Exception) {}
                        // IMMEDIATELY shred transient video preview file
                        FileShredder.shredAndPurge(transientFile)
                    }

                    if (frameBitmap != null) {
                        val imageBitmap = frameBitmap.asImageBitmap()
                        synchronized(memoryCache) {
                            memoryCache.put(cacheKey, imageBitmap)
                        }
                        imageBitmap
                    } else {
                        null
                    }
                }

                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Purges all in-memory bitmaps immediately when the vault is locked.
     */
    fun clearCache() {
        synchronized(memoryCache) {
            memoryCache.evictAll()
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
