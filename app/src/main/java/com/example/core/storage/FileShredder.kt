package com.example.core.storage

import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

/**
 * High-security file shredder utility.
 * Overwrites files with random bytes and null bytes before truncation and deletion,
 * preventing flash memory recovery of temporary plaintext artifacts.
 */
object FileShredder {

    private val random = SecureRandom()

    fun shredAndPurge(file: File): Boolean {
        if (!file.exists()) return true
        if (file.isDirectory) {
            file.listFiles()?.forEach { shredAndPurge(it) }
            return file.delete()
        }

        try {
            val length = file.length()
            if (length > 0) {
                RandomAccessFile(file, "rws").use { raf ->
                    // Pass 1: Cryptographic random bytes
                    val buffer = ByteArray(minOf(length.toInt(), 4096))
                    var written = 0L
                    while (written < length) {
                        random.nextBytes(buffer)
                        val bytesToWrite = minOf(buffer.size.toLong(), length - written).toInt()
                        raf.write(buffer, 0, bytesToWrite)
                        written += bytesToWrite
                    }

                    // Pass 2: Zero-fill pass
                    raf.seek(0)
                    buffer.fill(0)
                    written = 0L
                    while (written < length) {
                        val bytesToWrite = minOf(buffer.size.toLong(), length - written).toInt()
                        raf.write(buffer, 0, bytesToWrite)
                        written += bytesToWrite
                    }
                    raf.setLength(0)
                }
            }
        } catch (_: Exception) {
            // Best-effort secure wipe
        }

        return file.delete()
    }
}
