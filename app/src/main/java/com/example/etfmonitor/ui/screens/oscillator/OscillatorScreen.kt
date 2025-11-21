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
import com.etfmonitor.ui.components.LoadingCard
import com.etfmonitor.ui.components.ErrorCard
import com.etfmonitor.ui.components.IdleCard

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
            // Search Card with Autocomplete
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

                    // TextField with autocomplete
                    Column {
                        OutlinedTextField(
                            value = textFieldValue,
                            onValueChange = {
                                textFieldValue = it
                                viewModel.updateSearchQuery(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("종목명 또는 코드") },
                            placeholder = { Text("예: 삼성전자") },
                            singleLine = true,
                            trailingIcon = {
                                if (textFieldValue.isNotBlank()) {
                                    IconButton(onClick = {
                                        textFieldValue = ""
                                        viewModel.clearSuggestions()
                                    }) {
                                        Icon(Icons.Default.Clear, "지우기")
                                    }
                                }
                            }
                        )

                        // Autocomplete Dropdown
                        if (suggestions.isNotEmpty() && textFieldValue.isNotBlank()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    suggestions.forEach { stock ->
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

                    // MACD 차트
                    MacdChart(
                        result = currentState.oscillatorResult,
                        latestDate = currentState.stockData.dates.lastOrNull()
                    )

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
