package com.example.camera.data

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private const val TAG = "CubeLutParser"

/**
 * Parsed representation of an Adobe .cube Look-Up Table (1D or 3D).
 */
data class ParsedCubeLut(
    val id: String,
    val title: String,
    val is3D: Boolean,
    val size: Int,
    val domainMin: FloatArray = floatArrayOf(0f, 0f, 0f),
    val domainMax: FloatArray = floatArrayOf(1f, 1f, 1f),
    val tonemapCurve: FloatArray, // 64 points for Camera2 TonemapCurve (x=0..1 -> y=0..1)
    val colorMatrix: FloatArray,  // 4x5 (20 floats) for Android/Compose ColorMatrix
    val estimatedContrast: Float = 1.15f,
    val estimatedSaturation: Float = 1.05f,
    val highlightRollOff: Float = 0.65f,
    val shadowToe: Float = 0.02f
) {
    val matrix3x3: FloatArray
        get() = floatArrayOf(
            colorMatrix[0], colorMatrix[1], colorMatrix[2],
            colorMatrix[5], colorMatrix[6], colorMatrix[7],
            colorMatrix[10], colorMatrix[11], colorMatrix[12]
        )

    fun toTonemapCurve(): android.hardware.camera2.params.TonemapCurve {
        val numPoints = 32
        val red = FloatArray(numPoints * 2)
        val green = FloatArray(numPoints * 2)
        val blue = FloatArray(numPoints * 2)
        for (i in 0 until numPoints) {
            val x = i.toFloat() / (numPoints - 1).toFloat()
            val curveIdx = (x * (tonemapCurve.size - 1)).toInt().coerceIn(0, tonemapCurve.size - 1)
            val y = tonemapCurve[curveIdx].coerceIn(0f, 1f)
            val idx = i * 2
            red[idx] = x
            red[idx + 1] = y
            green[idx] = x
            green[idx + 1] = y
            blue[idx] = x
            blue[idx + 1] = y
        }
        return android.hardware.camera2.params.TonemapCurve(red, green, blue)
    }

    fun toAndroidColorMatrix(): android.graphics.ColorMatrix {
        return android.graphics.ColorMatrix(colorMatrix)
    }
}

/**
 * High-performance, robust parser for industry-standard .cube LUT files.
 * Supports both 1D and 3D LUT tables conforming to Adobe Cube LUT specifications.
 */
object CubeLutParser {

    private val cache = java.util.concurrent.ConcurrentHashMap<String, ParsedCubeLut>()

    fun getOrLoad(filePath: String): ParsedCubeLut? {
        cache[filePath]?.let { return it }
        val file = java.io.File(filePath)
        if (!file.exists()) return null
        return try {
            file.inputStream().use { stream ->
                parseStream(stream, file.nameWithoutExtension)?.also { parsed ->
                    cache[filePath] = parsed
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached .cube LUT from $filePath", e)
            null
        }
    }

    fun parse(context: Context, uri: Uri, fallbackTitle: String = "Custom LUT"): ParsedCubeLut? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                parseStream(stream, fallbackTitle)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse .cube LUT from URI: $uri", e)
            null
        }
    }

    fun parseStream(stream: InputStream, defaultTitle: String): ParsedCubeLut? {
        val reader = BufferedReader(InputStreamReader(stream))
        var title = defaultTitle
        var is3D = true
        var lutSize = 0
        var domainMin = floatArrayOf(0f, 0f, 0f)
        var domainMax = floatArrayOf(1f, 1f, 1f)

        val tableData = mutableListOf<FloatArray>()

        reader.forEachLine { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) {
                return@forEachLine
            }

            val tokens = line.split("\\s+".toRegex())
            if (tokens.isEmpty()) return@forEachLine

            when (tokens[0].uppercase()) {
                "TITLE" -> {
                    title = tokens.drop(1).joinToString(" ").replace("\"", "").trim()
                }
                "LUT_3D_SIZE" -> {
                    is3D = true
                    lutSize = tokens.getOrNull(1)?.toIntOrNull() ?: 33
                }
                "LUT_1D_SIZE" -> {
                    is3D = false
                    lutSize = tokens.getOrNull(1)?.toIntOrNull() ?: 1024
                }
                "DOMAIN_MIN" -> {
                    if (tokens.size >= 4) {
                        domainMin = floatArrayOf(
                            tokens[1].toFloatOrNull() ?: 0f,
                            tokens[2].toFloatOrNull() ?: 0f,
                            tokens[3].toFloatOrNull() ?: 0f
                        )
                    }
                }
                "DOMAIN_MAX" -> {
                    if (tokens.size >= 4) {
                        domainMax = floatArrayOf(
                            tokens[1].toFloatOrNull() ?: 1f,
                            tokens[2].toFloatOrNull() ?: 1f,
                            tokens[3].toFloatOrNull() ?: 1f
                        )
                    }
                }
                else -> {
                    // Try parsing RGB triplet
                    if (tokens.size >= 3) {
                        val r = tokens[0].toFloatOrNull()
                        val g = tokens[1].toFloatOrNull()
                        val b = tokens[2].toFloatOrNull()
                        if (r != null && g != null && b != null) {
                            tableData.add(floatArrayOf(r, g, b))
                        }
                    }
                }
            }
        }

        if (tableData.isEmpty() || lutSize <= 0) {
            Log.w(TAG, "Empty or invalid .cube file: size=$lutSize, rows=${tableData.size}")
            return null
        }

