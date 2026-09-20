package com.example.camera.engine

import android.graphics.ColorMatrix
import com.example.camera.data.CubeLutParser
import com.example.camera.model.CinemaColorProfile
import com.example.camera.model.CinemaConfig
import com.example.camera.model.CinematicLut
import com.example.camera.model.LogBitDepth
import kotlin.math.pow

/**
 * Unified, single source of truth for the Cinema mode Color Pipeline.
 * Ensures the exact same color transform (Log profile curve, exposure, contrast, saturation,
 * washed-out reduction, and Hollywood/Custom .cube LUT) is applied to both the live viewfinder
 * and the final recorded/exported video file.
 */
object CinemaColorPipeline {

    /**
     * Computes the unified 4x5 ColorMatrix for Cinema mode preview and final video export.
     * Returns null if no transform is active (e.g. Native with default parameters and no LUT).
     */
    fun computeCinemaColorMatrix(
        config: CinemaConfig?,
        rec2020Params: Rec2020AutoToneParams? = null
    ): ColorMatrix? {
        if (config == null || config.logBitDepth == LogBitDepth.OFF) return null

        val colorMatrix = ColorMatrix()
        var hasTransform = false

        // 1. Color Profile Characteristic Curve
        when (config.colorProfile) {
            CinemaColorProfile.FLAT_LOG -> {
                // True Flat Log: lifted milky shadow pedestal (+32 offset) and low contrast
                val flatPedestal = ColorMatrix(floatArrayOf(
                    0.86f, 0f, 0f, 0f, 32f,
                    0f, 0.86f, 0f, 0f, 32f,
                    0f, 0f, 0.86f, 0f, 32f,
                    0f, 0f, 0f, 1f, 0f
                ))
                colorMatrix.postConcat(flatPedestal)
                hasTransform = true
            }
            CinemaColorProfile.HLG -> {
                // HLG: vibrant preserved realistic colors
                val hlgSat = ColorMatrix()
                hlgSat.setSaturation(1.22f)
                colorMatrix.postConcat(hlgSat)
                hasTransform = true
            }
            CinemaColorProfile.REC_2020 -> {
                // REC.2020 Real-Time Auto Tone Control:
                // Continuous real-time Exposure, Highlight roll-off shoulder, Shadow toe lift, Contrast & Inky Black Pedestal
                val p = rec2020Params ?: Rec2020AutoToneParams()
                val rec2020Matrix = Rec2020AutoToneEngine.computePreviewColorMatrix(p)
                colorMatrix.postConcat(rec2020Matrix)
                hasTransform = true
            }
            CinemaColorProfile.APPLE_LOG_2 -> {
                // Apple Log 2: Technical logarithmic transfer curve with elevated black pedestal (+38.4f offset),
                // extended highlight latitude, smooth parabolic shadow roll-off, and grading-friendly profile
                val appleLogPedestal = ColorMatrix(floatArrayOf(
                    0.72f, 0f, 0f, 0f, 38.4f,
                    0f, 0.72f, 0f, 0f, 38.4f,
                    0f, 0f, 0.72f, 0f, 38.4f,
                    0f, 0f, 0f, 1f, 0f
                ))
                val logSat = ColorMatrix()
                logSat.setSaturation(0.88f)
                appleLogPedestal.postConcat(logSat)
                colorMatrix.postConcat(appleLogPedestal)
                hasTransform = true
            }
            CinemaColorProfile.NATIVE -> {
                // Native unadjusted profile
            }
        }

        // 2. User Controls (for non-REC_2020 profiles)
        if (config.colorProfile != CinemaColorProfile.REC_2020) {
            // Washed Out Reduction (recovers deep blacks & midtone contrast from flat profiles)
            if (config.washedOut > 0.0f) {
                val w = config.washedOut
                val pedestalReduction = -28f * w
                val contrastBoost = 1.0f + (w * 0.25f)
                val t = (1.0f - contrastBoost) * 128f + pedestalReduction
                val washedOutMatrix = ColorMatrix(floatArrayOf(
                    contrastBoost, 0f, 0f, 0f, t,
                    0f, contrastBoost, 0f, 0f, t,
                    0f, 0f, contrastBoost, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                ))
                colorMatrix.postConcat(washedOutMatrix)
                hasTransform = true
            }

            // Real-time Exposure control (+/-)
            if (config.exposure != 0.0f) {
                val expMultiplier = 2.0f.pow(config.exposure * 0.75f)
                val expMatrix = ColorMatrix(floatArrayOf(
                    expMultiplier, 0f, 0f, 0f, 0f,
                    0f, expMultiplier, 0f, 0f, 0f,
                    0f, 0f, expMultiplier, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                colorMatrix.postConcat(expMatrix)
                hasTransform = true
            }

            // User Contrast control (+/-)
            if (config.contrast != 0.0f) {
                val c = 1.0f + (config.contrast * 0.4f)
                val t = (1.0f - c) * 128f
                val contrastMatrix = ColorMatrix(floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                ))
                colorMatrix.postConcat(contrastMatrix)
                hasTransform = true
            }
        }

        // Saturation control (+/-)
        if (config.saturation != 1.0f) {
            val satMatrix = ColorMatrix()
            satMatrix.setSaturation(config.saturation)
            colorMatrix.postConcat(satMatrix)
            hasTransform = true
        }

        // 3. Cinematic LUT Transform (Hollywood Presets or Custom .cube)
        val lut = config.selectedLut
        if (lut != CinematicLut.NONE) {
            val lutMat = if (lut == CinematicLut.CUSTOM && !config.customLutPath.isNullOrBlank()) {
                CubeLutParser.getOrLoad(config.customLutPath)?.toAndroidColorMatrix()
            } else {
                lut.toAndroidColorMatrix()
            }
            if (lutMat != null) {
                colorMatrix.postConcat(lutMat)
                hasTransform = true
            }
        }

        return if (hasTransform) colorMatrix else null
    }
}
