package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Monitors device motion hardware (accelerometer) to provide instantaneous panic-lock triggers:
 * 1. Flip-to-Lock: Turning the phone face-down locks the vault instantly.
 * 2. Shake-to-Lock: Rapid physical shaking initiates immediate emergency lockdown.
 */
class PanicSensorManager(
    private val context: Context,
    private val onPanicLockTriggered: () -> Unit
) : SensorEventListener {

    companion object {
        private const val PREFS_NAME = "private_vault_panic_sensors"
        private const val KEY_FLIP_TO_LOCK = "key_flip_to_lock"
        private const val KEY_SHAKE_TO_LOCK = "key_shake_to_lock"

        private const val SHAKE_THRESHOLD_ACCEL = 22.0f
        private const val FLIP_Z_THRESHOLD = -7.5f
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _flipToLockEnabled = MutableStateFlow(prefs.getBoolean(KEY_FLIP_TO_LOCK, true))
    val flipToLockEnabled: StateFlow<Boolean> = _flipToLockEnabled.asStateFlow()

    private val _shakeToLockEnabled = MutableStateFlow(prefs.getBoolean(KEY_SHAKE_TO_LOCK, true))
    val shakeToLockEnabled: StateFlow<Boolean> = _shakeToLockEnabled.asStateFlow()

    private var lastShakeTimestamp: Long = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var isListening = false

    fun setFlipToLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLIP_TO_LOCK, enabled).apply()
        _flipToLockEnabled.value = enabled
    }

    fun setShakeToLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHAKE_TO_LOCK, enabled).apply()
        _shakeToLockEnabled.value = enabled
    }

    fun startListening() {
        if (isListening || accelerometer == null) return
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        isListening = true
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val now = System.currentTimeMillis()

        // 1. Flip-to-lock detection (face down)
        if (_flipToLockEnabled.value) {
            // When phone is placed screen-down, Z acceleration is negative (~ -9.8 m/s²)
            if (z < FLIP_Z_THRESHOLD && (now - lastShakeTimestamp > 1200L)) {
                lastShakeTimestamp = now
                triggerPanicLock("FLIP_FACE_DOWN")
                return
            }
        }

        // 2. Shake-to-lock detection
        if (_shakeToLockEnabled.value) {
            val deltaX = x - lastX
            val deltaY = y - lastY
            val deltaZ = z - lastZ

            val deltaAccel = sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble()).toFloat()

            if (deltaAccel > SHAKE_THRESHOLD_ACCEL && (now - lastShakeTimestamp > 1500L)) {
                lastShakeTimestamp = now
                triggerPanicLock("SHAKE_ACCELERATION")
            }
        }

        lastX = x
        lastY = y
        lastZ = z
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    private fun triggerPanicLock(reason: String) {
        vibratePanic()
        onPanicLockTriggered()
    }

    private fun vibratePanic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 150), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 100, 80, 150), -1)
            }
        } catch (_: Exception) {}
    }
}
