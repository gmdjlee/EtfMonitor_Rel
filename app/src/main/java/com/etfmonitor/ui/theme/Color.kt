package com.etfmonitor.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Forest Green Design Theme - Material Design 3
 * Clean, professional color palette inspired by the reference design
 * Theme Name: Forest Green
 * Base Source Color: Deep Forest Green (#3D6B4F)
 * Version: 2.0
 */

// ============================================
// Light Theme Colors - Forest Green
// ============================================

// Primary - Deep Forest Green
val primaryLight = Color(0xFF3D6B4F)  // Deep forest green
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFBFE4C7)  // Soft mint green
val onPrimaryContainerLight = Color(0xFF002111)

// Secondary - Olive/Khaki
val secondaryLight = Color(0xFF7A8B6D)  // Olive green
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFD8E7CA)  // Light olive
val onSecondaryContainerLight = Color(0xFF131F0D)

// Tertiary - Gray Green (Sage)
val tertiaryLight = Color(0xFF6B8B7A)  // Sage gray-green
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFCDE8DB)  // Light sage
val onTertiaryContainerLight = Color(0xFF002018)

// Error - Coral Red
val errorLight = Color(0xFFB3261E)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFF9DEDC)
val onErrorContainerLight = Color(0xFF410E0B)

// Surface Colors - Clean Grays
val backgroundLight = Color(0xFFF8FAF8)  // Very light gray-green
val onBackgroundLight = Color(0xFF191C19)
val surfaceLight = Color(0xFFF8FAF8)
val onSurfaceLight = Color(0xFF191C19)
val surfaceVariantLight = Color(0xFFDDE5DB)  // Light gray-green
val onSurfaceVariantLight = Color(0xFF414942)
val outlineLight = Color(0xFF727971)
val outlineVariantLight = Color(0xFFC1C9BF)

// Surface Container Colors (for cards and elevated surfaces)
val surfaceDimLight = Color(0xFFD9DED9)
val surfaceBrightLight = Color(0xFFF8FAF8)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFF2F5F2)
val surfaceContainerLight = Color(0xFFECEFEC)
val surfaceContainerHighLight = Color(0xFFE6EAE6)
val surfaceContainerHighestLight = Color(0xFFE0E4E0)

// Inverse Colors
val inverseSurfaceLight = Color(0xFF2E312E)
val inverseOnSurfaceLight = Color(0xFFF0F1EF)
val inversePrimaryLight = Color(0xFFA3D7AF)

// Scrim and Shadow
val scrimLight = Color(0xFF000000)

// ============================================
// Dark Theme Colors - Forest Green Night
// ============================================

// Primary - Bright Mint Green
val primaryDark = Color(0xFFA3D7AF)  // Bright mint for dark mode
val onPrimaryDark = Color(0xFF0A3A1E)
val primaryContainerDark = Color(0xFF255239)  // Dark forest
val onPrimaryContainerDark = Color(0xFFBFE4C7)

// Secondary - Light Olive
val secondaryDark = Color(0xFFBCCBAE)  // Light olive
val onSecondaryDark = Color(0xFF273420)
val secondaryContainerDark = Color(0xFF3E4B36)  // Dark olive
val onSecondaryContainerDark = Color(0xFFD8E7CA)

// Tertiary - Light Sage
val tertiaryDark = Color(0xFFB1CCBF)  // Light sage
val onTertiaryDark = Color(0xFF1D352B)
val tertiaryContainerDark = Color(0xFF344C41)  // Dark sage
val onTertiaryContainerDark = Color(0xFFCDE8DB)

// Error
val errorDark = Color(0xFFF2B8B5)
val onErrorDark = Color(0xFF601410)
val errorContainerDark = Color(0xFF8C1D18)
val onErrorContainerDark = Color(0xFFF9DEDC)

// Surface Colors - Dark Mode
val backgroundDark = Color(0xFF111411)  // Very dark green-gray
val onBackgroundDark = Color(0xFFE2E3E0)
val surfaceDark = Color(0xFF111411)
val onSurfaceDark = Color(0xFFE2E3E0)
val surfaceVariantDark = Color(0xFF414942)  // Dark gray-green
val onSurfaceVariantDark = Color(0xFFC1C9BF)
val outlineDark = Color(0xFF8B938A)
val outlineVariantDark = Color(0xFF414942)

// Surface Container Colors - Dark Mode
val surfaceDimDark = Color(0xFF111411)
val surfaceBrightDark = Color(0xFF373A36)
val surfaceContainerLowestDark = Color(0xFF0C0F0C)
val surfaceContainerLowDark = Color(0xFF191C19)
val surfaceContainerDark = Color(0xFF1D201D)
val surfaceContainerHighDark = Color(0xFF282B28)
val surfaceContainerHighestDark = Color(0xFF333632)

