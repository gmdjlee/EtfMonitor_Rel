package com.etfmonitor.core.ui.component

import android.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.core.analysis.*
import com.etfmonitor.core.database.entities.TrendDirection
import com.etfmonitor.core.ui.theme.*
import com.etfmonitor.core.common.util.AppLogger
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter

/**
 * 시계열 분석 차트 컴포넌트
 */

private val logger = AppLogger.getLogger("TimeSeriesCharts")

/**
 * 시계열 데이터 개요 차트
 * 시장 지수 + Fear & Greed + Oscillator를 함께 표시
 */
@Composable
fun TimeSeriesOverviewChart(
    data: TimeSeriesData,
    modifier: Modifier = Modifier,
    chartColorViewModel: ChartColorViewModel = hiltViewModel()
) {
    if (data.dataPoints.isEmpty()) {
        logger.w("Empty data for TimeSeriesOverviewChart")
        return
    }

    val chartColors by chartColorViewModel.chartColorSettings.collectAsState()
    val colorSettings = chartColors.marketCapOscillator

    val isDark = isSystemInDarkTheme()
    val indexColor = colorSettings.lineColor1
    val fearGreedColor = Color.rgb(156, 39, 176)  // Purple
    val oscillatorColor = colorSettings.lineColor2
    val textColor = colorSettings.textColor
    val legendColor = colorSettings.legendColor
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()

    val dates = data.getDates()
    val indexValues = data.getSeriesValues(TimeSeriesIndicator.MARKET_INDEX)
    val fearGreedValues = data.getSeriesValues(TimeSeriesIndicator.FEAR_GREED)
    val oscillatorValues = data.getSeriesValues(TimeSeriesIndicator.OSCILLATOR)

    ChartCard(
        title = "${data.market} 시계열 개요",
        subtitle = "${data.startDate} ~ ${data.endDate} (${data.totalDays}일)",
        modifier = modifier
    ) {
        AndroidView(
            factory = { context ->
                CombinedChart(context).apply {
                    description.isEnabled = false
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(true)
                    setPinchZoom(true)
                    setDrawGridBackground(false)

                    // 마커 뷰
                    val markerView = CustomMarkerView(
                        context,
                        R.layout.marker_view,
                        dates
                    ) { value -> String.format("%.2f", value) }
                    marker = markerView

                    // X축 설정
                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        setDrawGridLines(true)
                        gridLineWidth = 1f
                        setGridColor(gridColor)
                        enableGridDashedLine(10f, 5f, 0f)
                        setTextColor(textColor)
                        granularity = 1f
                        labelRotationAngle = -45f
                        setLabelCount(ChartLabelCalculator.calculateOptimalLabelCount(dates.size), false)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val index = value.toInt()
                                return if (index >= 0 && index < dates.size) {
                                    dates[index].substring(5)  // MM-DD
                                } else ""
                            }
                        }
                    }

                    // 왼쪽 Y축 (시장 지수)
                    axisLeft.apply {
                        setDrawGridLines(true)
                        gridLineWidth = 1f
                        setGridColor(gridColor)
                        enableGridDashedLine(10f, 5f, 0f)
                        setTextColor(textColor)
                        setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                    }

                    // 오른쪽 Y축 (Fear & Greed, Oscillator)
                    axisRight.apply {
                        isEnabled = true
                        setDrawGridLines(false)
                        setTextColor(textColor)
                        setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                        axisMinimum = -0.2f
                        axisMaximum = 1.2f
                    }

                    legend.apply {
                        isEnabled = true
                        textSize = 10f
                        setTextColor(legendColor)
                    }
                }
            },
            update = { chart ->
                try {
                    val lineDataSets = mutableListOf<LineDataSet>()

                    // 시장 지수 라인
                    val indexEntries = indexValues.mapIndexedNotNull { index, value ->
                        value?.let { Entry(index.toFloat(), it.toFloat()) }
                    }
                    if (indexEntries.isNotEmpty()) {
                        val indexDataSet = LineDataSet(indexEntries, "지수").apply {
                            axisDependency = YAxis.AxisDependency.LEFT
                            color = indexColor
                            lineWidth = 2.5f
                            setDrawCircles(false)
                            setDrawValues(false)
                            mode = LineDataSet.Mode.CUBIC_BEZIER
                        }
                        lineDataSets.add(indexDataSet)
                    }

                    // Fear & Greed 라인
                    val fearGreedEntries = fearGreedValues.mapIndexedNotNull { index, value ->
                        value?.let { Entry(index.toFloat(), it.toFloat()) }
                    }
                    if (fearGreedEntries.isNotEmpty()) {
                        val fearGreedDataSet = LineDataSet(fearGreedEntries, "F&G").apply {
                            axisDependency = YAxis.AxisDependency.RIGHT
                            color = fearGreedColor
                            lineWidth = 2f
                            setDrawCircles(false)
                            setDrawValues(false)
                            mode = LineDataSet.Mode.CUBIC_BEZIER
                        }
                        lineDataSets.add(fearGreedDataSet)
                    }

                    // Oscillator 라인 (0-100 -> 0-1로 정규화)
                    val oscillatorEntries = oscillatorValues.mapIndexedNotNull { index, value ->
                        value?.let { Entry(index.toFloat(), (it / 100f).toFloat()) }
                    }
                    if (oscillatorEntries.isNotEmpty()) {
                        val oscillatorDataSet = LineDataSet(oscillatorEntries, "Osc").apply {
                            axisDependency = YAxis.AxisDependency.RIGHT
                            color = oscillatorColor
                            lineWidth = 2f
                            setDrawCircles(false)
                            setDrawValues(false)
                            enableDashedLine(10f, 5f, 0f)
                        }
                        lineDataSets.add(oscillatorDataSet)
                    }

                    if (lineDataSets.isNotEmpty()) {
                        val lineData = LineData(lineDataSets.toList())
                        val combinedData = CombinedData().apply {
                            setData(lineData)
                        }
                        chart.data = combinedData
                        chart.invalidate()
                    }
                } catch (e: Exception) {
                    logger.e("Error updating TimeSeriesOverviewChart", e)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        )
    }
}

