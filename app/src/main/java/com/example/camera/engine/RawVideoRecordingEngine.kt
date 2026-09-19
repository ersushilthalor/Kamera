package com.example.camera.engine

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.BlackLevelPattern
import android.hardware.camera2.params.ColorSpaceTransform
import android.media.Image
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.example.camera.model.CameraResolution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.Deflater

private const val TAG = "RawVideoRecorder"

/**
 * Storage & Telemetry state for Sensor RAW Video Mode.
 */
data class RawVideoTelemetry(
    val isSupported: Boolean = true,
    val unsupportedReason: String? = null,
    val isRecording: Boolean = false,
    val recordedFrames: Long = 0L,
    val recordedBytes: Long = 0L,
    val currentDataRateMbPerSec: Float = 0f,
    val freeStorageGb: Float = 0f,
    val estimatedRemainingMinutes: Int = 0,
    val rawFormatLabel: String = "RAW_SENSOR (Bayer CFA)",
    val resolutionLabel: String = "1920x1080",
    val bayerPatternLabel: String = "RGGB"
)

/**
 * Real Sensor RAW Video Recording Engine.
 *
 * Captures native sensor RAW/Bayer frames directly through Camera2 / Camera HAL
 * without any processing, tone mapping, ISP pipeline, sharpening, or compression artifacts.
 *
 * Container format: Kamera RAW Stream (.rawvid)
 * Binary Layout:
 * 1. Stream Header (256 bytes):
 *    - Magic: 0x4B524157 ("KRAW")
 *    - Version: 1
 *    - Width (Int32), Height (Int32), Target FPS (Int32)
 *    - RAW Format (Int32: ImageFormat.RAW_SENSOR = 32, RAW10 = 37, RAW12 = 38)
 *    - Bayer Pattern (Int32: 0=RGGB, 1=GRBG, 2=GBRG, 3=BGGR)
 *    - White Level (Int32)
 *    - Black Level (4 Floats: R, Gr, Gb, B)
 *    - Color Transform 1 (9 Floats: 3x3 sensor calibration matrix)
 *    - Focal Length (Float), Max Aperture (Float)
 *    - Total Frame Count placeholder (Int64, patched upon completion)
 *    - Reserved padding up to 256 bytes
 *
 * 2. Continuous Frame Chunks:
 *    - Frame Magic: 0x46524D45 ("FRME")
 *    - Frame Index (Int64)
 *    - Timestamp Ns (Int64)
 *    - Exposure Time Ns (Int64)
 *    - Sensor ISO (Int32)
 *    - Focus Distance (Float)
 *    - Compression Mode (Int16: 0 = uncompressed raw bayer, 1 = lossless Deflate)
 *    - Payload Size (Int32)
 *    - Raw Bayer Payload (exact sensor bytes)
 */
class RawVideoRecordingEngine(private val context: Context) {

    private val _telemetry = MutableStateFlow(RawVideoTelemetry())
    val telemetry: StateFlow<RawVideoTelemetry> = _telemetry.asStateFlow()

    private val isRecording = AtomicBoolean(false)
    private val frameCount = AtomicLong(0L)
    private val totalBytesWritten = AtomicLong(0L)

    private var currentOutputFile: File? = null
    private var outputStream: BufferedOutputStream? = null
    private var fileOutputStream: FileOutputStream? = null

    private var recordingWidth = 0
    private var recordingHeight = 0
    private var recordingFps = 30
    private var rawFormat = ImageFormat.RAW_SENSOR
    private var bayerPattern = 0 // 0=RGGB
    private var whiteLevel = 1023
    private val blackLevel = FloatArray(4) { 64f }
    private val colorTransform = FloatArray(9) { if (it % 4 == 0) 1f else 0f }
    private var focalLength = 4.25f
    private var aperture = 1.8f
    private var recordingStartTimeMs = 0L

    // Reusable byte buffers for deflater & IO to minimize allocations during streaming
    private val deflater = Deflater(Deflater.BEST_SPEED)
    private var compressionBuffer: ByteArray? = null

    /**
     * Inspects hardware characteristics to verify real RAW support.
     */
    fun checkHardwareSupport(characteristics: CameraCharacteristics?): Pair<Boolean, String?> {
        if (characteristics == null) {
            return Pair(false, "Camera sensor characteristics unavailable")
        }

        val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        val supportsRaw = capabilities?.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
        ) == true

        if (!supportsRaw) {
            val reason = "Camera HAL reports REQUEST_AVAILABLE_CAPABILITIES_RAW not supported on this lens"
            _telemetry.value = _telemetry.value.copy(
                isSupported = false,
                unsupportedReason = reason
            )
            return Pair(false, reason)
        }

        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val rawSizes = map?.getOutputSizes(ImageFormat.RAW_SENSOR)
        if (rawSizes.isNullOrEmpty()) {
            val reason = "No valid RAW_SENSOR resolutions exposed by camera HAL"
            _telemetry.value = _telemetry.value.copy(
                isSupported = false,
                unsupportedReason = reason
            )
            return Pair(false, reason)
        }

