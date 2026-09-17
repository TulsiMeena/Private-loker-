package com.example.feature.files.archive

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale

/**
 * Enterprise-grade security validator for ZIP and archive files.
 *
 * Enforces:
 * 1. Strict Path Traversal Protection (Zip Slip defense)
 * 2. Decompression Bomb Protection (size, count, ratio, depth)
 * 3. File Format & Magic Number Authentication
 * 4. Free Storage Capacity Guard
 */
object ZipSecurityEngine {

    // Documented and configurable safe extraction limits
    const val MAX_TOTAL_EXTRACTED_BYTES = 500L * 1024 * 1024 // 500 MB maximum total
    const val MAX_ENTRY_COUNT = 10_000                        // 10,000 files maximum
    const val MAX_NESTING_DEPTH = 20                          // 20 directory levels maximum
    const val MAX_SINGLE_FILE_BYTES = 250L * 1024 * 1024     // 250 MB max single file
    const val MAX_COMPRESSION_RATIO = 100.0                   // 100:1 ratio safeguard
    const val RATIO_CHECK_MIN_BYTES = 2L * 1024 * 1024        // 2 MB threshold before ratio check

    // Magic headers
    private val ZIP_MAGIC_LOCAL = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val ZIP_MAGIC_EMPTY = byteArrayOf(0x50, 0x4B, 0x05, 0x06)
    private val ZIP_MAGIC_SPANNED = byteArrayOf(0x50, 0x4B, 0x07, 0x08)
    private val ZIP_MAGIC_CENTRAL = byteArrayOf(0x50, 0x4B, 0x01, 0x02)

    private val SEVEN_Z_MAGIC = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
    private val RAR_MAGIC = byteArrayOf(0x52, 0x61, 0x72, 0x21) // "Rar!"
    private val GZ_MAGIC = byteArrayOf(0x1F.toByte(), 0x8B.toByte())

    /**
     * Inspects the archive format from file bytes.
     * Returns "ZIP" if supported, or throws [UnsupportedArchiveException] if an unsupported format
     * or corrupted format is detected.
     */
    fun detectAndValidateArchiveFormat(file: File): String {
        if (!file.exists() || file.length() < 4) {
            throw IllegalArgumentException("Archive file is empty or missing")
        }

        val header = ByteArray(6)
        val bytesRead = FileInputStream(file).use { it.read(header) }
        if (bytesRead < 4) {
            throw IllegalArgumentException("Archive integrity check failed: file truncated")
        }

        // Check for known unsupported formats first
        if (matchesMagic(header, SEVEN_Z_MAGIC)) {
            throw UnsupportedArchiveException("Unsupported Archive Format: 7z archives are not supported")
        }
        if (matchesMagic(header, RAR_MAGIC)) {
            throw UnsupportedArchiveException("Unsupported Archive Format: RAR archives are not supported")
        }
        if (matchesMagic(header, GZ_MAGIC)) {
            throw UnsupportedArchiveException("Unsupported Archive Format: GZ archives are not supported directly")
        }

        // Check for standard ZIP magic bytes
        if (matchesMagic(header, ZIP_MAGIC_LOCAL) ||
            matchesMagic(header, ZIP_MAGIC_EMPTY) ||
            matchesMagic(header, ZIP_MAGIC_SPANNED) ||
            matchesMagic(header, ZIP_MAGIC_CENTRAL)
        ) {
            return "ZIP"
        }

        val name = file.name.lowercase(Locale.ROOT)
        if (name.endsWith(".tar") || name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            throw UnsupportedArchiveException("Unsupported Archive Format: TAR archives are not supported")
        }

        // If file extension is .zip or .apk or .jar, but header is not valid zip header
        throw UnsupportedArchiveException("Archive integrity check failed: invalid or corrupt header")
    }

    private fun matchesMagic(data: ByteArray, magic: ByteArray): Boolean {
        if (data.size < magic.size) return false
        for (i in magic.indices) {
            if (data[i] != magic[i]) return false
        }
        return true
    }

