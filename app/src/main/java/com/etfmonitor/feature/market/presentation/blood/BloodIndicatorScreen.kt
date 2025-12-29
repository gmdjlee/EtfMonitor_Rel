package com.etfmonitor.feature.market.presentation.blood

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.core.ui.component.*
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.core.ui.theme.*
import com.etfmonitor.feature.market.domain.model.BloodIndicator
import com.etfmonitor.feature.market.domain.model.BloodSignalType
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter

/**
 * Blood Indicator Screen - US Market Health Monitor
 *
 * BLOOD = IRX (3M T-Bill) / (HYG Yield - 10Y Treasury)
 * - Rising BLOOD = Risk On (Market healthy)
 * - Falling BLOOD = Risk Off (Market stress)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloodIndicatorScreen(
    onNavigateBack: () -> Unit,
    viewModel: BloodIndicatorViewModel = hiltViewModel()
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            BloodIndicatorHeader(onNavigateBack = onNavigateBack)
            BloodIndicatorContent(viewModel = viewModel)
        }
    }
}

/**
 * Reusable Blood Indicator content without header
 * Used in standalone screen and hub screen
 */
@Composable
fun BloodIndicatorContent(
    viewModel: BloodIndicatorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()
    val bloodData by viewModel.bloodData.collectAsState()
    val showFirstRunDialog by viewModel.showFirstRunDialog.collectAsState()
    var showManualDialog by remember { mutableStateOf(false) }

    // Get chart colors from settings
    val settingsViewModel: com.etfmonitor.feature.settings.presentation.SettingsViewModel = hiltViewModel()
    val chartColorSettings by settingsViewModel.chartColorSettings.collectAsState()

    // First run dialog
    if (showFirstRunDialog) {
        BloodInitializeDialog(
            onDismiss = { viewModel.onFirstRunDialogShown() },
            onConfirm = { days ->
                viewModel.onFirstRunDialogConfirmed()
                viewModel.initialize(days)
            }
        )
    }

    // Manual dialog
    if (showManualDialog) {
        BloodInitializeDialog(
            onDismiss = { showManualDialog = false },
            onConfirm = { days ->
                showManualDialog = false
                viewModel.initialize(days)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // State Display
            when (val currentState = state) {
                is BloodIndicatorState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is BloodIndicatorState.Initializing -> {
                    InitializingCard(
                        message = currentState.message,
                        progress = currentState.progress
                    )
                }
                is BloodIndicatorState.Updating -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(currentState.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is BloodIndicatorState.Success -> {
                    SuccessInfoCard(message = currentState.message)
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(3000)
                        viewModel.clearMessage()
                    }
                }
                is BloodIndicatorState.Error -> {
                    ErrorInfoCard(message = currentState.message)
                }
                is BloodIndicatorState.Idle -> {
                    if (!currentState.hasData) {
                        NoDataCard(onCollectClick = { showManualDialog = true })
                    }
                }
            }

            // Main Content
            if (bloodData.isNotEmpty()) {
                val latest = bloodData.firstOrNull()
                if (latest != null) {
                    // Current Value Display
                    BloodValueSection(latest = latest)

                    // Components Breakdown
                    ComponentsCard(latest = latest)

                    // Date Range Selector (Blood Indicator specific options)
                    DateRangeSelector(
                        selectedRange = selectedRange,
                        onRangeSelected = { viewModel.updateDateRange(it) },
                        availableOptions = listOf(
                            DateRangeOption.SIX_MONTHS,
                            DateRangeOption.YEAR,
                            DateRangeOption.THREE_YEARS,
                            DateRangeOption.FIVE_YEARS,
                            DateRangeOption.SEVEN_YEARS,
                            DateRangeOption.ALL
                        )
                    )

                    // Dual-Axis Chart (BLOOD + SPY)
                    BloodChartSection(
                        data = bloodData,
                        chartColors = chartColorSettings.bloodIndicator
                    )

                    // Explanation
                    ExplanationCard()
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BloodIndicatorHeader(onNavigateBack: () -> Unit) {
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
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = "Blood Indicator",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BloodValueSection(latest: BloodIndicator) {
    val (statusText, statusColor, icon) = when (latest.signalType) {
        BloodSignalType.RISK_ON -> Triple(
            "Risk On - 상승 추세",
            MaterialTheme.extendedColors.chartGreen,
            Icons.Default.TrendingUp
        )
        BloodSignalType.RISK_OFF -> Triple(
            "Risk Off - 하락 추세",
            MaterialTheme.extendedColors.chartRed,
            Icons.Default.TrendingDown
        )
        BloodSignalType.NEUTRAL -> Triple(
            "Neutral - 중립",
            MaterialTheme.colorScheme.onSurface,
            Icons.Default.TrendingFlat
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Blood Value
        Text(
            text = String.format("%.4f", latest.bloodValue),
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black),
            color = statusColor
        )

        // Status Chip
        Surface(
            shape = RoundedCornerShape(50),
            color = statusColor.copy(alpha = 0.1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = statusColor
                )
            }
        }

        // Date
        Text(
            text = "기준일: ${latest.date}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun ComponentsCard(latest: BloodIndicator) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "구성 요소",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            HorizontalDivider()

            ComponentRow("IRX (3M T-Bill)", "${String.format("%.2f", latest.irx)}%")
            ComponentRow("HYG Yield", "${String.format("%.2f", latest.hygYield)}%")
            ComponentRow("10Y Treasury", "${String.format("%.2f", latest.tenYearYield)}%")
            ComponentRow(
                "Spread (HYG - 10Y)",
                "${String.format("%.2f", latest.spreadValue)}%",
                if (latest.spreadValue > 0) MaterialTheme.extendedColors.chartGreen
                else MaterialTheme.extendedColors.chartRed
            )

            latest.spyClose?.let { spy ->
                HorizontalDivider()
                ComponentRow("S&P 500", String.format("$%.2f", spy))
            }
        }
    }
}

@Composable
private fun ComponentRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = valueColor
        )
    }
}

@Composable
private fun BloodChartSection(
    data: List<BloodIndicator>,
    chartColors: SingleChartColorSettings
) {
    // 5년(약 1260 영업일) 이상이면 장기 기간으로 판단
    val isLongPeriod = data.size > 1000

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "BLOOD vs S&P 500 (with MAs)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            BloodDualAxisChart(
                data = data,
                chartColors = chartColors,
                isLongPeriod = isLongPeriod,
                modifier = Modifier.fillMaxWidth().height(300.dp)
            )

            // Moving Average Legend
            BloodChartLegend()
        }
    }
}

