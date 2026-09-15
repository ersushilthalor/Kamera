package com.example.camera.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import com.example.camera.data.CameraPreferences
import com.example.camera.model.BackgroundCameraStatus
import com.example.camera.model.LensInfo
import com.example.camera.model.LensType
import com.example.camera.model.MotorolaInstantSwitchState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "MotorolaSwitchEngine"

/**
 * Motorola-optimized Instant Camera Switching Engine.
 *
 * Keeps the Ultra-Wide (and/or Front camera) prepared quietly in the background
 * while using the 1× Main camera, enabling zero-delay instant switching.
 *
 * Features:
 * - Independent "Keep Ready" and "Little Preview" controls for Ultra-Wide and Front camera.
 * - Live Picture-in-Picture preview rendering onto TextureViews when Little Preview is enabled.
 * - Low-power, quiet background stream when Little Preview is disabled (sensor warm & 3A converged).
 * - Automatic hardware detection for Motorola Edge, Razr, and Moto G series devices.
 * - Crash-free automatic fallback to Motorola Turbo Fast Switch if concurrent streaming is
 *   unsupported on specific firmware/hardware combinations.
 */
class MotorolaInstantSwitchEngine(private val context: Context) {

    private val cameraManager: CameraManager? =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private val preferences = CameraPreferences(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Motorola hardware detection
    val isMotorolaDevice: Boolean by lazy {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        val d = Build.DEVICE.lowercase()
        val p = Build.PRODUCT.lowercase()
        m.contains("motorola") || b.contains("motorola") || b.contains("moto") ||
                d.contains("motorola") || d.contains("moto") || p.contains("moto")
    }

    // Concurrent camera HAL capability
    private var isConcurrentHardwareSupported = true

    // Background Thread & Handler
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    // Background Camera Device & Session
    private var backgroundCameraDevice: CameraDevice? = null
    private var backgroundCaptureSession: CameraCaptureSession? = null
    private var activeBackgroundLens: LensInfo? = null

    // Surfaces
    private var offscreenSurfaceTexture: SurfaceTexture? = null
    private var offscreenSurface: Surface? = null

    // Live Little Preview SurfaceTextures (supplied by UI)
    private var ultraWidePreviewSurfaceTexture: SurfaceTexture? = null
    private var ultraWidePreviewSurface: Surface? = null
    private var frontPreviewSurfaceTexture: SurfaceTexture? = null
    private var frontPreviewSurface: Surface? = null

    // State Flow
    private val _switchState = MutableStateFlow(
        MotorolaInstantSwitchState(
            isKeepUltraWideReady = preferences.isKeepUltraWideReady,
            isShowUltraWidePreview = preferences.isShowUltraWidePreview,
            isKeepFrontCameraReady = preferences.isKeepFrontCameraReady,
            isShowFrontCameraPreview = preferences.isShowFrontCameraPreview,
            isMotorolaDevice = isMotorolaDevice,
            isConcurrentHardwareSupported = true,
            statusMessage = if (isMotorolaDevice) {
                "Motorola Multi-Sensor Engine Active"
            } else {
                "Instant Multi-Camera Ready"
            }
        )
    )
    val switchState: StateFlow<MotorolaInstantSwitchState> = _switchState.asStateFlow()

    // Primary Camera context
    private var currentPrimaryLens: LensInfo? = null
    private var availableLenses: List<LensInfo> = emptyList()

    init {
        checkConcurrentHardwareSupport()
        startBackgroundThread()
    }

    private fun checkConcurrentHardwareSupport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && cameraManager != null) {
            try {
                val concurrentSets = cameraManager.concurrentCameraIds
                Log.d(TAG, "Motorola HAL Concurrent camera sets: $concurrentSets")
                // Even if empty on some OEM HALs, Motorola devices often support concurrent open
                // We keep isConcurrentHardwareSupported = true initially and catch any HAL contention
            } catch (t: Throwable) {
                Log.w(TAG, "Error checking concurrent camera IDs", t)
            }
        }
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("MotorolaInstantSwitchThread").apply {
                start()
                backgroundHandler = Handler(looper)
            }
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join(500)
            backgroundThread = null
            backgroundHandler = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Configuration & Settings Toggles (Strictly Independent Behavior)
    // -----------------------------------------------------------------------------------------

    fun setKeepUltraWideReady(enabled: Boolean) {
        preferences.isKeepUltraWideReady = enabled
        _switchState.value = _switchState.value.copy(isKeepUltraWideReady = enabled)
        refreshBackgroundCameraState()
    }

    fun setShowUltraWidePreview(enabled: Boolean) {
        preferences.isShowUltraWidePreview = enabled
        _switchState.value = _switchState.value.copy(isShowUltraWidePreview = enabled)
        refreshBackgroundCameraState()
    }

