package com.example.camera.pipeline.video

import androidx.compose.ui.graphics.Color
import com.squareup.moshi.JsonClass

/**
 * Dedicated Hardware & Software Video Processing Pipeline for Video Mode.
 *
 * NOT a simple filter or post-processing overlay:
 * Capture-to-encoder computational video architecture:
 * Dynamic S-curve tone mapping with aggressive highlight roll-off,
 * anti-magenta highlight reconstruction, multi-frame temporal HDR integration,
 * motion-aware temporal noise reduction, and broadcast-grade encoding.
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
    HDR(
        id = "hdr",
        displayName = "HDR Pipeline",
        badgeLabel = "HDR",
        subtitle = "DSLR Computational HDR",
        description = "Computational DSLR-style HDR video pipeline: aggressive highlight protection, anti-magenta recovery, multi-frame temporal denoise, deep inky shadows, and broadcast-grade encoding.",
        accentColor = Color(0xFFFFB300)
    ),

    OFF(
        id = "off",
        displayName = "Standard Android",
        badgeLabel = "Standard",
        subtitle = "Default Sensor ISP",
        description = "Default standard Android camera HAL video pipeline without specialized computational ISP routing.",
        accentColor = Color(0xFF9E9E9E)
    );

    companion object {
        val SELECTABLE_PIPELINES = listOf(HDR)

        fun fromId(id: String): VideoPipelineType {
            return when (id.lowercase()) {
                "off" -> OFF
                else -> HDR // Maps "hdr", "dslr", "iphone", "samsung" seamlessly to HDR
            }
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
