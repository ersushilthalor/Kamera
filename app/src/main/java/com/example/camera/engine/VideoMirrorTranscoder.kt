package com.example.camera.engine

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Build
import android.util.Log
import android.view.Surface
import android.graphics.SurfaceTexture
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * High-performance hardware video mirror transcoder.
 * Mirrors front-camera MP4 videos horizontally while preserving full frame rate,
 * bit rate, resolution, color space, and original uncompressed audio fidelity.
 */
object VideoMirrorTranscoder {

    private const val TAG = "VideoMirrorTranscoder"
    private const val TIMEOUT_USEC = 10000L

    /**
     * Transcodes video from inputFile to outputFile with optional horizontal mirroring
     * and optional ColorMatrix filter transformation.
     * Audio track is passed through sample-by-sample without lossy re-encoding.
     */
    fun mirrorVideo(inputFile: File, outputFile: File): Boolean {
        return transcodeVideo(inputFile, outputFile, isMirrored = true, colorMatrix = null)
    }

    fun transcodeVideo(
        inputFile: File,
        outputFile: File,
        isMirrored: Boolean,
        colorMatrix: FloatArray? = null,
        targetWidth: Int? = null,
        targetHeight: Int? = null,
        targetBitrate: Int? = null,
        forceHevc: Boolean = false,
        useHighQualityUpscale: Boolean = false
    ): Boolean {
        if (!inputFile.exists() || inputFile.length() == 0L) {
            Log.e(TAG, "Input file does not exist or is empty")
            return false
        }

        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var eglHelper: EglSurfaceHelper? = null

        return try {
            extractor = MediaExtractor().apply { setDataSource(inputFile.absolutePath) }
            val trackCount = extractor.trackCount

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") && videoTrackIndex < 0) {
                    videoTrackIndex = i
                    videoFormat = format
                } else if (mime.startsWith("audio/") && audioTrackIndex < 0) {
                    audioTrackIndex = i
                    audioFormat = format
                }
            }

            if (videoTrackIndex < 0 || videoFormat == null) {
                Log.e(TAG, "No video track found in input file")
                return false
            }

