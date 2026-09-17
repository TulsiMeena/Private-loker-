package com.example.core.storage

import android.content.Context
import java.io.File
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Secure Temporary File Manager.
 *
 * Security Requirements:
 * - Uses application-private cache storage (`vault_transient`).
 * - Tracks all active temporary working files in a concurrent registry.
 * - Cleans them immediately after transient operations complete.
 * - Scans and shreds abandoned temporary files on application startup and on lock.
 * - Generates unguessable randomized filenames without sensitive metadata.
 * - Prevents plaintext artifacts from persisting in flash storage via [FileShredder].
 */
class VaultTempFileManager(private val context: Context) {

    private val transientDir: File = File(context.cacheDir, "vault_transient").apply {
        if (!exists()) mkdirs()
    }

    private val activeTrackedFiles: MutableSet<File> = Collections.newSetFromMap(ConcurrentHashMap())

    init {
        cleanupAbandonedTempFiles()
    }

    /**
     * Creates a new tracked transient working file with a cryptographically randomized name.
     */
    fun createTransientFile(extension: String = "tmp"): File {
        val safeExt = if (extension.startsWith(".")) extension else ".$extension"
        val randomName = "tmp_pv_${UUID.randomUUID()}$safeExt"
        val tempFile = File(transientDir, randomName)
        activeTrackedFiles.add(tempFile)
        return tempFile
    }

    /**
     * Securely shreds and purges a tracked transient working file.
     */
    fun releaseTransientFile(file: File): Boolean {
        activeTrackedFiles.remove(file)
        return FileShredder.shredAndPurge(file)
    }

    /**
     * Purges and shreds all active transient files and scans the directory for any orphaned artifacts.
     */
    fun cleanupAbandonedTempFiles() {
        // 1. Clean actively tracked files
        val trackedCopy = ArrayList(activeTrackedFiles)
        trackedCopy.forEach { file ->
            activeTrackedFiles.remove(file)
            FileShredder.shredAndPurge(file)
        }

        // 2. Scan transient directory for untracked leftovers
        transientDir.listFiles()?.forEach { leftover ->
            FileShredder.shredAndPurge(leftover)
        }
    }

    /**
     * Number of currently open transient working files.
     */
    fun getActiveFileCount(): Int = activeTrackedFiles.size

    val directory: File get() = transientDir

    fun getTransientFilesCount(): Int {
        return transientDir.listFiles()?.size ?: 0
    }

    fun getTransientFilesSizeBytes(): Long {
        return transientDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}
