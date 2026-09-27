package com.voicehabit.tracker.presentation.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Authentic Duro 8-point Asterisk / Flower Brand Logo
 */
@Composable
fun DuroAsterisk(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = DuroOrange,
    strokeWidth: Float = 5f
) {
    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension / 2f * 0.88f
        val innerCircleRadius = radius * 0.28f

        // Draw center circle
        drawCircle(
            color = color,
            radius = innerCircleRadius,
            center = center
        )

        // Draw 8 spokes at 45 degree intervals
        for (i in 0 until 8) {
            val angleRad = Math.toRadians((i * 45.0))
            val start = Offset(
                x = center.x + (innerCircleRadius * 0.5f * cos(angleRad)).toFloat(),
                y = center.y + (innerCircleRadius * 0.5f * sin(angleRad)).toFloat()
            )
            val end = Offset(
                x = center.x + (radius * cos(angleRad)).toFloat(),
                y = center.y + (radius * sin(angleRad)).toFloat()
            )
            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}
