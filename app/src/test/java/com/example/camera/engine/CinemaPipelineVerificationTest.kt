package com.example.camera.engine

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.TonemapCurve
import androidx.test.core.app.ApplicationProvider
import com.example.camera.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CinemaPipelineVerificationTest {

    private lateinit var context: Context
    private lateinit var cinemaEngine: CinemaEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        cinemaEngine = CinemaEngine(context)
    }

    @Test
    fun testRec2020NaturalContrastNoWashedOutPedestal() {
        val config = CinemaConfig(
            colorProfile = CinemaColorProfile.REC_2020,
            shadows = 0f,
            highlights = 0f,
            contrast = 0f,
            exposure = 0f
        )
        cinemaEngine.updateConfig(config)
        val curve = cinemaEngine.getTonemapCurve()
        val count = curve.getPointCount(TonemapCurve.CHANNEL_RED)

        // Verify black level: input 0.0f should map to 0.0f without artificial milky pedestal (>0.05f)
        val blackPoint = curve.getPoint(TonemapCurve.CHANNEL_RED, 0)
        assertEquals(0.0f, blackPoint.x, 0.001f)
        assertTrue("Rec.2020 black point must be true deep black (<=0.01f), got ${blackPoint.y}", blackPoint.y <= 0.01f)

        // Verify middle-grey (x=0.5, y ≈ 0.71 on BT.2020 0.45 power law) is in natural photographic range
        val midPoint = curve.getPoint(TonemapCurve.CHANNEL_RED, count / 2)
        assertTrue("Rec.2020 mid-tone should have natural filmic gamma, got ${midPoint.y}", midPoint.y in 0.50f..0.80f)

        // Verify highlights reach full range
        val whitePoint = curve.getPoint(TonemapCurve.CHANNEL_RED, count - 1)
        assertEquals(1.0f, whitePoint.x, 0.001f)
        assertEquals(1.0f, whitePoint.y, 0.01f)
    }

    @Test
    fun testExposureControlsInCinemaPipeline() {
        val baseConfig = CinemaConfig(
            colorProfile = CinemaColorProfile.REC_709,
            exposure = 0.0f
        )
        cinemaEngine.updateConfig(baseConfig)
        val baseCurve = cinemaEngine.getTonemapCurve()
        val count = baseCurve.getPointCount(TonemapCurve.CHANNEL_RED)
        val baseMid = baseCurve.getPoint(TonemapCurve.CHANNEL_RED, count / 2).y

        // Positive exposure should lift the mid-tones
        cinemaEngine.updateConfig(baseConfig.copy(exposure = 0.5f))
        val positiveExpCurve = cinemaEngine.getTonemapCurve()
        val positiveMid = positiveExpCurve.getPoint(TonemapCurve.CHANNEL_RED, count / 2).y
        assertTrue("Positive exposure must increase mid-tones ($positiveMid > $baseMid)", positiveMid > baseMid)

        // Negative exposure should lower the mid-tones
        cinemaEngine.updateConfig(baseConfig.copy(exposure = -0.5f))
        val negativeExpCurve = cinemaEngine.getTonemapCurve()
        val negativeMid = negativeExpCurve.getPoint(TonemapCurve.CHANNEL_RED, count / 2).y
        assertTrue("Negative exposure must decrease mid-tones ($negativeMid < $baseMid)", negativeMid < baseMid)
    }

    @Test
    fun testNoiseReductionLevelsInCaptureRequest() {
        val constructor = CaptureRequest.Builder::class.java.getDeclaredConstructor()
        constructor.isAccessible = true

        // CinemaNoiseReduction.OFF -> NOISE_REDUCTION_MODE_OFF
        val builderOff = constructor.newInstance()
        cinemaEngine.updateConfig(CinemaConfig(noiseReduction = CinemaNoiseReduction.OFF))
        cinemaEngine.applyToCaptureRequest(builderOff)
        assertEquals(CaptureRequest.NOISE_REDUCTION_MODE_OFF, builderOff.get(CaptureRequest.NOISE_REDUCTION_MODE))

        // CinemaNoiseReduction.LOW -> NOISE_REDUCTION_MODE_MINIMAL (or FAST if minimal unavailable)
        val builderLow = constructor.newInstance()
        cinemaEngine.updateConfig(CinemaConfig(noiseReduction = CinemaNoiseReduction.LOW))
        cinemaEngine.applyToCaptureRequest(builderLow)
        assertEquals(CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL, builderLow.get(CaptureRequest.NOISE_REDUCTION_MODE))

        // CinemaNoiseReduction.HIGH -> NOISE_REDUCTION_MODE_HIGH_QUALITY
        val builderHigh = constructor.newInstance()
        cinemaEngine.updateConfig(CinemaConfig(noiseReduction = CinemaNoiseReduction.HIGH))
        cinemaEngine.applyToCaptureRequest(builderHigh)
        assertEquals(CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY, builderHigh.get(CaptureRequest.NOISE_REDUCTION_MODE))
    }

    @Test
    fun testCinematicLutsBakeAndColorSeparation() {
        val configUnbaked = CinemaConfig(
            colorProfile = CinemaColorProfile.FLAT_LOG,
            selectedLut = CinematicLut.TEAL_ORANGE,
            isBakeLutToOutput = false
        )
        assertFalse("LUT should not be baked into file if isBakeLutToOutput is false", configUnbaked.shouldBakeLut)

        val configBaked = CinemaConfig(
            colorProfile = CinemaColorProfile.FLAT_LOG,
            selectedLut = CinematicLut.TEAL_ORANGE,
            isBakeLutToOutput = true
        )
        assertTrue("LUT must be baked when selectedLut != NONE and isBakeLutToOutput is true", configBaked.shouldBakeLut)

        // Verify Hollywood cinematic LUT values are properly calibrated
        val tealOrange = CinematicLut.TEAL_ORANGE
        assertTrue(tealOrange.contrast > 1.0f)
        assertTrue(tealOrange.saturation > 1.0f)

        val warmCinema = CinematicLut.WARM_CINEMA
        assertTrue("Warm Cinema has filmic contrast", warmCinema.contrast > 1.0f)
        assertTrue("Warm Cinema has amber warmth offset", warmCinema.warmCoolOffset > 0f)

        val mutedFilm = CinematicLut.MUTED_FILM
        assertTrue("Muted Film has lifted shadow toe", mutedFilm.shadowToe > 0f)
        assertTrue("Muted Film has subdued saturation", mutedFilm.saturation < 1.0f)
    }

    @Test
    fun testWashedOutSliderReducesFlatPedestalAndRestoresContrast() {
        val flatConfig = CinemaConfig(
            colorProfile = CinemaColorProfile.FLAT_LOG,
            washedOut = 0.0f
        )
        cinemaEngine.updateConfig(flatConfig)
        val flatCurve = cinemaEngine.getTonemapCurve()
        val flatBlack = flatCurve.getPoint(TonemapCurve.CHANNEL_RED, 0).y

        // Washed out slider at 1.0 should significantly lower black level / pedestal to eliminate hazy look
        val punchyConfig = CinemaConfig(
            colorProfile = CinemaColorProfile.FLAT_LOG,
            washedOut = 1.0f
        )
        cinemaEngine.updateConfig(punchyConfig)
        val punchyCurve = cinemaEngine.getTonemapCurve()
        val punchyBlack = punchyCurve.getPoint(TonemapCurve.CHANNEL_RED, 0).y

        assertTrue("Washed-out slider must reduce shadow pedestal ($punchyBlack < $flatBlack)", punchyBlack < flatBlack)
    }

    @Test
    fun testNativeColorProfileUsesNaturalOetf() {
        val nativeConfig = CinemaConfig(
            colorProfile = CinemaColorProfile.NATIVE,
            washedOut = 0f
        )
        cinemaEngine.updateConfig(nativeConfig)
        val curve = cinemaEngine.getTonemapCurve()
        val count = curve.getPointCount(TonemapCurve.CHANNEL_RED)

        // Native black point must be 0.0 without any lifted pedestal
        val blackPoint = curve.getPoint(TonemapCurve.CHANNEL_RED, 0)
        assertEquals(0.0f, blackPoint.y, 0.005f)

        // Native middle-grey should be standard Rec.709 OETF (~0.73)
        val midPoint = curve.getPoint(TonemapCurve.CHANNEL_RED, count / 2)
        assertTrue("Native mid-tone should match standard photographic gamma", midPoint.y in 0.65f..0.80f)
    }

    @Test
    fun testProRes10BitSoftwareRecorderPipeline() {
        val recorder = CinemaSoftwareRecordingEngine(context)
        val tempDest = File(context.cacheDir, "test_prores_10bit.mp4")

        try {
            val surface = recorder.startRecording(
                destFile = tempDest,
                width = 1920,
                height = 1080,
                fps = 24,
                bitrate = 90_000_000,
                codec = CinemaCodec.PRORES,
                bitDepth = LogBitDepth.BIT_10,
                isAudioEnabled = false
            )
            assertNotNull("Surface should be generated for ProRes 10-bit recording", surface)

            // Stopping recording should finalize the MP4 container
            val outFile = recorder.stopRecording()
            assertNotNull("Output file should be returned after stopping", outFile)
            assertTrue("Destination file should exist", outFile?.exists() == true)
        } finally {
            try { tempDest.delete() } catch (ignored: Exception) {}
        }
    }

    @Test
    fun test8BitStandardRecordingPipeline() {
        val recorder = CinemaSoftwareRecordingEngine(context)
        val tempDest = File(context.cacheDir, "test_standard_8bit.mp4")

        try {
            val surface = recorder.startRecording(
                destFile = tempDest,
                width = 1280,
                height = 720,
                fps = 30,
                bitrate = 20_000_000,
                codec = CinemaCodec.H264,
                bitDepth = LogBitDepth.BIT_8,
                isAudioEnabled = false
            )
            assertNotNull("Surface should be created for 8-bit standard recording", surface)
            val outFile = recorder.stopRecording()
            assertNotNull(outFile)
        } finally {
            try { tempDest.delete() } catch (ignored: Exception) {}
        }
    }
}
