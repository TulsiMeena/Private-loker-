package com.example.feature.backup

import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup format specification for PrivateVault.
 * Version 1: AES-256-GCM envelope authenticated backup with PBKDF2 key derivation.
 */
object BackupConstants {
    const val FORMAT_VERSION = 1
    const val MAGIC_HEADER = "PVBACKUP"
    const val PBKDF2_ITERATIONS = 100_000
    const val SALT_LENGTH_BYTES = 32
    const val GCM_IV_LENGTH_BYTES = 12
    const val GCM_TAG_LENGTH_BITS = 128
    const val ENTRY_HEADER = "backup_header.json"
    const val ENTRY_MANIFEST = "manifest.enc"
    const val OBJECTS_DIR = "objects/"
    const val DEFAULT_BACKUP_FILENAME_PREFIX = "PrivateVault_Backup_"
    const val BACKUP_FILE_EXTENSION = ".pvault"
}

enum class BackupProtectionType {
    PASSPHRASE,
    RECOVERY_KEY
}

enum class BackupVerificationStatus {
    VERIFIED,
    ATTENTION_REQUIRED,
    CORRUPTED,
    INVALID_SECRET,
    UNSUPPORTED_VERSION,
    PENDING
}

enum class RestoreMode {
    REPLACE_VAULT,
    MERGE_VAULT
}

enum class RestoreConflictResolution {
    KEEP_EXISTING,
    USE_BACKUP,
    KEEP_BOTH,
    SKIP
}

enum class BackupReminderSchedule(val displayName: String, val intervalDays: Int) {
    OFF("Off", 0),
    WEEKLY("Weekly (7 Days)", 7),
    MONTHLY("Monthly (30 Days)", 30)
}

/**
 * Public, non-sensitive header serialized in plain JSON in the backup container.
 * Contains ZERO secrets, ZERO keys, and ZERO filenames.
 */
data class BackupHeaderData(
    val magic: String = BackupConstants.MAGIC_HEADER,
    val formatVersion: Int = BackupConstants.FORMAT_VERSION,
    val protectionType: BackupProtectionType,
    val saltBase64: String,
    val kdfIterations: Int = BackupConstants.PBKDF2_ITERATIONS,
    val createdAt: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0.0"
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("magic", magic)
        obj.put("formatVersion", formatVersion)
        obj.put("protectionType", protectionType.name)
        obj.put("saltBase64", saltBase64)
        obj.put("kdfIterations", kdfIterations)
        obj.put("createdAt", createdAt)
        obj.put("appVersion", appVersion)
        return obj.toString(2)
    }

    companion object {
        fun fromJson(jsonStr: String): BackupHeaderData {
            val obj = JSONObject(jsonStr)
            val magic = obj.optString("magic", "")
            if (magic != BackupConstants.MAGIC_HEADER) {
                throw IllegalArgumentException("Invalid backup container: Header magic mismatch")
            }
            return BackupHeaderData(
                magic = magic,
                formatVersion = obj.optInt("formatVersion", 1),
                protectionType = BackupProtectionType.valueOf(obj.optString("protectionType", BackupProtectionType.PASSPHRASE.name)),
                saltBase64 = obj.getString("saltBase64"),
                kdfIterations = obj.optInt("kdfIterations", BackupConstants.PBKDF2_ITERATIONS),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                appVersion = obj.optString("appVersion", "1.0.0")
            )
        }
    }
}

/**
 * Serialized metadata of a single vault item inside the encrypted manifest.
 */
data class BackupItemMetadata(
    val id: Long,
    val title: String,
    val category: String,
    val encryptedPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val modifiedAt: Long,
    val isFavorite: Boolean,
    val checksumSha256: String,
    val folderId: Long?,
    val isTrash: Boolean,
    val deletedAt: Long?,
    val originalFolderId: Long?,
    val encryptedSizeBytes: Long,
    val storageVersion: Int,
    val tags: String
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("title", title)
        obj.put("category", category)
        obj.put("encryptedPath", encryptedPath)
        obj.put("mimeType", mimeType)
        obj.put("sizeBytes", sizeBytes)
        obj.put("createdAt", createdAt)
        obj.put("modifiedAt", modifiedAt)
        obj.put("isFavorite", isFavorite)
        obj.put("checksumSha256", checksumSha256)
        obj.put("folderId", folderId ?: JSONObject.NULL)
        obj.put("isTrash", isTrash)
        obj.put("deletedAt", deletedAt ?: JSONObject.NULL)
        obj.put("originalFolderId", originalFolderId ?: JSONObject.NULL)
        obj.put("encryptedSizeBytes", encryptedSizeBytes)
        obj.put("storageVersion", storageVersion)
        obj.put("tags", tags)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): BackupItemMetadata {
            return BackupItemMetadata(
                id = obj.getLong("id"),
                title = obj.getString("title"),
                category = obj.getString("category"),
                encryptedPath = obj.getString("encryptedPath"),
                mimeType = obj.getString("mimeType"),
                sizeBytes = obj.getLong("sizeBytes"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                modifiedAt = obj.optLong("modifiedAt", System.currentTimeMillis()),
                isFavorite = obj.optBoolean("isFavorite", false),
                checksumSha256 = obj.optString("checksumSha256", ""),
                folderId = if (obj.isNull("folderId")) null else obj.getLong("folderId"),
                isTrash = obj.optBoolean("isTrash", false),
                deletedAt = if (obj.isNull("deletedAt")) null else obj.getLong("deletedAt"),
                originalFolderId = if (obj.isNull("originalFolderId")) null else obj.getLong("originalFolderId"),
                encryptedSizeBytes = obj.optLong("encryptedSizeBytes", 0L),
                storageVersion = obj.optInt("storageVersion", 1),
                tags = obj.optString("tags", "")
            )
        }
    }
}

