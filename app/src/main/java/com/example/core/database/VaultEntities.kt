package com.example.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Metadata record for a vault item.
 *
 * Security Invariants:
 * - Never contains plaintext file contents.
 * - Never contains encryption keys, PINs, or salt.
 * - [encryptedPath] is an opaque UUID referring to a .pvault container.
 * - Original filename is only stored here as [title].
 */
@Entity(
    tableName = "vault_items",
    indices = [
        Index("folderId"),
        Index("isTrash"),
        Index("category"),
        Index("checksumSha256")
    ]
)
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: String,
    val encryptedPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val checksumSha256: String = "",
    val folderId: Long? = null,
    val isTrash: Boolean = false,
    val deletedAt: Long? = null,
    val originalFolderId: Long? = null,
    val encryptedSizeBytes: Long = 0L,
    val storageVersion: Int = 1,
    val tags: String = "",
    val lastAccessedAt: Long? = null
)

/**
 * Hierarchical virtual folder entity within the encrypted vault.
 * Folders organize metadata; physical storage remains uniformly encrypted.
 */
@Entity(
    tableName = "vault_folders",
    indices = [
        Index("parentId"),
        Index("isTrash")
    ]
)
data class VaultFolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isTrash: Boolean = false,
    val deletedAt: Long? = null
)

@Entity(tableName = "security_audit_logs")
data class SecurityAuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String,
    val details: String,
    val isSuccess: Boolean
)

/**
 * Local metadata bookmark for specific locations inside encrypted documents.
 */
@Entity(
    tableName = "document_bookmarks",
    indices = [
        Index("documentId")
    ]
)
data class DocumentBookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val title: String,
    val pageNumber: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Local metadata record for backup operations.
 *
 * Security Requirements:
 * - Stores minimal safe operational metadata only.
 * - NEVER stores recovery passphrase, master PIN, or cryptographic keys.
 * - NEVER stores private file names or file contents.
 */
@Entity(
    tableName = "backup_history",
    indices = [
        Index("timestamp"),
        Index("isVerified")
    ]
)
data class BackupHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val backupSizeBytes: Long = 0L,
    val formatVersion: Int = 1,
    val destinationLabel: String = "External Storage",
    val uriString: String? = null,
    val objectCount: Int = 0,
    val folderCount: Int = 0,
    val isVerified: Boolean = false,
    val lastVerifiedTimestamp: Long? = null,
    val verificationStatus: String = "PENDING", // "VERIFIED", "ATTENTION_REQUIRED", "FAILED", "PENDING"
    val isSuccess: Boolean = true,
    val protectionType: String = "PASSPHRASE", // "PASSPHRASE" or "RECOVERY_KEY"
    val errorDetails: String? = null
)

