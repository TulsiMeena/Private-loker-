package com.example.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Query("SELECT * FROM vault_items WHERE isTrash = 0 ORDER BY createdAt DESC")
    fun getAllItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items")
    suspend fun getAllItemsList(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE isTrash = 0 AND category = :category ORDER BY createdAt DESC")
    fun getItemsByCategory(category: String): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE isTrash = 0 AND (category = 'DOCUMENT' OR category = 'TEXT' OR title LIKE '%.pdf' OR title LIKE '%.doc' OR title LIKE '%.docx' OR title LIKE '%.txt' OR title LIKE '%.rtf' OR title LIKE '%.md' OR title LIKE '%.csv' OR title LIKE '%.json' OR title LIKE '%.xml' OR title LIKE '%.odt' OR title LIKE '%.epub' OR title LIKE '%.log') ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE isTrash = 0 AND (category = 'IMAGE' OR category = 'VIDEO' OR category = 'AUDIO') ORDER BY createdAt DESC")
    fun getAllMedia(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE isTrash = 0 ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentItems(limit: Int = 5): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE isTrash = 0 AND lastAccessedAt IS NOT NULL ORDER BY lastAccessedAt DESC LIMIT :limit")
    fun getRecentlyAccessedItems(limit: Int = 10): Flow<List<VaultItemEntity>>

    @Query("UPDATE vault_items SET lastAccessedAt = :time WHERE id = :id")
    suspend fun updateLastAccessed(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE vault_items SET lastAccessedAt = NULL")
    suspend fun clearRecentHistory()

    @Query("SELECT * FROM vault_items WHERE isTrash = 0 AND (title LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%') ORDER BY createdAt DESC")
    fun searchItems(query: String): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE isTrash = 0 AND ((:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId) ORDER BY createdAt DESC")
    fun getItemsInFolder(folderId: Long?): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE isTrash = 0 AND isFavorite = 1 ORDER BY modifiedAt DESC")
    fun getFavoriteItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE isTrash = 1 ORDER BY deletedAt DESC")
    fun getTrashItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE isTrash = 1 ORDER BY deletedAt DESC")
    suspend fun getTrashItemsList(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE isTrash = 1 AND deletedAt IS NOT NULL AND deletedAt <= :cutoffTime")
    suspend fun getExpiredTrashItems(cutoffTime: Long): List<VaultItemEntity>

    @Query("SELECT COUNT(*) FROM vault_items WHERE isTrash = 1")
    fun getTrashItemCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM vault_items WHERE isTrash = 0")
    fun getTotalItemCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM vault_items WHERE isTrash = 0 AND category = :category")
    fun getItemCountByCategory(category: String): Flow<Int>

    @Query("SELECT SUM(sizeBytes) FROM vault_items WHERE isTrash = 0")
    fun getTotalSizeBytes(): Flow<Long?>

    @Query("SELECT tags FROM vault_items WHERE isTrash = 0 AND tags != ''")
    fun getAllTags(): Flow<List<String>>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Long): VaultItemEntity?

    @Query("SELECT * FROM vault_items WHERE isTrash = 0 AND checksumSha256 = :checksum LIMIT 1")
    suspend fun findByChecksum(checksum: String): VaultItemEntity?

    @Query("SELECT * FROM vault_items WHERE isTrash = 0 AND title = :title AND ((:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId) LIMIT 1")
    suspend fun findByTitleAndFolder(title: String, folderId: Long?): VaultItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: VaultItemEntity): Long

    @Update
    suspend fun updateItem(item: VaultItemEntity)

    @Query("UPDATE vault_items SET isTrash = 1, deletedAt = :deletedAt, originalFolderId = folderId, folderId = NULL WHERE id = :id")
    suspend fun moveToTrash(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE vault_items SET isTrash = 0, deletedAt = NULL, folderId = originalFolderId, originalFolderId = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: Long)

    @Query("UPDATE vault_items SET isTrash = 0, deletedAt = NULL, folderId = :targetFolderId, originalFolderId = NULL, title = :title, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun restoreItemWithDetails(
        id: Long,
        targetFolderId: Long?,
        title: String,
        modifiedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE vault_items SET folderId = :targetFolderId, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun moveItemToFolder(id: Long, targetFolderId: Long?, modifiedAt: Long = System.currentTimeMillis())

    @Query("UPDATE vault_items SET isFavorite = :isFavorite, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean, modifiedAt: Long = System.currentTimeMillis())

    @Query("UPDATE vault_items SET title = :newTitle, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun renameItem(id: Long, newTitle: String, modifiedAt: Long = System.currentTimeMillis())

    @Query("UPDATE vault_items SET tags = :tags, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun updateTags(id: Long, tags: String, modifiedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("DELETE FROM vault_items WHERE isTrash = 1")
    suspend fun clearTrash()

    @Query("DELETE FROM vault_items")
    suspend fun clearAll()
}

@Dao
interface VaultFolderDao {

    @Query("SELECT * FROM vault_folders WHERE isTrash = 0 AND ((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId) ORDER BY name ASC")
    fun getFolders(parentId: Long?): Flow<List<VaultFolderEntity>>

    @Query("SELECT * FROM vault_folders WHERE isTrash = 0 ORDER BY name ASC")
    fun getAllFolders(): Flow<List<VaultFolderEntity>>

    @Query("SELECT * FROM vault_folders WHERE isTrash = 0 ORDER BY name ASC")
    suspend fun getAllFoldersList(): List<VaultFolderEntity>

    @Query("SELECT * FROM vault_folders WHERE id = :id LIMIT 1")
    suspend fun getFolderById(id: Long): VaultFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: VaultFolderEntity): Long

    @Update
    suspend fun updateFolder(folder: VaultFolderEntity)

    @Query("UPDATE vault_folders SET name = :newName WHERE id = :id")
    suspend fun renameFolder(id: Long, newName: String)

    @Query("DELETE FROM vault_folders WHERE id = :id")
    suspend fun deleteFolderById(id: Long)

    @Query("DELETE FROM vault_folders")
    suspend fun clearAll()
}

@Dao
interface SecurityAuditDao {

    @Query("SELECT * FROM security_audit_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 50): Flow<List<SecurityAuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SecurityAuditLogEntity)

    @Query("DELETE FROM security_audit_logs")
    suspend fun clearLogs()
}

@Dao
interface DocumentBookmarkDao {

    @Query("SELECT * FROM document_bookmarks WHERE documentId = :documentId ORDER BY pageNumber ASC, createdAt ASC")
    fun getBookmarksForDocument(documentId: Long): Flow<List<DocumentBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: DocumentBookmarkEntity): Long

    @Delete
    suspend fun deleteBookmark(bookmark: DocumentBookmarkEntity)

    @Query("DELETE FROM document_bookmarks WHERE documentId = :documentId")
    suspend fun deleteBookmarksForDocument(documentId: Long)

    @Query("DELETE FROM document_bookmarks")
    suspend fun clearAllBookmarks()

    @Query("SELECT * FROM document_bookmarks ORDER BY createdAt ASC")
    suspend fun getAllBookmarksList(): List<DocumentBookmarkEntity>
}

@Dao
interface BackupHistoryDao {

    @Query("SELECT * FROM backup_history ORDER BY timestamp DESC")
    fun getAllBackupHistory(): Flow<List<BackupHistoryEntity>>

    @Query("SELECT * FROM backup_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentBackups(limit: Int = 10): Flow<List<BackupHistoryEntity>>

    @Query("SELECT * FROM backup_history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestBackup(): BackupHistoryEntity?

    @Query("SELECT * FROM backup_history WHERE isVerified = 1 ORDER BY lastVerifiedTimestamp DESC LIMIT 1")
    suspend fun getLatestVerifiedBackup(): BackupHistoryEntity?

    @Query("SELECT * FROM backup_history WHERE id = :id LIMIT 1")
    suspend fun getBackupById(id: Long): BackupHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupHistory(record: BackupHistoryEntity): Long

    @Update
    suspend fun updateBackupHistory(record: BackupHistoryEntity)

    @Query("DELETE FROM backup_history WHERE id = :id")
    suspend fun deleteBackupHistory(id: Long)

    @Query("DELETE FROM backup_history")
    suspend fun clearBackupHistory()
}

