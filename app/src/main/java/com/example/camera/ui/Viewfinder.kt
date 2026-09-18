package com.example.camera.ui

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.os.Build
import android.util.Size as CameraSize
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import kotlin.math.pow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.camera.model.CameraMode
import com.example.camera.model.CinemaColorProfile
import com.example.camera.model.CinemaConfig
import com.example.camera.model.CinematicLut
import com.example.camera.model.GridType
import com.example.camera.model.LogBitDepth
import com.example.camera.model.PhotoFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun Viewfinder(
    aspectRatio: Float,
    gridType: GridType,
    focusRingPoint: Offset?,
    isAeLocked: Boolean,
    isAfLocked: Boolean,
    isFrontCamera: Boolean = false,
    cameraMode: CameraMode = CameraMode.PHOTO,
    previewBufferSize: CameraSize? = null,
    sensorOrientation: Int = 90,
    activePhotoFilter: PhotoFilter? = null,
    activeLut: CinematicLut? = null,
    isLutPreviewEnabled: Boolean = false,
    cinemaConfig: CinemaConfig? = null,
    rec2020AutoToneParams: com.example.camera.engine.Rec2020AutoToneParams? = null,
    isVideoPipelineEnabled: Boolean = true,
    activeVideoPipeline: com.example.camera.pipeline.video.VideoPipelineType = com.example.camera.pipeline.video.VideoPipelineType.IPHONE,
    onSurfaceTextureAvailable: (SurfaceTexture?) -> Unit,
    onTapToFocus: (Offset, Float, Float) -> Unit,
    onZoomChange: (Float) -> Unit,
    onExposureCompensationChange: (Int) -> Unit = {},
    onToggleLock: () -> Unit = {},
    currentExposureCompensation: Int = 0,
    modifier: Modifier = Modifier
) {
    var currentScale by remember { mutableFloatStateOf(1.0f) }
    var isZoomBarVisible by remember { mutableStateOf(false) }
    var zoomHideJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("viewfinder_container")
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        // Expected aspect ratio for current mode (portrait display: height / width)
        // 4:3 Photo -> 4f / 3f = 1.333f
        // 16:9 Video/Cinema -> 16f / 9f = 1.777f
        val targetRatio = if (aspectRatio > 0.1f) {
            if (aspectRatio < 1.0f) 1f / aspectRatio else aspectRatio
        } else {
            when (cameraMode) {
                CameraMode.VIDEO, CameraMode.CINEMA, CameraMode.DOLLY_ZOOM -> 16f / 9f
                else -> 4f / 3f
            }
        }

        // Viewfinder spans dimensions dictated strictly by the native camera output aspect ratio,
        // fitting cleanly within the container bounds with letterboxing/pillarboxing as appropriate.
        val (targetWidth, targetHeight) = if (containerWidth * targetRatio <= containerHeight) {
            containerWidth to (containerWidth * targetRatio)
        } else {
            (containerHeight / targetRatio) to containerHeight
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = targetWidth, height = targetHeight)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            var changed = false
                            if (zoom != 1f) {
                                currentScale = (currentScale * zoom).coerceIn(0.5f, 10.0f)
                                changed = true
                            }
                            // Horizontal swipe: Right to Left (pan.x < 0) zooms in; Left to Right (pan.x > 0) zooms out
                            if (abs(pan.x) > abs(pan.y) && abs(pan.x) > 1.5f) {
                                val zoomDelta = -pan.x / 140f
                                currentScale = (currentScale + zoomDelta).coerceIn(0.5f, 10.0f)
                                changed = true
                            }
                            if (changed) {
                                onZoomChange(currentScale)
                                isZoomBarVisible = true
                                zoomHideJob?.cancel()
                                zoomHideJob = coroutineScope.launch {
                                    delay(1000)
                                    isZoomBarVisible = false
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                val normX = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val normY = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                onTapToFocus(offset, normX, normY)
                            },
                            onLongPress = { offset ->
                                val normX = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val normY = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                onTapToFocus(offset, normX, normY)
                                onToggleLock()
                            }
                        )
                    }
            ) {
                // 100% Native Camera2 TextureView Preview:
                // No custom processing, transformations, orientation pipelines, scaling hacks,
                // stretching, rotation, or cropping. Output comes directly from Camera2 pipeline.
                AndroidView(
                    factory = { context ->
                        TextureView(context).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                    onSurfaceTextureAvailable(st)
                                }
                                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                    onSurfaceTextureAvailable(null)
                                    return true
                                }
                                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                            }
                        }
                    },
                    update = { textureView ->
                        val effectiveLut = activeLut ?: cinemaConfig?.selectedLut
                        val effectiveLutPreview = isLutPreviewEnabled || (cinemaConfig?.isLutPreviewEnabled == true)

                        val colorMatrix = android.graphics.ColorMatrix()
                        var hasFilter = false

                        if (cameraMode == CameraMode.CINEMA && cinemaConfig != null && cinemaConfig.logBitDepth != LogBitDepth.OFF) {
                            // 1. Log Profile characteristic preview
                            when (cinemaConfig.colorProfile) {
                                CinemaColorProfile.FLAT_LOG -> {
                                    // True Flat Log: lifted milky shadow pedestal (+32 offset) and low contrast
                                    val flatPedestal = android.graphics.ColorMatrix(floatArrayOf(
                                        0.86f, 0f, 0f, 0f, 32f,
                                        0f, 0.86f, 0f, 0f, 32f,
                                        0f, 0f, 0.86f, 0f, 32f,
                                        0f, 0f, 0f, 1f, 0f
                                    ))
                                    colorMatrix.postConcat(flatPedestal)
                                    hasFilter = true
                                }
                                CinemaColorProfile.HLG -> {
                                    // HLG: vibrant preserved realistic colors
                                    val hlgSat = android.graphics.ColorMatrix()
                                    hlgSat.setSaturation(1.22f)
                                    colorMatrix.postConcat(hlgSat)
                                    hasFilter = true
                                }
                                CinemaColorProfile.REC_2020 -> {
                                    // REC.2020 Real-Time Auto Tone Control:
                                    // Continuous real-time Exposure, Highlight roll-off shoulder, Shadow toe lift, Contrast & Inky Black Pedestal
                                    // Calibrated preview matrix guarantees true neutral whites, zero pink/red artifacts, and rich flagship color depth
                                    val p = rec2020AutoToneParams ?: com.example.camera.engine.Rec2020AutoToneParams()
                                    val rec2020Matrix = com.example.camera.engine.Rec2020AutoToneEngine.computePreviewColorMatrix(p)
                                    colorMatrix.postConcat(rec2020Matrix)
                                    hasFilter = true
                                }
                                CinemaColorProfile.NATIVE -> {
                                    // Native: standard unadjusted natural camera profile
                                }
                                else -> {}
                            }

                            // For non-REC_2020 profiles, apply manual user sliders
                            if (cinemaConfig.colorProfile != CinemaColorProfile.REC_2020) {
                                // 2. Washed Out Reduction (recovers deep blacks & midtone contrast from flat profiles)
                                if (cinemaConfig.washedOut > 0.0f) {
                                    val w = cinemaConfig.washedOut
                                    val pedestalReduction = -28f * w
                                    val contrastBoost = 1.0f + (w * 0.25f)
                                    val t = (1.0f - contrastBoost) * 128f + pedestalReduction
                                    val washedOutMatrix = android.graphics.ColorMatrix(floatArrayOf(
                                        contrastBoost, 0f, 0f, 0f, t,
                                        0f, contrastBoost, 0f, 0f, t,
                                        0f, 0f, contrastBoost, 0f, t,
                                        0f, 0f, 0f, 1f, 0f
                                    ))
                                    colorMatrix.postConcat(washedOutMatrix)
                                    hasFilter = true
                                }

                                // 3. Real-time Exposure control (+/-) on viewfinder
                                if (cinemaConfig.exposure != 0.0f) {
                                    val expMultiplier = 2.0f.pow(cinemaConfig.exposure * 0.75f)
                                    val expMatrix = android.graphics.ColorMatrix(floatArrayOf(
                                        expMultiplier, 0f, 0f, 0f, 0f,
                                        0f, expMultiplier, 0f, 0f, 0f,
                                        0f, 0f, expMultiplier, 0f, 0f,
                                        0f, 0f, 0f, 1f, 0f
                                    ))
                                    colorMatrix.postConcat(expMatrix)
                                    hasFilter = true
                                }

                                // 4. User Contrast control (+/-)
                                if (cinemaConfig.contrast != 0.0f) {
                                    val c = 1.0f + (cinemaConfig.contrast * 0.4f)
                                    val t = (1.0f - c) * 128f
                                    val contrastMatrix = android.graphics.ColorMatrix(floatArrayOf(
                                        c, 0f, 0f, 0f, t,
                                        0f, c, 0f, 0f, t,
                                        0f, 0f, c, 0f, t,
                                        0f, 0f, 0f, 1f, 0f
                                    ))
                                    colorMatrix.postConcat(contrastMatrix)
                                    hasFilter = true
                                }
                            }

                            // User Saturation control (+/-)
                            if (cinemaConfig.saturation != 1.0f) {
                                val satMatrix = android.graphics.ColorMatrix()
                                satMatrix.setSaturation(cinemaConfig.saturation)
                                colorMatrix.postConcat(satMatrix)
                                hasFilter = true
                            }

                            // 5. Cinematic LUT monitoring (active in both Preview LUT mode and Bake LUT mode)
                            val shouldShowLut = (effectiveLutPreview || cinemaConfig.isBakeLutToOutput) &&
                                    effectiveLut != null && effectiveLut != CinematicLut.NONE
                            if (shouldShowLut && effectiveLut != null) {
                                val lutMat = effectiveLut.toAndroidColorMatrix()
                                if (lutMat != null) {
                                    colorMatrix.postConcat(lutMat)
                                    hasFilter = true
                                }
                            }
                        } else if (cameraMode == CameraMode.VIDEO && isVideoPipelineEnabled && activeVideoPipeline != com.example.camera.pipeline.video.VideoPipelineType.OFF) {
                            val pipelineMat = when (activeVideoPipeline) {
                                com.example.camera.pipeline.video.VideoPipelineType.IPHONE -> com.example.camera.pipeline.video.IPhoneVideoPipeline().getPreviewColorMatrix()
                                com.example.camera.pipeline.video.VideoPipelineType.DSLR -> com.example.camera.pipeline.video.DslrVideoPipeline().getPreviewColorMatrix()
                                com.example.camera.pipeline.video.VideoPipelineType.SAMSUNG -> com.example.camera.pipeline.video.SamsungVideoPipeline().getPreviewColorMatrix()
                                else -> null
                            }
                            if (pipelineMat != null) {
                                colorMatrix.postConcat(pipelineMat)
                                hasFilter = true
                            }
                        } else if (cameraMode == CameraMode.PHOTO && activePhotoFilter != null && activePhotoFilter != PhotoFilter.ORIGINAL) {
                            val filterMat = activePhotoFilter.toAndroidColorMatrix()
                            if (filterMat != null) {
                                colorMatrix.postConcat(filterMat)
                                hasFilter = true
                            }
                        }

                        if (hasFilter) {
                            val paint = android.graphics.Paint()
                            paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
                            textureView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, paint)
                        } else {
                            textureView.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Optional Non-Destructive Live LUT / Filter / Video Pipeline Monitoring Badge
                val badgeLut = activeLut ?: cinemaConfig?.selectedLut
                val badgeLutPreview = isLutPreviewEnabled || (cinemaConfig?.isLutPreviewEnabled == true)
                if (cameraMode == CameraMode.CINEMA && badgeLutPreview && badgeLut != null && badgeLut != CinematicLut.NONE) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCC111318))
                            .border(1.dp, badgeLut.accentColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "LUT: ${badgeLut.label} (PREVIEW)",
                            color = badgeLut.accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                } else if (cameraMode == CameraMode.VIDEO && isVideoPipelineEnabled && activeVideoPipeline != com.example.camera.pipeline.video.VideoPipelineType.OFF) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCC111318))
                            .border(1.dp, activeVideoPipeline.accentColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "PIPE: ${activeVideoPipeline.badgeLabel}",
                            color = activeVideoPipeline.accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                } else if (cameraMode == CameraMode.PHOTO && activePhotoFilter != null && activePhotoFilter != PhotoFilter.ORIGINAL) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCC111318))
                            .border(1.dp, activePhotoFilter.swatchColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "FILTER: ${activePhotoFilter.displayName}",
                            color = activePhotoFilter.swatchColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Grid Overlay
                if (gridType != GridType.NONE) {
                    CameraGridOverlay(gridType = gridType, modifier = Modifier.fillMaxSize())
                }

                // Tap to focus animated ring
                AnimatedVisibility(
                    visible = focusRingPoint != null,
                    enter = fadeIn() + scaleIn(initialScale = 1.3f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    focusRingPoint?.let { point ->
                        FocusRingIndicator(
                            point = point,
                            isAeLocked = isAeLocked,
                            isAfLocked = isAfLocked,
                            exposureCompensation = currentExposureCompensation,
                            onExposureChange = onExposureCompensationChange,
                            onLockClick = onToggleLock
                        )
                    }
                }

                // Minimal Zoom Bar HUD overlay (auto-hides after 1s of inactivity)
                AnimatedVisibility(
                    visible = isZoomBarVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 76.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xDD111827),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier.testTag("viewfinder_minimal_zoom_bar")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f×", currentScale),
                                color = Color(0xFFFFD54F),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            // Sleek minimal slider track indicator
                            Box(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.25f))
                            ) {
                                val normProgress = ((currentScale - 0.5f) / (10.0f - 0.5f)).coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(normProgress)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFFFFD54F))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FocusRingIndicator(
    point: Offset,
    isAeLocked: Boolean,
    isAfLocked: Boolean,
    exposureCompensation: Int = 0,
    onExposureChange: (Int) -> Unit = {},
    onLockClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "focusPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val density = androidx.compose.ui.platform.LocalDensity.current
    val ringSizePx = with(density) { 72.dp.toPx() }
    val offsetX = with(density) { (point.x - ringSizePx / 2).toDp() }
    val offsetY = with(density) { (point.y - ringSizePx / 2).toDp() }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .offset(x = offsetX, y = offsetY)
                .size(72.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val ringColor = if (isAeLocked || isAfLocked) Color(0xFFFFD54F) else Color(0xFFFFEB3B)
                drawCircle(
                    color = ringColor.copy(alpha = alpha),
                    radius = size.minDimension / 2f,
                    style = Stroke(width = 2.dp.toPx())
                )
                // Small crosshair in center
                drawLine(
                    color = ringColor.copy(alpha = 0.8f),
                    start = Offset(size.width / 2f - 6.dp.toPx(), size.height / 2f),
                    end = Offset(size.width / 2f + 6.dp.toPx(), size.height / 2f),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawLine(
                    color = ringColor.copy(alpha = 0.8f),
                    start = Offset(size.width / 2f, size.height / 2f - 6.dp.toPx()),
                    end = Offset(size.width / 2f, size.height / 2f + 6.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // Lock Indicator Badge (tap to toggle lock)
            if (isAeLocked || isAfLocked) {
                Row(
                    modifier = Modifier
                        .offset(y = 44.dp)
                        .clip(CircleShape)
                        .background(Color(0xEE000000))
                        .border(1.dp, Color(0xFFFFD54F).copy(alpha = 0.5f), CircleShape)
                        .clickable { onLockClick() }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isAeLocked && isAfLocked) "AE/AF LOCK" else if (isAeLocked) "AE LOCK" else "AF LOCK",
                        color = Color(0xFFFFD54F),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun CameraGridOverlay(
    gridType: GridType,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val gridColor = Color.White.copy(alpha = 0.35f)
        val strokeWidth = 1.dp.toPx()

        when (gridType) {
            GridType.THIRDS -> {
                // Vertical lines
                drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth)
                drawLine(gridColor, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth)
                // Horizontal lines
                drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth)
                drawLine(gridColor, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth)
            }
            GridType.GOLDEN -> {
                val phi = 0.618f
                val left = w * (1f - phi)
                val right = w * phi
                val top = h * (1f - phi)
                val bottom = h * phi

                drawLine(gridColor, Offset(left, 0f), Offset(left, h), strokeWidth)
                drawLine(gridColor, Offset(right, 0f), Offset(right, h), strokeWidth)
                drawLine(gridColor, Offset(0f, top), Offset(w, top), strokeWidth)
                drawLine(gridColor, Offset(0f, bottom), Offset(w, bottom), strokeWidth)
            }
            GridType.SQUARE -> {
                val squareDim = minOf(w, h)
                val startX = (w - squareDim) / 2f
                val startY = (h - squareDim) / 2f
                drawRect(
                    color = gridColor,
                    topLeft = Offset(startX, startY),
                    size = Size(squareDim, squareDim),
                    style = Stroke(strokeWidth)
                )
            }
            GridType.LEVEL -> {
                // Center horizon line with dashed styling
                drawLine(
                    color = Color(0xFF64FFDA).copy(alpha = 0.75f),
                    start = Offset(w * 0.2f, h / 2f),
                    end = Offset(w * 0.8f, h / 2f),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
                )
                // Center level dot
                drawCircle(
                    color = Color(0xFF64FFDA),
                    radius = 3.dp.toPx(),
                    center = Offset(w / 2f, h / 2f)
                )
            }
            GridType.NONE -> {}
        }
    }
}
