package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Dead-Man's Switch (Inactivity Self-Destruct Watchdog):
 * Monitors user inactivity. If the vault is abandoned or unaccessed for a designated period
 * (e.g. 7, 14, 30, 60, 90 days), the vault automatically and irreversibly shreds all encrypted
 * partitions, wipes cryptographic keys, and resets to initial factory state.
 */
class DeadManSwitchManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "private_vault_dead_man_switch"
        private const val KEY_ENABLED = "key_dms_enabled"
        private const val KEY_INACTIVITY_DAYS = "key_dms_inactivity_days"
        private const val KEY_LAST_ACTIVE_TIME = "key_dms_last_active_time"

        val SUPPORTED_DAYS = listOf(7, 14, 30, 60, 90)

        @Volatile
        private var INSTANCE: DeadManSwitchManager? = null

        fun getInstance(context: Context): DeadManSwitchManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DeadManSwitchManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _inactivityDays = MutableStateFlow(prefs.getInt(KEY_INACTIVITY_DAYS, 30))
    val inactivityDays: StateFlow<Int> = _inactivityDays.asStateFlow()

    private val _lastActiveTime = MutableStateFlow(
        prefs.getLong(KEY_LAST_ACTIVE_TIME, System.currentTimeMillis())
    )
    val lastActiveTime: StateFlow<Long> = _lastActiveTime.asStateFlow()

    private val _daysRemaining = MutableStateFlow(calculateDaysRemaining())
    val daysRemaining: StateFlow<Int> = _daysRemaining.asStateFlow()

    init {
        refreshState()
    }

    fun isEnabled(): Boolean = _enabled.value

    fun setEnabled(enabled: Boolean) {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putLong(KEY_LAST_ACTIVE_TIME, now)
            .apply()
        _enabled.value = enabled
        _lastActiveTime.value = now
        _daysRemaining.value = _inactivityDays.value
    }

    fun setInactivityDays(days: Int) {
        val validDays = if (days in SUPPORTED_DAYS) days else 30
        prefs.edit().putInt(KEY_INACTIVITY_DAYS, validDays).apply()
        _inactivityDays.value = validDays
        _daysRemaining.value = calculateDaysRemaining()
    }

    /**
     * Resets the inactivity timer (called on vault unlock, app launch, or manual check-in).
     */
    fun recordCheckIn() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_ACTIVE_TIME, now).apply()
        _lastActiveTime.value = now
        _daysRemaining.value = _inactivityDays.value
    }

    fun refreshState() {
        _daysRemaining.value = calculateDaysRemaining()
    }

    private fun calculateDaysRemaining(): Int {
        if (!_enabled.value) return _inactivityDays.value
        val now = System.currentTimeMillis()
        val limitMillis = TimeUnit.DAYS.toMillis(_inactivityDays.value.toLong())
        val elapsed = now - _lastActiveTime.value
        val remainingMillis = limitMillis - elapsed
        return if (remainingMillis <= 0) 0 else max(1, TimeUnit.MILLISECONDS.toDays(remainingMillis).toInt())
    }

    fun getExpiryTimestamp(): Long {
        val limitMillis = TimeUnit.DAYS.toMillis(_inactivityDays.value.toLong())
        return _lastActiveTime.value + limitMillis
    }

    fun getFormattedExpiryDate(): String {
        return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(getExpiryTimestamp()))
    }

    fun getFormattedLastActiveDate(): String {
        return SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(_lastActiveTime.value))
    }

    /**
     * Verifies if the watchdog timer has expired.
     * If expired and armed, invokes the destruction callback.
     */
    fun checkAndExecuteSelfDestruct(onSelfDestruct: suspend () -> Unit): Boolean {
        if (!_enabled.value) return false
        val now = System.currentTimeMillis()
        val limitMillis = TimeUnit.DAYS.toMillis(_inactivityDays.value.toLong())
        val elapsed = now - _lastActiveTime.value

        if (elapsed >= limitMillis) {
            // Self-destruct condition fulfilled! Disarm and trigger wipe
            setEnabled(false)
            return true
        }
        return false
    }
}
