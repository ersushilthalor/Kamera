package com.example.camera.engine.optical

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.camera.model.PortraitConfig
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Master Optical Blur Guided Portrait Processing Pipeline.
 *
 * Full real processing pipeline:
 * Captured full-resolution image
 *   → optical defocus estimation
 *   → depth estimation
 *   → high-resolution foreground alpha matte with dedicated hair refinement pass
 *   → optical/depth fusion
 *   → depth-dependent variable-radius bokeh
 *   → hair-aware alpha compositing
 *   → final full-resolution JPEG bitmap
 */
class OpticalBlurGuidedPipeline(private val context: Context) {

    companion object {
        private const val TAG = "OpticalBlurGuided"
    }

    private val defocusEstimator = OpticalDefocusEstimator()
    private val fusionEngine = OpticalDepthFusionEngine()
    private val bokehRenderer = OpticalBokehRenderer()

    // Single persistent ML Kit selfie segmenter instance (reused between shots for hardware acceleration)
    private val segmenter: Segmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()
        Segmentation.getClient(options)
    }

    /**
     * Executes the complete Optical Blur Guided Portrait processing pipeline on the captured full-resolution bitmap.
     */
    suspend fun processOpticalGuidedPortrait(
        fullResBitmap: Bitmap,
        config: PortraitConfig,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = fullResBitmap.width
        val height = fullResBitmap.height

        var decontaminatedBg: Bitmap? = null
        var variableBokehBg: Bitmap? = null
        var finalPortraitBmp: Bitmap? = null

        try {
            onProgress(0.10f, "Estimating physical lens optical defocus...")

            // Stage 1: Optical Defocus Estimation
            // Estimates spatial distribution of real optical blur already produced by physical lens
            // Generates continuous floating-point defocus map and confidence map
            val defocusResult = defocusEstimator.estimateOpticalDefocus(
                bitmap = fullResBitmap,
                analysisScale = if (max(width, height) > 2400) (2400f / max(width, height)) else 1.0f
            )

            // Upsample defocus maps if analysis was performed on a scaled grid
            val defocusMap = if (defocusResult.width == width && defocusResult.height == height) {
                defocusResult.defocusMap
            } else {
                upsampleBilinear(defocusResult.defocusMap, defocusResult.width, defocusResult.height, width, height)
            }

            val confidenceMap = if (defocusResult.width == width && defocusResult.height == height) {
                defocusResult.confidenceMap
            } else {
                upsampleBilinear(defocusResult.confidenceMap, defocusResult.width, defocusResult.height, width, height)
            }

            onProgress(0.25f, "Segmenting subject contours & estimating depth...")

            // Stage 2: Hardware-accelerated Subject Segmentation (ML Kit)
            val mlScale = (1280f / max(width, height)).coerceAtMost(1.0f)
            val targetMlW = (width * mlScale).toInt().coerceAtLeast(1)
            val targetMlH = (height * mlScale).toInt().coerceAtLeast(1)

            val inputForMl = if (mlScale < 1.0f) {
                Bitmap.createScaledBitmap(fullResBitmap, targetMlW, targetMlH, true)
            } else {
                fullResBitmap
            }

            val inputImage = InputImage.fromBitmap(inputForMl, 0)
            val maskResult = try {
                withContext(Dispatchers.IO) {
                    val task = segmenter.process(inputImage)
                    Tasks.await(task)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "ML Kit segmentation fallback to center depth prior", t)
                null
            } finally {
                if (inputForMl != fullResBitmap && !inputForMl.isRecycled) {
                    inputForMl.recycle()
                }
            }

            val initialMask: FloatArray
            if (maskResult != null) {
                val maskW = maskResult.width
                val maskH = maskResult.height
                val buffer = maskResult.buffer
                buffer.rewind()

                val raw = FloatArray(maskW * maskH)
                for (i in raw.indices) {
                    raw[i] = buffer.float.coerceIn(0f, 1f)
                }
                initialMask = if (maskW == width && maskH == height) {
                    raw
                } else {
                    upsampleBilinear(raw, maskW, maskH, width, height)
                }
            } else {
                // Graceful anatomical fallback prior
                initialMask = FloatArray(width * height) { idx ->
                    val x = idx % width
                    val y = idx / width
                    val dx = (x - width * 0.5f) / (width * 0.42f)
                    val dy = (y - height * 0.52f) / (height * 0.45f)
                    (1f - (dx * dx + dy * dy - 0.25f) / 0.75f).coerceIn(0f, 1f)
                }
            }

            onProgress(0.40f, "Estimating dense depth field...")

            // Stage 3: Continuous Depth Estimation
            val depthMap = fusionEngine.estimateContinuousDepthMap(
                initialMask = initialMask,
                lumaGuide = defocusMap,
                width = width,
                height = height
            )

            onProgress(0.55f, "Refining fine hair strands & background gaps...")

            // Stage 4: High-Resolution Foreground Alpha Matting (Dedicated Hair Strand Pass)
            val alphaMatte = fusionEngine.computeHighResolutionHairMatte(
                sourceBitmap = fullResBitmap,
                initialAlpha = initialMask,
                width = width,
                height = height
            )

            onProgress(0.70f, "Fusing optical defocus with depth bokeh...")

            // Stage 5: Optical & Depth Fusion
            // Combines optical defocus, depth map, foreground segmentation, and alpha matte
            // Computes synthetic blur: R_synthetic = sqrt(max(0, R_target^2 - R_existing^2))
            val fusionResult = fusionEngine.fuseOpticalAndDepth(
                defocusMap = defocusMap,
                confidenceMap = confidenceMap,
                depthMap = depthMap,
                alphaMatte = alphaMatte,
                width = width,
                height = height,
                simulatedAperture = config.simulatedAperture,
                blurStrength = config.blurStrength
            )

            onProgress(0.80f, "Rendering depth-dependent ${config.bokehStyle.label} bokeh...")

            // Stage 6: Anti-Halo Background Edge Decontamination
            decontaminatedBg = fusionEngine.decontaminateBackgroundBeforeBlur(
                source = fullResBitmap,
                alphaMask = alphaMatte,
                width = width,
                height = height
            )

            // Stage 7: Depth-Dependent Variable-Radius Bokeh Rendering
            // Preserves existing optical defocus and synthesizes required aperture blur
            variableBokehBg = bokehRenderer.renderVariableRadiusBokeh(
                source = decontaminatedBg,
                syntheticRadii = fusionResult.syntheticBlurRadius,
                depthMap = fusionResult.depthMap,
                maxRadius = fusionResult.maxTargetRadius,
                bokehStyle = config.bokehStyle
            )

            onProgress(0.90f, "Compositing razor-sharp hair & subject...")

            // Stage 8: Precision Hair-Aware Subject Compositing
            finalPortraitBmp = bokehRenderer.compositeSharpSubjectWithHairMatte(
                original = fullResBitmap,
                blurredBackground = variableBokehBg,
                alphaMatte = alphaMatte,
                skinToneCorrection = config.skinToneCorrection,
                faceEnhancement = config.faceEnhancement,
                style = config.selectedStyle
            )

            finalPortraitBmp
        } finally {
            try {
                if (decontaminatedBg != null && !decontaminatedBg.isRecycled) {
                    decontaminatedBg.recycle()
                }
                if (variableBokehBg != null && !variableBokehBg.isRecycled) {
                    variableBokehBg.recycle()
                }
            } catch (ignored: Throwable) {}
        }
    }

    private fun upsampleBilinear(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
        val dst = FloatArray(dw * dh)
        val xRatio = (sw - 1).toFloat() / dw.toFloat()
        val yRatio = (sh - 1).toFloat() / dh.toFloat()

        for (y in 0 until dh) {
            val srcY = y * yRatio
            val y1 = srcY.toInt()
            val y2 = (y1 + 1).coerceAtMost(sh - 1)
            val yDiff = srcY - y1

            for (x in 0 until dw) {
                val srcX = x * xRatio
                val x1 = srcX.toInt()
                val x2 = (x1 + 1).coerceAtMost(sw - 1)
                val xDiff = srcX - x1

                val a = src[y1 * sw + x1]
                val b = src[y1 * sw + x2]
                val c = src[y2 * sw + x1]
                val d = src[y2 * sw + x2]

                dst[y * dw + x] = (a * (1 - xDiff) * (1 - yDiff) +
                        b * xDiff * (1 - yDiff) +
                        c * (1 - xDiff) * yDiff +
                        d * xDiff * yDiff).coerceIn(0f, 1f)
            }
        }
        return dst
    }
}
