package com.example.feature.vault.import_feature

import com.example.core.database.DuplicateResolution

sealed class ImportProgressState {
    data object Idle : ImportProgressState()

    data class Preparing(
        val fileName: String,
        val currentFileIndex: Int,
        val totalFiles: Int
    ) : ImportProgressState()

    data class Encrypting(
        val fileName: String,
        val currentFileIndex: Int,
        val totalFiles: Int,
        val bytesProcessed: Long,
        val totalBytes: Long,
        val percentage: Float
    ) : ImportProgressState()

    data class Verifying(
        val fileName: String,
        val currentFileIndex: Int,
        val totalFiles: Int
    ) : ImportProgressState()

    data class DuplicatePrompt(
        val fileName: String,
        val currentFileIndex: Int,
        val totalFiles: Int,
        val conflictReason: String,
        val onResolutionSelected: (DuplicateResolution) -> Unit
    ) : ImportProgressState()

    data class Success(
        val importedCount: Int,
        val totalBytes: Long,
        val summaryText: String
    ) : ImportProgressState()

    data class Error(
        val fileName: String,
        val errorMessage: String
    ) : ImportProgressState()

    data object Cancelled : ImportProgressState()
}
