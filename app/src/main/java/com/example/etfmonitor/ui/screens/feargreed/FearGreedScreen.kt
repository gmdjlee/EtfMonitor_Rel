package com.etfmonitor.ui.screens.feargreed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.ui.components.LoadingCard
import com.etfmonitor.ui.components.ErrorCard
import com.etfmonitor.ui.components.IdleCard
import com.etfmonitor.ui.components.ChartCard
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.CombinedData
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.formatter.ValueFormatter
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.toArgb
import com.etfmonitor.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FearGreedScreen(
    onNavigateBack: () -> Unit,
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

    // 첫 실행 다이얼로그
    if (showFirstRunDialog) {
        FearGreedInitializeDialog(
            onDismiss = { viewModel.onFirstRunDialogShown() },
            onConfirm = { days ->
                viewModel.onFirstRunDialogConfirmed()
                viewModel.initialize(days)
            }
        )
    }

    // 수동 데이터 수집 다이얼로그
    if (showManualPeriodDialog) {
        FearGreedInitializeDialog(
            onDismiss = { showManualPeriodDialog = false },
            onConfirm = { days ->
                showManualPeriodDialog = false
                viewModel.initialize(days)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fear & Greed Index") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Market Selection
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "시장 선택",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedMarket == "KOSPI",
                            onClick = { viewModel.setSelectedMarket("KOSPI") },
                            label = { Text("KOSPI") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedMarket == "KOSDAQ",
                            onClick = { viewModel.setSelectedMarket("KOSDAQ") },
                            label = { Text("KOSDAQ") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // State Display
            when (val currentState = state) {
                is FearGreedState.Loading -> LoadingCard("데이터 로딩 중...")
                is FearGreedState.Initializing -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Text(currentState.message)
                            Text("진행률: ${currentState.progress}%", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                is FearGreedState.Updating -> LoadingCard(currentState.message)
                is FearGreedState.Success -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            currentState.message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(3000)
                        viewModel.clearMessage()
                    }
                }
                is FearGreedState.Error -> ErrorCard(currentState.message)
                is FearGreedState.Idle -> {
                    if (!currentState.hasData) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Fear & Greed Index 데이터가 없습니다.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(onClick = { showManualPeriodDialog = true }) {
                                    Text("데이터 수집")
                                }
                            }
                        }
                    }
                }
            }

            // Charts
            if (fearGreedData.isNotEmpty()) {
                // Latest Values Card
                val latest = fearGreedData.firstOrNull()
                if (latest != null) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "최신 지표 (${latest.date})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Fear & Greed", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${(latest.fearGreedValue * 100).toInt()}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            latest.fearGreedValue > 0.6 -> MaterialTheme.colorScheme.error
                                            latest.fearGreedValue < 0.4 -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                                Column {
                                    Text("Oscillator", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        String.format("%.3f", latest.oscillator),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (latest.oscillator > 0) MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // Fear & Greed Oscillator Chart with Index
                ChartCard(
                    title = "Fear & Greed Oscillator & ${selectedMarket} 지수",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FearGreedChart(
                        data = fearGreedData,
                        chartColors = chartColorSettings.fearGreed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                    )
                }
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
    val fearGreedColor = chartColors.lineColor1  // Fear & Greed Oscillator
    val indexColor = chartColors.lineColor2      // KOSPI/KOSDAQ 지수
    val textColor = chartColors.textColor        // 축 라벨/틱 색상 (기본값: Black)
    val legendColor = chartColors.legendColor    // 범례 색상 (기본값: Black)
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

                // X축 설정 (날짜)
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

                // 왼쪽 Y축 (Oscillator)
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridLineWidth = 1f
                    setGridColor(gridColor)
                    enableGridDashedLine(10f, 5f, 0f)
                    setTextColor(fearGreedColor)
                    setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                    // Oscillator는 범위가 자동으로 설정됨
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return String.format("%.3f", value)
                        }
                    }
                }

                // 오른쪽 Y축 (KOSPI/KOSDAQ 지수)
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

            // Oscillator 라인
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

            // KOSPI/KOSDAQ 지수 라인
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

    var selectedDays by remember { mutableStateOf(365) } // 기본값: 12개월

    AlertDialog(
        onDismissRequest = { },
        title = { Text("Fear & Greed Index 초기화") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Fear & Greed Index 데이터가 없습니다.\n수집 기간을 선택하세요.",
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
                        "데이터 수집에는 선택한 기간에 따라 1-3분 정도 소요됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDays) }) {
                Text("수집 시작")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("나중에")
            }
        }
    )
}

private data class FearGreedPeriodOption(
    val days: Int,
    val label: String,
    val description: String
)
