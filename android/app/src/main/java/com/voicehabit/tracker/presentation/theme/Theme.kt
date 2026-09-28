package com.voicehabit.tracker.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

private val DairyShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VoiceHabitTrackerTheme(
    preset: AppThemePreset = AppThemePreset.NOIR_INK,
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val palette = getPaletteForPreset(preset, amoled)

    val colorScheme = darkColorScheme(
        primary = palette.accent,
        onPrimary = if (preset == AppThemePreset.NOIR_INK) Color(0xFF101014) else Color.White,
        secondary = palette.accentSecondary,
        onSecondary = palette.textPrimary,
        tertiary = palette.accent,
        background = palette.background,
        surface = palette.surface,
        surfaceVariant = palette.surfaceElevated,
        outline = palette.border,
        error = DairyDanger,
        onBackground = palette.textPrimary,
        onSurface = palette.textPrimary
    )

    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalAppPalette provides palette,
        LocalDensity provides Density(
            density = density.density,
            fontScale = fontScale
        )
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = DairyShapes,
            content = content
        )
    }
}

@Composable
fun duroTextFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppTheme.colors.accent,
    unfocusedBorderColor = AppTheme.colors.border,
    focusedTextColor = AppTheme.colors.textPrimary,
    unfocusedTextColor = AppTheme.colors.textPrimary,
    focusedContainerColor = AppTheme.colors.surface,
    unfocusedContainerColor = AppTheme.colors.surface
)
