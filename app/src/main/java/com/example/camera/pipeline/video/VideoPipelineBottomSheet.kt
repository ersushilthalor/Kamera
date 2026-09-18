package com.example.camera.pipeline.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
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
import com.example.camera.viewmodel.CameraViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPipelineBottomSheet(
    viewModel: CameraViewModel,
    onDismissRequest: () -> Unit
) {
    val isEnabled by viewModel.isVideoPipelineEnabled.collectAsState()
    val activePipeline by viewModel.activeVideoPipeline.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF14161C),
        contentColor = Color.White,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.35f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Video Processing Pipeline",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(activePipeline.accentColor.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "REAL ISP",
                                color = activePipeline.accentColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                    Text(
                        text = "Hardware ISP capture → tone mapping → color matrix → encoding",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.5.sp
                        )
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { viewModel.toggleVideoPipelineEnabled(it) },
                    modifier = Modifier.testTag("video_pipeline_master_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = activePipeline.accentColor,
                        checkedTrackColor = activePipeline.accentColor.copy(alpha = 0.4f)
                    )
                )
            }

            AnimatedVisibility(visible = isEnabled) {
                Column {
                    Text(
                        text = "SELECT VIDEO PIPELINE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = activePipeline.accentColor
                        ),
                        modifier = Modifier.padding(bottom = 8.dp, top = 4.dp)
                    )

                    // 3 Selectable Pipeline Cards
                    VideoPipelineType.SELECTABLE_PIPELINES.forEach { pipeline ->
                        val isSelected = pipeline == activePipeline
                        val cardBg = if (isSelected) Color(0xFF1E232E) else Color(0xFF181A22)
                        val borderColor = if (isSelected) pipeline.accentColor else Color.White.copy(alpha = 0.10f)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
                                .clickable { viewModel.selectVideoPipeline(pipeline) }
                                .testTag("video_pipeline_card_${pipeline.id}"),
                            colors = CardDefaults.cardColors(containerColor = cardBg)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) pipeline.accentColor.copy(alpha = 0.25f) else Color(0xFF262832)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (pipeline) {
                                            VideoPipelineType.IPHONE -> Icons.Outlined.PhoneIphone
                                            VideoPipelineType.DSLR -> Icons.Outlined.CameraAlt
                                            VideoPipelineType.SAMSUNG -> Icons.Outlined.AutoAwesome
                                            else -> Icons.Outlined.Videocam
                                        },
                                        contentDescription = null,
                                        tint = if (isSelected) pipeline.accentColor else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = pipeline.displayName,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.9f)
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "· ${pipeline.subtitle}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = if (isSelected) pipeline.accentColor else Color.White.copy(alpha = 0.5f),
                                                fontSize = 11.5.sp
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = pipeline.description,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    )
                                }

                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(pipeline.accentColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Active Pipeline Technical Specifications Breakdown
                    val characteristics = when (activePipeline) {
                        VideoPipelineType.IPHONE -> IPhoneVideoPipeline().getCharacteristics()
                        VideoPipelineType.DSLR -> DslrVideoPipeline().getCharacteristics()
                        VideoPipelineType.SAMSUNG -> SamsungVideoPipeline().getCharacteristics()
                        else -> IPhoneVideoPipeline().getCharacteristics()
                    }

                    Text(
                        text = "PIPELINE ARCHITECTURE SPECIFICATIONS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = activePipeline.accentColor
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF191B24),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            SpecRow("Tone Mapping", characteristics.toneMapping, activePipeline.accentColor)
                            SpecDivider()
                            SpecRow("Highlights & Shadows", characteristics.highlightShadow, activePipeline.accentColor)
                            SpecDivider()
                            SpecRow("Dynamic Range", characteristics.dynamicRange, activePipeline.accentColor)
                            SpecDivider()
                            SpecRow("Contrast Curve", characteristics.contrastCurve, activePipeline.accentColor)
                            SpecDivider()
                            SpecRow("Color Science", characteristics.colorScience, activePipeline.accentColor)
                            SpecDivider()
                            SpecRow("White Balance", characteristics.whiteBalance, activePipeline.accentColor)
                            SpecDivider()
                            SpecRow("Sharpening / Edge", characteristics.sharpeningDetail, activePipeline.accentColor)
                            SpecDivider()
                            SpecRow("Noise Reduction", characteristics.noiseReduction, activePipeline.accentColor)
                            SpecDivider()
                            SpecRow("Encoder Bitrate", characteristics.encodingOutput, activePipeline.accentColor)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = !isEnabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1F28)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Standard Android camera HAL pipeline active. Enable the master switch to route video frames through the iPhone, DSLR, or Samsung hardware ISP processing engines.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecRow(title: String, detail: String, accentColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = accentColor,
                fontSize = 11.sp
            ),
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                lineHeight = 15.sp
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SpecDivider() {
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.05f),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
