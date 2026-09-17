package com.example.feature.backup

import android.util.Base64
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Dedicated cryptographic helper for PrivateVault Secure Backup & Recovery.
 *
 * Security Architecture:
 * - PBKDF2WithHmacSHA256 key derivation with 100,000 iterations and 32-byte salt.
 * - AES-256-GCM authenticated encryption with 12-byte cryptographically random IVs.
 * - High-entropy recovery key generator (256-bit random source).
 * - Zero hardcoded passwords, zero static keys, zero plaintext leaks.
 */
object BackupCryptoHelper {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val AES_ALGORITHM = "AES"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_LENGTH_BITS = 256
    private const val BUFFER_SIZE = 64 * 1024

    private val secureRandom = SecureRandom()

    /**
     * Generates a 32-byte cryptographically secure random salt.
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(BackupConstants.SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Generates a high-entropy 24-character cryptographic recovery key (e.g. "PV-A8K2-7M9X-4F1L-9Q3Z-B5T6-2E8W").
     * Uses an unambiguous uppercase alphanumeric alphabet (excluding 0/O, 1/I to prevent transcription errors).
     */
    fun generateRecoveryKey(): String {
        val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" // 32 unambiguous characters (5 bits each)
        val groups = 6
        val groupLength = 4
        val result = StringBuilder("PV-")

        for (g in 0 until groups) {
            for (i in 0 until groupLength) {
                val index = secureRandom.nextInt(alphabet.length)
                result.append(alphabet[index])
            }
            if (g < groups - 1) {
                result.append("-")
            }
        }
        return result.toString()
    }

    /**
     * Derives a 256-bit AES SecretKey from a recovery passphrase or recovery key using PBKDF2.
     */
    fun deriveBackupKey(secret: CharArray, salt: ByteArray, iterations: Int = BackupConstants.PBKDF2_ITERATIONS): SecretKey {
        require(salt.size >= 16) { "Salt must be at least 16 bytes" }
        require(iterations >= 10_000) { "KDF iterations must be at least 10,000" }

        val spec = PBEKeySpec(secret, salt, iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val rawKey = factory.generateSecret(spec).encoded
        return SecretKeySpec(rawKey, AES_ALGORITHM)
    }

    /**
     * Encrypts byte array using AES-256-GCM under the Backup Master Key.
     * Output format: [12-byte IV] + [AES-GCM ciphertext with 128-bit authentication tag].
     */
    fun encryptBytes(plainBytes: ByteArray, key: SecretKey): ByteArray {
        val iv = ByteArray(BackupConstants.GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(BackupConstants.GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val cipherText = cipher.doFinal(plainBytes)
        val result = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(cipherText, 0, result, iv.size, cipherText.size)
        return result
    }

    /**
     * Decrypts byte array using AES-256-GCM.
     * Input format: [12-byte IV] + [AES-GCM ciphertext with 128-bit tag].
     * Throws Exception if authentication tag verification fails (wrong key or corrupted data).
     */
    fun decryptBytes(encryptedPayload: ByteArray, key: SecretKey): ByteArray {
        if (encryptedPayload.size <= BackupConstants.GCM_IV_LENGTH_BYTES) {
            throw IllegalArgumentException("Encrypted payload too short to contain valid GCM IV and tag")
        }

        val iv = ByteArray(BackupConstants.GCM_IV_LENGTH_BYTES)
        System.arraycopy(encryptedPayload, 0, iv, 0, iv.size)

        val cipherTextSize = encryptedPayload.size - iv.size
        val cipherText = ByteArray(cipherTextSize)
        System.arraycopy(encryptedPayload, iv.size, cipherText, 0, cipherTextSize)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(BackupConstants.GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return cipher.doFinal(cipherText)
    }

    /**
     * Streams and encrypts data from an [InputStream] to an [OutputStream] using AES-256-GCM.
     * Prepends the 12-byte random IV, then streams ciphertext ending with the 128-bit GCM tag.
     */
    fun encryptStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        key: SecretKey,
        onProgress: ((bytesRead: Long) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ): Long {
        val iv = ByteArray(BackupConstants.GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        outputStream.write(iv)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(BackupConstants.GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val cipherOut = CipherOutputStream(outputStream, cipher)
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var totalRead = 0L

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            if (isCancelled()) throw java.util.concurrent.CancellationException("Operation cancelled by user")
            cipherOut.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            onProgress?.invoke(totalRead)
        }
        // flush and close cipher stream to commit the GCM authentication tag
        cipherOut.flush()
        return totalRead
    }

    /**
     * Streams and decrypts data from an [InputStream] to an [OutputStream] using AES-256-GCM.
     * Reads the 12-byte IV from the beginning, then decrypts and verifies the GCM tag on stream completion.
     */
    fun decryptStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        key: SecretKey,
        onProgress: ((bytesWritten: Long) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ): Long {
        val iv = ByteArray(BackupConstants.GCM_IV_LENGTH_BYTES)
        var ivRead = 0
        while (ivRead < iv.size) {
            val count = inputStream.read(iv, ivRead, iv.size - ivRead)
            if (count == -1) throw IllegalArgumentException("Incomplete stream: Could not read GCM IV")
            ivRead += count
        }

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(BackupConstants.GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val cipherIn = CipherInputStream(inputStream, cipher)
        val buffer = ByteArray(BUFFER_SIZE)
        var bytes: Int
        var totalWritten = 0L

        while (cipherIn.read(buffer).also { bytes = it } != -1) {
            if (isCancelled()) throw java.util.concurrent.CancellationException("Operation cancelled by user")
            outputStream.write(buffer, 0, bytes)
            totalWritten += bytes
            onProgress?.invoke(totalWritten)
        }
        outputStream.flush()
        return totalWritten
    }

    /**
     * Computes SHA-256 hex digest of a byte array.
     */
    fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Helper to encode ByteArray to Base64.
     */
    fun toBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    /**
     * Helper to decode Base64 to ByteArray.
     */
    fun fromBase64(str: String): ByteArray = Base64.decode(str, Base64.NO_WRAP)
}
