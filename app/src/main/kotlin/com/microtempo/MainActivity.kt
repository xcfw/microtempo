package com.microtempo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.microtempo.calibration.DisplayLatencyCompensator
import com.microtempo.ui.CalibrationScreen
import com.microtempo.ui.ClockScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val compensator = remember { DisplayLatencyCompensator(context) }
            var showCalibration by remember { mutableStateOf(false) }

            MaterialTheme(colorScheme = darkColorScheme()) {
                if (showCalibration) {
                    CalibrationScreen(
                        compensator = compensator,
                        onComplete = { showCalibration = false },
                        onCancel = { showCalibration = false }
                    )
                } else {
                    ClockScreen(
                        compensator = compensator,
                        onOpenCalibration = { showCalibration = true }
                    )
                }
            }
        }
    }
}
