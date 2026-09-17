package com.example.feature.documents

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Secure in-memory PDF thumbnail provider.
 *
 * Security Principles:
 * - Bitmaps are kept strictly in RAM (LruCache).
 * - No unencrypted image files are written to internal or external storage.
 * - Entire thumbnail memory cache is wiped upon vault lock.
 */
object DocumentThumbnailHelper {

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // Use 1/8th of available memory for thumbnails

    private val memoryCache = object : LruCache<Long, Bitmap>(cacheSize) {
        override fun sizeOf(key: Long, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun getCachedThumbnail(itemId: Long): Bitmap? {
        return synchronized(memoryCache) {
            memoryCache.get(itemId)
        }
    }

    suspend fun loadPdfThumbnail(
        item: VaultItemEntity,
        repository: VaultRepository,
        targetWidth: Int = 200,
        targetHeight: Int = 260
    ): Bitmap? = withContext(Dispatchers.IO) {
        // Check cache first
        synchronized(memoryCache) {
            val cached = memoryCache.get(item.id)
            if (cached != null && !cached.isRecycled) {
                return@withContext cached
            }
        }

        var tempFile: File? = null
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null

        try {
            val previewResult = repository.createTransientPreview(item)
            if (previewResult.isFailure) return@withContext null
            tempFile = previewResult.getOrNull() ?: return@withContext null

            pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount <= 0) return@withContext null

            page = renderer.openPage(0)

            val width = if (page.width > 0) targetWidth else 200
            val height = if (page.height > 0 && page.width > 0) {
                (targetWidth.toFloat() / page.width * page.height).toInt().coerceIn(100, 400)
            } else {
                targetHeight
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            synchronized(memoryCache) {
                memoryCache.put(item.id, bitmap)
            }

            bitmap
        } catch (_: Exception) {
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            tempFile?.let { com.example.core.storage.FileShredder.shredAndPurge(it) }
        }
    }

    /**
     * Purges all decrypted preview bitmaps from memory immediately upon vault lock.
     */
    fun clearCache() {
        synchronized(memoryCache) {
            memoryCache.evictAll()
        }
    }
}
