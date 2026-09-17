package com.example.feature.notes

import com.example.core.database.VaultItemEntity

/**
 * Extension point contract for Encrypted Text & Markdown Notes.
 */
interface SecureNotesHandler {
    suspend fun createNote(title: String, content: String): Result<VaultItemEntity>
    suspend fun readNote(item: VaultItemEntity): Result<String>
    suspend fun updateNote(item: VaultItemEntity, newContent: String): Result<Unit>
}

class DefaultSecureNotesHandler : SecureNotesHandler {
    override suspend fun createNote(title: String, content: String): Result<VaultItemEntity> {
        return Result.failure(NotImplementedError("Secure notes creation reserved for next iteration"))
    }

    override suspend fun readNote(item: VaultItemEntity): Result<String> {
        return Result.failure(NotImplementedError("Secure notes reading reserved for next iteration"))
    }

    override suspend fun updateNote(item: VaultItemEntity, newContent: String): Result<Unit> {
        return Result.failure(NotImplementedError("Secure notes update reserved for next iteration"))
    }
}
