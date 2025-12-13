package com.etfmonitor.ui.screens.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.ui.components.*
import com.etfmonitor.ui.theme.*

/**
 * Home Screen - Moss Green Nature Theme
 * Modern design matching the React design guide specification
 *
 * Layout:
 * - Header with title and notification bell
 * - Market Status Summary Bar (KOSPI/KOSDAQ)
 * - AI Insights featured card
 * - 2x2 Dashboard Grid
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

    // Dialog handlers
    LaunchedEffect(showUnifiedInitDialog) {
        if (showUnifiedInitDialog) showUnifiedDialog = true
    }

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
                    lastDate = lastDate,
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
            shape = MaterialTheme.extendedShapes.card,
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
    lastDate: String?,
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
    val summary = (state as? HomeState.Idle)?.summary
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
    ) {
        // Header
        HomeHeader(
            lastDate = lastDate,
            onNotificationClick = { /* TODO */ }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Market Status Summary Bar
            if (summary != null) {
                MarketSummaryBar(summary = summary)
            }

            // AI Insights Section
            DesignSectionHeader(title = stringResource(R.string.home_ai_insights_title))
            AIInsightsCard(
                title = stringResource(R.string.home_ai_signal_label),
                subtitle = stringResource(R.string.home_ai_signal_title),
                description = stringResource(R.string.home_ai_signal_desc),
                score = summary?.let { getAIScore(it) },
                tags = listOf(stringResource(R.string.home_ai_tag_sector)),
                icon = Icons.Default.AutoAwesome,
                onClick = onNavigateToAIAnalysis
            )

            // Market Tools Grid
            DesignSectionHeader(title = stringResource(R.string.home_market_tools_title))
            MarketToolsGrid(
                hasData = hasData,
                summary = summary,
                onNavigateToList = onNavigateToList,
                onNavigateToStatistics = onNavigateToStatistics,
                onNavigateToOscillator = onNavigateToOscillator,
                onNavigateToMarketDeposit = onNavigateToMarketDeposit,
                onNavigateToFearGreed = onNavigateToFearGreed,
                onNavigateToMarketOscillator = onNavigateToMarketOscillator,
                onNavigateToPrediction = onNavigateToPrediction,
                onNavigateToAdvancedDashboard = onNavigateToAdvancedDashboard
            )

            // Bottom padding for scroll
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun HomeHeader(
    lastDate: String?,
    onNotificationClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Normal,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            lastDate?.let {
                Text(
                    text = stringResource(R.string.home_last_update_short, it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        IconButton(
            onClick = onNotificationClick,
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = Color.Transparent,
                    shape = MaterialTheme.shapes.extraLarge
                )
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = stringResource(R.string.action_notifications),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun MarketSummaryBar(summary: HomeSummary) {
    val kospiValue = summary.kospiOscillator?.let { String.format("%.0f", it + 2500) } ?: "—"
    val kosdaqValue = summary.kosdaqOscillator?.let { String.format("%.0f", it + 800) } ?: "—"

    val kospiChange = summary.kospiFearGreed?.let {
        val sign = if (it >= 0) "+" else ""
        "$sign${String.format("%.1f", it)}%"
    } ?: "—"

    val kosdaqChange = summary.kosdaqFearGreed?.let {
        val sign = if (it >= 0) "+" else ""
        "$sign${String.format("%.1f", it)}%"
    } ?: "—"

    MarketStatusBar(
        kospiValue = kospiValue,
        kospiChange = kospiChange,
        kospiIsPositive = (summary.kospiFearGreed ?: 0.0) >= 0,
        kosdaqValue = kosdaqValue,
        kosdaqChange = kosdaqChange,
        kosdaqIsPositive = (summary.kosdaqFearGreed ?: 0.0) >= 0
    )
}

@Composable
private fun MarketToolsGrid(
    hasData: Boolean,
    summary: HomeSummary?,
    onNavigateToList: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToOscillator: () -> Unit,
    onNavigateToMarketDeposit: () -> Unit,
    onNavigateToFearGreed: () -> Unit,
    onNavigateToMarketOscillator: () -> Unit,
    onNavigateToPrediction: () -> Unit,
    onNavigateToAdvancedDashboard: () -> Unit
) {
    // First row: Fear & Greed, Supply Analysis
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DashboardCard(
            title = stringResource(R.string.menu_fear_greed),
            subtitle = stringResource(R.string.menu_fear_greed_desc),
            icon = Icons.Default.BarChart,
            value = summary?.kospiFearGreed?.let { String.format("%.0f", it + 50) },
            trendLabel = summary?.kospiFearGreed?.let {
                if (it >= 20) stringResource(R.string.status_greed)
                else if (it <= -20) stringResource(R.string.status_fear)
                else stringResource(R.string.status_neutral)
            },
            trend = if ((summary?.kospiFearGreed ?: 0.0) >= 0) "up" else "down",
            colorRole = DashboardCardColorRole.PRIMARY,
            onClick = onNavigateToFearGreed,
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
        )

        DashboardCard(
            title = stringResource(R.string.menu_stock_analysis),
            subtitle = stringResource(R.string.menu_stock_analysis_desc),
            icon = Icons.Default.ShowChart,
            colorRole = DashboardCardColorRole.SECONDARY,
            onClick = onNavigateToOscillator,
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
        )
    }

    // Second row: ETF Trend, Overbought/Oversold
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DashboardCard(
            title = stringResource(R.string.menu_etf_theme_list),
            subtitle = stringResource(R.string.menu_etf_theme_desc),
            icon = Icons.Default.PieChart,
            colorRole = DashboardCardColorRole.SECONDARY,
            onClick = onNavigateToList,
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
        )

        DashboardCard(
            title = stringResource(R.string.menu_market_overbought),
            subtitle = stringResource(R.string.menu_market_overbought_desc),
            icon = Icons.Default.Speed,
            colorRole = DashboardCardColorRole.ERROR,
            onClick = onNavigateToMarketOscillator,
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
        )
    }

    // Third row: Market Fund, Statistics
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DashboardCard(
            title = stringResource(R.string.menu_market_fund),
            subtitle = stringResource(R.string.menu_market_fund_desc),
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            colorRole = DashboardCardColorRole.PRIMARY,
            onClick = onNavigateToMarketDeposit,
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
        )

        if (hasData) {
            DashboardCard(
                title = stringResource(R.string.menu_etf_statistics),
                subtitle = stringResource(R.string.menu_etf_statistics_desc),
                icon = Icons.Default.Analytics,
                colorRole = DashboardCardColorRole.SECONDARY,
                onClick = onNavigateToStatistics,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }

    // Fourth row: ML Prediction, Advanced Dashboard (if hasData)
    if (hasData) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DashboardCard(
                title = stringResource(R.string.menu_ml_prediction),
                subtitle = stringResource(R.string.menu_ml_prediction_desc),
                icon = Icons.Default.Psychology,
                colorRole = DashboardCardColorRole.PRIMARY,
                onClick = onNavigateToPrediction,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
            )

            DashboardCard(
                title = stringResource(R.string.menu_advanced_analysis),
                subtitle = stringResource(R.string.menu_advanced_analysis_desc),
                icon = Icons.Default.Dashboard,
                colorRole = DashboardCardColorRole.SECONDARY,
                onClick = onNavigateToAdvancedDashboard,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
            )
        }
    }
}

private fun getAIScore(summary: HomeSummary): String? {
    val fearGreed = summary.kospiFearGreed ?: return null
    val score = ((fearGreed + 100) / 2).coerceIn(0.0, 100.0)
    return "+${score.toInt()}${stringResource_placeholder}"
}

// Placeholder for stringResource outside composable
private const val stringResource_placeholder = "점"
