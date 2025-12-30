package com.etfmonitor.feature.stock.presentation.trend

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.core.ui.component.DateRangeSelector
import com.etfmonitor.core.ui.theme.ChartGridDark
import com.etfmonitor.core.ui.theme.ChartGridLight
import com.etfmonitor.feature.stock.domain.model.StockTrend
import com.etfmonitor.feature.stock.domain.model.HoldingTimeSeries
import com.etfmonitor.core.common.util.AmountFormatter
import com.etfmonitor.core.common.util.DateFormatter
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockTrendScreen(
    etfTicker: String,
    stockTicker: String,
    onNavigateBack: () -> Unit,
    onNavigateToOscillator: ((String) -> Unit)? = null,
    viewModel: StockTrendViewModel = hiltViewModel()
) {
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
                                is StockTrendState.Success -> s.trend.stockName
                                else -> stringResource(R.string.stock_trend_title)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.nav_back))
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
            is StockTrendState.Loading -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is StockTrendState.Success -> {
                TrendContent(
                    trend = s.trend,
                    selectedRange = selectedRange,
                    onRangeSelected = { viewModel.updateDateRange(it) },
                    modifier = Modifier.padding(padding)
                )
            }
            is StockTrendState.Error -> {
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
    trend: StockTrend,
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
            onRangeSelected = onRangeSelected,
            availableOptions = listOf(
                DateRangeOption.WEEK,
                DateRangeOption.MONTH,
                DateRangeOption.THREE_MONTHS,
                DateRangeOption.SIX_MONTHS,
                DateRangeOption.YEAR,
                DateRangeOption.ALL
            )
        )

        SummaryCard(trend.timeSeries)

        StockTrendChartSection(
            title = stringResource(R.string.stock_trend_weight_chart),
            data = trend.timeSeries,
            valueExtractor = { it.weight },
            valueFormatter = { String.format("%.2f%%", it) },
            isPrimary = true
        )
        StockTrendChartSection(
            title = stringResource(R.string.stock_trend_amount_chart),
            data = trend.timeSeries,
            valueExtractor = { it.amount / 100_000_000 },
            valueFormatter = { AmountFormatter.format(it * 100_000_000) },
            isPrimary = false
        )
        DataTable(trend.timeSeries)

        Spacer(modifier = Modifier.height(16.dp))
    }
}

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
            Text(stringResource(R.string.stock_trend_summary), style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(stringResource(R.string.label_data_period), style = MaterialTheme.typography.labelSmall)
                    Text("${first.date} ~ ${last.date}")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(
                    label = stringResource(R.string.label_weight_change),
                    value = String.format("%+.2f%%", weightChange)
                )
                SummaryItem(
                    label = stringResource(R.string.label_amount_change),
                    value = AmountFormatter.formatChange(amountChange)
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

/**
 * Fear & Greed 스타일 차트 섹션
 * Surface with RoundedCornerShape, BorderStroke, chart title styling
 */
@Composable
private fun StockTrendChartSection(
    title: String,
    data: List<HoldingTimeSeries>,
    valueExtractor: (HoldingTimeSeries) -> Float,
    valueFormatter: (Float) -> String,
    isPrimary: Boolean
) {
    val maxValue = data.maxOfOrNull { valueExtractor(it) } ?: 0f
    val isAmountChart = title.contains("금액")
    val chartTitle = if (isAmountChart) {
        val unit = AmountFormatter.getChartUnit(maxValue)
        "평가금액 추이 ($unit)"
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
                StockTrendLineChart(
                    data = data,
                    valueExtractor = valueExtractor,
                    isPrimary = isPrimary,
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
private fun StockTrendLineChart(
    data: List<HoldingTimeSeries>,
    valueExtractor: (HoldingTimeSeries) -> Float,
    isPrimary: Boolean,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val lineColor = if (isPrimary) {
        MaterialTheme.colorScheme.primary.toArgb()
    } else {
        MaterialTheme.colorScheme.secondary.toArgb()
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

private fun formatDateForChart(date: String): String = DateFormatter.formatForChart(date)

@Composable
private fun DataTable(timeSeries: List<HoldingTimeSeries>) {
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
                Text(amountHeader, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            timeSeries.reversed().take(5).forEach { item ->
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
                        AmountFormatter.formatForTable(item.amount, maxAmount),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
