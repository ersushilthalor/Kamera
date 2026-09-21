package com.example.camera.ui.panels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.camera.model.CameraAspectRatio
import com.example.camera.model.FlashMode
import com.example.camera.model.GridType
import com.example.camera.model.TimerMode
import com.example.camera.ui.components.FrostedGlassBox

private val AccentOrange = Color(0xFFFF7A00)
private val PanelBg = Color(0xE614161E)

@Composable
fun QuickSettingsPanel(
    isOpen: Boolean,
    flashMode: FlashMode,
    timerMode: TimerMode,
    aspectRatio: CameraAspectRatio,
    gridType: GridType,
    isRawEnabled: Boolean,
    isLevelerEnabled: Boolean,
    onFlashSelected: (FlashMode) -> Unit,
    onTimerSelected: (TimerMode) -> Unit,
    onAspectRatioSelected: (CameraAspectRatio) -> Unit,
    onGridTypeSelected: (GridType) -> Unit,
    onToggleRaw: () -> Unit,
    onToggleLeveler: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
        modifier = modifier
    ) {
        FrostedGlassBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .testTag("quick_settings_panel"),
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
                // Header
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
                            text = "QUICK CAMERA SETTINGS",
                            color = AccentOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("close_quick_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Quick Settings",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 1: Flash Mode
                SettingRow(title = "Flash") {
                    listOf(
                        FlashMode.OFF to "OFF",
                        FlashMode.AUTO to "AUTO",
                        FlashMode.ON to "ON",
                        FlashMode.TORCH to "TORCH"
                    ).forEach { (mode, label) ->
                        val isSelected = flashMode == mode
                        PillChip(
                            label = label,
                            isSelected = isSelected,
                            tag = "flash_$label",
                            onClick = { onFlashSelected(mode) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Section 2: Timer
                SettingRow(title = "Timer") {
                    listOf(
                        TimerMode.OFF to "OFF",
                        TimerMode.SEC_3 to "3s",
                        TimerMode.SEC_5 to "5s",
                        TimerMode.SEC_10 to "10s"
                    ).forEach { (mode, label) ->
                        val isSelected = timerMode == mode
                        PillChip(
                            label = label,
                            isSelected = isSelected,
                            tag = "timer_$label",
                            onClick = { onTimerSelected(mode) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Section 3: Aspect Ratio
                SettingRow(title = "Ratio") {
                    listOf(
                        CameraAspectRatio.RATIO_4_3 to "4:3",
                        CameraAspectRatio.RATIO_16_9 to "16:9",
                        CameraAspectRatio.RATIO_1_1 to "1:1",
                        CameraAspectRatio.RATIO_FULL to "FULL"
                    ).forEach { (ratio, label) ->
                        val isSelected = aspectRatio == ratio
                        PillChip(
                            label = label,
                            isSelected = isSelected,
                            tag = "ratio_$label",
                            onClick = { onAspectRatioSelected(ratio) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Section 4: Grid & Guides
                SettingRow(title = "Grid") {
                    listOf(
                        GridType.NONE to "OFF",
                        GridType.THIRDS to "3×3",
                        GridType.GOLDEN to "PHI",
                        GridType.SQUARE to "1:1"
                    ).forEach { (grid, label) ->
                        val isSelected = gridType == grid
                        PillChip(
                            label = label,
                            isSelected = isSelected,
                            tag = "grid_$label",
                            onClick = { onGridTypeSelected(grid) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Section 5: Horizon Leveler & RAW Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = isLevelerEnabled,
                        onClick = onToggleLeveler,
                        label = {
                            Text(
                                text = if (isLevelerEnabled) "HORIZON LEVEL ON" else "HORIZON LEVEL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentOrange,
                            selectedLabelColor = Color.Black,
                            containerColor = Color.White.copy(alpha = 0.08f),
                            labelColor = Color.White.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier.weight(1f).testTag("quick_leveler_chip")
                    )

                    FilterChip(
                        selected = isRawEnabled,
                        onClick = onToggleRaw,
                        label = {
                            Text(
                                text = if (isRawEnabled) "RAW DNG + JPG" else "JPEG ONLY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentOrange,
                            selectedLabelColor = Color.Black,
                            containerColor = Color.White.copy(alpha = 0.08f),
                            labelColor = Color.White.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier.weight(1f).testTag("quick_raw_chip")
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(50.dp)
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun PillChip(
    label: String,
    isSelected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) AccentOrange else Color.White.copy(alpha = 0.08f))
            .border(
                1.dp,
                if (isSelected) AccentOrange else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else Color.White,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 11.sp
        )
    }
}
