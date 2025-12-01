package com.etfmonitor.ui.screens.oscillator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.ui.components.MarketCapOscillatorChart
import com.etfmonitor.ui.components.MacdChart
import com.etfmonitor.ui.components.TrendSignalChart
import com.etfmonitor.ui.components.LoadingCard
import com.etfmonitor.ui.components.ErrorCard
import com.etfmonitor.ui.components.IdleCard
import com.etfmonitor.ui.components.ElderImpulseChart
import com.etfmonitor.ui.components.DemarkTDChart
import com.etfmonitor.oscillator.model.TrendSignalAnalysis
import com.etfmonitor.oscillator.model.TrendTradeSignal
import com.etfmonitor.oscillator.model.FearGreedState
import com.etfmonitor.oscillator.model.ElderImpulseData
import com.etfmonitor.oscillator.model.DemarkTDData
import com.etfmonitor.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OscillatorScreen(
    onNavigateBack: () -> Unit,
    viewModel: OscillatorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val demarkTDInterval by viewModel.demarkTDInterval.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("차트 분석") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        var textFieldValue by remember { mutableStateOf("") }
        var showHistoryDialog by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search Card with Autocomplete - Wrapped in Box for overlay
            Box(modifier = Modifier.fillMaxWidth()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "종목 검색",
                                style = MaterialTheme.typography.titleMedium
                            )

                            // History 버튼
                            if (searchHistory.isNotEmpty()) {
                                TextButton(onClick = { showHistoryDialog = true }) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("History")
                                }
                            }
                        }

                        // TextField
                        OutlinedTextField(
                            value = textFieldValue,
                            onValueChange = {
                                textFieldValue = it
                                viewModel.updateSearchQuery(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(
                                    "종목명 또는 코드",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            placeholder = {
                                Text(
                                    "예: 삼성전자",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            trailingIcon = {
                                if (textFieldValue.isNotBlank()) {
                                    IconButton(onClick = {
                                        textFieldValue = ""
                                        viewModel.clearSuggestions()
                                    }) {
                                        Icon(
                                            Icons.Default.Clear,
                                            "지우기",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            shape = MaterialTheme.extendedShapes.searchBar,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.outline,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Button(
                            onClick = {
                                if (textFieldValue.isNotBlank()) {
                                    viewModel.searchAndAnalyze(textFieldValue)
                                    viewModel.clearSuggestions()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = textFieldValue.isNotBlank() && state !is OscillatorState.Loading
                        ) {
                            Icon(Icons.Default.Search, null)
                            Spacer(Modifier.width(8.dp))
                            Text("분석")
                        }
                    }
                }

                // Autocomplete Dropdown - Overlay outside Card
                if (suggestions.isNotEmpty() && textFieldValue.isNotBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 120.dp) // Position below TextField
                            .heightIn(max = 300.dp), // Limit max height
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
                            items(suggestions) { stock ->
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
                                        viewModel.clearSuggestions()
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

            // State Content
            when (val currentState = state) {
                is OscillatorState.Loading -> {
                    LoadingCard(message = "데이터 분석 중...")
                }

                is OscillatorState.Success -> {
                    // Stock Info Card
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    }

                    // 시가총액 & 수급 오실레이터 차트
                    MarketCapOscillatorChart(
                        result = currentState.oscillatorResult,
                        marketCap = currentState.stockData.marketCap,
                        latestDate = currentState.stockData.dates.lastOrNull()
                    )

                    // 추세 시그널 차트 (MA/CMF/Fear&Greed) - MACD 차트 위에 배치
                    currentState.trendSignalData?.let { trendData ->
                        TrendSignalChart(
                            data = trendData,
                            latestDate = trendData.dates.lastOrNull()
                        )

                        // 추세 시그널 분석 카드
                        currentState.trendSignalAnalysis?.let { analysis ->
                            TrendSignalAnalysisCard(analysis)
                        }
                    }

                    // MACD 차트
                    MacdChart(
                        result = currentState.oscillatorResult,
                        latestDate = currentState.stockData.dates.lastOrNull()
                    )

                    // Elder Impulse 차트 (주봉)
                    currentState.elderImpulseData?.let { elderData ->
                        ElderImpulseChart(
                            data = elderData,
                            latestDate = elderData.dates.lastOrNull()
                        )
                    }

                    // DeMark TD 차트 (인터벌 선택 가능)
                    currentState.demarkTDData?.let { demarkData ->
                        DemarkTDChartWithSelector(
                            data = demarkData,
                            currentInterval = demarkTDInterval,
                            onIntervalChange = { viewModel.changeDemarkTDInterval(it) }
                        )
                    }

                    // Oscillator Data Card
                    DataCard(currentState.oscillatorResult)
                }

                is OscillatorState.Error -> {
                    ErrorCard(message = currentState.message)
                }

                is OscillatorState.Idle -> {
                    IdleCard(message = "종목을 검색하여 수급 오실레이터를 분석하세요")
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
                text = "일봉",
                selected = currentInterval == "d",
                onClick = { onIntervalChange("d") },
                modifier = Modifier.weight(1f)
            )
            IntervalButton(
                text = "주봉",
                selected = currentInterval == "w",
                onClick = { onIntervalChange("w") },
                modifier = Modifier.weight(1f)
            )
            IntervalButton(
                text = "월봉",
                selected = currentInterval == "m",
                onClick = { onIntervalChange("m") },
                modifier = Modifier.weight(1f)
            )
        }

        // DeMark TD 차트
        DemarkTDChart(
            data = data,
            latestDate = data.dates.lastOrNull()
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
    searchHistory: List<com.etfmonitor.database.entities.SearchHistory>,
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
                Text("최근 검색")
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
                        "검색 히스토리가 없습니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchHistory) { history ->
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
                Text("닫기")
            }
        }
    )
}

@Composable
private fun DataCard(result: com.etfmonitor.oscillator.model.OscillatorResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "최근 데이터",
                style = MaterialTheme.typography.titleMedium
            )

            if (result.dates.isNotEmpty()) {
                val lastIdx = result.dates.size - 1

                DataRow("날짜", result.dates.last())
                DataRow("오실레이터", String.format("%.4f", result.oscillator.last()))
                DataRow("EMA(12)", String.format("%.4f", result.ema.last()))
                DataRow("MACD", String.format("%.4f", result.macd.last()))
                DataRow("Signal", String.format("%.4f", result.signal.last()))
                DataRow("Histogram", String.format("%.4f", result.histogram.last()))

                HorizontalDivider()

                Text(
                    "데이터 포인트: ${result.dates.size}개",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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
                    "추세 시그널 분석",
                    style = MaterialTheme.typography.titleMedium
                )

                // 신호 배지
                val (signalText, signalColor) = when (analysis.signal) {
                    TrendTradeSignal.STRONG_BUY -> "강력 매수" to Color(0xFF4CAF50)
                    TrendTradeSignal.BUY -> "매수" to Color(0xFF8BC34A)
                    TrendTradeSignal.NEUTRAL -> "중립" to Color(0xFF9E9E9E)
                    TrendTradeSignal.SELL -> "매도" to Color(0xFFFF9800)
                    TrendTradeSignal.STRONG_SELL -> "강력 매도" to Color(0xFFF44336)
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
            DataRow("현재가", String.format("%,.0f", analysis.currentPrice))
            DataRow("MA", String.format("%,.0f", analysis.maPrice))
            DataRow("CMF", String.format("%.3f", analysis.cmfValue))

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
                    "Fear & Greed",
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
            DataRow("최근 매수 시그널", "${analysis.recentBuyCount}회")
            DataRow("최근 매도 시그널", "${analysis.recentSellCount}회")

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
