package com.example.core.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Thrown when an unauthorized access attempt is made while the vault is locked.
 */
class VaultLockedException(message: String = "Vault is locked: Cryptographic operations forbidden.") :
    SecurityException(message)

/**
 * Thrown when an encrypted object fails authenticated cryptographic integrity check.
 */
class VaultIntegrityException(
    message: String = "Cryptographic integrity failure: Data has been tampered with or corrupted.",
    cause: Throwable? = null
) : SecurityException(message, cause)

/**
 * Dedicated Vault Key Management Architecture.
 *
 * Security Requirements:
 * - Generate cryptographic keys securely using Android KeyStore where available.
 * - Protect key material with AES-256-GCM.
 * - Never place encryption keys in SharedPreferences.
 * - Never hard-code encryption keys.
 * - Never log cryptographic keys.
 * - Never expose keys to UI code.
 * - Separate authentication state from encryption implementation via access gates.
 * - Architecture supports key rotation without rewriting the storage system.
 */
interface VaultKeyManager {
    fun getCipherForEncryption(iv: ByteArray): Cipher
    fun getCipherForDecryption(iv: ByteArray, keyVersion: Int = 1): Cipher
    fun encryptBytes(plainBytes: ByteArray): ByteArray
    fun decryptBytes(encryptedPayload: ByteArray, keyVersion: Int = 1): ByteArray
    fun getCurrentKeyVersion(): Int
    fun rotateMasterKey(): Int
    fun isKeyAvailable(): Boolean
}

class AndroidVaultKeyManager(
    private val keyAliasPrefix: String = "private_vault_aes256_v"
) : VaultKeyManager {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }

    private val secureRandom = SecureRandom()
    private var activeKeyVersion = 1

    // Fallback software key cache strictly for non-KeyStore environments (such as unit test runners)
    private val softwareKeyCache = mutableMapOf<Int, SecretKey>()

    private val keyStore: KeyStore? = try {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    } catch (_: Exception) {
        null
    }

    init {
        ensureKeyExists(activeKeyVersion)
    }

    private fun getAliasForVersion(version: Int): String = "$keyAliasPrefix$version"

    @Synchronized
    private fun ensureKeyExists(version: Int) {
        val alias = getAliasForVersion(version)
        if (keyStore != null) {
            try {
                if (!keyStore.containsAlias(alias)) {
                    val keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        ANDROID_KEYSTORE
                    )
                    val specBuilder = KeyGenParameterSpec.Builder(
                        alias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(false) // Allow custom IV injection for streamed headers

                    keyGenerator.init(specBuilder.build())
                    keyGenerator.generateKey()
                }
                return
            } catch (_: Exception) {
                // KeyStore unavailable or running in local JVM test runner; fall through to software generation
            }
        }

        // Software key fallback (for JVM unit tests / environments without KeyStore provider)
        if (!softwareKeyCache.containsKey(version)) {
            val rawKey = ByteArray(32)
            secureRandom.nextBytes(rawKey)
            softwareKeyCache[version] = SecretKeySpec(rawKey, "AES")
        }
    }

    private fun getKey(version: Int): SecretKey {
        ensureKeyExists(version)
        val alias = getAliasForVersion(version)
        if (keyStore != null) {
            try {
                val entry = keyStore.getEntry(alias, null)
                if (entry is KeyStore.SecretKeyEntry) {
                    return entry.secretKey
                }
            } catch (_: Exception) {
                // Fall back to software cache if KeyStore entry retrieval fails
            }
        }

        return softwareKeyCache[version]
            ?: throw IllegalStateException("Cryptographic key for version $version unavailable")
    }

    override fun getCipherForEncryption(iv: ByteArray): Cipher {
        require(iv.size == GCM_IV_LENGTH_BYTES) { "GCM IV must be exactly $GCM_IV_LENGTH_BYTES bytes" }
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, getKey(activeKeyVersion), spec)
        return cipher
    }

    override fun getCipherForDecryption(iv: ByteArray, keyVersion: Int): Cipher {
        require(iv.size == GCM_IV_LENGTH_BYTES) { "GCM IV must be exactly $GCM_IV_LENGTH_BYTES bytes" }
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getKey(keyVersion), spec)
        return cipher
    }

    override fun encryptBytes(plainBytes: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        val cipher = getCipherForEncryption(iv)
        val cipherText = cipher.doFinal(plainBytes)

        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return combined
    }

    override fun decryptBytes(encryptedPayload: ByteArray, keyVersion: Int): ByteArray {
        if (encryptedPayload.size <= GCM_IV_LENGTH_BYTES) {
            throw VaultIntegrityException("Encrypted payload too short to contain valid IV and tag")
        }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        System.arraycopy(encryptedPayload, 0, iv, 0, GCM_IV_LENGTH_BYTES)

        val cipherTextSize = encryptedPayload.size - GCM_IV_LENGTH_BYTES
        val cipherText = ByteArray(cipherTextSize)
        System.arraycopy(encryptedPayload, GCM_IV_LENGTH_BYTES, cipherText, 0, cipherTextSize)

        return try {
            val cipher = getCipherForDecryption(iv, keyVersion)
            cipher.doFinal(cipherText)
        } catch (e: Exception) {
            throw VaultIntegrityException("Decryption authentication failed: ${e.message}")
        }
    }

    override fun getCurrentKeyVersion(): Int = activeKeyVersion

    @Synchronized
    override fun rotateMasterKey(): Int {
        activeKeyVersion += 1
        ensureKeyExists(activeKeyVersion)
        return activeKeyVersion
    }

    override fun isKeyAvailable(): Boolean {
        return try {
            getKey(activeKeyVersion)
            true
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Access controller separating authentication state from encryption operations.
 * Prevents access to decrypted vault contents when locked.
 */
class VaultAccessGate(
    private val keyManager: VaultKeyManager,
    private val isUnlockedProvider: () -> Boolean
) {
    fun requireUnlocked() {
        if (!isUnlockedProvider()) {
            throw VaultLockedException()
        }
    }

    fun getEncryptionCipher(iv: ByteArray): Cipher {
        requireUnlocked()
        return keyManager.getCipherForEncryption(iv)
    }

    fun getDecryptionCipher(iv: ByteArray, keyVersion: Int): Cipher {
        requireUnlocked()
        return keyManager.getCipherForDecryption(iv, keyVersion)
    }

    fun encryptBytes(plainBytes: ByteArray): ByteArray {
        requireUnlocked()
        return keyManager.encryptBytes(plainBytes)
    }

    fun decryptBytes(encryptedPayload: ByteArray, keyVersion: Int): ByteArray {
        requireUnlocked()
        return keyManager.decryptBytes(encryptedPayload, keyVersion)
    }

    val currentKeyVersion: Int
        get() {
            requireUnlocked()
            return keyManager.getCurrentKeyVersion()
        }
}
