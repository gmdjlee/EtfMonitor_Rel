package com.etfmonitor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 Shape System - Moss Green Nature Theme
 * Professional, modern rounded corners for production-level apps
 *
 * Following Material Design 3 guidelines with enhanced rounding:
 * - Extra Small: 4dp - Chips, small buttons
 * - Small: 8dp - Cards, text fields
 * - Medium: 16dp - Dialogs, bottom sheets
 * - Large: 24dp - FABs, large cards
 * - Extra Large: 32dp - Hero sections, special components (2rem in design guide)
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * Extended shape tokens for specialized use cases
 * Updated for Moss Green Nature theme - Enhanced rounded corners (32dp for cards)
 */
data class ExtendedShapes(
    val card: Shape = RoundedCornerShape(32.dp),  // Standard card corners (2rem from design guide)
    val cardLarge: Shape = RoundedCornerShape(32.dp),  // Large cards
    val cardMedium: Shape = RoundedCornerShape(24.dp),  // Medium cards
    val cardSmall: Shape = RoundedCornerShape(16.dp),  // Small cards
    val button: Shape = RoundedCornerShape(100.dp),  // Fully rounded buttons
    val buttonOutlined: Shape = RoundedCornerShape(100.dp),  // Fully rounded for outlined buttons
    val buttonLarge: Shape = RoundedCornerShape(100.dp),  // Fully rounded for prominence
    val dialog: Shape = RoundedCornerShape(28.dp),  // Dialogs
    val bottomSheet: Shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),  // Enhanced bottom sheet
    val chip: Shape = RoundedCornerShape(100.dp),  // Fully rounded chips
    val fab: Shape = RoundedCornerShape(16.dp),  // FAB corners
    val fabExtended: Shape = RoundedCornerShape(100.dp),  // Extended FAB (pill shape)
    val searchBar: Shape = RoundedCornerShape(100.dp),  // Fully rounded search bars
    val badge: Shape = RoundedCornerShape(8.dp),  // Status badges with gentle rounding
    val statusChip: Shape = RoundedCornerShape(8.dp),  // Status chips (from design guide)
    val filterChip: Shape = RoundedCornerShape(100.dp),  // Filter chips (pill shape)
    val listItem: Shape = RoundedCornerShape(16.dp),  // List item backgrounds
    val aiInsightsCard: Shape = RoundedCornerShape(32.dp),  // AI insights featured card
    val iconContainer: Shape = RoundedCornerShape(16.dp),  // Icon containers in cards
    val circle: Shape = CircleShape
)

val LocalExtendedShapes = staticCompositionLocalOf { ExtendedShapes() }
