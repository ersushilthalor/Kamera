package com.example.camera.pipeline.video

import androidx.compose.ui.graphics.Color
import com.squareup.moshi.JsonClass

/**
 * Dedicated Hardware & Software Video Processing Pipelines for Video Mode.
 *
 * NOT a simple filter or post-processing overlay:
 * When selected, the camera hardware ISP, tonemap curves, color space transforms,
 * edge sharpening, noise reduction, and video encoder profiles are dynamically
 * routed through a completely distinct image processing architecture.
 */
@JsonClass(generateAdapter = false)
enum class VideoPipelineType(
    val id: String,
    val displayName: String,
    val badgeLabel: String,
    val subtitle: String,
    val description: String,
    val accentColor: Color
) {
    IPHONE(
        id = "iphone",
        displayName = "iPhone Pipeline",
        badgeLabel = "iPhone",
        subtitle = "Warm Natural & Smooth Roll-Off",
        description = "Apple-inspired processing: subtle ambient warmth, natural skin tone protection, low-to-moderate contrast with wide dynamic range, smooth highlight roll-off, controlled sharpening without digital halos, and natural tonal transitions.",
        accentColor = Color(0xFFFFCA28)
    ),

    DSLR(
        id = "dslr",
        displayName = "DSLR Pipeline",
        badgeLabel = "DSLR",
        subtitle = "Neutral Studio & Photographic Depth",
        description = "Natural camera/DSLR-inspired processing: strict neutral studio color science, deep inky shadows with fine textural detail, filmic logarithmic-linear knee, zero artificial edge haloing, and organic optical depth.",
        accentColor = Color(0xFFEF5350)
    ),

    SAMSUNG(
        id = "samsung",
        displayName = "Samsung Pipeline",
        badgeLabel = "Samsung",
        subtitle = "Dynamic HDR & Vivid Clarity",
        description = "Modern Samsung computational video: multi-band HDR dynamic range compression, punchy S-curve contrast, rich and vivid foliage and sky color separation, deep shadow recovery, and crisp micro-edge definition.",
        accentColor = Color(0xFF2979FF)
    ),

    OFF(
        id = "off",
        displayName = "Standard Android",
        badgeLabel = "Standard",
        subtitle = "Default Sensor ISP",
        description = "Default standard Android camera HAL video pipeline without specialized ISP routing.",
        accentColor = Color(0xFF9E9E9E)
    );

    companion object {
        val SELECTABLE_PIPELINES = listOf(IPHONE, DSLR, SAMSUNG)

        fun fromId(id: String): VideoPipelineType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: IPHONE
        }
    }
}

/**
 * Architectural breakdown characteristics for UI transparency and technical inspection.
 */
data class VideoPipelineCharacteristics(
    val toneMapping: String,
    val highlightShadow: String,
    val dynamicRange: String,
    val contrastCurve: String,
    val colorScience: String,
    val whiteBalance: String,
    val sharpeningDetail: String,
    val noiseReduction: String,
    val localContrast: String,
    val saturationResponse: String,
    val encodingOutput: String
)
