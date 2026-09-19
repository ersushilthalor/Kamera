package com.example.camera.pipeline.video

import android.graphics.ColorMatrix
import android.hardware.camera2.CaptureRequest
import android.util.Log
import com.example.camera.engine.VideoHdrEngine
import com.example.camera.model.HardwareCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "VideoPipelineEngine"

/**
 * Coordinates the computational HDR video processing pipeline and routes camera frames dynamically
 * through capture → ISP processing → viewfinder → video encoder.
 *
 * Exclusively provides the unified computational DSLR-Style HDR pipeline,
 * eliminating the old legacy simulated pipelines.
 */
class VideoPipelineEngine(
    private var hdrEngine: VideoHdrEngine? = null
) {

    var hdrPipeline = HdrVideoPipeline(hdrEngine)
        private set

    private val _activePipelineType = MutableStateFlow(VideoPipelineType.HDR)
    val activePipelineType: StateFlow<VideoPipelineType> = _activePipelineType.asStateFlow()

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    fun attachHdrEngine(engine: VideoHdrEngine) {
        hdrEngine = engine
        hdrPipeline = HdrVideoPipeline(engine)
    }

    fun getActivePipeline(): VideoPipeline {
        return if (_activePipelineType.value == VideoPipelineType.OFF) {
            // Revert fallback
            hdrPipeline
        } else {
            hdrPipeline
        }
    }

    fun setPipeline(type: VideoPipelineType) {
        Log.i(TAG, "Switching video pipeline to: ${type.displayName}")
        _activePipelineType.value = type
    }

    fun setEnabled(enabled: Boolean) {
        Log.i(TAG, "Video pipeline master enabled: $enabled")
        _isEnabled.value = enabled
    }

    /**
     * Applies the active pipeline's ISP parameters to the repeating video CaptureRequest.Builder.
     * This directly programs the hardware sensor ISP so both the preview surface and
     * the MediaRecorder recording surface receive genuine pipeline-processed frames.
     */
    fun applyToCaptureRequest(
        builder: CaptureRequest.Builder,
        capabilities: HardwareCapabilities,
        currentIso: Int = 100
    ) {
        if (!_isEnabled.value || _activePipelineType.value == VideoPipelineType.OFF) {
            // Revert to standard camera HAL defaults
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_FAST)
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_FAST)
            builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST)
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST)
            return
        }

        val pipeline = getActivePipeline()
        pipeline.applyToCaptureRequest(
            builder = builder,
            supportsContrastCurve = capabilities.supportsTonemapCurve,
            supportsTransform = capabilities.supportsColorTransform,
            supportsHighQualityEdge = capabilities.supportsEdgeMode,
            supportsHighQualityNr = capabilities.supportsNoiseReduction,
            supportsMinimalNr = capabilities.supportsNoiseReduction,
            currentIso = currentIso
        )
    }

    /**
     * Returns optimized target video bitrate for encoding.
     */
    fun getEncoderBitrate(width: Int, height: Int, defaultBitrate: Int): Int {
        if (!_isEnabled.value || _activePipelineType.value == VideoPipelineType.OFF) {
            return defaultBitrate
        }
        return getActivePipeline().getEncoderBitrate(width, height, defaultBitrate)
    }

    /**
     * Returns real-time Viewfinder color matrix to ensure preview matches recorded video.
     */
    fun getPreviewColorMatrix(): ColorMatrix? {
        if (!_isEnabled.value || _activePipelineType.value == VideoPipelineType.OFF) {
            return null
        }
        return getActivePipeline().getPreviewColorMatrix()
    }
}
