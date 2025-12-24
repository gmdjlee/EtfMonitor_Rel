package com.etfmonitor.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 Spacing System
 * Consistent spacing scale based on 4dp baseline grid
 *
 * Professional spacing for production-level apps:
 * - extraSmall: 4dp - Compact spacing, icons, badges
 * - small: 8dp - Text line spacing, small gaps
 * - medium: 16dp - Standard padding, card content
 * - large: 24dp - Section spacing, large gaps
 * - extraLarge: 32dp - Screen margins, major sections
 * - extraExtraLarge: 48dp - Hero sections, major separators
 */
data class Spacing(
    val none: Dp = 0.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val extraExtraLarge: Dp = 48.dp
)

/**
 * Local composition for spacing values
 * Access via MaterialTheme.spacing
 */
val LocalSpacing = staticCompositionLocalOf { Spacing() }
