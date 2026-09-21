package com.example.camera.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.model.HollywoodColorGrade
import com.example.camera.ui.components.FrostedGlassBox

/**
 * Liquid Glass Floating Hollywood-style Colour Grading Selector Bar.
 * - Live real-time preview
 * - 10 Hollywood presets + Off
 * - 0% to 100% Grade Intensity Slider
 * - Clean filmic UI matching Cinema Mode aesthetics
 */
@Composable
fun HollywoodGradeSelectorBar(
    selectedGrade: HollywoodColorGrade,
    intensity: Float,
    onGradeSelected: (HollywoodColorGrade) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeAccent = if (selectedGrade != HollywoodColorGrade.OFF) selectedGrade.accentColor else Color(0xFFFFD54F)

    FrostedGlassBox(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("hollywood_grade_selector_bar"),
        shape = RoundedCornerShape(26.dp),
        elevation = 20.dp,
        baseAlpha = 0.86f,
        baseTint = Color(0xFF0F121C)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(activeAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HOLLYWOOD",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GRADE",
                        color = activeAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Active grade badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(activeAccent.copy(alpha = 0.15f))
                            .border(1.dp, activeAccent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (selectedGrade == HollywoodColorGrade.OFF) "OFF"
                            else "${selectedGrade.displayName} • ${(intensity * 100).toInt()}%",
                            color = activeAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Close button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable(onClick = onClose)
                            .testTag("hollywood_bar_close_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close Hollywood Grades",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Presets Horizontal Scroll
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HollywoodColorGrade.entries.forEach { grade ->
                    val isSelected = (grade == selectedGrade)
                    HollywoodGradeItem(
                        grade = grade,
                        isSelected = isSelected,
                        onClick = { onGradeSelected(grade) }
                    )
                }
            }

            // Grade Intensity Slider & Details
            AnimatedVisibility(visible = selectedGrade != HollywoodColorGrade.OFF) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "GRADE INTENSITY",
                            color = Color.White.copy(alpha = 0.70f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "${(intensity * 100).toInt()}%",
                            color = activeAccent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Slider(
                        value = intensity,
                        onValueChange = onIntensityChange,
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = activeAccent,
                            activeTrackColor = activeAccent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("hollywood_intensity_slider")
                    )

                    Text(
                        text = "${selectedGrade.subtitle}: ${selectedGrade.description}",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HollywoodGradeItem(
    grade: HollywoodColorGrade,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accent = grade.accentColor
    val borderColor = if (isSelected) accent else Color.White.copy(alpha = 0.12f)
    val bgColor = if (isSelected) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
            .testTag("hollywood_grade_chip_${grade.id}"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accent, accent.copy(alpha = 0.6f))
                        )
                    )
            )
            Text(
                text = grade.displayName,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                letterSpacing = 0.2.sp
            )
        }
    }
}
