package com.example.core.storage

import android.content.Context
import com.example.core.security.VaultAccessGate
import com.example.core.security.VaultIntegrityException
import com.example.core.security.VaultKeyManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.CancellationException
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream

data class EncryptedImportResult(
    val fileId: String,
    val relativePath: String,
    val originalSizeBytes: Long,
    val encryptedSizeBytes: Long,
    val checksumSha256: String,
    val storageVersion: Int = 1
)

/**
 * Production-Grade Private Vault Storage Engine.
 *
 * Core Principles:
 * - Dedicated storage architecture separating source, encrypted storage, metadata, and temporary files.
 * - Encrypted vault files (.pvault) are the permanent, encrypted source of truth.
 * - Atomic storage: partial imports are written to .tmp_enc and never treated as valid vault files.
 * - Authenticated streaming AES-256-GCM encryption with 64-byte authenticated VaultObject v1 header.
 * - Zero-Trace shredding on failure, cancellation, or deletion.
 * - Large file support via streaming buffered I/O without loading whole files into memory.
 */
class VaultStorageEngine(
    private val context: Context,
    private val accessGate: VaultAccessGate,
    val tempFileManager: VaultTempFileManager
) {
    companion object {
        const val BUFFER_SIZE = 64 * 1024 // 64 KB streaming buffer
    }

    private val secureRandom = SecureRandom()

    val encryptedDir: File = File(context.filesDir, "vault_encrypted").apply {
        if (!exists()) mkdirs()
    }

    /**
     * Streams and encrypts an input stream into an atomic encrypted vault container (.pvault).
     *
     * @param inputStream Source data stream from SAF or application
     * @param expectedTotalBytes Expected size for progress reporting (-1 if unknown)
     * @param onProgress Callback receiving (bytesProcessed, totalBytes, phaseDescription)
     * @param isCancelled Check callback to safely abort large imports
     */
    fun importStream(
        inputStream: InputStream,
        expectedTotalBytes: Long = -1L,
        onProgress: ((bytesProcessed: Long, totalBytes: Long, phase: String) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ): EncryptedImportResult {
        // Enforce unlocked security access
        accessGate.requireUnlocked()

        val fileId = UUID.randomUUID().toString()
        val tempEncFile = File(encryptedDir, "$fileId.tmp_enc")
        val finalVaultFile = File(encryptedDir, "$fileId.pvault")

        val iv = ByteArray(VaultObjectHeader.IV_SIZE_BYTES)
        secureRandom.nextBytes(iv)
        val keyVersion = accessGate.currentKeyVersion
        val cipher = accessGate.getEncryptionCipher(iv)

        var totalBytesRead = 0L
        val digest = MessageDigest.getInstance("SHA-256")

        try {
            if (isCancelled()) throw CancellationException("Import cancelled by user")

            onProgress?.invoke(0L, expectedTotalBytes, "PREPARING")

            // Reserve 64 bytes for the VaultObject header
            FileOutputStream(tempEncFile).use { fileOut ->
                BufferedOutputStream(fileOut, BUFFER_SIZE).use { bufferedOut ->
                    // Write placeholder header
                    bufferedOut.write(ByteArray(VaultObjectHeader.HEADER_SIZE_BYTES))

                    val cipherOut = CipherOutputStream(bufferedOut, cipher)
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytes: Int

                    val bufferedIn = BufferedInputStream(inputStream, BUFFER_SIZE)
                    while (bufferedIn.read(buffer).also { bytes = it } != -1) {
                        if (isCancelled()) {
                            throw CancellationException("Import cancelled by user")
                        }

                        digest.update(buffer, 0, bytes)
                        cipherOut.write(buffer, 0, bytes)
                        totalBytesRead += bytes

                        onProgress?.invoke(totalBytesRead, expectedTotalBytes, "ENCRYPTING")
                    }

                    onProgress?.invoke(totalBytesRead, expectedTotalBytes, "VERIFYING")
                    cipherOut.flush()
                    cipherOut.close()
                }
            }

            if (isCancelled()) throw CancellationException("Import cancelled by user")

            // Finalize header with true size and checksum
            val checksumBytes = digest.digest()
            val checksumHex = VaultObjectHeader.checksumBytesToHex(checksumBytes)

            val header = VaultObjectHeader(
                formatVersion = VaultObjectHeader.FORMAT_VERSION_V1,
                algorithmId = VaultObjectHeader.ALGORITHM_AES_256_GCM,
                keyVersion = keyVersion,
                iv = iv,
                originalSizeBytes = totalBytesRead,
                originalChecksumSha256Bytes = checksumBytes
            )

            // Write finalized header at offset 0
            RandomAccessFile(tempEncFile, "rws").use { raf ->
                raf.seek(0)
                val headerBuffer = java.nio.ByteBuffer.allocate(VaultObjectHeader.HEADER_SIZE_BYTES)
                headerBuffer.put(VaultObjectHeader.MAGIC_BYTES)
                headerBuffer.put(header.formatVersion)
                headerBuffer.put(header.algorithmId)
                headerBuffer.putInt(header.keyVersion)
                headerBuffer.put(header.iv)
                headerBuffer.putLong(header.originalSizeBytes)
                headerBuffer.put(header.originalChecksumSha256Bytes)
                raf.write(headerBuffer.array())
            }

            // Atomic rename to final vault file
            val renamed = tempEncFile.renameTo(finalVaultFile)
            if (!renamed) {
                // Fallback copy if rename fails on strange filesystem mount
                tempEncFile.copyTo(finalVaultFile, overwrite = true)
                FileShredder.shredAndPurge(tempEncFile)
            }

            onProgress?.invoke(totalBytesRead, totalBytesRead, "STORED IN VAULT")

            return EncryptedImportResult(
                fileId = fileId,
                relativePath = finalVaultFile.name,
                originalSizeBytes = totalBytesRead,
                encryptedSizeBytes = finalVaultFile.length(),
                checksumSha256 = checksumHex,
                storageVersion = VaultObjectHeader.FORMAT_VERSION_V1.toInt()
            )
        } catch (e: Throwable) {
            // Clean up temporary encrypted artifact immediately
            if (tempEncFile.exists()) {
                FileShredder.shredAndPurge(tempEncFile)
            }
            if (finalVaultFile.exists()) {
                FileShredder.shredAndPurge(finalVaultFile)
            }
            throw e
        }
    }

    /**
     * Decrypts an encrypted vault file directly into an output stream (e.g. for SAF export).
     */
    fun decryptToStream(
        relativePath: String,
        outputStream: OutputStream,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ): Long {
        accessGate.requireUnlocked()
        val vaultFile = File(encryptedDir, relativePath)
        if (!vaultFile.exists()) {
            throw IllegalArgumentException("Encrypted vault object not found: $relativePath")
        }

        FileInputStream(vaultFile).use { fileIn ->
            BufferedInputStream(fileIn, BUFFER_SIZE).use { bufferedIn ->
                val header = VaultObjectHeader.readFromStream(bufferedIn)
                val cipher = accessGate.getDecryptionCipher(header.iv, header.keyVersion)

                val cipherIn = CipherInputStream(bufferedIn, cipher)
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(BUFFER_SIZE)
                var bytes: Int
                var totalBytesDecrypted = 0L

                val bufferedOut = BufferedOutputStream(outputStream, BUFFER_SIZE)
                try {
                    while (cipherIn.read(buffer).also { bytes = it } != -1) {
                        digest.update(buffer, 0, bytes)
                        bufferedOut.write(buffer, 0, bytes)
                        totalBytesDecrypted += bytes
                        onProgress?.invoke(totalBytesDecrypted, header.originalSizeBytes)
                    }
                    bufferedOut.flush()
                } catch (e: Exception) {
                    throw VaultIntegrityException("AEAD cryptographic authentication or payload decryption failure: ${e.message}", e)
                }

                // Authenticated integrity check
                val computedChecksum = digest.digest()
                if (!computedChecksum.contentEquals(header.originalChecksumSha256Bytes)) {
                    throw VaultIntegrityException("SHA-256 payload integrity mismatch on $relativePath")
                }

                return totalBytesDecrypted
            }
        }
    }

    /**
     * Decrypts an encrypted vault file into an in-memory byte array.
     */
    fun decryptToMemory(relativePath: String): ByteArray {
        accessGate.requireUnlocked()
        val out = ByteArrayOutputStream()
        decryptToStream(relativePath, out)
        return out.toByteArray()
    }

    /**
     * Decrypts to a transient tracked temporary file for secure viewing.
     * Automatically tracked by [VaultTempFileManager].
     */
    fun createTransientDecryptedFile(relativePath: String, extensionHint: String = "tmp"): File {
        accessGate.requireUnlocked()
        val tempFile = tempFileManager.createTransientFile(extensionHint)
        try {
            FileOutputStream(tempFile).use { out ->
                decryptToStream(relativePath, out)
            }
            return tempFile
        } catch (e: Throwable) {
            tempFileManager.releaseTransientFile(tempFile)
            throw e
        }
    }

    /**
     * Verifies the cryptographic integrity and header of a stored vault file without retaining plaintext.
     */
    fun verifyIntegrity(relativePath: String): Boolean {
        accessGate.requireUnlocked()
        return try {
            val devNull = object : OutputStream() {
                override fun write(b: Int) {}
                override fun write(b: ByteArray, off: Int, len: Int) {}
            }
            decryptToStream(relativePath, devNull)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Physically shreds and purges an encrypted vault file from disk.
     */
    fun deleteEncryptedFile(relativePath: String): Boolean {
        val file = File(encryptedDir, relativePath)
        return FileShredder.shredAndPurge(file)
    }

    /**
     * Purges all temporary working files.
     */
    fun purgeTemporaryFiles() {
        tempFileManager.cleanupAbandonedTempFiles()
    }

    /**
     * Computes the total storage volume consumed by encrypted vault containers.
     */
    fun getEncryptedStorageSizeBytes(): Long {
        return encryptedDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Shreds and purges all files in the encrypted vault store.
     */
    fun purgeAllVaultFiles() {
        encryptedDir.listFiles()?.forEach { file ->
            FileShredder.shredAndPurge(file)
        }
        tempFileManager.cleanupAbandonedTempFiles()
    }
}
