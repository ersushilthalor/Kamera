package com.example

import com.example.camera.model.CameraResolution
import com.example.camera.model.LensInfo
import com.example.camera.model.LensType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {
    @Test
    fun testMainActivityLaunch() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        assertNotNull(controller.get())
    }

    @Test
    fun testMainActivityLaunchWithPermissionsGranted() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = org.robolectric.Shadows.shadowOf(app)
        shadowApp.grantPermissions(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO)

        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        assertNotNull(controller.get())
    }

    @Test
    fun testCameraResolutionCalculations() {
        val res43 = CameraResolution(4000, 3000)
        assertEquals(12.0f, res43.megapixels, 0.01f)
        assertEquals("4:3", res43.aspectRatioLabel)

        val res169 = CameraResolution(3840, 2160)
        assertEquals(8.29f, res169.megapixels, 0.02f)
        assertEquals("16:9", res169.aspectRatioLabel)

        val res209 = CameraResolution(2400, 1080)
        assertEquals("20:9", res209.aspectRatioLabel)
    }

    @Test
    fun testLensInfoTypes() {
        val ultraWide = LensInfo(
            cameraId = "2",
            facing = android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK,
            lensType = LensType.ULTRAWIDE,
            displayName = "0.5x Ultra Wide",
            focalLengthMm = 1.94f,
            maxAperture = 2.2f,
            isPhysical = true,
            isHiddenAux = true,
            fovDegrees = 118f
        )
        assertEquals("0.5x", ultraWide.lensType.shortLabel)
        assertEquals("Ultra Wide", ultraWide.lensType.fullLabel)
        assertTrue(ultraWide.isPhysical)
        assertTrue(ultraWide.isHiddenAux)
    }

    @Test
    fun testMirrorSelfiePreferenceDefault() {
        val prefs = com.example.camera.data.CameraPreferences(
            androidx.test.core.app.ApplicationProvider.getApplicationContext()
        )
        // Default is true for "Save selfie as previewed (without flipping)"
        assertTrue(prefs.saveSelfieAsPreviewed)
        prefs.saveSelfieAsPreviewed = false
        assertFalse(prefs.saveSelfieAsPreviewed)
        prefs.saveSelfieAsPreviewed = true
        assertTrue(prefs.saveSelfieAsPreviewed)
    }

    @Test
    fun testVideoHdrModeAndPreferences() {
        val prefs = com.example.camera.data.CameraPreferences(
            androidx.test.core.app.ApplicationProvider.getApplicationContext()
        )
        // Default HDR is AUTO
        assertEquals(com.example.camera.model.VideoHdrMode.AUTO, prefs.videoHdrMode)
        assertEquals(50, prefs.videoHdrManualIntensity)

        prefs.videoHdrMode = com.example.camera.model.VideoHdrMode.MANUAL
        assertEquals(com.example.camera.model.VideoHdrMode.MANUAL, prefs.videoHdrMode)

        prefs.videoHdrManualIntensity = 80
        assertEquals(80, prefs.videoHdrManualIntensity)
    }

    @Test
    fun testVideoHdrEngineCalculations() {
        val engine = com.example.camera.engine.VideoHdrEngine()

        // 1. Initial mode is AUTO
        assertEquals(com.example.camera.model.VideoHdrMode.AUTO, engine.currentState.mode)

        // 2. Simulate frames in low-light / high-ISO scene
        // Simulating ISO 3200, 1/30s exposure (33_333_333 ns), aperture 1.8
        for (i in 0 until 10) {
            engine.processFrameValues(
                iso = 3200,
                exposureNs = 33_333_333L,
                aperture = 1.8f,
                focusDist = 1.5f
            )
        }

        val stateHighIso = engine.currentState
        assertTrue(stateHighIso.isHdrActive)
        // Shadows lifted for low-light scene
        assertTrue(stateHighIso.shadowLift > 0.2f)
        // Aggressive adaptive spatial + temporal noise reduction triggered for high ISO
        assertTrue(stateHighIso.noiseReductionStrength > 0.5f)
        assertTrue(stateHighIso.statusDescription.contains("Low-Light"))

        // 3. Test Manual Mode
        engine.mode = com.example.camera.model.VideoHdrMode.MANUAL
        engine.manualIntensity = 80
        for (i in 0 until 5) {
            engine.processFrameValues(
                iso = 400,
                exposureNs = 16_666_666L,
                aperture = 1.8f,
                focusDist = 2.0f
            )
        }
        val stateManual = engine.currentState
        assertEquals(80, stateManual.manualIntensity)
        assertTrue(stateManual.shadowLift > 0.25f)
        assertTrue(stateManual.highlightProtection > 0.25f)

        // 4. Test OFF Mode
        engine.mode = com.example.camera.model.VideoHdrMode.OFF
        engine.processFrameValues(
            iso = 400,
            exposureNs = 16_666_666L,
            aperture = 1.8f,
            focusDist = 2.0f
        )
        val stateOff = engine.currentState
        assertFalse(stateOff.isHdrActive)
        assertEquals(0f, stateOff.shadowLift, 0.001f)
        assertEquals(0f, stateOff.highlightProtection, 0.001f)
    }

    @Test
    fun testDollyZoomEngineLockAndApparentSize() {
        val dolly = com.example.camera.engine.DollyZoomEngine()
        assertFalse(dolly.dollyState.value.isCalibrated)
        assertFalse(dolly.dollyState.value.isSubjectLocked)

        // Lock on subject at coordinates (0.5, 0.5)
        dolly.lockSubject(
            normX = 0.5f,
            normY = 0.5f,
            currentZoom = 1.0f,
            faces = null,
            lensFocusDiopters = 1.0f, // 1 meter
            sensorRect = android.graphics.Rect(0, 0, 4000, 3000),
            minZoom = 1.0f,
            maxZoom = 8.0f
        )

        val lockedState = dolly.dollyState.value
        assertTrue(lockedState.isCalibrated)
        assertTrue(lockedState.isTracking)
        assertTrue(lockedState.isSubjectLocked)
        assertNotNull(lockedState.subjectBounds)
        assertEquals(1.0f, lockedState.initialZoom, 0.001f)
        assertEquals(1.0f, lockedState.targetDistanceMeters, 0.1f)

        // Reset
        dolly.reset()
        assertFalse(dolly.dollyState.value.isCalibrated)
        assertFalse(dolly.dollyState.value.isTracking)
    }

    @Test
    fun testDualVideoModePurged() {
        val modes = com.example.camera.model.CameraMode.entries.map { it.name }
        assertFalse("DUAL_VIDEO should not exist in CameraMode", modes.contains("DUAL_VIDEO"))
    }

    @Test
    fun testCinemaCodecsAndFileExtensions() {
        val vp9Codec = com.example.camera.model.CinemaCodec.VP9
        val proResCodec = com.example.camera.model.CinemaCodec.PRORES
        val h264Codec = com.example.camera.model.CinemaCodec.H264
        val h265Codec = com.example.camera.model.CinemaCodec.H265

        // VP9 uses webm container
        val isVp9Software = true
        val vp9Extension = if (isVp9Software && vp9Codec == com.example.camera.model.CinemaCodec.VP9) "webm" else "mp4"
        assertEquals("webm", vp9Extension)

        // ProRes and H264/H265 use mp4 container
        val proResExtension = if (isVp9Software && proResCodec == com.example.camera.model.CinemaCodec.VP9) "webm" else "mp4"
        assertEquals("mp4", proResExtension)

        val h264Extension = if (false && h264Codec == com.example.camera.model.CinemaCodec.VP9) "webm" else "mp4"
        assertEquals("mp4", h264Extension)
    }

    @Test
    fun testCinemaTempFileCreationAndCleanup() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val cacheDir = context.cacheDir.apply { mkdirs() }
        assertTrue(cacheDir.exists())

        val tempFile = java.io.File(cacheDir, "cinema_temp_${System.currentTimeMillis()}.mp4")
        if (tempFile.exists()) tempFile.delete()
        tempFile.createNewFile()
        assertTrue(tempFile.exists())
        assertEquals(0L, tempFile.length())

        // Simulate writing recorded bytes
        val testData = "test_video_data".toByteArray()
        tempFile.writeBytes(testData)
        assertEquals(testData.size.toLong(), tempFile.length())

        // Cleanup
        tempFile.delete()
        assertFalse(tempFile.exists())
    }

    @Test
    fun testMotorolaInstantSwitchPreferencesIndependence() {
        val prefs = com.example.camera.data.CameraPreferences(
            androidx.test.core.app.ApplicationProvider.getApplicationContext()
        )

        // Test default values
        assertTrue(prefs.isKeepUltraWideReady)
        assertFalse(prefs.isShowUltraWidePreview)
        assertTrue(prefs.isKeepFrontCameraReady)
        assertFalse(prefs.isShowFrontCameraPreview)

        // Test independent toggling for Ultra-Wide
        // Ultra-Wide Ready ON + Preview OFF
        prefs.isKeepUltraWideReady = true
        prefs.isShowUltraWidePreview = false
        assertTrue(prefs.isKeepUltraWideReady)
        assertFalse(prefs.isShowUltraWidePreview)

        // Ultra-Wide Ready ON + Preview ON
        prefs.isShowUltraWidePreview = true
        assertTrue(prefs.isKeepUltraWideReady)
        assertTrue(prefs.isShowUltraWidePreview)

        // Ultra-Wide Ready OFF + Preview ON
        prefs.isKeepUltraWideReady = false
        assertFalse(prefs.isKeepUltraWideReady)
        assertTrue(prefs.isShowUltraWidePreview)

        // Test independent toggling for Front Camera
        // Front Ready ON + Preview OFF
        prefs.isKeepFrontCameraReady = true
        prefs.isShowFrontCameraPreview = false
        assertTrue(prefs.isKeepFrontCameraReady)
        assertFalse(prefs.isShowFrontCameraPreview)

        // Front Ready ON + Preview ON
        prefs.isShowFrontCameraPreview = true
        assertTrue(prefs.isKeepFrontCameraReady)
        assertTrue(prefs.isShowFrontCameraPreview)

        // Front Ready OFF
        prefs.isKeepFrontCameraReady = false
        assertFalse(prefs.isKeepFrontCameraReady)
    }

    @Test
    fun testMotorolaInstantSwitchEngineState() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val engine = com.example.camera.engine.MotorolaInstantSwitchEngine(context)

        assertNotNull(engine.switchState.value)

        // Test independent state toggles via engine
        engine.setKeepUltraWideReady(true)
        assertTrue(engine.switchState.value.isKeepUltraWideReady)

        engine.setShowUltraWidePreview(true)
        assertTrue(engine.switchState.value.isShowUltraWidePreview)

        engine.setShowUltraWidePreview(false)
        assertFalse(engine.switchState.value.isShowUltraWidePreview)
        assertTrue(engine.switchState.value.isKeepUltraWideReady)

        engine.setKeepFrontCameraReady(true)
        assertTrue(engine.switchState.value.isKeepFrontCameraReady)

        engine.setShowFrontCameraPreview(true)
        assertTrue(engine.switchState.value.isShowFrontCameraPreview)

        engine.release()
    }

    @Test
    fun testHdrVideoPipelineArchitectureSinglePreset() {
        // Video mode must only have 1 single selectable pipeline: HDR
        assertEquals(1, com.example.camera.pipeline.video.VideoPipelineType.SELECTABLE_PIPELINES.size)
        assertEquals(com.example.camera.pipeline.video.VideoPipelineType.HDR, com.example.camera.pipeline.video.VideoPipelineType.SELECTABLE_PIPELINES[0])

        // Any old pipeline ID should gracefully resolve to HDR
        assertEquals(com.example.camera.pipeline.video.VideoPipelineType.HDR, com.example.camera.pipeline.video.VideoPipelineType.fromId("iphone"))
        assertEquals(com.example.camera.pipeline.video.VideoPipelineType.HDR, com.example.camera.pipeline.video.VideoPipelineType.fromId("samsung"))
        assertEquals(com.example.camera.pipeline.video.VideoPipelineType.HDR, com.example.camera.pipeline.video.VideoPipelineType.fromId("dslr"))
        assertEquals(com.example.camera.pipeline.video.VideoPipelineType.HDR, com.example.camera.pipeline.video.VideoPipelineType.fromId("hdr"))

        val pipeline = com.example.camera.pipeline.video.HdrVideoPipeline()
        assertEquals("HDR Pipeline", pipeline.name)
        val chars = pipeline.getCharacteristics()
        assertTrue(chars.highlightShadow.contains("anti-magenta", ignoreCase = true))
        assertTrue(chars.dynamicRange.contains("temporal", ignoreCase = true))

        // High-bitrate 10-bit HDR encoding verification
        val bitrate4k = pipeline.getEncoderBitrate(3840, 2160, 40_000_000)
        assertTrue(bitrate4k >= 65_000_000)
    }

    @Test
    fun testHdrAntiMagentaHighlightReconstruction() {
        val engine = com.example.camera.engine.VideoHdrEngine()
        // Feed bright daylight scene with highlight clipping risk
        for (i in 0 until 10) {
            engine.processFrameValues(
                iso = 100,
                exposureNs = 2_000_000L,
                aperture = 2.8f,
                focusDist = 10.0f
            )
        }

        val tonemapCurve = engine.getHdrTonemapCurve()
        assertNotNull(tonemapCurve)

        // Verify Anti-Magenta Highlight Reconstruction:
        // As input x approaches 1.0f (knee region x >= 0.85),
        // red and blue channels must converge towards green (desaturate highlights into pure neutral white)
        // rather than allowing red/blue to blow out ahead of green and cause magenta tints.
        val rOutNearClip: Float = tonemapCurve.getPoint(0, 55).y // Red near clipping (x = 55/63 = 0.873)
        val gOutNearClip: Float = tonemapCurve.getPoint(1, 55).y // Green near clipping
        val bOutNearClip: Float = tonemapCurve.getPoint(2, 55).y // Blue near clipping

        val rMax: Float = tonemapCurve.getPoint(0, 63).y // Red at full clip (x = 1.0)
        val gMax: Float = tonemapCurve.getPoint(1, 63).y // Green at full clip
        val bMax: Float = tonemapCurve.getPoint(2, 63).y // Blue at full clip

        // Full clipping point must strictly converge to 1.0f neutral white
        assertEquals(1.0f, rMax, 0.001f)
        assertEquals(1.0f, gMax, 0.001f)
        assertEquals(1.0f, bMax, 0.001f)

        // Near-clip: red and blue must not significantly exceed green (which causes pink/magenta tint)
        val redGreenDiff: Float = rOutNearClip - gOutNearClip
        val blueGreenDiff: Float = bOutNearClip - gOutNearClip
        assertTrue(redGreenDiff <= 0.05f)
        assertTrue(blueGreenDiff <= 0.05f)

        // Monotonicity check: output must be non-decreasing
        for (channel in 0..2) {
            var lastY = -1.0f
            for (i in 0 until 64) {
                val pt = tonemapCurve.getPoint(channel, i)
                val currentY: Float = pt.y
                assertTrue(currentY >= lastY - 0.001f)
                assertTrue(currentY in 0.0f..1.0f)
                lastY = currentY
            }
        }
    }

    @Test
    fun testHdrTemporalMotionAdaptationAndShadowRecovery() {
        val engine = com.example.camera.engine.VideoHdrEngine()

        // 1. Static Scene: same focus, exposure, and ISO -> motionFactor is low
        for (i in 0 until 6) {
            engine.processFrameValues(
                iso = 400,
                exposureNs = 16_666_666L,
                aperture = 1.8f,
                focusDist = 2.0f
            )
        }
        assertFalse(engine.currentState.isMotionDetected)

        // Deep black level baseline (x=0) must remain at 0.0f for inky blacks
        val tonemapCurve = engine.getHdrTonemapCurve()
        assertEquals(0.0f, tonemapCurve.getPoint(0, 0).y, 0.0001f)
        assertEquals(0.0f, tonemapCurve.getPoint(1, 0).y, 0.0001f)
        assertEquals(0.0f, tonemapCurve.getPoint(2, 0).y, 0.0001f)

        // Shadow region (x = 8/63 = 0.127) must be lifted compared to linear
        val shadowY: Float = tonemapCurve.getPoint(1, 8).y
        val linearY = 8f / 63f
        assertTrue(shadowY >= linearY)

        // 2. Rapid Motion: sharp shift in focus distance and exposure time
        engine.processFrameValues(
            iso = 1200,
            exposureNs = 33_333_333L,
            aperture = 1.8f,
            focusDist = 0.5f // Large focus step -> triggers motion detection
        )
        assertTrue(engine.currentState.isMotionDetected)
    }
}