data class BackupFolderMetadata(
    val id: Long,
    val name: String,
    val parentId: Long?,
    val createdAt: Long,
    val isTrash: Boolean,
    val deletedAt: Long?
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("parentId", parentId ?: JSONObject.NULL)
        obj.put("createdAt", createdAt)
        obj.put("isTrash", isTrash)
        obj.put("deletedAt", deletedAt ?: JSONObject.NULL)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): BackupFolderMetadata {
            return BackupFolderMetadata(
                id = obj.getLong("id"),
                name = obj.getString("name"),
                parentId = if (obj.isNull("parentId")) null else obj.getLong("parentId"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                isTrash = obj.optBoolean("isTrash", false),
                deletedAt = if (obj.isNull("deletedAt")) null else obj.getLong("deletedAt")
            )
        }
    }
}

data class BackupBookmarkMetadata(
    val id: Long,
    val documentId: Long,
    val title: String,
    val pageNumber: Int,
    val createdAt: Long
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("documentId", documentId)
        obj.put("title", title)
        obj.put("pageNumber", pageNumber)
        obj.put("createdAt", createdAt)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): BackupBookmarkMetadata {
            return BackupBookmarkMetadata(
                id = obj.getLong("id"),
                documentId = obj.getLong("documentId"),
                title = obj.getString("title"),
                pageNumber = obj.optInt("pageNumber", 1),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}

data class BackupSettingsMetadata(
    val autoLockTimeout: String = "SCREEN_OFF",
    val biometricEnabled: Boolean = true,
    val screenProtectionEnabled: Boolean = false,
    val recentAppPrivacyEnabled: Boolean = true,
    val clipboardProtectionEnabled: Boolean = true,
    val clipboardTimeoutSeconds: Int = 30,
    val notificationPrivacyLevel: String = "CONCEAL_CONTENT",
    val reminderSchedule: String = "WEEKLY"
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("autoLockTimeout", autoLockTimeout)
        obj.put("biometricEnabled", biometricEnabled)
        obj.put("screenProtectionEnabled", screenProtectionEnabled)
        obj.put("recentAppPrivacyEnabled", recentAppPrivacyEnabled)
        obj.put("clipboardProtectionEnabled", clipboardProtectionEnabled)
        obj.put("clipboardTimeoutSeconds", clipboardTimeoutSeconds)
        obj.put("notificationPrivacyLevel", notificationPrivacyLevel)
        obj.put("reminderSchedule", reminderSchedule)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): BackupSettingsMetadata {
            return BackupSettingsMetadata(
                autoLockTimeout = obj.optString("autoLockTimeout", "SCREEN_OFF"),
                biometricEnabled = obj.optBoolean("biometricEnabled", true),
                screenProtectionEnabled = obj.optBoolean("screenProtectionEnabled", false),
                recentAppPrivacyEnabled = obj.optBoolean("recentAppPrivacyEnabled", true),
                clipboardProtectionEnabled = obj.optBoolean("clipboardProtectionEnabled", true),
                clipboardTimeoutSeconds = obj.optInt("clipboardTimeoutSeconds", 30),
                notificationPrivacyLevel = obj.optString("notificationPrivacyLevel", "CONCEAL_CONTENT"),
                reminderSchedule = obj.optString("reminderSchedule", "WEEKLY")
            )
        }
    }
}

/**
 * Authenticated manifest payload encrypted with the Backup Master Key (AES-256-GCM).
 */
