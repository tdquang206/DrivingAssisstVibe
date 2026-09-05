package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.location.LocationTracker
import com.example.location.PlayServicesLocationTracker
import com.example.overlay.OverlayController
import com.example.tts.VoiceAlertManager
import com.example.camera.CameraManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.vision.VehicleDetectionState
import com.example.vision.YoloVehicleDetector
import com.example.vision.debug.DetectorStats

class DashcamForegroundService : Service(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private lateinit var locationTracker: LocationTracker
    private lateinit var voiceAlertManager: VoiceAlertManager
    private lateinit var overlayController: OverlayController
    private lateinit var cameraManager: CameraManager
    private var vehicleDetector: YoloVehicleDetector? = null

    private val isAnalyzing = AtomicBoolean(false)
    private var lastInferenceTimestamp = 0L
    private val targetInferenceFps = 5.0f
    private val minInferenceIntervalMs = (1000f / targetInferenceFps).toLong()
    private var skippedFramesCount = 0

    private var totalInferenceTime = 0L
    private var inferenceCount = 0L
    private val recentInferenceTimestamps = ArrayDeque<Long>()

    companion object {
        private const val TAG = "DashcamService"
        private val _isDriving = MutableStateFlow(false)
        val isDriving: StateFlow<Boolean> = _isDriving.asStateFlow()

        private val _detectionState = MutableStateFlow(VehicleDetectionState())
        val detectionState: StateFlow<VehicleDetectionState> = _detectionState.asStateFlow()

        const val ACTION_START_DRIVE = "com.example.action.START_DRIVE"
        const val ACTION_STOP_DRIVE = "com.example.action.STOP_DRIVE"
        const val ACTION_TEST_TTS_EN = "com.example.action.TEST_TTS_EN"
        const val ACTION_TEST_TTS_VI = "com.example.action.TEST_TTS_VI"
        
        // Expose a way for the debug UI to attach a preview surface to the running service's camera
        var activeService: DashcamForegroundService? = null
            private set
        
        fun setSurfaceProvider(provider: androidx.camera.core.Preview.SurfaceProvider?) {
            activeService?.cameraManager?.setSurfaceProvider(provider)
        }

        private const val CHANNEL_ID = "DashcamServiceChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        activeService = this
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        createNotificationChannel()
        
        locationTracker = PlayServicesLocationTracker(this)
        voiceAlertManager = VoiceAlertManager(this)
        overlayController = OverlayController(this, locationTracker.currentSpeedKmh)
        cameraManager = CameraManager(this)
        vehicleDetector = YoloVehicleDetector(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        when (intent?.action) {
            ACTION_START_DRIVE -> startDrive()
            ACTION_STOP_DRIVE -> stopDrive()
            ACTION_TEST_TTS_EN -> {
                voiceAlertManager.setLanguage(Locale.US)
                voiceAlertManager.speak("Too close. Increase following distance.")
            }
            ACTION_TEST_TTS_VI -> {
                voiceAlertManager.setLanguage(Locale("vi", "VN"))
                voiceAlertManager.speak("Khoảng cách quá gần. Hãy giữ khoảng cách.")
            }
        }
        return START_NOT_STICKY
    }

    private fun startDrive() {
        if (_isDriving.value) return

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dashcam Active")
            .setContentText("Drive mode is running in the background.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        
        locationTracker.startTracking()
        overlayController.start()

        val analyzer = ImageAnalysis.Analyzer { imageProxy: ImageProxy ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastInferenceTimestamp < minInferenceIntervalMs || !isAnalyzing.compareAndSet(false, true)) {
                skippedFramesCount++
                imageProxy.close()
                return@Analyzer
            }

            try {
                val detector = vehicleDetector
                if (detector == null) {
                    imageProxy.close()
                    isAnalyzing.set(false)
                    return@Analyzer
                }

                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val bitmap = imageProxy.toBitmap()
                lastInferenceTimestamp = now

                val frame = detector.detect(bitmap, rotationDegrees)

                val completedNow = SystemClock.elapsedRealtime()
                recentInferenceTimestamps.addLast(completedNow)
                while (recentInferenceTimestamps.isNotEmpty() && completedNow - recentInferenceTimestamps.first() > 2000L) {
                    recentInferenceTimestamps.removeFirst()
                }
                val currentFps = if (recentInferenceTimestamps.size > 1) {
                    val windowDurationSec = (completedNow - recentInferenceTimestamps.first()) / 1000f
                    if (windowDurationSec > 0f) recentInferenceTimestamps.size / windowDurationSec else 0f
                } else {
                    0f
                }

                inferenceCount++
                totalInferenceTime += frame.inferenceTimeMs
                val avgInferenceMs = if (inferenceCount > 0) totalInferenceTime / inferenceCount else 0L

                val stats = DetectorStats(
                    fps = Math.round(currentFps * 10f) / 10f,
                    latestInferenceTimeMs = frame.inferenceTimeMs,
                    avgInferenceTimeMs = avgInferenceMs,
                    preprocessTimeMs = detector.latestPreprocessTimeMs,
                    postprocessTimeMs = detector.latestPostprocessTimeMs,
                    totalPipelineTimeMs = detector.latestPreprocessTimeMs + frame.inferenceTimeMs + detector.latestPostprocessTimeMs,
                    vehicleCount = frame.vehicles.size,
                    leadVehicleType = "NONE",
                    leadScore = 0f,
                    modelResolution = "640x640",
                    skippedFrames = skippedFramesCount,
                    maxVehicleConf = frame.diagnostics.maxVehicleConf,
                    rawCandidatesCount = frame.diagnostics.rawCount,
                    aboveConfCount = frame.diagnostics.aboveConfCount,
                    validBoxCount = frame.diagnostics.validBoxCount,
                    afterNmsCount = frame.diagnostics.afterNmsCount,
                    minConfidence = (detector as? YoloVehicleDetector)?.minConfidence ?: 0.40f
                )

                _detectionState.value = VehicleDetectionState(
                    vehicles = frame.vehicles,
                    leadVehicle = null,
                    stats = stats
                )
            } catch (e: Exception) {
                Log.e(TAG, "Image analysis error", e)
            } finally {
                imageProxy.close()
                isAnalyzing.set(false)
            }
        }

        cameraManager.startCamera(this, analyzer)
        _isDriving.value = true
    }

    private fun stopDrive() {
        _isDriving.value = false
        locationTracker.stopTracking()
        overlayController.stop()
        cameraManager.stopCamera()
        _detectionState.value = VehicleDetectionState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        _isDriving.value = false
        activeService = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
        locationTracker.stopTracking()
        overlayController.stop()
        voiceAlertManager.shutdown()
        cameraManager.stopCamera()
        vehicleDetector?.close()
        vehicleDetector = null
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Dashcam Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
