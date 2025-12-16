package com.microtempo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.microtempo.PreciseNtpClock
import com.microtempo.calibration.AutoCalibrator
import com.microtempo.calibration.CalibrationParams
import com.microtempo.calibration.CalibrationResult
import com.microtempo.calibration.CalibrationState
import com.microtempo.calibration.CalibrationTier
import com.microtempo.calibration.CameraCapabilities
import com.microtempo.calibration.CameraCapabilityDetector
import com.microtempo.calibration.DisplayLatencyCompensator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CalibrationScreen(
    compensator: DisplayLatencyCompensator,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    var capabilities by remember { mutableStateOf<CameraCapabilities?>(null) }
    var calibrator by remember { mutableStateOf<AutoCalibrator?>(null) }
    var isCalibrating by remember { mutableStateOf(false) }
    var isFlashing by remember { mutableStateOf(false) }
    var flashState by remember { mutableStateOf(false) }
    var calibrationResult by remember { mutableStateOf<CalibrationResult?>(null) }

    // Detect camera capabilities on launch
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            capabilities = CameraCapabilityDetector.detect(context)
            capabilities?.let { caps ->
                val params = CalibrationParams.forFps(caps.maxFps)
                calibrator = AutoCalibrator(caps, params)
            }
        }
    }

    // Flash effect during calibration
    LaunchedEffect(isFlashing) {
        if (!isFlashing) return@LaunchedEffect

        val caps = capabilities ?: return@LaunchedEffect
        val params = CalibrationParams.forFps(caps.maxFps)
        val cal = calibrator ?: return@LaunchedEffect

        while (isFlashing) {
            // Record flash timestamp BEFORE setting flash state
            val flashTimestamp = SystemClock.elapsedRealtimeNanos()
            cal.recordFlashTimestamp(flashTimestamp)

            flashState = true
            delay(params.flashDurationMs.toLong())
            flashState = false
            delay((params.flashPeriodMs - params.flashDurationMs).toLong())
        }
    }

    // Monitor calibration state
    val calibrationState by calibrator?.state?.collectAsState() ?: remember {
        mutableStateOf(CalibrationState.Idle)
    }

    LaunchedEffect(calibrationState) {
        when (val state = calibrationState) {
            is CalibrationState.Completed -> {
                isFlashing = false
                isCalibrating = false
                calibrationResult = state.result
            }
            is CalibrationState.Error -> {
                isFlashing = false
                isCalibrating = false
            }
            else -> {}
        }
    }

    // Animated background color for flash
    val backgroundColor by animateColorAsState(
        targetValue = if (flashState) Color.White else Color(0xFF0A0A0A),
        label = "flash"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        when {
            !hasCameraPermission -> {
                PermissionRequest(
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onCancel = onCancel
                )
            }
            capabilities == null -> {
                LoadingState()
            }
            calibrationResult != null -> {
                ResultScreen(
                    result = calibrationResult!!,
                    onAccept = {
                        compensator.setCalibrationResult(calibrationResult!!)
                        onComplete()
                    },
                    onRetry = {
                        calibrationResult = null
                    },
                    onCancel = onCancel
                )
            }
            isCalibrating -> {
                CalibrationInProgress(
                    state = calibrationState,
                    capabilities = capabilities!!,
                    flashState = flashState
                )
            }
            else -> {
                CalibrationSetup(
                    capabilities = capabilities!!,
                    currentDelayMs = compensator.delayMs,
                    onStartCalibration = {
                        scope.launch {
                            val cameraManager = context.getSystemService(CameraManager::class.java)
                            isCalibrating = true
                            isFlashing = true
                            calibrator?.startCalibration(cameraManager)

                            // Auto-stop after calibration duration
                            val params = CalibrationParams.forFps(capabilities!!.maxFps)
                            delay(params.calibrationDurationSeconds * 1000L + 1000L)
                            calibrator?.stopCalibration()
                        }
                    },
                    onCancel = onCancel
                )
            }
        }
    }
}

@Composable
private fun PermissionRequest(
    onRequest: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera Permission Required",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Automatic calibration uses the camera to measure display latency by detecting screen flashes.",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRequest) {
            Text("Grant Permission")
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onCancel) {
            Text("Cancel", color = Color.White)
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Detecting camera capabilities...",
                fontSize = 16.sp,
                color = Color.White
            )
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator()
        }
    }
}