    fun setKeepFrontCameraReady(enabled: Boolean) {
        preferences.isKeepFrontCameraReady = enabled
        _switchState.value = _switchState.value.copy(isKeepFrontCameraReady = enabled)
        refreshBackgroundCameraState()
    }

    fun setShowFrontCameraPreview(enabled: Boolean) {
        preferences.isShowFrontCameraPreview = enabled
        _switchState.value = _switchState.value.copy(isShowFrontCameraPreview = enabled)
        refreshBackgroundCameraState()
    }

    /**
     * Set the preview SurfaceTexture for Ultra-Wide Little Preview.
     */
    fun setUltraWidePreviewSurfaceTexture(texture: SurfaceTexture?) {
        ultraWidePreviewSurfaceTexture = texture
        ultraWidePreviewSurface?.release()
        ultraWidePreviewSurface = if (texture != null) Surface(texture) else null
        refreshBackgroundCameraState()
    }

    /**
     * Set the preview SurfaceTexture for Front Camera Little Preview.
     */
    fun setFrontPreviewSurfaceTexture(texture: SurfaceTexture?) {
        frontPreviewSurfaceTexture = texture
        frontPreviewSurface?.release()
        frontPreviewSurface = if (texture != null) Surface(texture) else null
        refreshBackgroundCameraState()
    }

    /**
     * Update the known list of lenses and the currently active primary lens.
     */
    fun updatePrimaryLens(lens: LensInfo?, allLenses: List<LensInfo>) {
        currentPrimaryLens = lens
        availableLenses = allLenses
        refreshBackgroundCameraState()
    }

    // -----------------------------------------------------------------------------------------
    // Target Selection & Lifecycle Management
    // -----------------------------------------------------------------------------------------

    /**
     * Evaluates which camera should be running in the background based on:
     * - Current primary lens
     * - isKeepUltraWideReady
     * - isKeepFrontCameraReady
     * - Device capabilities
     */
    @Synchronized
    private fun refreshBackgroundCameraState() {
        val primary = currentPrimaryLens ?: return
        val currentState = _switchState.value

        // If concurrent streaming is unsupported on this specific model/firmware,
        // use Turbo Fast Handover instead of trying to open secondary camera
        if (!isConcurrentHardwareSupported) {
            _switchState.value = currentState.copy(
                ultraWideStatus = if (currentState.isKeepUltraWideReady) BackgroundCameraStatus.FALLBACK_TURBO else BackgroundCameraStatus.OFF,
                frontStatus = if (currentState.isKeepFrontCameraReady) BackgroundCameraStatus.FALLBACK_TURBO else BackgroundCameraStatus.OFF,
                statusMessage = "Motorola Turbo Fast Handover Active"
            )
            closeBackgroundCamera()
            return
        }

        // Determine what background lens we desire:
        // Priority 1: If primary is Back (1x Main or Telephoto), and user wants Ultra-Wide Ready
        val ultraWideLens = availableLenses.firstOrNull { it.lensType == LensType.ULTRAWIDE }
        val frontLens = availableLenses.firstOrNull { it.facing == CameraCharacteristics.LENS_FACING_FRONT }

        val isPrimaryRear = primary.facing == CameraCharacteristics.LENS_FACING_BACK
        val isPrimaryMain1x = isPrimaryRear && primary.lensType == LensType.WIDE

        var desiredLens: LensInfo? = null
        var isUltraWideTarget = false

        if (isPrimaryMain1x && currentState.isKeepUltraWideReady && ultraWideLens != null) {
            desiredLens = ultraWideLens
            isUltraWideTarget = true
        } else if (isPrimaryRear && currentState.isKeepFrontCameraReady && frontLens != null) {
            desiredLens = frontLens
            isUltraWideTarget = false
        } else if (!isPrimaryRear && currentState.isKeepFrontCameraReady && ultraWideLens != null && currentState.isKeepUltraWideReady) {
            // When front camera is primary, keep ultra-wide or main rear warm
            desiredLens = ultraWideLens
            isUltraWideTarget = true
        }

        // If ultra-wide is a zoom preset of the SAME primary camera (e.g., zoom ratio 0.5x on Camera 0),
        // it is ALREADY running inside the primary camera session!
        if (isUltraWideTarget && desiredLens != null && desiredLens.cameraId == primary.cameraId) {
            val showPreview = currentState.isShowUltraWidePreview
            val status = if (showPreview) BackgroundCameraStatus.READY_PREVIEW else BackgroundCameraStatus.READY_QUIET
            _switchState.value = currentState.copy(
                ultraWideStatus = status,
                frontStatus = if (currentState.isKeepFrontCameraReady) BackgroundCameraStatus.READY_QUIET else BackgroundCameraStatus.OFF,
                activeStandbyLens = LensType.ULTRAWIDE,
                switchLatencyEstimateMs = 0,
                statusMessage = "Integrated Ultra-Wide Ready (0ms Instant Switch)"
            )
            closeBackgroundCamera()
            return
        }

        // If no background camera is desired, close background camera and update state
        if (desiredLens == null) {
            closeBackgroundCamera()
            _switchState.value = currentState.copy(
                ultraWideStatus = BackgroundCameraStatus.OFF,
                frontStatus = BackgroundCameraStatus.OFF,
                activeStandbyLens = null,
                statusMessage = "Motorola Standby Idle"
            )
            return
        }

        // If desired lens is already running in background:
        if (backgroundCameraDevice != null && activeBackgroundLens?.cameraId == desiredLens.cameraId) {
            updateBackgroundSession(isUltraWideTarget)
            return
        }

        // Otherwise, open desired background camera
        openBackgroundCamera(desiredLens, isUltraWideTarget)
    }

