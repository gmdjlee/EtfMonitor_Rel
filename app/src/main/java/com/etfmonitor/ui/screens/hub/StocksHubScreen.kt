package com.etfmonitor.ui.screens.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.oscillator.model.DemarkTDData
import com.etfmonitor.oscillator.model.FearGreedState
import com.etfmonitor.oscillator.model.TrendSignalAnalysis
import com.etfmonitor.oscillator.model.TrendTradeSignal
import com.etfmonitor.ui.components.MarketCapOscillatorChart
import com.etfmonitor.ui.components.MacdChart
import com.etfmonitor.ui.components.TrendSignalChart
import com.etfmonitor.ui.components.ElderImpulseChart
import com.etfmonitor.ui.components.DemarkTDChart
import com.etfmonitor.database.entities.SearchHistory
import com.etfmonitor.ui.screens.oscillator.OscillatorViewModel
import com.etfmonitor.ui.screens.oscillator.OscillatorState

/**
 * Stocks Hub Screen - 종목
 *
 * Renamed from 종목 수급 분석 to 종목
 * Shows stock supply/demand analysis (Oscillator)
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StocksHubScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStatistics: (String) -> Unit,
    initialTicker: String? = null,
    viewModel: OscillatorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val demarkTDInterval by viewModel.demarkTDInterval.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()

    var showHistoryDialog by remember { mutableStateOf(false) }

    // Set initial ticker if provided (skip history save when navigating via FAB)
    LaunchedEffect(initialTicker) {
        initialTicker?.let { ticker ->
            viewModel.analyzeStock(ticker, saveHistory = false)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        HubHeader(
            title = "종목",
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onSettingsClick = onNavigateToSettings
        )

        // Search Bar
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = {
                    Text(
                        stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // History button
                        if (searchHistory.isNotEmpty() && searchQuery.isEmpty()) {
                            IconButton(onClick = { showHistoryDialog = true }) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = stringResource(R.string.search_history),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        // Clear button
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.onSearchQueryChanged("")
                                viewModel.onClearSuggestions()
                            }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.action_clear),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )

            // Autocomplete Dropdown
            if (suggestions.isNotEmpty() && searchQuery.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 64.dp)
                        .heightIn(max = 300.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(suggestions, key = { it.ticker }) { stock ->
                            ListItem(
                                headlineContent = { Text(stock.name) },
                                supportingContent = {
                                    Text(
                                        "${stock.ticker} • ${stock.market}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                modifier = Modifier.clickable {
                                    viewModel.onClearSuggestions()
                                    viewModel.analyzeStock(stock.ticker)
                                }
                            )
                            if (stock != suggestions.last()) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content
        when (val currentState = state) {
            is OscillatorState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.data_analyzing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is OscillatorState.Success -> {
                // Build chart pages
                val chartPages = buildStockChartPages(
                    currentState = currentState,
                    demarkTDInterval = demarkTDInterval,
                    onDemarkIntervalChange = { viewModel.changeDemarkTDInterval(it) }
                )

                val pagerState = rememberPagerState(
                    initialPage = 0,
                    pageCount = { chartPages.size }
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Stock Info Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        currentState.stockData.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        currentState.stockData.ticker,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (currentState.oscillatorResult.dates.isNotEmpty()) {
                                        Text(
                                            currentState.oscillatorResult.dates.last(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "${currentState.oscillatorResult.dates.size}개 데이터",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Page Indicators + Chart Title
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = chartPages.getOrNull(pagerState.currentPage)?.title ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Page Indicators
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                chartPages.forEachIndexed { index, _ ->
                                    Box(
                                        modifier = Modifier
                                            .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                                            .background(
                                                color = if (index == pagerState.currentPage)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Horizontal Pager for Charts
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            pageSpacing = 16.dp
                        ) { page ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                chartPages[page].content()
                            }
                        }

                        // Swipe hint
                        Text(
                            text = stringResource(R.string.oscillator_swipe_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    // Floating Action Button
                    ExtendedFloatingActionButton(
                        onClick = { onNavigateToStatistics(currentState.stockData.ticker) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        icon = {
                            Icon(Icons.Default.Analytics, contentDescription = null)
                        },
                        text = {
                            Text(stringResource(R.string.fab_etf_analysis))
                        }
                    )
                }
            }

            is OscillatorState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = currentState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            is OscillatorState.Idle -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (suggestions.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.search_results),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        suggestions.forEach { stock ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { viewModel.analyzeStock(stock.ticker) },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = stock.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                        Text(
                                            text = "${stock.ticker} • ${stock.market}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else if (searchQuery.isEmpty()) {
                        // Empty state - prompt to search
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.oscillator_idle_message),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "수급 분석, 추세 신호, MACD 등을 확인할 수 있습니다",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Search History Dialog
    if (showHistoryDialog) {
        StockSearchHistoryDialog(
            searchHistory = searchHistory,
            onDismiss = { showHistoryDialog = false },
            onSelectStock = { ticker ->
                showHistoryDialog = false
                viewModel.analyzeStock(ticker)
            }
        )
    }
}

/**
 * Chart page data class
 */
private data class StockChartPage(
    val title: String,
    val content: @Composable () -> Unit
)

/**
 * Build chart pages list
 */