@Composable
private fun CalibrationSetup(
    capabilities: CameraCapabilities,
    currentDelayMs: Double,
    onStartCalibration: () -> Unit,
    onCancel: () -> Unit
) {
    val params = CalibrationParams.forFps(capabilities.maxFps)
    val tier = CalibrationTier.forFps(capabilities.maxFps)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Display Latency Calibration",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(Modifier.height(24.dp))

        // Current delay
        Text(
            text = "Current delay: %.2f ms".format(currentDelayMs),
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(32.dp))

        // Camera info card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.1f))
                .padding(16.dp)
        ) {
            Text(
                text = "Camera: ${capabilities.maxFps} fps",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = "Quality: ${tier.label}",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = "Expected precision: ±${String.format("%.2f", params.expectedPrecisionMs)} ms",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = "Duration: ${params.calibrationDurationSeconds} seconds",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Position your phone so the front camera can see a mirror reflection of the screen, or point at another device displaying this app.",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onStartCalibration,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) {
            Text("Start Calibration")
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(onClick = onCancel) {
            Text("Cancel", color = Color.White)
        }
    }
}

@Composable
private fun CalibrationInProgress(
    state: CalibrationState,
    capabilities: CameraCapabilities,
    flashState: Boolean
) {
    val textColor = if (flashState) Color.Black else Color.White

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (state) {
            is CalibrationState.Initializing -> {
                Text(
                    text = "Initializing camera...",
                    fontSize = 18.sp,
                    color = textColor
                )
            }
            is CalibrationState.Recording -> {
                Text(
                    text = "Recording",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(0.7f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${state.samplesCollected} samples collected",
                    fontSize = 14.sp,
                    color = textColor.copy(alpha = 0.7f)
                )

                // Show current time with microseconds
                Spacer(Modifier.height(32.dp))
                CurrentTimeDisplay(textColor)
            }
            is CalibrationState.Analyzing -> {
                Text(
                    text = "Analyzing...",
                    fontSize = 18.sp,
                    color = textColor
                )
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(0.7f),
                )
            }
            else -> {
                Text(
                    text = "Calibrating...",
                    fontSize = 18.sp,
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun CurrentTimeDisplay(textColor: Color) {
    var time by remember { mutableStateOf(PreciseNtpClock.nowOrNull()) }

    LaunchedEffect(Unit) {
        while (true) {
            time = PreciseNtpClock.nowOrNull()
            delay(1)
        }
    }

    time?.let { t ->
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = t.millis
        }
        val timeStr = String.format(
            java.util.Locale.US,
            "%02d:%02d:%02d.%03d%03d",
            calendar.get(java.util.Calendar.HOUR_OF_DAY),
            calendar.get(java.util.Calendar.MINUTE),
            calendar.get(java.util.Calendar.SECOND),
            (t.millis % 1000).toInt(),
            t.micros
        )
        Text(
            text = timeStr,
            fontSize = 20.sp,
            fontFamily = FontFamily.Monospace,
            color = textColor
        )
    }
}

@Composable
private fun ResultScreen(
    result: CalibrationResult,
    onAccept: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Calibration Complete",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(Modifier.height(32.dp))

        // Results card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.1f))
                .padding(16.dp)
        ) {
            ResultRow("Measured delay:", "%.2f ms".format(result.medianDelayMs))
            ResultRow("Mean:", "%.2f ms".format(result.meanDelayMs))
            ResultRow("Std dev:", "%.3f ms".format(result.stdDevMs))
            ResultRow("Precision:", "±%.3f ms".format(result.estimatedPrecisionMs))
            ResultRow("Samples:", "${result.sampleCount} (${result.outlierCount} outliers)")
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onAccept,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) {
            Text("Accept & Apply")
        }

        Spacer(Modifier.height(12.dp))

        Row {
            OutlinedButton(onClick = onRetry) {
                Text("Retry", color = Color.White)
            }
            Spacer(Modifier.width(16.dp))
            OutlinedButton(onClick = onCancel) {
                Text("Cancel", color = Color.White)
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = Color.White
        )
    }
}
