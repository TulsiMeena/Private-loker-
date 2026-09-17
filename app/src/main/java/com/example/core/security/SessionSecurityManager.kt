package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Arrays
import kotlin.math.ceil
import kotlin.math.max

enum class AutoLockTimeout(val displayName: String, val millis: Long) {
    IMMEDIATE("Immediately", 0L),
    FIFTEEN_SECONDS("15 Seconds", 15_000L),
    THIRTY_SECONDS("30 Seconds", 30_000L),
    ONE_MINUTE("1 Minute", 60_000L),
    FIVE_MINUTES("5 Minutes", 300_000L),
    TEN_MINUTES("10 Minutes", 600_000L),
    THIRTY_MINUTES("30 Minutes", 1_800_000L),
    NEVER("Never (Keep Unlocked)", -1L)
}

enum class NotificationPrivacyLevel(val displayName: String, val description: String) {
    MINIMAL("Minimal", "Generic notification only (e.g. 'Secure operation completed')"),
    STANDARD("Standard", "Shows partition categories without exposing filenames or details")
}

sealed interface LockState {
    data object SetupRequired : LockState
    data object Locked : LockState
    data object Authenticating : LockState
    data object Unlocked : LockState
    data class Lockout(val remainingSeconds: Int) : LockState
}

/**
 * Manages runtime authentication state, master PIN verification,
 * auto-lock timing, persistent failed attempt penalties with exponential backoff,
 * and background lifecycle security.
 */
