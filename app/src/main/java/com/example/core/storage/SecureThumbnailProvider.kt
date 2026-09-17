package com.example.core.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ephemeral In-Memory Thumbnail Provider.
 *
 * Security Invariants:
 * - NEVER creates persistent or cached files on disk outside the encrypted vault container.
 * - Decrypts directly into volatile memory only when needed.
 * - Downsamples images to conserve RAM and avoid OOM.
 * - Cache lives strictly in memory and is wiped clean upon vault locking or memory pressure.
 */
object SecureThumbnailProvider {

    // 20-item in-memory LRU cache
    private val memoryCache = object : LruCache<String, ImageBitmap>(20) {}

    fun getCached(path: String): ImageBitmap? {
        return synchronized(memoryCache) {
            memoryCache.get(path)
        }
    }

    suspend fun loadThumbnail(
        path: String,
        storageManager: VaultStorageManager,
        targetWidth: Int = 240,
        targetHeight: Int = 240
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        synchronized(memoryCache) {
            memoryCache.get(path)?.let { return@withContext it }
        }

        try {
            val decryptedBytes = storageManager.decryptToMemory(path)
            if (decryptedBytes.isEmpty()) return@withContext null

            // First decode bounds
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size, options)

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565 // Low memory footprint

            val bitmap = BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size, options)
                ?: return@withContext null

            val imageBitmap = bitmap.asImageBitmap()
            synchronized(memoryCache) {
                memoryCache.put(path, imageBitmap)
            }
            imageBitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Immediately purges in-memory thumbnails upon lock or session invalidation.
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
