package com.etfmonitor.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 Elevation System
 * Professional elevation levels for consistent depth hierarchy
 *
 * Usage:
 * - Level 0: No elevation (flat surfaces)
 * - Level 1: Cards, chips at rest
 * - Level 2: Floating action buttons, cards on hover
 * - Level 3: Dialogs, pickers
 * - Level 4: Navigation drawers, modal bottom sheets
 * - Level 5: App bars, top app bars
 */
data class Elevation(
    val level0: Dp = 0.dp,
    val level1: Dp = 1.dp,
    val level2: Dp = 3.dp,
    val level3: Dp = 6.dp,
    val level4: Dp = 8.dp,
    val level5: Dp = 12.dp
)

/**
 * Local composition for elevation values
 * Access via MaterialTheme.elevation
 */
val LocalElevation = staticCompositionLocalOf { Elevation() }
