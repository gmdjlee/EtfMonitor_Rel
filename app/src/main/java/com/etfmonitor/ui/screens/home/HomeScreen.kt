package com.etfmonitor.ui.screens.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.ui.theme.*

/**
 * Home Screen - Moss Green Nature Theme
 * Clean, modern grid layout with Material Design 3
 *
 * Split into:
 * - HomeScreen.kt: Main screen, content, menu cards
 * - HomeDialogs.kt: All dialogs (DaysSelectionDialog, etc.)
 * - HomeSummaryCard.kt: SummaryCard and helper functions
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
    onNavigateToAIAnalysis: () -> Unit,
    onNavigateToPrediction: () -> Unit,
    onNavigateToAdvancedDashboard: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val showFirstRunDialog by viewModel.showFirstRunDialog.collectAsState()
    val showMarketDepositDialog by viewModel.showMarketDepositDialog.collectAsState()
    val showFearGreedDialog by viewModel.showFearGreedDialog.collectAsState()
    val showMarketOscillatorDialog by viewModel.showMarketOscillatorDialog.collectAsState()
    val showUnifiedInitDialog by viewModel.showUnifiedInitDialog.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lastDate = (state as? HomeState.Idle)?.lastDate

    var showDaysDialog by remember { mutableStateOf(false) }
    var showMarketDepositPagesDialog by remember { mutableStateOf(false) }
    var showFearGreedPeriodDialog by remember { mutableStateOf(false) }
    var showMarketOscillatorPeriodDialog by remember { mutableStateOf(false) }
    var showUnifiedDialog by remember { mutableStateOf(false) }

    // 통합 다이얼로그 핸들러
    LaunchedEffect(showUnifiedInitDialog) {
        if (showUnifiedInitDialog) showUnifiedDialog = true
    }

    // 기존 개별 다이얼로그 핸들러 (호환성 유지)
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
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        lastDate?.let {
                            Text(
                                stringResource(R.string.home_last_update, it),
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
                            contentDescription = stringResource(R.string.nav_settings),
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
                    onNavigateToMarketOscillator = onNavigateToMarketOscillator,
                    onNavigateToAIAnalysis = onNavigateToAIAnalysis,
                    onNavigateToPrediction = onNavigateToPrediction,
                    onNavigateToAdvancedDashboard = onNavigateToAdvancedDashboard
                )
            }
        }
    }

    // Dialogs (from HomeDialogs.kt)
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

    // 통합 초기화 다이얼로그
    if (showUnifiedDialog) {
        UnifiedInitializationDialog(
            onDismiss = {
                showUnifiedDialog = false
                viewModel.onUnifiedInitDialogDismiss()
            },
            onConfirm = { etfDays, depositPages, fearGreedDays, oscillatorDays ->
                showUnifiedDialog = false
                viewModel.initializeAll(etfDays, depositPages, fearGreedDays, oscillatorDays)
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
    onNavigateToMarketOscillator: () -> Unit,
    onNavigateToAIAnalysis: () -> Unit,
    onNavigateToPrediction: () -> Unit,
    onNavigateToAdvancedDashboard: () -> Unit
) {
    val hasData = (state as? HomeState.Idle)?.hasData ?: false

    // Menu items
    val menuItems = buildList {
        if (hasData) {
            add(
                MenuItem(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = stringResource(R.string.menu_etf_theme_list),
                    description = stringResource(R.string.menu_etf_theme_desc),
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToList
                )
            )
            add(
                MenuItem(
                    icon = Icons.Default.Analytics,
                    title = stringResource(R.string.menu_etf_statistics),
                    description = stringResource(R.string.menu_etf_statistics_desc),
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = onNavigateToStatistics
                )
            )
        }
        add(
            MenuItem(
                icon = Icons.Filled.ShowChart,
                title = stringResource(R.string.menu_stock_analysis),
                description = stringResource(R.string.menu_stock_analysis_desc),
                color = MaterialTheme.colorScheme.tertiary,
                onClick = onNavigateToOscillator
            )
        )
        add(
            MenuItem(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                title = stringResource(R.string.menu_market_fund),
                description = stringResource(R.string.menu_market_fund_desc),
                color = MaterialTheme.colorScheme.primary,
                onClick = onNavigateToMarketDeposit
            )
        )
        add(
            MenuItem(
                icon = Icons.Default.BarChart,
                title = stringResource(R.string.menu_fear_greed),
                description = stringResource(R.string.menu_fear_greed_desc),
                color = MaterialTheme.colorScheme.tertiary,
                onClick = onNavigateToFearGreed
            )
        )
        add(
            MenuItem(
                icon = Icons.Default.Speed,
                title = stringResource(R.string.menu_market_overbought),
                description = stringResource(R.string.menu_market_overbought_desc),
                color = MaterialTheme.colorScheme.secondary,
                onClick = onNavigateToMarketOscillator
            )
        )
        add(
            MenuItem(
                icon = Icons.Default.AutoAwesome,
                title = stringResource(R.string.menu_ai_analysis),
                description = stringResource(R.string.menu_ai_analysis_desc),
                color = MaterialTheme.colorScheme.tertiary,
                onClick = onNavigateToAIAnalysis
            )
        )
        // ML 주가 예측 (ETF 데이터 있을 때만 표시)
        if (hasData) {
            add(
                MenuItem(
                    icon = Icons.Default.Psychology,
                    title = stringResource(R.string.menu_ml_prediction),
                    description = stringResource(R.string.menu_ml_prediction_desc),
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToPrediction
                )
            )
            // 고급 분석 대시보드
            add(
                MenuItem(
                    icon = Icons.Default.Dashboard,
                    title = stringResource(R.string.menu_advanced_analysis),
                    description = stringResource(R.string.menu_advanced_analysis_desc),
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = onNavigateToAdvancedDashboard
                )
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
    ) {
        // Summary card (from HomeSummaryCard.kt)
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
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier
                    .fillMaxSize()
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

                Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))

                // Text content
                Column(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                    modifier = Modifier.weight(1f)
                ) {
                    // Title
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )

                    // Description
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
        }
    }
}