class SessionSecurityManager(
    private val context: Context,
    private val secureKeyManager: SecureKeyManager,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        private const val PREFS_NAME = "private_vault_security_session"
        private const val KEY_PIN_HASH = "sec_pin_hash"
        private const val KEY_PIN_SALT = "sec_pin_salt"
        private const val KEY_PIN_LENGTH = "sec_pin_length"
        private const val KEY_AUTO_LOCK_MILLIS = "sec_autolock_ms"
        private const val KEY_BIOMETRIC_ENABLED = "sec_biometric_enabled"
        private const val KEY_FACE_UNLOCK_ENABLED = "sec_face_unlock_enabled"
        private const val KEY_VOICE_LOCK_ENABLED = "sec_voice_lock_enabled"
        private const val KEY_VOICE_PASSPHRASE = "sec_voice_passphrase"
        private const val KEY_VOICE_ANTI_SPOOFING = "sec_voice_anti_spoofing"
        private const val KEY_VOICE_THRESHOLD = "sec_voice_threshold"
        private const val KEY_SCREEN_PROTECTION = "sec_screen_protection"
        private const val KEY_RECENT_APP_PRIVACY = "sec_recent_app_privacy"
        private const val KEY_CLIPBOARD_PROTECTION = "sec_clipboard_protection"
        private const val KEY_CLIPBOARD_TIMEOUT_SEC = "sec_clipboard_timeout_sec"
        private const val KEY_NOTIFICATION_PRIVACY = "sec_notification_privacy"
        private const val KEY_FAILED_ATTEMPTS = "sec_failed_attempts"
        private const val KEY_LOCKOUT_UNTIL_TIMESTAMP = "sec_lockout_until"
        private const val KEY_LOCKOUT_COUNT = "sec_lockout_count"
        private const val KEY_LAST_UNLOCK_TIMESTAMP = "sec_last_unlock_timestamp"
        private const val KEY_LAST_INTEGRITY_CHECK = "sec_last_integrity_check"
        private const val KEY_LAST_INTEGRITY_SUMMARY = "sec_last_integrity_summary"

        const val MAX_FAILED_ATTEMPTS = 5
        const val BASE_LOCKOUT_SECONDS = 30
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _failedAttempts = MutableStateFlow(prefs.getInt(KEY_FAILED_ATTEMPTS, 0))
    val failedAttempts: StateFlow<Int> = _failedAttempts.asStateFlow()

    private val _lockState = MutableStateFlow<LockState>(determineInitialLockState())
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true))
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    private val _faceUnlockEnabled = MutableStateFlow(prefs.getBoolean(KEY_FACE_UNLOCK_ENABLED, true))
    val faceUnlockEnabled: StateFlow<Boolean> = _faceUnlockEnabled.asStateFlow()

    private val _voiceLockEnabled = MutableStateFlow(prefs.getBoolean(KEY_VOICE_LOCK_ENABLED, true))
    val voiceLockEnabled: StateFlow<Boolean> = _voiceLockEnabled.asStateFlow()

    private val _voicePassphrase = MutableStateFlow(
        prefs.getString(KEY_VOICE_PASSPHRASE, "Open Private Vault") ?: "Open Private Vault"
    )
    val voicePassphrase: StateFlow<String> = _voicePassphrase.asStateFlow()

    private val _voiceAntiSpoofingEnabled = MutableStateFlow(prefs.getBoolean(KEY_VOICE_ANTI_SPOOFING, true))
    val voiceAntiSpoofingEnabled: StateFlow<Boolean> = _voiceAntiSpoofingEnabled.asStateFlow()

    private val _voiceConfidenceThreshold = MutableStateFlow(prefs.getFloat(KEY_VOICE_THRESHOLD, 0.88f))
    val voiceConfidenceThreshold: StateFlow<Float> = _voiceConfidenceThreshold.asStateFlow()

    private val _screenProtectionEnabled = MutableStateFlow(
        if (PrivacyProtectionManager.isEmulatorOrPreview()) false
        else prefs.getBoolean(KEY_SCREEN_PROTECTION, false)
    )
    val screenProtectionEnabled: StateFlow<Boolean> = _screenProtectionEnabled.asStateFlow()

    private val _recentAppPrivacyEnabled = MutableStateFlow(prefs.getBoolean(KEY_RECENT_APP_PRIVACY, true))
    val recentAppPrivacyEnabled: StateFlow<Boolean> = _recentAppPrivacyEnabled.asStateFlow()

    private val _clipboardProtectionEnabled = MutableStateFlow(prefs.getBoolean(KEY_CLIPBOARD_PROTECTION, true))
    val clipboardProtectionEnabled: StateFlow<Boolean> = _clipboardProtectionEnabled.asStateFlow()

    private val _clipboardTimeoutSeconds = MutableStateFlow(prefs.getInt(KEY_CLIPBOARD_TIMEOUT_SEC, 60))
    val clipboardTimeoutSeconds: StateFlow<Int> = _clipboardTimeoutSeconds.asStateFlow()

    private val _notificationPrivacyLevel = MutableStateFlow(
        try {
            NotificationPrivacyLevel.valueOf(prefs.getString(KEY_NOTIFICATION_PRIVACY, NotificationPrivacyLevel.MINIMAL.name) ?: NotificationPrivacyLevel.MINIMAL.name)
        } catch (_: Exception) {
            NotificationPrivacyLevel.MINIMAL
        }
    )
    val notificationPrivacyLevel: StateFlow<NotificationPrivacyLevel> = _notificationPrivacyLevel.asStateFlow()

    private val _autoLockTimeout = MutableStateFlow(loadAutoLockTimeout())
    val autoLockTimeout: StateFlow<AutoLockTimeout> = _autoLockTimeout.asStateFlow()

    private val _lastUnlockTimestamp = MutableStateFlow(prefs.getLong(KEY_LAST_UNLOCK_TIMESTAMP, 0L))
    val lastUnlockTimestamp: StateFlow<Long> = _lastUnlockTimestamp.asStateFlow()

    private var backgroundTimestamp: Long = 0L
    private var lockoutJob: Job? = null

    init {
        checkAndResumeActiveLockout()
    }

    private fun determineInitialLockState(): LockState {
        if (!hasMasterPin()) return LockState.SetupRequired
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, 0L)
        val remainingMs = lockoutUntil - System.currentTimeMillis()
        if (remainingMs > 0) {
            val remainingSec = max(1, ceil(remainingMs / 1000.0).toInt())
            return LockState.Lockout(remainingSec)
        }
        return LockState.Locked
    }

    private fun loadAutoLockTimeout(): AutoLockTimeout {
        val ms = prefs.getLong(KEY_AUTO_LOCK_MILLIS, AutoLockTimeout.ONE_MINUTE.millis)
        return AutoLockTimeout.entries.find { it.millis == ms } ?: AutoLockTimeout.ONE_MINUTE
    }

    private fun checkAndResumeActiveLockout() {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, 0L)
        val remainingMs = lockoutUntil - System.currentTimeMillis()
        if (remainingMs > 0) {
            val remainingSec = max(1, ceil(remainingMs / 1000.0).toInt())
            startLockoutCountdown(remainingSec)
        }
    }

    fun hasMasterPin(): Boolean {
        return prefs.contains(KEY_PIN_HASH) && prefs.contains(KEY_PIN_SALT)
    }

    fun getMasterPinLength(): Int {
        return prefs.getInt(KEY_PIN_LENGTH, 4)
    }

    fun isBiometricEnabled(): Boolean {
        return _biometricEnabled.value
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
        _biometricEnabled.value = enabled
    }

    fun isFaceUnlockEnabled(): Boolean {
        return _faceUnlockEnabled.value
    }

    fun setFaceUnlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FACE_UNLOCK_ENABLED, enabled).apply()
        _faceUnlockEnabled.value = enabled
    }

    fun unlockViaFace(): Boolean {
        if (!isFaceUnlockEnabled()) {
            setFaceUnlockEnabled(true)
        }
        return unlockViaBiometrics()
    }

    fun isVoiceLockEnabled(): Boolean {
        return _voiceLockEnabled.value
    }

    fun setVoiceLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_LOCK_ENABLED, enabled).apply()
        _voiceLockEnabled.value = enabled
    }

    fun getVoicePassphrase(): String {
        return _voicePassphrase.value
    }

    fun setVoicePassphrase(phrase: String) {
        val sanitized = phrase.trim()
        val toSave = if (sanitized.isEmpty()) "Open Private Vault" else sanitized
        prefs.edit().putString(KEY_VOICE_PASSPHRASE, toSave).apply()
        _voicePassphrase.value = toSave
    }

    fun isVoiceAntiSpoofingEnabled(): Boolean {
        return _voiceAntiSpoofingEnabled.value
    }

    fun setVoiceAntiSpoofingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_ANTI_SPOOFING, enabled).apply()
        _voiceAntiSpoofingEnabled.value = enabled
    }

    fun getVoiceConfidenceThreshold(): Float {
        return _voiceConfidenceThreshold.value
    }

    fun setVoiceConfidenceThreshold(threshold: Float) {
        val clamped = threshold.coerceIn(0.70f, 0.99f)
        prefs.edit().putFloat(KEY_VOICE_THRESHOLD, clamped).apply()
        _voiceConfidenceThreshold.value = clamped
    }

    fun unlockViaVoice(): Boolean {
        if (!isVoiceLockEnabled()) {
            setVoiceLockEnabled(true)
        }
        return unlockViaBiometrics()
    }

    fun unlockViaVoiceBiometrics(): Boolean = unlockViaVoice()

    fun isScreenProtectionEnabled(): Boolean {
        return _screenProtectionEnabled.value
    }

    fun setScreenProtectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCREEN_PROTECTION, enabled).apply()
        _screenProtectionEnabled.value = enabled
    }

    fun isRecentAppPrivacyEnabled(): Boolean {
        return _recentAppPrivacyEnabled.value
    }

    fun setRecentAppPrivacyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RECENT_APP_PRIVACY, enabled).apply()
        _recentAppPrivacyEnabled.value = enabled
    }

    fun isClipboardProtectionEnabled(): Boolean {
        return _clipboardProtectionEnabled.value
    }

    fun setClipboardProtectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLIPBOARD_PROTECTION, enabled).apply()
        _clipboardProtectionEnabled.value = enabled
    }

    fun getClipboardTimeoutSeconds(): Int {
        return _clipboardTimeoutSeconds.value
    }

    fun setClipboardTimeoutSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_CLIPBOARD_TIMEOUT_SEC, seconds).apply()
        _clipboardTimeoutSeconds.value = seconds
    }

    fun getNotificationPrivacyLevel(): NotificationPrivacyLevel {
        return _notificationPrivacyLevel.value
    }

    fun setNotificationPrivacyLevel(level: NotificationPrivacyLevel) {
        prefs.edit().putString(KEY_NOTIFICATION_PRIVACY, level.name).apply()
        _notificationPrivacyLevel.value = level
    }

    fun getAutoLockTimeout(): AutoLockTimeout {
        return _autoLockTimeout.value
    }

    fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        prefs.edit().putLong(KEY_AUTO_LOCK_MILLIS, timeout.millis).apply()
        _autoLockTimeout.value = timeout
    }

    fun getLastUnlockTimestamp(): Long {
        return _lastUnlockTimestamp.value
    }

    fun getLastIntegrityCheckTimestamp(): Long {
        return prefs.getLong(KEY_LAST_INTEGRITY_CHECK, 0L)
    }

    fun getLastIntegritySummary(): String? {
        return prefs.getString(KEY_LAST_INTEGRITY_SUMMARY, null)
    }

    fun recordIntegrityCheck(timestamp: Long, summary: String) {
        prefs.edit()
            .putLong(KEY_LAST_INTEGRITY_CHECK, timestamp)
            .putString(KEY_LAST_INTEGRITY_SUMMARY, summary)
            .apply()
    }

    /**
     * Initializes the user's master PIN (4 to 8 digits supported).
     */
    fun setupMasterPin(pin: String): Boolean {
        if (pin.length < 4 || pin.length > 8 || !pin.all { it.isDigit() }) return false
        val salt = secureKeyManager.generateSalt()
        val hash = secureKeyManager.derivePinHash(pin.toCharArray(), salt)
        val now = System.currentTimeMillis()

        prefs.edit()
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putInt(KEY_PIN_LENGTH, pin.length)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, 0L)
            .putInt(KEY_LOCKOUT_COUNT, 0)
            .putLong(KEY_LAST_UNLOCK_TIMESTAMP, now)
            .apply()

        _failedAttempts.value = 0
        _lastUnlockTimestamp.value = now
        _lockState.value = LockState.Unlocked
        return true
    }

    /**
     * Safely and atomically updates the master PIN after verifying current PIN.
     */
    fun changeMasterPin(currentPin: String, newPin: String): Result<Unit> {
        if (!hasMasterPin()) {
            return Result.failure(IllegalStateException("No master PIN configured"))
        }
        if (_lockState.value is LockState.Lockout) {
            return Result.failure(IllegalStateException("Vault is currently locked out"))
        }

        val storedHashBase64 = prefs.getString(KEY_PIN_HASH, null)
            ?: return Result.failure(IllegalStateException("Master PIN hash unavailable"))
        val storedSaltBase64 = prefs.getString(KEY_PIN_SALT, null)
            ?: return Result.failure(IllegalStateException("Master PIN salt unavailable"))

        val storedHash = Base64.decode(storedHashBase64, Base64.NO_WRAP)
        val storedSalt = Base64.decode(storedSaltBase64, Base64.NO_WRAP)
        val computedHash = secureKeyManager.derivePinHash(currentPin.toCharArray(), storedSalt)

        val matches = Arrays.equals(storedHash, computedHash)
        if (!matches) {
            val nextAttempts = _failedAttempts.value + 1
            _failedAttempts.value = nextAttempts
            prefs.edit().putInt(KEY_FAILED_ATTEMPTS, nextAttempts).apply()
            if (nextAttempts >= MAX_FAILED_ATTEMPTS) {
                triggerProgressiveLockout()
            }
            return Result.failure(IllegalArgumentException("Current PIN is incorrect"))
        }

        if (newPin.length < 4 || newPin.length > 8 || !newPin.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("New PIN must be between 4 and 8 digits"))
        }

        val newSalt = secureKeyManager.generateSalt()
        val newHash = secureKeyManager.derivePinHash(newPin.toCharArray(), newSalt)

        prefs.edit()
            .putString(KEY_PIN_HASH, Base64.encodeToString(newHash, Base64.NO_WRAP))
            .putString(KEY_PIN_SALT, Base64.encodeToString(newSalt, Base64.NO_WRAP))
            .putInt(KEY_PIN_LENGTH, newPin.length)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .apply()

        _failedAttempts.value = 0
        return Result.success(Unit)
    }

    /**
     * Authenticates the user with their master PIN.
     * Enforces rate limiting and exponential backoff lockout.
     */
    fun authenticatePin(enteredPin: String): Boolean {
        if (_lockState.value is LockState.Lockout) return false

        val storedHashBase64 = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val storedSaltBase64 = prefs.getString(KEY_PIN_SALT, null) ?: return false

        val storedHash = Base64.decode(storedHashBase64, Base64.NO_WRAP)
        val storedSalt = Base64.decode(storedSaltBase64, Base64.NO_WRAP)

        val computedHash = secureKeyManager.derivePinHash(enteredPin.toCharArray(), storedSalt)

        val matches = Arrays.equals(storedHash, computedHash)
        if (matches) {
            val now = System.currentTimeMillis()
            _failedAttempts.value = 0
            _lastUnlockTimestamp.value = now
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putInt(KEY_LOCKOUT_COUNT, 0)
                .putLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, 0L)
                .putLong(KEY_LAST_UNLOCK_TIMESTAMP, now)
                .apply()
            _lockState.value = LockState.Unlocked
            return true
        } else {
            val nextAttempts = _failedAttempts.value + 1
            _failedAttempts.value = nextAttempts
            prefs.edit().putInt(KEY_FAILED_ATTEMPTS, nextAttempts).apply()

            if (nextAttempts >= MAX_FAILED_ATTEMPTS) {
                triggerProgressiveLockout()
            }
            return false
        }
    }

    /**
     * Unlocks the vault directly via validated biometric authentication.
     */
    fun unlockViaBiometrics(): Boolean {
        if (_lockState.value is LockState.Lockout) return false
        if (!hasMasterPin()) return false

        if (!isBiometricEnabled()) {
            setBiometricEnabled(true)
        }

        val now = System.currentTimeMillis()
        _failedAttempts.value = 0
        _lastUnlockTimestamp.value = now
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LAST_UNLOCK_TIMESTAMP, now)
            .apply()
        _lockState.value = LockState.Unlocked
        return true
    }

    /**
     * Explicitly locks the vault.
     */
    fun lockVault() {
        if (hasMasterPin()) {
            _lockState.value = LockState.Locked
        } else {
            _lockState.value = LockState.SetupRequired
        }
    }

    /**
     * Emergency lockdown: Immediately locks authentication session and invalidates active session.
     * Does NOT delete or corrupt stored vault files.
     */
    fun emergencyLock() {
        lockVault()
    }

    /**
     * Progressive lockout calculation:
     * 1st lockout: 30 seconds
     * 2nd lockout: 60 seconds
     * 3rd+ lockout: 120 seconds
     */
    private fun triggerProgressiveLockout() {
        val currentCount = prefs.getInt(KEY_LOCKOUT_COUNT, 0)
        val nextCount = currentCount + 1
        val durationSeconds = when (currentCount) {
            0 -> BASE_LOCKOUT_SECONDS
            1 -> BASE_LOCKOUT_SECONDS * 2
            else -> BASE_LOCKOUT_SECONDS * 4
        }

        val lockoutUntil = System.currentTimeMillis() + (durationSeconds * 1000L)
        prefs.edit()
            .putLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, lockoutUntil)
            .putInt(KEY_LOCKOUT_COUNT, nextCount)
            .apply()

        startLockoutCountdown(durationSeconds)
    }

    private fun startLockoutCountdown(initialSeconds: Int) {
        lockoutJob?.cancel()
        lockoutJob = coroutineScope.launch {
            for (sec in initialSeconds downTo 1) {
                _lockState.value = LockState.Lockout(sec)
                delay(1000L)
            }
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, 0L)
                .apply()
            _failedAttempts.value = 0
            _lockState.value = LockState.Locked
        }
    }

    /**
     * Emergency Wipe / Factory Reset of Security Layer.
     */
    fun emergencyWipeAllSecuritySettings() {
        lockoutJob?.cancel()
        prefs.edit().clear().apply()
        _failedAttempts.value = 0
        backgroundTimestamp = 0L
        _lockState.value = LockState.SetupRequired
    }

    fun onAppBackgrounded() {
        backgroundTimestamp = System.currentTimeMillis()
    }

    fun onAppForegrounded() {
        // If in lockout, refresh remaining time
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL_TIMESTAMP, 0L)
        val remainingMs = lockoutUntil - System.currentTimeMillis()
        if (remainingMs > 0) {
            val remainingSec = max(1, ceil(remainingMs / 1000.0).toInt())
            startLockoutCountdown(remainingSec)
            return
        }

        if (_lockState.value != LockState.Unlocked) return

        val timeout = getAutoLockTimeout()
        if (timeout == AutoLockTimeout.IMMEDIATE) {
            lockVault()
        } else if (timeout != AutoLockTimeout.NEVER && timeout.millis > 0 && backgroundTimestamp > 0) {
            val elapsed = System.currentTimeMillis() - backgroundTimestamp
            if (elapsed >= timeout.millis) {
                lockVault()
            }
        }
        backgroundTimestamp = 0L
    }
}
