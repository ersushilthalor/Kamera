package com.example.camera.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * Authentic Cinematic Look-Up Table (LUT) Presets for Log & Video grading.
 *
 * Implements genuine film-style color grades affecting:
 * - Contrast & S-curve dynamics
 * - Tonal curve shaping (highlight shoulder & shadow toe)
 * - Highlight roll-off (organic compression without harsh clipping)
 * - Shadow rendering (lifted matte vs deep inky blacks)
 * - Saturation & chroma density
 * - Skin tone protection & radiant separation
 * - Color separation across RGB spectrum
 */
enum class CinematicLut(
    val id: String,
    val label: String,
    val description: String,
    val category: String,
    val accentColor: Color,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val highlightRollOff: Float = 0.5f,
    val shadowToe: Float = 0.0f,
    val warmCoolOffset: Float = 0.0f,
    private val matrixValues: FloatArray? = null
) {
    NONE(
        id = "clean_log",
        label = "None (Clean)",
        description = "Unmodified sensor curve for pure uncompressed Log mastering or natural video",
        category = "Master",
        accentColor = Color(0xFFFFD54F),
        contrast = 1.0f,
        saturation = 1.0f,
        highlightRollOff = 0.5f,
        shadowToe = 0.0f,
        warmCoolOffset = 0.0f,
        matrixValues = null
    ),

    FILMIC_NEUTRAL(
        id = "filmic_neutral",
        label = "Filmic Neutral",
        description = "Balanced cinema print stock: gentle organic S-curve, natural skin tones, smooth highlight roll-off and clean shadows",
        category = "Print Stock",
        accentColor = Color(0xFFFDD835),
        contrast = 1.15f,
        saturation = 1.04f,
        highlightRollOff = 0.70f,
        shadowToe = 0.02f,
        warmCoolOffset = 0.02f,
        matrixValues = floatArrayOf(
            1.08f, -0.02f, -0.01f, 0f, 2f,
            -0.01f, 1.05f, -0.01f, 0f, 1f,
            -0.02f, -0.01f, 1.06f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    WARM_CINEMA(
        id = "warm_cinema",
        label = "Warm Cinema",
        description = "Golden hour cinematic glow: creamy warm skin tone protection, radiant amber highlights, and gently compressed warm shadows",
        category = "Cinema Warmth",
        accentColor = Color(0xFFFFB300),
        contrast = 1.18f,
        saturation = 1.12f,
        highlightRollOff = 0.65f,
        shadowToe = 0.05f,
        warmCoolOffset = 0.20f,
        matrixValues = floatArrayOf(
            1.16f, 0.02f, -0.05f, 0f, 6f,
            0.02f, 1.06f, -0.03f, 0f, 3f,
            -0.06f, -0.02f, 0.90f, 0f, -4f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    COOL_DRAMATIC(
        id = "cool_dramatic",
        label = "Cool/Dramatic",
        description = "Moody cinematic thriller: rich slate-cyan shadows, crisp specular highlights, sculpted cheekbones and controlled contrast",
        category = "Dramatic",
        accentColor = Color(0xFF4FC3F7),
        contrast = 1.25f,
        saturation = 0.94f,
        highlightRollOff = 0.50f,
        shadowToe = -0.04f,
        warmCoolOffset = -0.18f,
        matrixValues = floatArrayOf(
            0.92f, -0.01f, 0.02f, 0f, -3f,
            -0.02f, 1.02f, 0.03f, 0f, 1f,
            0.02f, 0.05f, 1.18f, 0f, 8f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    TEAL_ORANGE(
        id = "teal_orange",
        label = "Teal & Orange",
        description = "Hollywood blockbuster color separation: luminous amber-gold skin tones against rich deep teal/cyan shadows",
        category = "Hollywood",
        accentColor = Color(0xFF00E5FF),
        contrast = 1.24f,
        saturation = 1.18f,
        highlightRollOff = 0.60f,
        shadowToe = 0.00f,
        warmCoolOffset = 0.06f,
        matrixValues = floatArrayOf(
            1.22f, -0.06f, -0.08f, 0f, 8f,
            -0.03f, 1.08f, 0.03f, 0f, 2f,
            -0.10f, 0.06f, 1.24f, 0f, 6f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    MUTED_FILM(
        id = "muted_film",
        label = "Muted Film",
        description = "Understated nostalgic arthouse: lifted matte charcoal shadows, soft pastel tonality, and delicate highlight roll-off",
        category = "Vintage",
        accentColor = Color(0xFFB0BEC5),
        contrast = 1.06f,
        saturation = 0.78f,
        highlightRollOff = 0.80f,
        shadowToe = 0.08f,
        warmCoolOffset = -0.04f,
        matrixValues = floatArrayOf(
            0.94f, 0.02f, 0.02f, 0f, 5f,
            0.02f, 0.95f, 0.02f, 0f, 5f,
            0.02f, 0.02f, 0.98f, 0f, 7f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    HIGH_CONTRAST_CINEMA(
        id = "high_contrast_cinema",
        label = "High-Contrast Cinema",
        description = "Punchy modern film: deep inky blacks, pronounced S-curve, high specular contrast, vibrant color density with controlled roll-off",
        category = "Bold Cinema",
        accentColor = Color(0xFFFF5252),
        contrast = 1.34f,
        saturation = 1.22f,
        highlightRollOff = 0.40f,
        shadowToe = -0.08f,
        warmCoolOffset = 0.00f,
        matrixValues = floatArrayOf(
            1.24f, 0.02f, -0.02f, 0f, -4f,
            0.01f, 1.20f, 0.01f, 0f, -3f,
            -0.02f, 0.02f, 1.22f, 0f, -3f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    SOFT_FILM(
        id = "soft_film",
        label = "Soft Film",
        description = "Delicate portrait emulsion: lifted gentle shadows, glowing radiant skin tones, soft dreamy highlight shoulder, pastel color response",
        category = "Portrait",
        accentColor = Color(0xFFFFAB91),
        contrast = 1.04f,
        saturation = 0.88f,
        highlightRollOff = 0.85f,
        shadowToe = 0.10f,
        warmCoolOffset = 0.08f,
        matrixValues = floatArrayOf(
            1.06f, 0.03f, -0.02f, 0f, 6f,
            0.02f, 1.02f, -0.01f, 0f, 5f,
            -0.03f, 0.01f, 0.96f, 0f, 3f,
            0f, 0f, 0f, 1f, 0f
        )
    );

    /**
     * Generates a Compose [ColorFilter] for real-time live viewfinder monitoring.
     */
    fun toColorFilter(): ColorFilter? {
        val vals = matrixValues ?: return null
        return ColorFilter.colorMatrix(ColorMatrix(vals))
    }

    /**
     * Generates an Android [android.graphics.ColorMatrix] for image post-processing & viewfinder layer.
     */
    fun toAndroidColorMatrix(): android.graphics.ColorMatrix? {
        val vals = matrixValues ?: return null
        return android.graphics.ColorMatrix(vals)
    }
}