/**
 * 자금 동향 시계열 차트
 */
@Composable
fun TimeSeriesDepositChart(
    data: TimeSeriesData,
    modifier: Modifier = Modifier,
    chartColorViewModel: ChartColorViewModel = hiltViewModel()
) {
    if (data.dataPoints.isEmpty()) {
        return
    }

    val chartColors by chartColorViewModel.chartColorSettings.collectAsState()
    val colorSettings = chartColors.marketDeposit

    val isDark = isSystemInDarkTheme()
    val depositColor = colorSettings.lineColor1
    val creditColor = colorSettings.lineColor2
    val textColor = colorSettings.textColor
    val legendColor = colorSettings.legendColor
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()

    val dates = data.getDates()
    val depositAmounts = data.getSeriesValues(TimeSeriesIndicator.DEPOSIT_AMOUNT)
    val creditAmounts = data.getSeriesValues(TimeSeriesIndicator.CREDIT_AMOUNT)

    ChartCard(
        title = "자금 동향",
        subtitle = "${data.startDate} ~ ${data.endDate}",
        modifier = modifier
    ) {
        AndroidView(
            factory = { context ->
                CombinedChart(context).apply {
                    description.isEnabled = false
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(true)
                    setPinchZoom(true)
                    setDrawGridBackground(false)

                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        setDrawGridLines(true)
                        gridLineWidth = 1f
                        setGridColor(gridColor)
                        setTextColor(textColor)
                        granularity = 1f
                        labelRotationAngle = -45f
                        setLabelCount(ChartLabelCalculator.calculateOptimalLabelCount(dates.size), false)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val index = value.toInt()
                                return if (index >= 0 && index < dates.size) {
                                    dates[index].substring(5)
                                } else ""
                            }
                        }
                    }

                    axisLeft.apply {
                        setDrawGridLines(true)
                        setTextColor(textColor)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return "${(value / 10000).toInt()}만억"
                            }
                        }
                    }

                    axisRight.apply {
                        isEnabled = true
                        setDrawGridLines(false)
                        setTextColor(textColor)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return "${(value / 10000).toInt()}만억"
                            }
                        }
                    }

                    legend.apply {
                        isEnabled = true
                        textSize = 10f
                        setTextColor(legendColor)
                    }
                }
            },
            update = { chart ->
                try {
                    val lineDataSets = mutableListOf<LineDataSet>()

                    // 고객예탁금
                    val depositEntries = depositAmounts.mapIndexedNotNull { index, value ->
                        value?.let { Entry(index.toFloat(), it.toFloat()) }
                    }
                    if (depositEntries.isNotEmpty()) {
                        val depositDataSet = LineDataSet(depositEntries, "예탁금").apply {
                            axisDependency = YAxis.AxisDependency.LEFT
                            color = depositColor
                            lineWidth = 2.5f
                            setDrawCircles(false)
                            setDrawValues(false)
                            mode = LineDataSet.Mode.CUBIC_BEZIER
                        }
                        lineDataSets.add(depositDataSet)
                    }

                    // 신용잔고
                    val creditEntries = creditAmounts.mapIndexedNotNull { index, value ->
                        value?.let { Entry(index.toFloat(), it.toFloat()) }
                    }
                    if (creditEntries.isNotEmpty()) {
                        val creditDataSet = LineDataSet(creditEntries, "신용잔고").apply {
                            axisDependency = YAxis.AxisDependency.RIGHT
                            color = creditColor
                            lineWidth = 2.5f
                            setDrawCircles(false)
                            setDrawValues(false)
                            mode = LineDataSet.Mode.CUBIC_BEZIER
                        }
                        lineDataSets.add(creditDataSet)
                    }

                    if (lineDataSets.isNotEmpty()) {
                        val lineData = LineData(lineDataSets.toList())
                        val combinedData = CombinedData().apply {
                            setData(lineData)
                        }
                        chart.data = combinedData
                        chart.invalidate()
                    }
                } catch (e: Exception) {
                    logger.e("Error updating TimeSeriesDepositChart", e)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        )
    }
}

