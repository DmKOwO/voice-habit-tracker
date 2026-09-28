package com.voicehabit.tracker.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppThemePreset(
    val id: String,
    val title: String,
    val subtitle: String
) {
    NOIR_INK("noir_ink", "Noir & Ink", "Editorial монохром"),
    MARS_RUST("mars_rust", "Mars Rust", "Швейцарский терракот"),
    ESPRESSO("espresso", "Espresso", "Dark Academia винтаж"),
    MIDNIGHT_SPARK("midnight_spark", "Midnight Spark", "Глубокая лаванда");

    companion object {
        fun fromId(id: String): AppThemePreset = entries.firstOrNull { it.id == id } ?: NOIR_INK
    }
}

data class ThemePalette(
    val id: String,
    val name: String,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val accent: Color,
    val accentSecondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val error: Color = Color(0xFFEF4444),
    val isAmoled: Boolean = false
) {
    fun toAmoled(): ThemePalette = copy(
        background = Color.Black,
        surface = Color(0xFF0D0D10),
        surfaceElevated = Color(0xFF141418),
        border = border.copy(alpha = 0.5f),
        isAmoled = true
    )
}

val NoirInkPalette = ThemePalette(
    id = "noir_ink",
    name = "Noir & Ink",
    background = Color(0xFF0F0F11),
    surface = Color(0xFF17171B),
    surfaceElevated = Color(0xFF1F1F24),
    border = Color(0xFF2A2A32),
    accent = Color(0xFFFFFFFF),
    accentSecondary = Color(0xFFE2DFD8),
    textPrimary = Color(0xFFF4F1EA),
    textSecondary = Color(0xFFA8A5A0),
    textMuted = Color(0xFF7A7876)
)

val MarsRustPalette = ThemePalette(
    id = "mars_rust",
    name = "Mars Rust",
    background = Color(0xFF100E0E),
    surface = Color(0xFF1A1615),
    surfaceElevated = Color(0xFF241E1C),
    border = Color(0xFF2F2624),
    accent = Color(0xFFDE6B48),
    accentSecondary = Color(0xFFD45B3E),
    textPrimary = Color(0xFFF5EBE6),
    textSecondary = Color(0xFFA89993),
    textMuted = Color(0xFF7D706B)
)

val EspressoPalette = ThemePalette(
    id = "espresso",
    name = "Espresso",
    background = Color(0xFF181312),
    surface = Color(0xFF221B19),
    surfaceElevated = Color(0xFF2D2321),
    border = Color(0xFF382D2A),
    accent = Color(0xFFC7A774),
    accentSecondary = Color(0xFFB5935E),
    textPrimary = Color(0xFFEFE7DC),
    textSecondary = Color(0xFFA6978C),
    textMuted = Color(0xFF8A7B72)
)

val MidnightSparkPalette = ThemePalette(
    id = "midnight_spark",
    name = "Midnight Spark",
    background = Color(0xFF0E0B14),
    surface = Color(0xFF161220),
    surfaceElevated = Color(0xFF1F192C),
    border = Color(0xFF2B223D),
    accent = Color(0xFFB8A5E3),
    accentSecondary = Color(0xFF9D84D6),
    textPrimary = Color(0xFFF0EEF5),
    textSecondary = Color(0xFFA39CB5),
    textMuted = Color(0xFF6F6782)
)

fun getPaletteForPreset(preset: AppThemePreset, amoled: Boolean = false): ThemePalette {
    val base = when (preset) {
        AppThemePreset.NOIR_INK -> NoirInkPalette
        AppThemePreset.MARS_RUST -> MarsRustPalette
        AppThemePreset.ESPRESSO -> EspressoPalette
        AppThemePreset.MIDNIGHT_SPARK -> MidnightSparkPalette
    }
    return if (amoled) base.toAmoled() else base
}

val LocalAppPalette = staticCompositionLocalOf { NoirInkPalette }

object AppTheme {
    val colors: ThemePalette
        @Composable
        @ReadOnlyComposable
        get() = LocalAppPalette.current
}
