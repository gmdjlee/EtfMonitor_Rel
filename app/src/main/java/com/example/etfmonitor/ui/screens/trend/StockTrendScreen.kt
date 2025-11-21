package com.etfmonitor.ui.screens.trend

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.etfmonitor.database.entities.HoldingTimeSeries
import com.etfmonitor.ui.utils.AmountFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockTrendScreen(
    etfTicker: String,
    stockTicker: String,
    onNavigateBack: () -> Unit,
    viewModel: StockTrendViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when (val s = state) {
                                is TrendState.Success -> s.trend.stockName
                                else -> "종목 추이"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "$etfTicker - $stockTicker",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        when (val s = state) {
            is TrendState.Loading -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is TrendState.Success -> {
                TrendContent(
                    trend = s.trend,
                    modifier = Modifier.padding(padding)
                )
            }
            is TrendState.Error -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    Alignment.Center
                ) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun TrendContent(
    trend: com.etfmonitor.repository.StockTrend,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SummaryCard(trend.timeSeries)
        ChartCard(
            title = "비중 추이 (%)",
            data = trend.timeSeries,
            valueExtractor = { it.weight },
            color = MaterialTheme.colorScheme.primary
        )
        ChartCard(
            title = "평가금액 추이 (억원)",
            data = trend.timeSeries,
            valueExtractor = { it.amount / 100_000_000 },
            color = MaterialTheme.colorScheme.secondary
        )
        DataTable(trend.timeSeries)
    }
}

// ✅ 1. SummaryCard 개선
@Composable
private fun SummaryCard(timeSeries: List<HoldingTimeSeries>) {
    if (timeSeries.isEmpty()) return

    val first = timeSeries.first()
    val last = timeSeries.last()
    val weightChange = last.weight - first.weight
    val amountChange = last.amount - first.amount

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("요약", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("데이터 기간", style = MaterialTheme.typography.labelSmall)
                    Text("${first.date} ~ ${last.date}")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(
                    label = "비중 변화",
                    value = String.format("%+.2f%%", weightChange)
                )
                SummaryItem(
                    label = "금액 변화",
                    value = AmountFormatter.formatChange(amountChange)  // ✅ 개선
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

// ✅ 2. ChartCard 개선 (평가금액 차트용)
@Composable
private fun ChartCard(
    title: String,
    data: List<HoldingTimeSeries>,
    valueExtractor: (HoldingTimeSeries) -> Float,
    color: Color
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ✅ 개선: 차트 제목에 동적 단위 추가
            val maxValue = data.maxOfOrNull { valueExtractor(it) } ?: 0f
            val isAmountChart = title.contains("금액")
            val chartTitle = if (isAmountChart) {
                val unit = AmountFormatter.getChartUnit(maxValue)
                "평가금액 추이 ($unit)"
            } else {
                title
            }

            Text(chartTitle, style = MaterialTheme.typography.titleMedium)

            if (data.isEmpty()) {
                Text("데이터 없음", style = MaterialTheme.typography.bodySmall)
            } else {
                val modelProducer = remember { CartesianChartModelProducer() }
                val scope = rememberCoroutineScope()
                val dateLabelsKey = remember { ExtraStore.Key<List<String>>() }

                LaunchedEffect(data) {
                    scope.launch(Dispatchers.Default) {
                        modelProducer.runTransaction {
                            lineSeries {
                                // ✅ 개선: 차트 값 변환
                                if (isAmountChart) {
                                    series(data.map { AmountFormatter.toChartValue(valueExtractor(it)) })
                                } else {
                                    series(data.map { valueExtractor(it).toDouble() })
                                }
                            }
                            extras { extraStore ->
                                extraStore[dateLabelsKey] = data.map { formatDateForChart(it.date) }
                            }
                        }
                    }
                }

                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(),
                        startAxis = rememberStartAxis(
                            label = rememberTextComponent(
                                color = MaterialTheme.colorScheme.onSurface,
                                textSize = 10.sp
                            )
                        ),
                        bottomAxis = rememberBottomAxis(
                            label = rememberTextComponent(
                                color = MaterialTheme.colorScheme.onSurface,
                                textSize = 10.sp
                            ),
                            valueFormatter = { x, chartValues, _ ->
                                val dateLabels = chartValues.model.extraStore.getOrNull(dateLabelsKey)
                                val index = x.toInt()
                                if (dateLabels != null && index >= 0 && index < dateLabels.size) {
                                    dateLabels[index]
                                } else {
                                    ""
                                }
                            },
                            itemPlacer = remember {
                                HorizontalAxis.ItemPlacer.default(
                                    spacing = 1,
                                    addExtremeLabelPadding = true
                                )
                            }
                        )
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }
    }
}

// ✅ 날짜 포맷 함수 (차트용 - 짧게)
private fun formatDateForChart(date: String): String {
    return try {
        val parts = date.split("-")
        if (parts.size == 3) {
            "${parts[1]}/${parts[2]}"
        } else {
            date
        }
    } catch (e: Exception) {
        date
    }
}

// ✅ 3. DataTable 개선
@Composable
private fun DataTable(timeSeries: List<HoldingTimeSeries>) {
    // 최대 금액 계산
    val maxAmount = timeSeries.maxOfOrNull { it.amount } ?: 0f
    val amountHeader = AmountFormatter.getTableHeader(maxAmount)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("상세 데이터", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("날짜", Modifier.weight(2f), style = MaterialTheme.typography.labelSmall)
                Text("비중", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text(amountHeader, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)  // ✅ 개선
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            timeSeries.reversed().forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(item.date, Modifier.weight(2f), style = MaterialTheme.typography.bodySmall)
                    Text(
                        String.format("%.2f%%", item.weight),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        AmountFormatter.formatForTable(item.amount, maxAmount),  // ✅ 개선
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}