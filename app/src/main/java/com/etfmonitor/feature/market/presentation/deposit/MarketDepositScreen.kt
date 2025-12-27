package com.etfmonitor.feature.market.presentation.deposit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.core.ui.component.DateRangeSelector
import com.etfmonitor.core.ui.component.SingleChartColorSettings
import com.etfmonitor.core.ui.theme.*
import com.etfmonitor.feature.market.domain.model.MarketDepositData
import com.etfmonitor.feature.settings.presentation.SettingsViewModel
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter

/**
 * Market Deposit Screen - Fear & Greed 스타일로 재설계
 */
@Composable
fun MarketDepositScreen(
    onNavigateBack: () -> Unit,
    viewModel: MarketDepositViewModel = hiltViewModel()
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Custom Header (FearGreedScreen 스타일)
            MarketDepositHeader(onNavigateBack = onNavigateBack)

            // Content
            MarketDepositContent(viewModel = viewModel)
        }
    }
}

/**
 * Reusable Market Deposit content without header
 * Used in standalone screen and hub screen
 */
@Composable
fun MarketDepositContent(
    viewModel: MarketDepositViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()
    val depositData by viewModel.depositData.collectAsState()

    // Get chart colors from settings
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val chartColorSettings by settingsViewModel.chartColorSettings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // State Display
            when (val currentState = state) {
                is MarketDepositState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is MarketDepositState.Success -> {
                    SuccessCard(message = currentState.message)
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(3000)
                        viewModel.clearMessage()
                    }
                }
                is MarketDepositState.Error -> {
                    ErrorInfoCard(message = currentState.message)
                }
                is MarketDepositState.Idle -> {
                    // Auto-load, no action needed
                }
            }

            // Main Content (if data available)
            if (depositData.dates.isNotEmpty()) {
                val lastIdx = depositData.dates.size - 1

                // Summary Section (게이지 대신 요약 정보)
                DepositSummarySection(
                    depositAmount = depositData.depositAmounts[lastIdx],
                    depositChange = depositData.depositChanges[lastIdx],
                    creditAmount = depositData.creditAmounts[lastIdx],
                    creditChange = depositData.creditChanges[lastIdx]
                )

                // Stats Row
                StatsRow(data = depositData)

                // Date Range Selector
                DateRangeSelector(
                    selectedRange = selectedRange,
                    onRangeSelected = { viewModel.updateDateRange(it) }
                )

                // Chart
                ChartSection(
                    data = depositData,
                    chartColors = chartColorSettings.marketDeposit
                )

                // Disclaimer
                DisclaimerText()
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun MarketDepositHeader(onNavigateBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.nav_back),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = stringResource(R.string.market_deposit_title),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DepositSummarySection(
    depositAmount: Double,
    depositChange: Double,
    creditAmount: Double,
    creditChange: Double
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 고객예탁금 메인 표시
        Text(
            text = String.format("%.0f", depositAmount / 10000),
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Black
            ),
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "조원",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 변화량 칩
        val depositChangeColor = if (depositChange > 0)
            MaterialTheme.extendedColors.chartGreen
        else
            MaterialTheme.extendedColors.chartRed

        Surface(
            shape = RoundedCornerShape(50),
            color = depositChangeColor.copy(alpha = 0.1f)
        ) {
            Text(
                text = String.format("전일 대비 %+.0f억원", depositChange),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = depositChangeColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // 신용잔고
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "신용잔고:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format("%.0f억원", creditAmount),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            val creditChangeColor = if (creditChange > 0)
                MaterialTheme.extendedColors.chartGreen
            else
                MaterialTheme.extendedColors.chartRed

            Text(
                text = String.format("(%+.0f)", creditChange),
                style = MaterialTheme.typography.bodyMedium,
                color = creditChangeColor
            )
        }
    }
}

@Composable
private fun StatsRow(data: MarketDepositData) {
    val lastIdx = data.dates.size - 1
    val yesterday = if (lastIdx >= 1) data.depositAmounts[lastIdx - 1] else null
    val weekAgo = if (lastIdx >= 5) data.depositAmounts[lastIdx - 5] else null
    val monthAgo = if (lastIdx >= 20) data.depositAmounts[lastIdx - 20] else null

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatBox(
            label = "어제",
            value = yesterday?.let { String.format("%.0f", it / 10000) } ?: "—",
            unit = "조",
            modifier = Modifier.weight(1f)
        )
        StatBox(
            label = "1주일 전",
            value = weekAgo?.let { String.format("%.0f", it / 10000) } ?: "—",
            unit = "조",
            modifier = Modifier.weight(1f)
        )
        StatBox(
            label = "1달 전",
            value = monthAgo?.let { String.format("%.0f", it / 10000) } ?: "—",
            unit = "조",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (value != "—") {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartSection(
    data: MarketDepositData,
    chartColors: SingleChartColorSettings
) {
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
                text = stringResource(R.string.market_deposit_chart_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            MarketDepositChartView(
                data = data,
                chartColors = chartColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
        }
    }
}

@Composable
private fun MarketDepositChartView(
    data: MarketDepositData,
    chartColors: SingleChartColorSettings,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val depositColor = chartColors.lineColor1
    val creditColor = chartColors.lineColor2
    val textColor = chartColors.textColor
    val legendColor = chartColors.legendColor
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()

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
                    CombinedChart.DrawOrder.LINE
                ))

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
                            return if (index >= 0 && index < data.dates.size) {
                                data.dates[index]
                            } else {
                                ""
                            }
                        }
                    }
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    gridLineWidth = 1f
                    setGridColor(gridColor)
                    enableGridDashedLine(10f, 5f, 0f)
                    setTextColor(depositColor)
                    setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return String.format("%.0f조", value / 10000)
                        }
                    }
                }

                axisRight.apply {
                    isEnabled = true
                    setDrawGridLines(false)
                    setTextColor(creditColor)
                    setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return String.format("%.0f조", value / 10000)
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
        modifier = modifier
    )
}

@Composable
private fun DisclaimerText() {
    Text(
        text = "* 증시 자금 동향은 시장 유동성 및 투자심리를 파악하는\n보조 지표로 활용하는 것이 좋습니다.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 8.dp),
        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.5f
    )
}

@Composable
private fun SuccessCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.extendedColors.successContainer
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extendedColors.onSuccessContainer,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun ErrorInfoCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp)
        )
    }
}
