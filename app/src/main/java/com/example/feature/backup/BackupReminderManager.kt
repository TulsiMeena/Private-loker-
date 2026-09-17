package com.example.feature.backup

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages local, user-controlled backup reminder preferences and status checks.
 *
 * Requirements:
 * - Local-First: Zero cloud upload, zero automatic network transmission.
 * - Explicit user control: Reminders never create or upload backups automatically.
 * - Options: Off, Weekly (7 days), Monthly (30 days).
 */
class BackupReminderManager(
    context: Context
) {
    companion object {
        private const val PREFS_NAME = "private_vault_backup_prefs"
        private const val KEY_REMINDER_SCHEDULE = "backup_reminder_schedule"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _schedule = MutableStateFlow(
        try {
            BackupReminderSchedule.valueOf(
                prefs.getString(KEY_REMINDER_SCHEDULE, BackupReminderSchedule.WEEKLY.name)
                    ?: BackupReminderSchedule.WEEKLY.name
            )
        } catch (_: Exception) {
            BackupReminderSchedule.WEEKLY
        }
    )
    val schedule: StateFlow<BackupReminderSchedule> = _schedule.asStateFlow()

    fun setSchedule(newSchedule: BackupReminderSchedule) {
        prefs.edit().putString(KEY_REMINDER_SCHEDULE, newSchedule.name).apply()
        _schedule.value = newSchedule
    }

    /**
     * Evaluates if a backup reminder is currently due based on the last backup timestamp.
     */
    fun isBackupDue(lastBackupTimestamp: Long?): Boolean {
        val currentSchedule = _schedule.value
        if (currentSchedule == BackupReminderSchedule.OFF) return false
        if (lastBackupTimestamp == null || lastBackupTimestamp <= 0L) return true

        val elapsedMs = System.currentTimeMillis() - lastBackupTimestamp
        val thresholdMs = currentSchedule.intervalDays.toLong() * 24 * 60 * 60 * 1000L
        return elapsedMs > thresholdMs
    }

    /**
     * Returns human-readable status text of the reminder.
     */
    fun getReminderStatusText(lastBackupTimestamp: Long?): String {
        val currentSchedule = _schedule.value
        if (currentSchedule == BackupReminderSchedule.OFF) {
            return "Reminders are turned off"
        }
        if (lastBackupTimestamp == null || lastBackupTimestamp <= 0L) {
            return "No previous backup found. Vault protection recommended."
        }
        val elapsedDays = (System.currentTimeMillis() - lastBackupTimestamp) / (24 * 60 * 60 * 1000L)
        return if (elapsedDays >= currentSchedule.intervalDays) {
            "Backup due: Last backed up $elapsedDays days ago (${currentSchedule.displayName})"
        } else {
            val remainingDays = currentSchedule.intervalDays - elapsedDays
            "Up to date: Next reminder in $remainingDays days"
        }
    }
}
