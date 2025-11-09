package com.etfmonitor.ui.components

import android.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.etfmonitor.R
import com.etfmonitor.oscillator.model.MarketDepositData
import com.etfmonitor.oscillator.model.OscillatorResult
import com.etfmonitor.ui.theme.*
import android.util.Log

/**
 * 시가총액 + 수급 오실레이터 복합 차트
 */
@Composable
fun MarketCapOscillatorChart(
    result: OscillatorResult,
    marketCap: List<Long>,
    latestDate: String? = null,
    modifier: Modifier = Modifier
) {
    // 데이터 검증
    if (result.dates.isEmpty() || marketCap.isEmpty()) {
        Log.w("ChartComponents", "Empty data for MarketCapOscillatorChart")
        return
    }

    // Jetcaster 테마 색상 가져오기
    val isDark = isSystemInDarkTheme()
    val primaryColor = ChartOrange.toArgb()  // 시가총액 - 오렌지
    val tertiaryColor = ChartYellow.toArgb()  // 오실레이터 - 노란색
    val textColor = if (isDark) ChartTextDark.toArgb() else ChartTextLight.toArgb()
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
                        setDrawOrder(arrayOf(
                            CombinedChart.DrawOrder.LINE,
                            CombinedChart.DrawOrder.LINE
                        ))

                        // 마커 뷰 설정
                        try {
                            val markerView = MarketCapMarkerView(
                                context,
                                R.layout.marker_view,
                                result.dates
                            )
                            marker = markerView
                        } catch (e: Exception) {
                            Log.e("ChartComponents", "Error creating marker", e)
                        }

                        // X축 설정
                        xAxis.apply {
                            position = XAxis.XAxisPosition.BOTTOM
                            setDrawGridLines(true)
                            gridLineWidth = 1f
                            gridColor = gridColor
                            enableGridDashedLine(10f, 5f, 0f)
                            textColor = textColor
                            granularity = 1f
                            labelRotationAngle = -45f
                            setLabelCount(10, false)
                            valueFormatter = object : ValueFormatter() {
                                override fun getFormattedValue(value: Float): String {
                                    val index = value.toInt()
                                    return if (index >= 0 && index < result.dates.size) {
                                        result.dates[index]
                                    } else {
                                        ""
                                    }
                                }
                            }
                        }

                        // 왼쪽 Y축 (시가총액)
                        axisLeft.apply {
                            setDrawGridLines(true)
                            gridLineWidth = 1f
                            gridColor = gridColor
                            enableGridDashedLine(10f, 5f, 0f)
                            textColor = primaryColor
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
                            textColor = tertiaryColor
                            setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                        }

                        legend.apply {
                            isEnabled = true
                            textSize = 12f
                            textColor = textColor
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChartComponents", "Error creating chart", e)
                    CombinedChart(context)
                }
            },
            update = { chart ->
                try {
                    // 시가총액 라인
                    val marketCapEntries = marketCap.mapIndexed { index, value ->
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
                    val oscEntries = result.oscillator.mapIndexed { index, value ->
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
                    Log.e("ChartComponents", "Error updating chart", e)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
        )
    }
}

/**
 * MACD 차트
 */
@Composable
fun MacdChart(
    result: OscillatorResult,
    latestDate: String? = null,
    modifier: Modifier = Modifier
) {
    // Jetcaster 테마 색상 가져오기
    val isDark = isSystemInDarkTheme()
    val macdColor = ChartOrange.toArgb()      // MACD 라인 - 오렌지
    val signalColor = ChartYellow.toArgb()    // Signal 라인 - 노란색
    val positiveColor = ChartGreen.toArgb()   // Histogram 양수 - 녹색
    val negativeColor = ChartRed.toArgb()     // Histogram 음수 - 빨간색
    val textColor = if (isDark) ChartTextDark.toArgb() else ChartTextLight.toArgb()
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()

    ChartCard(
        title = "MACD",
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
                    setDrawOrder(arrayOf(
                        CombinedChart.DrawOrder.BAR,
                        CombinedChart.DrawOrder.LINE
                    ))

                    // MACD 전용 마커 뷰
                    val markerView = MacdMarkerView(
                        context,
                        R.layout.marker_view,
                        result.dates,
                        result.macd,
                        result.signal
                    )
                    marker = markerView

                    // X축 설정
                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        setDrawGridLines(true)
                        gridLineWidth = 1f
                        gridColor = gridColor
                        enableGridDashedLine(10f, 5f, 0f)
                        textColor = textColor
                        granularity = 1f
                        labelRotationAngle = -45f
                        setLabelCount(10, false)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val index = value.toInt()
                                return if (index >= 0 && index < result.dates.size) {
                                    result.dates[index]
                                } else {
                                    ""
                                }
                            }
                        }
                    }

                    // Y축 설정
                    axisLeft.apply {
                        setDrawGridLines(true)
                        gridLineWidth = 1f
                        gridColor = gridColor
                        enableGridDashedLine(10f, 5f, 0f)
                        textColor = textColor
                        setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                    }
                    axisRight.isEnabled = false

                    legend.apply {
                        isEnabled = true
                        textSize = 12f
                        textColor = textColor
                    }
                }
            },
            update = { chart ->
                // Histogram
                val barEntries = result.histogram.mapIndexed { index, value ->
                    BarEntry(index.toFloat(), value.toFloat())
                }
                val barDataSet = BarDataSet(barEntries, "").apply {
                    colors = result.histogram.map { value ->
                        if (value >= 0) positiveColor
                        else negativeColor
                    }
                    setDrawValues(false)
                    isHighlightEnabled = false
                }
                val barData = BarData(barDataSet).apply {
                    barWidth = 0.8f
                }

                // MACD 라인
                val macdEntries = result.macd.mapIndexed { index, value ->
                    Entry(index.toFloat(), value.toFloat())
                }
                val macdDataSet = LineDataSet(macdEntries, "MACD").apply {
                    color = macdColor
                    lineWidth = 2f
                    setCircleColor(macdColor)
                    circleRadius = 2f
                    setDrawCircleHole(false)
                    setDrawValues(false)
                    highLightColor = macdColor
                }

                // Signal 라인
                val signalEntries = result.signal.mapIndexed { index, value ->
                    Entry(index.toFloat(), value.toFloat())
                }
                val signalDataSet = LineDataSet(signalEntries, "Signal").apply {
                    color = signalColor
                    lineWidth = 2f
                    setCircleColor(signalColor)
                    circleRadius = 2f
                    setDrawCircleHole(false)
                    setDrawValues(false)
                    enableDashedLine(10f, 5f, 0f)
                    highLightColor = signalColor
                }

                val lineData = LineData(macdDataSet, signalDataSet)
                val combinedData = CombinedData().apply {
                    setData(barData)
                    setData(lineData)
                }

                chart.data = combinedData
                chart.legend.isEnabled = true
                chart.invalidate()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
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
    modifier: Modifier = Modifier
) {
    // Jetcaster 테마 색상 가져오기
    val isDark = isSystemInDarkTheme()
    val depositColor = ChartOrange.toArgb()   // 고객예탁금 - 오렌지
    val creditColor = ChartPink.toArgb()      // 신용잔고 - 분홍색
    val textColor = if (isDark) ChartTextDark.toArgb() else ChartTextLight.toArgb()
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
                        gridColor = gridColor
                        enableGridDashedLine(10f, 5f, 0f)
                        textColor = textColor
                        granularity = 1f
                        labelRotationAngle = -45f
                        setLabelCount(8, false)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val index = value.toInt()
                                return if (index >= 0 && index < data.dates.size) {
                                    data.dates[index]
                                } else {
                                    ""
                                }
                            }
                        }
                    }

                    // 왼쪽 Y축 (고객예탁금)
                    axisLeft.apply {
                        setDrawGridLines(true)
                        gridLineWidth = 1f
                        gridColor = gridColor
                        enableGridDashedLine(10f, 5f, 0f)
                        textColor = depositColor
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
                        textColor = creditColor
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
                        textColor = textColor
                    }
                }
            },
            update = { chart ->
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

/**
 * 차트 카드 컨테이너
 */
@Composable
private fun ChartCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
