package com.etfmonitor.ui.screens.oscillator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.oscillator.model.ElderImpulseData
import com.etfmonitor.oscillator.model.DemarkTDData
import com.etfmonitor.ui.components.*
import com.etfmonitor.ui.theme.*

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
    val quickChartAnalysisEnabled by viewModel.quickChartAnalysisEnabled.collectAsState()

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
                    SearchTextField(
                        value = textFieldValue,
                        onValueChange = {
                            textFieldValue = it
                            viewModel.onSearchQueryChanged(it)
                        },
                        hasHistory = searchHistory.isNotEmpty(),
                        onHistoryClick = { showHistoryDialog = true },
                        onClear = {
                            textFieldValue = ""
                            viewModel.onClearSuggestions()
                        },
                        onSearchDone = { keyboardController?.hide() }
                    )
                }

                // Autocomplete Dropdown
                if (textFieldValue.isNotBlank()) {
                    SearchAutocompleteDropdown(
                        suggestions = suggestions,
                        onSuggestionSelected = { ticker ->
                            val stock = suggestions.find { it.ticker == ticker }
                            textFieldValue = stock?.name ?: ""
                            viewModel.onClearSuggestions()
                            viewModel.analyzeStock(ticker)
                        },
                        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium)
                    )
                }
            }

            // State Content
            when (val currentState = state) {
                is OscillatorState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
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
                        onDemarkIntervalChange = { viewModel.changeDemarkTDInterval(it) }
                    )

                    val pagerState = rememberPagerState(
                        initialPage = 0,
                        pageCount = { chartPages.size }
                    )

                    Column(
                        modifier = Modifier.fillMaxSize()
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
                }

                is OscillatorState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ErrorCard(message = currentState.message)
                    }
                }

                is OscillatorState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
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
    onDemarkIntervalChange: (String) -> Unit
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TrendSignalChart(
                        data = trendData,
                        latestDate = trendData.dates.lastOrNull()
                    )
                    currentState.trendSignalAnalysis?.let { analysis ->
                        TrendSignalAnalysisCard(analysis)
                    }
                }
            }
        )
    }

    // 4. Elder Impulse 차트
    currentState.elderImpulseData?.let { elderData ->
        pages.add(
            ChartPage(
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
            PeriodToggleButton(
                text = stringResource(R.string.interval_daily),
                isSelected = currentInterval == "d",
                onClick = { onIntervalChange("d") },
                modifier = Modifier.weight(1f)
            )
            PeriodToggleButton(
                text = stringResource(R.string.interval_weekly),
                isSelected = currentInterval == "w",
                onClick = { onIntervalChange("w") },
                modifier = Modifier.weight(1f)
            )
            PeriodToggleButton(
                text = stringResource(R.string.interval_monthly),
                isSelected = currentInterval == "m",
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

