package com.etfmonitor.core.ui.component

import android.graphics.Color
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
import com.etfmonitor.oscillator.model.DemarkTDData
import com.etfmonitor.oscillator.model.ElderImpulseData
import com.etfmonitor.oscillator.model.ImpulseState
import com.etfmonitor.oscillator.model.OscillatorResult
import com.etfmonitor.oscillator.model.TrendSignalData
import com.etfmonitor.core.ui.theme.*
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.ScatterChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter

/**
 * Technical analysis chart components
 * - MacdChart: MACD 차트
 * - TrendSignalChart: 추세 시그널 차트
 * - ElderImpulseChart: Elder Impulse System 차트
 * - DemarkTDChart: DeMark TD Setup 차트
 */

private val logger = AppLogger.getLogger("TechnicalCharts")

/**
 * MACD 차트
 */
@Composable
fun MacdChart(
    result: OscillatorResult,
    latestDate: String? = null,
    modifier: Modifier = Modifier,
    chartColorViewModel: ChartColorViewModel = hiltViewModel()
) {
    // 차트 색상 설정 가져오기
    val chartColors by chartColorViewModel.chartColorSettings.collectAsState()
    val colorSettings = chartColors.macd

    // Modern theme colors
    val isDark = isSystemInDarkTheme()
    val macdColor = colorSettings.lineColor1      // MACD 라인 (기본값: Black)
    val signalColor = colorSettings.lineColor2    // Signal 라인
    val positiveColor = colorSettings.positiveColor   // Histogram 양수
    val negativeColor = colorSettings.negativeColor     // Histogram 음수
    val textColor = colorSettings.textColor       // 축 라벨/틱 색상 (기본값: Black)
    val legendColor = colorSettings.legendColor   // 범례 색상 (기본값: Black)
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
                        setGridColor(gridColor)
                        enableGridDashedLine(10f, 5f, 0f)
                        setTextColor(textColor)
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
                        setGridColor(gridColor)
                        enableGridDashedLine(10f, 5f, 0f)
                        setTextColor(textColor)
                        setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                    }
                    axisRight.isEnabled = false

                    legend.apply {
                        isEnabled = true
                        textSize = 12f
                        setTextColor(legendColor)
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
 * 추세 시그널 차트 (가격 + MA + 매수/매도 시그널 + Fear & Greed)
 *
 * 시그널 색상:
 * - 매수/보조매수: 빨간색 (한국 주식시장 관례)
 * - 매도/보조매도: 파란색
 *
 * 시그널 마커: 종가 라인 위에 표시
 */
@Composable
fun TrendSignalChart(
    data: TrendSignalData,
    latestDate: String? = null,
    modifier: Modifier = Modifier,
    chartColorViewModel: ChartColorViewModel = hiltViewModel()
) {
    // 데이터 검증
    if (data.dates.isEmpty() || data.close.isEmpty()) {
        logger.w("Empty data for TrendSignalChart")
        return
    }

    // 차트 색상 설정 가져오기
    val chartColors by chartColorViewModel.chartColorSettings.collectAsState()
    val colorSettings = chartColors.macd  // MACD 색상 재활용

    // Theme colors
    val isDark = isSystemInDarkTheme()
    val priceColor = colorSettings.lineColor1           // 종가
    val maColor = colorSettings.lineColor2              // MA

    // 매수: 빨간색, 매도: 파란색 (한국 주식시장 관례)
    val buyColor = Color.rgb(244, 67, 54)               // 매수 (빨간색)
    val auxBuyColor = Color.rgb(255, 138, 128)          // 보조매수 (연한 빨간색)
    val sellColor = Color.rgb(33, 150, 243)             // 매도 (파란색)
    val auxSellColor = Color.rgb(130, 177, 255)         // 보조매도 (연한 파란색)

    // Fear & Greed 라인 색상
    val fearGreedColor = Color.rgb(156, 39, 176)        // 보라색

    val textColor = colorSettings.textColor       // 축 라벨/틱 색상 (기본값: Black)
    val legendColor = colorSettings.legendColor   // 범례 색상 (기본값: Black)
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()

    ChartCard(
        title = "추세 시그널 (MA/CMF/Fear&Greed)",
        subtitle = latestDate?.let { "최신 데이터: $it (${data.interval})" },
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
                        CombinedChart.DrawOrder.LINE,
                        CombinedChart.DrawOrder.SCATTER
                    ))

                    // 마커 뷰
                    val markerView = CustomMarkerView(
                        context,
                        R.layout.marker_view,
                        data.dates
                    ) { value ->
                        String.format("%,.0f", value)
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

                    // 왼쪽 Y축 (가격)
                    axisLeft.apply {
                        setDrawGridLines(true)
                        gridLineWidth = 1f
                        setGridColor(gridColor)
                        enableGridDashedLine(10f, 5f, 0f)
                        setTextColor(textColor)
                        setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return when {
                                    value >= 1_000_000 -> String.format("%.1f만", value / 10_000f)
                                    value >= 10_000 -> String.format("%.0f", value)
                                    else -> String.format("%.0f", value)
                                }
                            }
                        }
                    }

                    // 오른쪽 Y축 (Fear & Greed: -1 ~ +1)
                    axisRight.apply {
                        isEnabled = true
                        setDrawGridLines(false)
                        setTextColor(textColor)
                        setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                        axisMinimum = -1.2f
                        axisMaximum = 1.2f
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return when {
                                    value > 0.6f -> "탐욕"
                                    value > 0.2f -> "+"
                                    value > -0.2f -> "중립"
                                    value > -0.6f -> "-"
                                    else -> "공포"
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
                    val lineDataSets = mutableListOf<LineDataSet>()

                    // 1. 종가 라인
                    val closeEntries = data.close.mapIndexed { index, value ->
                        Entry(index.toFloat(), value.toFloat())
                    }
                    val closeDataSet = LineDataSet(closeEntries, "종가").apply {
                        axisDependency = YAxis.AxisDependency.LEFT
                        color = priceColor
                        lineWidth = 2.5f
                        setDrawCircles(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        highLightColor = priceColor
                    }
                    lineDataSets.add(closeDataSet)

                    // 2. MA 라인
                    val maEntries = data.ma.mapIndexed { index, value ->
                        Entry(index.toFloat(), value.toFloat())
                    }
                    val maDataSet = LineDataSet(maEntries, "MA").apply {
                        axisDependency = YAxis.AxisDependency.LEFT
                        color = maColor
                        lineWidth = 2f
                        setDrawCircles(false)
                        setDrawValues(false)
                        enableDashedLine(10f, 5f, 0f)
                        highLightColor = maColor
                    }
                    lineDataSets.add(maDataSet)

                    // 3. Fear & Greed 라인 차트 (오른쪽 Y축)
                    val fearGreedEntries = data.fearGreed.mapIndexed { index, value ->
                        Entry(index.toFloat(), value.toFloat())
                    }
                    val fearGreedDataSet = LineDataSet(fearGreedEntries, "F&G").apply {
                        axisDependency = YAxis.AxisDependency.RIGHT
                        color = fearGreedColor
                        lineWidth = 1.5f
                        setDrawCircles(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        highLightColor = fearGreedColor
                    }
                    lineDataSets.add(fearGreedDataSet)

                    val lineData = LineData(lineDataSets.toList())

                    // 4. 매수/매도 시그널 (Scatter) - 종가 라인 위에 표시
                    val buyEntries = mutableListOf<Entry>()
                    val auxBuyEntries = mutableListOf<Entry>()
                    val sellEntries = mutableListOf<Entry>()
                    val auxSellEntries = mutableListOf<Entry>()

                    // 매수 시그널 (종가에 표시)
                    data.buySignal.forEachIndexed { index, signal ->
                        if (signal == 1) {
                            buyEntries.add(Entry(index.toFloat(), data.close[index].toFloat()))
                        }
                    }

                    // 보조매수 시그널 (종가에 표시)
                    data.auxBuySignal.forEachIndexed { index, signal ->
                        if (signal == 1) {
                            auxBuyEntries.add(Entry(index.toFloat(), data.close[index].toFloat()))
                        }
                    }

                    // 매도 시그널 (종가에 표시)
                    data.sellSignal.forEachIndexed { index, signal ->
                        if (signal == 1) {
                            sellEntries.add(Entry(index.toFloat(), data.close[index].toFloat()))
                        }
                    }

                    // 보조매도 시그널 (종가에 표시)
                    data.auxSellSignal.forEachIndexed { index, signal ->
                        if (signal == 1) {
                            auxSellEntries.add(Entry(index.toFloat(), data.close[index].toFloat()))
                        }
                    }

                    val scatterDataSets = mutableListOf<ScatterDataSet>()

                    // 매수 (빨간색, 큰 삼각형 마커)
                    if (buyEntries.isNotEmpty()) {
                        val buyDataSet = ScatterDataSet(buyEntries, "매수").apply {
                            axisDependency = YAxis.AxisDependency.LEFT
                            color = buyColor
                            setScatterShape(ScatterChart.ScatterShape.TRIANGLE)
                            scatterShapeSize = 24f
                            setDrawValues(false)
                        }
                        scatterDataSets.add(buyDataSet)
                    }

                    // 보조매수 (연한 빨간색, 작은 삼각형 마커)
                    if (auxBuyEntries.isNotEmpty()) {
                        val auxBuyDataSet = ScatterDataSet(auxBuyEntries, "보조매수").apply {
                            axisDependency = YAxis.AxisDependency.LEFT
                            color = auxBuyColor
                            setScatterShape(ScatterChart.ScatterShape.TRIANGLE)
                            scatterShapeSize = 18f
                            setDrawValues(false)
                        }
                        scatterDataSets.add(auxBuyDataSet)
                    }

                    // 매도 (파란색, 큰 역삼각형 마커)
                    if (sellEntries.isNotEmpty()) {
                        val sellDataSet = ScatterDataSet(sellEntries, "매도").apply {
                            axisDependency = YAxis.AxisDependency.LEFT
                            color = sellColor
                            shapeRenderer = InvertedTriangleShapeRenderer()
                            scatterShapeSize = 24f
                            setDrawValues(false)
                        }
                        scatterDataSets.add(sellDataSet)
                    }

                    // 보조매도 (연한 파란색, 작은 역삼각형 마커)
                    if (auxSellEntries.isNotEmpty()) {
                        val auxSellDataSet = ScatterDataSet(auxSellEntries, "보조매도").apply {
                            axisDependency = YAxis.AxisDependency.LEFT
                            color = auxSellColor
                            shapeRenderer = InvertedTriangleShapeRenderer()
                            scatterShapeSize = 18f
                            setDrawValues(false)
                        }
                        scatterDataSets.add(auxSellDataSet)
                    }

                    // CombinedData 조립
                    val combinedData = CombinedData().apply {
                        setData(lineData)
                        if (scatterDataSets.isNotEmpty()) {
                            setData(ScatterData(scatterDataSets.toList()))
                        }
                    }

                    chart.data = combinedData
                    chart.invalidate()
                } catch (e: Exception) {
                    logger.e("Error updating TrendSignalChart", e)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
        )
    }
}

/**
 * 시가총액 + Elder Impulse System 복합 차트 (주봉)
 */
@Composable
fun ElderImpulseChart(
    data: ElderImpulseData,
    modifier: Modifier = Modifier,
    chartColorViewModel: ChartColorViewModel = hiltViewModel()
) {
    if (data.dates.isEmpty()) {
        logger.w("Empty data for ElderImpulseChart")
        return
    }

    val chartColors by chartColorViewModel.chartColorSettings.collectAsState()
    val colorSettings = chartColors.marketCapOscillator

    val isDark = isSystemInDarkTheme()
    val marketCapColor = colorSettings.lineColor1  // 시가총액 (기본값: Black)
    val emaColor = colorSettings.lineColor2
    val textColor = colorSettings.textColor        // 축 라벨/틱 색상 (기본값: Black)
    val legendColor = colorSettings.legendColor    // 범례 색상 (기본값: Black)
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()
    val bullColor = ChartGreen.toArgb()
    val bearColor = ChartRed.toArgb()
    val neutralColor = ChartTextLight.toArgb()

    // 현재 Impulse 상태
    val currentImpulse = data.impulse.lastOrNull()?.let { ImpulseState.fromValue(it) } ?: ImpulseState.NEUTRAL

    ChartCard(
        title = "Elder Impulse System (주봉)",
        subtitle = "현재 상태: ${currentImpulse.displayName}",
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
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val idx = value.toInt()
                                return if (idx in data.dates.indices) {
                                    val date = data.dates[idx]
                                    if (date.length >= 7) date.substring(5) else date
                                } else ""
                            }
                        }
                    }

                    // 왼쪽 Y축: 시가총액
                    axisLeft.apply {
                        setTextColor(textColor)
                        setDrawGridLines(true)
                        gridLineWidth = 0.5f
                        setGridColor(gridColor)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return if (value >= 1_000_000_000_000) {
                                    String.format("%.1f조", value / 1_000_000_000_000)
                                } else {
                                    String.format("%.0f억", value / 100_000_000)
                                }
                            }
                        }
                    }

                    // 오른쪽 Y축: 종가/EMA
                    axisRight.apply {
                        isEnabled = true
                        setTextColor(textColor)
                        setDrawGridLines(false)
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

                    // 시가총액 라인
                    val marketCapEntries = data.marketCap.mapIndexed { index, value ->
                        Entry(index.toFloat(), value.toFloat())
                    }
                    val marketCapDataSet = LineDataSet(marketCapEntries, "시가총액").apply {
                        axisDependency = YAxis.AxisDependency.LEFT
                        color = marketCapColor
                        lineWidth = 2f
                        setDrawCircles(false)
                        setDrawValues(false)
                    }
                    lineDataSets.add(marketCapDataSet)

                    // EMA13 라인 - Grey dashed line
                    val emaEntries = data.ema.mapIndexed { index, value ->
                        Entry(index.toFloat(), value.toFloat())
                    }
                    val emaDataSet = LineDataSet(emaEntries, "EMA13").apply {
                        axisDependency = YAxis.AxisDependency.RIGHT
                        color = Color.GRAY
                        lineWidth = 1.5f
                        enableDashedLine(10f, 5f, 0f)  // lineLength, spaceLength, phase
                        setDrawCircles(false)
                        setDrawValues(false)
                    }
                    lineDataSets.add(emaDataSet)

                    val lineData = LineData(lineDataSets.toList())

                    // Impulse 상태를 Circle Scatter로 표시 (Bullish, Bearish, Neutral 모두)
                    val bullEntries = mutableListOf<Entry>()
                    val bearEntries = mutableListOf<Entry>()
                    val neutralEntries = mutableListOf<Entry>()

                    data.impulse.forEachIndexed { index, value ->
                        when (value) {
                            1 -> bullEntries.add(Entry(index.toFloat(), data.close[index].toFloat()))
                            -1 -> bearEntries.add(Entry(index.toFloat(), data.close[index].toFloat()))
                            else -> neutralEntries.add(Entry(index.toFloat(), data.close[index].toFloat()))
                        }
                    }

                    val scatterDataSets = mutableListOf<ScatterDataSet>()

                    if (bullEntries.isNotEmpty()) {
                        val bullDataSet = ScatterDataSet(bullEntries, "Bullish").apply {
                            axisDependency = YAxis.AxisDependency.RIGHT
                            color = bullColor
                            setScatterShape(ScatterChart.ScatterShape.CIRCLE)
                            scatterShapeSize = 12f
                            setDrawValues(false)
                        }
                        scatterDataSets.add(bullDataSet)
                    }

                    if (bearEntries.isNotEmpty()) {
                        val bearDataSet = ScatterDataSet(bearEntries, "Bearish").apply {
                            axisDependency = YAxis.AxisDependency.RIGHT
                            color = bearColor
                            setScatterShape(ScatterChart.ScatterShape.CIRCLE)
                            scatterShapeSize = 12f
                            setDrawValues(false)
                        }
                        scatterDataSets.add(bearDataSet)
                    }

                    if (neutralEntries.isNotEmpty()) {
                        val neutralDataSet = ScatterDataSet(neutralEntries, "Neutral").apply {
                            axisDependency = YAxis.AxisDependency.RIGHT
                            color = neutralColor
                            setScatterShape(ScatterChart.ScatterShape.CIRCLE)
                            scatterShapeSize = 12f
                            setDrawValues(false)
                        }
                        scatterDataSets.add(neutralDataSet)
                    }

                    val combinedData = CombinedData().apply {
                        setData(lineData)
                        if (scatterDataSets.isNotEmpty()) {
                            setData(ScatterData(scatterDataSets.toList()))
                        }
                    }

                    chart.data = combinedData
                    chart.invalidate()
                } catch (e: Exception) {
                    logger.e("Error updating ElderImpulseChart", e)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        )
    }
}

