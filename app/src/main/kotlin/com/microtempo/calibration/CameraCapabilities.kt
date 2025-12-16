package com.microtempo.calibration

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Range
import android.util.Size

data class CameraCapabilities(
    val cameraId: String,
    val maxFps: Int,
    val videoSize: Size,
    val isHighSpeedSupported: Boolean,
    val highSpeedFpsRanges: List<Range<Int>> = emptyList()
)

object CameraCapabilityDetector {

    fun detect(context: Context): CameraCapabilities? {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Prefer front camera for selfie-based calibration
        val cameraId = findFrontCamera(cameraManager) ?: cameraManager.cameraIdList.firstOrNull()
            ?: return null

        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return null

        // Check high-speed video capabilities (240fps+)
        val highSpeedRanges = getHighSpeedFpsRanges(configMap)
        val highSpeedSizes = getHighSpeedVideoSizes(configMap)

        return if (highSpeedRanges.isNotEmpty() && highSpeedSizes.isNotEmpty()) {
            // High-speed video supported
            val bestRange = highSpeedRanges.maxByOrNull { it.upper } ?: highSpeedRanges.first()
            val bestSize = selectOptimalSize(highSpeedSizes, bestRange, configMap)
            CameraCapabilities(
                cameraId = cameraId,
                maxFps = bestRange.upper,
                videoSize = bestSize,
                isHighSpeedSupported = true,
                highSpeedFpsRanges = highSpeedRanges
            )
        } else {
            // Fall back to standard video recording
            val standardFps = getMaxStandardFps(configMap)
            val standardSizes = configMap.getOutputSizes(android.media.MediaRecorder::class.java)?.toList()
                ?: emptyList()
            val bestSize = standardSizes.minByOrNull { it.width * it.height }
                ?: Size(640, 480)
            CameraCapabilities(
                cameraId = cameraId,
                maxFps = standardFps,
                videoSize = bestSize,
                isHighSpeedSupported = false
            )
        }
    }

    private fun findFrontCamera(cameraManager: CameraManager): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        }
    }

    private fun getHighSpeedFpsRanges(configMap: StreamConfigurationMap): List<Range<Int>> {
        return try {
            configMap.highSpeedVideoFpsRanges?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getHighSpeedVideoSizes(configMap: StreamConfigurationMap): List<Size> {
        return try {
            configMap.highSpeedVideoSizes?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun selectOptimalSize(
        sizes: List<Size>,
        fpsRange: Range<Int>,
        configMap: StreamConfigurationMap
    ): Size {
        // For calibration, prefer smaller sizes for higher FPS and lower processing overhead
        val sizesForFps = sizes.filter { size ->
            try {
                val ranges = configMap.getHighSpeedVideoFpsRangesFor(size)
                ranges?.any { it.upper >= fpsRange.upper } == true
            } catch (e: Exception) {
                false
            }
        }

        // Select smallest size that supports the target FPS
        return sizesForFps.minByOrNull { it.width * it.height }
            ?: sizes.minByOrNull { it.width * it.height }
            ?: Size(640, 480)
    }

    private fun getMaxStandardFps(configMap: StreamConfigurationMap): Int {
        // Standard video usually maxes at 30 or 60 fps
        val outputMinFrameDuration = try {
            val sizes = configMap.getOutputSizes(android.media.MediaRecorder::class.java)
            sizes?.minOfOrNull { size ->
                configMap.getOutputMinFrameDuration(android.media.MediaRecorder::class.java, size)
            } ?: 33_333_333L // Default to 30fps
        } catch (e: Exception) {
            33_333_333L
        }

        // Convert nanoseconds per frame to FPS
        return if (outputMinFrameDuration > 0) {
            (1_000_000_000L / outputMinFrameDuration).toInt().coerceIn(15, 120)
        } else {
            30
        }
    }
}
