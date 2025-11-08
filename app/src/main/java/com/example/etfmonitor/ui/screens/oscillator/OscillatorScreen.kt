package com.etfmonitor.ui.screens.oscillator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.etfmonitor.oscillator.model.TradeSignal
import com.etfmonitor.ui.components.MarketCapOscillatorChart
import com.etfmonitor.ui.components.MacdChart
import com.etfmonitor.ui.components.LoadingCard
import com.etfmonitor.ui.components.ErrorCard
import com.etfmonitor.ui.components.IdleCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OscillatorScreen(
    onNavigateBack: () -> Unit,
    viewModel: OscillatorViewModel = viewModel(factory = OscillatorViewModel.Factory)
) {
    val state by viewModel.state.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "종목 검색",
                        style = MaterialTheme.typography.titleMedium
                    )

                    var textFieldValue by remember { mutableStateOf("") }

                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("종목명 또는 코드") },
                        placeholder = { Text("예: 삼성전자") },
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            if (textFieldValue.isNotBlank()) {
                                viewModel.searchAndAnalyze(textFieldValue)
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

                    // Signal Analysis Card
                    SignalCard(currentState.signalAnalysis)

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
    }
}

@Composable
private fun SignalCard(analysis: com.etfmonitor.oscillator.model.SignalAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (analysis.signal) {
                TradeSignal.STRONG_BUY -> Color(0xFF1B5E20)
                TradeSignal.BUY -> Color(0xFF388E3C)
                TradeSignal.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
                TradeSignal.SELL -> Color(0xFFD32F2F)
                TradeSignal.STRONG_SELL -> Color(0xFFB71C1C)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "매매 신호",
                style = MaterialTheme.typography.titleMedium,
                color = if (analysis.signal == TradeSignal.NEUTRAL)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    Color.White
            )

            Text(
                when (analysis.signal) {
                    TradeSignal.STRONG_BUY -> "강력 매수"
                    TradeSignal.BUY -> "매수"
                    TradeSignal.NEUTRAL -> "중립"
                    TradeSignal.SELL -> "매도"
                    TradeSignal.STRONG_SELL -> "강력 매도"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (analysis.signal == TradeSignal.NEUTRAL)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    Color.White
            )

            HorizontalDivider()

            InfoRow("점수", "${analysis.score.toInt()}/100")
            InfoRow("추세", analysis.trend)
            InfoRow("외국인", analysis.foreignTrend)
            InfoRow("기관", analysis.institutionTrend)

            HorizontalDivider()

            Text(
                analysis.recommendation,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (analysis.signal == TradeSignal.NEUTRAL)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    Color.White
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
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
