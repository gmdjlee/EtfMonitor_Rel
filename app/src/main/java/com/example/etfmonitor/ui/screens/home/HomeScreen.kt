package com.etfmonitor.ui.screens.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.vector.ImageVector
import android.graphics.Path as AndroidPath
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.hilt.navigation.compose.hiltViewModel

// Screen size class for adaptive layout
enum class ScreenSizeClass {
    COMPACT,  // Small phones (width < 600dp)
    MEDIUM,   // Large phones, small tablets (600dp <= width < 840dp)
    EXPANDED  // Tablets, desktops (width >= 840dp)
}

// Adaptive layout configuration
data class AdaptiveLayoutConfig(
    val itemSize: Dp,
    val itemSpacing: Dp,
    val verticalSpacing: Dp,
    val iconSize: Dp,
    val fontSize: Int,
    val itemsPerRow: Int
)

@Composable
private fun getScreenSizeClass(): ScreenSizeClass {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    return when {
        screenWidth < 600.dp -> ScreenSizeClass.COMPACT
        screenWidth < 840.dp -> ScreenSizeClass.MEDIUM
        else -> ScreenSizeClass.EXPANDED
    }
}

@Composable
private fun getAdaptiveLayoutConfig(screenSizeClass: ScreenSizeClass): AdaptiveLayoutConfig {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp
    val isLandscape = screenWidth > screenHeight

    return when (screenSizeClass) {
        ScreenSizeClass.COMPACT -> {
            if (isLandscape) {
                // Landscape phone - smaller items for triangle layout
                AdaptiveLayoutConfig(
                    itemSize = 90.dp,
                    itemSpacing = 6.dp,
                    verticalSpacing = (-25).dp,
                    iconSize = 28.dp,
                    fontSize = 10,
                    itemsPerRow = 3
                )
            } else {
                // Portrait phone - optimized for 3-column triangle layout
                AdaptiveLayoutConfig(
                    itemSize = min(screenWidth / 3.0f, 130f).dp,
                    itemSpacing = (-25).dp,
                    verticalSpacing = (-10).dp,
                    iconSize = 32.dp,
                    fontSize = 11,
                    itemsPerRow = 2
                )
            }
        }
        ScreenSizeClass.MEDIUM -> {
            // Large phones, small tablets
            AdaptiveLayoutConfig(
                itemSize = 140.dp,
                itemSpacing = 10.dp,
                verticalSpacing = (-35).dp,
                iconSize = 42.dp,
                fontSize = 13,
                itemsPerRow = 3
            )
        }
        ScreenSizeClass.EXPANDED -> {
            // Tablets, desktops - larger items
            AdaptiveLayoutConfig(
                itemSize = 180.dp,
                itemSpacing = (-35).dp,
                verticalSpacing = (-15).dp,
                iconSize = 52.dp,
                fontSize = 15,
                itemsPerRow = if (isLandscape) 5 else 3
            )
        }
    }
}

