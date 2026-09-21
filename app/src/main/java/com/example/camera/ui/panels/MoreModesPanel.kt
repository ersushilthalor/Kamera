package com.example.camera.ui.panels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.model.PhotoMegapixelMode
import com.example.camera.ui.components.FrostedGlassBox

private val AccentOrange = Color(0xFFFF7A00)
private val PanelBg = Color(0xE614161E)

@Composable
fun MoreModesPanel(
    isOpen: Boolean,
    photoMegapixelMode: PhotoMegapixelMode,
    isRefocusPhotoEnabled: Boolean,
    onSelectHiRes50M: () -> Unit,
    onSelectNight: () -> Unit,
    onSelectCinemaLog: () -> Unit,
    onSelectMacro: () -> Unit,
    onSelectDollyZoom: () -> Unit,
    onSelectAiSubjectTracking: () -> Unit,
    onToggleRefocus: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        modifier = modifier
    ) {
        FrostedGlassBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("more_modes_panel"),
            shape = RoundedCornerShape(22.dp),
            elevation = 16.dp,
            baseAlpha = 0.88f,
            baseTint = PanelBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AccentOrange)
                        )
                        Text(
                            text = "MORE CAPTURE MODES",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("close_more_modes_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close More Modes",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Grid Row 1: Hasselblad Hi-Res 50MP & Night Fusion
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val is50MActive = photoMegapixelMode == PhotoMegapixelMode.M50
                    MoreModeCard(
                        icon = Icons.Outlined.HighQuality,
                        title = "Hasselblad Hi-Res",
                        subtitle = if (is50MActive) "50MP Active" else "50MP Ultra Detail",
                        isActive = is50MActive,
                        tag = "mode_card_hires",
                        modifier = Modifier.weight(1f),
                        onClick = onSelectHiRes50M
                    )

                    MoreModeCard(
                        icon = Icons.Outlined.NightsStay,
                        title = "Night Fusion",
                        subtitle = "Computational HDR",
                        isActive = false,
                        tag = "mode_card_night",
                        modifier = Modifier.weight(1f),
                        onClick = onSelectNight
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Grid Row 2: Cinema Log & Macro Close-Up
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MoreModeCard(
                        icon = Icons.Outlined.Movie,
                        title = "Cinema Log",
                        subtitle = "10-Bit Studio Color",
                        isActive = false,
                        tag = "mode_card_cinema_log",
                        modifier = Modifier.weight(1f),
                        onClick = onSelectCinemaLog
                    )

                    MoreModeCard(
                        icon = Icons.Outlined.CenterFocusStrong,
                        title = "Macro Mode",
                        subtitle = "Extreme Optical Lock",
                        isActive = false,
                        tag = "mode_card_macro",
                        modifier = Modifier.weight(1f),
                        onClick = onSelectMacro
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Grid Row 3: Dolly Zoom & AI Tracking
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MoreModeCard(
                        icon = Icons.Outlined.ZoomOutMap,
                        title = "Dolly Zoom",
                        subtitle = "Vertigo Hitchcock",
                        isActive = false,
                        tag = "mode_card_dolly_zoom",
                        modifier = Modifier.weight(1f),
                        onClick = onSelectDollyZoom
                    )

                    MoreModeCard(
                        icon = Icons.Outlined.GpsFixed,
                        title = "AI Tracking",
                        subtitle = "Gyro Gimbal Tracking",
                        isActive = false,
                        tag = "mode_card_ai_tracking",
                        modifier = Modifier.weight(1f),
                        onClick = onSelectAiSubjectTracking
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Grid Row 4: Refocus Photo & Camera System Settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MoreModeCard(
                        icon = Icons.Outlined.PhotoFilter,
                        title = "Refocus Photo",
                        subtitle = if (isRefocusPhotoEnabled) "Multi-Plane Active" else "Post-Focus Planes",
                        isActive = isRefocusPhotoEnabled,
                        tag = "mode_card_refocus",
                        modifier = Modifier.weight(1f),
                        onClick = onToggleRefocus
                    )

                    MoreModeCard(
                        icon = Icons.Outlined.Settings,
                        title = "System Settings",
                        subtitle = "Hardware & Advanced",
                        isActive = false,
                        tag = "mode_card_settings",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreModeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isActive: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isActive) AccentOrange.copy(alpha = 0.18f)
                else Color.White.copy(alpha = 0.05f)
            )
            .border(
                1.dp,
                if (isActive) AccentOrange else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(tag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) AccentOrange else Color.White.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    color = if (isActive) AccentOrange else Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        }
    }
}
