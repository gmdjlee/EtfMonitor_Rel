package com.etfmonitor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 Shape System
 * Professional, modern rounded corners for production-level apps
 *
 * Following Material Design 3 guidelines:
 * - Extra Small: 4dp - Chips, small buttons
 * - Small: 8dp - Cards, text fields
 * - Medium: 12dp - Dialogs, bottom sheets
 * - Large: 16dp - FABs, large cards
 * - Extra Large: 28dp - Hero sections, special components
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Extended shape tokens for specialized use cases
 * Updated for Moss Green Nature theme - Consistent rounded corners
 */
data class ExtendedShapes(
    val card: Shape = RoundedCornerShape(16.dp),  // Standard card corners
    val cardLarge: Shape = RoundedCornerShape(20.dp),  // Large cards
    val button: Shape = RoundedCornerShape(100.dp),  // Fully rounded buttons
    val buttonOutlined: Shape = RoundedCornerShape(100.dp),  // Fully rounded for outlined buttons
    val buttonLarge: Shape = RoundedCornerShape(100.dp),  // Fully rounded for prominence
    val dialog: Shape = RoundedCornerShape(24.dp),  // Dialogs
    val bottomSheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    val chip: Shape = RoundedCornerShape(100.dp),  // Fully rounded chips
    val fab: Shape = RoundedCornerShape(16.dp),  // FAB corners
    val searchBar: Shape = RoundedCornerShape(100.dp),  // Fully rounded search bars
    val badge: Shape = RoundedCornerShape(8.dp),  // Status badges with gentle rounding
    val circle: Shape = CircleShape
)

val LocalExtendedShapes = staticCompositionLocalOf { ExtendedShapes() }