        val totalExpected = if (is3D) lutSize * lutSize * lutSize else lutSize
        if (tableData.size < totalExpected) {
            Log.w(TAG, "Truncated .cube file: expected $totalExpected rows, found ${tableData.size}")
        }

        // 1. Sample 64-point Luminance / Tonemap Curve along neutral diagonal (R=G=B)
        val curvePoints = 64
        val tonemapCurve = FloatArray(curvePoints)

        if (is3D) {
            // In 3D LUT ordering: red fastest, green next, blue slowest
            // Index = r + g * size + b * size * size
            for (i in 0 until curvePoints) {
                val t = i.toFloat() / (curvePoints - 1).toFloat()
                val idx = (t * (lutSize - 1)).toInt().coerceIn(0, lutSize - 1)
                val flatIndex = (idx + idx * lutSize + idx * lutSize * lutSize).coerceIn(0, tableData.size - 1)
                val rgb = tableData[flatIndex]
                // Perceptual Rec.709 luminance Y = 0.2126 R + 0.7152 G + 0.0722 B
                val lum = (0.2126f * rgb[0] + 0.7152f * rgb[1] + 0.0722f * rgb[2]).coerceIn(0f, 1f)
                tonemapCurve[i] = lum
            }
        } else {
            for (i in 0 until curvePoints) {
                val t = i.toFloat() / (curvePoints - 1).toFloat()
                val idx = (t * (tableData.size - 1)).toInt().coerceIn(0, tableData.size - 1)
                val rgb = tableData[idx]
                val lum = (0.2126f * rgb[0] + 0.7152f * rgb[1] + 0.0722f * rgb[2]).coerceIn(0f, 1f)
                tonemapCurve[i] = lum
            }
        }

        // 2. Extract 4x5 ColorMatrix by sampling basis color responses
        // Red corner: (size-1, 0, 0)
        // Green corner: (0, size-1, 0)
        // Blue corner: (0, 0, size-1)
        // Black corner: (0, 0, 0)
        // White corner: (size-1, size-1, size-1)
        val blackRgb: FloatArray
        val redRgb: FloatArray
        val greenRgb: FloatArray
        val blueRgb: FloatArray
        val whiteRgb: FloatArray

        if (is3D) {
            val maxIdx = lutSize - 1
            fun get3D(r: Int, g: Int, b: Int): FloatArray {
                val idx = (r + g * lutSize + b * lutSize * lutSize).coerceIn(0, tableData.size - 1)
                return tableData[idx]
            }
            blackRgb = get3D(0, 0, 0)
            redRgb = get3D(maxIdx, 0, 0)
            greenRgb = get3D(0, maxIdx, 0)
            blueRgb = get3D(0, 0, maxIdx)
            whiteRgb = get3D(maxIdx, maxIdx, maxIdx)
        } else {
            blackRgb = tableData.first()
            whiteRgb = tableData.last()
            redRgb = floatArrayOf(whiteRgb[0], blackRgb[1], blackRgb[2])
            greenRgb = floatArrayOf(blackRgb[0], whiteRgb[1], blackRgb[2])
            blueRgb = floatArrayOf(blackRgb[0], blackRgb[1], whiteRgb[2])
        }

        // Calculate affine color transform matrix
        val rr = (redRgb[0] - blackRgb[0]).coerceIn(0.5f, 1.8f)
        val rg = (redRgb[1] - blackRgb[1]).coerceIn(-0.4f, 0.4f)
        val rb = (redRgb[2] - blackRgb[2]).coerceIn(-0.4f, 0.4f)
        val roff = blackRgb[0] * 255f

        val gr = (greenRgb[0] - blackRgb[0]).coerceIn(-0.4f, 0.4f)
        val gg = (greenRgb[1] - blackRgb[1]).coerceIn(0.5f, 1.8f)
        val gb = (greenRgb[2] - blackRgb[2]).coerceIn(-0.4f, 0.4f)
        val goff = blackRgb[1] * 255f

        val br = (blueRgb[0] - blackRgb[0]).coerceIn(-0.4f, 0.4f)
        val bg = (blueRgb[1] - blackRgb[1]).coerceIn(-0.4f, 0.4f)
        val bb = (blueRgb[2] - blackRgb[2]).coerceIn(0.5f, 1.8f)
        val boff = blackRgb[2] * 255f

        val colorMatrix = floatArrayOf(
            rr, rg, rb, 0f, roff,
            gr, gg, gb, 0f, goff,
            br, bg, bb, 0f, boff,
            0f, 0f, 0f, 1f, 0f
        )

        // 3. Compute characteristics
        val midToneIndex = curvePoints / 2
        val midVal = tonemapCurve[midToneIndex]
        val contrast = ((tonemapCurve[curvePoints * 3 / 4] - tonemapCurve[curvePoints / 4]) * 2.0f).coerceIn(0.8f, 1.5f)
        val shadowToe = tonemapCurve[0].coerceIn(0f, 0.2f)
        val highlightRollOff = (1.0f - tonemapCurve[curvePoints - 1]).coerceIn(0f, 0.3f) + 0.5f

        val id = "lut_" + System.currentTimeMillis()

        return ParsedCubeLut(
            id = id,
            title = if (title.isNotBlank()) title else defaultTitle,
            is3D = is3D,
            size = lutSize,
            domainMin = domainMin,
            domainMax = domainMax,
            tonemapCurve = tonemapCurve,
            colorMatrix = colorMatrix,
            estimatedContrast = contrast,
            estimatedSaturation = 1.05f,
            highlightRollOff = highlightRollOff,
            shadowToe = shadowToe
        )
    }
}