/**
 * ETF 통계 시계열 차트
 */
@Composable
fun TimeSeriesEtfChart(
    data: TimeSeriesData,
    modifier: Modifier = Modifier,
    chartColorViewModel: ChartColorViewModel = hiltViewModel()
) {
    if (data.dataPoints.isEmpty()) {
        return
    }

    val chartColors by chartColorViewModel.chartColorSettings.collectAsState()
    val colorSettings = chartColors.macd

    val isDark = isSystemInDarkTheme()
    val positiveColor = colorSettings.positiveColor
    val negativeColor = colorSettings.negativeColor
    val textColor = colorSettings.textColor
    val legendColor = colorSettings.legendColor
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()

    val dates = data.getDates()
    val netFlowValues = data.getSeriesValues(TimeSeriesIndicator.ETF_NET_FLOW)

    ChartCard(
        title = "ETF 순편입 추이",
        subtitle = "${data.startDate} ~ ${data.endDate}",
        modifier = modifier
    ) {
        AndroidView(
            factory = { context ->
                CombinedChart(context).apply {
                    description.isEnabled = false
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(true)
                    setPinchZoom(true)
                    setDrawGridBackground(false)

                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        setDrawGridLines(true)
                        gridLineWidth = 1f
                        setGridColor(gridColor)
                        setTextColor(textColor)
                        granularity = 1f
                        labelRotationAngle = -45f
                        setLabelCount(ChartLabelCalculator.calculateOptimalLabelCount(dates.size), false)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val index = value.toInt()
                                return if (index >= 0 && index < dates.size) {
                                    dates[index].substring(5)
                                } else ""
                            }
                        }
                    }

                    axisLeft.apply {
                        setDrawGridLines(true)
                        setTextColor(textColor)
                    }

                    axisRight.isEnabled = false

                    legend.apply {
                        isEnabled = true
                        textSize = 10f
                        setTextColor(legendColor)
                    }
                }
            },
            update = { chart ->
                try {
                    // 순편입 Bar
                    val barEntries = netFlowValues.mapIndexedNotNull { index, value ->
                        value?.let { BarEntry(index.toFloat(), it.toFloat()) }
                    }

                    if (barEntries.isNotEmpty()) {
                        val barDataSet = BarDataSet(barEntries, "순편입").apply {
                            colors = netFlowValues.map { value ->
                                if ((value ?: 0.0) >= 0) positiveColor else negativeColor
                            }
                            setDrawValues(false)
                        }
                        val barData = BarData(barDataSet).apply {
                            barWidth = 0.8f
                        }

                        val combinedData = CombinedData().apply {
                            setData(barData)
                        }
                        chart.data = combinedData
                        chart.invalidate()
                    }
                } catch (e: Exception) {
                    logger.e("Error updating TimeSeriesEtfChart", e)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
    }
}

/**
 * 추세 분석 시각화 컴포넌트
 */
