package com.etfmonitor.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Modern ETF Monitor Theme
 * Professional financial app design with Material Design 3
 * Features:
 * - Material You dynamic color support (Android 12+)
 * - Custom professional color palette fallback
 * - Enhanced surface elevation system
 * - Montserrat typography
 */

private val LightColorScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight
)

private val DarkColorScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark
)

/**
 * Extended theme colors for financial data visualization
 */
data class ExtendedColors(
    val statusNew: androidx.compose.ui.graphics.Color,
    val statusIncrease: androidx.compose.ui.graphics.Color,
    val statusDecrease: androidx.compose.ui.graphics.Color,
    val statusRemoved: androidx.compose.ui.graphics.Color,
    val statusMaintain: androidx.compose.ui.graphics.Color,
    val chartPrimary: androidx.compose.ui.graphics.Color,
    val chartSecondary: androidx.compose.ui.graphics.Color,
    val chartTertiary: androidx.compose.ui.graphics.Color,
    val chartGreen: androidx.compose.ui.graphics.Color,
    val chartRed: androidx.compose.ui.graphics.Color,
    val chartBlue: androidx.compose.ui.graphics.Color,
    val surfaceElevation1: androidx.compose.ui.graphics.Color,
    val surfaceElevation2: androidx.compose.ui.graphics.Color,
    val surfaceElevation3: androidx.compose.ui.graphics.Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        statusNew = StatusNew,
        statusIncrease = StatusIncrease,
        statusDecrease = StatusDecrease,
        statusRemoved = StatusRemoved,
        statusMaintain = StatusMaintain,
        chartPrimary = ChartPrimary,
        chartSecondary = ChartSecondary,
        chartTertiary = ChartTertiary,
        chartGreen = ChartGreen,
        chartRed = ChartRed,
        chartBlue = ChartBlue,
        surfaceElevation1 = SurfaceElevation1Light,
        surfaceElevation2 = SurfaceElevation2Light,
        surfaceElevation3 = SurfaceElevation3Light
    )
}

@Composable
fun EtfMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Enable dynamic color by default for Material You theming on Android 12+
    dynamicColor: Boolean = true,
    typography: androidx.compose.material3.Typography = Typography,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Extended colors for financial data (not affected by dynamic color)
    val extendedColors = ExtendedColors(
        statusNew = StatusNew,
        statusIncrease = StatusIncrease,
        statusDecrease = StatusDecrease,
        statusRemoved = StatusRemoved,
        statusMaintain = StatusMaintain,
        chartPrimary = ChartPrimary,
        chartSecondary = ChartSecondary,
        chartTertiary = ChartTertiary,
        chartGreen = ChartGreen,
        chartRed = ChartRed,
        chartBlue = ChartBlue,
        surfaceElevation1 = if (darkTheme) SurfaceElevation1Dark else SurfaceElevation1Light,
        surfaceElevation2 = if (darkTheme) SurfaceElevation2Dark else SurfaceElevation2Light,
        surfaceElevation3 = if (darkTheme) SurfaceElevation3Dark else SurfaceElevation3Light
    )

    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors,
        LocalElevation provides Elevation(),
        LocalSpacing provides Spacing(),
        LocalMotion provides MotionScheme(),
        LocalExtendedShapes provides ExtendedShapes()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = Shapes,
            content = content
        )
    }
}

/**
 * Access extended theme colors
 * Usage: MaterialTheme.extendedColors.statusNew
 */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current

/**
 * Access elevation system
 * Usage: MaterialTheme.elevation.level2
 */
val MaterialTheme.elevation: Elevation
    @Composable
    get() = LocalElevation.current

/**
 * Access spacing system
 * Usage: MaterialTheme.spacing.medium
 */
val MaterialTheme.spacing: Spacing
    @Composable
    get() = LocalSpacing.current

/**
 * Access motion system
 * Usage: MaterialTheme.motion.emphasized
 */
val MaterialTheme.motion: MotionScheme
    @Composable
    get() = LocalMotion.current

/**
 * Access extended shapes
 * Usage: MaterialTheme.extendedShapes.card
 */
val MaterialTheme.extendedShapes: ExtendedShapes
    @Composable
    get() = LocalExtendedShapes.current