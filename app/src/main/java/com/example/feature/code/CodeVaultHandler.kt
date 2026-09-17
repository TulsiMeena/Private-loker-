package com.example.feature.code

import com.example.core.database.VaultItemEntity

/**
 * Extension point contract for Private Code & Snippet Vault.
 */
interface CodeVaultHandler {
    suspend fun saveCodeSnippet(title: String, language: String, source: String): Result<VaultItemEntity>
    suspend fun readCodeSnippet(item: VaultItemEntity): Result<String>
}

class DefaultCodeVaultHandler : CodeVaultHandler {
    override suspend fun saveCodeSnippet(title: String, language: String, source: String): Result<VaultItemEntity> {
        return Result.failure(NotImplementedError("Code vault handler reserved for next iteration"))
    }

    override suspend fun readCodeSnippet(item: VaultItemEntity): Result<String> {
        return Result.failure(NotImplementedError("Code snippet read reserved for next iteration"))
    }
}
