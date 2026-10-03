package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class IntruderLogEntry(
    val id: String,
    val timestamp: Long,
    val photoPath: String?,
    val triggerReason: String,
    val attemptCount: Int,
    val formattedTime: String
)

/**
 * Manages stealth break-in detection, capturing an intruder photo on failed PIN/biometric attempts,
 * and storing audit records securely in the app's internal sandbox.
 */
class IntruderDetectionManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "private_vault_intruder_detection"
        private const val KEY_INTRUDER_CAPTURE_ENABLED = "key_intruder_capture_enabled"
        private const val KEY_ATTEMPT_THRESHOLD = "key_attempt_threshold"
        private const val KEY_INTRUDER_LOGS_JSON = "key_intruder_logs_json"

        @Volatile
        private var INSTANCE: IntruderDetectionManager? = null

        fun getInstance(context: Context): IntruderDetectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IntruderDetectionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _captureEnabled = MutableStateFlow(prefs.getBoolean(KEY_INTRUDER_CAPTURE_ENABLED, true))
    val captureEnabled: StateFlow<Boolean> = _captureEnabled.asStateFlow()

    private val _attemptThreshold = MutableStateFlow(prefs.getInt(KEY_ATTEMPT_THRESHOLD, 3))
    val attemptThreshold: StateFlow<Int> = _attemptThreshold.asStateFlow()

    private val _intruderLogs = MutableStateFlow<List<IntruderLogEntry>>(loadLogs())
    val intruderLogs: StateFlow<List<IntruderLogEntry>> = _intruderLogs.asStateFlow()

    private val capturesDir: File by lazy {
        File(context.filesDir, "intruder_captures").apply {
            if (!exists()) mkdirs()
        }
    }

    fun isCaptureEnabled(): Boolean = _captureEnabled.value

    fun setCaptureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INTRUDER_CAPTURE_ENABLED, enabled).apply()
        _captureEnabled.value = enabled
    }

    fun getAttemptThreshold(): Int = _attemptThreshold.value

    fun setAttemptThreshold(threshold: Int) {
        val clamped = threshold.coerceIn(1, 5)
        prefs.edit().putInt(KEY_ATTEMPT_THRESHOLD, clamped).apply()
        _attemptThreshold.value = clamped
    }

    /**
     * Triggered on failed PIN or biometric attempt. If failed count matches or exceeds threshold,
     * secretly takes an intruder photo snapshot.
     */
    fun onFailedAttempt(attemptsCount: Int, reason: String) {
        if (!_captureEnabled.value) return
        if (attemptsCount < _attemptThreshold.value) return

        captureIntruderPhoto(reason, attemptsCount)
    }

    private fun captureIntruderPhoto(reason: String, attemptsCount: Int) {
        val now = System.currentTimeMillis()
        val entryId = UUID.randomUUID().toString()
        val fileName = "intruder_${now}_${entryId.take(8)}.jpg"
        val photoFile = File(capturesDir, fileName)

        val hasCameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasCameraPermission) {
            attemptFrontCameraCapture(photoFile) { success ->
                if (success && photoFile.exists() && photoFile.length() > 0) {
                    recordLog(entryId, now, photoFile.absolutePath, reason, attemptsCount)
                } else {
                    // Fallback to telemetry evidence snapshot if camera sensor timed out or unavailable
                    val generatedFile = createSecurityAlertSnapshot(photoFile, now, attemptsCount, reason)
                    recordLog(entryId, now, generatedFile.absolutePath, reason, attemptsCount)
                }
            }
        } else {
            // Synthesize high-security break-in alert snapshot bitmap
            val generatedFile = createSecurityAlertSnapshot(photoFile, now, attemptsCount, "$reason (No Camera Permission)")
            recordLog(entryId, now, generatedFile.absolutePath, reason, attemptsCount)
        }
    }

    private fun attemptFrontCameraCapture(destinationFile: File, onComplete: (Boolean) -> Unit) {
        val isCompleted = java.util.concurrent.atomic.AtomicBoolean(false)
        val thread = android.os.HandlerThread("IntruderCamThread").apply { start() }
        val handler = Handler(thread.looper)

        fun finish(success: Boolean, camera: CameraDevice? = null, reader: ImageReader? = null) {
            if (isCompleted.compareAndSet(false, true)) {
                try { camera?.close() } catch (_: Throwable) {}
                try { reader?.close() } catch (_: Throwable) {}
                try { thread.quitSafely() } catch (_: Throwable) {}
                Handler(Looper.getMainLooper()).post {
                    onComplete(success)
                }
            }
        }

        // Safety watchdog: max 3.5 seconds
        handler.postDelayed({
            finish(false)
        }, 3500L)

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: run { finish(false); return }

            val cameraIdList = cameraManager.cameraIdList
            if (cameraIdList.isEmpty()) {
                finish(false)
                return
            }

            // Find front camera, or fallback to first available camera
            val targetCameraId = cameraIdList.firstOrNull { id ->
                try {
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
                } catch (_: Throwable) {
                    false
                }
            } ?: cameraIdList[0]

            val characteristics = cameraManager.getCameraCharacteristics(targetCameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSizes = map?.getOutputSizes(android.graphics.ImageFormat.JPEG)

            // Pick closest to 640x480
            val bestSize = jpegSizes?.minByOrNull {
                kotlin.math.abs(it.width - 640) + kotlin.math.abs(it.height - 480)
            } ?: android.util.Size(640, 480)

            val imageReader = ImageReader.newInstance(
                bestSize.width,
                bestSize.height,
                android.graphics.ImageFormat.JPEG,
                2
            )

            var activeCamera: CameraDevice? = null

            imageReader.setOnImageAvailableListener({ reader ->
                val image = try { reader.acquireLatestImage() } catch (_: Throwable) { null }
                if (image != null) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        FileOutputStream(destinationFile).use { it.write(bytes) }
                        finish(true, activeCamera, reader)
                    } catch (_: Throwable) {
                        finish(false, activeCamera, reader)
                    } finally {
                        try { image.close() } catch (_: Throwable) {}
                    }
                } else {
                    finish(false, activeCamera, reader)
                }
            }, handler)

            cameraManager.openCamera(targetCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    activeCamera = camera
                    try {
                        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                        builder.addTarget(imageReader.surface)
                        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

                        camera.createCaptureSession(
                            listOf(imageReader.surface),
                            object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                                    try {
                                        session.capture(builder.build(), null, handler)
                                    } catch (_: Throwable) {
                                        finish(false, camera, imageReader)
                                    }
                                }

                                override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                                    finish(false, camera, imageReader)
                                }
                            },
                            handler
                        )
                    } catch (_: Throwable) {
                        finish(false, camera, imageReader)
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    finish(false, camera, imageReader)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    finish(false, camera, imageReader)
                }
            }, handler)

        } catch (_: Throwable) {
            finish(false)
        }
    }

    private fun createSecurityAlertSnapshot(
        destinationFile: File,
        timestamp: Long,
        attemptsCount: Int,
        reason: String
    ): File {
        val width = 480
        val height = 480
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Dark red/crimson tactical background
        canvas.drawColor(Color.rgb(20, 10, 15))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(239, 68, 68)
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRect(20f, 20f, width - 20f, height - 20f, paint)

        // Radar circle in center
        paint.strokeWidth = 2f
        paint.color = Color.rgb(239, 68, 68)
        canvas.drawCircle(width / 2f, 180f, 80f, paint)
        canvas.drawCircle(width / 2f, 180f, 40f, paint)

        // Text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText("⚠️ INTRUSION DETECTED", width / 2f, 320f, textPaint)

        textPaint.textSize = 18f
        textPaint.color = Color.rgb(200, 200, 200)
        canvas.drawText("Reason: $reason", width / 2f, 360f, textPaint)
        canvas.drawText("Failed Attempts: $attemptsCount", width / 2f, 395f, textPaint)

        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        textPaint.textSize = 15f
        textPaint.color = Color.rgb(150, 150, 150)
        canvas.drawText(timeStr, width / 2f, 435f, textPaint)

        FileOutputStream(destinationFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
        return destinationFile
    }

    private fun recordLog(
        id: String,
        timestamp: Long,
        photoPath: String?,
        reason: String,
        attemptsCount: Int
    ) {
        val formattedTime = SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        val entry = IntruderLogEntry(
            id = id,
            timestamp = timestamp,
            photoPath = photoPath,
            triggerReason = reason,
            attemptCount = attemptsCount,
            formattedTime = formattedTime
        )

        val updated = listOf(entry) + _intruderLogs.value
        _intruderLogs.value = updated
        persistLogs(updated)
    }

    fun deleteLog(id: String) {
        val entry = _intruderLogs.value.find { it.id == id }
        entry?.photoPath?.let { path ->
            try { File(path).delete() } catch (_: Exception) {}
        }
        val updated = _intruderLogs.value.filterNot { it.id == id }
        _intruderLogs.value = updated
        persistLogs(updated)
    }

    fun clearAllLogs() {
        _intruderLogs.value.forEach { entry ->
            entry.photoPath?.let { path ->
                try { File(path).delete() } catch (_: Exception) {}
            }
        }
        _intruderLogs.value = emptyList()
        persistLogs(emptyList())
    }

    private fun persistLogs(logs: List<IntruderLogEntry>) {
        val array = JSONArray()
        logs.forEach { entry ->
            val obj = JSONObject().apply {
                put("id", entry.id)
                put("timestamp", entry.timestamp)
                put("photoPath", entry.photoPath ?: "")
                put("triggerReason", entry.triggerReason)
                put("attemptCount", entry.attemptCount)
                put("formattedTime", entry.formattedTime)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_INTRUDER_LOGS_JSON, array.toString()).apply()
    }

    private fun loadLogs(): List<IntruderLogEntry> {
        val jsonStr = prefs.getString(KEY_INTRUDER_LOGS_JSON, null) ?: return emptyList()
        val list = mutableListOf<IntruderLogEntry>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val path = obj.optString("photoPath", "").takeIf { it.isNotEmpty() }
                list.add(
                    IntruderLogEntry(
                        id = obj.getString("id"),
                        timestamp = obj.getLong("timestamp"),
                        photoPath = path,
                        triggerReason = obj.getString("triggerReason"),
                        attemptCount = obj.getInt("attemptCount"),
                        formattedTime = obj.optString("formattedTime", "")
                    )
                )
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return list
    }
}