        _telemetry.value = _telemetry.value.copy(
            isSupported = true,
            unsupportedReason = null
        )
        return Pair(true, null)
    }

    /**
     * Updates storage calculation and telemetry.
     */
    fun updateStorageEstimates(width: Int, height: Int, fps: Int) {
        try {
            val path = context.getExternalFilesDir(null) ?: context.filesDir
            val stat = StatFs(path.path)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val freeGb = availableBytes.toFloat() / (1024f * 1024f * 1024f)

            // Approximate RAW bayer frame size: 16-bit per pixel uncompressed (or packed)
            val bytesPerFrame = (width * height * 1.5f).toLong()
            val bytesPerSec = bytesPerFrame * fps
            val mbPerSec = bytesPerSec.toFloat() / (1024f * 1024f)

            // Lossless compression reduces size by ~35% on average
            val effectiveBytesPerSec = (bytesPerSec * 0.65f).toLong()
            val remainingSecs = if (effectiveBytesPerSec > 0) availableBytes / effectiveBytesPerSec else 0L
            val remainingMinutes = (remainingSecs / 60L).toInt().coerceAtLeast(0)

            _telemetry.value = _telemetry.value.copy(
                freeStorageGb = freeGb,
                currentDataRateMbPerSec = mbPerSec * 0.65f,
                estimatedRemainingMinutes = remainingMinutes,
                resolutionLabel = "${width}x${height} @ ${fps}fps"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating storage estimates", e)
        }
    }

    /**
     * Starts continuous RAW Video stream recording.
     */
    @Synchronized
    fun startRecording(
        destFile: File,
        width: Int,
        height: Int,
        fps: Int,
        characteristics: CameraCharacteristics?
    ): Boolean {
        if (isRecording.get()) {
            Log.w(TAG, "RAW video recording is already active")
            return true
        }

        val (supported, reason) = checkHardwareSupport(characteristics)
        if (!supported) {
            Log.e(TAG, "Cannot start RAW video recording: $reason")
            return false
        }

        try {
            currentOutputFile = destFile
            destFile.parentFile?.mkdirs()
            if (destFile.exists()) destFile.delete()
            destFile.createNewFile()

            recordingWidth = width
            recordingHeight = height
            recordingFps = fps
            frameCount.set(0L)
            totalBytesWritten.set(0L)
            recordingStartTimeMs = System.currentTimeMillis()

            // Extract sensor characteristics
            whiteLevel = characteristics?.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL) ?: 1023
            val bayer = characteristics?.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT) ?: 0
            bayerPattern = bayer

            val blPattern = characteristics?.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)
            if (blPattern != null) {
                blackLevel[0] = blPattern.getOffsetForIndex(0, 0).toFloat()
                blackLevel[1] = blPattern.getOffsetForIndex(1, 0).toFloat()
                blackLevel[2] = blPattern.getOffsetForIndex(0, 1).toFloat()
                blackLevel[3] = blPattern.getOffsetForIndex(1, 1).toFloat()
            }

            val cTransform = characteristics?.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1)
            if (cTransform != null) {
                for (r in 0 until 3) {
                    for (c in 0 until 3) {
                        val rational = cTransform.getElement(c, r)
                        colorTransform[r * 3 + c] = rational.numerator.toFloat() / rational.denominator.toFloat()
                    }
                }
            }

            val focalLengths = characteristics?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            focalLength = focalLengths?.firstOrNull() ?: 4.25f

            val apertures = characteristics?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
            aperture = apertures?.firstOrNull() ?: 1.8f

            val fos = FileOutputStream(destFile)
            fileOutputStream = fos
            outputStream = BufferedOutputStream(fos, 1024 * 1024) // 1MB buffer

            // Write 256-byte header
            writeHeader(outputStream!!)
            outputStream!!.flush()

            isRecording.set(true)

            val bayerPatternText = when (bayerPattern) {
                0 -> "RGGB"
                1 -> "GRBG"
                2 -> "GBRG"
                3 -> "BGGR"
                else -> "Bayer $bayerPattern"
            }

            _telemetry.value = _telemetry.value.copy(
                isRecording = true,
                recordedFrames = 0L,
                recordedBytes = 256L,
                bayerPatternLabel = bayerPatternText,
                resolutionLabel = "${width}x${height} @ ${fps}fps"
            )

            Log.i(TAG, "RAW Video recording started: ${destFile.absolutePath} (${width}x${height}, $bayerPatternText)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start RAW Video recording", e)
            cleanUp()
            return false
        }
    }

    /**
     * Writes binary header (256 bytes).
     */
    private fun writeHeader(out: BufferedOutputStream) {
        val header = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
        header.put(byteArrayOf('K'.code.toByte(), 'R'.code.toByte(), 'A'.code.toByte(), 'W'.code.toByte())) // Magic "KRAW"
        header.putShort(1.toShort()) // Version 1
        header.putShort(0.toShort()) // Reserved
        header.putInt(recordingWidth)
        header.putInt(recordingHeight)
        header.putInt(recordingFps)
        header.putInt(rawFormat)
        header.putInt(bayerPattern)
        header.putInt(whiteLevel)

        // Black level (4 floats)
        for (i in 0 until 4) header.putFloat(blackLevel[i])

        // Color transform (9 floats)
        for (i in 0 until 9) header.putFloat(colorTransform[i])

        header.putFloat(focalLength)
        header.putFloat(aperture)
        header.putLong(0L) // Total frames placeholder at byte offset 88

        // Fill remaining bytes up to 256 with zeros
        while (header.hasRemaining()) {
            header.put(0.toByte())
        }

        val headerBytes = header.array()
        out.write(headerBytes)
        totalBytesWritten.addAndGet(headerBytes.size.toLong())
    }

    /**
     * Ingests a native sensor RAW frame and appends it to the continuous container stream.
     */
    @Synchronized
    fun onRawImageAvailable(image: Image, result: TotalCaptureResult?) {
        if (!isRecording.get() || outputStream == null) {
            return
        }

        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val bufferSize = buffer.remaining()
            if (bufferSize <= 0) return

            val frameIdx = frameCount.getAndIncrement()
            val timestampNs = result?.get(CaptureResult.SENSOR_TIMESTAMP) ?: image.timestamp
            val exposureNs = result?.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 10_000_000L
            val iso = result?.get(CaptureResult.SENSOR_SENSITIVITY) ?: 100
            val focusDist = result?.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: 0f

            // Frame Header (32 bytes)
            // 0..3: "FRME"
            // 4..11: Frame index (Long)
            // 12..19: Timestamp Ns (Long)
            // 20..27: Exposure Ns (Long)
            // 28..31: ISO (Int)
            // Next 8 bytes: Focus dist (Float), Compression mode (Short), Reserved (Short)
            // Next 4 bytes: Payload Size (Int)
            val rawBytes = ByteArray(bufferSize)
            buffer.get(rawBytes)

            // Perform fast lossless packing / Deflate compression
            deflater.reset()
            deflater.setInput(rawBytes)
            deflater.finish()

            val maxCompressed = bufferSize + 1024
            if (compressionBuffer == null || compressionBuffer!!.size < maxCompressed) {
                compressionBuffer = ByteArray(maxCompressed)
            }
            val compressedSize = deflater.deflate(compressionBuffer!!)

            val useCompression = compressedSize > 0 && compressedSize < bufferSize
            val payloadBytes = if (useCompression) compressionBuffer!! else rawBytes
            val payloadSize = if (useCompression) compressedSize else bufferSize
            val compressionMode: Short = if (useCompression) 1 else 0

            val frameHeader = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            frameHeader.put(byteArrayOf('F'.code.toByte(), 'R'.code.toByte(), 'M'.code.toByte(), 'E'.code.toByte()))
            frameHeader.putLong(frameIdx)
            frameHeader.putLong(timestampNs)
            frameHeader.putLong(exposureNs)
            frameHeader.putInt(iso)
            frameHeader.putFloat(focusDist)
            frameHeader.putShort(compressionMode)
            frameHeader.putShort(0.toShort()) // Reserved
            frameHeader.putInt(payloadSize)

            val out = outputStream ?: return
            out.write(frameHeader.array())
            out.write(payloadBytes, 0, payloadSize)

            val written = totalBytesWritten.addAndGet(44L + payloadSize)

            if (frameIdx % 15 == 0L) {
                val elapsedSec = (System.currentTimeMillis() - recordingStartTimeMs).coerceAtLeast(1) / 1000f
                val mbPerSec = (written / (1024f * 1024f)) / elapsedSec
                _telemetry.value = _telemetry.value.copy(
                    recordedFrames = frameIdx + 1,
                    recordedBytes = written,
                    currentDataRateMbPerSec = mbPerSec
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing RAW frame to stream", e)
        }
    }

    /**
     * Finalizes and closes the continuous RAW Video container stream.
     */
    @Synchronized
    fun stopRecording(): File? {
        if (!isRecording.get()) {
            return null
        }

        isRecording.set(false)
        val file = currentOutputFile

        try {
            outputStream?.flush()
            outputStream?.close()
            fileOutputStream?.close()

            // Patch total frame count in header at offset 88
            if (file != null && file.exists() && file.length() >= 256) {
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(88)
                    val frameBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                    frameBuf.putLong(frameCount.get())
                    raf.write(frameBuf.array())
                }
            }

            Log.i(TAG, "RAW Video recording successfully finished: ${file?.absolutePath} (${frameCount.get()} frames, ${file?.length()} bytes)")
            _telemetry.value = _telemetry.value.copy(
                isRecording = false,
                recordedFrames = frameCount.get(),
                recordedBytes = file?.length() ?: 0L
            )
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Error closing RAW Video recording stream", e)
            return file
        } finally {
            cleanUp()
        }
    }

    private fun cleanUp() {
        try { outputStream?.close() } catch (ignored: Exception) {}
        try { fileOutputStream?.close() } catch (ignored: Exception) {}
        outputStream = null
        fileOutputStream = null
        currentOutputFile = null
        isRecording.set(false)
    }
}
