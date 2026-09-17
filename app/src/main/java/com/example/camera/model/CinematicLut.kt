package com.example.camera.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * Cinematic 3D/1D Look-Up Table (LUT) Presets for Log video monitoring & grading.
 * Designed to preview filmic contrast & color separation on top of Log transfer curves
 * without modifying the pristine recorded Log master stream.
 */
enum class CinematicLut(
    val id: String,
    val label: String,
    val description: String,
    val category: String,
    val accentColor: Color,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val warmCoolOffset: Float = 0.0f,
    private val matrixValues: FloatArray? = null
) {
    NONE(
        id = "clean_log",
        label = "None (Clean Log)",
        description = "Unmodified wide-latitude Log sensor curve for pure uncompressed grading",
        category = "Master",
        accentColor = Color(0xFFFFD54F),
        contrast = 1.0f,
        saturation = 1.0f,
        warmCoolOffset = 0.0f
    ),

    KODAK_2383(
        id = "kodak_2383",
        label = "Kodak 2383",
        description = "Legendary Hollywood print stock with warm highlight roll-off, creamy golden skin tones & deep split-tone shadows",
        category = "Cinema Film",
        accentColor = Color(0xFFF6C343),
        contrast = 1.24f,
        saturation = 1.15f,
        warmCoolOffset = 0.16f,
        matrixValues = floatArrayOf(
            1.18f, 0.02f, -0.04f, 0f, 6f,
            0.02f, 1.06f, -0.02f, 0f, 2f,
            -0.06f, 0.01f, 0.92f, 0f, -6f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    TEAL_ORANGE(
        id = "teal_orange",
        label = "Teal & Orange",
        description = "Modern Hollywood blockbuster color separation: amber skin tones protected against rich cyan-teal shadows",
        category = "Hollywood",
        accentColor = Color(0xFF00E5FF),
        contrast = 1.26f,
        saturation = 1.20f,
        warmCoolOffset = 0.08f,
        matrixValues = floatArrayOf(
            1.20f, -0.05f, -0.07f, 0f, 8f,
            -0.03f, 1.07f, 0.04f, 0f, 2f,
            -0.10f, 0.06f, 1.22f, 0f, 6f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    ARRI_ALEXA_709(
        id = "arri_alexa_709",
        label = "ARRI Alexa 709",
        description = "Industry standard reference monitoring LUT: natural skin tones, smooth highlight shoulder & neutral deep blacks",
        category = "Director",
        accentColor = Color(0xFFFFCC80),
        contrast = 1.16f,
        saturation = 1.05f,
        warmCoolOffset = 0.02f,
        matrixValues = floatArrayOf(
            1.08f, -0.02f, -0.02f, 0f, 0f,
            -0.01f, 1.06f, -0.01f, 0f, 0f,
            -0.02f, -0.02f, 1.08f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    KODAK_PORTRA(
        id = "kodak_portra",
        label = "Kodak Portra 400",
        description = "Iconic portrait emulsion with smooth gentle highlight compression, warm skin glow & delicate pastel tones",
        category = "Cinema Film",
        accentColor = Color(0xFFFFAB91),
        contrast = 1.10f,
        saturation = 1.08f,
        warmCoolOffset = 0.14f,
        matrixValues = floatArrayOf(
            1.12f, 0.04f, -0.04f, 0f, 4f,
            0.02f, 1.04f, -0.02f, 0f, 2f,
            -0.04f, -0.01f, 0.94f, 0f, -2f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    FUJI_ETERNA(
        id = "fuji_eterna",
        label = "Fuji Eterna 500T",
        description = "Acclaimed cinema latitude: soft understated saturation, gentle tonal graduation & cool filmic shadow toe",
        category = "Modern Cine",
        accentColor = Color(0xFF80CBC4),
        contrast = 1.12f,
        saturation = 0.88f,
        warmCoolOffset = -0.06f,
        matrixValues = floatArrayOf(
            0.96f, 0.02f, 0.02f, 0f, 2f,
            0.02f, 0.98f, 0.02f, 0f, 2f,
            0.02f, 0.04f, 1.04f, 0f, 4f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    MUTED_CINE(
        id = "muted_cine",
        label = "Muted Arthouse",
        description = "Subtle desaturation with clean charcoal shadows & soft, organic highlight roll",
        category = "Modern Cine",
        accentColor = Color(0xFFB0BEC5),
        contrast = 1.08f,
        saturation = 0.80f,
        warmCoolOffset = -0.04f,
        matrixValues = floatArrayOf(
            0.94f, 0.03f, 0.03f, 0f, 4f,
            0.03f, 0.95f, 0.03f, 0f, 4f,
            0.03f, 0.03f, 0.98f, 0f, 6f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    BLEACH_BYPASS(
        id = "bleach_bypass",
        label = "Bleach Bypass",
        description = "Silver retention chemical process with desaturated hues, crisp specular retention & dramatic gritty texture",
        category = "Dramatic",
        accentColor = Color(0xFFCFD8DC),
        contrast = 1.36f,
        saturation = 0.45f,
        warmCoolOffset = -0.03f,
        matrixValues = floatArrayOf(
            1.22f, 0.10f, 0.10f, 0f, -4f,
            0.10f, 1.22f, 0.10f, 0f, -4f,
            0.10f, 0.10f, 1.22f, 0f, -4f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    MONO_CINE(
        id = "mono_cine",
        label = "Cine Monochrome",
        description = "Panatomic silver emulsion black & white grading with luminous midtones & rich velvety blacks",
        category = "Monochrome",
        accentColor = Color(0xFFFAFAFA),
        contrast = 1.28f,
        saturation = 0.0f,
        warmCoolOffset = 0.0f,
        matrixValues = floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    NEO_NOIR(
        id = "neo_noir",
        label = "Neo-Noir Thriller",
        description = "Atmospheric thriller tone with emerald-cyan deep shadows, punchy contrast & rich neon highlights",
        category = "Atmospheric",
        accentColor = Color(0xFF26A69A),
        contrast = 1.26f,
        saturation = 0.90f,
        warmCoolOffset = -0.12f,
        matrixValues = floatArrayOf(
            0.92f, 0.01f, -0.01f, 0f, -2f,
            -0.01f, 1.10f, 0.03f, 0f, 4f,
            0.01f, 0.05f, 1.16f, 0f, 8f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    WARM_SUNSET(
        id = "warm_sunset",
        label = "Golden Sunset",
        description = "Luminous amber golden hour lighting, radiant skin warmth & gentle compressed highlight contrast",
        category = "Warm",
        accentColor = Color(0xFFFFB300),
        contrast = 1.15f,
        saturation = 1.18f,
        warmCoolOffset = 0.24f,
        matrixValues = floatArrayOf(
            1.20f, 0.03f, -0.08f, 0f, 10f,
            0.02f, 1.08f, -0.04f, 0f, 4f,
            -0.10f, -0.03f, 0.82f, 0f, -6f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    COOL_STEEL(
        id = "cool_steel",
        label = "Cool Steel & Ice",
        description = "Nordic sci-fi cold steel aesthetic with cobalt shadows and clean neutral skin tones",
        category = "Cool",
        accentColor = Color(0xFF64B5F6),
        contrast = 1.20f,
        saturation = 0.95f,
        warmCoolOffset = -0.20f,
        matrixValues = floatArrayOf(
            0.88f, -0.01f, 0.01f, 0f, -4f,
            -0.01f, 0.98f, 0.04f, 0f, 0f,
            0.03f, 0.06f, 1.28f, 0f, 12f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    EKTACHROME(
        id = "ektachrome",
        label = "Ektachrome 100",
        description = "Vibrant saturated primaries, ultra-clean cyan skies and dynamic reversal clarity",
        category = "Cinema Film",
        accentColor = Color(0xFF29B6F6),
        contrast = 1.22f,
        saturation = 1.28f,
        warmCoolOffset = -0.06f,
        matrixValues = floatArrayOf(
            1.10f, -0.03f, -0.01f, 0f, 2f,
            -0.01f, 1.15f, -0.03f, 0f, 4f,
            -0.03f, 0.02f, 1.24f, 0f, 8f,
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
     * Generates an Android [android.graphics.ColorMatrix] for image post-processing.
     */
    fun toAndroidColorMatrix(): android.graphics.ColorMatrix? {
        val vals = matrixValues ?: return null
        return android.graphics.ColorMatrix(vals)
    }
}