data class BackupManifestData(
    val formatVersion: Int = BackupConstants.FORMAT_VERSION,
    val createdAt: Long = System.currentTimeMillis(),
    val protectionType: String,
    val itemCount: Int,
    val folderCount: Int,
    val totalOriginalSizeBytes: Long,
    val totalEncryptedSizeBytes: Long,
    val objectsOverallChecksumSha256: String,
    val items: List<BackupItemMetadata>,
    val folders: List<BackupFolderMetadata>,
    val bookmarks: List<BackupBookmarkMetadata>,
    val safeSettings: BackupSettingsMetadata
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("formatVersion", formatVersion)
        root.put("createdAt", createdAt)
        root.put("protectionType", protectionType)
        root.put("itemCount", itemCount)
        root.put("folderCount", folderCount)
        root.put("totalOriginalSizeBytes", totalOriginalSizeBytes)
        root.put("totalEncryptedSizeBytes", totalEncryptedSizeBytes)
        root.put("objectsOverallChecksumSha256", objectsOverallChecksumSha256)

        val itemsArr = JSONArray()
        items.forEach { itemsArr.put(it.toJsonObject()) }
        root.put("items", itemsArr)

        val foldersArr = JSONArray()
        folders.forEach { foldersArr.put(it.toJsonObject()) }
        root.put("folders", foldersArr)

        val bookmarksArr = JSONArray()
        bookmarks.forEach { bookmarksArr.put(it.toJsonObject()) }
        root.put("bookmarks", bookmarksArr)

        root.put("safeSettings", safeSettings.toJsonObject())
        return root.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): BackupManifestData {
            val root = JSONObject(jsonStr)
            val itemsArr = root.optJSONArray("items") ?: JSONArray()
            val items = mutableListOf<BackupItemMetadata>()
            for (i in 0 until itemsArr.length()) {
                items.add(BackupItemMetadata.fromJsonObject(itemsArr.getJSONObject(i)))
            }

            val foldersArr = root.optJSONArray("folders") ?: JSONArray()
            val folders = mutableListOf<BackupFolderMetadata>()
            for (i in 0 until foldersArr.length()) {
                folders.add(BackupFolderMetadata.fromJsonObject(foldersArr.getJSONObject(i)))
            }

            val bookmarksArr = root.optJSONArray("bookmarks") ?: JSONArray()
            val bookmarks = mutableListOf<BackupBookmarkMetadata>()
            for (i in 0 until bookmarksArr.length()) {
                bookmarks.add(BackupBookmarkMetadata.fromJsonObject(bookmarksArr.getJSONObject(i)))
            }

            val safeSettings = if (root.has("safeSettings")) {
                BackupSettingsMetadata.fromJsonObject(root.getJSONObject("safeSettings"))
            } else {
                BackupSettingsMetadata()
            }

            return BackupManifestData(
                formatVersion = root.optInt("formatVersion", 1),
                createdAt = root.optLong("createdAt", System.currentTimeMillis()),
                protectionType = root.optString("protectionType", BackupProtectionType.PASSPHRASE.name),
                itemCount = root.optInt("itemCount", items.size),
                folderCount = root.optInt("folderCount", folders.size),
                totalOriginalSizeBytes = root.optLong("totalOriginalSizeBytes", 0L),
                totalEncryptedSizeBytes = root.optLong("totalEncryptedSizeBytes", 0L),
                objectsOverallChecksumSha256 = root.optString("objectsOverallChecksumSha256", ""),
                items = items,
                folders = folders,
                bookmarks = bookmarks,
                safeSettings = safeSettings
            )
        }
    }
}

/**
 * Real-time operational progress state for backup creation.
 */
sealed class BackupCreationState {
    data object Idle : BackupCreationState()
    data class Preparing(val totalItems: Int, val totalSizeBytes: Long) : BackupCreationState()
    data class Encrypting(
        val currentItemIndex: Int,
        val totalItems: Int,
        val bytesProcessed: Long,
        val totalBytes: Long,
        val currentFileTitle: String
    ) : BackupCreationState()
    data object Authenticating : BackupCreationState()
    data object Finalizing : BackupCreationState()
    data class Completed(
        val backupSizeBytes: Long,
        val objectCount: Int,
        val destinationLabel: String,
        val durationMs: Long
    ) : BackupCreationState()
    data class Error(val message: String) : BackupCreationState()
}

/**
 * Result of non-destructive cryptographic and structural verification of a backup.
 */
data class BackupVerificationResult(
    val status: BackupVerificationStatus,
    val formatVersion: Int,
    val createdAt: Long,
    val objectCount: Int,
    val folderCount: Int,
    val totalSizeBytes: Long,
    val protectionType: BackupProtectionType,
    val details: List<String>,
    val errorMessage: String? = null
)

/**
 * Safe preview metadata presented to the user before confirming restore.
 */
data class RestorePreviewData(
    val backupName: String,
    val createdAt: Long,
    val formatVersion: Int,
    val objectCount: Int,
    val folderCount: Int,
    val totalSizeBytes: Long,
    val protectionType: BackupProtectionType,
    val integrityStatus: String,
    val items: List<BackupItemMetadata>,
    val conflictCount: Int
)

/**
 * Real-time operational progress state for restore.
 */
sealed class RestoreProgressState {
    data object Idle : RestoreProgressState()
    data object Inspecting : RestoreProgressState()
    data object Authenticating : RestoreProgressState()
    data class StagingObjects(
        val currentItemIndex: Int,
        val totalItems: Int,
        val bytesProcessed: Long,
        val totalBytes: Long
    ) : RestoreProgressState()
    data object ReconcilingDatabase : RestoreProgressState()
    data object VerifyingRestoredState : RestoreProgressState()
    data class Completed(
        val restoredCount: Int,
        val mergedCount: Int,
        val conflictsResolved: Int
    ) : RestoreProgressState()
    data class Error(val message: String) : RestoreProgressState()
}
