package com.example.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * Hardware/Software backed KeyStore encryption manager.
 * Provides AES-256-GCM encryption and secure key derivation without ever exposing raw keys.
 */
class SecureKeyManager {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "private_vault_master_aes256"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val PBKDF2_ITERATIONS = 100_000
        private const val PBKDF2_KEY_LENGTH = 256
    }

    private var softwareFallbackKey: SecretKey? = null

    private val keyStore: KeyStore? = try {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    } catch (_: Exception) {
        null
    }

    init {
        ensureMasterKeyExists()
    }

    /**
     * Verifies that the hardware-backed (or AndroidKeyStore) master key exists,
     * or generates a new 256-bit AES GCM key.
     */
    private fun ensureMasterKeyExists() {
        if (keyStore != null) {
            try {
                if (!keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                    val keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        ANDROID_KEYSTORE
                    )
                    val keyGenSpec = KeyGenParameterSpec.Builder(
                        MASTER_KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .build()

                    keyGenerator.init(keyGenSpec)
                    keyGenerator.generateKey()
                }
                return
            } catch (_: Exception) {
                // KeyStore provider not installed (JVM testing runner); fallback to software AES
            }
        }
        if (softwareFallbackKey == null) {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            softwareFallbackKey = keyGen.generateKey()
        }
    }

    private fun getMasterKey(): SecretKey {
        if (keyStore != null) {
            try {
                if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                    return (keyStore.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
                }
            } catch (_: Exception) {}
        }
        return softwareFallbackKey ?: run {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            val key = keyGen.generateKey()
            softwareFallbackKey = key
            key
        }
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM.
     * Returns a payload prepended with the 12-byte IV followed by the authenticated ciphertext.
     */
    fun encrypt(plainBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())
        val iv = cipher.iv // 12 bytes
        val cipherText = cipher.doFinal(plainBytes)

        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return combined
    }

    /**
     * Decrypts a payload previously encrypted with [encrypt].
     */
    fun decrypt(encryptedPayload: ByteArray): ByteArray {
        require(encryptedPayload.size > GCM_IV_LENGTH_BYTES) { "Invalid encrypted payload size" }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        System.arraycopy(encryptedPayload, 0, iv, 0, GCM_IV_LENGTH_BYTES)

        val cipherTextSize = encryptedPayload.size - GCM_IV_LENGTH_BYTES
        val cipherText = ByteArray(cipherTextSize)
        System.arraycopy(encryptedPayload, GCM_IV_LENGTH_BYTES, cipherText, 0, cipherTextSize)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), spec)

        return cipher.doFinal(cipherText)
    }

    /**
     * Derives a cryptographic hash from a user PIN with PBKDF2 and a random salt.
     */
    fun derivePinHash(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    /**
     * Generates a secure random salt for PIN verification.
     */
    fun generateSalt(size: Int = 16): ByteArray {
        val salt = ByteArray(size)
        SecureRandom().nextBytes(salt)
        return salt
    }
}
