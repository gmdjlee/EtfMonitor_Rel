package com.etfmonitor.feature.stock.presentation.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.etfmonitor.R
import com.etfmonitor.core.database.entities.StockAggregatedTimePoint
import com.etfmonitor.core.database.entities.StockAggregatedTrend
import com.etfmonitor.repository.DataRepository
import com.etfmonitor.core.common.util.AmountFormatter
import com.etfmonitor.core.ui.component.ChartCard
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
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
    onNavigateToOscillator: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val factory = EntryPointAccessors.fromApplication(
        context.applicationContext,
        AggregatedStockTrendViewModelFactoryProvider::class.java
    ).aggregatedStockTrendViewModelFactory()

    val viewModel: AggregatedStockTrendViewModel = viewModel(
        factory = AggregatedStockTrendViewModel.provideFactory(
            assistedFactory = factory,
            stockTicker = stockTicker
        )
    )
    val state by viewModel.state.collectAsState()
    val quickChartAnalysisEnabled by viewModel.quickChartAnalysisEnabled.collectAsState()

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
        },
        floatingActionButton = {
            if (quickChartAnalysisEnabled && onNavigateToOscillator != null) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToOscillator(viewModel.stockTicker) },
                    icon = { Icon(Icons.Default.ShowChart, contentDescription = null) },
                    text = { Text(stringResource(R.string.go_to_oscillator_analysis)) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
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

// ✅ 1. AggregatedSummaryCard 개선
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
                    value = AmountFormatter.format(last.totalAmount)  // ✅ 개선
                )
                SummaryItem(
                    label = "금액 변화",
                    value = AmountFormatter.formatChange(amountChange)  // ✅ 개선
                )
                SummaryItem(
                    label = "보유 ETF",
                    value = "${last.etfCount}개"
                )
            }
        }
    }
}

// SummaryItem is defined in CashDepositTab.kt (internal visibility)

// ✅ 2. AggregatedChartCard 개선
// Uses shared ChartCard with dark mode support
@Composable
private fun AggregatedChartCard(
    title: String,
    data: List<StockAggregatedTimePoint>,
    valueExtractor: (StockAggregatedTimePoint) -> Float
) {
    // ✅ 개선: 차트 제목에 동적 단위 추가
    val maxValue = data.maxOfOrNull { valueExtractor(it) } ?: 0f
    val isAmountChart = title.contains("금액")
    val chartTitle = if (isAmountChart) {
        val unit = AmountFormatter.getChartUnit(maxValue)
        "총 평가금액 추이 ($unit)"
    } else {
        title
    }

    ChartCard(
        title = chartTitle,
        modifier = Modifier.fillMaxWidth()
    ) {
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
                            color = ComposeColor.Black,
                            textSize = 10.sp
                        )
                    ),
                    bottomAxis = rememberBottomAxis(
                        label = rememberTextComponent(
                            color = ComposeColor.Black,
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

// formatDateForChart is defined in CashDepositTab.kt (internal visibility)

// ✅ 3. AggregatedDataTable 개선
@Composable
private fun AggregatedDataTable(timeSeries: List<StockAggregatedTimePoint>) {
    // 최대 금액 계산
    val maxAmount = timeSeries.maxOfOrNull { it.totalAmount } ?: 0f
    val amountHeader = AmountFormatter.getTableHeader(maxAmount)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("상세 데이터", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("날짜", Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall)
                Text(amountHeader, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)  // ✅ 개선
                Text("ETF수", Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall)
                Text("최대%", Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall)
                Text("평균%", Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall)
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            timeSeries.reversed().take(5).forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(item.date, Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall)
                    Text(
                        AmountFormatter.formatForTable(item.totalAmount, maxAmount),  // ✅ 개선
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

/**
 * Production-ready ViewModel using Hilt Assisted Injection
 *
 * 최적화:
 * - @AssistedInject: 런타임 파라미터(stockTicker)와 Hilt 의존성(repository)을 모두 지원
 * - AssistedFactory: 타입 안전한 팩토리 패턴
 * - EtfMonitorApp.instance 제거: 메모리 누수 위험 제거
 */
class AggregatedStockTrendViewModel @AssistedInject constructor(
    @Assisted val stockTicker: String,
    private val repository: DataRepository,
    private val etfDao: com.etfmonitor.core.database.EtfDao,
    val pyClient: com.etfmonitor.core.network.python.OscillatorPyClient
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(stockTicker: String): AggregatedStockTrendViewModel
    }

    companion object {
        private const val QUICK_CHART_ANALYSIS_KEY = "quick_chart_analysis_enabled"

        fun provideFactory(
            assistedFactory: Factory,
            stockTicker: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return assistedFactory.create(stockTicker) as T
            }
        }
    }

    private val _state = MutableStateFlow<AggregatedTrendState>(AggregatedTrendState.Loading)
    val state: StateFlow<AggregatedTrendState> = _state.asStateFlow()

    private val _quickChartAnalysisEnabled = MutableStateFlow(false)
    val quickChartAnalysisEnabled: StateFlow<Boolean> = _quickChartAnalysisEnabled.asStateFlow()

    init {
        loadTrend()
        loadQuickChartAnalysisSetting()
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

    private fun loadQuickChartAnalysisSetting() {
        viewModelScope.launch {
            try {
                val enabled = etfDao.getSetting(QUICK_CHART_ANALYSIS_KEY) == "true"
                _quickChartAnalysisEnabled.value = enabled
            } catch (e: Exception) {
                // Ignore error, keep default value
            }
        }
    }
}

sealed class AggregatedTrendState {
    object Loading : AggregatedTrendState()
    data class Success(val trend: StockAggregatedTrend) : AggregatedTrendState()
    data class Error(val message: String) : AggregatedTrendState()
}

/**
 * Hilt EntryPoint to access AssistedFactory from Composable
 * EntryPoint는 Factory 타입을 제공하는 메서드를 가져야 함
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AggregatedStockTrendViewModelFactoryProvider {
    fun aggregatedStockTrendViewModelFactory(): AggregatedStockTrendViewModel.Factory
}