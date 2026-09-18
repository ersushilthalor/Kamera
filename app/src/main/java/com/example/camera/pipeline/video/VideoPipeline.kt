package com.example.camera.pipeline.video

import android.graphics.ColorMatrix
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.TonemapCurve

/**
 * Base contract for a genuine Video Processing Pipeline.
 *
 * Each concrete pipeline implementation defines its own distinct ISP parameters,
 * tonal transfer curves, chromatic matrices, noise suppression filters, sharpening passes,
 * and hardware encoder bitrate profiles.
 */
interface VideoPipeline {
    val type: VideoPipelineType
    val name: String get() = type.displayName

    /**
     * Computes the 64-point hardware tonemap curve for this pipeline.
     */
    fun getTonemapCurve(numPoints: Int = 64): TonemapCurve

    /**
     * Color space transform rational 3x3 matrix for chromatic tuning.
     */
    fun getColorSpaceTransform(): ColorSpaceTransform

    /**
     * White balance / channel gain vectors for subtle spectral tuning.
     */
    fun getColorGains(): RggbChannelVector

    /**
     * Camera2 hardware Edge Enhancement mode.
     */
    fun getEdgeMode(supportsHighQuality: Boolean): Int

    /**
     * Camera2 hardware Noise Reduction mode adaptive to scene ISO.
     */
    fun getNoiseReductionMode(iso: Int, supportsHighQuality: Boolean, supportsMinimal: Boolean): Int

    /**
     * Optimized video encoder bitrate for this pipeline's detail target.
     */
    fun getEncoderBitrate(width: Int, height: Int, baseBitrate: Int): Int

    /**
     * Real-time Viewfinder color matrix ensuring the live preview
     * matches the recorded output with zero latency.
     */
    fun getPreviewColorMatrix(): ColorMatrix

    /**
     * Detailed technical breakdown of this pipeline's characteristics.
     */
    fun getCharacteristics(): VideoPipelineCharacteristics

    /**
     * Apply all hardware ISP parameters directly to the Camera2 CaptureRequest.Builder.
     */
    fun applyToCaptureRequest(
        builder: CaptureRequest.Builder,
        supportsContrastCurve: Boolean,
        supportsTransform: Boolean,
        supportsHighQualityEdge: Boolean,
        supportsHighQualityNr: Boolean,
        supportsMinimalNr: Boolean,
        currentIso: Int = 100
    )
}
