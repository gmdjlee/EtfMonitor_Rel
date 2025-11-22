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
 */
data class ExtendedShapes(
    val card: Shape = RoundedCornerShape(12.dp),
    val cardLarge: Shape = RoundedCornerShape(16.dp),
    val button: Shape = RoundedCornerShape(8.dp),
    val buttonLarge: Shape = RoundedCornerShape(12.dp),
    val dialog: Shape = RoundedCornerShape(28.dp),
    val bottomSheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    val chip: Shape = RoundedCornerShape(8.dp),
    val fab: Shape = RoundedCornerShape(16.dp),
    val circle: Shape = CircleShape
)

val LocalExtendedShapes = staticCompositionLocalOf { ExtendedShapes() }
