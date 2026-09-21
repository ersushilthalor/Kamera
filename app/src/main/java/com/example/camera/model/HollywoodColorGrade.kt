package com.example.camera.model

import android.graphics.ColorMatrix
import androidx.compose.ui.graphics.Color

/**
 * Professional Hollywood-style Colour Grading Presets.
 * Multi-stage grading:
 * - Exposure offset & tone curve
 * - Contrast S-curve
 * - Highlight roll-off & shoulder recovery
 * - Shadow toe & black pedestal lift
 * - Warm / cool color temperature tuning
 * - Saturation & vibrance tuning
 * - Skin tones preservation & selective color separation (e.g. teal shadows + warm bronze skin)
 */
enum class HollywoodColorGrade(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val description: String,
    val accentColor: Color,
    val contrast: Float,
    val saturation: Float,
    val exposureOffset: Float,
    val shadowToe: Float,
    val highlightRollOff: Float,
    val rawMatrix: FloatArray
) {
    OFF(
        id = "off",
        displayName = "Off",
        subtitle = "Standard Video",
        description = "No Hollywood color grading applied. Natural capture output.",
        accentColor = Color(0xFF9E9E9E),
        contrast = 1.0f,
        saturation = 1.0f,
        exposureOffset = 0.0f,
        shadowToe = 0.0f,
        highlightRollOff = 0.0f,
        rawMatrix = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    CINEMATIC_NEUTRAL(
        id = "cinematic_neutral",
        displayName = "Cinematic Neutral",
        subtitle = "Reference Film Baseline",
        description = "Calibrated film reference baseline, gentle shadow roll-off, and pristine natural skin tones with zero color cast.",
        accentColor = Color(0xFFFFD54F),
        contrast = 1.06f,
        saturation = 1.00f,
        exposureOffset = 0.0f,
        shadowToe = 3.0f,
        highlightRollOff = -1.0f,
        rawMatrix = floatArrayOf(
            1.05f, 0.00f, 0.00f, 0f, 3.0f,
            0.00f, 1.05f, 0.00f, 0f, 3.0f,
            0.00f, 0.00f, 1.05f, 0f, 3.0f,
            0.00f, 0.00f, 0.00f, 1f, 0.0f
        )
    ),

    HOLLYWOOD_FILM(
        id = "hollywood_film",
        displayName = "Hollywood Film",
        subtitle = "Kodak 35mm Motion Picture",
        description = "Authentic 35mm motion picture print stock with rich golden-amber highlights, deep cyan-tinted shadows, and radiant skin tones.",
        accentColor = Color(0xFFFF9800),
        contrast = 1.18f,
        saturation = 1.12f,
        exposureOffset = 0.05f,
        shadowToe = 4.0f,
        highlightRollOff = -2.0f,
        rawMatrix = floatArrayOf(
            1.16f,  0.02f, -0.04f, 0f,  6.0f,
            0.01f,  1.08f, -0.02f, 0f,  3.0f,
           -0.06f, -0.02f,  0.94f, 0f, -2.0f,
            0.00f,  0.00f,  0.00f, 1f,  0.0f
        )
    ),

    WARM_CINEMA(
        id = "warm_cinema",
        displayName = "Warm Cinema",
        subtitle = "Golden Hour Glow",
        description = "Lush Mediterranean / California golden hour illumination with creamy amber highlights and lifted warm chocolate shadows.",
        accentColor = Color(0xFFFF7043),
        contrast = 1.12f,
        saturation = 1.15f,
        exposureOffset = 0.08f,
        shadowToe = 5.0f,
        highlightRollOff = -1.5f,
        rawMatrix = floatArrayOf(
            1.20f,  0.03f, -0.06f, 0f,  8.0f,
            0.02f,  1.10f, -0.04f, 0f,  5.0f,
           -0.08f, -0.03f,  0.88f, 0f, -6.0f,
            0.00f,  0.00f,  0.00f, 1f,  0.0f
        )
    ),

    MOODY_CINEMA(
        id = "moody_cinema",
        displayName = "Moody Cinema",
        subtitle = "Atmospheric Neo-Noir",
        description = "Psychological thriller palette with sculpted slate-blue shadows, desaturated cool midtones, and preserved luminous skin accents.",
        accentColor = Color(0xFF4FC3F7),
        contrast = 1.25f,
        saturation = 0.88f,
        exposureOffset = -0.05f,
        shadowToe = 2.0f,
        highlightRollOff = -3.0f,
        rawMatrix = floatArrayOf(
            0.92f, -0.02f,  0.03f, 0f, -4.0f,
           -0.02f,  1.02f,  0.04f, 0f,  2.0f,
            0.03f,  0.06f,  1.22f, 0f,  9.0f,
            0.00f,  0.00f,  0.00f, 1f,  0.0f
        )
    ),

    TEAL_AND_ORANGE(
        id = "teal_and_orange",
        displayName = "Teal & Orange",
        subtitle = "Blockbuster Contrast",
        description = "Iconic Hollywood blockbuster separation: luminous bronze skin tones dynamically sculpted against deep maritime teal shadows.",
        accentColor = Color(0xFF00E5FF),
        contrast = 1.26f,
        saturation = 1.18f,
        exposureOffset = 0.02f,
        shadowToe = 3.0f,
        highlightRollOff = -2.0f,
        rawMatrix = floatArrayOf(
            1.26f, -0.07f, -0.09f, 0f, 10.0f,
           -0.04f,  1.10f,  0.04f, 0f,  3.0f,
           -0.12f,  0.07f,  1.28f, 0f,  8.0f,
            0.00f,  0.00f,  0.00f, 1f,  0.0f
        )
    ),

    FILMIC_NATURAL(
        id = "filmic_natural",
        displayName = "Filmic Natural",
        subtitle = "Organic Daylight Cinema",
        description = "Gentle analog film emulation inspired by Fuji Eterna: soft highlight shoulder, muted pastel greens, and delicate organic skin roll-off.",
        accentColor = Color(0xFF81C784),
        contrast = 1.04f,
        saturation = 0.94f,
        exposureOffset = 0.0f,
        shadowToe = 4.0f,
        highlightRollOff = -2.0f,
        rawMatrix = floatArrayOf(
            1.03f,  0.02f, -0.02f, 0f, 4.0f,
            0.01f,  1.03f, -0.01f, 0f, 4.0f,
           -0.02f,  0.02f,  0.98f, 0f, 3.0f,
            0.00f,  0.00f,  0.00f, 1f, 0.0f
        )
    ),

    CLEAN_CINEMA(
        id = "clean_cinema",
        displayName = "Clean Cinema",
        subtitle = "Modern 8K Studio Clarity",
        description = "Crisp modern streaming cinema master: pure neutral blacks, high micro-contrast, vibrant primary separation, and pristine specular clarity.",
        accentColor = Color(0xFFE0E0E0),
        contrast = 1.15f,
        saturation = 1.08f,
        exposureOffset = 0.03f,
        shadowToe = 0.0f,
        highlightRollOff = -0.5f,
        rawMatrix = floatArrayOf(
            1.12f, 0.00f, 0.00f, 0f, 0.0f,
            0.00f, 1.12f, 0.00f, 0f, 0.0f,
            0.00f, 0.00f, 1.12f, 0f, 0.0f,
            0.00f, 0.00f, 0.00f, 1f, 0.0f
        )
    ),

    HIGH_CONTRAST_CINEMA(
        id = "high_contrast_cinema",
        displayName = "High Contrast Cinema",
        subtitle = "Bleach Bypass Noir",
        description = "Striking silver-retention film process: heavy dramatic contrast, crushed ink shadows, specular highlight punch, and stylized saturation.",
        accentColor = Color(0xFFFF5252),
        contrast = 1.34f,
        saturation = 0.74f,
        exposureOffset = -0.02f,
        shadowToe = -3.0f,
        highlightRollOff = -4.0f,
        rawMatrix = floatArrayOf(
            1.28f,  0.02f, -0.03f, 0f, -6.0f,
            0.02f,  1.20f,  0.01f, 0f, -5.0f,
           -0.03f,  0.02f,  1.20f, 0f, -5.0f,
            0.00f,  0.00f,  0.00f, 1f,  0.0f
        )
    ),

    SOFT_FILM(
        id = "soft_film",
        displayName = "Soft Film",
        subtitle = "Vintage Arthouse Bloom",
        description = "Poetic vintage film look with low contrast, milky lifted shadow toe, rolled-off highlight shoulder, and delicate pastel warmth.",
        accentColor = Color(0xFFCE93D8),
        contrast = 0.98f,
        saturation = 0.90f,
        exposureOffset = 0.06f,
        shadowToe = 8.0f,
        highlightRollOff = -1.0f,
        rawMatrix = floatArrayOf(
            1.02f,  0.02f, -0.01f, 0f, 8.0f,
            0.01f,  1.01f, -0.01f, 0f, 7.0f,
           -0.01f,  0.01f,  0.96f, 0f, 6.0f,
            0.00f,  0.00f,  0.00f, 1f, 0.0f
        )
    ),

    NIGHT_CINEMA(
        id = "night_cinema",
        displayName = "Night Cinema",
        subtitle = "Cyberpunk Nocturne",
        description = "Low-light cinema aesthetic: deep clean inky blacks, boosted neon cyan and magenta saturation, and shadow noise suppression.",
        accentColor = Color(0xFF7C4DFF),
        contrast = 1.28f,
        saturation = 1.22f,
        exposureOffset = -0.04f,
        shadowToe = -2.0f,
        highlightRollOff = -2.0f,
        rawMatrix = floatArrayOf(
            1.10f, -0.03f,  0.04f, 0f, -3.0f,
           -0.02f,  1.06f,  0.02f, 0f, -2.0f,
            0.04f, -0.02f,  1.24f, 0f,  4.0f,
            0.00f,  0.00f,  0.00f, 1f,  0.0f
        )
    );

    /**
     * Blends this Hollywood grade's 4x5 ColorMatrix with the identity matrix according to intensity.
     * 0.0 = original image (identity)
     * 1.0 = full selected grade
     * Intermediate values = smooth linear interpolation across all matrix cells and offsets.
     */
    fun getBlendedFloatArray(intensity: Float): FloatArray {
        val clampedIntensity = intensity.coerceIn(0f, 1f)
        if (this == OFF || clampedIntensity == 0f) {
            return floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        }
        val out = FloatArray(20)
        for (i in 0 until 20) {
            val isDiagonal = (i == 0 || i == 6 || i == 12 || i == 18)
            val identityVal = if (isDiagonal) 1.0f else 0.0f
            val targetVal = rawMatrix[i]
            out[i] = identityVal + clampedIntensity * (targetVal - identityVal)
        }
        return out
    }

    fun getBlendedColorMatrix(intensity: Float): ColorMatrix {
        return ColorMatrix(getBlendedFloatArray(intensity))
    }
}
