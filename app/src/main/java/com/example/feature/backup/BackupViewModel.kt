package com.example.feature.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.BackupHistoryDao
import com.example.core.database.BackupHistoryEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel orchestrating the Secure Backup & Recovery Center.
 */
class BackupViewModel(
    val backupManager: SecureBackupManager,
    private val backupHistoryDao: BackupHistoryDao
) : ViewModel() {

    val historyList: StateFlow<List<BackupHistoryEntity>> = backupHistoryDao.getAllBackupHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminderSchedule: StateFlow<BackupReminderSchedule> = backupManager.reminderManager.schedule

    private val _latestBackup = MutableStateFlow<BackupHistoryEntity?>(null)
    val latestBackup: StateFlow<BackupHistoryEntity?> = _latestBackup.asStateFlow()

    private val _latestVerifiedBackup = MutableStateFlow<BackupHistoryEntity?>(null)
    val latestVerifiedBackup: StateFlow<BackupHistoryEntity?> = _latestVerifiedBackup.asStateFlow()

    private val _isBackupDue = MutableStateFlow(false)
    val isBackupDue: StateFlow<Boolean> = _isBackupDue.asStateFlow()

    // Backup Creation States
    private val _creationState = MutableStateFlow<BackupCreationState>(BackupCreationState.Idle)
    val creationState: StateFlow<BackupCreationState> = _creationState.asStateFlow()

    // Verification States
    private val _verificationResult = MutableStateFlow<BackupVerificationResult?>(null)
    val verificationResult: StateFlow<BackupVerificationResult?> = _verificationResult.asStateFlow()

    private val _isVerifying = MutableStateFlow(false)
    val isVerifying: StateFlow<Boolean> = _isVerifying.asStateFlow()

    // Restore States
    private val _restorePreview = MutableStateFlow<RestorePreviewData?>(null)
    val restorePreview: StateFlow<RestorePreviewData?> = _restorePreview.asStateFlow()

    private val _restoreState = MutableStateFlow<RestoreProgressState>(RestoreProgressState.Idle)
    val restoreState: StateFlow<RestoreProgressState> = _restoreState.asStateFlow()

    // UI Dialog States
    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    private val _showRestoreSecretDialog = MutableStateFlow(false)
    val showRestoreSecretDialog: StateFlow<Boolean> = _showRestoreSecretDialog.asStateFlow()

    private val _showRestorePreviewDialog = MutableStateFlow(false)
    val showRestorePreviewDialog: StateFlow<Boolean> = _showRestorePreviewDialog.asStateFlow()

    private val _showVerificationDialog = MutableStateFlow(false)
    val showVerificationDialog: StateFlow<Boolean> = _showVerificationDialog.asStateFlow()

    private val _showRecoveryKeyDialog = MutableStateFlow(false)
    val showRecoveryKeyDialog: StateFlow<Boolean> = _showRecoveryKeyDialog.asStateFlow()

    private val _generatedRecoveryKey = MutableStateFlow<String?>(null)
    val generatedRecoveryKey: StateFlow<String?> = _generatedRecoveryKey.asStateFlow()

    private val _pendingRestoreUri = MutableStateFlow<Uri?>(null)
    val pendingRestoreUri: StateFlow<Uri?> = _pendingRestoreUri.asStateFlow()

    private val _pendingRestoreSecret = MutableStateFlow<CharArray?>(null)

    private val _pendingVerifyUri = MutableStateFlow<Uri?>(null)
    val pendingVerifyUri: StateFlow<Uri?> = _pendingVerifyUri.asStateFlow()

    private val _pendingVerifySecretDialog = MutableStateFlow(false)
    val pendingVerifySecretDialog: StateFlow<Boolean> = _pendingVerifySecretDialog.asStateFlow()

    private val _uiToast = MutableStateFlow<String?>(null)
    val uiToast: StateFlow<String?> = _uiToast.asStateFlow()

    private var activeJob: Job? = null
    private var isCancelledFlag = false

    init {
        refreshBackupStatus()
    }

    fun refreshBackupStatus() {
        viewModelScope.launch {
            val latest = backupHistoryDao.getLatestBackup()
            _latestBackup.value = latest
            _latestVerifiedBackup.value = backupHistoryDao.getLatestVerifiedBackup()
            _isBackupDue.value = backupManager.reminderManager.isBackupDue(latest?.timestamp)
        }
    }

    fun setReminderSchedule(schedule: BackupReminderSchedule) {
        backupManager.reminderManager.setSchedule(schedule)
        refreshBackupStatus()
    }

    // --- Creation Flow ---

    fun openCreateBackupDialog() {
        _showCreateDialog.value = true
    }

    fun dismissCreateDialog() {
        _showCreateDialog.value = false
    }

    fun generateNewRecoveryKey(): String {
        val key = BackupCryptoHelper.generateRecoveryKey()
        _generatedRecoveryKey.value = key
        _showRecoveryKeyDialog.value = true
        return key
    }

    fun dismissRecoveryKeyDialog() {
        _showRecoveryKeyDialog.value = false
    }

    fun executeBackupCreation(
        destinationUri: Uri,
        protectionType: BackupProtectionType,
        secretChars: CharArray,
        destinationLabel: String
    ) {
        isCancelledFlag = false
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            backupManager.createBackupSaf(
                destinationUri = destinationUri,
                protectionType = protectionType,
                secretChars = secretChars,
                destinationLabel = destinationLabel,
                onProgress = { state -> _creationState.value = state },
                isCancelled = { isCancelledFlag }
            ).fold(
                onSuccess = {
                    refreshBackupStatus()
                    _uiToast.value = "Backup created and verified successfully"
                },
                onFailure = { err ->
                    _uiToast.value = "Backup failed: ${err.message}"
                }
            )
        }
    }

    fun cancelActiveOperation() {
        isCancelledFlag = true
        activeJob?.cancel()
        _creationState.value = BackupCreationState.Idle
        _restoreState.value = RestoreProgressState.Idle
        _isVerifying.value = false
        backupManager.cleanupStaleStagingFiles()
    }

    fun resetCreationState() {
        _creationState.value = BackupCreationState.Idle
    }

    // --- Restore Flow ---

    fun onRestoreFileSelected(uri: Uri) {
        _pendingRestoreUri.value = uri
        _showRestoreSecretDialog.value = true
    }

    fun dismissRestoreSecretDialog() {
        _showRestoreSecretDialog.value = false
        _pendingRestoreUri.value = null
        _pendingRestoreSecret.value = null
    }

    fun inspectPendingRestore(secretChars: CharArray) {
        val uri = _pendingRestoreUri.value ?: return
        _pendingRestoreSecret.value = secretChars
        _restoreState.value = RestoreProgressState.Inspecting

        viewModelScope.launch {
            backupManager.inspectBackupForRestore(uri, secretChars).fold(
                onSuccess = { preview ->
                    _restorePreview.value = preview
                    _restoreState.value = RestoreProgressState.Idle
                    _showRestoreSecretDialog.value = false
                    _showRestorePreviewDialog.value = true
                },
                onFailure = { err ->
                    _restoreState.value = RestoreProgressState.Idle
                    _uiToast.value = "Inspection failed: ${err.message}"
                }
            )
        }
    }

    fun dismissRestorePreviewDialog() {
        _showRestorePreviewDialog.value = false
        _restorePreview.value = null
        _pendingRestoreUri.value = null
        _pendingRestoreSecret.value = null
    }

    fun executeRestore(
        restoreMode: RestoreMode,
        conflictResolution: RestoreConflictResolution
    ) {
        val uri = _pendingRestoreUri.value ?: return
        val secret = _pendingRestoreSecret.value ?: return

        isCancelledFlag = false
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            backupManager.restoreBackupSaf(
                backupUri = uri,
                secretChars = secret,
                restoreMode = restoreMode,
                conflictResolution = conflictResolution,
                onProgress = { state -> _restoreState.value = state },
                isCancelled = { isCancelledFlag }
            ).fold(
                onSuccess = { result ->
                    refreshBackupStatus()
                    _showRestorePreviewDialog.value = false
                    _uiToast.value = "Vault successfully restored: ${result.restoredCount} objects"
                },
                onFailure = { err ->
                    _uiToast.value = "Restore failed: ${err.message}"
                }
            )
        }
    }

    fun resetRestoreState() {
        _restoreState.value = RestoreProgressState.Idle
        _restorePreview.value = null
        _pendingRestoreUri.value = null
        _pendingRestoreSecret.value = null
    }

    // --- Verification Flow ---

    fun onVerifyFileSelected(uri: Uri) {
        _pendingVerifyUri.value = uri
        _pendingVerifySecretDialog.value = true
    }

    fun dismissVerifySecretDialog() {
        _pendingVerifySecretDialog.value = false
        _pendingVerifyUri.value = null
    }

    fun executeVerification(secretChars: CharArray?, historyId: Long? = null) {
        val uri = _pendingVerifyUri.value ?: return
        _pendingVerifySecretDialog.value = false
        _isVerifying.value = true
        _showVerificationDialog.value = true

        viewModelScope.launch {
            val result = backupManager.verifyBackupSaf(
                backupUri = uri,
                secretChars = secretChars,
                historyIdToUpdate = historyId
            )
            _verificationResult.value = result
            _isVerifying.value = false
            refreshBackupStatus()
        }
    }

    fun verifyHistoryItem(item: BackupHistoryEntity) {
        if (item.uriString != null) {
            val uri = Uri.parse(item.uriString)
            _pendingVerifyUri.value = uri
            _pendingVerifySecretDialog.value = true
        } else {
            _uiToast.value = "Storage URI not found for this history record"
        }
    }

    fun dismissVerificationDialog() {
        _showVerificationDialog.value = false
        _verificationResult.value = null
        _pendingVerifyUri.value = null
    }

    fun deleteHistoryRecord(id: Long) {
        viewModelScope.launch {
            backupManager.deleteHistoryRecord(id)
            refreshBackupStatus()
            _uiToast.value = "Backup history record removed"
        }
    }

    fun clearUiToast() {
        _uiToast.value = null
    }
}
