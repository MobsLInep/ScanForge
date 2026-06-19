package com.scanforge.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.scanforge.designsystem.motion.ScanForgeMotion

/**
 * CompositionLocals carrying the **extended** ScanForge tokens that Material 3's own
 * theme cannot hold (custom colour roles, mono type, elevation, named shapes).
 * Material 3's colour/typography/shape are still provided by [MaterialTheme] underneath.
 */
val LocalScanForgeColors = staticCompositionLocalOf { DarkScanForgeColors }
val LocalScanForgeMono = staticCompositionLocalOf { ScanForgeMono }
val LocalScanForgeElevation = staticCompositionLocalOf { ScanForgeElevationDefault }
val LocalScanForgeShapeTokens = staticCompositionLocalOf { ScanForgeShapeTokensDefault }

/** User-selectable accent. [Amber] is the brand default; [Teal] promotes the secondary role. */
enum class SfAccent { Amber, Teal }

/**
 * The ScanForge theme.
 *
 * Dark is the brand default (hence [darkTheme] defaults to following the system but the app
 * forces dark via settings). [dynamicColor] opts into Material You wallpaper colours on API 31+,
 * which intentionally overrides the brand palette when the user enables it.
 */
@Composable
fun ScanForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    accent: SfAccent = SfAccent.Amber,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> ScanForgeDarkColorScheme
        else -> ScanForgeLightColorScheme
    }
    // Teal accent promotes the brand teal (the secondary role) to primary for a cooler CTA palette.
    val colorScheme = if (accent == SfAccent.Teal) {
        baseScheme.copy(
            primary = baseScheme.secondary,
            onPrimary = baseScheme.onSecondary,
            primaryContainer = baseScheme.secondaryContainer,
            onPrimaryContainer = baseScheme.onSecondaryContainer,
            secondary = baseScheme.primary,
            onSecondary = baseScheme.onPrimary,
            secondaryContainer = baseScheme.primaryContainer,
            onSecondaryContainer = baseScheme.onPrimaryContainer,
        )
    } else {
        baseScheme
    }
    val extendedColors = if (darkTheme) DarkScanForgeColors else LightScanForgeColors

    CompositionLocalProvider(
        LocalScanForgeColors provides extendedColors,
        LocalScanForgeMono provides ScanForgeMono,
        LocalScanForgeElevation provides ScanForgeElevationDefault,
        LocalScanForgeShapeTokens provides ScanForgeShapeTokensDefault,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ScanForgeTypography,
            shapes = ScanForgeShapes,
            content = content,
        )
    }
}

/**
 * Accessor for ScanForge tokens, mirroring how `MaterialTheme.colorScheme` reads.
 *
 * Usage: `ScanForgeTheme.colors.scanBeam`, `ScanForgeTheme.mono.medium`, `ScanForgeTheme.motion`.
 */
object ScanForgeTheme {
    val colors: ScanForgeColors
        @Composable @ReadOnlyComposable get() = LocalScanForgeColors.current

    val mono: ScanForgeMonoStyles
        @Composable @ReadOnlyComposable get() = LocalScanForgeMono.current

    val elevation: ScanForgeElevation
        @Composable @ReadOnlyComposable get() = LocalScanForgeElevation.current

    val shapesExt: ScanForgeShapeTokens
        @Composable @ReadOnlyComposable get() = LocalScanForgeShapeTokens.current

    /** Standardised motion specs (durations / easings / springs). */
    val motion: ScanForgeMotion get() = ScanForgeMotion
}