/**
 * 시가총액 + DeMark TD Setup 복합 차트
 */
@Composable
fun DemarkTDChart(
    data: DemarkTDData,
    modifier: Modifier = Modifier,
    chartColorViewModel: ChartColorViewModel = hiltViewModel()
) {
    if (data.dates.isEmpty()) {
        logger.w("Empty data for DemarkTDChart")
        return
    }

    val chartColors by chartColorViewModel.chartColorSettings.collectAsState()
    val colorSettings = chartColors.marketCapOscillator

    val isDark = isSystemInDarkTheme()
    val marketCapColor = colorSettings.lineColor1   // 시가총액 (기본값: Black)
    val closeColor = colorSettings.lineColor2
    val textColor = colorSettings.textColor         // 축 라벨/틱 색상 (기본값: Black)
    val legendColor = colorSettings.legendColor     // 범례 색상 (기본값: Black)
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()
    val sellFatigueColor = ChartRed.toArgb()
    val buyFatigueColor = ChartGreen.toArgb()

    // 현재 TD 상태
    val currentTdSell = data.tdSell.lastOrNull() ?: 0
    val currentTdBuy = data.tdBuy.lastOrNull() ?: 0
    val statusText = when {
        currentTdSell >= 9 -> "매도 피로 ($currentTdSell) - 하락 전환 가능"
        currentTdBuy >= 9 -> "매수 피로 ($currentTdBuy) - 상승 전환 가능"
        currentTdSell > 0 -> "상승 지속 ($currentTdSell)"
        currentTdBuy > 0 -> "하락 지속 ($currentTdBuy)"
        else -> "중립"
    }

    ChartCard(
        title = "DeMark TD Setup (${data.intervalName})",
        subtitle = "현재 상태: $statusText",
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
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val idx = value.toInt()
                                return if (idx in data.dates.indices) {
                                    val date = data.dates[idx]
                                    if (date.length >= 7) date.substring(5) else date
                                } else ""
                            }
                        }
                    }

                    // 왼쪽 Y축: 시가총액
                    axisLeft.apply {
                        setTextColor(textColor)
                        setDrawGridLines(true)
                        gridLineWidth = 0.5f
                        setGridColor(gridColor)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return if (value >= 1_000_000_000_000) {
                                    String.format("%.1f조", value / 1_000_000_000_000)
                                } else {
                                    String.format("%.0f억", value / 100_000_000)
                                }
                            }
                        }
                    }

                    // 오른쪽 Y축: TD 카운트
                    axisRight.apply {
                        isEnabled = true
                        setTextColor(textColor)
                        setDrawGridLines(false)
                        axisMinimum = -15f
                        axisMaximum = 15f
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

                    // 시가총액 라인
                    val marketCapEntries = data.marketCap.mapIndexed { index, value ->
                        Entry(index.toFloat(), value.toFloat())
                    }
                    val marketCapDataSet = LineDataSet(marketCapEntries, "시가총액").apply {
                        axisDependency = YAxis.AxisDependency.LEFT
                        color = marketCapColor
                        lineWidth = 2f
                        setDrawCircles(false)
                        setDrawValues(false)
                    }
                    lineDataSets.add(marketCapDataSet)

                    // TD Sell 라인 (양수: 상승 피로)
                    val tdSellEntries = data.tdSell.mapIndexed { index, value ->
                        Entry(index.toFloat(), value.toFloat())
                    }
                    val tdSellDataSet = LineDataSet(tdSellEntries, "매도피로").apply {
                        axisDependency = YAxis.AxisDependency.RIGHT
                        color = sellFatigueColor
                        lineWidth = 1.5f
                        setDrawCircles(false)
                        setDrawValues(false)
                        setDrawFilled(true)
                        fillColor = sellFatigueColor
                        fillAlpha = 50
                    }
                    lineDataSets.add(tdSellDataSet)

                    // TD Buy 라인 (음수로 표시: 하락 피로)
                    val tdBuyEntries = data.tdBuy.mapIndexed { index, value ->
                        Entry(index.toFloat(), -value.toFloat())
                    }
                    val tdBuyDataSet = LineDataSet(tdBuyEntries, "매수피로").apply {
                        axisDependency = YAxis.AxisDependency.RIGHT
                        color = buyFatigueColor
                        lineWidth = 1.5f
                        setDrawCircles(false)
                        setDrawValues(false)
                        setDrawFilled(true)
                        fillColor = buyFatigueColor
                        fillAlpha = 50
                    }
                    lineDataSets.add(tdBuyDataSet)

                    val lineData = LineData(lineDataSets.toList())

                    // 마커 제거 - 라인 차트만 표시
                    val combinedData = CombinedData().apply {
                        setData(lineData)
                    }

                    chart.data = combinedData
                    chart.invalidate()
                } catch (e: Exception) {
                    logger.e("Error updating DemarkTDChart", e)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        )
    }
}
