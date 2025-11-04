package com.etfmonitor.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.etfmonitor.EtfMonitorApp
import com.etfmonitor.database.entities.StockAggregatedTimePoint
import com.etfmonitor.database.entities.StockAggregatedTrend
import com.etfmonitor.repository.DataRepository
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AggregatedStockTrendScreen(
    stockTicker: String,
    onNavigateBack: () -> Unit,
    viewModel: AggregatedStockTrendViewModel = viewModel(
        factory = AggregatedStockTrendViewModel.factory(stockTicker)
    )
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when (val s = state) {
                                is AggregatedTrendState.Success -> s.trend.stockName
                                else -> "종목 통합 추이"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stockTicker,
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
            is AggregatedTrendState.Loading -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is AggregatedTrendState.Success -> {
                AggregatedTrendContent(
                    trend = s.trend,
                    modifier = Modifier.padding(padding)
                )
            }
            is AggregatedTrendState.Error -> {
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
private fun AggregatedTrendContent(
    trend: StockAggregatedTrend,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AggregatedSummaryCard(trend.timeSeries)
        AggregatedChartCard(
            title = "총 평가금액 추이 (억원)",
            data = trend.timeSeries,
            valueExtractor = { it.totalAmount / 100_000_000 }
        )
        AggregatedChartCard(
            title = "최대 비중 추이 (%)",
            data = trend.timeSeries,
            valueExtractor = { it.maxWeight }
        )
        AggregatedChartCard(
            title = "평균 비중 추이 (%)",
            data = trend.timeSeries,
            valueExtractor = { it.avgWeight }
        )
        AggregatedDataTable(trend.timeSeries)
    }
}

@Composable
private fun AggregatedSummaryCard(timeSeries: List<StockAggregatedTimePoint>) {
    if (timeSeries.isEmpty()) return

    val first = timeSeries.first()
    val last = timeSeries.last()
    val amountChange = last.totalAmount - first.totalAmount

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
            Text("요약 (전체 ETF 통합)", style = MaterialTheme.typography.titleMedium)
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
                    label = "현재 총액",
                    value = String.format("%.1f억", last.totalAmount / 100_000_000)
                )
                SummaryItem(
                    label = "금액 변화",
                    value = String.format("%+.1f억", amountChange / 100_000_000)
                )
                SummaryItem(
                    label = "보유 ETF",
                    value = "${last.etfCount}개"
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

@Composable
private fun AggregatedChartCard(
    title: String,
    data: List<StockAggregatedTimePoint>,
    valueExtractor: (StockAggregatedTimePoint) -> Float
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)

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
                                series(data.map { valueExtractor(it).toDouble() })
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
                                    spacing = 2,
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

@Composable
private fun AggregatedDataTable(timeSeries: List<StockAggregatedTimePoint>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("상세 데이터", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("날짜", Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall)
                Text("총액(억)", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("ETF수", Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall)
                Text("최대%", Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall)
                Text("평균%", Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall)
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            timeSeries.reversed().forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(item.date, Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall)
                    Text(
                        String.format("%.1f", item.totalAmount / 100_000_000),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "${item.etfCount}",
                        Modifier.weight(0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        String.format("%.2f", item.maxWeight),
                        Modifier.weight(0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        String.format("%.2f", item.avgWeight),
                        Modifier.weight(0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

class AggregatedStockTrendViewModel(
    private val stockTicker: String,
    private val repository: DataRepository
) : ViewModel() {

    private val _state = MutableStateFlow<AggregatedTrendState>(AggregatedTrendState.Loading)
    val state: StateFlow<AggregatedTrendState> = _state.asStateFlow()

    init {
        loadTrend()
    }

    private fun loadTrend() {
        viewModelScope.launch {
            try {
                val trend = repository.getStockAggregatedTrend(stockTicker)
                _state.value = if (trend != null) {
                    AggregatedTrendState.Success(trend)
                } else {
                    AggregatedTrendState.Error("데이터를 찾을 수 없습니다")
                }
            } catch (e: Exception) {
                _state.value = AggregatedTrendState.Error(e.message ?: "오류 발생")
            }
        }
    }

    companion object {
        fun factory(stockTicker: String): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AggregatedStockTrendViewModel(
                        stockTicker,
                        EtfMonitorApp.instance.repository
                    ) as T
                }
            }
        }
    }
}

sealed class AggregatedTrendState {
    object Loading : AggregatedTrendState()
    data class Success(val trend: StockAggregatedTrend) : AggregatedTrendState()
    data class Error(val message: String) : AggregatedTrendState()
}