package com.example.camera.engine

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.example.camera.model.LensType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "StreamCompositor"

/**
 * Lightweight GPU / OpenGL ES 2.0 Viewfinder Compositor.
 *
 * Provides persistent Surface/SurfaceTexture pairs for both Main (1×) and Ultra-Wide (0.5×)
 * cameras, allowing concurrent streaming without session recreation.
 *
 * During a lens switch (1× ↔ 0.5×), the compositor switches which camera texture is
 * displayed in the main viewfinder in 1 render frame (< 16ms), eliminating:
 * - Camera device re-opening (openCamera)
 * - Capture session recreation (createCaptureSession)
 * - Preview Surface re-allocation
 * - 3A re-convergence delays
 *
 * Also routes the standby stream to the Little Preview overlay without requiring a secondary
 * camera session.
 */
class CameraStreamCompositor {

    // EGL objects
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglConfig: EGLConfig? = null
    private var dummyPbuffer: EGLSurface? = null

    // Output EGL Surfaces
    private var mainEglSurface: EGLSurface? = null
    private var littleEglSurface: EGLSurface? = null

    private var mainTargetSurface: Surface? = null
    private var mainWidth: Int = 1080
    private var mainHeight: Int = 1920

    private var littleTargetSurface: Surface? = null
    private var littleWidth: Int = 240
    private var littleHeight: Int = 320

    // Persistent Camera Streams
    var mainCameraSurfaceTexture: SurfaceTexture? = null
        private set
    var mainCameraSurface: Surface? = null
        private set

    var ultraWideCameraSurfaceTexture: SurfaceTexture? = null
        private set
    var ultraWideCameraSurface: Surface? = null
        private set

    // Texture IDs
    private var mainTexId: Int = 0
    private var ultraWideTexId: Int = 0

    // Transform matrices (populated by SurfaceTexture.getTransformMatrix)
    private val mainTexMatrix = FloatArray(16)
    private val ultraWideTexMatrix = FloatArray(16)

    // Active Displayed Lens
    @Volatile
    var activeLensType: LensType = LensType.WIDE
        private set

    // Standby Little Preview Visibility
    @Volatile
    var isLittlePreviewEnabled: Boolean = false

    // Frame availability flags and independent frame counters
    private val isMainFrameAvailable = AtomicBoolean(false)
    private val isUltraWideFrameAvailable = AtomicBoolean(false)
    private val mainFrameCount = AtomicLong(0L)
    private val ultraWideFrameCount = AtomicLong(0L)
    private val lastMainTimestampNs = AtomicLong(0L)
    private val lastUltraWideTimestampNs = AtomicLong(0L)

    // Switch Verification
    @Volatile
    private var targetSwitchBaselineFrameCount: Long = 0L
    @Volatile
    private var switchPendingFirstFrame: Boolean = false
    @Volatile
    private var forceMainRender: Boolean = false

    // Latency Measurement
    @Volatile
    private var switchStartNs: Long = 0L
    var onFirstFrameRendered: ((targetLens: LensType, latencyMs: Long) -> Unit)? = null

    // Background GL Thread
    private var glThread: HandlerThread? = null
    private var glHandler: Handler? = null
    private val initLatch = java.util.concurrent.CountDownLatch(1)

    // Shader Program & Locations
    private var programId: Int = 0
    private var aPositionLoc: Int = 0
    private var aTexCoordLoc: Int = 0
    private var uTexMatrixLoc: Int = 0
    private var uSamplerLoc: Int = 0

    // Quad Buffers
    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    init {
        // Standard fullscreen quad coordinates
        val quadVertices = floatArrayOf(
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f
        )
        val quadTexCoords = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )

        vertexBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(quadVertices)
                position(0)
            }

        texCoordBuffer = ByteBuffer.allocateDirect(quadTexCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(quadTexCoords)
                position(0)
            }

        startGlThread()
    }

    private fun startGlThread() {
        val thread = HandlerThread("CompositorGLThread").apply { start() }
        glThread = thread
        val handler = Handler(thread.looper)
        glHandler = handler

        handler.post {
            try {
                initEGL()
                initGL()
                initCameraSurfaces()
            } catch (t: Throwable) {
                Log.w(TAG, "GL/EGL init skipped or unsupported in this environment: ${t.message}")
            } finally {
                initLatch.countDown()
            }
        }
    }

    private fun initEGL() {
        try {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == null || eglDisplay == EGL14.EGL_NO_DISPLAY) {
                Log.w(TAG, "eglGetDisplay failed or unsupported in this environment")
                return
            }
        } catch (t: Throwable) {
            Log.w(TAG, "EGL not available in this environment: ${t.message}")
            return
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            Log.e(TAG, "eglInitialize failed")
            return
        }

        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
        eglConfig = configs[0]

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

        // Create 1x1 pbuffer dummy surface so context can be made current immediately
        val pbufferAttribs = intArrayOf(
            EGL14.EGL_WIDTH, 1,
            EGL14.EGL_HEIGHT, 1,
            EGL14.EGL_NONE
        )
        dummyPbuffer = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferAttribs, 0)
        EGL14.eglMakeCurrent(eglDisplay, dummyPbuffer, dummyPbuffer, eglContext)
    }

    private fun initGL() {
        val vertexShaderSource = """
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            uniform mat4 uTexMatrix;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = (uTexMatrix * aTextureCoord).xy;
            }
        """.trimIndent()

        val fragmentShaderSource = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """.trimIndent()

        val vShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
        val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)

        programId = GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vShader)
            GLES20.glAttachShader(prog, fShader)
            GLES20.glLinkProgram(prog)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                Log.e(TAG, "Program link failed: " + GLES20.glGetProgramInfoLog(prog))
            }
        }

        aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(programId, "aTextureCoord")
        uTexMatrixLoc = GLES20.glGetUniformLocation(programId, "uTexMatrix")
        uSamplerLoc = GLES20.glGetUniformLocation(programId, "sTexture")

        // Create OES external textures
        val textures = IntArray(2)
        GLES20.glGenTextures(2, textures, 0)
        mainTexId = textures[0]
        ultraWideTexId = textures[1]

        setupOesTexture(mainTexId)
        setupOesTexture(ultraWideTexId)
    }

    private fun setupOesTexture(id: Int) {
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e(TAG, "Shader compile failed ($type): " + GLES20.glGetShaderInfoLog(shader))
        }
        return shader
    }

    private fun initCameraSurfaces() {
        val handler = glHandler ?: return

        // Main Camera persistent SurfaceTexture & Surface
        val mainSt = SurfaceTexture(mainTexId).apply {
            setDefaultBufferSize(1920, 1080)
            setOnFrameAvailableListener({
                mainFrameCount.incrementAndGet()
                isMainFrameAvailable.set(true)
                triggerRender()
            }, handler)
        }
        mainCameraSurfaceTexture = mainSt
        mainCameraSurface = Surface(mainSt)

        // Ultra-Wide persistent SurfaceTexture & Surface
        val uwSt = SurfaceTexture(ultraWideTexId).apply {
            setDefaultBufferSize(1920, 1080)
            setOnFrameAvailableListener({
                ultraWideFrameCount.incrementAndGet()
                isUltraWideFrameAvailable.set(true)
                triggerRender()
            }, handler)
        }
        ultraWideCameraSurfaceTexture = uwSt
        ultraWideCameraSurface = Surface(uwSt)

        Log.d(TAG, "Compositor persistent camera input surfaces created.")
        initLatch.countDown()
    }

    fun awaitInitialized(timeoutMs: Long = 500): Boolean {
        return try {
            initLatch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            false
        }
    }

    // -----------------------------------------------------------------------------------------
    // Target Surface Management (Viewfinder & Little Preview)
    // -----------------------------------------------------------------------------------------

    fun setMainViewfinderSurface(surface: Surface?, width: Int, height: Int) {
        glHandler?.post {
            val display = eglDisplay
            val ctx = eglContext
            val pbuf = dummyPbuffer
            val oldMain = mainEglSurface

            if (oldMain != null && display != null && ctx != null && pbuf != null) {
                try {
                    EGL14.eglMakeCurrent(display, pbuf, pbuf, ctx)
                    EGL14.eglDestroySurface(display, oldMain)
                } catch (ignored: Exception) {}
                mainEglSurface = null
            }

            mainTargetSurface = surface
            mainWidth = if (width > 0) width else 1080
            mainHeight = if (height > 0) height else 1920

            if (surface != null && surface.isValid && display != null && eglConfig != null) {
                val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
                try {
                    mainEglSurface = EGL14.eglCreateWindowSurface(display, eglConfig, surface, surfaceAttribs, 0)
                    Log.d(TAG, "Main Viewfinder EGL Surface attached ($mainWidth x $mainHeight)")
                    forceMainRender = true
                    triggerRender()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create main EGL window surface", e)
                }
            }
        }
    }

    fun setLittlePreviewSurface(surface: Surface?, width: Int, height: Int) {
        glHandler?.post {
            val display = eglDisplay
            val ctx = eglContext
            val pbuf = dummyPbuffer
            val oldLittle = littleEglSurface

            if (oldLittle != null && display != null && ctx != null && pbuf != null) {
                try {
                    EGL14.eglMakeCurrent(display, pbuf, pbuf, ctx)
                    EGL14.eglDestroySurface(display, oldLittle)
                } catch (ignored: Exception) {}
                littleEglSurface = null
            }

            littleTargetSurface = surface
            littleWidth = if (width > 0) width else 240
            littleHeight = if (height > 0) height else 320

            if (surface != null && surface.isValid && display != null && eglConfig != null) {
                val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
                try {
                    littleEglSurface = EGL14.eglCreateWindowSurface(display, eglConfig, surface, surfaceAttribs, 0)
                    Log.d(TAG, "Little Preview EGL Surface attached ($littleWidth x $littleHeight)")
                    triggerRender()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create little preview EGL window surface", e)
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Lens Stream Switching (Zero Session Recreation)
    // -----------------------------------------------------------------------------------------

    /**
     * Switch which camera stream is presented to the main viewfinder.
     * Takes effect on the next render frame (< 16ms).
     *
     * @param targetLens LensType to display (WIDE or ULTRAWIDE)
     * @param startTimestampNs nanoTime recorded when the user pressed the switch button
     */
    fun switchActiveStream(targetLens: LensType, startTimestampNs: Long = System.nanoTime()) {
        switchStartNs = startTimestampNs
        activeLensType = targetLens
        targetSwitchBaselineFrameCount = if (targetLens == LensType.ULTRAWIDE) {
            ultraWideFrameCount.get()
        } else {
            mainFrameCount.get()
        }
        switchPendingFirstFrame = true
        forceMainRender = true
        Log.i(TAG, "Compositor switching active stream to: $targetLens (baselineFrameCount=$targetSwitchBaselineFrameCount)")
        triggerRender()
    }

    fun getMainFrameCount(): Long = mainFrameCount.get()
    fun getUltraWideFrameCount(): Long = ultraWideFrameCount.get()
    fun getMainTimestamp(): Long = lastMainTimestampNs.get()
    fun getUltraWideTimestamp(): Long = lastUltraWideTimestampNs.get()
    fun getFrameCount(lens: LensType): Long = if (lens == LensType.ULTRAWIDE) ultraWideFrameCount.get() else mainFrameCount.get()
    fun getTimestamp(lens: LensType): Long = if (lens == LensType.ULTRAWIDE) lastUltraWideTimestampNs.get() else lastMainTimestampNs.get()

    private fun triggerRender() {
        glHandler?.post {
            renderFrame()
        }
    }

    private fun renderFrame() {
        val display = eglDisplay ?: return
        val ctx = eglContext ?: return

        var newMainFrame = false
        var newUltraWideFrame = false

        // 1. Consume available frames to keep both hardware pipelines flowing & 3A converged
        if (isMainFrameAvailable.getAndSet(false)) {
            try {
                mainCameraSurfaceTexture?.updateTexImage()
                mainCameraSurfaceTexture?.getTransformMatrix(mainTexMatrix)
                val ts = mainCameraSurfaceTexture?.timestamp ?: 0L
                lastMainTimestampNs.set(ts)
                newMainFrame = true
            } catch (e: Exception) {
                Log.w(TAG, "Error updating main texture image", e)
            }
        }

        if (isUltraWideFrameAvailable.getAndSet(false)) {
            try {
                ultraWideCameraSurfaceTexture?.updateTexImage()
                ultraWideCameraSurfaceTexture?.getTransformMatrix(ultraWideTexMatrix)
                val ts = ultraWideCameraSurfaceTexture?.timestamp ?: 0L
                lastUltraWideTimestampNs.set(ts)
                newUltraWideFrame = true
            } catch (e: Exception) {
                Log.w(TAG, "Error updating ultrawide texture image", e)
            }
        }

        val activeLens = activeLensType
        val mainSurf = mainEglSurface
        val shouldForceMain = forceMainRender
        forceMainRender = false

        val hasNewActiveFrame = if (activeLens == LensType.ULTRAWIDE) {
            newUltraWideFrame
        } else {
            newMainFrame
        }

        val activeTotalFrames = if (activeLens == LensType.ULTRAWIDE) {
            ultraWideFrameCount.get()
        } else {
            mainFrameCount.get()
        }

        // 2. Render to Main Viewfinder EGL Surface
        // Only render if a new frame arrived for active lens, or on forceMainRender when frames exist.
        // Do not repeatedly render the same old Ultra-Wide frame.
        if (mainSurf != null && (hasNewActiveFrame || (shouldForceMain && activeTotalFrames > 0L))) {
            EGL14.eglMakeCurrent(display, mainSurf, mainSurf, ctx)
            GLES20.glViewport(0, 0, mainWidth, mainHeight)
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            if (activeLens == LensType.ULTRAWIDE) {
                drawQuad(ultraWideTexId, ultraWideTexMatrix)
            } else {
                drawQuad(mainTexId, mainTexMatrix)
            }

            EGL14.eglSwapBuffers(display, mainSurf)

            // 3. Verify new frame arrival after switch request before completing switch latency
            val startNs = switchStartNs
            if (switchPendingFirstFrame && hasNewActiveFrame && activeTotalFrames > targetSwitchBaselineFrameCount) {
                switchPendingFirstFrame = false
                if (startNs > 0) {
                    switchStartNs = 0L
                    val latencyNs = System.nanoTime() - startNs
                    val latencyMs = latencyNs / 1_000_000L
                    Log.i(TAG, "[LATENCY] Instant switch to $activeLens verified and displayed in ${latencyMs}ms (frame #$activeTotalFrames > $targetSwitchBaselineFrameCount)")
                    onFirstFrameRendered?.invoke(activeLens, latencyMs)
                }
            }
        }

        // 4. Render to Little Preview EGL Surface (if visible)
        val littleSurf = littleEglSurface
        val hasNewStandbyFrame = if (activeLens == LensType.ULTRAWIDE) newMainFrame else newUltraWideFrame
        if (isLittlePreviewEnabled && littleSurf != null && (hasNewStandbyFrame || shouldForceMain)) {
            EGL14.eglMakeCurrent(display, littleSurf, littleSurf, ctx)
            GLES20.glViewport(0, 0, littleWidth, littleHeight)
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            // Little Preview displays the STANDBY camera stream:
            // When Main is active -> Little Preview shows Ultra-Wide
            // When Ultra-Wide is active -> Little Preview shows Main
            if (activeLens == LensType.ULTRAWIDE) {
                drawQuad(mainTexId, mainTexMatrix)
            } else {
                drawQuad(ultraWideTexId, ultraWideTexMatrix)
            }

            EGL14.eglSwapBuffers(display, littleSurf)
        }
    }

    private fun drawQuad(textureId: Int, texMatrix: FloatArray) {
        GLES20.glUseProgram(programId)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(uSamplerLoc, 0)

        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)

        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(aTexCoordLoc)
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTexCoordLoc)
    }

    fun release() {
        glHandler?.post {
            try {
                mainCameraSurface?.release()
                mainCameraSurface = null
                mainCameraSurfaceTexture?.release()
                mainCameraSurfaceTexture = null

                ultraWideCameraSurface?.release()
                ultraWideCameraSurface = null
                ultraWideCameraSurfaceTexture?.release()
                ultraWideCameraSurfaceTexture = null

                val display = eglDisplay
                val ctx = eglContext
                val mainSurf = mainEglSurface
                val littleSurf = littleEglSurface
                val pbuf = dummyPbuffer

                if (mainSurf != null && display != null) {
                    EGL14.eglDestroySurface(display, mainSurf)
                    mainEglSurface = null
                }
                if (littleSurf != null && display != null) {
                    EGL14.eglDestroySurface(display, littleSurf)
                    littleEglSurface = null
                }
                if (pbuf != null && display != null) {
                    EGL14.eglDestroySurface(display, pbuf)
                    dummyPbuffer = null
                }
                if (ctx != null && display != null) {
                    EGL14.eglDestroyContext(display, ctx)
                    eglContext = null
                }
                if (display != null) {
                    EGL14.eglTerminate(display)
                    eglDisplay = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing compositor GL resources", e)
            }
        }

        glThread?.quitSafely()
        try {
            glThread?.join(500)
            glThread = null
            glHandler = null
        } catch (ignored: Exception) {}
    }
}
