package com.microtempo.calibration

import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sqrt

data class CalibrationSample(
    val flashTimestampNanos: Long,
    val cameraTimestampNanos: Long,
    val frameBrightness: Float
) {
    val delayNanos: Long get() = cameraTimestampNanos - flashTimestampNanos
    val delayMs: Double get() = delayNanos / 1_000_000.0
}

data class CalibrationResult(
    val medianDelayMs: Double,
    val meanDelayMs: Double,
    val stdDevMs: Double,
    val sampleCount: Int,
    val outlierCount: Int,
    val estimatedPrecisionMs: Double
)

sealed class CalibrationState {
    data object Idle : CalibrationState()
    data object Initializing : CalibrationState()
    data class Recording(val progress: Float, val samplesCollected: Int) : CalibrationState()
    data class Analyzing(val progress: Float) : CalibrationState()
    data class Completed(val result: CalibrationResult) : CalibrationState()
    data class Error(val message: String) : CalibrationState()
}

class AutoCalibrator(
    private val capabilities: CameraCapabilities,
    private val params: CalibrationParams
) {
    private val _state = MutableStateFlow<CalibrationState>(CalibrationState.Idle)
    val state: StateFlow<CalibrationState> = _state

    private val samples = mutableListOf<CalibrationSample>()
    private val flashTimestamps = Channel<Long>(Channel.UNLIMITED)

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var lastBrightness = 0f
    private var isFlashDetected = false
    private var pendingFlashTimestamp: Long? = null

    fun recordFlashTimestamp(timestampNanos: Long) {
        flashTimestamps.trySend(timestampNanos)
    }

    suspend fun startCalibration(cameraManager: CameraManager) {
        _state.value = CalibrationState.Initializing
        samples.clear()

        try {
            startBackgroundThread()
            openCamera(cameraManager)
        } catch (e: Exception) {
            _state.value = CalibrationState.Error("Failed to start calibration: ${e.message}")
        }
    }

    fun stopCalibration() {
        closeCamera()
        stopBackgroundThread()

        if (samples.size >= 10) {
            analyzeResults()
        } else {
            _state.value = CalibrationState.Error("Not enough samples collected (${samples.size})")
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraCalibration").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            // Ignore
        }
    }

    private fun openCamera(cameraManager: CameraManager) {
        val size = capabilities.videoSize
        imageReader = ImageReader.newInstance(
            size.width, size.height,
            ImageFormat.YUV_420_888,
            2
        ).apply {
            setOnImageAvailableListener({ reader ->
                processFrame(reader)
            }, backgroundHandler)
        }

        try {
            cameraManager.openCamera(
                capabilities.cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        createCaptureSession()
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        cameraDevice = null
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        cameraDevice = null
                        _state.value = CalibrationState.Error("Camera error: $error")
                    }
                },
                backgroundHandler
            )
        } catch (e: SecurityException) {
            _state.value = CalibrationState.Error("Camera permission denied")
        }
    }

    private fun createCaptureSession() {
        val camera = cameraDevice ?: return
        val reader = imageReader ?: return

        val surfaces = listOf(reader.surface)

        try {
            camera.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        startCapture(session, surfaces)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        _state.value = CalibrationState.Error("Failed to configure camera session")
                    }
                },
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            _state.value = CalibrationState.Error("Camera access error: ${e.message}")
        }
    }

    private fun startCapture(session: CameraCaptureSession, surfaces: List<Surface>) {
        val camera = cameraDevice ?: return

        try {
            val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                surfaces.forEach { addTarget(it) }
                // Request fastest possible frame rate
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    android.util.Range(capabilities.maxFps, capabilities.maxFps))
            }.build()

            session.setRepeatingRequest(
                captureRequest,
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        // Sensor timestamp is in nanoseconds, same clock as SystemClock.elapsedRealtimeNanos()
                        val sensorTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
                        processCaptureResult(sensorTimestamp)
                    }
                },
                backgroundHandler
            )

            _state.value = CalibrationState.Recording(0f, 0)
        } catch (e: CameraAccessException) {
            _state.value = CalibrationState.Error("Failed to start capture: ${e.message}")
        }
    }

    private fun processFrame(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        try {
            val brightness = calculateBrightness(image)

            // Detect flash transition (dark -> bright)
            val isNowBright = brightness > params.minBrightnessThreshold
            val wasDark = lastBrightness < params.minBrightnessThreshold * 0.5f

            if (isNowBright && wasDark && !isFlashDetected) {
                isFlashDetected = true
                // Try to get the pending flash timestamp
                pendingFlashTimestamp = flashTimestamps.tryReceive().getOrNull()
            } else if (!isNowBright) {
                isFlashDetected = false
            }

            lastBrightness = brightness
        } finally {
            image.close()
        }
    }

    private fun processCaptureResult(sensorTimestamp: Long) {
        val flashTs = pendingFlashTimestamp
        if (flashTs != null && isFlashDetected) {
            val sample = CalibrationSample(
                flashTimestampNanos = flashTs,
                cameraTimestampNanos = sensorTimestamp,
                frameBrightness = lastBrightness
            )
            samples.add(sample)
            pendingFlashTimestamp = null

            val progress = samples.size.toFloat() / params.sampleCount
            _state.value = CalibrationState.Recording(progress.coerceAtMost(1f), samples.size)
        }
    }

    private fun calculateBrightness(image: android.media.Image): Float {
        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer
        val ySize = yBuffer.remaining()

        // Sample every 16th pixel for speed
        var sum = 0L
        var count = 0
        val step = 16

        for (i in 0 until ySize step step) {
            sum += (yBuffer.get(i).toInt() and 0xFF)
            count++
        }

        return if (count > 0) sum.toFloat() / (count * 255f) else 0f
    }

    private fun analyzeResults() {
        _state.value = CalibrationState.Analyzing(0f)

        if (samples.isEmpty()) {
            _state.value = CalibrationState.Error("No samples collected")
            return
        }

        // Calculate delays
        val delays = samples.map { it.delayMs }

        // Median for robustness
        val sortedDelays = delays.sorted()
        val median = sortedDelays[sortedDelays.size / 2]

        // MAD (Median Absolute Deviation) for outlier detection
        val absoluteDeviations = delays.map { abs(it - median) }.sorted()
        val mad = absoluteDeviations[absoluteDeviations.size / 2]
        val madSigma = mad * 1.4826 // Scale factor for normal distribution

        // Remove outliers (> 3 sigma from median)
        val threshold = params.outlierSigmaThreshold * madSigma
        val filteredDelays = delays.filter { abs(it - median) <= threshold }
        val outlierCount = delays.size - filteredDelays.size

        if (filteredDelays.isEmpty()) {
            _state.value = CalibrationState.Error("All samples rejected as outliers")
            return
        }

        // Final statistics
        val finalMedian = filteredDelays.sorted()[filteredDelays.size / 2]
        val mean = filteredDelays.average()
        val variance = filteredDelays.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)

        // Estimated precision: stdDev / sqrt(N)
        val estimatedPrecision = stdDev / sqrt(filteredDelays.size.toDouble())

        _state.value = CalibrationState.Analyzing(1f)

        val result = CalibrationResult(
            medianDelayMs = finalMedian,
            meanDelayMs = mean,
            stdDevMs = stdDev,
            sampleCount = filteredDelays.size,
            outlierCount = outlierCount,
            estimatedPrecisionMs = estimatedPrecision
        )

        _state.value = CalibrationState.Completed(result)
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }
}
