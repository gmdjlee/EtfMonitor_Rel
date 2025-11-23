package com.etfmonitor.ui.screens.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.ui.theme.*
import com.etfmonitor.ui.screens.home.HomeSummary

/**
 * Home Screen - Moss Green Nature Theme
 * Clean, modern grid layout with Material Design 3
 */

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
    val showMarketDepositDialog by viewModel.showMarketDepositDialog.collectAsState()
    val showFearGreedDialog by viewModel.showFearGreedDialog.collectAsState()
    val showMarketOscillatorDialog by viewModel.showMarketOscillatorDialog.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lastDate = (state as? HomeState.Idle)?.lastDate

    var showDaysDialog by remember { mutableStateOf(false) }
    var showMarketDepositPagesDialog by remember { mutableStateOf(false) }
    var showFearGreedPeriodDialog by remember { mutableStateOf(false) }
    var showMarketOscillatorPeriodDialog by remember { mutableStateOf(false) }

    // Dialog handlers
    LaunchedEffect(showFirstRunDialog) {
        if (showFirstRunDialog) showDaysDialog = true
    }

    LaunchedEffect(showMarketDepositDialog) {
        if (showMarketDepositDialog) showMarketDepositPagesDialog = true
    }

    LaunchedEffect(showFearGreedDialog) {
        if (showFearGreedDialog) showFearGreedPeriodDialog = true
    }

    LaunchedEffect(showMarketOscillatorDialog) {
        if (showMarketOscillatorDialog) showMarketOscillatorPeriodDialog = true
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
                        Text(
                            "Market Monitor",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        lastDate?.let {
                            Text(
                                "최근 업데이트: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "설정",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
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

    // Dialogs
    if (showDaysDialog) {
        DaysSelectionDialog(
            onDismiss = {
                showDaysDialog = false
                if (showFirstRunDialog) viewModel.onFirstRunDialogShown()
            },
            onConfirm = { days ->
                viewModel.initialize(days)
                showDaysDialog = false
                if (showFirstRunDialog) viewModel.onFirstRunDialogShown()
            }
        )
    }

    if (showMarketDepositPagesDialog) {
        MarketDepositPagesSelectionDialog(
            onDismiss = {
                showMarketDepositPagesDialog = false
                if (showMarketDepositDialog) viewModel.onMarketDepositDialogShown()
            },
            onConfirm = { pages ->
                viewModel.initializeMarketDeposit(pages)
                showMarketDepositPagesDialog = false
            }
        )
    }

    if (showFearGreedPeriodDialog) {
        FearGreedPeriodSelectionDialog(
            onDismiss = {
                showFearGreedPeriodDialog = false
                if (showFearGreedDialog) viewModel.onFearGreedDialogShown()
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
                if (showMarketOscillatorDialog) viewModel.onMarketOscillatorDialogShown()
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
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(MaterialTheme.spacing.large),
            shape = MaterialTheme.extendedShapes.cardLarge,
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = MaterialTheme.elevation.level3
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
                modifier = Modifier.padding(MaterialTheme.spacing.extraLarge)
            ) {
                CircularProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.size(72.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(MaterialTheme.extendedShapes.circle),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    "$progress%",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
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

    // Menu items
    val menuItems = buildList {
        if (hasData) {
            add(
                MenuItem(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "ETF 테마 목록",
                    description = "테마별 ETF 편입 종목 분석",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToList
                )
            )
            add(
                MenuItem(
                    icon = Icons.Default.Analytics,
                    title = "ETF 전체 통계",
                    description = "통합 편입 비중 및 트렌드",
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = onNavigateToStatistics
                )
            )
        }
        add(
            MenuItem(
                icon = Icons.Filled.ShowChart,
                title = "종목 수급 분석",
                description = "외인/기관 매매 동향",
                color = MaterialTheme.colorScheme.tertiary,
                onClick = onNavigateToOscillator
            )
        )
        add(
            MenuItem(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                title = "증시 자금 동향",
                description = "시장 예수금 추이",
                color = MaterialTheme.colorScheme.primary,
                onClick = onNavigateToMarketDeposit
            )
        )
        add(
            MenuItem(
                icon = Icons.Default.BarChart,
                title = "Fear & Greed",
                description = "시장 심리 지수",
                color = MaterialTheme.colorScheme.tertiary,
                onClick = onNavigateToFearGreed
            )
        )
        add(
            MenuItem(
                icon = Icons.Default.Speed,
                title = "시장 과매수/과매도",
                description = "RSI 기반 시장 분석",
                color = MaterialTheme.colorScheme.secondary,
                onClick = onNavigateToMarketOscillator
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
    ) {
        // Summary card
        val summary = (state as? HomeState.Idle)?.summary
        if (summary != null) {
            SummaryCard(summary = summary)
        }

        // Grid layout - 2 columns, evenly distributed vertically
        menuItems.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
            ) {
                rowItems.forEach { item ->
                    MenuCard(
                        icon = item.icon,
                        title = item.title,
                        description = item.description,
                        color = item.color,
                        onClick = item.onClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill remaining space if odd number
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// Data class for menu items
private data class MenuItem(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
private fun MenuCard(
    icon: ImageVector,
    title: String,
    description: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Scale animation
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    OutlinedCard(
        modifier = modifier
            .fillMaxHeight()
            .animateContentSize(),
        shape = MaterialTheme.extendedShapes.card,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.05f),
                            color.copy(alpha = 0.02f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                modifier = Modifier
                    .padding(MaterialTheme.spacing.medium)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                // Icon with rounded rectangle background
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))

                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )

                // Description
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
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
        title = { Text("초기 데이터 수집") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "ETF 데이터 수집 기간을 선택하세요",
                    style = MaterialTheme.typography.bodyMedium
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

                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
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
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            "참고사항",
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
            FilledTonalButton(
                onClick = { onConfirm(selectedOption.days) },
                shape = MaterialTheme.extendedShapes.button
            ) {
                Text("시작")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
        shape = MaterialTheme.extendedShapes.cardLarge
    )
}

@Composable
private fun MarketDepositPagesSelectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val pagesOptions = listOf(
        MarketDepositPagesOption(5, "5페이지", "약 최근 5일"),
        MarketDepositPagesOption(10, "10페이지 (권장)", "약 최근 10일"),
        MarketDepositPagesOption(15, "15페이지", "약 최근 15일"),
        MarketDepositPagesOption(20, "20페이지", "약 최근 20일"),
        MarketDepositPagesOption(30, "30페이지", "약 최근 30일")
    )

    var selectedPages by remember { mutableStateOf(10) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("증시 자금 동향 초기화") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "증시 자금 동향 데이터 수집 페이지 수를 선택하세요.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(8.dp))

                pagesOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (selectedPages == option.pages),
                                onClick = { selectedPages = option.pages }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedPages == option.pages),
                            onClick = { selectedPages = option.pages }
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
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Text(
                        "데이터 수집에는 약 30초-1분 정도 소요됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(selectedPages) },
                shape = MaterialTheme.extendedShapes.button
            ) {
                Text("수집 시작")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("나중에")
            }
        },
        shape = MaterialTheme.extendedShapes.cardLarge
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

    var selectedDays by remember { mutableStateOf(365) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                            .selectable(
                                selected = (selectedDays == option.days),
                                onClick = { selectedDays = option.days }
                            )
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
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Text(
                        "데이터 수집에는 선택한 기간에 따라 1-3분 정도 소요됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(selectedDays) },
                shape = MaterialTheme.extendedShapes.button
            ) {
                Text("수집 시작")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("나중에")
            }
        },
        shape = MaterialTheme.extendedShapes.cardLarge
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

    var selectedDays by remember { mutableStateOf(365) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                            .selectable(
                                selected = (selectedDays == option.days),
                                onClick = { selectedDays = option.days }
                            )
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
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Text(
                        "데이터 수집에는 선택한 기간에 따라 1-5분 정도 소요됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(selectedDays) },
                shape = MaterialTheme.extendedShapes.button
            ) {
                Text("수집 시작")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("나중에")
            }
        },
        shape = MaterialTheme.extendedShapes.cardLarge
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

private data class MarketDepositPagesOption(
    val pages: Int,
    val label: String,
    val description: String
)

private data class DaysOption(
    val days: Int,
    val label: String,
    val description: String
)

@Composable
private fun SummaryCard(summary: HomeSummary) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.extendedShapes.card,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Text(
                "시장 현황",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.spacing.small))

            // 증시 자금 동향
            if (summary.depositChange != null || summary.creditChange != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "증시 자금",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        summary.depositChange?.let {
                            Text(
                                "예탁금: ${formatChange(it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = getChangeColor(it)
                            )
                        }
                        summary.creditChange?.let {
                            Text(
                                "신용잔고: ${formatChange(it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = getChangeColor(it)
                            )
                        }
                    }
                }
            }

            // Fear & Greed Index
            if (summary.kospiFearGreed != null || summary.kosdaqFearGreed != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Fear & Greed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        summary.kospiFearGreed?.let {
                            Text(
                                "KOSPI: ${String.format("%.2f", it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = getFearGreedColor(it)
                            )
                        }
                        summary.kosdaqFearGreed?.let {
                            Text(
                                "KOSDAQ: ${String.format("%.2f", it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = getFearGreedColor(it)
                            )
                        }
                    }
                }
            }

            // 시장 과매수/과매도
            if (summary.kospiStatus != null || summary.kosdaqStatus != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "시장 상태",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        summary.kospiStatus?.let { status ->
                            Text(
                                "KOSPI: ${getStatusText(status)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = getStatusColor(status)
                            )
                        }
                        summary.kosdaqStatus?.let { status ->
                            Text(
                                "KOSDAQ: ${getStatusText(status)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = getStatusColor(status)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun formatChange(value: Double): String {
    val sign = if (value > 0) "+" else ""
    return "$sign${String.format("%.0f", value / 100000000)}억"
}

@Composable
private fun getChangeColor(value: Double): Color {
    return when {
        value > 0 -> MaterialTheme.colorScheme.error  // 증가 = 빨강
        value < 0 -> MaterialTheme.colorScheme.primary  // 감소 = 파랑
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
private fun getFearGreedColor(value: Double): Color {
    return when {
        value >= 60 -> MaterialTheme.colorScheme.error  // Greed
        value <= 40 -> MaterialTheme.colorScheme.primary  // Fear
        else -> MaterialTheme.colorScheme.onSurface  // Neutral
    }
}

@Composable
private fun getStatusText(status: String): String {
    return when (status) {
        "Overbought" -> "과매수"
        "Oversold" -> "과매도"
        else -> "중립"
    }
}

@Composable
private fun getStatusColor(status: String): Color {
    return when (status) {
        "Overbought" -> MaterialTheme.colorScheme.error
        "Oversold" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
}
