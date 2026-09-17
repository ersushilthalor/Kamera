package com.example.camera.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.model.*

/**
 * Premium Stock Flagship Camera Settings Categories:
 * 1. Capture & Quality
 * 2. Video & Audio
 * 3. Processing & AI
 * 4. Controls & Gestures
 * 5. Advanced & Labs
 * 6. General / About
 */
enum class FlagshipCategory(val title: String, val icon: ImageVector) {
    ALL("All", Icons.Outlined.GridView),
    CAPTURE("Capture & Quality", Icons.Outlined.CameraAlt),
    VIDEO("Video & Audio", Icons.Outlined.Videocam),
    PROCESSING("Processing & AI", Icons.Outlined.AutoAwesome),
    CONTROLS("Controls & Gestures", Icons.Outlined.TouchApp),
    ADVANCED("Advanced & Labs", Icons.Outlined.Build),
    ABOUT("General / About", Icons.Outlined.Info)
}

/**
 * Redesigned Premium Stock Flagship Camera Settings Sheet.
 * Simple, clean, minimal dark theme matching the camera viewfinder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDrawer(
    isOpen: Boolean,
    cameraMode: CameraMode,
    capabilities: HardwareCapabilities,
    availableLenses: List<LensInfo> = emptyList(),
    selectedLens: LensInfo? = null,
    selectedPhotoResolution: CameraResolution?,
    selectedVideoResolution: CameraResolution?,
    photoMegapixelMode: PhotoMegapixelMode = PhotoMegapixelMode.M12,
    isRefocusPhotoEnabled: Boolean = false,
    refocusFrameCount: Int = 5,
    isHighQualityZoomEnabled: Boolean = true,
    zoomProcessingQuality: com.example.camera.zoom.ZoomProcessingQuality = com.example.camera.zoom.ZoomProcessingQuality.BALANCED,
    videoFps: Int = 30,
    videoBitrate: VideoBitrateOption = VideoBitrateOption.AUTO,
    isVideoStabilizationEnabled: Boolean = true,
    isAudioEnabled: Boolean = true,
    isRawEnabled: Boolean = false,
    saveSelfieAsPreviewed: Boolean = true,
    gridType: GridType = GridType.NONE,
    cinemaConfig: CinemaConfig = CinemaConfig(),
    cinemaCapabilities: CinemaHardwareCapabilities = CinemaHardwareCapabilities(),
    viewfinderResolution: ViewfinderResolution = ViewfinderResolution.NORMAL,
    hybridStabilizationConfig: HybridStabilizationConfig = HybridStabilizationConfig(),
    nightConfig: NightConfig = NightConfig(),
    tapFocusConfig: TapFocusConfig = TapFocusConfig(),
    // Extended Settings State
    videoCodec: String = "HEVC",
    jpegQuality: Int = 95,
    volumeKeyAction: String = "SHUTTER",
    doubleTapAction: String = "FLIP",
    shutterFeedback: String = "SOUND_AND_HAPTIC",
    antibandingMode: String = "AUTO",
    windNoiseReduction: Boolean = true,
    audioSource: String = "CAMCORDER",
    horizonLeveler: Boolean = true,
    viewfinderFps: Int = 60,
    thermalProtection: Boolean = true,
    isAutoHdrEnabled: Boolean = true,
    isAiAutoFramingEnabled: Boolean = false,
    currentZoom: Float = 1.0f,
    exposureCompensation: Int = 0,
    manualIso: Int? = null,
    manualShutterSpeedNs: Long? = null,
    focusMode: FocusMode = FocusMode.CONTINUOUS,
    manualFocusDistance: Float = 0.0f,
    portraitConfig: PortraitConfig = PortraitConfig(),
    selectedPhotoFilter: PhotoFilter = PhotoFilter.ORIGINAL,
    // Callbacks
    onLensSelected: (LensInfo) -> Unit = {},
    onForceDeepScan: () -> Unit = {},
    onPhotoResolutionSelected: (CameraResolution) -> Unit = {},
    onPhotoMegapixelModeSelected: (PhotoMegapixelMode) -> Unit = {},
    onRefocusPhotoToggle: (Boolean) -> Unit = {},
    onRefocusFrameCountChange: (Int) -> Unit = {},
    onHighQualityZoomToggle: (Boolean) -> Unit = {},
    onZoomProcessingQualitySelect: (com.example.camera.zoom.ZoomProcessingQuality) -> Unit = {},
    onVideoResolutionSelected: (CameraResolution) -> Unit = {},
    onViewfinderResolutionSelected: (ViewfinderResolution) -> Unit = {},
    onVideoFpsSelected: (Int) -> Unit = {},
    onVideoBitrateSelected: (VideoBitrateOption) -> Unit = {},
    onStabilizationToggle: (Boolean) -> Unit = {},
    onHybridStabilizationChange: (HybridStabilizationConfig) -> Unit = {},
    onOisToggle: (Boolean) -> Unit = {},
    onUltraStabilizationToggle: () -> Unit = {},
    onNightConfigChange: (NightConfig) -> Unit = {},
    onTapFocusConfigChange: (TapFocusConfig) -> Unit = {},
    onAudioToggle: () -> Unit = {},
    onRawToggle: () -> Unit = {},
    onSaveSelfieAsPreviewedToggle: (Boolean) -> Unit = {},
    onGridTypeSelected: (GridType) -> Unit = {},
    onCinemaConfigChange: (CinemaConfig) -> Unit = {},
    onVideoCodecSelected: (String) -> Unit = {},
    onJpegQualitySelected: (Int) -> Unit = {},
    onVolumeKeyActionSelected: (String) -> Unit = {},
    onDoubleTapActionSelected: (String) -> Unit = {},
    onShutterFeedbackSelected: (String) -> Unit = {},
    onAntibandingModeSelected: (String) -> Unit = {},
    onWindNoiseReductionToggle: (Boolean) -> Unit = {},
    onAudioSourceSelected: (String) -> Unit = {},
    onHorizonLevelerToggle: (Boolean) -> Unit = {},
    onViewfinderFpsSelected: (Int) -> Unit = {},
    onThermalProtectionToggle: (Boolean) -> Unit = {},
    onAutoHdrToggle: (Boolean) -> Unit = {},
    onAiAutoFramingToggle: (Boolean) -> Unit = {},
    onZoomChange: (Float) -> Unit = {},
    onExposureCompensationChange: (Int) -> Unit = {},
    onManualIsoChange: (Int?) -> Unit = {},
    onManualShutterSpeedChange: (Long?) -> Unit = {},
    onFocusModeChange: (FocusMode) -> Unit = {},
    onManualFocusDistanceChange: (Float) -> Unit = {},
    onPortraitConfigChange: (PortraitConfig) -> Unit = {},
    onPhotoFilterSelected: (PhotoFilter) -> Unit = {},
    onResetAllSettings: () -> Unit = {},
    // Custom Image Processing Pipeline
    isCustomPipelineEnabled: Boolean = true,
    activePipelinePreset: com.example.camera.pipeline.model.PipelinePreset = com.example.camera.pipeline.model.PipelinePreset.HASSELBLAD,
    onCustomPipelineToggle: (Boolean) -> Unit = {},
    onSelectPipelinePreset: (com.example.camera.pipeline.model.PipelinePreset) -> Unit = {},
    onOpenPipelineStudio: () -> Unit = {},
    onOpenBeforeAfter: () -> Unit = {},
    // Motorola Instant Camera Switching
    instantSwitchState: MotorolaInstantSwitchState = MotorolaInstantSwitchState(),
    onKeepUltraWideReadyToggle: (Boolean) -> Unit = {},
    onShowUltraWidePreviewToggle: (Boolean) -> Unit = {},
    onKeepFrontCameraReadyToggle: (Boolean) -> Unit = {},
    onShowFrontCameraPreviewToggle: (Boolean) -> Unit = {},
    // UI Customization callbacks
    uiCustomizationState: UiCustomizationState = UiCustomizationState(),
    onSelectTemplate: (UiTemplateType) -> Unit = {},
    onUpdateGlobalLayoutConfig: (ModeLayoutConfig) -> Unit = {},
    onUpdateModeLayoutConfig: (CameraMode, ModeLayoutConfig) -> Unit = { _, _ -> },
    onResetModeLayoutConfig: (CameraMode) -> Unit = {},
    onSaveCustomPreset: (String, ModeLayoutConfig) -> Unit = { _, _ -> },
    onLoadCustomPreset: (CustomUiPreset) -> Unit = {},
    onDeleteCustomPreset: (String) -> Unit = {},
    onResetAllToTemplate: (UiTemplateType) -> Unit = {},
    onOpenCustomUiStudio: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    var selectedFilterCategory by remember { mutableStateOf(FlagshipCategory.ALL) }
    var showResetDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF121316),
        contentColor = Color(0xFFF3F4F6),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0xFF374151))
        },
        modifier = modifier.testTag("settings_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF26210A))
                            .border(1.dp, Color(0xFFFFD54F).copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Text(
                        text = "Camera Settings",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1F2127))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Settings",
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Category Filter Pills
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FlagshipCategory.entries.toTypedArray()) { cat ->
                    val isSelected = selectedFilterCategory == cat
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) Color(0xFFFFD54F) else Color(0xFF1E2026),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) Color(0xFFFFD54F) else Color(0xFF2C2F38)
                        ),
                        modifier = Modifier
                            .clickable { selectedFilterCategory = cat }
                            .testTag("category_pill_${cat.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = cat.icon,
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFF121316) else Color(0xFF9CA3AF),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = cat.title,
                                color = if (isSelected) Color(0xFF121316) else Color(0xFFE5E7EB),
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Settings Content Body
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. CAPTURE & QUALITY
                if (selectedFilterCategory == FlagshipCategory.ALL || selectedFilterCategory == FlagshipCategory.CAPTURE) {
                    item {
                        FlagshipSectionHeader("CAPTURE & QUALITY")
                        FlagshipCard {
                            // Photo Resolution
                            FlagshipRowItem(
                                icon = Icons.Outlined.PhotoSizeSelectActual,
                                title = "Photo Resolution",
                                subtitle = "${photoMegapixelMode.label} · ${selectedPhotoResolution?.let { "${it.width}x${it.height}" } ?: "High Res"}"
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    PhotoMegapixelMode.entries.forEach { mode ->
                                        val isSelected = photoMegapixelMode == mode
                                        FlagshipSmallChip(
                                            label = mode.label,
                                            isSelected = isSelected,
                                            onClick = { onPhotoMegapixelModeSelected(mode) }
                                        )
                                    }
                                }
                            }

                            FlagshipDivider()

                            // RAW Capture (DNG)
                            FlagshipSwitchItem(
                                icon = Icons.Outlined.RawOn,
                                title = "RAW (DNG) Capture",
                                subtitle = "Save 16-bit uncompressed sensor data",
                                checked = isRawEnabled,
                                onCheckedChange = { onRawToggle() }
                            )

                            FlagshipDivider()

                            // JPEG Quality
                            FlagshipRowItem(
                                icon = Icons.Outlined.HighQuality,
                                title = "JPEG Quality",
                                subtitle = "$jpegQuality% compression quality"
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(90, 95, 100).forEach { q ->
                                        FlagshipSmallChip(
                                            label = "$q%",
                                            isSelected = jpegQuality == q,
                                            onClick = { onJpegQualitySelected(q) }
                                        )
                                    }
                                }
                            }

                            FlagshipDivider()

                            // Refocus Photo
                            FlagshipSwitchItem(
                                icon = Icons.Outlined.FilterCenterFocus,
                                title = "Refocus Photo",
                                subtitle = "Burst capture with varying focal depths",
                                checked = isRefocusPhotoEnabled,
                                onCheckedChange = onRefocusPhotoToggle
                            )

                            FlagshipDivider()

                            // Grid Overlay
                            FlagshipRowItem(
                                icon = Icons.Outlined.GridOn,
                                title = "Framing Grid",
                                subtitle = gridType.name.replace("_", " ")
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    GridType.entries.take(4).forEach { gt ->
                                        FlagshipSmallChip(
                                            label = when (gt) {
                                                GridType.NONE -> "Off"
                                                GridType.THIRDS -> "3x3"
                                                GridType.GOLDEN -> "Golden"
                                                GridType.SQUARE -> "1:1"
                                                else -> gt.title
                                            },
                                            isSelected = gridType == gt,
                                            onClick = { onGridTypeSelected(gt) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. VIDEO & AUDIO
                if (selectedFilterCategory == FlagshipCategory.ALL || selectedFilterCategory == FlagshipCategory.VIDEO) {
                    item {
                        FlagshipSectionHeader("VIDEO & AUDIO")
                        FlagshipCard {
                            // Video Resolution
                            FlagshipRowItem(
                                icon = Icons.Outlined.Hd,
                                title = "Video Resolution",
                                subtitle = selectedVideoResolution?.let { "${it.width}x${it.height}" } ?: "4K UHD"
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val is4k = selectedVideoResolution?.width == 3840
                                    val is1080 = selectedVideoResolution?.width == 1920
                                    val is720 = selectedVideoResolution?.width == 1280

                                    FlagshipSmallChip("4K", is4k) {
                                        capabilities.supportedVideoResolutions.firstOrNull { it.width == 3840 }
                                            ?.let { onVideoResolutionSelected(it) }
                                    }
                                    FlagshipSmallChip("1080p", is1080) {
                                        capabilities.supportedVideoResolutions.firstOrNull { it.width == 1920 }
                                            ?.let { onVideoResolutionSelected(it) }
                                    }
                                    FlagshipSmallChip("720p", is720) {
                                        capabilities.supportedVideoResolutions.firstOrNull { it.width == 1280 }
                                            ?.let { onVideoResolutionSelected(it) }
                                    }
                                }
                            }

                            FlagshipDivider()

                            // Frame Rate
                            FlagshipRowItem(
                                icon = Icons.Outlined.Speed,
                                title = "Framerate",
                                subtitle = "$videoFps frames per second"
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(24, 30, 60).forEach { fps ->
                                        FlagshipSmallChip(
                                            label = "${fps}fps",
                                            isSelected = videoFps == fps,
                                            onClick = { onVideoFpsSelected(fps) }
                                        )
                                    }
                                }
                            }

                            FlagshipDivider()

                            // Video Codec
                            FlagshipRowItem(
                                icon = Icons.Outlined.Code,
                                title = "Video Codec",
                                subtitle = if (videoCodec == "HEVC") "HEVC / H.265 (High Efficiency)" else "H.264 (Maximum Compatibility)"
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("HEVC", "AVC").forEach { codec ->
                                        FlagshipSmallChip(
                                            label = codec,
                                            isSelected = videoCodec == codec,
                                            onClick = { onVideoCodecSelected(codec) }
                                        )
                                    }
                                }
                            }

                            FlagshipDivider()

                            // Audio Recording
                            FlagshipSwitchItem(
                                icon = Icons.Outlined.Mic,
                                title = "Record Audio",
                                subtitle = if (isAudioEnabled) "Stereo microphone capture" else "Video muted",
                                checked = isAudioEnabled,
                                onCheckedChange = { onAudioToggle() }
                            )

                            if (isAudioEnabled) {
                                FlagshipDivider()

                                // Wind Noise Reduction
                                FlagshipSwitchItem(
                                    icon = Icons.Outlined.Air,
                                    title = "Wind Noise Reduction",
                                    subtitle = "Hardware microphone frequency filtering",
                                    checked = windNoiseReduction,
                                    onCheckedChange = onWindNoiseReductionToggle
                                )
                            }
                        }
                    }
                }

                // 3. PROCESSING & AI
                if (selectedFilterCategory == FlagshipCategory.ALL || selectedFilterCategory == FlagshipCategory.PROCESSING) {
                    item {
                        FlagshipSectionHeader("PROCESSING & AI")
                        FlagshipCard {
                            // Optical Blur Guided Portrait
                            FlagshipSwitchItem(
                                icon = Icons.Outlined.Portrait,
                                title = "Optical Blur Portrait",
                                subtitle = "Optical defocus estimation & fine hair matting",
                                checked = portraitConfig.opticalBlurGuided,
                                onCheckedChange = { onPortraitConfigChange(portraitConfig.copy(opticalBlurGuided = it)) }
                            )

                            FlagshipDivider()

                            // AI Super-Resolution Zoom
                            FlagshipSwitchItem(
                                icon = Icons.Outlined.ZoomIn,
                                title = "AI Super-Resolution Zoom",
                                subtitle = "Multi-frame subpixel detail enhancement",
                                checked = isHighQualityZoomEnabled,
                                onCheckedChange = onHighQualityZoomToggle
                            )

                            FlagshipDivider()

                            // Auto HDR
                            FlagshipSwitchItem(
                                icon = Icons.Outlined.HdrOn,
                                title = "Auto HDR Fusion",
                                subtitle = "Zero-shutter-lag multi-exposure dynamic range",
                                checked = isAutoHdrEnabled,
                                onCheckedChange = onAutoHdrToggle
                            )

                            FlagshipDivider()

                            // Ultra Action Stabilization
                            FlagshipSwitchItem(
                                icon = Icons.Outlined.MotionPhotosOn,
                                title = "Ultra Action Stabilization",
                                subtitle = "Rock-steady wide gyro EIS for sports & fast movement",
                                checked = hybridStabilizationConfig.isUltraStabilizationEnabled,
                                onCheckedChange = { onUltraStabilizationToggle() }
                            )

                            FlagshipDivider()

                            // Optical Image Stabilization (OIS)
                            FlagshipSwitchItem(
                                icon = Icons.Outlined.Camera,
                                title = "Optical Image Stabilization (OIS)",
                                subtitle = if (capabilities.supportsOis) "Physical voice-coil lens stabilization" else "Sensor does not support hardware OIS",
                                checked = hybridStabilizationConfig.isOisPreferred && capabilities.supportsOis,
                                enabled = capabilities.supportsOis,
                                onCheckedChange = onOisToggle
                            )

                            FlagshipDivider()

                            // Video Stabilization (EIS)
                            FlagshipSwitchItem(
                                icon = Icons.Outlined.VideoStable,
                                title = "Video Stabilization (EIS)",
                                subtitle = "ISP digital sensor frame margin compensation",
                                checked = isVideoStabilizationEnabled && hybridStabilizationConfig.isEisPreferred,
                                onCheckedChange = {
                                    onStabilizationToggle(it)
                                    onHybridStabilizationChange(hybridStabilizationConfig.copy(isEisPreferred = it, isHybridEnabled = it))
                                }
                            )

                            FlagshipDivider()

                            // Cinema Log Profile
                            FlagshipRowItem(
                                icon = Icons.Outlined.MovieFilter,
                                title = "Cinema Log Curve",
                                subtitle = "${cinemaConfig.colorProfile.label} (${cinemaConfig.logBitDepth.label})"
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(CinemaColorProfile.REC_709, CinemaColorProfile.FLAT_LOG, CinemaColorProfile.HLG).forEach { profile ->
                                        FlagshipSmallChip(
                                            label = profile.label,
                                            isSelected = cinemaConfig.colorProfile == profile,
                                            onClick = { onCinemaConfigChange(cinemaConfig.copy(colorProfile = profile)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. CONTROLS & GESTURES
                if (selectedFilterCategory == FlagshipCategory.ALL || selectedFilterCategory == FlagshipCategory.CONTROLS) {
                    item {
                        FlagshipSectionHeader("CONTROLS & GESTURES")
                        FlagshipCard {
                            // Save Selfie As Previewed
                            FlagshipSwitchItem(
                                icon = Icons.Outlined.FlipCameraAndroid,
                                title = "Save Selfie As Previewed",
                                subtitle = "Mirror front camera photos",
                                checked = saveSelfieAsPreviewed,
                                onCheckedChange = onSaveSelfieAsPreviewedToggle
                            )

                            FlagshipDivider()

                            // Volume Key Action
                            FlagshipRowItem(
                                icon = Icons.Outlined.VolumeUp,
                                title = "Volume Key Action",
                                subtitle = volumeKeyAction
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("SHUTTER", "ZOOM", "VOLUME").forEach { action ->
                                        FlagshipSmallChip(
                                            label = action,
                                            isSelected = volumeKeyAction == action,
                                            onClick = { onVolumeKeyActionSelected(action) }
                                        )
                                    }
                                }
                            }

                            FlagshipDivider()

                            // Double-Tap Action
                            FlagshipRowItem(
                                icon = Icons.Outlined.TouchApp,
                                title = "Double-Tap Action",
                                subtitle = if (doubleTapAction == "FLIP") "Switch Front/Rear" else doubleTapAction
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("FLIP", "ZOOM", "NONE").forEach { act ->
                                        FlagshipSmallChip(
                                            label = act,
                                            isSelected = doubleTapAction == act,
                                            onClick = { onDoubleTapActionSelected(act) }
                                        )
                                    }
                                }
                            }

                            FlagshipDivider()

                            // Shutter Feedback
                            FlagshipRowItem(
                                icon = Icons.Outlined.Vibration,
                                title = "Shutter Feedback",
                                subtitle = shutterFeedback.replace("_", " ")
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("SOUND_AND_HAPTIC", "HAPTIC_ONLY", "SILENT").forEach { mode ->
                                        FlagshipSmallChip(
                                            label = when (mode) {
                                                "SOUND_AND_HAPTIC" -> "Both"
                                                "HAPTIC_ONLY" -> "Haptic"
                                                else -> "Silent"
                                            },
                                            isSelected = shutterFeedback == mode,
                                            onClick = { onShutterFeedbackSelected(mode) }
                                        )
                                    }
                                }
                            }

                            FlagshipDivider()

                            // Tap to Focus
                            FlagshipSwitchItem(
                                icon = Icons.Outlined.CenterFocusStrong,
                                title = "Tap to Focus & Meter",
                                subtitle = "Auto-exposure spot metering on focus target",
                                checked = tapFocusConfig.isTapToFocusEnabled,
                                onCheckedChange = { onTapFocusConfigChange(tapFocusConfig.copy(isTapToFocusEnabled = it)) }
                            )
                        }
                    }
                }

                // 5. ADVANCED & LABS
                if (selectedFilterCategory == FlagshipCategory.ALL || selectedFilterCategory == FlagshipCategory.ADVANCED) {
                    item {
                        FlagshipSectionHeader("ADVANCED & LABS")
                        FlagshipCard {
                            // Anti-Banding
                            FlagshipRowItem(
                                icon = Icons.Outlined.WbIncandescent,
                                title = "Anti-Banding",
                                subtitle = "Flicker reduction frequency ($antibandingMode)"
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("AUTO", "50HZ", "60HZ").forEach { mode ->
                                        FlagshipSmallChip(
                                            label = mode,
                                            isSelected = antibandingMode == mode,
                                            onClick = { onAntibandingModeSelected(mode) }
                                        )
                                    }
                                }
                            }

                            FlagshipDivider()

                            // Viewfinder Refresh Rate
                            FlagshipRowItem(
                                icon = Icons.Outlined.Refresh,
                                title = "Viewfinder Refresh Rate",
                                subtitle = "${viewfinderFps}Hz preview stream"
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(60, 120).forEach { rate ->
                                        FlagshipSmallChip(
                                            label = "${rate}Hz",
                                            isSelected = viewfinderFps == rate,
                                            onClick = { onViewfinderFpsSelected(rate) }
                                        )
                                    }
                                }
                            }

                            FlagshipDivider()

                            // Thermal ISP Protection
                            FlagshipSwitchItem(
                                icon = Icons.Outlined.Thermostat,
                                title = "Thermal Protection",
                                subtitle = "Dynamically throttle ISP load during overheating",
                                checked = thermalProtection,
                                onCheckedChange = onThermalProtectionToggle
                            )

                            FlagshipDivider()

                            // Horizon Leveler
                            FlagshipSwitchItem(
                                icon = Icons.Outlined.ScreenRotation,
                                title = "Tilt Horizon Leveler",
                                subtitle = "Sensor gyroscope alignment indicator",
                                checked = horizonLeveler,
                                onCheckedChange = onHorizonLevelerToggle
                            )
                        }
                    }
                }

                // 6. GENERAL / ABOUT
                if (selectedFilterCategory == FlagshipCategory.ALL || selectedFilterCategory == FlagshipCategory.ABOUT) {
                    item {
                        FlagshipSectionHeader("GENERAL / ABOUT")
                        FlagshipCard {
                            // Camera HAL Info
                            FlagshipRowItem(
                                icon = Icons.Outlined.Info,
                                title = "Hardware Support",
                                subtitle = "Camera2 API · ${availableLenses.size} detected lenses · ${if (capabilities.supportsRaw) "RAW supported" else "Standard ISP"}"
                            )

                            FlagshipDivider()

                            // Reset Settings
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showResetDialog = true }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2C1515)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.RestartAlt,
                                            contentDescription = null,
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Reset All Settings",
                                            color = Color(0xFFEF4444),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Restore camera parameters to factory defaults",
                                            color = Color(0xFF9CA3AF),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0xFF6B7280),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = Color(0xFF1C1D22),
            title = {
                Text(
                    text = "Reset Settings?",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will restore all photo, video, processing, and control preferences back to factory defaults.",
                    color = Color(0xFFD1D5DB),
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetAllSettings()
                        showResetDialog = false
                    }
                ) {
                    Text("Reset", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = Color(0xFF9CA3AF))
                }
            }
        )
    }
}

@Composable
private fun FlagshipSectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFF9CA3AF),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun FlagshipCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1A1C22),
        border = BorderStroke(1.dp, Color(0xFF272A34)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun FlagshipDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 14.dp),
        thickness = 0.6.dp,
        color = Color(0xFF262933)
    )
}

@Composable
private fun FlagshipRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF232630)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFFFD54F),
                    modifier = Modifier.size(15.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (action != null) {
            Spacer(modifier = Modifier.width(8.dp))
            action()
        }
    }
}

@Composable
private fun FlagshipSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF232630)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) Color(0xFFFFD54F) else Color(0xFF6B7280),
                    modifier = Modifier.size(15.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    color = if (enabled) Color.White else Color(0xFF9CA3AF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = if (enabled) Color(0xFF9CA3AF) else Color(0xFF6B7280),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF121316),
                checkedTrackColor = Color(0xFFFFD54F),
                uncheckedThumbColor = Color(0xFF9CA3AF),
                uncheckedTrackColor = Color(0xFF272A34),
                disabledCheckedTrackColor = Color(0xFF3F3A22),
                disabledUncheckedTrackColor = Color(0xFF1F2128)
            ),
            modifier = Modifier.scale(0.8f)
        )
    }
}

@Composable
private fun FlagshipSmallChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Color(0xFF26210A) else Color(0xFF232630),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) Color(0xFFFFD54F) else Color.Transparent
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = if (isSelected) Color(0xFFFFD54F) else Color(0xFFD1D5DB),
            fontSize = 10.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}
