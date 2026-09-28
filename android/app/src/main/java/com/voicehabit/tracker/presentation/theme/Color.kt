package com.voicehabit.tracker.presentation.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// dairy Editorial Design System (Zero AI-slop, Zero harsh neon)
// ============================================================================

// Base Editorial Noir tokens (Default)
val DairyBackground = Color(0xFF0F0F11)
val DairySurface = Color(0xFF17171B)
val DairySurfaceElevated = Color(0xFF1F1F24)
val DairyBorder = Color(0xFF2A2A32)
val DairyAccent = Color(0xFFFFFFFF)
val DairyAccentWarm = Color(0xFFF4F1EA)

// Mars Rust Terracotta
val MarsRustAccent = Color(0xFFDE6B48)
val MarsRustDark = Color(0xFF100E0E)

// Dark Academia Antique Gold
val EspressoAccent = Color(0xFFC7A774)
val EspressoDark = Color(0xFF181312)

// Midnight Celestial Lavender
val MidnightAccent = Color(0xFFB8A5E3)
val MidnightDark = Color(0xFF0E0B14)

// Functional & State Indicators (Muted, sophisticated)
val DairySuccess = Color(0xFF5BA872)
val DairyWarning = Color(0xFFD69E2E)
val DairyDanger = Color(0xFFCF5C5C)
val DairyMuted = Color(0xFF7A7876)

// ============================================================================
// Backward compatibility bridge (Old neon orange Duro colors completely purged)
// ============================================================================
val DuroBackground = DairyBackground
val DuroSurface = DairySurface
val DuroSurfaceElevated = DairySurfaceElevated
val DuroBorder = DairyBorder

// DuroOrange purged: redirected to refined ivory/white accent
val DuroOrange = DairyAccentWarm
val DuroJournalLavender = Color(0xFFB8A5E3)
// DuroCyan blue purged: redirected to refined warm ivory/accent
val DuroCyan = DairyAccentWarm
val DuroLime = Color(0xFF73D216)
val DuroPink = Color(0xFFE06C75)
val DuroAmber = Color(0xFFD19A66)
val DuroRed = DairyDanger
// DuroPurple purged: redirected to Midnight celestial lavender
val DuroPurple = MidnightAccent

val DuroTextPrimary = Color(0xFFF4F1EA)
val DuroTextSecondary = Color(0xFFA8A5A0)
val DuroTextMuted = Color(0xFF7A7876)
val DuroTabInactive = Color(0xFF636166)

// Compatibility aliases
val DarkBackground = DairyBackground
val SurfaceDark = DairySurface
val SurfaceCard = DairySurfaceElevated
val PrimaryIndigo = DairyAccentWarm
val SecondaryEmerald = DairySuccess
val AccentAmber = DairyWarning
val ErrorRed = DairyDanger
val TextPrimary = DuroTextPrimary
val TextSecondary = DuroTextSecondary
val TextMuted = DuroTextMuted
val WaveformActive = DairyAccentWarm
