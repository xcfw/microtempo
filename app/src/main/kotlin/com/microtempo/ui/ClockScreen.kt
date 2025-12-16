package com.microtempo.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.microtempo.ClockViewModel
import com.microtempo.PreciseNtpClock
import com.microtempo.PreciseTime
import com.microtempo.SyncInfo
import com.microtempo.WeatherData
import com.microtempo.calibration.DisplayLatencyCompensator

@Composable
fun ClockScreen(
    vm: ClockViewModel = viewModel(),
    compensator: DisplayLatencyCompensator? = null,
    onOpenCalibration: () -> Unit = {}
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // Keep screen on
    DisposableEffect(Unit) {
        val window = (context as Activity).window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLandscape = maxWidth > maxHeight
            val clockSize = min(maxWidth, maxHeight) * 0.85f

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnalogClockWithTick(modifier = Modifier.size(clockSize), compensator = compensator)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DigitalDisplayWithTick(onRefresh = { vm.sync() }, compensator = compensator)
                        Spacer(Modifier.height(8.dp))
                        WeatherDisplay(weather = state.weather, onRefresh = { vm.sync() })
                        Spacer(Modifier.height(8.dp))
                        SyncStatus(state.lastSync)
                        Spacer(Modifier.height(4.dp))
                        DelayIndicator(
                            compensator = compensator,
                            onLongPress = onOpenCalibration
                        )
                        Spacer(Modifier.height(16.dp))
                        ServerToggle(
                            currentServer = state.selectedServer.name,
                            syncing = state.syncing,
                            onToggle = { vm.toggleServer() }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    AnalogClockWithTick(modifier = Modifier.size(clockSize), compensator = compensator)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DigitalDisplayWithTick(onRefresh = { vm.sync() }, compensator = compensator)
                        Spacer(Modifier.height(4.dp))
                        SyncStatus(state.lastSync)
                        Spacer(Modifier.height(4.dp))
                        DelayIndicator(
                            compensator = compensator,
                            onLongPress = onOpenCalibration
                        )
                    }
                    WeatherDisplay(weather = state.weather, onRefresh = { vm.sync() })
                    ServerToggle(
                        currentServer = state.selectedServer.name,
                        syncing = state.syncing,
                        onToggle = { vm.toggleServer() }
                    )
                }
            }
        }

        DebugOverlay(state.logs, Modifier.align(Alignment.BottomStart))
    }
}

/**
 * Moon phase to emoji mapping
 */
private fun moonPhaseToEmoji(phase: String): String {
    return when {
        phase.contains("New", ignoreCase = true) -> "🌑"
        phase.contains("Waxing Crescent", ignoreCase = true) -> "🌒"
        phase.contains("First Quarter", ignoreCase = true) -> "🌓"
        phase.contains("Waxing Gibbous", ignoreCase = true) -> "🌔"
        phase.contains("Full", ignoreCase = true) -> "🌕"
        phase.contains("Waning Gibbous", ignoreCase = true) -> "🌖"
        phase.contains("Last Quarter", ignoreCase = true) || phase.contains("Third Quarter", ignoreCase = true) -> "🌗"
        phase.contains("Waning Crescent", ignoreCase = true) -> "🌘"
        else -> "🌙"
    }
}

@Composable
private fun WeatherDisplay(weather: WeatherData?, onRefresh: () -> Unit) {
    if (weather == null) {
        Text(
            text = "...",
            fontSize = 24.sp,
            color = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.clickable { onRefresh() }
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Temperature - clickable to refresh NTP
            Text(
                text = "${weather.tempC}°C",
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                modifier = Modifier.clickable { onRefresh() }
            )
            // Moon phase emoji - clickable to refresh NTP
            Text(
                text = moonPhaseToEmoji(weather.moonPhase),
                fontSize = 24.sp,
                modifier = Modifier.clickable { onRefresh() }
            )
        }
    }
}

/**
 * Digital display with frame-synced animation.
 * Clickable to trigger NTP refresh.
 * Uses compensated time if compensator is provided.
 */
