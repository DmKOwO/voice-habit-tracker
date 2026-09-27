package com.voicehabit.tracker.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryIndigo,
    secondary = SecondaryEmerald,
    tertiary = AccentAmber,
    background = DarkBackground,
    surface = SurfaceDark,
    error = ErrorRed,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

private val DuroShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val AmoledColorScheme = darkColorScheme(
    primary = PrimaryIndigo,
    secondary = SecondaryEmerald,
    tertiary = AccentAmber,
    background = androidx.compose.ui.graphics.Color.Black,
    surface = androidx.compose.ui.graphics.Color.Black,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF0A0A0A),
    error = ErrorRed,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun VoiceHabitTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    // The app intentionally renders the same deep-dark Duro identity in both
    // system modes, while still honoring Material 3 tonal roles for surfaces.
    // AMOLED-режим гасит фоны до чистого чёрного (экономия батареи на OLED).
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
            density = density.density,
            fontScale = fontScale
        )
    ) {
        MaterialTheme(
            colorScheme = if (amoled) AmoledColorScheme else DarkColorScheme,
            typography = Typography,
            shapes = DuroShapes,
            content = content
        )
    }
}
