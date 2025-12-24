package com.etfmonitor.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Material Design 3 Motion System
 * Professional animation specifications for smooth, natural interactions
 *
 * Easing curves based on Material Design 3 guidelines:
 * - Emphasized: Dynamic content changes (entering/exiting screens)
 * - Standard: Moderate emphasis (cards, buttons)
 * - Decelerated: Elements entering the screen
 * - Accelerated: Elements leaving the screen
 */

/**
 * Material Design 3 Easing Functions
 */
object MaterialEasing {
    // Emphasized easing for dynamic, expressive motion
    val emphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    // Standard easing for most UI transitions
    val standard = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val standardDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val standardAccelerate = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
}

/**
 * Motion duration tokens (in milliseconds)
 */
object MotionDuration {
    const val short1 = 50
    const val short2 = 100
    const val short3 = 150
    const val short4 = 200
    const val medium1 = 250
    const val medium2 = 300
    const val medium3 = 350
    const val medium4 = 400
    const val long1 = 450
    const val long2 = 500
    const val long3 = 550
    const val long4 = 600
    const val extraLong1 = 700
    const val extraLong2 = 800
    const val extraLong3 = 900
    const val extraLong4 = 1000
}

/**
 * Pre-configured animation specs for common use cases
 */
data class MotionScheme(
    // Quick interactions (ripples, state changes)
    val quick: AnimationSpec<Float> = tween(
        durationMillis = MotionDuration.short4,
        easing = MaterialEasing.standard
    ),

    // Standard UI transitions (most common)
    val default: AnimationSpec<Float> = tween(
        durationMillis = MotionDuration.medium2,
        easing = MaterialEasing.standard
    ),

    // Emphasized transitions (screen changes, important actions)
    val emphasized: AnimationSpec<Float> = tween(
        durationMillis = MotionDuration.medium4,
        easing = MaterialEasing.emphasized
    ),

    // Smooth spring animations (for natural, bouncy motion)
    val spring: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    ),

    // Expressive spring (more bouncy, for playful interactions)
    val expressiveSpring: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
)

/**
 * Local composition for motion values
 * Access via MaterialTheme.motion
 */
val LocalMotion = staticCompositionLocalOf { MotionScheme() }
