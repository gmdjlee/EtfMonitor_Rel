package com.etfmonitor.feature.market.presentation.feargreed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.isSystemInDarkTheme
import com.etfmonitor.R
import com.etfmonitor.core.ui.component.*
import com.etfmonitor.core.ui.theme.*
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.formatter.ValueFormatter

/**
 * Fear & Greed Screen - Moss Green Nature Theme
 * Modern detail screen design matching the React design guide
 *
 * Layout:
 * - Back arrow header with title
 * - Gauge visual (semi-circle)
 * - Stats row (yesterday, 1 week ago, 1 month ago)
 * - Chart with bars
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FearGreedScreen(
    onNavigateBack: () -> Unit,
    viewModel: FearGreedViewModel = hiltViewModel()
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Custom Header
            FearGreedHeader(onNavigateBack = onNavigateBack)

            // Content
            FearGreedContent(viewModel = viewModel)
        }
    }
}

/**
 * Reusable Fear & Greed content without header
 * Used in standalone screen and hub screen
 */
@Composable
fun FearGreedContent(
    viewModel: FearGreedViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val selectedMarket by viewModel.selectedMarket.collectAsState()
    val fearGreedData by viewModel.fearGreedData.collectAsState()
    val showFirstRunDialog by viewModel.showFirstRunDialog.collectAsState()
    var showManualPeriodDialog by remember { mutableStateOf(false) }

    // Get chart colors from settings
    val settingsViewModel: com.etfmonitor.ui.screens.settings.SettingsViewModel = hiltViewModel()
    val chartColorSettings by settingsViewModel.chartColorSettings.collectAsState()

    // First run dialog
    if (showFirstRunDialog) {
        FearGreedInitializeDialog(
            onDismiss = { viewModel.onFirstRunDialogShown() },
            onConfirm = { days ->
                viewModel.onFirstRunDialogConfirmed()
                viewModel.initialize(days)
            }
        )
    }

    // Manual data collection dialog
    if (showManualPeriodDialog) {
        FearGreedInitializeDialog(
            onDismiss = { showManualPeriodDialog = false },
            onConfirm = { days ->
                showManualPeriodDialog = false
                viewModel.initialize(days)
            }
        )
    }

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
                is FearGreedState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is FearGreedState.Initializing -> {
                    InitializingCard(
                        message = currentState.message,
                        progress = currentState.progress
                    )
                }
                is FearGreedState.Updating -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                currentState.message,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                is FearGreedState.Success -> {
                    SuccessCard(message = currentState.message)
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(3000)
                        viewModel.clearMessage()
                    }
                }
                is FearGreedState.Error -> {
                    ErrorInfoCard(message = currentState.message)
                }
                is FearGreedState.Idle -> {
                    if (!currentState.hasData) {
                        NoDataCard(onCollectClick = { showManualPeriodDialog = true })
                    }
                }
            }

            // Market Selection Chips
            MarketSelectionChips(
                selectedMarket = selectedMarket,
                onMarketSelected = { viewModel.onSelectedMarketChanged(it) }
            )

            // Main Content (if data available)
            if (fearGreedData.isNotEmpty()) {
                val latest = fearGreedData.firstOrNull()
                if (latest != null) {
                    // Gauge Visual
                    FearGreedGaugeSection(
                        value = (latest.fearGreedValue * 100).toFloat(),
                        oscillator = latest.oscillator
                    )

                    // Stats Row
                    StatsRow(data = fearGreedData)

                    // Chart
                    ChartSection(
                        data = fearGreedData,
                        selectedMarket = selectedMarket,
                        chartColors = chartColorSettings.fearGreed
                    )

                    // Disclaimer
                    DisclaimerText()
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FearGreedHeader(onNavigateBack: () -> Unit) {
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
            text = stringResource(R.string.fear_greed_title),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MarketSelectionChips(
    selectedMarket: String,
    onMarketSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedMarket == "KOSPI",
            onClick = { onMarketSelected("KOSPI") },
            label = { Text("KOSPI") },
            modifier = Modifier.weight(1f),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        FilterChip(
            selected = selectedMarket == "KOSDAQ",
            onClick = { onMarketSelected("KOSDAQ") },
            label = { Text("KOSDAQ") },
            modifier = Modifier.weight(1f),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
    }
}

@Composable
private fun FearGreedGaugeSection(
    value: Float,
    oscillator: Double
) {
    val (statusText, statusColor) = when {
        value >= 70 -> "Greed (탐욕)" to MaterialTheme.extendedColors.chartGreen
        value <= 30 -> "Fear (공포)" to MaterialTheme.extendedColors.chartRed
        else -> "Neutral (중립)" to MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Gauge visual (simplified semi-circle representation)
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(100.dp)
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Background arc
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(topStart = 100.dp, topEnd = 100.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            // Filled portion (simplified)
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (value / 100f).coerceIn(0f, 1f))
                    .height(200.dp)
                    .clip(RoundedCornerShape(topStart = 100.dp, topEnd = 100.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.primary
                            )
                        )
                    )
            )
        }

        // Value display
        Text(
            text = value.toInt().toString(),
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Black
            ),
            color = MaterialTheme.colorScheme.primary
        )

        // Status chip
        Surface(
            shape = RoundedCornerShape(50),
            color = statusColor.copy(alpha = 0.1f)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = statusColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Oscillator value
        Text(
            text = "Oscillator: ${String.format("%.3f", oscillator)}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (oscillator > 0) MaterialTheme.extendedColors.chartGreen
                    else MaterialTheme.extendedColors.chartRed
        )
    }
}