// Inverse Colors - Dark Mode
val inverseSurfaceDark = Color(0xFFE2E3E0)
val inverseOnSurfaceDark = Color(0xFF2E312E)
val inversePrimaryDark = Color(0xFF3D6B4F)

// Scrim
val scrimDark = Color(0xFF000000)

// ============================================
// Status Colors for Financial Data
// ============================================
val StatusNew = Color(0xFF3D6B4F)      // Forest green - new holdings
val StatusIncrease = Color(0xFF2E7D5A)  // Teal green - increased weight
val StatusDecrease = Color(0xFFE57373)  // Soft red - decreased weight
val StatusRemoved = Color(0xFF9E9E9E)   // Gray - removed
val StatusMaintain = Color(0xFF7A8B6D)  // Olive - maintained

// ============================================
// Chart Colors - Professional Palette
// ============================================
val ChartPrimary = Color(0xFF3D6B4F)    // Main chart line - forest green
val ChartSecondary = Color(0xFF6B8B7A)  // Secondary line - sage
val ChartTertiary = Color(0xFF7A8B6D)   // Tertiary line - olive
val ChartGreen = Color(0xFF2E7D5A)      // Bullish/positive - teal green
val ChartRed = Color(0xFFE57373)        // Bearish/negative - soft red
val ChartBlue = Color(0xFF5C8A9A)       // Neutral/info - blue-gray
val ChartPurple = Color(0xFF8E7CC3)     // Accent - muted purple
val ChartOrange = Color(0xFFE0A050)     // Warning - warm orange
val ChartCyan = Color(0xFF6BADC2)       // Highlight - cyan
val ChartPink = Color(0xFFD4A5A5)       // Special - dusty pink

// ============================================
// Gradient Colors for Modern UI Effects
// ============================================
val GradientStart = Color(0xFF3D6B4F)   // Deep forest
val GradientMiddle = Color(0xFF5A8A6A)  // Mid green
val GradientEnd = Color(0xFFA3D7AF)     // Light mint

// ============================================
// Surface Elevation Colors for Layered UI
// ============================================
val SurfaceElevation1Light = Color(0xFFF2F5F2)  // Very light
val SurfaceElevation2Light = Color(0xFFECEFEC)  // Light
val SurfaceElevation3Light = Color(0xFFE6EAE6)  // Medium light
val SurfaceElevation1Dark = Color(0xFF1D201D)   // Dark elevation 1
val SurfaceElevation2Dark = Color(0xFF282B28)   // Dark elevation 2
val SurfaceElevation3Dark = Color(0xFF333632)   // Dark elevation 3

// ============================================
// Chart Grid and Text Colors
// ============================================
val ChartGridLight = Color(0xFFDDE5DB)  // Light grid
val ChartGridDark = Color(0xFF333632)   // Dark grid
val ChartTextLight = Color(0xFF191C19)  // Dark text on light
val ChartTextDark = Color(0xFFE2E3E0)   // Light text on dark

// ============================================
// Shimmer Effect Colors for Loading States
// ============================================
val ShimmerColorLight = Color(0xFFDDE5DB)      // Light shimmer base
val ShimmerHighlightLight = Color(0xFFF8FAF8)  // Light shimmer highlight
val ShimmerColorDark = Color(0xFF333632)       // Dark shimmer base
val ShimmerHighlightDark = Color(0xFF414942)   // Dark shimmer highlight

// ============================================
// Chart Card Background Colors
// ============================================
val ChartCardBackgroundLight = Color(0xFFFFFFFF)  // Pure white for charts
val ChartCardBackgroundDark = Color(0xFFF5F7F5)   // Light for readability in dark mode

// ============================================
// Semantic Colors for Production Apps
// ============================================
val SuccessLight = Color(0xFF2E7D5A)    // Success - teal green
val SuccessDark = Color(0xFF81C995)     // Success - light green
val WarningLight = Color(0xFFE0A050)    // Warning - warm orange
val WarningDark = Color(0xFFFFCC80)     // Warning - light orange
val InfoLight = Color(0xFF5C8A9A)       // Info - blue-gray
val InfoDark = Color(0xFF90CAD8)        // Info - light blue

// ============================================
// Interactive State Colors
// ============================================
val RippleLight = Color(0xFF3D6B4F).copy(alpha = 0.12f)
val RippleDark = Color(0xFFA3D7AF).copy(alpha = 0.16f)
val HoverLight = Color(0xFF3D6B4F).copy(alpha = 0.06f)
val HoverDark = Color(0xFFA3D7AF).copy(alpha = 0.08f)

// ============================================
// Special Accent Colors (for stars, badges, etc.)
// ============================================
val AccentStar = Color(0xFF3D6B4F)      // Star/favorite icon color
val AccentBadge = Color(0xFF3D6B4F)     // Badge background
val AccentHighlight = Color(0xFFBFE4C7) // Highlight background
