package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        VaultItemEntity::class,
        VaultFolderEntity::class,
        SecurityAuditLogEntity::class,
        DocumentBookmarkEntity::class,
        BackupHistoryEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {

    abstract fun vaultDao(): VaultDao
    abstract fun vaultFolderDao(): VaultFolderDao
    abstract fun securityAuditDao(): SecurityAuditDao
    abstract fun documentBookmarkDao(): DocumentBookmarkDao
    abstract fun backupHistoryDao(): BackupHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        fun getInstance(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "private_vault_metadata.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