@Composable
private fun DigitalDisplayWithTick(
    onRefresh: () -> Unit,
    compensator: DisplayLatencyCompensator? = null
) {
    var time by remember { mutableStateOf<PreciseTime?>(null) }
    var frameCount by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            withInfiniteAnimationFrameNanos { frameTimeNanos ->
                frameCount = frameTimeNanos
                time = compensator?.getCompensatedTime() ?: PreciseNtpClock.nowOrNull()
            }
        }
    }

    if (time == null) {
        Text(
            text = "--:--:--.------",
            fontSize = 28.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.Gray,
            letterSpacing = 2.sp,
            modifier = Modifier.clickable { onRefresh() }
        )
    } else {
        Text(
            text = formatTime(time!!),
            fontSize = 28.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            letterSpacing = 2.sp,
            modifier = Modifier.clickable { onRefresh() }
        )
    }
}

private val calendar = java.util.Calendar.getInstance()

private fun formatTime(time: PreciseTime): String {
    calendar.timeInMillis = time.millis
    return String.format(
        java.util.Locale.US,
        "%02d:%02d:%02d.%03d%03d",
        calendar.get(java.util.Calendar.HOUR_OF_DAY),
        calendar.get(java.util.Calendar.MINUTE),
        calendar.get(java.util.Calendar.SECOND),
        (time.millis % 1000).toInt(),
        time.micros
    )
}

/**
 * Analog clock with frame-synced animation for smooth hand movement.
 * Uses compensated time if compensator is provided.
 */
@Composable
private fun AnalogClockWithTick(
    modifier: Modifier = Modifier,
    compensator: DisplayLatencyCompensator? = null
) {
    var time by remember { mutableStateOf<PreciseTime?>(null) }
    var frameCount by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            withInfiniteAnimationFrameNanos { frameTimeNanos ->
                frameCount = frameTimeNanos
                time = compensator?.getCompensatedTime() ?: PreciseNtpClock.nowOrNull()
            }
        }
    }

    time?.let {
        AnalogClock(time = it, modifier = modifier)
    } ?: Box(modifier = modifier)
}

@Composable
private fun SyncStatus(syncInfo: SyncInfo?) {
    val text = syncInfo?.let {
        String.format(
            java.util.Locale.US,
            "RTT: %.1fms",
            it.rttMs
        )
    } ?: "Not synced"
    Text(
        text = text,
        fontSize = 12.sp,
        color = Color.White.copy(alpha = 0.5f)
    )
}

/**
 * Server toggle - click to switch between Cloudflare and Google
 */
@Composable
private fun ServerToggle(
    currentServer: String,
    syncing: Boolean,
    onToggle: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(enabled = !syncing) { onToggle() }
            .padding(8.dp)
    ) {
        if (syncing) {
            CircularProgressIndicator(
                Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = Color.White.copy(alpha = 0.5f)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = currentServer,
            fontSize = 14.sp,
            color = if (syncing) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.7f)
        )
    }
}

/**
 * Display latency indicator - shows current compensation delay.
 * Short tap: shows tooltip hint
 * Long press: opens calibration screen
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DelayIndicator(
    compensator: DisplayLatencyCompensator?,
    onLongPress: () -> Unit
) {
    var showHint by remember { mutableStateOf(false) }

    val delayText = compensator?.let {
        String.format(java.util.Locale.US, "Device lag: %.1fms", it.delayMs)
    } ?: "Device lag: --"

    val calibratedText = if (compensator?.isCalibrated == true) {
        String.format(java.util.Locale.US, " (±%.2fms)", compensator.precisionMs)
    } else {
        " (est.)"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = delayText + calibratedText,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.4f),
            modifier = Modifier
                .combinedClickable(
                    onClick = { showHint = !showHint },
                    onLongClick = onLongPress
                )
                .padding(4.dp)
        )
        if (showHint) {
            Text(
                text = "Hold to calibrate",
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun DebugOverlay(logs: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(8.dp)) {
        logs.takeLast(3).forEach { log ->
            Text(
                text = log,
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.25f),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
