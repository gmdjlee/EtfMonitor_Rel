package com.etfmonitor.feature.stock.presentation.statistics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.etfmonitor.R
import com.etfmonitor.feature.stock.domain.model.StockAggregatedTimePoint
import com.etfmonitor.feature.stock.domain.model.StockAggregatedTrend
import com.etfmonitor.feature.stock.domain.repository.StockStatisticsRepository
import com.etfmonitor.core.common.util.AmountFormatter
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.core.ui.component.DateRangeSelector
import com.etfmonitor.core.ui.theme.ChartGridDark
import com.etfmonitor.core.ui.theme.ChartGridLight
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

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
    val selectedRange by viewModel.selectedRange.collectAsState()

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
                    selectedRange = selectedRange,
                    onRangeSelected = { viewModel.updateDateRange(it) },
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
    selectedRange: DateRangeOption,
    onRangeSelected: (DateRangeOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 기간 선택
        DateRangeSelector(
            selectedRange = selectedRange,
            onRangeSelected = onRangeSelected
        )

        AggregatedSummaryCard(trend.timeSeries)

        AggregatedChartSection(
            title = "총 평가금액 추이",
            data = trend.timeSeries,
            valueExtractor = { it.totalAmount / 100_000_000 },
            chartColor = 0
        )
        AggregatedChartSection(
            title = "최대 비중 추이 (%)",
            data = trend.timeSeries,
            valueExtractor = { it.maxWeight },
            chartColor = 1
        )
        AggregatedChartSection(
            title = "평균 비중 추이 (%)",
            data = trend.timeSeries,
            valueExtractor = { it.avgWeight },
            chartColor = 2
        )
        AggregatedDataTable(trend.timeSeries)

        Spacer(modifier = Modifier.height(16.dp))
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

/**
 * Fear & Greed 스타일 차트 섹션
 * Surface with RoundedCornerShape, BorderStroke, chart title styling
 */
@Composable
private fun AggregatedChartSection(
    title: String,
    data: List<StockAggregatedTimePoint>,
    valueExtractor: (StockAggregatedTimePoint) -> Float,
    chartColor: Int // 0: primary, 1: secondary, 2: tertiary
) {
    val maxValue = data.maxOfOrNull { valueExtractor(it) } ?: 0f
    val isAmountChart = title.contains("금액")
    val chartTitle = if (isAmountChart) {
        val unit = AmountFormatter.getChartUnit(maxValue)
        "총 평가금액 추이 ($unit)"
    } else {
        title
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = chartTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (data.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "데이터 없음",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                AggregatedLineChart(
                    data = data,
                    valueExtractor = valueExtractor,
                    colorIndex = chartColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                )
            }
        }
    }
}

/**
 * MPAndroidChart 기반 라인 차트 (Fear & Greed 스타일)
 */
@Composable
private fun AggregatedLineChart(
    data: List<StockAggregatedTimePoint>,
    valueExtractor: (StockAggregatedTimePoint) -> Float,
    colorIndex: Int,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val lineColor = when (colorIndex) {
        0 -> MaterialTheme.colorScheme.primary.toArgb()
        1 -> MaterialTheme.colorScheme.secondary.toArgb()
        else -> MaterialTheme.colorScheme.tertiary.toArgb()
    }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()

    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)
                legend.isEnabled = false

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(true)
                    gridLineWidth = 1f
                    setGridColor(gridColor)
                    enableGridDashedLine(10f, 5f, 0f)
                    setTextColor(textColor)
                    granularity = 1f
                    labelRotationAngle = -45f
                    setLabelCount(6, false)
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    gridLineWidth = 1f
                    setGridColor(gridColor)
                    enableGridDashedLine(10f, 5f, 0f)
                    setTextColor(textColor)
                }

                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            val entries = data.mapIndexed { index, item ->
                Entry(index.toFloat(), valueExtractor(item))
            }

            val dataSet = LineDataSet(entries, "").apply {
                color = lineColor
                lineWidth = 2.5f
                setCircleColor(lineColor)
                circleRadius = 2f
                setDrawCircleHole(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                highLightColor = lineColor
            }

            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val index = value.toInt()
                    return if (index >= 0 && index < data.size) {
                        formatDateForChart(data[index].date)
                    } else {
                        ""
                    }
                }
            }

            chart.data = LineData(dataSet)
            chart.invalidate()
        },
        modifier = modifier
    )
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
    private val stockStatisticsRepository: StockStatisticsRepository,
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

    // 날짜 범위 선택 상태
    private val _selectedRange = MutableStateFlow(DateRangeOption.YEAR)
    val selectedRange: StateFlow<DateRangeOption> = _selectedRange.asStateFlow()

    // 전체 데이터 캐시
    private var fullTrend: StockAggregatedTrend? = null

    init {
        loadTrend()
        loadQuickChartAnalysisSetting()
    }

    /**
     * 날짜 범위 업데이트
     */
    fun updateDateRange(option: DateRangeOption) {
        if (option == _selectedRange.value) return
        _selectedRange.value = option
        applyDateRangeFilter()
    }

    private fun loadTrend() {
        viewModelScope.launch {
            try {
                val trend = stockStatisticsRepository.getStockAggregatedTrend(stockTicker)
                fullTrend = trend
                if (trend != null) {
                    applyDateRangeFilter()
                } else {
                    _state.value = AggregatedTrendState.Error("데이터를 찾을 수 없습니다")
                }
            } catch (e: Exception) {
                _state.value = AggregatedTrendState.Error(e.message ?: "오류 발생")
            }
        }
    }

    private fun applyDateRangeFilter() {
        val trend = fullTrend ?: return

        val filteredTimeSeries = if (_selectedRange.value == DateRangeOption.ALL) {
            trend.timeSeries
        } else {
            val cutoffDate = LocalDate.now().minusDays(_selectedRange.value.days.toLong())
            trend.timeSeries.filter { point ->
                try {
                    LocalDate.parse(point.date) >= cutoffDate
                } catch (e: Exception) {
                    true // 파싱 실패 시 포함
                }
            }
        }

        val filteredTrend = trend.copy(timeSeries = filteredTimeSeries)
        _state.value = AggregatedTrendState.Success(filteredTrend)
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