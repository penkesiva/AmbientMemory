package com.ambientmemory.timeline.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Size
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ambientmemory.timeline.AmbientMemoryApp
import com.ambientmemory.timeline.MainActivity
import com.ambientmemory.timeline.R
import com.ambientmemory.timeline.bluetooth.CarBluetoothDetector
import com.ambientmemory.timeline.activity.ActivityTransitionSubscription
import com.ambientmemory.timeline.data.db.RawCaptureEventEntity
import com.ambientmemory.timeline.diagnostics.CaptureEventLog
import com.ambientmemory.timeline.health.HealthConnectBridge
import com.ambientmemory.timeline.work.CaptureProcessing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class MemoryCaptureService : LifecycleService() {
    companion object {
        private const val TAG = "MemoryCaptureService"
        private const val ACTIVITY_RECOGNITION_PERMISSION = "android.permission.ACTIVITY_RECOGNITION"
        const val ACTION_START = "com.ambientmemory.timeline.START"
        const val ACTION_PAUSE = "com.ambientmemory.timeline.PAUSE"
        const val ACTION_RESUME = "com.ambientmemory.timeline.RESUME"
        const val ACTION_STOP = "com.ambientmemory.timeline.STOP"
        private const val CHANNEL_ID = "ambient_capture"
        private const val NOTIFICATION_ID = 42
    }

    private val cameraExecutor by lazy { Executors.newSingleThreadExecutor() }
    private val graph by lazy { (application as AmbientMemoryApp).graph }

    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSessionLifecycle: CameraSessionLifecycleOwner? = null
    private var boundCamera: Camera? = null
    @Volatile private var captureInFlight: Boolean = false
    @Volatile private var sessionBound: Boolean = false
    @Volatile private var cameraReady: Boolean = false
    @Volatile private var analysisFramesSinceBind: Int = 0
    @Volatile private var lastAnalysisFrameElapsedMs: Long = 0L
    private var consecutiveCameraInactiveErrors: Int = 0
    @Volatile private var captureCooldownUntilMs: Long = 0L
    @Volatile private var rebindInProgress: Boolean = false
    @Volatile private var nextCaptureNotBeforeMs: Long = 0L
    private var currentCaptureSessionId: Long? = null
    private var paused: Boolean = false

    private var schedulerJob: Job? = null
    private lateinit var activitySubscription: ActivityTransitionSubscription

    override fun onCreate() {
        super.onCreate()
        activitySubscription = ActivityTransitionSubscription(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_STOP -> handleStop()
        }
        return START_STICKY
    }

    private fun handleStart() {
        lifecycleScope.launch {
            if (currentCaptureSessionId != null) return@launch
            if (!hasRequiredRuntimePermissions()) {
                CaptureEventLog.add("Missing permissions; capture not started")
                stopSelf()
                return@launch
            }
            paused = false
            val existing = graph.repository.getActiveCaptureSessionOrNull()
            val id =
                if (existing != null && existing.state != "STOPPED") {
                    paused = existing.state == "PAUSED"
                    existing.id
                } else {
                    paused = false
                    graph.repository.startCaptureSession()
                }
            currentCaptureSessionId = id
            CaptureEventLog.add("Session started (id=$id)")
            val notification = buildNotification(paused = paused)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (_: SecurityException) {
                CaptureEventLog.add("Start failed (permission denied)")
                stopSelf()
                return@launch
            }
            runCatching { activitySubscription.register() }
                .onSuccess { CaptureEventLog.add("Activity transitions registered") }
                .onFailure { CaptureEventLog.add("Activity transition setup failed") }
            cameraSessionLifecycle?.endSession()
            cameraSessionLifecycle =
                CameraSessionLifecycleOwner().also {
                    it.startSession()
                    CaptureEventLog.add("CameraX lifecycle owner active")
                }
            bindCamera()
            delay(500)
            startScheduler()
        }
    }

    private fun handlePause() {
        paused = true
        CaptureEventLog.add("Session paused")
        currentCaptureSessionId?.let { sid ->
            lifecycleScope.launch { graph.repository.pauseCaptureSession(sid) }
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(paused = true))
    }

    private fun handleResume() {
        paused = false
        CaptureEventLog.add("Session resumed")
        currentCaptureSessionId?.let { sid ->
            lifecycleScope.launch { graph.repository.resumeCaptureSession(sid) }
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(paused = false))
    }

    private fun handleStop() {
        schedulerJob?.cancel()
        schedulerJob = null
        CaptureEventLog.add("Session stopping")
        lifecycleScope.launch {
            val sid =
                currentCaptureSessionId
                    ?: graph.repository.getActiveCaptureSessionOrNull()?.id
            sid?.let { sessionId ->
                graph.repository.stopCaptureSession(sessionId)
                val cap = graph.repository.getCaptureSession(sessionId)
                if (cap != null) {
                    val settings = graph.repository.readSettings()
                    graph.sessionGrouper.regroupCaptureSession(sessionId, cap, settings.sessionGapMinutes * 60_000L)
                }
                CaptureEventLog.add("Session marked stopped (id=$sessionId)")
            }
            runCatching { activitySubscription.unregister() }
            runCatching {
                cameraProvider?.unbindAll()
            }
            sessionBound = false
            cameraReady = false
            boundCamera = null
            imageAnalysis = null
            analysisFramesSinceBind = 0
            lastAnalysisFrameElapsedMs = 0L
            captureInFlight = false
            cameraSessionLifecycle?.endSession()
            cameraSessionLifecycle = null
            currentCaptureSessionId = null
            CaptureEventLog.add("Session stopped")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasRequiredRuntimePermissions(): Boolean {
        if (!hasPermission(android.Manifest.permission.CAMERA)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !hasPermission(ACTIVITY_RECOGNITION_PERMISSION)
        ) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(android.Manifest.permission.POST_NOTIFICATIONS)
        ) {
            return false
        }
        return true
    }

    private fun isCaptureSessionReady(): Boolean {
        val owner = cameraSessionLifecycle ?: return false
        val lifecycleActive =
            owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        val stateOpen = boundCamera?.cameraInfo?.cameraState?.value?.type == CameraState.Type.OPEN
        val captureConfigured = imageCapture?.resolutionInfo != null
        val recentFrames =
            analysisFramesSinceBind >= 2 &&
                (SystemClock.elapsedRealtime() - lastAnalysisFrameElapsedMs) <= 2_000L
        return sessionBound && lifecycleActive && stateOpen && captureConfigured && recentFrames
    }

    private fun bindCamera() {
        if (!hasPermission(android.Manifest.permission.CAMERA)) {
            CaptureEventLog.add("Camera permission missing")
            return
        }
        val owner = cameraSessionLifecycle ?: return
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener(
            {
                if (cameraSessionLifecycle !== owner) return@addListener
                val provider = providerFuture.get()
                cameraProvider = provider
                val capture =
                    ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetResolution(Size(640, 360))
                        .build()
                val analysis =
                    ImageAnalysis.Builder()
                        .setTargetResolution(Size(640, 360))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build().apply {
                            setAnalyzer(cameraExecutor) { image ->
                                analysisFramesSinceBind += 1
                                lastAnalysisFrameElapsedMs = SystemClock.elapsedRealtime()
                                image.close()
                            }
                        }
                imageCapture = capture
                imageAnalysis = analysis
                try {
                    provider.unbindAll()
                    sessionBound = false
                    cameraReady = false
                    analysisFramesSinceBind = 0
                    lastAnalysisFrameElapsedMs = 0L
                    val camera =
                        try {
                            provider.bindToLifecycle(
                                owner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                capture,
                                analysis,
                            )
                        } catch (se: SecurityException) {
                            CaptureEventLog.add("Camera bind denied")
                            Log.w(TAG, "Camera bind denied by permission state", se)
                            return@addListener
                        }
                    boundCamera = camera
                    sessionBound = true
                    // Give camera2 graph and use case attachment time to settle on some devices.
                    nextCaptureNotBeforeMs = SystemClock.elapsedRealtime() + 3_000L
                    camera.cameraInfo.cameraState.observe(owner) { state ->
                        val readyNow = state.type == CameraState.Type.OPEN
                        if (readyNow != cameraReady) {
                            cameraReady = readyNow
                            if (readyNow) {
                                CaptureEventLog.add("Camera ready")
                            } else {
                                CaptureEventLog.add("Camera not ready (${state.type.name.lowercase()})")
                            }
                        }
                    }
                    CaptureEventLog.add("Camera bound")
                } catch (e: Exception) {
                    Log.e(TAG, "bindCamera failed", e)
                    sessionBound = false
                    cameraReady = false
                    boundCamera = null
                    imageAnalysis = null
                    analysisFramesSinceBind = 0
                    lastAnalysisFrameElapsedMs = 0L
                    CaptureEventLog.add("Camera bind failed")
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun startScheduler() {
        schedulerJob?.cancel()
        schedulerJob =
            lifecycleScope.launch {
                while (isActive) {
                    val settings = graph.repository.readSettings()
                    val sessionId = currentCaptureSessionId
                    if (sessionId == null) break
                    if (!settings.captureEnabled || (paused)) {
                        delay(500)
                        continue
                    }
                    if (settings.onlyDuringActiveSessions && sessionId == null) {
                        delay(500)
                        continue
                    }
                    val now = SystemClock.elapsedRealtime()
                    if (now < nextCaptureNotBeforeMs) {
                        delay(300)
                        continue
                    }
                    if (now < captureCooldownUntilMs) {
                        delay(500)
                        continue
                    }
                    captureOnce(sessionId)
                    delay(settings.captureIntervalSeconds * 1000)
                }
            }
    }

    private suspend fun captureOnce(sessionId: Long) {
        if (captureInFlight) {
            CaptureEventLog.add("Capture skipped (in flight)")
            return
        }
        if (!isCaptureSessionReady()) {
            consecutiveCameraInactiveErrors += 1
            if (consecutiveCameraInactiveErrors >= 3) {
                captureCooldownUntilMs = SystemClock.elapsedRealtime() + 5_000L
                val stateType = boundCamera?.cameraInfo?.cameraState?.value?.type
                CaptureEventLog.add(
                    "Capture deferred (camera ${stateType?.name ?: "unknown"}, frames=$analysisFramesSinceBind)",
                )
                triggerCameraRebind()
                consecutiveCameraInactiveErrors = 0
            }
            return
        }
        val capture = imageCapture ?: return
        val outFile = graph.repository.newCaptureFile(sessionId)
        val opts =
            ImageCapture.OutputFileOptions.Builder(outFile)
                .build()
        runCatching {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    captureInFlight = true
                    var submitted = false
                    cont.invokeOnCancellation {
                        if (!submitted) {
                            captureInFlight = false
                        }
                    }
                    try {
                        capture.takePicture(
                            opts,
                            cameraExecutor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    captureInFlight = false
                                    if (cont.isActive) cont.resumeWith(Result.success(Unit))
                                    consecutiveCameraInactiveErrors = 0
                                    captureCooldownUntilMs = 0L
                                    CaptureEventLog.add("Image captured")
                                    lifecycleScope.launch(Dispatchers.Default) {
                                        finalizeCapture(outFile.absolutePath, sessionId)
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    captureInFlight = false
                                    val msg = exception.message.orEmpty()
                                    val causeMsg = exception.cause?.message.orEmpty()
                                    val inactiveLike =
                                        msg.contains("Camera is not active", ignoreCase = true) ||
                                            causeMsg.contains("Camera is not active", ignoreCase = true) ||
                                            exception.imageCaptureError == ImageCapture.ERROR_CAMERA_CLOSED
                                    if (inactiveLike) {
                                        sessionBound = false
                                        consecutiveCameraInactiveErrors += 1
                                        if (consecutiveCameraInactiveErrors >= 3) {
                                            captureCooldownUntilMs = SystemClock.elapsedRealtime() + 5_000L
                                            CaptureEventLog.add("Capture cooling down 5s (camera inactive)")
                                            triggerCameraRebind()
                                            consecutiveCameraInactiveErrors = 0
                                        } else {
                                            CaptureEventLog.add("Capture skipped (camera inactive)")
                                        }
                                        Log.i(TAG, "Capture skipped: camera inactive")
                                    } else {
                                        Log.w(TAG, "Capture request failed, skipping this tick", exception)
                                        CaptureEventLog.add("Capture skipped (${exception.imageCaptureError})")
                                    }
                                    if (cont.isActive) cont.resumeWith(Result.success(Unit))
                                    runCatching { outFile.delete() }
                                }
                            },
                        )
                        submitted = true
                    } catch (t: Throwable) {
                        captureInFlight = false
                        throw t
                    }
                }
            }
        }.onFailure { error ->
            captureInFlight = false
            Log.w(TAG, "Capture loop encountered recoverable error", error)
            runCatching { outFile.delete() }
        }
    }

    private fun triggerCameraRebind() {
        if (rebindInProgress || currentCaptureSessionId == null) return
        rebindInProgress = true
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                CaptureEventLog.add("Rebinding camera")
                runCatching { cameraProvider?.unbindAll() }
                sessionBound = false
                cameraReady = false
                boundCamera = null
                imageCapture = null
                imageAnalysis = null
                analysisFramesSinceBind = 0
                lastAnalysisFrameElapsedMs = 0L
                captureInFlight = false
                delay(300)
                bindCamera()
            } finally {
                rebindInProgress = false
            }
        }
    }

    private suspend fun finalizeCapture(
        absolutePath: String,
        sessionId: Long,
    ) {
        val now = System.currentTimeMillis()
        val activityState = graph.repository.getLastActivityState()
        val settings = graph.repository.readSettings()
        val file = java.io.File(absolutePath)
        if (!file.exists()) return

        val decision =
            graph.dedupeManager.evaluateNewCapture(
                captureSessionId = sessionId,
                newFile = file,
                currentActivityState = activityState,
                nowMillis = now,
                settings = settings,
            )

        val carBtConfigured =
            settings.useCarBluetoothForCommute &&
                settings.carBluetoothDeviceAddress.isNotBlank() &&
                CarBluetoothDetector.hasBluetoothConnectPermission(applicationContext)
        val carBtConnected =
            carBtConfigured &&
                CarBluetoothDetector.isDeviceConnected(
                    applicationContext,
                    settings.carBluetoothDeviceAddress,
                )
        val healthJson =
            withContext(Dispatchers.IO) {
                HealthConnectBridge.readSnapshotJson(applicationContext, now)
            }
        val row =
            RawCaptureEventEntity(
                captureSessionId = sessionId,
                timestampMillis = now,
                imageUri = absolutePath,
                imageHash = decision.imageHash,
                activityState = activityState,
                carBtConnected = carBtConnected,
                carBtConfigured = carBtConfigured,
                acceptedForProcessing = decision.accepted,
                dedupeReason = decision.reason,
                processingStatus =
                    if (decision.accepted) {
                        "queued"
                    } else {
                        "skipped"
                    },
                healthConnectJson = healthJson,
            )
        val rawId = graph.repository.insertRawCapture(row)
        if (decision.accepted) {
            CaptureEventLog.add("Raw queued (id=$rawId)")
            CaptureProcessing.enqueue(
                applicationContext,
                rawId,
                settings.wifiOnlyProcessing,
            )
        } else {
            CaptureEventLog.add("Raw skipped (${decision.reason ?: "dedupe"})")
        }
    }

    private fun buildNotification(paused: Boolean): Notification {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val pauseIntent =
            Intent(this, MemoryCaptureService::class.java).setAction(ACTION_PAUSE)
        val resumeIntent =
            Intent(this, MemoryCaptureService::class.java).setAction(ACTION_RESUME)
        val stopIntent =
            Intent(this, MemoryCaptureService::class.java).setAction(ACTION_STOP)
        val pausePi =
            PendingIntent.getService(
                this,
                1,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val resumePi =
            PendingIntent.getService(
                this,
                2,
                resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val stopPi =
            PendingIntent.getService(
                this,
                3,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val stateText =
            if (paused) {
                getString(R.string.capture_state_paused)
            } else {
                getString(R.string.capture_state_running)
            }

        val builder =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_simple)
                .setContentTitle(getString(R.string.notification_capture_title))
                .setContentText(stateText)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(
                    0,
                    if (paused) getString(R.string.action_resume) else getString(R.string.action_pause),
                    if (paused) resumePi else pausePi,
                )
                .addAction(0, getString(R.string.action_stop), stopPi)

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        val ch =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_capture),
                NotificationManager.IMPORTANCE_LOW,
            )
        nm.createNotificationChannel(ch)
    }

    override fun onDestroy() {
        schedulerJob?.cancel()
        lifecycleScope.launch(Dispatchers.IO) { runCatching { activitySubscription.unregister() } }
        runCatching { cameraProvider?.unbindAll() }
        sessionBound = false
        cameraReady = false
        boundCamera = null
        imageAnalysis = null
        analysisFramesSinceBind = 0
        lastAnalysisFrameElapsedMs = 0L
        captureInFlight = false
        cameraSessionLifecycle?.endSession()
        cameraSessionLifecycle = null
        cameraExecutor.shutdown()
        super.onDestroy()
    }

}
