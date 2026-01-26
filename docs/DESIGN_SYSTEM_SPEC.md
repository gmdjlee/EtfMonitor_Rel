# ETF Monitor Design System Specification

**Version**: 1.0.0
**Theme Name**: Moss Green Nature
**Framework**: Jetpack Compose + Material Design 3
**Last Updated**: 2026-01-26

---

## Table of Contents

1. [Overview](#overview)
2. [Color System](#color-system)
3. [Typography System](#typography-system)
4. [Spacing System](#spacing-system)
5. [Elevation System](#elevation-system)
6. [Shape System](#shape-system)
7. [Motion System](#motion-system)
8. [Component Library](#component-library)
9. [Usage Patterns](#usage-patterns)
10. [Migration Guide](#migration-guide)

---

## Overview

### Design Philosophy

**Moss Green Nature** is a professional, nature-inspired design system optimized for financial data visualization. The palette evokes growth, stability, and trust through organic green tones while maintaining excellent readability in both light and dark modes.

### Key Characteristics

- **Material Design 3 Compliant**: Full integration with M3 color system and components
- **Financial Data Optimized**: Dedicated status and chart colors for market visualization
- **Dark Mode Excellence**: Warm dark colors prevent harsh contrast
- **Runtime Customizable**: Typography scaling and chart colors can be adjusted at runtime
- **4dp Grid System**: Consistent spacing based on 4dp baseline

### Architecture

```
theme/
├── Color.kt          # Color palette (213 color values)
├── Type.kt           # Typography system (14 styles)
├── Shape.kt          # Corner radius definitions (21 shapes)
├── Spacing.kt        # Spacing scale (7 tiers)
├── Elevation.kt      # Depth hierarchy (6 levels)
├── Motion.kt         # Animation specs (5 easings, 16 durations)
├── Theme.kt          # Material3 integration
└── ThemeManager.kt   # Runtime state management
```

---

## Color System

### Primary Palette

#### Light Theme

| Token | Hex | RGB | Usage |
|-------|-----|-----|-------|
| `primary` | `#4C6C43` | rgb(76, 108, 67) | Primary actions, key UI elements |
| `onPrimary` | `#FFFFFF` | rgb(255, 255, 255) | Text/icons on primary |
| `primaryContainer` | `#CDEDA3` | rgb(205, 237, 163) | Primary container backgrounds |
| `onPrimaryContainer` | `#102000` | rgb(16, 32, 0) | Text on primary container |

| Token | Hex | RGB | Usage |
|-------|-----|-----|-------|
| `secondary` | `#586249` | rgb(88, 98, 73) | Secondary actions |
| `onSecondary` | `#FFFFFF` | rgb(255, 255, 255) | Text on secondary |
| `secondaryContainer` | `#DCE7C8` | rgb(220, 231, 200) | Secondary container |
| `onSecondaryContainer` | `#161E0B` | rgb(22, 30, 11) | Text on secondary container |

| Token | Hex | RGB | Usage |
|-------|-----|-----|-------|
| `tertiary` | `#396663` | rgb(57, 102, 99) | Accent, highlights |
| `onTertiary` | `#FFFFFF` | rgb(255, 255, 255) | Text on tertiary |
| `tertiaryContainer` | `#BBEBEB` | rgb(187, 235, 235) | Tertiary container |
| `onTertiaryContainer` | `#002020` | rgb(0, 32, 32) | Text on tertiary container |

| Token | Hex | RGB | Usage |
|-------|-----|-----|-------|
| `error` | `#BA1A1A` | rgb(186, 26, 26) | Error states |
| `onError` | `#FFFFFF` | rgb(255, 255, 255) | Text on error |
| `errorContainer` | `#FFDAD6` | rgb(255, 218, 214) | Error container |
| `onErrorContainer` | `#410002` | rgb(65, 0, 2) | Text on error container |

#### Light Theme - Surfaces

| Token | Hex | Usage |
|-------|-----|-------|
| `background` | `#FEFCF4` | App background (warm off-white) |
| `onBackground` | `#1B1C18` | Text on background |
| `surface` | `#FEFCF4` | Surface elements |
| `onSurface` | `#1B1C18` | Text on surface |
| `surfaceVariant` | `#E1E4D5` | Variant surfaces (light gray-green) |
| `onSurfaceVariant` | `#44483D` | Text on variant surfaces |
| `outline` | `#75796C` | Borders, dividers |
| `outlineVariant` | `#C5C8BA` | Subtle borders |

#### Light Theme - Surface Containers

| Token | Hex | Elevation Level |
|-------|-----|-----------------|
| `surfaceContainerLowest` | `#FFFFFF` | Lowest (level 0) |
| `surfaceContainerLow` | `#F8F6EE` | Low (level 1) |
| `surfaceContainer` | `#F2F0E8` | Default |
| `surfaceContainerHigh` | `#ECEAD2` | High (level 3) |
| `surfaceContainerHighest` | `#E6E4DC` | Highest (level 4) |

#### Dark Theme

| Token | Hex | RGB | Usage |
|-------|-----|-----|-------|
| `primary` | `#B1D18A` | rgb(177, 209, 138) | Primary actions |
| `onPrimary` | `#1F3701` | rgb(31, 55, 1) | Text on primary |
| `primaryContainer` | `#354E16` | rgb(53, 78, 22) | Primary container |
| `onPrimaryContainer` | `#CDEDA3` | rgb(205, 237, 163) | Text on primary container |

| Token | Hex | RGB | Usage |
|-------|-----|-----|-------|
| `secondary` | `#BFCBAD` | rgb(191, 203, 173) | Secondary actions |
| `onSecondary` | `#2A331E` | rgb(42, 51, 30) | Text on secondary |
| `secondaryContainer` | `#404A33` | rgb(64, 74, 51) | Secondary container |
| `onSecondaryContainer` | `#DCE7C8` | rgb(220, 231, 200) | Text on secondary container |

| Token | Hex | RGB | Usage |
|-------|-----|-----|-------|
| `tertiary` | `#A0CFCF` | rgb(160, 207, 207) | Accent |
| `onTertiary` | `#003738` | rgb(0, 55, 56) | Text on tertiary |
| `tertiaryContainer` | `#1F4E4D` | rgb(31, 78, 77) | Tertiary container |
| `onTertiaryContainer` | `#BBEBEB` | rgb(187, 235, 235) | Text on tertiary container |

| Token | Hex | RGB | Usage |
|-------|-----|-----|-------|
| `error` | `#FFB4AB` | rgb(255, 180, 171) | Error states |
| `onError` | `#690005` | rgb(105, 0, 5) | Text on error |
| `errorContainer` | `#93000A` | rgb(147, 0, 10) | Error container |
| `onErrorContainer` | `#FFDAD6` | rgb(255, 218, 214) | Text on error container |

#### Dark Theme - Surfaces

| Token | Hex | Usage |
|-------|-----|-------|
| `background` | `#1A1C18` | App background (warm dark) |
| `onBackground` | `#E3E3DC` | Text on background |
| `surface` | `#1A1C18` | Surface elements |
| `onSurface` | `#E3E3DC` | Text on surface |
| `surfaceVariant` | `#44483D` | Variant surfaces |
| `onSurfaceVariant` | `#C5C8BA` | Text on variant |
| `outline` | `#8F9285` | Borders |
| `outlineVariant` | `#44483D` | Subtle borders |

#### Dark Theme - Surface Containers

| Token | Hex | Elevation Level |
|-------|-----|-----------------|
| `surfaceContainerLowest` | `#151713` | Lowest |
| `surfaceContainerLow` | `#1B1C18` | Low |
| `surfaceContainer` | `#202120` | Default |
| `surfaceContainerHigh` | `#2A2C28` | High |
| `surfaceContainerHighest` | `#353733` | Highest |

#### Inverse Colors

| Mode | `inverseSurface` | `inverseOnSurface` | `inversePrimary` |
|------|------------------|--------------------|--------------------|
| Light | `#30312C` | `#F1EFEA` | `#B1D18A` |
| Dark | `#E3E3DC` | `#30312C` | `#4C6C43` |

### Extended Colors - Financial Status

| Token | Hex | Usage |
|-------|-----|-------|
| `statusNew` | `#4C6C43` | New holdings |
| `statusIncrease` | `#2E7D5A` | Weight increased |
| `statusDecrease` | `#BA1A1A` | Weight decreased |
| `statusRemoved` | `#8F9285` | Removed holdings |
| `statusMaintain` | `#586249` | Maintained |

### Extended Colors - Charts

| Token | Hex | Usage |
|-------|-----|-------|
| `chartPrimary` | `#4C6C43` | Main chart line |
| `chartSecondary` | `#396663` | Secondary line |
| `chartTertiary` | `#586249` | Tertiary line |
| `chartGreen` | `#2E7D5A` | Bullish/positive |
| `chartRed` | `#BA1A1A` | Bearish/negative |
| `chartBlue` | `#396663` | Neutral/info |
| `chartPurple` | `#8E7CC3` | Accent data |
| `chartOrange` | `#E0A050` | Warning/highlight |
| `chartCyan` | `#A0CFCF` | Special highlight |
| `chartPink` | `#D4A5A5` | Special data |

### Extended Colors - Semantic

| Category | Light | Dark | Usage |
|----------|-------|------|-------|
| Success | `#2E7D5A` | `#81C995` | Success states |
| SuccessContainer | `#C4EED0` | `#0F5223` | Success background |
| Warning | `#E0A050` | `#FFCC80` | Warning states |
| Info | `#396663` | `#A0CFCF` | Information |

### Extended Colors - Surface Elevation

| Token | Light | Dark |
|-------|-------|------|
| `surfaceElevation1` | `#F8F6EE` | `#202120` |
| `surfaceElevation2` | `#F2F0E8` | `#2A2C28` |
| `surfaceElevation3` | `#ECEAD2` | `#353733` |

### Extended Colors - Charts Grid & Text

| Token | Light | Dark |
|-------|-------|------|
| `chartGrid` | `#E1E4D5` | `#353733` |
| `chartText` | `#1B1C18` | `#E3E3DC` |
| `chartCardBackground` | `#FFFFFF` | `#F5F7F5` |

### Extended Colors - Shimmer Effect

| Token | Light | Dark |
|-------|-------|------|
| `shimmerColor` | `#E1E4D5` | `#353733` |
| `shimmerHighlight` | `#FEFCF4` | `#44483D` |

### Extended Colors - AI Insights

| Token | Hex | Usage |
|-------|-----|-------|
| `aiInsightsBackground` | `#2D4438` | AI card background |
| `aiInsightsAccent` | `#CDEDA3` | AI accent color |
| `aiInsightsText` | `#FFFFFF` | AI text color |
| `aiInsightsSubtext` | `#FFFFFF` @ 80% | AI subtext |

### Extended Colors - Interactive States

| State | Light | Dark |
|-------|-------|------|
| Ripple | `#4C6C43` @ 12% | `#B1D18A` @ 16% |
| Hover | `#4C6C43` @ 6% | `#B1D18A` @ 8% |

### Gradients

```kotlin
val gradientBrush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF4C6C43),  // Moss green (start)
        Color(0xFF5A8A6A),  // Mid green
        Color(0xFFB1D18A)   // Light moss (end)
    )
)
```

---

## Typography System

### Font Family

**Primary**: Montserrat (Google Fonts)
- Clean, modern, professional aesthetic
- Excellent readability at all sizes
- Available weights: Regular (400), Medium (500), SemiBold (600), Bold (700)

```kotlin
val MontserratFontFamily = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_bold, FontWeight.Bold)
)
```

### Type Scale

#### Display Styles (Bold/SemiBold - Impactful headers)

| Style | Weight | Size | Line Height | Letter Spacing |
|-------|--------|------|-------------|----------------|
| `displayLarge` | Bold | 57sp | 64sp | -0.25sp |
| `displayMedium` | Bold | 45sp | 52sp | 0sp |
| `displaySmall` | SemiBold | 36sp | 44sp | 0sp |

#### Headline Styles (SemiBold - Section headers)

| Style | Weight | Size | Line Height | Letter Spacing |
|-------|--------|------|-------------|----------------|
| `headlineLarge` | SemiBold | 32sp | 40sp | 0sp |
| `headlineMedium` | SemiBold | 28sp | 36sp | 0sp |
| `headlineSmall` | SemiBold | 24sp | 32sp | 0sp |

#### Title Styles (Medium/SemiBold - Card/component titles)

| Style | Weight | Size | Line Height | Letter Spacing |
|-------|--------|------|-------------|----------------|
| `titleLarge` | SemiBold | 22sp | 28sp | 0sp |
| `titleMedium` | Medium | 16sp | 24sp | 0.15sp |
| `titleSmall` | Medium | 14sp | 20sp | 0.1sp |

#### Body Styles (Regular - Content text)

| Style | Weight | Size | Line Height | Letter Spacing |
|-------|--------|------|-------------|----------------|
| `bodyLarge` | Regular | 16sp | 24sp | 0.5sp |
| `bodyMedium` | Regular | 14sp | 20sp | 0.25sp |
| `bodySmall` | Regular | 12sp | 16sp | 0.4sp |

#### Label Styles (Medium - Buttons, tags)

| Style | Weight | Size | Line Height | Letter Spacing |
|-------|--------|------|-------------|----------------|
| `labelLarge` | Medium | 14sp | 20sp | 0.1sp |
| `labelMedium` | Medium | 12sp | 16sp | 0.5sp |
| `labelSmall` | Medium | 11sp | 16sp | 0.5sp |

### Dynamic Typography Scaling

Support for runtime typography adjustment (accessibility):

```kotlin
fun createScaledTypography(
    displayScale: Float = 1.0f,   // 0.0 - 2.0
    headlineScale: Float = 1.0f,
    titleScale: Float = 1.0f,
    bodyScale: Float = 1.0f,
    labelScale: Float = 1.0f
): Typography
```

---

## Spacing System

### 4dp Baseline Grid

All spacing derives from a 4dp baseline for visual harmony:

| Token | Value | Multiplier | Usage |
|-------|-------|------------|-------|
| `none` | 0dp | 0x | No spacing |
| `extraSmall` | 4dp | 1x | Icons, badges, compact elements |
| `small` | 8dp | 2x | Text gaps, inline spacing |
| `medium` | 16dp | 4x | **Standard padding**, card content |
| `large` | 24dp | 6x | Section spacing, large gaps |
| `extraLarge` | 32dp | 8x | Screen margins, major sections |
| `extraExtraLarge` | 48dp | 12x | Hero sections, major separators |

### Implementation

```kotlin
@Immutable
data class Spacing(
    val none: Dp = 0.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val extraExtraLarge: Dp = 48.dp
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

val MaterialTheme.spacing: Spacing
    @Composable get() = LocalSpacing.current
```

### Common Padding Patterns

```kotlin
// Standard card content
Modifier.padding(MaterialTheme.spacing.medium)  // 16dp all sides

// Hub headers
Modifier.padding(horizontal = 24.dp, vertical = 16.dp)

// Screen margins
Modifier.padding(horizontal = MaterialTheme.spacing.extraLarge)  // 32dp

// Compact element spacing
Modifier.padding(MaterialTheme.spacing.small)  // 8dp

// Between list items
Spacer(Modifier.height(MaterialTheme.spacing.small))  // 8dp
```

---

## Elevation System

### Depth Hierarchy

Professional shadow system for clear visual layering:

| Level | Value | Usage | Example |
|-------|-------|-------|---------|
| `level0` | 0dp | Flat surfaces | Text, basic content |
| `level1` | 1dp | Cards at rest | List items, chips |
| `level2` | 3dp | Elevated cards | **Most common**, hover states |
| `level3` | 6dp | Dialogs, pickers | Modal content |
| `level4` | 8dp | Navigation drawers | Bottom sheets |
| `level5` | 12dp | App bars | Top/bottom app bars |

### Implementation

```kotlin
@Immutable
data class Elevation(
    val level0: Dp = 0.dp,
    val level1: Dp = 1.dp,
    val level2: Dp = 3.dp,
    val level3: Dp = 6.dp,
    val level4: Dp = 8.dp,
    val level5: Dp = 12.dp
)

val LocalElevation = staticCompositionLocalOf { Elevation() }

val MaterialTheme.elevation: Elevation
    @Composable get() = LocalElevation.current
```

### Usage

```kotlin
ElevatedCard(
    elevation = CardDefaults.elevatedCardElevation(
        defaultElevation = MaterialTheme.elevation.level2  // 3dp
    )
)
```

---

## Shape System

### Material Design 3 Base Shapes

| Shape | Corner Radius | Usage |
|-------|---------------|-------|
| `extraSmall` | 4dp | Chips, small buttons |
| `small` | 8dp | Cards, text fields |
| `medium` | 16dp | Dialogs, bottom sheets |
| `large` | 24dp | FABs, large cards |
| `extraLarge` | 32dp | Hero sections |

### Extended Shapes

Professional extended shape system:

| Token | Radius | Usage |
|-------|--------|-------|
| `card` | 32dp | Standard card corners |
| `cardLarge` | 32dp | Large cards |
| `cardMedium` | 24dp | Medium cards |
| `cardSmall` | 16dp | Small cards |
| `button` | 100dp | Pill-shaped buttons |
| `buttonOutlined` | 100dp | Outlined buttons |
| `buttonLarge` | 100dp | Large buttons |
| `dialog` | 28dp | Dialog boxes |
| `bottomSheet` | 32dp | Bottom sheet (top only) |
| `chip` | 100dp | Pill-shaped chips |
| `fab` | 16dp | Floating action buttons |
| `fabExtended` | 100dp | Extended FAB |
| `searchBar` | 100dp | Search bars (pill) |
| `badge` | 8dp | Status badges |
| `statusChip` | 8dp | Status chips |
| `filterChip` | 100dp | Filter chips |
| `listItem` | 16dp | List item backgrounds |
| `aiInsightsCard` | 32dp | AI insights card |
| `iconContainer` | 16dp | Icon containers |
| `circle` | CircleShape | Circular elements |

### Implementation

```kotlin
@Immutable
data class ExtendedShapes(
    val card: Shape = RoundedCornerShape(32.dp),
    val cardLarge: Shape = RoundedCornerShape(32.dp),
    val cardMedium: Shape = RoundedCornerShape(24.dp),
    val cardSmall: Shape = RoundedCornerShape(16.dp),
    val button: Shape = RoundedCornerShape(100.dp),
    val dialog: Shape = RoundedCornerShape(28.dp),
    val bottomSheet: Shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    val chip: Shape = RoundedCornerShape(100.dp),
    val searchBar: Shape = RoundedCornerShape(100.dp),
    val badge: Shape = RoundedCornerShape(8.dp),
    // ... more shapes
)

val LocalExtendedShapes = staticCompositionLocalOf { ExtendedShapes() }

val MaterialTheme.extendedShapes: ExtendedShapes
    @Composable get() = LocalExtendedShapes.current
```

---

## Motion System

### Easing Functions

Material Design 3 cubic bezier curves:

| Easing | Bezier | Usage |
|--------|--------|-------|
| `emphasized` | (0.2, 0.0, 0.0, 1.0) | Dynamic content changes |
| `emphasizedDecelerate` | (0.05, 0.7, 0.1, 1.0) | Elements entering |
| `emphasizedAccelerate` | (0.3, 0.0, 0.8, 0.15) | Elements exiting |
| `standard` | (0.4, 0.0, 0.2, 1.0) | Most UI transitions |
| `standardDecelerate` | (0.0, 0.0, 0.2, 1.0) | Standard entering |
| `standardAccelerate` | (0.4, 0.0, 1.0, 1.0) | Standard exiting |

### Duration Tokens

| Category | Durations (ms) |
|----------|----------------|
| Short | 50, 100, 150, 200 |
| Medium | 250, 300, 350, 400 |
| Long | 450, 500, 550, 600 |
| Extra Long | 700, 800, 900, 1000 |

### Animation Specs

| Spec | Duration | Easing | Usage |
|------|----------|--------|-------|
| `quick` | 200ms | standard | Ripples, state changes |
| `default` | 300ms | standard | Most transitions |
| `emphasized` | 400ms | emphasized | Important actions |
| `spring` | - | MediumBouncy | Natural motion |
| `expressiveSpring` | - | LowBouncy | Playful interactions |

### Implementation

```kotlin
@Immutable
data class Motion(
    val emphasized: CubicBezierEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f),
    val standard: CubicBezierEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f),

    val durationShort1: Int = 50,
    val durationShort2: Int = 100,
    val durationMedium1: Int = 250,
    val durationMedium2: Int = 300,

    val quick: AnimationSpec<Float> = tween(200, easing = standard),
    val default: AnimationSpec<Float> = tween(300, easing = standard),
    val spring: AnimationSpec<Float> = spring(dampingRatio = DampingRatioMediumBouncy)
)

val MaterialTheme.motion: Motion
    @Composable get() = LocalMotion.current
```

---

## Component Library

### 1. State Cards

Three state indicator cards for loading, error, and idle states:

#### LoadingCard

```kotlin
@Composable
fun LoadingCard(
    modifier: Modifier = Modifier,
    message: String = "Loading..."
)
```

- Pulsing alpha animation (1200ms cycle)
- CircularProgressIndicator (56dp)
- Uses `surfaceContainerHigh` background

#### ErrorCard

```kotlin
@Composable
fun ErrorCard(
    message: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
)
```

- Error icon + message + optional close button
- Uses `errorContainer` background

#### IdleCard

```kotlin
@Composable
fun IdleCard(
    message: String,
    modifier: Modifier = Modifier
)
```

- OutlinedCard with subtle border
- Encourages user interaction

### 2. Bottom Navigation Bar

Five-item navigation with elevated center button:

```kotlin
enum class MainNavItem(val route: String, val label: String, val icon: ImageVector) {
    MARKET_INDICATOR("market_indicator", "시장 지표", Icons.Filled.BarChart),
    ETF("etf_hub", "ETF", Icons.Filled.PieChart),
    HOME("home", "홈", Icons.Filled.Home),  // Center elevated
    STOCKS("stocks", "종목", Icons.Filled.ShowChart),
    ANALYSIS("analysis", "분석", Icons.Filled.Analytics)
}
```

- Center home button: 56dp elevated circle with gradient
- Spring animation: 1.1x scale on selection
- 8dp elevation shadow

### 3. Hub Header

Shared header component for hub screens:

```kotlin
@Composable
fun HubHeader(
    title: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSettingsClick: () -> Unit
)
```

### 4. Filter Chip Row

Selection chips with visual feedback:

```kotlin
@Composable
fun FilterChipRow(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
)
```

- Pill-shaped (100dp corners)
- Selected: `primaryContainer` background
- Unselected: 1dp outline border

### 5. Date Range Selector

Horizontal scrollable time range filter:

```kotlin
enum class DateRangeOption(val label: String, val days: Int) {
    WEEK("1주", 7),
    MONTH("1개월", 30),
    THREE_MONTHS("3개월", 90),
    SIX_MONTHS("6개월", 180),
    YEAR("1년", 365),
    THREE_YEARS("3년", 1095),
    FIVE_YEARS("5년", 1825),
    SEVEN_YEARS("7년", 2555),
    ALL("전체", -1)
}
```

### 6. Chart Card

Universal container for charts:

```kotlin
@Composable
fun ChartCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
)
```

- Always black title (chart readability)
- 3dp elevation
- Animated content size (300ms)

### 7. Search Field with Autocomplete

Stock search with history:

```kotlin
@Composable
fun UnifiedStockSearchField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<StockSearchItem>,
    searchHistory: List<SearchHistory>,
    isSearching: Boolean,
    onSelectStock: (ticker: String, name: String) -> Unit
)
```

### 8. Error Boundary

Error handling wrapper:

```kotlin
@Composable
fun ErrorBoundary(
    state: ErrorBoundaryState,
    onRetry: (() -> Unit)? = null,
    fallback: @Composable (Throwable, (() -> Unit)?) -> Unit = { e, retry ->
        DefaultErrorFallback(e, retry)
    },
    content: @Composable () -> Unit
)
```

---

## Usage Patterns

### Accessing Theme Values

```kotlin
@Composable
fun MyComponent() {
    // Colors
    val primary = MaterialTheme.colorScheme.primary
    val statusNew = MaterialTheme.extendedColors.statusNew

    // Typography
    val headline = MaterialTheme.typography.headlineLarge

    // Shapes
    val cardShape = MaterialTheme.extendedShapes.card

    // Elevation
    val elevation = MaterialTheme.elevation.level2

    // Spacing
    val padding = MaterialTheme.spacing.medium

    // Motion
    val animSpec = MaterialTheme.motion.default
}
```

### State-Driven UI Pattern

```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel) {
    val state by viewModel.state.collectAsState()

    when (state) {
        is State.Loading -> LoadingCard()
        is State.Error -> ErrorCard(
            message = (state as State.Error).message,
            onDismiss = { viewModel.clearError() }
        )
        is State.Success -> SuccessContent(state.data)
        is State.Idle -> IdleCard("No data available")
    }
}
```

### Chart with Date Range Pattern

```kotlin
@Composable
fun ChartScreen() {
    var selectedRange by remember { mutableStateOf(DateRangeOption.MONTH) }

    Column {
        DateRangeSelector(
            selectedRange = selectedRange,
            onRangeSelected = { selectedRange = it }
        )

        ChartCard(
            title = "Market Data",
            subtitle = "Last updated: $date"
        ) {
            MarketCapOscillatorChart(data = filteredData)
        }
    }
}
```

### Error Boundary Pattern

```kotlin
@Composable
fun SafeScreen() {
    val errorState = rememberErrorBoundaryState()

    ErrorBoundary(
        state = errorState,
        onRetry = { viewModel.reload() }
    ) {
        MainContent(
            onError = { errorState.setError(it) }
        )
    }
}
```

---

## Migration Guide

### Porting to Another App

#### Step 1: Copy Theme Files

Copy the entire `theme/` directory:
- `Color.kt` - All color definitions
- `Type.kt` - Typography with Montserrat font
- `Shape.kt` - Shape definitions
- `Spacing.kt` - Spacing scale
- `Elevation.kt` - Elevation levels
- `Motion.kt` - Animation specs
- `Theme.kt` - Material3 integration
- `ThemeManager.kt` - Runtime state

#### Step 2: Add Font Resources

Add Montserrat font files to `res/font/`:
- `montserrat_regular.ttf`
- `montserrat_medium.ttf`
- `montserrat_semibold.ttf`
- `montserrat_bold.ttf`

#### Step 3: Wrap App with Theme

```kotlin
@Composable
fun MyApp() {
    EtfMonitorTheme(
        darkTheme = isSystemInDarkTheme(),
        dynamicColor = false  // Use custom palette
    ) {
        // Your app content
    }
}
```

#### Step 4: Use Design Tokens

Replace hardcoded values with theme tokens:

```kotlin
// Before
Modifier.padding(16.dp)
RoundedCornerShape(12.dp)

// After
Modifier.padding(MaterialTheme.spacing.medium)
MaterialTheme.extendedShapes.cardSmall
```

#### Step 5: Adapt Extended Colors

Rename financial-specific colors if needed:

```kotlin
// Original (financial)
MaterialTheme.extendedColors.statusIncrease  // Green for increase

// Adapted (general purpose)
MaterialTheme.extendedColors.success  // General success state
```

### Color Palette Customization

To create a different color theme, modify `Color.kt`:

```kotlin
// Change primary colors
val primaryLight = Color(0xFF4C6C43)  // Moss Green
// To:
val primaryLight = Color(0xFF1E88E5)  // Blue theme
```

The entire palette will automatically adapt based on Material 3 tonal system.

---

## Appendix: Complete Color Reference

### All 213 Color Values

See `Color.kt` for the complete list organized by:
- Light/Dark theme variants
- Primary/Secondary/Tertiary/Error
- Surface and container colors
- Status and chart colors
- Semantic colors
- Interactive states
- AI and special features

### Icon Sizes Reference

| Size | Usage |
|------|-------|
| 20dp | Search icons |
| 24dp | Standard icons |
| 28dp | Navigation icons |
| 40dp | Nav item background |
| 48dp | Medium feature icons |
| 56dp | Progress indicators |
| 64dp | Large error icons |

### Chart Heights Reference

| Height | Usage |
|--------|-------|
| 200dp | Standard charts |
| 250dp | Larger charts |
| 300dp | Technical indicators |
| 350dp | Full-width detail charts |

---

**Document Version**: 1.0.0
**Created**: 2026-01-26
**Source**: ETF Monitor v17 (Schema)