    @SuppressLint("MissingPermission")
    private fun openBackgroundCamera(lens: LensInfo, isUltraWide: Boolean) {
        val mgr = cameraManager ?: return
        val handler = backgroundHandler ?: return

        closeBackgroundCamera()

        _switchState.value = _switchState.value.copy(
            ultraWideStatus = if (isUltraWide) BackgroundCameraStatus.PREPARING else _switchState.value.ultraWideStatus,
            frontStatus = if (!isUltraWide) BackgroundCameraStatus.PREPARING else _switchState.value.frontStatus,
            statusMessage = "Preparing ${if (isUltraWide) "Ultra-Wide" else "Front"} in background..."
        )

        try {
            mgr.openCamera(lens.cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.d(TAG, "Background camera opened successfully: ${camera.id}")
                    backgroundCameraDevice = camera
                    activeBackgroundLens = lens
                    createBackgroundCaptureSession(camera, isUltraWide)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Background camera disconnected: ${camera.id}")
                    camera.close()
                    if (backgroundCameraDevice == camera) {
                        backgroundCameraDevice = null
                        activeBackgroundLens = null
                    }
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.w(TAG, "Background camera open error: $error (Camera ${camera.id})")
                    try { camera.close() } catch (ignored: Throwable) {}
                    if (backgroundCameraDevice == camera) {
                        backgroundCameraDevice = null
                        activeBackgroundLens = null
                    }

                    // If HAL indicates max cameras in use or device busy, gracefully fallback
                    // to Turbo Fast Handover instead of failing or retrying in a loop
                    if (error == ERROR_MAX_CAMERAS_IN_USE ||
                        error == ERROR_CAMERA_IN_USE ||
                        error == ERROR_CAMERA_DEVICE) {
                        Log.i(TAG, "Device does not support simultaneous background camera. Falling back to Motorola Turbo Fast Handover.")
                        isConcurrentHardwareSupported = false
                        _switchState.value = _switchState.value.copy(
                            isConcurrentHardwareSupported = false,
                            ultraWideStatus = if (_switchState.value.isKeepUltraWideReady) BackgroundCameraStatus.FALLBACK_TURBO else BackgroundCameraStatus.OFF,
                            frontStatus = if (_switchState.value.isKeepFrontCameraReady) BackgroundCameraStatus.FALLBACK_TURBO else BackgroundCameraStatus.OFF,
                            statusMessage = "Motorola Turbo Fast Handover (Hardware Concurrent Unsupported)"
                        )
                    } else {
                        _switchState.value = _switchState.value.copy(
                            ultraWideStatus = if (isUltraWide) BackgroundCameraStatus.UNAVAILABLE else _switchState.value.ultraWideStatus,
                            frontStatus = if (!isUltraWide) BackgroundCameraStatus.UNAVAILABLE else _switchState.value.frontStatus
                        )
                    }
                }
            }, handler)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to open background camera ${lens.cameraId}", t)
            isConcurrentHardwareSupported = false
            _switchState.value = _switchState.value.copy(
                isConcurrentHardwareSupported = false,
                ultraWideStatus = BackgroundCameraStatus.FALLBACK_TURBO,
                frontStatus = BackgroundCameraStatus.FALLBACK_TURBO,
                statusMessage = "Turbo Fast Handover Active"
            )
        }
    }

    /**
     * Creates or updates the capture session for the background camera.
     * Uses Little Preview surface if enabled and available; otherwise uses a lightweight
     * quiet offscreen surface so AE/AF remain converged.
     */
    private fun createBackgroundCaptureSession(camera: CameraDevice, isUltraWide: Boolean) {
        val handler = backgroundHandler ?: return
        val state = _switchState.value

        val wantLittlePreview = if (isUltraWide) state.isShowUltraWidePreview else state.isShowFrontCameraPreview
        val previewSurface = if (isUltraWide) ultraWidePreviewSurface else frontPreviewSurface

        val targetSurface: Surface
        val isUsingPreviewSurface: Boolean

        if (wantLittlePreview && previewSurface != null && previewSurface.isValid) {
            targetSurface = previewSurface
            isUsingPreviewSurface = true
        } else {
            // Create quiet offscreen SurfaceTexture (640x480) for low-power standby
            if (offscreenSurfaceTexture == null) {
                offscreenSurfaceTexture = SurfaceTexture(101).apply {
                    setDefaultBufferSize(640, 480)
                }
                offscreenSurface = Surface(offscreenSurfaceTexture)
            }
            targetSurface = offscreenSurface ?: return
            isUsingPreviewSurface = false
        }

        try {
            val surfaces = listOf(targetSurface)
            camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    backgroundCaptureSession = session
                    try {
                        val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(targetSurface)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                            // On Motorola, lower framerate during quiet standby to save battery & prevent thermal rise
                            if (!isUsingPreviewSurface) {
                                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, android.util.Range(15, 30))
                            }
                        }.build()

                        session.setRepeatingRequest(req, null, handler)

                        val status = if (isUsingPreviewSurface) {
                            BackgroundCameraStatus.READY_PREVIEW
                        } else {
                            BackgroundCameraStatus.READY_QUIET
                        }

                        _switchState.value = _switchState.value.copy(
                            ultraWideStatus = if (isUltraWide) status else _switchState.value.ultraWideStatus,
                            frontStatus = if (!isUltraWide) status else _switchState.value.frontStatus,
                            activeStandbyLens = if (isUltraWide) LensType.ULTRAWIDE else LensType.FRONT,
                            switchLatencyEstimateMs = if (isUsingPreviewSurface) 10 else 25,
                            statusMessage = if (isUsingPreviewSurface) {
                                "${if (isUltraWide) "Ultra-Wide" else "Front"} Live Preview Active"
                            } else {
                                "${if (isUltraWide) "Ultra-Wide" else "Front"} Ready in Background (Quiet)"
                            }
                        )
                        Log.d(TAG, "Background session running. Preview: $isUsingPreviewSurface")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting background repeating request", e)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.w(TAG, "Background capture session configuration failed")
                    _switchState.value = _switchState.value.copy(
                        ultraWideStatus = if (isUltraWide) BackgroundCameraStatus.FALLBACK_TURBO else _switchState.value.ultraWideStatus,
                        frontStatus = if (!isUltraWide) BackgroundCameraStatus.FALLBACK_TURBO else _switchState.value.frontStatus,
                        statusMessage = "Fast Normal Handover"
                    )
                }
            }, handler)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create background capture session", t)
        }
    }

    private fun updateBackgroundSession(isUltraWide: Boolean) {
        val camera = backgroundCameraDevice ?: return
        try {
            backgroundCaptureSession?.close()
            backgroundCaptureSession = null
        } catch (ignored: Throwable) {}
        createBackgroundCaptureSession(camera, isUltraWide)
    }

    /**
     * Handover of the background camera to become primary!
     * Returns the open CameraDevice if it matches the target lens, or null if not available.
     */
    @Synchronized
    fun handoffBackgroundCamera(targetLens: LensInfo): CameraDevice? {
        val bgDevice = backgroundCameraDevice ?: return null
        if (activeBackgroundLens?.cameraId != targetLens.cameraId) return null

        Log.i(TAG, "Instant Handover: Promoting background camera ${bgDevice.id} to primary!")

        // Detach session cleanly without closing hardware CameraDevice
        try {
            backgroundCaptureSession?.stopRepeating()
            backgroundCaptureSession?.close()
        } catch (ignored: Throwable) {}
        backgroundCaptureSession = null

        backgroundCameraDevice = null
        activeBackgroundLens = null

        return bgDevice
    }

    @Synchronized
    fun closeBackgroundCamera() {
        try {
            backgroundCaptureSession?.close()
            backgroundCaptureSession = null
        } catch (ignored: Throwable) {}

        try {
            backgroundCameraDevice?.close()
            backgroundCameraDevice = null
        } catch (ignored: Throwable) {}

        activeBackgroundLens = null
    }

    fun release() {
        closeBackgroundCamera()
        try {
            offscreenSurface?.release()
            offscreenSurface = null
            offscreenSurfaceTexture?.release()
            offscreenSurfaceTexture = null
        } catch (ignored: Throwable) {}
        stopBackgroundThread()
    }
}