@Composable
fun TrendAnalysisCard(
    trends: List<TrendAnalysis>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "추세 분석",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            trends.forEach { trend ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        trend.indicator,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row {
                        Text(
                            trend.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when (trend.direction) {
                                TrendDirection.STRONG_UP, TrendDirection.UP ->
                                    androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                TrendDirection.STRONG_DOWN, TrendDirection.DOWN ->
                                    androidx.compose.ui.graphics.Color(0xFFE53935)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            String.format("%+.1f%%", trend.recentChange),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (trend.recentChange >= 0)
                                androidx.compose.ui.graphics.Color(0xFF4CAF50)
                            else
                                androidx.compose.ui.graphics.Color(0xFFE53935)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 상관관계 분석 시각화 컴포넌트
 */
@Composable
fun CorrelationAnalysisCard(
    correlations: List<CorrelationPair>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "상관관계 분석",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            correlations.take(6).forEach { corr ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${corr.indicator1} vs ${corr.indicator2}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        String.format("%+.3f", corr.correlation),
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            corr.correlation > 0.3 -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                            corr.correlation < -0.3 -> androidx.compose.ui.graphics.Color(0xFFE53935)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

/**
 * 이상치 경고 카드
 */
@Composable
fun AnomalyAlertCard(
    anomalies: List<AnomalyPoint>,
    modifier: Modifier = Modifier
) {
    if (anomalies.isEmpty()) return

    val highAnomalies = anomalies.filter { it.severity == AnomalySeverity.HIGH }

    if (highAnomalies.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "주의: 이상치 탐지",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))

            highAnomalies.take(3).forEach { anomaly ->
                Text(
                    "${anomaly.date}: ${anomaly.indicator}에서 이상값 (${String.format("%.2f", anomaly.value)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

// ============================================================
// 종목 주가 시계열 차트
// ============================================================

/**
 * 종목 주가 차트 (OHLC + 거래량)
 */
@Composable
fun StockPriceChart(
    data: StockTimeSeriesData,
    modifier: Modifier = Modifier,
    chartColorViewModel: ChartColorViewModel = hiltViewModel()
) {
    if (data.dataPoints.isEmpty()) {
        logger.w("Empty data for StockPriceChart")
        return
    }

    val chartColors by chartColorViewModel.chartColorSettings.collectAsState()
    val colorSettings = chartColors.marketCapOscillator

    val isDark = isSystemInDarkTheme()
    val priceColor = colorSettings.lineColor1
    val volumeColor = Color.rgb(100, 181, 246)  // Light blue
    val textColor = colorSettings.textColor
    val legendColor = colorSettings.legendColor
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()

    val dates = data.getDates()
    val closePrices = data.getClosePrices()
    val volumes = data.getVolumes()

    ChartCard(
        title = "${data.name} (${data.ticker})",
        subtitle = "${data.startDate} ~ ${data.endDate} (${data.totalDays}일)",
        modifier = modifier
    ) {
        AndroidView(
            factory = { context ->
                CombinedChart(context).apply {
                    description.isEnabled = false
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(true)
                    setPinchZoom(true)
                    setDrawGridBackground(false)
                    isHighlightPerDragEnabled = true

                    // 마커 뷰
                    val markerView = CustomMarkerView(
                        context,
                        R.layout.marker_view,
                        dates
                    ) { value -> String.format("%,.0f", value) }
                    marker = markerView

                    // X축 설정
                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        setDrawGridLines(true)
                        gridLineWidth = 1f
                        setGridColor(gridColor)
                        enableGridDashedLine(10f, 5f, 0f)
                        setTextColor(textColor)
                        granularity = 1f
                        labelRotationAngle = -45f
                        setLabelCount(ChartLabelCalculator.calculateOptimalLabelCount(dates.size), false)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val index = value.toInt()
                                return if (index >= 0 && index < dates.size) {
                                    dates[index].substring(5)  // MM-DD
                                } else ""
                            }
                        }
                    }

                    // 왼쪽 Y축 (주가)
                    axisLeft.apply {
                        setDrawGridLines(true)
                        gridLineWidth = 1f
                        setGridColor(gridColor)
                        enableGridDashedLine(10f, 5f, 0f)
                        setTextColor(textColor)
                        setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return String.format("%,.0f", value)
                            }
                        }
                    }

                    // 오른쪽 Y축 (거래량)
                    axisRight.apply {
                        isEnabled = true
                        setDrawGridLines(false)
                        setTextColor(textColor)
                        setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return when {
                                    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000)
                                    value >= 1_000 -> String.format("%.0fK", value / 1_000)
                                    else -> String.format("%.0f", value)
                                }
                            }
                        }
                    }

                    legend.apply {
                        isEnabled = true
                        textSize = 10f
                        setTextColor(legendColor)
                    }
                }
            },
            update = { chart ->
                try {
                    // 주가 라인
                    val priceEntries = closePrices.mapIndexed { index, price ->
                        Entry(index.toFloat(), price.toFloat())
                    }
                    val priceDataSet = LineDataSet(priceEntries, "종가").apply {
                        axisDependency = YAxis.AxisDependency.LEFT
                        color = priceColor
                        lineWidth = 2.5f
                        setDrawCircles(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        setDrawHighlightIndicators(true)
                        highLightColor = Color.YELLOW
                    }
                    val lineData = LineData(priceDataSet)

                    // 거래량 바
                    val volumeEntries = volumes.mapIndexed { index, vol ->
                        BarEntry(index.toFloat(), vol.toFloat())
                    }
                    val changeRates = data.getChangeRates()
                    val volumeDataSet = BarDataSet(volumeEntries, "거래량").apply {
                        axisDependency = YAxis.AxisDependency.RIGHT
                        colors = changeRates.mapIndexed { index, rate ->
                            if (index == 0 || rate >= 0) Color.rgb(76, 175, 80)  // Green
                            else Color.rgb(244, 67, 54)  // Red
                        }
                        setDrawValues(false)
                    }
                    val barData = BarData(volumeDataSet).apply {
                        barWidth = 0.8f
                    }

                    val combinedData = CombinedData().apply {
                        setData(lineData)
                        setData(barData)
                    }
                    chart.data = combinedData
                    chart.invalidate()
                } catch (e: Exception) {
                    logger.e("Error updating StockPriceChart", e)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
        )
    }
}

/**
 * 종목 분석 요약 카드
 */
@Composable
fun StockAnalysisSummaryCard(
    result: StockTimeSeriesAnalysisResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "분석 요약",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 가격 추세
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("가격 추세", style = MaterialTheme.typography.bodyMedium)
                Row {
                    Text(
                        result.priceTrend.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (result.priceTrend.direction) {
                            TrendDirection.STRONG_UP, TrendDirection.UP ->
                                androidx.compose.ui.graphics.Color(0xFF4CAF50)
                            TrendDirection.STRONG_DOWN, TrendDirection.DOWN ->
                                androidx.compose.ui.graphics.Color(0xFFE53935)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        String.format("%+.1f%%", result.priceTrend.recentChange),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.priceTrend.recentChange >= 0)
                            androidx.compose.ui.graphics.Color(0xFF4CAF50)
                        else
                            androidx.compose.ui.graphics.Color(0xFFE53935)
                    )
                }
            }

            // 거래량 추세
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("거래량 추세", style = MaterialTheme.typography.bodyMedium)
                Text(
                    result.volumeTrend.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (result.volumeTrend.direction) {
                        TrendDirection.STRONG_UP, TrendDirection.UP ->
                            androidx.compose.ui.graphics.Color(0xFF4CAF50)
                        TrendDirection.STRONG_DOWN, TrendDirection.DOWN ->
                            androidx.compose.ui.graphics.Color(0xFFE53935)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            // 변동성
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("일별 변동성", style = MaterialTheme.typography.bodyMedium)
                val volatilityLevel = when {
                    result.volatility > 3.0 -> "높음"
                    result.volatility > 1.5 -> "보통"
                    else -> "낮음"
                }
                Text(
                    "${String.format("%.2f", result.volatility)}% ($volatilityLevel)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        result.volatility > 3.0 -> androidx.compose.ui.graphics.Color(0xFFE53935)
                        result.volatility > 1.5 -> androidx.compose.ui.graphics.Color(0xFFFFA726)
                        else -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                    }
                )
            }

            // 평균 거래량
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("평균 거래량", style = MaterialTheme.typography.bodyMedium)
                Text(
                    String.format("%,d주", result.avgVolume),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // 가격 범위
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("가격 범위", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${String.format("%,.0f", result.priceRange.first)}원 ~ ${String.format("%,.0f", result.priceRange.second)}원",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
