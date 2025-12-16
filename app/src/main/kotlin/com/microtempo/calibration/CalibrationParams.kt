package com.microtempo.calibration

data class CalibrationParams(
    val flashDurationMs: Int,
    val flashPeriodMs: Int,
    val calibrationDurationSeconds: Int,
    val sampleCount: Int,
    val expectedPrecisionMs: Double,
    val minBrightnessThreshold: Float = 0.7f,
    val outlierSigmaThreshold: Double = 3.0
) {
    val frameIntervalMs: Double get() = 1000.0 / sampleCount * calibrationDurationSeconds

    companion object {
        fun forFps(maxFps: Int): CalibrationParams {
            return when {
                maxFps >= 960 -> CalibrationParams(
                    flashDurationMs = 5,
                    flashPeriodMs = 50,
                    calibrationDurationSeconds = 5,
                    sampleCount = 100,
                    expectedPrecisionMs = 0.05
                )
                maxFps >= 240 -> CalibrationParams(
                    flashDurationMs = 15,
                    flashPeriodMs = 33,
                    calibrationDurationSeconds = 15,
                    sampleCount = 450,
                    expectedPrecisionMs = 0.10
                )
                maxFps >= 120 -> CalibrationParams(
                    flashDurationMs = 25,
                    flashPeriodMs = 50,
                    calibrationDurationSeconds = 20,
                    sampleCount = 400,
                    expectedPrecisionMs = 0.20
                )
                else -> CalibrationParams(
                    flashDurationMs = 50,
                    flashPeriodMs = 100,
                    calibrationDurationSeconds = 30,
                    sampleCount = 300,
                    expectedPrecisionMs = 0.50
                )
            }
        }

        fun estimatePrecision(maxFps: Int, sampleCount: Int): Double {
            val frameIntervalMs = 1000.0 / maxFps
            // Statistical precision: (frame_interval / 2) / sqrt(N)
            return (frameIntervalMs / 2.0) / kotlin.math.sqrt(sampleCount.toDouble())
        }
    }
}

enum class CalibrationTier(
    val minFps: Int,
    val label: String,
    val description: String
) {
    ULTRA_HIGH(960, "Ultra High (960fps)", "Professional-grade ~0.05ms precision"),
    HIGH(240, "High (240fps)", "Excellent ~0.1ms precision"),
    MEDIUM(120, "Medium (120fps)", "Good ~0.2ms precision"),
    STANDARD(30, "Standard (30fps)", "Basic ~0.5ms precision");

    companion object {
        fun forFps(fps: Int): CalibrationTier {
            return entries.firstOrNull { fps >= it.minFps } ?: STANDARD
        }
    }
}
