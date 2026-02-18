package com.etfmonitor.core.ui.component

import androidx.compose.foundation.isSystemInDarkTheme
import com.etfmonitor.core.common.util.AppLogger
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.feature.market.domain.model.MarketDepositData
import com.etfmonitor.core.analysis.model.OscillatorResult
import com.etfmonitor.core.common.util.DateFormatter
import com.etfmonitor.core.ui.theme.*
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter

/**
 * Market-related chart components
 * - MarketCapOscillatorChart: 시가총액 + 수급 오실레이터 복합 차트
 * - MarketDepositChart: 증시 자금 동향 차트
 */

private val logger = AppLogger.getLogger("MarketCharts")

/**
 * 시가총액 + 수급 오실레이터 복합 차트
 */
@Composable
fun MarketCapOscillatorChart(
    result: OscillatorResult,
    marketCap: List<Long>,
    latestDate: String? = null,
    modifier: Modifier = Modifier,
    chartColorViewModel: ChartColorViewModel = hiltViewModel()
) {
    // 데이터 검증
    if (result.dates.isEmpty() || marketCap.isEmpty()) {
        logger.w("Empty data for MarketCapOscillatorChart")
        return
    }

    // 차트 색상 설정 가져오기
    val chartColors by chartColorViewModel.chartColorSettings.collectAsState()
    val colorSettings = chartColors.marketCapOscillator

    // Modern theme colors
    val isDark = isSystemInDarkTheme()
    val primaryColor = colorSettings.lineColor1  // 시가총액 (기본값: Black)
    val tertiaryColor = colorSettings.lineColor2  // 오실레이터
    val textColor = colorSettings.textColor      // 축 라벨/틱 색상 (기본값: Black)
    val legendColor = colorSettings.legendColor  // 범례 색상 (기본값: Black)
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()

    ChartCard(
        title = "시가총액 & 수급 오실레이터",
        subtitle = latestDate?.let { "최신 데이터: $it" },
        modifier = modifier
    ) {
        AndroidView(
            factory = { context ->
                try {
                    CombinedChart(context).apply {
                        description.isEnabled = false
                        setTouchEnabled(true)
                        isDragEnabled = true
                        setScaleEnabled(true)
                        setPinchZoom(true)
                        setDrawGridBackground(false)
                        setExtraBottomOffset(10f)  // Extra padding for rotated labels
                        setDrawOrder(arrayOf(
                            CombinedChart.DrawOrder.LINE,
                            CombinedChart.DrawOrder.LINE
                        ))

                        // 마커 뷰 설정 (오름차순 날짜 사용)
                        try {
                            val markerView = MarketCapMarkerView(
                                context,
                                R.layout.marker_view,
                                result.dates  // ViewModel에서 오름차순 정렬됨
                            )
                            marker = markerView
                        } catch (e: Exception) {
                            logger.e("Error creating marker", e)
                        }

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
                            setAvoidFirstLastClipping(true)  // Prevent edge label clipping
                            // labelCount and valueFormatter are set in update block
                        }

                        // 왼쪽 Y축 (시가총액)
                        axisLeft.apply {
                            setDrawGridLines(true)
                            gridLineWidth = 1f
                            setGridColor(gridColor)
                            enableGridDashedLine(10f, 5f, 0f)
                            setTextColor(textColor)
                            setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                            valueFormatter = object : ValueFormatter() {
                                override fun getFormattedValue(value: Float): String {
                                    val billions = (value / 100_000_000).toInt()
                                    return when {
                                        billions >= 10000 -> "${billions / 10000}조"
                                        billions >= 1000 -> String.format("%.1f조", billions / 10000f)
                                        else -> "${billions}억"
                                    }
                                }
                            }
                        }

                        // 오른쪽 Y축 (오실레이터)
                        axisRight.apply {
                            isEnabled = true
                            setDrawGridLines(false)
                            setTextColor(textColor)
                            setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                        }

                        legend.apply {
                            isEnabled = true
                            textSize = 12f
                            setTextColor(legendColor)
                        }
                    }
                } catch (e: Exception) {
                    logger.e("Error creating chart", e)
                    CombinedChart(context)
                }
            },
            update = { chart ->
                try {
                    // 데이터는 ViewModel에서 오름차순 정렬됨 (oldest→newest)
                    val chartDates = result.dates
                    val chartMarketCap = marketCap
                    val chartOscillator = result.oscillator

                    val dataCount = chartDates.size

                    // Update x-axis with dynamic label count and smart date formatting
                    chart.xAxis.apply {
                        setLabelCount(ChartLabelCalculator.calculateOptimalLabelCount(dataCount), false)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val index = value.toInt()
                                return if (index >= 0 && index < chartDates.size) {
                                    DateFormatter.formatForChartByDataCount(chartDates[index], dataCount)
                                } else {
                                    ""
                                }
                            }
                        }
                    }

                    // 시가총액 라인
                    val marketCapEntries = chartMarketCap.mapIndexed { index, value ->
                        Entry(index.toFloat(), value.toFloat())
                    }
                    val marketCapDataSet = LineDataSet(marketCapEntries, "시가총액").apply {
                        axisDependency = YAxis.AxisDependency.LEFT
                        color = primaryColor
                        lineWidth = 2.5f
                        setCircleColor(primaryColor)
                        circleRadius = 2f
                        setDrawCircleHole(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        highLightColor = primaryColor
                    }

                    // 오실레이터 라인
                    val oscEntries = chartOscillator.mapIndexed { index, value ->
                        Entry(index.toFloat(), value.toFloat())
                    }
                    val oscDataSet = LineDataSet(oscEntries, "오실레이터").apply {
                        axisDependency = YAxis.AxisDependency.RIGHT
                        color = tertiaryColor
                        lineWidth = 2.5f
                        setCircleColor(tertiaryColor)
                        circleRadius = 2f
                        setDrawCircleHole(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        highLightColor = tertiaryColor
                    }

                    val lineData = LineData(marketCapDataSet, oscDataSet)
                    val combinedData = CombinedData().apply {
                        setData(lineData)
                    }

                    chart.data = combinedData
                    chart.invalidate()
                } catch (e: Exception) {
                    logger.e("Error updating chart", e)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
        )
    }
}

/**
 * 증시 자금 동향 차트
 */
@Composable
fun MarketDepositChart(
    data: MarketDepositData,
    latestDate: String? = null,
    modifier: Modifier = Modifier,
    chartColorViewModel: ChartColorViewModel = hiltViewModel()
) {
    // 차트 색상 설정 가져오기
    val chartColors by chartColorViewModel.chartColorSettings.collectAsState()
    val colorSettings = chartColors.marketDeposit

    // Modern theme colors
    val isDark = isSystemInDarkTheme()
    val depositColor = colorSettings.lineColor1   // 고객예탁금 (기본값: Black)
    val creditColor = colorSettings.lineColor2    // 신용잔고
    val textColor = colorSettings.textColor       // 축 라벨/틱 색상 (기본값: Black)
    val legendColor = colorSettings.legendColor   // 범례 색상 (기본값: Black)
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()

    ChartCard(
        title = "증시 자금 동향 (고객예탁금 & 신용잔고)",
        subtitle = latestDate?.let { "최신 데이터: $it" },
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
                    setExtraBottomOffset(10f)  // Extra padding for rotated labels

                    // 마커 뷰
                    val markerView = CustomMarkerView(
                        context,
                        R.layout.marker_view,
                        data.dates
                    ) { value ->
                        "${value.toInt()}억원"
                    }
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
                        setAvoidFirstLastClipping(true)  // Prevent edge label clipping
                        // labelCount and valueFormatter are set in update block
                    }

                    // 왼쪽 Y축 (고객예탁금)
                    axisLeft.apply {
                        setDrawGridLines(true)
                        gridLineWidth = 1f
                        setGridColor(gridColor)
                        enableGridDashedLine(10f, 5f, 0f)
                        setTextColor(textColor)
                        setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return "${value.toInt()}억"
                            }
                        }
                    }

                    // 오른쪽 Y축 (신용잔고)
                    axisRight.apply {
                        isEnabled = true
                        setDrawGridLines(false)
                        setTextColor(textColor)
                        setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return "${value.toInt()}억"
                            }
                        }
                    }

                    legend.apply {
                        isEnabled = true
                        textSize = 12f
                        setTextColor(legendColor)
                    }
                }
            },
            update = { chart ->
                val dataCount = data.dates.size

                // Update x-axis with dynamic label count and smart date formatting
                chart.xAxis.apply {
                    setLabelCount(ChartLabelCalculator.calculateOptimalLabelCount(dataCount), false)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val index = value.toInt()
                            return if (index >= 0 && index < data.dates.size) {
                                DateFormatter.formatForChartByDataCount(data.dates[index], dataCount)
                            } else {
                                ""
                            }
                        }
                    }
                }

                // 고객예탁금
                val depositEntries = data.depositAmounts.mapIndexed { index, value ->
                    Entry(index.toFloat(), value.toFloat())
                }
                val depositDataSet = LineDataSet(depositEntries, "고객예탁금").apply {
                    axisDependency = YAxis.AxisDependency.LEFT
                    color = depositColor
                    lineWidth = 2.5f
                    setCircleColor(depositColor)
                    circleRadius = 2f
                    setDrawCircleHole(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    highLightColor = depositColor
                }

                // 신용잔고
                val creditEntries = data.creditAmounts.mapIndexed { index, value ->
                    Entry(index.toFloat(), value.toFloat())
                }
                val creditDataSet = LineDataSet(creditEntries, "신용잔고").apply {
                    axisDependency = YAxis.AxisDependency.RIGHT
                    color = creditColor
                    lineWidth = 2.5f
                    setCircleColor(creditColor)
                    circleRadius = 2f
                    setDrawCircleHole(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    highLightColor = creditColor
                }

                val lineData = LineData(depositDataSet, creditDataSet)
                val combinedData = CombinedData().apply {
                    setData(lineData)
                }

                chart.data = combinedData
                chart.invalidate()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
        )
    }
}
