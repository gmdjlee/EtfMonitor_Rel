package com.etfmonitor.feature.stock.presentation.oscillator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.core.ui.component.DateRangeSelector
import com.etfmonitor.core.ui.component.MarketCapOscillatorChart
import com.etfmonitor.core.ui.component.MacdChart
import com.etfmonitor.core.ui.component.TrendSignalChart
import com.etfmonitor.core.ui.component.LoadingCard
import com.etfmonitor.core.ui.component.ErrorCard
import com.etfmonitor.core.ui.component.IdleCard
import com.etfmonitor.core.ui.component.ElderImpulseChart
import com.etfmonitor.core.ui.component.DemarkTDChart
import com.etfmonitor.core.analysis.model.TrendSignalAnalysis
import com.etfmonitor.core.analysis.model.TrendSignalData
import com.etfmonitor.core.analysis.model.TrendTradeSignal
import com.etfmonitor.core.analysis.model.FearGreedState
import com.etfmonitor.core.analysis.model.ElderImpulseData
import com.etfmonitor.core.analysis.model.DemarkTDData
import com.etfmonitor.core.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OscillatorScreen(
    onNavigateBack: () -> Unit,
    initialTicker: String? = null,
    onNavigateToStatistics: ((String) -> Unit)? = null,
    viewModel: OscillatorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val demarkTDInterval by viewModel.demarkTDInterval.collectAsState()
    val trendSignalInterval by viewModel.trendSignalInterval.collectAsState()
    val elderImpulseInterval by viewModel.elderImpulseInterval.collectAsState()
    val quickChartAnalysisEnabled by viewModel.quickChartAnalysisEnabled.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()
    val currentTicker by viewModel.currentTicker.collectAsState()

    // FAB 표시 조건: 설정이 활성화되어 있고, Success 상태일 때
    val showFab = quickChartAnalysisEnabled &&
            onNavigateToStatistics != null &&
            state is OscillatorState.Success

    // Auto-analyze if initialTicker is provided
    LaunchedEffect(initialTicker) {
        if (initialTicker != null && state is OscillatorState.Idle) {
            viewModel.analyzeStock(initialTicker)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.oscillator_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.nav_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            if (showFab && state is OscillatorState.Success) {
                val successState = state as OscillatorState.Success
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToStatistics?.invoke(successState.stockData.ticker) },
                    icon = {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = null
                        )
                    },
                    text = { Text(stringResource(R.string.fab_etf_analysis)) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { padding ->
        var textFieldValue by remember { mutableStateOf("") }
        var showHistoryDialog by remember { mutableStateOf(false) }
        val keyboardController = LocalSoftwareKeyboardController.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search field with Autocomplete - Wrapped in Box for overlay
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Search TextField - Matches EtfListScreen design
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = {
                            textFieldValue = it
                            viewModel.onSearchQueryChanged(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // History 버튼
                                if (searchHistory.isNotEmpty() && textFieldValue.isEmpty()) {
                                    IconButton(onClick = { showHistoryDialog = true }) {
                                        Icon(
                                            Icons.Default.History,
                                            contentDescription = stringResource(R.string.search_history),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                // Clear 버튼
                                if (textFieldValue.isNotEmpty()) {
                                    IconButton(onClick = {
                                        textFieldValue = ""
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
                        shape = MaterialTheme.extendedShapes.searchBar,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
                    )
                }

                // Autocomplete Dropdown - Overlay below TextField
                if (suggestions.isNotEmpty() && textFieldValue.isNotBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = MaterialTheme.spacing.medium,
                                end = MaterialTheme.spacing.medium,
                                top = 72.dp
                            )
                            .heightIn(max = 300.dp),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 8.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth()
                        ) {
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
                                        textFieldValue = stock.name
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

            // Date Range Selector (분석된 종목이 있을 때 표시 - Loading 상태에서도 유지)
            if (currentTicker != null) {
                DateRangeSelector(
                    selectedRange = selectedRange,
                    onRangeSelected = { viewModel.updateDateRange(it) },
                    availableOptions = listOf(
                        DateRangeOption.WEEK,
                        DateRangeOption.MONTH,
                        DateRangeOption.THREE_MONTHS,
                        DateRangeOption.SIX_MONTHS,
                        DateRangeOption.YEAR,
                        DateRangeOption.ALL
                    )
                )
            }

            // State Content
            when (val currentState = state) {
                is OscillatorState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingCard(message = stringResource(R.string.data_analyzing))
                    }
                }

                is OscillatorState.Success -> {
                    // 차트 페이지 목록 구성
                    val chartPages = buildChartPages(
                        currentState = currentState,
                        demarkTDInterval = demarkTDInterval,
                        onDemarkIntervalChange = { viewModel.changeDemarkTDInterval(it) },
                        trendSignalInterval = trendSignalInterval,
                        onTrendSignalIntervalChange = { viewModel.changeTrendSignalInterval(it) },
                        elderImpulseInterval = elderImpulseInterval,
                        onElderImpulseIntervalChange = { viewModel.changeElderImpulseInterval(it) }
                    )

                    val pagerState = rememberPagerState(
                        initialPage = 0,
                        pageCount = { chartPages.size }
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Stock Info Card (고정)
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
                                // 종목명 & 종목코드 (왼쪽)
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
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

                                // 최근 데이터 날짜 & 데이터 포인트 (오른쪽)
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
                }

                is OscillatorState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        ErrorCard(message = currentState.message)
                    }
                }

                is OscillatorState.Idle -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        IdleCard(message = stringResource(R.string.oscillator_idle_message))
                    }
                }
            }
        }

        // Search History Dialog
        if (showHistoryDialog) {
            SearchHistoryDialog(
                searchHistory = searchHistory,
                onDismiss = { showHistoryDialog = false },
                onSelectStock = { ticker ->
                    showHistoryDialog = false
                    viewModel.analyzeStock(ticker)
                }
            )
        }
    }
}

/**
 * 차트 페이지 데이터 클래스
 */
private data class ChartPage(
    val title: String,
    val content: @Composable () -> Unit
)

/**
 * 차트 페이지 목록 빌드
 */
@Composable
private fun buildChartPages(
    currentState: OscillatorState.Success,
    demarkTDInterval: String,
    onDemarkIntervalChange: (String) -> Unit,
    trendSignalInterval: String,
    onTrendSignalIntervalChange: (String) -> Unit,
    elderImpulseInterval: String,
    onElderImpulseIntervalChange: (String) -> Unit
): List<ChartPage> {
    val pages = mutableListOf<ChartPage>()

    // 1. 시가총액 & 수급 오실레이터 차트
    pages.add(
        ChartPage(
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
            ChartPage(
                title = stringResource(R.string.oscillator_chart_demark)
            ) {
                DemarkTDChartWithSelector(
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
            ChartPage(
                title = stringResource(R.string.oscillator_chart_trend)
            ) {
                TrendSignalChartWithSelector(
                    data = trendData,
                    analysis = currentState.trendSignalAnalysis,
                    currentInterval = trendSignalInterval,
                    onIntervalChange = onTrendSignalIntervalChange
                )
            }
        )
    }

    // 4. Elder Impulse 차트
    currentState.elderImpulseData?.let { elderData ->
        pages.add(
            ChartPage(
                title = stringResource(R.string.oscillator_chart_elder)
            ) {
                ElderImpulseChartWithSelector(
                    data = elderData,
                    currentInterval = elderImpulseInterval,
                    onIntervalChange = onElderImpulseIntervalChange
                )
            }
        )
    }

    // 5. MACD 차트
    pages.add(
        ChartPage(
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
 * DeMark TD 차트 + 인터벌 선택 버튼
 */
@Composable
private fun DemarkTDChartWithSelector(
    data: DemarkTDData,
    currentInterval: String,
    onIntervalChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 인터벌 선택 버튼
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IntervalButton(
                text = stringResource(R.string.interval_daily),
                selected = currentInterval == "d",
                onClick = { onIntervalChange("d") },
                modifier = Modifier.weight(1f)
            )
            IntervalButton(
                text = stringResource(R.string.interval_weekly),
                selected = currentInterval == "w",
                onClick = { onIntervalChange("w") },
                modifier = Modifier.weight(1f)
            )
            IntervalButton(
                text = stringResource(R.string.interval_monthly),
                selected = currentInterval == "m",
                onClick = { onIntervalChange("m") },
                modifier = Modifier.weight(1f)
            )
        }

        // DeMark TD 차트
        DemarkTDChart(
            data = data,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 추세 시그널 차트 + 인터벌 선택 버튼
 */
@Composable
private fun TrendSignalChartWithSelector(
    data: TrendSignalData,
    analysis: TrendSignalAnalysis?,
    currentInterval: String,
    onIntervalChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 인터벌 선택 버튼 (일봉/주봉만 지원)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IntervalButton(
                text = stringResource(R.string.interval_daily),
                selected = currentInterval == "d",
                onClick = { onIntervalChange("d") },
                modifier = Modifier.weight(1f)
            )
            IntervalButton(
                text = stringResource(R.string.interval_weekly),
                selected = currentInterval == "w",
                onClick = { onIntervalChange("w") },
                modifier = Modifier.weight(1f)
            )
        }

        // 추세 시그널 차트
        TrendSignalChart(
            data = data,
            latestDate = data.dates.lastOrNull()
        )

        // 분석 카드
        analysis?.let { TrendSignalAnalysisCard(it) }
    }
}

/**
 * Elder Impulse 차트 + 인터벌 선택 버튼
 */
@Composable
private fun ElderImpulseChartWithSelector(
    data: ElderImpulseData,
    currentInterval: String,
    onIntervalChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 인터벌 선택 버튼 (일봉/주봉만 지원)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IntervalButton(
                text = stringResource(R.string.interval_daily),
                selected = currentInterval == "d",
                onClick = { onIntervalChange("d") },
                modifier = Modifier.weight(1f)
            )
            IntervalButton(
                text = stringResource(R.string.interval_weekly),
                selected = currentInterval == "w",
                onClick = { onIntervalChange("w") },
                modifier = Modifier.weight(1f)
            )
        }

        // Elder Impulse 차트
        ElderImpulseChart(
            data = data,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 인터벌 선택 버튼
 */
@Composable
private fun IntervalButton(
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
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(text)
        }
    }
}

@Composable
private fun SearchHistoryDialog(
    searchHistory: List<com.etfmonitor.core.database.entities.SearchHistory>,
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

@Composable
private fun DataRow(label: String, value: String) {
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
 * 추세 시그널 분석 카드
 */
@Composable
private fun TrendSignalAnalysisCard(analysis: TrendSignalAnalysis) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 제목 + 신호
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.oscillator_trend_analysis),
                    style = MaterialTheme.typography.titleMedium
                )

                // 신호 배지
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

            // 추세 설명
            Text(
                analysis.trendDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 지표 값
            DataRow(stringResource(R.string.label_current_price), String.format("%,.0f", analysis.currentPrice))
            DataRow(stringResource(R.string.label_ma), String.format("%,.0f", analysis.maPrice))
            DataRow(stringResource(R.string.label_cmf), String.format("%.3f", analysis.cmfValue))

            // Fear & Greed 상태
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

            // 시그널 카운트
            DataRow(stringResource(R.string.label_recent_buy_signals), "${analysis.recentBuyCount}회")
            DataRow(stringResource(R.string.label_recent_sell_signals), "${analysis.recentSellCount}회")

            HorizontalDivider()

            // 투자 권고
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
