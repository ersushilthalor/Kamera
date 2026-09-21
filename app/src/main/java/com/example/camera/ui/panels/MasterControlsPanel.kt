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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.model.FocusMode
import com.example.camera.model.HardwareCapabilities
import com.example.camera.model.WhiteBalanceMode
import com.example.camera.ui.components.FrostedGlassBox
import com.example.camera.viewmodel.ProControlTab
import kotlin.math.roundToInt

private val AccentOrange = Color(0xFFFF7A00)
private val PanelBg = Color(0xE614161E)

@Composable
fun MasterControlsPanel(
    isOpen: Boolean,
    capabilities: HardwareCapabilities,
    exposureCompensation: Int,
    manualIso: Int?,
    manualShutterSpeedNs: Long?,
    whiteBalance: WhiteBalanceMode,
    focusMode: FocusMode,
    manualFocusDistance: Float,
    isAeLocked: Boolean,
    isAfLocked: Boolean,
    isRawEnabled: Boolean,
    onExposureChange: (Int) -> Unit,
    onIsoChange: (Int?) -> Unit,
    onShutterChange: (Long?) -> Unit,
    onWbChange: (WhiteBalanceMode) -> Unit,
    onFocusModeChange: (FocusMode) -> Unit,
    onFocusDistanceChange: (Float) -> Unit,
    onToggleAeLock: () -> Unit,
    onToggleAfLock: () -> Unit,
    onToggleRaw: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(ProControlTab.EXPOSURE) }

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
                .testTag("master_controls_panel"),
            shape = RoundedCornerShape(22.dp),
            elevation = 16.dp,
            baseAlpha = 0.85f,
            baseTint = PanelBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
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
                            text = "MASTER PRO",
                            color = AccentOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // RAW Chip
                        FilterChip(
                            selected = isRawEnabled,
                            onClick = onToggleRaw,
                            label = {
                                Text(
                                    text = if (isRawEnabled) "RAW+JPG" else "JPG",
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
                            modifier = Modifier.testTag("master_raw_chip")
                        )

                        // AE Lock
                        FilterChip(
                            selected = isAeLocked,
                            onClick = onToggleAeLock,
                            label = {
                                Text(
                                    text = if (isAeLocked) "AE LOCK" else "AE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isAeLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentOrange,
                                selectedLabelColor = Color.Black,
                                selectedLeadingIconColor = Color.Black,
                                containerColor = Color.White.copy(alpha = 0.08f),
                                labelColor = Color.White.copy(alpha = 0.8f)
                            ),
                            modifier = Modifier.testTag("master_ae_lock_chip")
                        )

                        // AF Lock
                        FilterChip(
                            selected = isAfLocked,
                            onClick = onToggleAfLock,
                            label = {
                                Text(
                                    text = if (isAfLocked) "AF LOCK" else "AF",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isAfLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentOrange,
                                selectedLabelColor = Color.Black,
                                selectedLeadingIconColor = Color.Black,
                                containerColor = Color.White.copy(alpha = 0.08f),
                                labelColor = Color.White.copy(alpha = 0.8f)
                            ),
                            modifier = Modifier.testTag("master_af_lock_chip")
                        )

                        // Dismiss button
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("close_master_controls_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Master Controls",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Active Tab Content Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (selectedTab) {
                        ProControlTab.EXPOSURE -> {
                            val step = capabilities.exposureCompensationStep
                            val evVal = exposureCompensation * step
                            val sign = if (evVal > 0) "+" else ""
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${sign}%.1f EV".format(evVal),
                                    color = if (exposureCompensation != 0) AccentOrange else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Slider(
                                    value = exposureCompensation.toFloat(),
                                    onValueChange = { onExposureChange(it.roundToInt()) },
                                    valueRange = capabilities.minExposureCompensation.toFloat()..capabilities.maxExposureCompensation.toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = AccentOrange,
                                        activeTrackColor = AccentOrange,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("master_ev_slider")
                                )
                            }
                        }

                        ProControlTab.ISO -> {
                            val isoPresets = listOf(null, 50, 100, 200, 400, 800, 1600, 3200, 6400)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                isoPresets.forEach { isoVal ->
                                    val isSelected = manualIso == isoVal
                                    val label = if (isoVal == null) "AUTO" else "$isoVal"
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) AccentOrange else Color.White.copy(alpha = 0.1f))
                                            .border(
                                                1.dp,
                                                if (isSelected) AccentOrange else Color.White.copy(alpha = 0.15f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable { onIsoChange(isoVal) }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                            .testTag("iso_preset_$label"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        ProControlTab.SHUTTER -> {
                            val shutterPresets = listOf(
                                null to "AUTO",
                                125_000L to "1/8000s",
                                250_000L to "1/4000s",
                                500_000L to "1/2000s",
                                1_000_000L to "1/1000s",
                                2_000_000L to "1/500s",
                                4_000_000L to "1/250s",
                                8_000_000L to "1/125s",
                                16_666_667L to "1/60s",
                                33_333_333L to "1/30s",
                                66_666_667L to "1/15s",
                                125_000_000L to "1/8s",
                                250_000_000L to "1/4s",
                                500_000_000L to "1/2s",
                                1_000_000_000L to "1s",
                                2_000_000_000L to "2s"
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                shutterPresets.forEach { (nsVal, label) ->
                                    val isSelected = manualShutterSpeedNs == nsVal
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) AccentOrange else Color.White.copy(alpha = 0.1f))
                                            .border(
                                                1.dp,
                                                if (isSelected) AccentOrange else Color.White.copy(alpha = 0.15f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable { onShutterChange(nsVal) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                            .testTag("shutter_preset_$label"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        ProControlTab.WB -> {
                            val wbModes = WhiteBalanceMode.entries
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                wbModes.forEach { mode ->
                                    val isSelected = whiteBalance == mode
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) AccentOrange else Color.White.copy(alpha = 0.1f))
                                            .border(
                                                1.dp,
                                                if (isSelected) AccentOrange else Color.White.copy(alpha = 0.15f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable { onWbChange(mode) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                            .testTag("wb_preset_${mode.name}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = mode.title,
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        ProControlTab.FOCUS -> {
                            val isManual = focusMode == FocusMode.MANUAL
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(
                                            FocusMode.CONTINUOUS to "AF-C",
                                            FocusMode.AUTO to "AF-S",
                                            FocusMode.MACRO to "MACRO",
                                            FocusMode.MANUAL to "MF"
                                        ).forEach { (fMode, label) ->
                                            val isSel = focusMode == fMode
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (isSel) AccentOrange else Color.White.copy(alpha = 0.1f))
                                                    .clickable { onFocusModeChange(fMode) }
                                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = if (isSel) Color.Black else Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }

                                    if (isManual) {
                                        Text(
                                            text = "%.1f D".format(manualFocusDistance),
                                            color = AccentOrange,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (isManual) {
                                    val maxDist = if (capabilities.minFocusDistance > 0f) capabilities.minFocusDistance else 10f
                                    Slider(
                                        value = manualFocusDistance,
                                        onValueChange = onFocusDistanceChange,
                                        valueRange = 0f..maxDist,
                                        colors = SliderDefaults.colors(
                                            thumbColor = AccentOrange,
                                            activeTrackColor = AccentOrange,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        ProControlTab.TONE -> {
                            Text(
                                text = "RAW 10-Bit Linear Processing Active",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Tab Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProControlTab.entries.forEach { tab ->
                        val isSelected = tab == selectedTab
                        val tabName = when (tab) {
                            ProControlTab.EXPOSURE -> "EV"
                            ProControlTab.ISO -> "ISO"
                            ProControlTab.SHUTTER -> "S"
                            ProControlTab.WB -> "WB"
                            ProControlTab.FOCUS -> "FOCUS"
                            ProControlTab.TONE -> "TONE"
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) AccentOrange.copy(alpha = 0.2f)
                                    else Color.Transparent
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) AccentOrange else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedTab = tab }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .testTag("master_tab_${tab.name}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabName,
                                color = if (isSelected) AccentOrange else Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
