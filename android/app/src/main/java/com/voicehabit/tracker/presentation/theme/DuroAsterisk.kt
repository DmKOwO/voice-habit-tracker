package com.voicehabit.tracker.presentation.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Minimalist dairy Brand Logo: 4-Point Celestial Spark / Diamond
 */
@Composable
fun DairyLogo(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    color: Color = AppTheme.colors.accent
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f

        val path = Path().apply {
            moveTo(cx, 0f)
            cubicTo(cx, cy * 0.45f, cx * 1.55f, cy, w, cy)
            cubicTo(cx * 1.55f, cy, cx, cy * 1.55f, cx, h)
            cubicTo(cx, cy * 1.55f, cx * 0.45f, cy, 0f, cy)
            cubicTo(cx * 0.45f, cy, cx, cy * 0.45f, cx, 0f)
            close()
        }

        drawPath(path = path, color = color)
    }
}

/**
 * Compatibility bridge
 */
@Composable
fun DuroAsterisk(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = AppTheme.colors.accent,
    strokeWidth: Float = 5f
) {
    DairyLogo(modifier = modifier, size = size, color = color)
}