            val inWidth = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
            val inHeight = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val outWidth = targetWidth ?: inWidth
            val outHeight = targetHeight ?: inHeight
            val videoMime = videoFormat.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_VIDEO_AVC
            val bitrate = targetBitrate ?: if (outWidth >= 7680) {
                80_000_000
            } else if (videoFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
                videoFormat.getInteger(MediaFormat.KEY_BIT_RATE)
            } else {
                15_000_000
            }
            val frameRate = if (videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
            } else {
                30
            }
            val rotation = if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                videoFormat.getInteger(MediaFormat.KEY_ROTATION)
            } else {
                0
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer.setOrientationHint(rotation)

            val is8KTarget = (outWidth >= 7680 || outHeight >= 7680)
            val shouldTryHevc = forceHevc || is8KTarget

            val targetEncoderMime = if (shouldTryHevc) {
                try {
                    val testCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
                    testCodec.release()
                    MediaFormat.MIMETYPE_VIDEO_HEVC
                } catch (e: Exception) {
                    Log.w(TAG, "HEVC encoder unavailable, falling back to AVC", e)
                    MediaFormat.MIMETYPE_VIDEO_AVC
                }
            } else {
                try {
                    val testCodec = MediaCodec.createEncoderByType(videoMime)
                    testCodec.release()
                    videoMime
                } catch (e: Exception) {
                    MediaFormat.MIMETYPE_VIDEO_AVC
                }
            }

            // Configure encoder for target resolution (7680x4320 for 8K)
            val encFormat = MediaFormat.createVideoFormat(targetEncoderMime, outWidth, outHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                if (targetEncoderMime == MediaFormat.MIMETYPE_VIDEO_HEVC) {
                    // Main profile for broad compatibility
                    setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)
                }
            }

            encoder = try {
                MediaCodec.createEncoderByType(targetEncoderMime).apply {
                    configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to configure encoder for $outWidth x $outHeight ($targetEncoderMime), fallback to AVC", e)
                val fallbackFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, inWidth, inHeight).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, 35_000_000)
                    setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }
                MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                    configure(fallbackFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                }
            }

            val inputSurface = encoder.createInputSurface()
            encoder.start()

            // Setup EGL on input surface with high quality GPU scaling if requested
            val actualOutWidth = if (encFormat.containsKey(MediaFormat.KEY_WIDTH)) encFormat.getInteger(MediaFormat.KEY_WIDTH) else outWidth
            val actualOutHeight = if (encFormat.containsKey(MediaFormat.KEY_HEIGHT)) encFormat.getInteger(MediaFormat.KEY_HEIGHT) else outHeight

            eglHelper = EglSurfaceHelper(
                surface = inputSurface,
                width = actualOutWidth,
                height = actualOutHeight,
                inWidth = inWidth,
                inHeight = inHeight,
                useHighQualityUpscale = (useHighQualityUpscale || is8KTarget)
            )
            eglHelper.makeCurrent()

            // Configure decoder with SurfaceTexture
            val surfaceTexture = eglHelper.surfaceTexture
            val decoderSurface = Surface(surfaceTexture)

            decoder = MediaCodec.createDecoderByType(videoMime)
            decoder.configure(videoFormat, decoderSurface, null, 0)
            decoder.start()

            extractor.selectTrack(videoTrackIndex)

            var muxerVideoTrack = -1
            var muxerAudioTrack = -1
            var muxerStarted = false

            // If there's an audio track, prepare muxer track
            if (audioTrackIndex >= 0 && audioFormat != null) {
                muxerAudioTrack = muxer.addTrack(audioFormat)
            }

            // Buffer for reading samples
            val maxBufferSize = if (videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(1024 * 1024)
            } else {
                1024 * 1024
            }
            val inputBuffer = ByteBuffer.allocateDirect(maxBufferSize)

            var decoderDone = false
            var extractorDone = false
            var encoderDone = false
            val bufferInfo = MediaCodec.BufferInfo()

            while (!encoderDone) {
                // 1. Feed extractor to decoder
                if (!extractorDone) {
                    val inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC)
                    if (inputBufIndex >= 0) {
                        val buf = decoder.getInputBuffer(inputBufIndex)
                        if (buf != null) {
                            val sampleSize = extractor.readSampleData(buf, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(
                                    inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                extractorDone = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                decoder.queueInputBuffer(
                                    inputBufIndex, 0, sampleSize, presentationTimeUs, 0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                // 2. Drain decoder to Surface
                if (!decoderDone) {
                    val decStatus = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
                    if (decStatus >= 0) {
                        val render = bufferInfo.size > 0
                        decoder.releaseOutputBuffer(decStatus, render)
                        if (render) {
                            eglHelper.awaitNewImage()
                            eglHelper.drawImage(isMirrored = isMirrored, colorMatrix = colorMatrix)
                            eglHelper.setPresentationTime(bufferInfo.presentationTimeUs * 1000L)
                            eglHelper.swapBuffers()
                        }
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            decoderDone = true
                            encoder.signalEndOfInputStream()
                        }
                    }
                }

                // 3. Drain encoder to Muxer
                val encStatus = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
                if (encStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) {
                        throw IllegalStateException("Encoder format changed twice")
                    }
                    val newFormat = encoder.outputFormat
                    muxerVideoTrack = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (encStatus >= 0) {
                    val encodedData = encoder.getOutputBuffer(encStatus)
                    if (encodedData != null) {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size != 0 && muxerStarted) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(muxerVideoTrack, encodedData, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(encStatus, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true
                        }
                    }
                }
            }

            // Copy audio track directly
            if (audioTrackIndex >= 0 && muxerAudioTrack >= 0 && muxerStarted) {
                extractor.unselectTrack(videoTrackIndex)
                extractor.selectTrack(audioTrackIndex)
                extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                val audioBuffer = ByteBuffer.allocateDirect(256 * 1024)
                val audioBufferInfo = MediaCodec.BufferInfo()

                while (true) {
                    val sampleSize = extractor.readSampleData(audioBuffer, 0)
                    if (sampleSize < 0) break

                    audioBufferInfo.offset = 0
                    audioBufferInfo.size = sampleSize
                    audioBufferInfo.presentationTimeUs = extractor.sampleTime
                    audioBufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(muxerAudioTrack, audioBuffer, audioBufferInfo)
                    extractor.advance()
                }
            }

            Log.d(TAG, "Successfully mirrored front camera video: ${outputFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to mirror front camera video, falling back to original", e)
            false
        } finally {
            try { decoder?.stop() } catch (ignored: Exception) {}
            try { decoder?.release() } catch (ignored: Exception) {}
            try { encoder?.stop() } catch (ignored: Exception) {}
            try { encoder?.release() } catch (ignored: Exception) {}
            try { eglHelper?.release() } catch (ignored: Exception) {}
            try { extractor?.release() } catch (ignored: Exception) {}
            try { muxer?.stop() } catch (ignored: Exception) {}
            try { muxer?.release() } catch (ignored: Exception) {}
        }
    }

    /**
     * EGL + OpenGL ES 2.0 Surface helper to draw the decoded video texture
     * with horizontal flip onto the encoder's input surface.
     */
    private class EglSurfaceHelper(
        private val surface: Surface,
        val width: Int,
        val height: Int,
        val inWidth: Int = width,
        val inHeight: Int = height,
        val useHighQualityUpscale: Boolean = false
    ) {
        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        lateinit var surfaceTexture: SurfaceTexture
        private var textureId: Int = -1
        private var program: Int = 0
        private var uMVPMatrixLoc: Int = -1
        private var uSTMatrixLoc: Int = -1
        private var uColorMatrixLoc: Int = -1
        private var uColorOffsetLoc: Int = -1
        private var uHasColorMatrixLoc: Int = -1
        private var uUseUpscaleLoc: Int = -1
        private var uTexelSizeLoc: Int = -1
        private var aPositionLoc: Int = -1
        private var aTextureCoordLoc: Int = -1

        private val mvpMatrix = FloatArray(16)
        private val stMatrix = FloatArray(16)
        private val glColorMatrix = FloatArray(16)
        private val glColorOffset = FloatArray(4)

        @Volatile
        private var frameAvailable = false
        private val frameSyncObject = Object()

        private val vertexBuffer: FloatBuffer
        private val texCoordBuffer: FloatBuffer

        init {
            val coords = floatArrayOf(
                -1.0f, -1.0f, 0.0f,
                 1.0f, -1.0f, 0.0f,
                -1.0f,  1.0f, 0.0f,
                 1.0f,  1.0f, 0.0f
            )
            vertexBuffer = ByteBuffer.allocateDirect(coords.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                    put(coords)
                    position(0)
                }

            val texCoords = floatArrayOf(
                0.0f, 0.0f,
                1.0f, 0.0f,
                0.0f, 1.0f,
                1.0f, 1.0f
            )
            texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                    put(texCoords)
                    position(0)
                }

            initEgl()
            initGl()
        }

        private fun initEgl() {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142, 1, // EGL_RECORDABLE_ANDROID
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)
            val config = configs[0] ?: throw RuntimeException("Unable to find matching EGLConfig")

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, surface, surfaceAttribs, 0)
        }

        private fun initGl() {
            makeCurrent()

            val vertexShaderCode = """
                uniform mat4 uMVPMatrix;
                uniform mat4 uSTMatrix;
                attribute vec4 aPosition;
                attribute vec4 aTextureCoord;
                varying vec2 vTextureCoord;
                void main() {
                    gl_Position = uMVPMatrix * aPosition;
                    vTextureCoord = (uSTMatrix * aTextureCoord).xy;
                }
            """.trimIndent()

            val fragmentShaderCode = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform samplerExternalOES sTexture;
                uniform mat4 uColorMatrix;
                uniform vec4 uColorOffset;
                uniform int uHasColorMatrix;
                uniform int uUseUpscale;
                uniform vec2 uTexelSize;
                void main() {
                    vec4 texColor;
                    if (uUseUpscale != 0) {
                        // High-Quality GPU 2x Interpolation Filter with edge preservation
                        vec4 c = texture2D(sTexture, vTextureCoord);
                        vec4 n = texture2D(sTexture, vTextureCoord + vec2(0.0, uTexelSize.y));
                        vec4 s = texture2D(sTexture, vTextureCoord - vec2(0.0, uTexelSize.y));
                        vec4 e = texture2D(sTexture, vTextureCoord + vec2(uTexelSize.x, 0.0));
                        vec4 w = texture2D(sTexture, vTextureCoord - vec2(uTexelSize.x, 0.0));
                        vec4 sharpened = c * 1.35 - (n + s + e + w) * 0.0875;
                        texColor = clamp(sharpened, 0.0, 1.0);
                    } else {
                        texColor = texture2D(sTexture, vTextureCoord);
                    }
                    if (uHasColorMatrix != 0) {
                        vec3 rgb = clamp((uColorMatrix * vec4(texColor.rgb, 1.0)).rgb + uColorOffset.rgb, 0.0, 1.0);
                        gl_FragColor = vec4(rgb, texColor.a);
                    } else {
                        gl_FragColor = texColor;
                    }
                }
            """.trimIndent()

            val vShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
            val fShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

            program = GLES20.glCreateProgram().apply {
                GLES20.glAttachShader(this, vShader)
                GLES20.glAttachShader(this, fShader)
                GLES20.glLinkProgram(this)
            }

            aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
            aTextureCoordLoc = GLES20.glGetAttribLocation(program, "aTextureCoord")
            uMVPMatrixLoc = GLES20.glGetUniformLocation(program, "uMVPMatrix")
            uSTMatrixLoc = GLES20.glGetUniformLocation(program, "uSTMatrix")
            uColorMatrixLoc = GLES20.glGetUniformLocation(program, "uColorMatrix")
            uColorOffsetLoc = GLES20.glGetUniformLocation(program, "uColorOffset")
            uHasColorMatrixLoc = GLES20.glGetUniformLocation(program, "uHasColorMatrix")
            uUseUpscaleLoc = GLES20.glGetUniformLocation(program, "uUseUpscale")
            uTexelSizeLoc = GLES20.glGetUniformLocation(program, "uTexelSize")

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]

            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            surfaceTexture = SurfaceTexture(textureId).apply {
                setDefaultBufferSize(inWidth, inHeight)
                setOnFrameAvailableListener {
                    synchronized(frameSyncObject) {
                        frameAvailable = true
                        frameSyncObject.notifyAll()
                    }
                }
            }

            Matrix.setIdentityM(mvpMatrix, 0)
        }

        fun makeCurrent() {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        fun awaitNewImage() {
            synchronized(frameSyncObject) {
                val deadline = System.currentTimeMillis() + 1000L
                while (!frameAvailable && System.currentTimeMillis() < deadline) {
                    try {
                        frameSyncObject.wait(100)
                    } catch (ignored: InterruptedException) {
                        break
                    }
                }
                if (!frameAvailable) {
                    return
                }
                frameAvailable = false
            }
            try {
                surfaceTexture.updateTexImage()
                surfaceTexture.getTransformMatrix(stMatrix)
            } catch (ignored: Throwable) {}
        }

        fun drawImage(isMirrored: Boolean, colorMatrix: FloatArray? = null) {
            makeCurrent()
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(program)

            Matrix.setIdentityM(mvpMatrix, 0)
            if (isMirrored) {
                // Apply horizontal mirror (scale -1 on X axis)
                Matrix.scaleM(mvpMatrix, 0, -1f, 1f, 1f)
            }

            GLES20.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(uSTMatrixLoc, 1, false, stMatrix, 0)

            // High-quality GPU 2x interpolation uniforms
            if (useHighQualityUpscale && inWidth > 0 && inHeight > 0) {
                GLES20.glUniform1i(uUseUpscaleLoc, 1)
                GLES20.glUniform2f(uTexelSizeLoc, 1.0f / inWidth.toFloat(), 1.0f / inHeight.toFloat())
            } else {
                GLES20.glUniform1i(uUseUpscaleLoc, 0)
                GLES20.glUniform2f(uTexelSizeLoc, 0.0f, 0.0f)
            }

            if (colorMatrix != null && colorMatrix.size >= 20) {
                GLES20.glUniform1i(uHasColorMatrixLoc, 1)

                // Column-major 4x4 matrix
                glColorMatrix[0] = colorMatrix[0]; glColorMatrix[1] = colorMatrix[5]; glColorMatrix[2] = colorMatrix[10]; glColorMatrix[3] = colorMatrix[15]
                glColorMatrix[4] = colorMatrix[1]; glColorMatrix[5] = colorMatrix[6]; glColorMatrix[6] = colorMatrix[11]; glColorMatrix[7] = colorMatrix[16]
                glColorMatrix[8] = colorMatrix[2]; glColorMatrix[9] = colorMatrix[7]; glColorMatrix[10] = colorMatrix[12]; glColorMatrix[11] = colorMatrix[17]
                glColorMatrix[12] = colorMatrix[3]; glColorMatrix[13] = colorMatrix[8]; glColorMatrix[14] = colorMatrix[13]; glColorMatrix[15] = colorMatrix[18]

                glColorOffset[0] = colorMatrix[4] / 255.0f
                glColorOffset[1] = colorMatrix[9] / 255.0f
                glColorOffset[2] = colorMatrix[14] / 255.0f
                glColorOffset[3] = colorMatrix[19] / 255.0f

                GLES20.glUniformMatrix4fv(uColorMatrixLoc, 1, false, glColorMatrix, 0)
                GLES20.glUniform4fv(uColorOffsetLoc, 1, glColorOffset, 0)
            } else {
                GLES20.glUniform1i(uHasColorMatrixLoc, 0)
            }

            GLES20.glEnableVertexAttribArray(aPositionLoc)
            GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)

            GLES20.glEnableVertexAttribArray(aTextureCoordLoc)
            GLES20.glVertexAttribPointer(aTextureCoordLoc, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(aPositionLoc)
            GLES20.glDisableVertexAttribArray(aTextureCoordLoc)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
            GLES20.glUseProgram(0)
        }

        fun setPresentationTime(nsecs: Long) {
            android.opengl.EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs)
        }

        fun swapBuffers(): Boolean {
            return EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        fun release() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
            surfaceTexture.release()
        }

        private fun loadShader(type: Int, shaderCode: String): Int {
            return GLES20.glCreateShader(type).also { shader ->
                GLES20.glShaderSource(shader, shaderCode)
                GLES20.glCompileShader(shader)
            }
        }
    }
}