// Morph polygon shape for hexagon to star transformation
class MorphPolygonShape(
    private val morph: Morph,
    private val percentage: Float
) : Shape {
    private val matrix = Matrix()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        // Scale and translate to fit container
        matrix.reset()
        matrix.scale(size.width / 2f, size.height / 2f)
        matrix.translate(1f, 1f)

        val androidPath = morph.toPath(progress = percentage)
        val composePath = androidPath.asComposePath()
        composePath.transform(matrix)

        return Outline.Generic(composePath)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToList: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToOscillator: () -> Unit,
    onNavigateToMarketDeposit: () -> Unit,
    onNavigateToFearGreed: () -> Unit,
    onNavigateToMarketOscillator: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val showFirstRunDialog by viewModel.showFirstRunDialog.collectAsState()
    val showFearGreedDialog by viewModel.showFearGreedDialog.collectAsState()
    val showMarketOscillatorDialog by viewModel.showMarketOscillatorDialog.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lastDate = (state as? HomeState.Idle)?.lastDate

    var showDaysDialog by remember { mutableStateOf(false) }
    var showFearGreedPeriodDialog by remember { mutableStateOf(false) }
    var showMarketOscillatorPeriodDialog by remember { mutableStateOf(false) }

    // 첫 실행 다이얼로그 표시 (ETF 데이터)
    LaunchedEffect(showFirstRunDialog) {
        if (showFirstRunDialog) {
            showDaysDialog = true
        }
    }

    // Fear & Greed 다이얼로그 표시
    LaunchedEffect(showFearGreedDialog) {
        if (showFearGreedDialog) {
            showFearGreedPeriodDialog = true
        }
    }

    // 과매수/과매도 다이얼로그 표시
    LaunchedEffect(showMarketOscillatorDialog) {
        if (showMarketOscillatorDialog) {
            showMarketOscillatorPeriodDialog = true
        }
    }

    LaunchedEffect(state) {
        when (val s = state) {
            is HomeState.Success -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.clearMessage()
            }
            is HomeState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.clearMessage()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Market Monitor")
                        lastDate?.let {
                            Text(
                                "업데이트: $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (val s = state) {
            is HomeState.Initializing -> {
                LoadingScreen(
                    modifier = Modifier.padding(padding),
                    message = s.message,
                    progress = s.progress
                )
            }
            is HomeState.Updating -> {
                LoadingScreen(
                    modifier = Modifier.padding(padding),
                    message = s.message,
                    progress = s.progress
                )
            }
            else -> {
                HomeContent(
                    modifier = Modifier.padding(padding),
                    state = s,
                    onNavigateToList = onNavigateToList,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToStatistics = onNavigateToStatistics,
                    onNavigateToOscillator = onNavigateToOscillator,
                    onNavigateToMarketDeposit = onNavigateToMarketDeposit,
                    onNavigateToFearGreed = onNavigateToFearGreed,
                    onNavigateToMarketOscillator = onNavigateToMarketOscillator
                )
            }
        }
    }

    if (showDaysDialog) {
        DaysSelectionDialog(
            onDismiss = {
                showDaysDialog = false
                if (showFirstRunDialog) {
                    viewModel.onFirstRunDialogShown()
                }
            },
            onConfirm = { days ->
                viewModel.initialize(days)
                showDaysDialog = false
                if (showFirstRunDialog) {
                    viewModel.onFirstRunDialogShown()
                }
            }
        )
    }

    if (showFearGreedPeriodDialog) {
        FearGreedPeriodSelectionDialog(
            onDismiss = {
                showFearGreedPeriodDialog = false
                if (showFearGreedDialog) {
                    viewModel.onFearGreedDialogShown()
                }
            },
            onConfirm = { days ->
                viewModel.initializeFearGreed(days)
                showFearGreedPeriodDialog = false
            }
        )
    }

    if (showMarketOscillatorPeriodDialog) {
        MarketOscillatorPeriodSelectionDialog(
            onDismiss = {
                showMarketOscillatorPeriodDialog = false
                if (showMarketOscillatorDialog) {
                    viewModel.onMarketOscillatorDialogShown()
                }
            },
            onConfirm = { days ->
                viewModel.initializeMarketOscillator(days)
                showMarketOscillatorPeriodDialog = false
            }
        )
    }
}

@Composable
private fun LoadingScreen(
    modifier: Modifier = Modifier,
    message: String,
    progress: Int
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.size(68.dp),
                strokeWidth = 6.dp
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                "$progress%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun HomeContent(
    modifier: Modifier = Modifier,
    state: HomeState,
    onNavigateToList: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToOscillator: () -> Unit,
    onNavigateToMarketDeposit: () -> Unit,
    onNavigateToFearGreed: () -> Unit,
    onNavigateToMarketOscillator: () -> Unit
) {
    val hasData = (state as? HomeState.Idle)?.hasData ?: false
    val screenSizeClass = getScreenSizeClass()
    val layoutConfig = getAdaptiveLayoutConfig(screenSizeClass)

    // All menu items
    val menuItems = buildList {
        if (hasData) {
            add(
                MenuItem(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "ETF 테마 목록",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToList
                )
            )
            add(
                MenuItem(
                    icon = Icons.Default.Analytics,
                    title = "ETF 전체 통계",
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = onNavigateToStatistics
                )
            )
        }
        add(
            MenuItem(
                icon = Icons.Filled.ShowChart,
                title = "종목 수급 분석",
                color = MaterialTheme.colorScheme.tertiary,
                onClick = onNavigateToOscillator
            )
        )
        add(
            MenuItem(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                title = "증시 자금 동향",
                color = MaterialTheme.colorScheme.primary,
                onClick = onNavigateToMarketDeposit
            )
        )
        add(
            MenuItem(
                icon = Icons.Default.BarChart,
                title = "Fear & Greed",
                color = MaterialTheme.colorScheme.tertiary,
                onClick = onNavigateToFearGreed
            )
        )
        add(
            MenuItem(
                icon = Icons.Default.Speed,
                title = "시장 과매수/과매도",
                color = MaterialTheme.colorScheme.primary,
                onClick = onNavigateToMarketOscillator
            )
        )
        add(
            MenuItem(
                icon = Icons.Default.Settings,
                title = "설정",
                color = MaterialTheme.colorScheme.secondary,
                onClick = onNavigateToSettings
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Hexagon layout (3 columns: 3-2-3)
        Row(
            horizontalArrangement = Arrangement.spacedBy(layoutConfig.itemSpacing),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .wrapContentSize()
                .horizontalScroll(rememberScrollState())
        ) {
            // Left column: 3 items
            if (menuItems.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(layoutConfig.verticalSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    menuItems.take(3).forEach { item ->
                        HexagonMenuItem(
                            icon = item.icon,
                            title = item.title,
                            color = item.color,
                            onClick = item.onClick,
                            config = layoutConfig
                        )
                    }
                }
            }

            // Middle column: 2 items
            if (menuItems.size > 3) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(layoutConfig.verticalSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    menuItems.drop(3).take(2).forEach { item ->
                        HexagonMenuItem(
                            icon = item.icon,
                            title = item.title,
                            color = item.color,
                            onClick = item.onClick,
                            config = layoutConfig
                        )
                    }
                }
            }

            // Right column: 3 items (with empty placeholders if needed)
            if (menuItems.size > 5) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(layoutConfig.verticalSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val rightColumnItems = menuItems.drop(5).take(3)
                    rightColumnItems.forEach { item ->
                        HexagonMenuItem(
                            icon = item.icon,
                            title = item.title,
                            color = item.color,
                            onClick = item.onClick,
                            config = layoutConfig
                        )
                    }

                    // Add empty placeholders to fill up to 3 items
                    repeat(3 - rightColumnItems.size) {
                        Spacer(modifier = Modifier.size(layoutConfig.itemSize))
                    }
                }
            }
        }
    }
}

// Data class for menu items
private data class MenuItem(
    val icon: ImageVector,
    val title: String,
    val color: androidx.compose.ui.graphics.Color,
    val onClick: () -> Unit
)

@Composable
private fun HexagonMenuItem(
    icon: ImageVector,
    title: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    config: AdaptiveLayoutConfig
) {
    // Create rounded hexagon shape with smoother corners
    val shapeA = remember {
        RoundedPolygon(
            6,
            rounding = CornerRounding(0.25f)  // Slightly more rounded for modern look
        )
    }

    // Create star shape for morph target
    val shapeB = remember {
        RoundedPolygon.star(
            6,
            rounding = CornerRounding(0.15f)
        )
    }

    // Create morph between hexagon and star
    val morph = remember(shapeA, shapeB) {
        Morph(shapeA, shapeB)
    }

    val interactionSource = remember {
        MutableInteractionSource()
    }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animate morph progress from 0 (hexagon) to 1 (star)
    val animatedProgress = animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        label = "progress",
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)
    )

    // Animate scale for press feedback
    val animatedScale = animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        label = "scale",
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
    )

    Box(
        modifier = Modifier
            .size(config.itemSize)
            .padding(8.dp)
            .clip(MorphPolygonShape(morph, animatedProgress.value))
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.95f),
                        color.copy(alpha = 0.85f)
                    ),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
            .clickable(interactionSource = interactionSource, indication = null) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .animateContentSize()
                .then(
                    if (isPressed) Modifier
                    else Modifier
                )
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(config.iconSize),
                tint = MaterialTheme.colorScheme.surface
            )
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.surface,
                textAlign = TextAlign.Center,
                fontSize = config.fontSize.sp,
                lineHeight = (config.fontSize + 2).sp
            )
        }
    }
}