@Composable
private fun buildStockChartPages(
    currentState: OscillatorState.Success,
    demarkTDInterval: String,
    onDemarkIntervalChange: (String) -> Unit
): List<StockChartPage> {
    val pages = mutableListOf<StockChartPage>()

    // 1. 시가총액 & 수급 오실레이터 차트
    pages.add(
        StockChartPage(
            title = stringResource(R.string.oscillator_chart_marketcap)
        ) {
            MarketCapOscillatorChart(
                result = currentState.oscillatorResult,
                marketCap = currentState.stockData.marketCap,
                latestDate = currentState.stockData.dates.lastOrNull()
            )
        }
    )

    // 2. DeMark TD 차트
    currentState.demarkTDData?.let { demarkData ->
        pages.add(
            StockChartPage(
                title = stringResource(R.string.oscillator_chart_demark)
            ) {
                StockDemarkTDChartWithSelector(
                    data = demarkData,
                    currentInterval = demarkTDInterval,
                    onIntervalChange = onDemarkIntervalChange
                )
            }
        )
    }

    // 3. 추세 시그널 차트 + 분석 카드
    currentState.trendSignalData?.let { trendData ->
        pages.add(
            StockChartPage(
                title = stringResource(R.string.oscillator_chart_trend)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TrendSignalChart(
                        data = trendData,
                        latestDate = trendData.dates.lastOrNull()
                    )
                    currentState.trendSignalAnalysis?.let { analysis ->
                        StockTrendSignalAnalysisCard(analysis)
                    }
                }
            }
        )
    }

    // 4. Elder Impulse 차트
    currentState.elderImpulseData?.let { elderData ->
        pages.add(
            StockChartPage(
                title = stringResource(R.string.oscillator_chart_elder)
            ) {
                ElderImpulseChart(
                    data = elderData,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    // 5. MACD 차트
    pages.add(
        StockChartPage(
            title = stringResource(R.string.oscillator_chart_macd)
        ) {
            MacdChart(
                result = currentState.oscillatorResult,
                latestDate = currentState.stockData.dates.lastOrNull()
            )
        }
    )

    return pages
}

/**
 * DeMark TD Chart with interval selector
 */
@Composable
private fun StockDemarkTDChartWithSelector(
    data: DemarkTDData,
    currentInterval: String,
    onIntervalChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Interval selection buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StockIntervalButton(
                text = stringResource(R.string.interval_daily),
                selected = currentInterval == "d",
                onClick = { onIntervalChange("d") },
                modifier = Modifier.weight(1f)
            )
            StockIntervalButton(
                text = stringResource(R.string.interval_weekly),
                selected = currentInterval == "w",
                onClick = { onIntervalChange("w") },
                modifier = Modifier.weight(1f)
            )
            StockIntervalButton(
                text = stringResource(R.string.interval_monthly),
                selected = currentInterval == "m",
                onClick = { onIntervalChange("m") },
                modifier = Modifier.weight(1f)
            )
        }

        DemarkTDChart(data = data, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StockIntervalButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(text)
        }
    }
}

/**
 * Trend Signal Analysis Card
 */
@Composable
private fun StockTrendSignalAnalysisCard(analysis: TrendSignalAnalysis) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title + Signal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.oscillator_trend_analysis),
                    style = MaterialTheme.typography.titleMedium
                )

                val (signalText, signalColor) = when (analysis.signal) {
                    TrendTradeSignal.STRONG_BUY -> stringResource(R.string.signal_strong_buy) to Color(0xFF4CAF50)
                    TrendTradeSignal.BUY -> stringResource(R.string.signal_buy) to Color(0xFF8BC34A)
                    TrendTradeSignal.NEUTRAL -> stringResource(R.string.signal_neutral) to Color(0xFF9E9E9E)
                    TrendTradeSignal.SELL -> stringResource(R.string.signal_sell) to Color(0xFFFF9800)
                    TrendTradeSignal.STRONG_SELL -> stringResource(R.string.signal_strong_sell) to Color(0xFFF44336)
                }

                Surface(
                    color = signalColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = signalText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = signalColor
                    )
                }
            }

            HorizontalDivider()

            // Trend description
            Text(
                analysis.trendDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Indicator values
            StockDataRow(stringResource(R.string.label_current_price), String.format("%,.0f", analysis.currentPrice))
            StockDataRow(stringResource(R.string.label_ma), String.format("%,.0f", analysis.maPrice))
            StockDataRow(stringResource(R.string.label_cmf), String.format("%.3f", analysis.cmfValue))

            // Fear & Greed state
            val fearGreedState = FearGreedState.fromValue(analysis.fearGreedValue)
            val fearGreedColor = when (fearGreedState) {
                FearGreedState.EXTREME_FEAR -> Color(0xFFF44336)
                FearGreedState.FEAR -> Color(0xFFFF9800)
                FearGreedState.NEUTRAL -> Color(0xFF9E9E9E)
                FearGreedState.GREED -> Color(0xFF8BC34A)
                FearGreedState.EXTREME_GREED -> Color(0xFF4CAF50)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.label_fear_greed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        String.format("%.2f", analysis.fearGreedValue),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Surface(
                        color = fearGreedColor.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = fearGreedState.displayName,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = fearGreedColor
                        )
                    }
                }
            }

            // Signal counts
            StockDataRow(stringResource(R.string.label_recent_buy_signals), "${analysis.recentBuyCount}회")
            StockDataRow(stringResource(R.string.label_recent_sell_signals), "${analysis.recentSellCount}회")

            HorizontalDivider()

            // Recommendation
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = analysis.recommendation,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun StockDataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Search History Dialog
 */
@Composable
private fun StockSearchHistoryDialog(
    searchHistory: List<SearchHistory>,
    onDismiss: () -> Unit,
    onSelectStock: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.recent_search))
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                if (searchHistory.isEmpty()) {
                    Text(
                        stringResource(R.string.search_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchHistory, key = { it.id }) { history ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                ListItem(
                                    headlineContent = { Text(history.name) },
                                    supportingContent = {
                                        Text(
                                            "${history.ticker} • ${history.market}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        onSelectStock(history.ticker)
                                    }
                                )
                                if (history != searchHistory.last()) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}
