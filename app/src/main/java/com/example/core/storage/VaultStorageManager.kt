package com.example.core.storage

import android.content.Context
import com.example.core.security.AndroidVaultKeyManager
import com.example.core.security.VaultAccessGate
import com.example.core.security.VaultKeyManager
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

data class StorageImportResult(
    val relativePath: String,
    val originalSizeBytes: Long,
    val encryptedSizeBytes: Long,
    val checksumSha256: String
)

/**
 * High-level storage facade coordinating [VaultStorageEngine], [VaultTempFileManager],
 * and cryptographic access control.
 */
class VaultStorageManager(
    private val context: Context,
    private val keyManager: VaultKeyManager,
    private val accessGate: VaultAccessGate? = null,
    engine: VaultStorageEngine? = null
) {
    val tempFileManager = engine?.tempFileManager ?: VaultTempFileManager(context)

    val engine: VaultStorageEngine = engine ?: VaultStorageEngine(
        context = context,
        accessGate = accessGate ?: VaultAccessGate(keyManager) { true },
        tempFileManager = tempFileManager
    )

    val encryptedDir: File get() = engine.encryptedDir

    /**
     * Imports and encrypts an input stream using the authenticated VaultObject v1 engine.
     */
    fun importAndEncrypt(
        inputStream: InputStream,
        category: VaultCategory,
        expectedSizeBytes: Long = -1L,
        onProgress: ((Long, Long, String) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ): StorageImportResult {
        val result = engine.importStream(
            inputStream = inputStream,
            expectedTotalBytes = expectedSizeBytes,
            onProgress = onProgress,
            isCancelled = isCancelled
        )
        return StorageImportResult(
            relativePath = result.relativePath,
            originalSizeBytes = result.originalSizeBytes,
            encryptedSizeBytes = result.encryptedSizeBytes,
            checksumSha256 = result.checksumSha256
        )
    }

    /**
     * Decrypts an encrypted vault object into memory.
     */
    fun decryptToMemory(relativePath: String): ByteArray {
        return engine.decryptToMemory(relativePath)
    }

    /**
     * Creates a tracked temporary decrypted working file.
     */
    fun createTemporaryDecryptedFile(relativePath: String, tempFileName: String = "view.tmp"): File {
        return engine.createTransientDecryptedFile(relativePath, tempFileName)
    }

    /**
     * Releases and shreds a specific temporary file.
     */
    fun releaseTemporaryFile(file: File) {
        tempFileManager.releaseTransientFile(file)
    }

    /**
     * Physically shreds an encrypted file from disk.
     */
    fun deleteEncryptedFile(relativePath: String): Boolean {
        return engine.deleteEncryptedFile(relativePath)
    }

    /**
     * Purges and shreds all active and abandoned temporary working files.
     */
    fun purgeTemporaryFiles() {
        engine.purgeTemporaryFiles()
    }

    /**
     * Computes storage consumed by encrypted files.
     */
    fun getEncryptedStorageSizeBytes(): Long {
        return engine.getEncryptedStorageSizeBytes()
    }

    /**
     * Purges all vault data from physical storage.
     */
     fun purgeAllVaultFiles() {
         engine.purgeAllVaultFiles()
     }

    /**
     * Verifies cryptographic authenticated AEAD integrity of an encrypted vault object.
     */
    fun verifyIntegrity(relativePath: String): Boolean {
        return engine.verifyIntegrity(relativePath)
    }

    /**
     * Returns count of transient working files currently on disk.
     */
    fun getTemporaryFilesCount(): Int {
        return tempFileManager.getTransientFilesCount()
    }

    /**
     * Returns total bytes occupied by transient working files.
     */
    fun getTemporaryFilesSizeBytes(): Long {
        return tempFileManager.getTransientFilesSizeBytes()
    }
}