@Composable
private fun DaysSelectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val options = listOf(
        DaysOption(5, "5일", "빠른 테스트"),
        DaysOption(10, "10일", "약 2주"),
        DaysOption(15, "15일", "약 3주"),
        DaysOption(20, "20일", "약 1개월"),
        DaysOption(25, "25일 (권장)", "약 1.5개월"),
        DaysOption(30, "30일", "약 2개월"),
        DaysOption(40, "40일", "약 2.5개월"),
        DaysOption(50, "50일", "약 3개월")
    )

    var selectedOption by remember { mutableStateOf(options[4]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("초기 데이터 수집")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "ETF 데이터 수집 기간을 선택하세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(8.dp))

                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (option == selectedOption),
                                onClick = { selectedOption = option }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (option == selectedOption),
                            onClick = { selectedOption = option }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Fear & Greed Index 안내
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.BarChart,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "Fear & Greed Index",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "ETF 데이터 초기화 완료 후\nFear & Greed Index 데이터 1년(365일)을\n자동으로 수집합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            "ℹ️ 참고사항",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "• 기간이 길수록 수집 시간이 오래 걸립니다\n" +
                                    "• 25일 권장 (약 1-2분 소요)\n" +
                                    "• Fear & Greed Index는 추가 1-2분 소요\n" +
                                    "• 최초 실행 시 Python 패키지 로딩에 추가 시간이 필요할 수 있습니다",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedOption.days) }
            ) {
                Text("시작")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun FearGreedPeriodSelectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val periodOptions = listOf(
        FearGreedPeriodOption(180, "6개월", "약 180일"),
        FearGreedPeriodOption(365, "12개월 (권장)", "약 365일"),
        FearGreedPeriodOption(540, "18개월", "약 540일"),
        FearGreedPeriodOption(730, "24개월", "약 730일")
    )

    var selectedDays by remember { mutableStateOf(365) } // 기본값: 12개월

    AlertDialog(
        onDismissRequest = { },
        title = { Text("Fear & Greed Index 초기화") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Fear & Greed Index 데이터 수집 기간을 선택하세요.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(8.dp))

                periodOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedDays == option.days),
                            onClick = { selectedDays = option.days }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "데이터 수집에는 선택한 기간에 따라 1-3분 정도 소요됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDays) }) {
                Text("수집 시작")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("나중에")
            }
        }
    )
}

@Composable
private fun MarketOscillatorPeriodSelectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val periodOptions = listOf(
        MarketOscillatorPeriodOption(180, "6개월", "약 180일"),
        MarketOscillatorPeriodOption(365, "12개월 (권장)", "약 365일"),
        MarketOscillatorPeriodOption(540, "18개월", "약 540일"),
        MarketOscillatorPeriodOption(730, "24개월", "약 730일")
    )

    var selectedDays by remember { mutableStateOf(365) } // 기본값: 12개월

    AlertDialog(
        onDismissRequest = { },
        title = { Text("과매수/과매도 지표 초기화") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "과매수/과매도 지표 데이터 수집 기간을 선택하세요.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(8.dp))

                periodOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedDays == option.days),
                            onClick = { selectedDays = option.days }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "데이터 수집에는 선택한 기간에 따라 1-5분 정도 소요됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDays) }) {
                Text("수집 시작")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("나중에")
            }
        }
    )
}

private data class MarketOscillatorPeriodOption(
    val days: Int,
    val label: String,
    val description: String
)

private data class FearGreedPeriodOption(
    val days: Int,
    val label: String,
    val description: String
)

private data class DaysOption(
    val days: Int,
    val label: String,
    val description: String
)
