package com.example.feature.security

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.BackupHistoryEntity
import com.example.core.database.SecurityAuditLogEntity
import com.example.core.database.VaultRepository
import com.example.core.security.AutoLockTimeout
import com.example.core.security.BiometricAuthListener
import com.example.core.security.BiometricAuthenticator
import com.example.core.security.BiometricHardwareStatus
import com.example.core.security.NotificationPrivacyLevel
import com.example.core.security.SecurityCheckupReport
import com.example.core.security.SecurityStatusManager
import com.example.core.security.SessionSecurityManager
import com.example.core.security.VaultIntegrityReport
import com.example.core.security.VaultSecurityDashboardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SecurityCenterViewModel(
    private val securityStatusManager: SecurityStatusManager,
    private val sessionManager: SessionSecurityManager,
    private val vaultRepository: VaultRepository,
    private val biometricAuthenticator: BiometricAuthenticator
) : ViewModel() {

    val dashboardState: StateFlow<VaultSecurityDashboardState?> =
        securityStatusManager.securityDashboardFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    val auditLogs: StateFlow<List<SecurityAuditLogEntity>> =
        vaultRepository.auditLogs
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val latestBackup: StateFlow<BackupHistoryEntity?> =
        vaultRepository.backupHistory
            .map { list -> list.firstOrNull() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    val latestVerifiedBackup: StateFlow<BackupHistoryEntity?> =
        vaultRepository.backupHistory
            .map { list -> list.firstOrNull { it.isVerified } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    private val _integrityReport = MutableStateFlow<VaultIntegrityReport?>(null)
    val integrityReport: StateFlow<VaultIntegrityReport?> = _integrityReport.asStateFlow()

    private val _isCheckingIntegrity = MutableStateFlow(false)
    val isCheckingIntegrity: StateFlow<Boolean> = _isCheckingIntegrity.asStateFlow()

    private val _checkupReport = MutableStateFlow<SecurityCheckupReport?>(null)
    val checkupReport: StateFlow<SecurityCheckupReport?> = _checkupReport.asStateFlow()

    private val _isCheckingSecurity = MutableStateFlow(false)
    val isCheckingSecurity: StateFlow<Boolean> = _isCheckingSecurity.asStateFlow()

    private val _changePinDialogVisible = MutableStateFlow(false)
    val changePinDialogVisible: StateFlow<Boolean> = _changePinDialogVisible.asStateFlow()

    private val _disableBiometricDialogVisible = MutableStateFlow(false)
    val disableBiometricDialogVisible: StateFlow<Boolean> = _disableBiometricDialogVisible.asStateFlow()

    private val _emergencyLockDialogVisible = MutableStateFlow(false)
    val emergencyLockDialogVisible: StateFlow<Boolean> = _emergencyLockDialogVisible.asStateFlow()

    private val _clearActivityDialogVisible = MutableStateFlow(false)
    val clearActivityDialogVisible: StateFlow<Boolean> = _clearActivityDialogVisible.asStateFlow()

    private val _purgeTempFilesDialogVisible = MutableStateFlow(false)
    val purgeTempFilesDialogVisible: StateFlow<Boolean> = _purgeTempFilesDialogVisible.asStateFlow()

    private val _integrityReportDialogVisible = MutableStateFlow(false)
    val integrityReportDialogVisible: StateFlow<Boolean> = _integrityReportDialogVisible.asStateFlow()

    private val _checkupReportDialogVisible = MutableStateFlow(false)
    val checkupReportDialogVisible: StateFlow<Boolean> = _checkupReportDialogVisible.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    fun showChangePinDialog(show: Boolean) {
        _changePinDialogVisible.value = show
    }

    fun showDisableBiometricDialog(show: Boolean) {
        _disableBiometricDialogVisible.value = show
    }

    fun showEmergencyLockDialog(show: Boolean) {
        _emergencyLockDialogVisible.value = show
    }

    fun showClearActivityDialog(show: Boolean) {
        _clearActivityDialogVisible.value = show
    }

    fun showPurgeTempFilesDialog(show: Boolean) {
        _purgeTempFilesDialogVisible.value = show
    }

    fun showIntegrityReportDialog(show: Boolean) {
        _integrityReportDialogVisible.value = show
    }

    fun showCheckupReportDialog(show: Boolean) {
        _checkupReportDialogVisible.value = show
    }

    fun dismissMessage() {
        _uiMessage.value = null
    }

    fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        sessionManager.setAutoLockTimeout(timeout)
        viewModelScope.launch {
            vaultRepository.logSecurityEvent(
                action = "AUTOLOCK_INTERVAL_CHANGED",
                details = "Auto-lock delay set to ${timeout.displayName}",
                isSuccess = true
            )
        }
    }

    fun toggleBiometric(activity: FragmentActivity?) {
        if (sessionManager.isBiometricEnabled()) {
            _disableBiometricDialogVisible.value = true
        } else {
            enableBiometricWithPrompt(activity)
        }
    }

    fun enableBiometricWithPrompt(activity: FragmentActivity?) {
        val status = biometricAuthenticator.queryStatus()
        if (status != BiometricHardwareStatus.AVAILABLE) {
            sessionManager.setBiometricEnabled(true)
            viewModelScope.launch {
                vaultRepository.logSecurityEvent(
                    action = "BIOMETRIC_ENABLED",
                    details = "Biometric preference enabled ($status)",
                    isSuccess = true
                )
            }
            _uiMessage.value = when (status) {
                BiometricHardwareStatus.NOT_ENROLLED -> "Biometric enabled. Enroll fingerprint in Android Settings for hardware biometric."
                BiometricHardwareStatus.HARDWARE_UNAVAILABLE -> "Biometric enabled. Sensor currently unavailable."
                BiometricHardwareStatus.UNSUPPORTED -> "Biometric enabled in hardware enclave simulation mode."
                else -> "Biometric unlock activated."
            }
            return
        }

        biometricAuthenticator.authenticate(
            activity = activity,
            title = "Confirm Biometrics",
            subtitle = "Authenticate to enable biometric unlock for PrivateVault",
            listener = object : BiometricAuthListener {
                override fun onAuthenticationSucceeded() {
                    sessionManager.setBiometricEnabled(true)
                    viewModelScope.launch {
                        vaultRepository.logSecurityEvent(
                            action = "BIOMETRIC_ENABLED",
                            details = "Biometric authentication successfully activated",
                            isSuccess = true
                        )
                    }
                    _uiMessage.value = "Biometric unlock activated."
                }

                override fun onAuthenticationFailed() {
                    _uiMessage.value = "Biometric authentication failed."
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    _uiMessage.value = "Biometric setup error: $errString"
                }
            }
        )
    }

    fun confirmDisableBiometric() {
        sessionManager.setBiometricEnabled(false)
        _disableBiometricDialogVisible.value = false
        viewModelScope.launch {
            vaultRepository.logSecurityEvent(
                action = "BIOMETRIC_DISABLED",
                details = "Biometric authentication disabled by user. Master PIN remains active.",
                isSuccess = true
            )
        }
        _uiMessage.value = "Biometric unlock disabled. Master PIN remains active."
    }

    fun changeMasterPin(currentPin: String, newPin: String, confirmPin: String, onSuccess: () -> Unit) {
        if (newPin != confirmPin) {
            _uiMessage.value = "New PIN and confirmation do not match."
            return
        }
        if (newPin.length < 4 || newPin.length > 8 || !newPin.all { it.isDigit() }) {
            _uiMessage.value = "New PIN must be between 4 and 8 digits."
            return
        }

        val result = sessionManager.changeMasterPin(currentPin, newPin)
        result.onSuccess {
            _changePinDialogVisible.value = false
            viewModelScope.launch {
                vaultRepository.logSecurityEvent(
                    action = "PIN_CHANGED",
                    details = "Master PIN successfully changed (${newPin.length} digits)",
                    isSuccess = true
                )
            }
            _uiMessage.value = "Master PIN updated successfully."
            onSuccess()
        }.onFailure { error ->
            _uiMessage.value = error.message ?: "Failed to change master PIN."
        }
    }

    fun setScreenProtectionEnabled(enabled: Boolean) {
        sessionManager.setScreenProtectionEnabled(enabled)
        viewModelScope.launch {
            vaultRepository.logSecurityEvent(
                action = "SCREEN_PROTECTION_TOGGLED",
                details = "Screenshot & screen capture protection set to $enabled",
                isSuccess = true
            )
        }
    }

    fun setRecentAppPrivacyEnabled(enabled: Boolean) {
        sessionManager.setRecentAppPrivacyEnabled(enabled)
        viewModelScope.launch {
            vaultRepository.logSecurityEvent(
                action = "RECENT_APPS_PRIVACY_TOGGLED",
                details = "Recent apps preview masking set to $enabled",
                isSuccess = true
            )
        }
    }

    fun setClipboardProtectionEnabled(enabled: Boolean) {
        sessionManager.setClipboardProtectionEnabled(enabled)
        viewModelScope.launch {
            vaultRepository.logSecurityEvent(
                action = "CLIPBOARD_DEFENSE_TOGGLED",
                details = "Clipboard auto-clear defense set to $enabled",
                isSuccess = true
            )
        }
    }

    fun setClipboardTimeoutSeconds(seconds: Int) {
        sessionManager.setClipboardTimeoutSeconds(seconds)
    }

    fun setNotificationPrivacyLevel(level: NotificationPrivacyLevel) {
        sessionManager.setNotificationPrivacyLevel(level)
        viewModelScope.launch {
            vaultRepository.logSecurityEvent(
                action = "NOTIFICATION_PRIVACY_CHANGED",
                details = "Notification privacy level set to ${level.displayName}",
                isSuccess = true
            )
        }
    }

    fun manualLockVault(onLocked: () -> Unit) {
        viewModelScope.launch {
            vaultRepository.logSecurityEvent(
                action = "VAULT_LOCKED_MANUAL",
                details = "Vault session manually sealed by user",
                isSuccess = true
            )
            sessionManager.lockVault()
            onLocked()
        }
    }

    fun executeEmergencyLock(onLocked: () -> Unit) {
        viewModelScope.launch {
            _emergencyLockDialogVisible.value = false
            securityStatusManager.executeEmergencyLock()
            _uiMessage.value = "Emergency lockdown completed. Session sealed."
            onLocked()
        }
    }

    fun runIntegrityCheck() {
        if (_isCheckingIntegrity.value) return
        viewModelScope.launch {
            _isCheckingIntegrity.value = true
            try {
                val report = securityStatusManager.runVaultIntegrityCheck()
                _integrityReport.value = report
                _integrityReportDialogVisible.value = true
            } catch (e: Exception) {
                _uiMessage.value = "Integrity check encountered an issue: ${e.message}"
            } finally {
                _isCheckingIntegrity.value = false
            }
        }
    }

    fun runSecurityCheckup() {
        if (_isCheckingSecurity.value) return
        viewModelScope.launch {
            _isCheckingSecurity.value = true
            try {
                val report = securityStatusManager.runSecurityCheckup()
                _checkupReport.value = report
                _checkupReportDialogVisible.value = true
            } catch (e: Exception) {
                _uiMessage.value = "Security checkup failed: ${e.message}"
            } finally {
                _isCheckingSecurity.value = false
            }
        }
    }

    fun purgeTemporaryFiles() {
        viewModelScope.launch {
            _purgeTempFilesDialogVisible.value = false
            securityStatusManager.purgeTemporaryWorkingFiles()
            _uiMessage.value = "Temporary files shredded and purged."
        }
    }

    fun clearSecurityActivity() {
        viewModelScope.launch {
            _clearActivityDialogVisible.value = false
            securityStatusManager.clearSecurityActivity()
            _uiMessage.value = "Security activity history cleared."
        }
    }
}