@Composable
private fun StatsRow(data: List<com.etfmonitor.database.entities.FearGreedIndex>) {
    val latest = data.firstOrNull()
    val yesterday = data.getOrNull(1)
    val weekAgo = data.getOrNull(5)
    val monthAgo = data.getOrNull(20)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatBox(
            label = "어제",
            value = yesterday?.let { (it.fearGreedValue * 100).toInt().toString() } ?: "—",
            modifier = Modifier.weight(1f)
        )
        StatBox(
            label = "1주일 전",
            value = weekAgo?.let { (it.fearGreedValue * 100).toInt().toString() } ?: "—",
            modifier = Modifier.weight(1f)
        )
        StatBox(
            label = "1달 전",
            value = monthAgo?.let { (it.fearGreedValue * 100).toInt().toString() } ?: "—",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ChartSection(
    data: List<com.etfmonitor.database.entities.FearGreedIndex>,
    selectedMarket: String,
    chartColors: SingleChartColorSettings
) {
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "$selectedMarket vs Index",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            FearGreedChart(
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
private fun DisclaimerText() {
    Text(
        text = "* Fear & Greed 지수는 시장의 과열 및 침체 정도를 나타내며,\n투자 판단의 보조 지표로 활용하는 것이 좋습니다.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 8.dp),
        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.5f
    )
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress / 100f },
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$progress%",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.fear_greed_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onCollectClick,
                shape = RoundedCornerShape(50)
            ) {
                Text(stringResource(R.string.action_collect_data))
            }
        }
    }
}

@Composable
fun FearGreedChart(
    data: List<com.etfmonitor.database.entities.FearGreedIndex>,
    chartColors: SingleChartColorSettings,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val fearGreedColor = chartColors.lineColor1
    val indexColor = chartColors.lineColor2
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
                            val reversedData = data.reversed()
                            return if (index >= 0 && index < reversedData.size) {
                                reversedData[index].date
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
                    setTextColor(fearGreedColor)
                    setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return String.format("%.3f", value)
                        }
                    }
                }

                axisRight.apply {
                    isEnabled = true
                    setDrawGridLines(false)
                    setTextColor(indexColor)
                    setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return String.format("%.0f", value)
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
            val reversedData = data.reversed()

            val oscillatorEntries = reversedData.mapIndexed { index, item ->
                Entry(index.toFloat(), item.oscillator.toFloat())
            }
            val oscillatorDataSet = LineDataSet(oscillatorEntries, "Oscillator").apply {
                axisDependency = YAxis.AxisDependency.LEFT
                color = fearGreedColor
                lineWidth = 2.5f
                setCircleColor(fearGreedColor)
                circleRadius = 2f
                setDrawCircleHole(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                highLightColor = fearGreedColor
            }

            val indexEntries = reversedData.mapIndexed { index, item ->
                Entry(index.toFloat(), item.indexValue.toFloat())
            }
            val indexDataSet = LineDataSet(indexEntries, "${reversedData.firstOrNull()?.market ?: ""} 지수").apply {
                axisDependency = YAxis.AxisDependency.RIGHT
                color = indexColor
                lineWidth = 2.5f
                setCircleColor(indexColor)
                circleRadius = 2f
                setDrawCircleHole(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                highLightColor = indexColor
            }

            val lineData = LineData(oscillatorDataSet, indexDataSet)
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
private fun FearGreedInitializeDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val periodOptions = listOf(
        FearGreedPeriodOption(180, "6개월", "약 180일"),
        FearGreedPeriodOption(365, "12개월 (권장)", "약 365일"),
        FearGreedPeriodOption(540, "18개월", "약 540일"),
        FearGreedPeriodOption(730, "24개월", "약 730일")
    )

    var selectedDays by remember { mutableStateOf(365) }

    AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.fear_greed_init_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.fear_greed_init_desc),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(8.dp))

                periodOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedDays == option.days),
                            onClick = { selectedDays = option.days }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
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
                        stringResource(R.string.dialog_fear_greed_time_estimate),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDays) }) {
                Text(stringResource(R.string.action_start_collection))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_later))
            }
        }
    )
}

private data class FearGreedPeriodOption(
    val days: Int,
    val label: String,
    val description: String
)