@Composable
private fun BloodChartLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        LegendItem(color = Color(0xFFE53935), label = "BLOOD")
        LegendItem(color = Color.Black, label = "S&P 500")
        LegendItem(color = Color(0xFF2196F3), label = "20MA", isDashed = true)
        LegendItem(color = Color(0xFFFFA726), label = "60MA", isDashed = true)
        LegendItem(color = Color(0xFF4CAF50), label = "120MA", isDashed = true)
    }
}

@Composable
private fun LegendItem(color: Color, label: String, isDashed: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isDashed) {
            // Dashed line indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.width(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 5.dp, height = 3.dp)
                        .background(color, RoundedCornerShape(1.dp))
                )
                Box(
                    modifier = Modifier
                        .size(width = 5.dp, height = 3.dp)
                        .background(color, RoundedCornerShape(1.dp))
                )
            }
        } else {
            // Solid line indicator
            Box(
                modifier = Modifier
                    .size(width = 16.dp, height = 3.dp)
                    .background(color, RoundedCornerShape(1.dp))
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun BloodDualAxisChart(
    data: List<BloodIndicator>,
    chartColors: SingleChartColorSettings,
    isLongPeriod: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val bloodColor = chartColors.lineColor1
    val spyColor = chartColors.lineColor2
    val textColor = chartColors.textColor
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()

    // Moving average colors
    val ma20Color = Color(0xFF2196F3).toArgb()  // Blue
    val ma60Color = Color(0xFFFFA726).toArgb()  // Orange
    val ma120Color = Color(0xFF4CAF50).toArgb() // Green

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
                    enableGridDashedLine(10f, 5f, 0f)
                    setTextColor(textColor)
                    granularity = 1f
                    labelRotationAngle = -45f
                    setLabelCount(8, false)
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    gridLineWidth = 1f
                    setGridColor(gridColor)
                    enableGridDashedLine(10f, 5f, 0f)
                    setTextColor(bloodColor)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return String.format("%.2f", value)
                        }
                    }
                }

                axisRight.apply {
                    isEnabled = true
                    setDrawGridLines(false)
                    setTextColor(spyColor)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return String.format("%.0f", value)
                        }
                    }
                }

                legend.apply {
                    isEnabled = false  // Using custom legend
                }
            }
        },
        update = { chart ->
            // Data is already sorted ASC (oldest first) from DAO - no need to reverse
            val chartData = data
            val bloodValues = chartData.map { it.bloodValue.toFloat() }

            // Calculate moving averages
            val ma20 = calculateMovingAverage(bloodValues, 20)
            val ma60 = calculateMovingAverage(bloodValues, 60)
            val ma120 = calculateMovingAverage(bloodValues, 120)

            // Blood line
            val bloodEntries = chartData.mapIndexed { index, item ->
                Entry(index.toFloat(), item.bloodValue.toFloat())
            }
            val bloodDataSet = LineDataSet(bloodEntries, "BLOOD").apply {
                axisDependency = YAxis.AxisDependency.LEFT
                color = bloodColor
                lineWidth = 2.5f
                setDrawCircles(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }

            // 20MA line - Dashed line
            val ma20Entries = ma20.mapIndexedNotNull { index, value ->
                value?.let { Entry(index.toFloat(), it) }
            }
            val ma20DataSet = if (ma20Entries.isNotEmpty()) {
                LineDataSet(ma20Entries, "20MA").apply {
                    axisDependency = YAxis.AxisDependency.LEFT
                    color = ma20Color
                    lineWidth = 1.5f
                    setDrawCircles(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    enableDashedLine(10f, 5f, 0f)
                }
            } else null

            // 60MA line - Dashed line
            val ma60Entries = ma60.mapIndexedNotNull { index, value ->
                value?.let { Entry(index.toFloat(), it) }
            }
            val ma60DataSet = if (ma60Entries.isNotEmpty()) {
                LineDataSet(ma60Entries, "60MA").apply {
                    axisDependency = YAxis.AxisDependency.LEFT
                    color = ma60Color
                    lineWidth = 1.5f
                    setDrawCircles(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    enableDashedLine(10f, 5f, 0f)
                }
            } else null

            // 120MA line - Dashed line
            val ma120Entries = ma120.mapIndexedNotNull { index, value ->
                value?.let { Entry(index.toFloat(), it) }
            }
            val ma120DataSet = if (ma120Entries.isNotEmpty()) {
                LineDataSet(ma120Entries, "120MA").apply {
                    axisDependency = YAxis.AxisDependency.LEFT
                    color = ma120Color
                    lineWidth = 1.5f
                    setDrawCircles(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    enableDashedLine(10f, 5f, 0f)
                }
            } else null

            // SPY line (if available) - Black solid line
            val spyEntries = chartData.mapIndexedNotNull { index, item ->
                item.spyClose?.let { Entry(index.toFloat(), it.toFloat()) }
            }
            val spyDataSet = if (spyEntries.isNotEmpty()) {
                LineDataSet(spyEntries, "S&P 500").apply {
                    axisDependency = YAxis.AxisDependency.RIGHT
                    color = Color.Black.toArgb()
                    lineWidth = 2f
                    setDrawCircles(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    // Solid line (no dashing)
                }
            } else null

            val dataSets = mutableListOf<LineDataSet>()
            dataSets.add(bloodDataSet)
            ma20DataSet?.let { dataSets.add(it) }
            ma60DataSet?.let { dataSets.add(it) }
            ma120DataSet?.let { dataSets.add(it) }
            spyDataSet?.let { dataSets.add(it) }

            val lineData = LineData(dataSets as List<LineDataSet>)
            val combinedData = CombinedData().apply { setData(lineData) }

            chart.xAxis.valueFormatter = object : ValueFormatter() {
                private var lastYear = ""

                override fun getFormattedValue(value: Float): String {
                    val index = value.toInt()
                    if (index < 0 || index >= chartData.size) return ""

                    val dateStr = chartData[index].date // "YYYY-MM-DD"
                    val year = dateStr.substring(0, 4)
                    val monthDay = dateStr.substring(5) // "MM-DD"

                    return if (isLongPeriod) {
                        // 5년 이상: 연도만 표시 (연도가 바뀔 때)
                        if (year != lastYear) {
                            lastYear = year
                            year
                        } else {
                            ""
                        }
                    } else {
                        // 5년 미만: YY-MM 또는 연도 변경시 연도 표시
                        val shortYear = dateStr.substring(2, 4) // "YY"
                        val month = dateStr.substring(5, 7) // "MM"
                        if (year != lastYear) {
                            lastYear = year
                            "'$shortYear.$month"
                        } else {
                            monthDay.replace("-", ".")
                        }
                    }
                }
            }

            chart.data = combinedData
            chart.invalidate()
        },
        modifier = modifier
    )
}

/**
 * Calculate Simple Moving Average for a list of values
 * Returns null for indices where MA cannot be calculated (not enough data)
 */
private fun calculateMovingAverage(values: List<Float>, period: Int): List<Float?> {
    if (values.isEmpty()) return emptyList()

    return values.mapIndexed { index, _ ->
        if (index < period - 1) {
            null
        } else {
            val sum = (0 until period).sumOf { values[index - it].toDouble() }
            (sum / period).toFloat()
        }
    }
}

@Composable
private fun ExplanationCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Blood Indicator 해석",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = """
                    • BLOOD = IRX / (HYG Yield - 10Y Treasury)
                    • 상승 추세 (Risk On): 시장이 건강하고 위험 자산 선호
                    • 하락 추세 (Risk Off): 시장 스트레스, 안전 자산 선호
                    • US 국채 금리와 하이일드 채권 스프레드로 글로벌 위험 선호도 측정
                """.trimIndent(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun InitializingCard(message: String, progress: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(progress = { progress / 100f })
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Text(
                "$progress%",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun SuccessInfoCard(message: String) {
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

@Composable
private fun NoDataCard(onCollectClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Blood Indicator 데이터가 없습니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onCollectClick, shape = RoundedCornerShape(50)) {
                Text("데이터 수집")
            }
        }
    }
}

@Composable
private fun BloodInitializeDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val options = listOf(
        BloodPeriodOption(365, "1년", "약 365일"),
        BloodPeriodOption(1095, "3년", "약 1,095일"),
        BloodPeriodOption(1825, "5년 (권장)", "약 1,825일"),
        BloodPeriodOption(2555, "7년", "약 2,555일"),
        BloodPeriodOption(3650, "10년", "약 3,650일")
    )
    var selectedDays by remember { mutableStateOf(1825) }

    AlertDialog(
        onDismissRequest = { },
        title = { Text("Blood Indicator 데이터 수집") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "US 국채 및 HYG 데이터를 수집하여 시장 건강도를 분석합니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                options.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedDays == option.days,
                            onClick = { selectedDays = option.days }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "데이터 수집에 약 1분 정도 소요됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDays) }) { Text("수집 시작") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("나중에") }
        }
    )
}

private data class BloodPeriodOption(
    val days: Int,
    val label: String,
    val description: String
)
