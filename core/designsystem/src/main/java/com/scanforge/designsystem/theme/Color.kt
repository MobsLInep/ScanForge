package com.scanforge.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * ScanForge raw palette.
 *
 * Brand language: "forging clarity out of paper" — molten amber accents on cool slate.
 * The amber [Forge] colour drives all primary CTAs / the capture ring / active states, so it
 * maps to the Material 3 `primary` role. Slate indigo is the brand/surface tint, teal handles
 * processing + success pulses.
 */

// ── Forge amber (primary / CTA / capture ring / active) ───────────────────────
val Forge10 = Color(0xFF2A1603)
val Forge20 = Color(0xFF45290C)
val Forge30 = Color(0xFF633F18)
val Forge40 = Color(0xFF855729)
val Forge50 = Color(0xFFA8743E)
val Forge60 = Color(0xFFCB9255)   // light-theme primary
val Forge70 = Color(0xFFF2A65A)   // ★ brand amber — dark-theme primary
val Forge80 = Color(0xFFF8C089)
val Forge90 = Color(0xFFFFDDBA)
val Forge95 = Color(0xFFFFEEDD)

// ── Slate indigo (brand / surface tint / tertiary) ────────────────────────────
val Slate05 = Color(0xFF0E1116)   // ★ dark base surface
val Slate10 = Color(0xFF12161C)
val Slate15 = Color(0xFF161B23)
val Slate20 = Color(0xFF1A1F29)   // ★ elevated dark surface
val Slate25 = Color(0xFF202735)
val Slate30 = Color(0xFF2D3A5F)   // ★ primary seed slate indigo
val Slate40 = Color(0xFF3E4E78)
val Slate60 = Color(0xFF8893B5)
val Slate80 = Color(0xFFC3CBE0)
val Slate90 = Color(0xFFDEE3F2)
val Slate95 = Color(0xFFEEF1F9)
val Slate99 = Color(0xFFFBFCFF)

// ── Teal-cyan (secondary / processing + success pulses) ───────────────────────
val Teal20 = Color(0xFF06352F)
val Teal30 = Color(0xFF0C4F47)
val Teal40 = Color(0xFF146B61)
val Teal60 = Color(0xFF2E938A)
val Teal70 = Color(0xFF3FB8AF)   // ★ brand teal
val Teal80 = Color(0xFF7FD6CF)
val Teal90 = Color(0xFFB8ECE7)

// ── Error ─────────────────────────────────────────────────────────────────────
val Error30 = Color(0xFF7A1418)
val Error40 = Color(0xFFA51E23)
val Error70 = Color(0xFFE5484D)  // ★ brand error
val Error80 = Color(0xFFF28A8D)
val Error90 = Color(0xFFFFDAD9)

/**
 * Extended ScanForge colour roles that Material 3 doesn't model
 * (success / warning / processing) plus brand-specific surfaces.
 * Accessed via `ScanForgeTheme.colors`.
 */
@Immutable
data class ScanForgeColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val processing: Color,
    /** Amber sweep used by the capture ring / scan-line effect. */
    val scanBeam: Color,
    /** Slate indigo brand accent. */
    val brand: Color,
    /** 1px "machined edge" hairline on dark surfaces. */
    val hairline: Color,
    /** Base of the shimmer gradient for loading placeholders. */
    val shimmerBase: Color,
    /** Highlight of the shimmer gradient. */
    val shimmerHighlight: Color,
    val isDark: Boolean,
)

val DarkScanForgeColors = ScanForgeColors(
    success = Teal70,
    onSuccess = Teal20,
    warning = Forge70,
    onWarning = Forge10,
    processing = Teal70,
    scanBeam = Forge70,
    brand = Slate40,
    hairline = Color(0x14FFFFFF),       // ~8% white
    shimmerBase = Slate20,
    shimmerHighlight = Slate25,
    isDark = true,
)

val LightScanForgeColors = ScanForgeColors(
    success = Teal40,
    onSuccess = Color.White,
    warning = Forge40,
    onWarning = Color.White,
    processing = Teal60,
    scanBeam = Forge60,
    brand = Slate30,
    hairline = Color(0x14000000),       // ~8% black
    shimmerBase = Slate95,
    shimmerHighlight = Color.White,
    isDark = false,
)

/** Dark scheme — the ScanForge default. */
val ScanForgeDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Forge70,
    onPrimary = Forge10,
    primaryContainer = Forge30,
    onPrimaryContainer = Forge90,
    secondary = Teal70,
    onSecondary = Teal20,
    secondaryContainer = Teal30,
    onSecondaryContainer = Teal90,
    tertiary = Slate80,
    onTertiary = Slate10,
    tertiaryContainer = Slate30,
    onTertiaryContainer = Slate90,
    error = Error70,
    onError = Color(0xFF3A0608),
    errorContainer = Error30,
    onErrorContainer = Error90,
    background = Slate05,
    onBackground = Slate90,
    surface = Slate05,
    onSurface = Slate90,
    surfaceVariant = Slate20,
    onSurfaceVariant = Slate60,
    surfaceContainerLowest = Slate05,
    surfaceContainerLow = Slate10,
    surfaceContainer = Slate15,
    surfaceContainerHigh = Slate20,
    surfaceContainerHighest = Slate25,
    inverseSurface = Slate90,
    inverseOnSurface = Slate15,
    outline = Slate40,
    outlineVariant = Slate25,
    surfaceTint = Slate40,
    scrim = Color(0xCC000000),
)

/** Polished light scheme. */
val ScanForgeLightColorScheme: ColorScheme = lightColorScheme(
    primary = Forge40,
    onPrimary = Color.White,
    primaryContainer = Forge90,
    onPrimaryContainer = Forge20,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Teal20,
    tertiary = Slate30,
    onTertiary = Color.White,
    tertiaryContainer = Slate90,
    onTertiaryContainer = Slate20,
    error = Error40,
    onError = Color.White,
    errorContainer = Error90,
    onErrorContainer = Error30,
    background = Slate99,
    onBackground = Slate10,
    surface = Slate99,
    onSurface = Slate10,
    surfaceVariant = Slate95,
    onSurfaceVariant = Slate40,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Slate99,
    surfaceContainer = Slate95,
    surfaceContainerHigh = Slate90,
    surfaceContainerHighest = Slate80,
    inverseSurface = Slate15,
    inverseOnSurface = Slate95,
    outline = Slate60,
    outlineVariant = Slate80,
    surfaceTint = Slate30,
    scrim = Color(0x99000000),
)
