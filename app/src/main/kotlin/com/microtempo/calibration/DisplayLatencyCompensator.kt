package com.microtempo.calibration

import android.content.Context
import android.view.Display
import android.view.WindowManager
import com.microtempo.PreciseNtpClock
import com.microtempo.PreciseTime
import java.util.concurrent.atomic.AtomicLong

class DisplayLatencyCompensator(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "microtempo_calibration"
        private const val KEY_DISPLAY_DELAY_NANOS = "display_delay_nanos"
        private const val KEY_CALIBRATION_TIMESTAMP = "calibration_timestamp"
        private const val KEY_CALIBRATION_PRECISION = "calibration_precision"

        // Default: 35ms (typical Android device)
        private const val DEFAULT_DELAY_NANOS = 35_000_000L

        // Cap to prevent over-correction
        private const val MAX_DELAY_NANOS = 100_000_000L // 100ms
        private const val MIN_DELAY_NANOS = 0L
    }

    private val displayDelayNanos = AtomicLong(loadStoredDelay())
    private var calibrationPrecisionMs: Double = loadStoredPrecision()
    private var lastCalibrationTimestamp: Long = loadCalibrationTimestamp()

    val delayMs: Double get() = displayDelayNanos.get() / 1_000_000.0
    val delayNanos: Long get() = displayDelayNanos.get()
    val precisionMs: Double get() = calibrationPrecisionMs
    val isCalibrated: Boolean get() = lastCalibrationTimestamp > 0

    init {
        // If not calibrated, estimate based on display refresh rate
        if (!isCalibrated) {
            estimateFromDisplayRefreshRate()
        }
    }

    fun getCompensatedTime(): PreciseTime? {
        val raw = PreciseNtpClock.nowOrNull() ?: return null
        val compensatedNanos = raw.toTotalNanos() + displayDelayNanos.get()
        return PreciseTime.fromTotalNanos(compensatedNanos)
    }

    fun setCalibrationResult(result: CalibrationResult) {
        val delayNanos = (result.medianDelayMs * 1_000_000).toLong()
            .coerceIn(MIN_DELAY_NANOS, MAX_DELAY_NANOS)

        displayDelayNanos.set(delayNanos)
        calibrationPrecisionMs = result.estimatedPrecisionMs
        lastCalibrationTimestamp = System.currentTimeMillis()

        saveToPrefs()
    }

    fun setManualDelay(delayMs: Double) {
        val delayNanos = (delayMs * 1_000_000).toLong()
            .coerceIn(MIN_DELAY_NANOS, MAX_DELAY_NANOS)

        displayDelayNanos.set(delayNanos)
        calibrationPrecisionMs = 1.0 // Unknown precision for manual calibration
        lastCalibrationTimestamp = System.currentTimeMillis()

        saveToPrefs()
    }

    fun resetToDefault() {
        displayDelayNanos.set(DEFAULT_DELAY_NANOS)
        calibrationPrecisionMs = 0.0
        lastCalibrationTimestamp = 0

        clearPrefs()
        estimateFromDisplayRefreshRate()
    }

    private fun estimateFromDisplayRefreshRate() {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val refreshRate = display.refreshRate

        // Heuristic: 2-frame latency at detected refresh rate
        // Plus ~10ms for GPU/composition
        val frameTimeMs = 1000f / refreshRate
        val estimatedDelayMs = frameTimeMs * 2 + 10f
        val estimatedDelayNanos = (estimatedDelayMs * 1_000_000).toLong()
            .coerceIn(MIN_DELAY_NANOS, MAX_DELAY_NANOS)

        displayDelayNanos.set(estimatedDelayNanos)
    }

    private fun loadStoredDelay(): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_DISPLAY_DELAY_NANOS, DEFAULT_DELAY_NANOS)
    }

    private fun loadStoredPrecision(): Double {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_CALIBRATION_PRECISION, 0f).toDouble()
    }

    private fun loadCalibrationTimestamp(): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_CALIBRATION_TIMESTAMP, 0)
    }

    private fun saveToPrefs() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DISPLAY_DELAY_NANOS, displayDelayNanos.get())
            .putFloat(KEY_CALIBRATION_PRECISION, calibrationPrecisionMs.toFloat())
            .putLong(KEY_CALIBRATION_TIMESTAMP, lastCalibrationTimestamp)
            .apply()
    }

    private fun clearPrefs() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_DISPLAY_DELAY_NANOS)
            .remove(KEY_CALIBRATION_PRECISION)
            .remove(KEY_CALIBRATION_TIMESTAMP)
            .apply()
    }
}