    /**
     * Validates and sanitizes a relative archive entry path.
     * Throws [ZipPathTraversalException] if directory traversal attack or invalid characters are detected.
     */
    fun sanitizeEntryPath(rawPath: String): String {
        // Null byte injection protection
        if (rawPath.contains('\u0000')) {
            throw ZipPathTraversalException("Archive entry contains illegal null byte: $rawPath")
        }

        // Normalize separators to forward slash
        val normalized = rawPath.replace('\\', '/').trim()

        // Strip leading slashes to prevent absolute path interpretation
        val stripped = normalized.trimStart('/')

        if (stripped.isEmpty()) {
            return ""
        }

        // Disallow Windows drive letter references (e.g. C:file)
        if (stripped.length >= 2 && stripped[1] == ':' && stripped[0].isLetter()) {
            throw ZipPathTraversalException("Archive entry contains illegal drive letter specification: $rawPath")
        }

        // Split into path segments and examine each
        val segments = stripped.split('/').filter { it.isNotEmpty() }

        // Nesting depth limit check
        if (segments.size > MAX_NESTING_DEPTH) {
            throw ZipBombException("Archive exceeds safe extraction limits: directory nesting depth exceeds $MAX_NESTING_DEPTH levels")
        }

        for (segment in segments) {
            // Strict directory traversal prevention
            if (segment == ".." || segment == ".") {
                throw ZipPathTraversalException("Archive entry attempts directory traversal: $rawPath")
            }
        }

        return stripped
    }

    /**
     * Verifies that the resolved target file strictly resides within [baseDir].
     * Protects against Zip Slip vulnerabilities.
     */
    fun verifySafeDestination(destFile: File, baseDir: File): Boolean {
        val canonicalDest = destFile.canonicalPath
        val canonicalBase = baseDir.canonicalPath
        return canonicalDest.startsWith(canonicalBase + File.separator) || canonicalDest == canonicalBase
    }

    /**
     * Ensures target directory has sufficient storage space before proceeding with extraction.
     */
    fun checkStorageAvailable(targetDir: File, requiredBytes: Long) {
        val usableSpace = targetDir.usableSpace
        // Require at least requiredBytes plus a safety margin of 20 MB
        val margin = 20L * 1024 * 1024
        if (usableSpace < (requiredBytes + margin)) {
            throw InsufficientStorageException(
                "Not enough storage available for this operation. Required: ${(requiredBytes / (1024 * 1024))} MB, Available: ${(usableSpace / (1024 * 1024))} MB"
            )
        }
    }

    /**
     * Verifies cumulative extraction limits during streaming decompression.
     */
    fun validateCumulativeDecompression(
        totalBytesWritten: Long,
        totalEntriesProcessed: Int,
        totalCompressedBytesRead: Long,
        currentFileBytesWritten: Long
    ) {
        if (totalBytesWritten > MAX_TOTAL_EXTRACTED_BYTES) {
            throw ZipBombException(
                "Archive exceeds safe extraction limits: extracted size exceeds ${(MAX_TOTAL_EXTRACTED_BYTES / (1024 * 1024))} MB"
            )
        }

        if (totalEntriesProcessed > MAX_ENTRY_COUNT) {
            throw ZipBombException(
                "Archive exceeds safe extraction limits: entry count exceeds $MAX_ENTRY_COUNT items"
            )
        }

        if (currentFileBytesWritten > MAX_SINGLE_FILE_BYTES) {
            throw ZipBombException(
                "Archive exceeds safe extraction limits: single file size exceeds ${(MAX_SINGLE_FILE_BYTES / (1024 * 1024))} MB"
            )
        }

        // Compression ratio check for decompression bomb protection
        if (totalBytesWritten > RATIO_CHECK_MIN_BYTES && totalCompressedBytesRead > 0) {
            val ratio = totalBytesWritten.toDouble() / totalCompressedBytesRead.toDouble()
            if (ratio > MAX_COMPRESSION_RATIO) {
                throw ZipBombException(
                    "Archive exceeds safe extraction limits: suspicious compression ratio (${ratio.toInt()}:1)"
                )
            }
        }
    }
}
