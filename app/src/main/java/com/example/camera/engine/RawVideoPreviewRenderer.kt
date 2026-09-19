package com.example.camera.engine

import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.media.Image
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

private const val TAG = "RawPreviewRenderer"

/**
 * Dedicated Real-Time Demosaic & Rendering Engine for Sensor RAW Video Mode Viewfinder.
 *
 * Requirements:
 * 1. The viewfinder receives frames directly from the RAW sensor stream (ImageFormat.RAW_SENSOR).
 * 2. It does NOT use the normal YUV processed preview.
 * 3. Minimal linear demosaicing to RGB without tone mapping, HDR, sharpening, saturation boost, or LUTs.
 * 4. High performance & zero GC churn: maintains reusable direct pixel buffers.
 */
class RawVideoPreviewRenderer {

    private val _rawPreviewBitmap = MutableStateFlow<Bitmap?>(null)
    val rawPreviewBitmap: StateFlow<Bitmap?> = _rawPreviewBitmap.asStateFlow()

    @Volatile
    var lastRenderedBitmap: Bitmap? = null
        private set

    // Reusable buffers to eliminate frame allocation latency
    private var pixelBuffer: IntArray? = null
    private var reusableBitmap: Bitmap? = null
    private var cachedWidth = 0
    private var cachedHeight = 0
    private var cachedOutWidth = 0
    private var cachedOutHeight = 0

    // Temporary direct byte buffer copy for thread-safe uncompressed Bayer reading
    private var rawByteBuffer: ByteArray? = null

    /**
     * Demosaics the incoming sensor RAW Bayer frame directly to a preview RGB Bitmap.
     */
    @Synchronized
    fun renderRawBayerToPreview(image: Image, characteristics: CameraCharacteristics?) {
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride.coerceAtLeast(2) // Usually 2 bytes for 16-bit raw sensor data
            val srcWidth = image.width
            val srcHeight = image.height

            if (srcWidth <= 0 || srcHeight <= 0) return

            // Step factor to downsample the multi-megapixel raw sensor stream
            // to a buttery smooth, low-latency viewfinder preview (e.g. ~480-720p).
            // Step MUST be an even integer (e.g., 2, 4) to maintain identical Bayer CFA phase!
            val step = when {
                srcWidth >= 3840 -> 4
                srcWidth >= 1920 -> 2
                else -> 2
            }

            val outWidth = srcWidth / step
            val outHeight = srcHeight / step

            if (reusableBitmap == null || cachedOutWidth != outWidth || cachedOutHeight != outHeight) {
                cachedWidth = srcWidth
                cachedHeight = srcHeight
                cachedOutWidth = outWidth
                cachedOutHeight = outHeight
                pixelBuffer = IntArray(outWidth * outHeight)
                reusableBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            }

            val pixels = pixelBuffer ?: return
            val bitmap = reusableBitmap ?: return

            // Extract sensor calibration
            val whiteLevel = (characteristics?.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL) ?: 1023).toFloat()
            val cfa = characteristics?.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT) ?: 0

            // Black level offsets
            var blR = 64f
            var blGr = 64f
            var blGb = 64f
            var blB = 64f
            val blPattern = characteristics?.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)
            if (blPattern != null) {
                blR = blPattern.getOffsetForIndex(0, 0).toFloat()
                blGr = blPattern.getOffsetForIndex(1, 0).toFloat()
                blGb = blPattern.getOffsetForIndex(0, 1).toFloat()
                blB = blPattern.getOffsetForIndex(1, 1).toFloat()
            }

            val rangeR = (whiteLevel - blR).coerceAtLeast(1f)
            val rangeG = (whiteLevel - (blGr + blGb) * 0.5f).coerceAtLeast(1f)
            val rangeB = (whiteLevel - blB).coerceAtLeast(1f)

            val totalBytes = buffer.remaining()
            if (rawByteBuffer == null || rawByteBuffer!!.size < totalBytes) {
                rawByteBuffer = ByteArray(totalBytes)
            }
            val rawBytes = rawByteBuffer!!
            val currentPos = buffer.position()
            buffer.get(rawBytes, 0, totalBytes)
            buffer.position(currentPos)

            // Direct sensor Bayer demosaicing:
            // cfa: 0 = RGGB, 1 = GRBG, 2 = GBRG, 3 = BGGR
            // Linear sensor RGB extraction (Zero tone-curve, zero LUT, zero artificial sharpening)
            var outIdx = 0
            for (outY in 0 until outHeight) {
                val srcY = outY * step
                val rowOffset = srcY * rowStride
                val nextRowOffset = if (srcY + 1 < srcHeight) rowOffset + rowStride else rowOffset

                for (outX in 0 until outWidth) {
                    val srcX = outX * step
                    val colOffset = srcX * pixelStride
                    val nextColOffset = colOffset + pixelStride

                    // Read 16-bit little-endian Bayer sensor values
                    val p00 = readSample16(rawBytes, rowOffset + colOffset, totalBytes)
                    val p01 = readSample16(rawBytes, rowOffset + nextColOffset, totalBytes)
                    val p10 = readSample16(rawBytes, nextRowOffset + colOffset, totalBytes)
                    val p11 = readSample16(rawBytes, nextRowOffset + nextColOffset, totalBytes)

                    var rVal: Float
                    var gVal: Float
                    var bVal: Float

                    when (cfa) {
                        0 -> { // RGGB
                            rVal = p00.toFloat()
                            gVal = (p01 + p10) * 0.5f
                            bVal = p11.toFloat()
                        }
                        1 -> { // GRBG
                            gVal = (p00 + p11) * 0.5f
                            rVal = p01.toFloat()
                            bVal = p10.toFloat()
                        }
                        2 -> { // GBRG
                            gVal = (p00 + p11) * 0.5f
                            bVal = p01.toFloat()
                            rVal = p10.toFloat()
                        }
                        3 -> { // BGGR
                            bVal = p00.toFloat()
                            gVal = (p01 + p10) * 0.5f
                            rVal = p11.toFloat()
                        }
                        else -> { // Default RGGB
                            rVal = p00.toFloat()
                            gVal = (p01 + p10) * 0.5f
                            bVal = p11.toFloat()
                        }
                    }

                    // Linear normalization directly from sensor RAW data
                    val rNorm = (((rVal - blR) / rangeR) * 255f).toInt().coerceIn(0, 255)
                    val gNorm = (((gVal - (blGr + blGb) * 0.5f) / rangeG) * 255f).toInt().coerceIn(0, 255)
                    val bNorm = (((bVal - blB) / rangeB) * 255f).toInt().coerceIn(0, 255)

                    // ARGB pack
                    pixels[outIdx++] = (0xFF shl 24) or (rNorm shl 16) or (gNorm shl 8) or bNorm
                }
            }

            bitmap.setPixels(pixels, 0, outWidth, 0, 0, outWidth, outHeight)
            lastRenderedBitmap = bitmap
            _rawPreviewBitmap.value = bitmap
        } catch (e: Exception) {
            Log.w(TAG, "Error rendering RAW Bayer preview frame", e)
        }
    }

    private inline fun readSample16(bytes: ByteArray, offset: Int, maxLen: Int): Int {
        if (offset + 1 >= maxLen || offset < 0) return 0
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        return (b1 shl 8) or b0
    }

    fun clear() {
        _rawPreviewBitmap.value = null
        lastRenderedBitmap = null
    }
}
