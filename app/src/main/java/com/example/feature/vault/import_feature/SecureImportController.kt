package com.example.feature.vault.import_feature

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.core.database.DuplicateCheckResult
import com.example.core.database.DuplicateResolution
import com.example.core.database.VaultRepository
import com.example.core.storage.VaultCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SecureImportController(
    private val context: Context,
    private val repository: VaultRepository,
    private val coroutineScope: CoroutineScope
) {
    private val _importState = MutableStateFlow<ImportProgressState>(ImportProgressState.Idle)
    val importState: StateFlow<ImportProgressState> = _importState.asStateFlow()

    private var activeImportJob: Job? = null
    @Volatile
    private var isCancelledRequested = false

    fun startImport(
        uris: List<Uri>,
        targetCategory: VaultCategory? = null,
        targetFolderId: Long? = null
    ) {
        if (uris.isEmpty()) return

        isCancelledRequested = false
        activeImportJob?.cancel()

        activeImportJob = coroutineScope.launch {
            var successfullyImported = 0
            var cumulativeBytes = 0L

            val totalCount = uris.size

            for ((index, uri) in uris.withIndex()) {
                if (isCancelledRequested) {
                    _importState.value = ImportProgressState.Cancelled
                    break
                }

                val fileIndex = index + 1
                val fileDetails = queryUriDetails(uri)
                val fileName = fileDetails.name
                val fileSize = fileDetails.size
                val mimeType = fileDetails.mimeType
                val category = targetCategory ?: VaultCategory.fromFileNameAndMime(fileName, mimeType)

                _importState.value = ImportProgressState.Preparing(
                    fileName = fileName,
                    currentFileIndex = fileIndex,
                    totalFiles = totalCount
                )

                // Check for duplicate name in target folder
                val duplicateCheck = repository.checkForDuplicate(
                    checksumSha256 = "",
                    title = fileName,
                    folderId = targetFolderId
                )

                var chosenResolution = DuplicateResolution.KEEP_BOTH
                if (duplicateCheck is DuplicateCheckResult.NameConflict) {
                    val deferred = CompletableDeferred<DuplicateResolution>()
                    _importState.value = ImportProgressState.DuplicatePrompt(
                        fileName = fileName,
                        currentFileIndex = fileIndex,
                        totalFiles = totalCount,
                        conflictReason = "A file named \"$fileName\" already exists in this partition.",
                        onResolutionSelected = { resolution ->
                            deferred.complete(resolution)
                        }
                    )
                    chosenResolution = deferred.await()

                    if (chosenResolution == DuplicateResolution.CANCEL || chosenResolution == DuplicateResolution.SKIP) {
                        continue
                    }
                }

                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Could not open read stream for selected file")

                    val result = inputStream.use { stream ->
                        repository.importItem(
                            title = fileName,
                            category = category,
                            mimeType = mimeType,
                            inputStream = stream,
                            folderId = targetFolderId,
                            expectedSizeBytes = fileSize,
                            resolution = chosenResolution,
                            onProgress = { processed, total, phase ->
                                if (phase == "ENCRYPTING") {
                                    val pct = if (total > 0) (processed.toFloat() / total).coerceIn(0f, 1f) else 0.5f
                                    _importState.value = ImportProgressState.Encrypting(
                                        fileName = fileName,
                                        currentFileIndex = fileIndex,
                                        totalFiles = totalCount,
                                        bytesProcessed = processed,
                                        totalBytes = if (total > 0) total else processed,
                                        percentage = pct
                                    )
                                } else if (phase == "VERIFYING") {
                                    _importState.value = ImportProgressState.Verifying(
                                        fileName = fileName,
                                        currentFileIndex = fileIndex,
                                        totalFiles = totalCount
                                    )
                                }
                            },
                            isCancelled = { isCancelledRequested }
                        )
                    }

                    if (result.isSuccess) {
                        successfullyImported++
                        cumulativeBytes += result.getOrNull()?.sizeBytes ?: 0L
                    } else {
                        val err = result.exceptionOrNull()
                        if (err is CancellationException || isCancelledRequested) {
                            _importState.value = ImportProgressState.Cancelled
                            return@launch
                        }
                        _importState.value = ImportProgressState.Error(
                            fileName = fileName,
                            errorMessage = err?.message ?: "Encryption import failed"
                        )
                        return@launch
                    }
                } catch (e: Exception) {
                    if (e is CancellationException || isCancelledRequested) {
                        _importState.value = ImportProgressState.Cancelled
                        return@launch
                    }
                    _importState.value = ImportProgressState.Error(
                        fileName = fileName,
                        errorMessage = e.message ?: "Stream access failed"
                    )
                    return@launch
                }
            }

            if (!isCancelledRequested) {
                _importState.value = ImportProgressState.Success(
                    importedCount = successfullyImported,
                    totalBytes = cumulativeBytes,
                    summaryText = "Successfully encrypted and sealed $successfullyImported file(s) in the private vault."
                )
            }
        }
    }

    fun cancelImport() {
        isCancelledRequested = true
        activeImportJob?.cancel()
        _importState.value = ImportProgressState.Cancelled
    }

    fun dismiss() {
        _importState.value = ImportProgressState.Idle
    }

    private data class UriDetails(val name: String, val size: Long, val mimeType: String)

    private fun queryUriDetails(uri: Uri): UriDetails {
        var name = "secure_import_${System.currentTimeMillis()}"
        var size = -1L
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        val queriedName = cursor.getString(nameIndex)
                        if (!queriedName.isNullOrBlank()) {
                            name = queriedName
                        }
                    }
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {}

        return UriDetails(name = name, size = size, mimeType = mimeType)
    }
}
