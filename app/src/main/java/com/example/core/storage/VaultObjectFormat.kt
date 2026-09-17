package com.example.core.storage

import com.example.core.security.VaultIntegrityException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * VaultObject Format Specification v1.
 *
 * Header Structure (Total 64 Bytes Fixed):
 * - [0..5]   Magic: "PVAULT" (6 bytes ASCII)
 * - [6]      Format Version: 0x01 (1 byte)
 * - [7]      Cipher Algorithm: 0x01 = AES-256-GCM (1 byte)
 * - [8..11]  Master Key Version: Int (4 bytes BigEndian)
 * - [12..23] GCM Initialization Vector: 12 bytes
 * - [24..31] Original File Size: Long (8 bytes BigEndian)
 * - [32..63] Original SHA-256 Digest: 32 bytes
 *
 * Followed by the stream of AES-GCM ciphertext ending with the 128-bit authentication tag.
 *
 * This design:
 * - Detects corrupted or foreign files immediately (magic check)
 * - Verifies storage version and permits future format migrations (v2, v3)
 * - Carries integrity metadata directly within the authenticated container
 * - Enforces zero-leakage of filenames or unencrypted user data on disk
 */
data class VaultObjectHeader(
    val formatVersion: Byte = FORMAT_VERSION_V1,
    val algorithmId: Byte = ALGORITHM_AES_256_GCM,
    val keyVersion: Int = 1,
    val iv: ByteArray,
    val originalSizeBytes: Long,
    val originalChecksumSha256Bytes: ByteArray
) {
    companion object {
        val MAGIC_BYTES = byteArrayOf(0x50, 0x56, 0x41, 0x55, 0x4C, 0x54) // "PVAULT"
        const val FORMAT_VERSION_V1: Byte = 0x01
        const val ALGORITHM_AES_256_GCM: Byte = 0x01
        const val HEADER_SIZE_BYTES = 64
        const val IV_SIZE_BYTES = 12
        const val CHECKSUM_SIZE_BYTES = 32

        fun writeToStream(header: VaultObjectHeader, outputStream: OutputStream) {
            require(header.iv.size == IV_SIZE_BYTES) { "Invalid IV size" }
            require(header.originalChecksumSha256Bytes.size == CHECKSUM_SIZE_BYTES) { "Invalid checksum size" }

            val buffer = ByteBuffer.allocate(HEADER_SIZE_BYTES)
            buffer.put(MAGIC_BYTES)
            buffer.put(header.formatVersion)
            buffer.put(header.algorithmId)
            buffer.putInt(header.keyVersion)
            buffer.put(header.iv)
            buffer.putLong(header.originalSizeBytes)
            buffer.put(header.originalChecksumSha256Bytes)

            outputStream.write(buffer.array())
        }

        fun readFromStream(inputStream: InputStream): VaultObjectHeader {
            val headerBytes = ByteArray(HEADER_SIZE_BYTES)
            var readTotal = 0
            while (readTotal < HEADER_SIZE_BYTES) {
                val count = inputStream.read(headerBytes, readTotal, HEADER_SIZE_BYTES - readTotal)
                if (count == -1) {
                    throw VaultIntegrityException("Incomplete vault file: Unexpected end of header bytes")
                }
                readTotal += count
            }

            val buffer = ByteBuffer.wrap(headerBytes)

            // 1. Magic check
            val magic = ByteArray(MAGIC_BYTES.size)
            buffer.get(magic)
            if (!magic.contentEquals(MAGIC_BYTES)) {
                throw VaultIntegrityException("Invalid vault format: Missing PVAULT magic signature")
            }

            // 2. Format version check
            val formatVersion = buffer.get()
            if (formatVersion != FORMAT_VERSION_V1) {
                throw VaultIntegrityException("Unsupported vault storage version: $formatVersion (Expected $FORMAT_VERSION_V1)")
            }

            // 3. Algorithm check
            val algorithmId = buffer.get()
            if (algorithmId != ALGORITHM_AES_256_GCM) {
                throw VaultIntegrityException("Unsupported encryption algorithm ID: $algorithmId")
            }

            val keyVersion = buffer.int
            val iv = ByteArray(IV_SIZE_BYTES)
            buffer.get(iv)
            val originalSizeBytes = buffer.long
            val checksumBytes = ByteArray(CHECKSUM_SIZE_BYTES)
            buffer.get(checksumBytes)

            return VaultObjectHeader(
                formatVersion = formatVersion,
                algorithmId = algorithmId,
                keyVersion = keyVersion,
                iv = iv,
                originalSizeBytes = originalSizeBytes,
                originalChecksumSha256Bytes = checksumBytes
            )
        }

        fun checksumBytesToHex(bytes: ByteArray): String {
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun hexToChecksumBytes(hex: String): ByteArray {
            val cleanHex = hex.trim()
            if (cleanHex.length != 64) return ByteArray(CHECKSUM_SIZE_BYTES)
            val result = ByteArray(32)
            for (i in 0 until 32) {
                val byteStr = cleanHex.substring(i * 2, i * 2 + 2)
                result[i] = byteStr.toInt(16).toByte()
            }
            return result
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VaultObjectHeader
        if (formatVersion != other.formatVersion) return false
        if (algorithmId != other.algorithmId) return false
        if (keyVersion != other.keyVersion) return false
        if (!iv.contentEquals(other.iv)) return false
        if (originalSizeBytes != other.originalSizeBytes) return false
        if (!originalChecksumSha256Bytes.contentEquals(other.originalChecksumSha256Bytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = formatVersion.toInt()
        result = 31 * result + algorithmId.toInt()
        result = 31 * result + keyVersion
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + originalSizeBytes.hashCode()
        result = 31 * result + originalChecksumSha256Bytes.contentHashCode()
        return result
    }
}
