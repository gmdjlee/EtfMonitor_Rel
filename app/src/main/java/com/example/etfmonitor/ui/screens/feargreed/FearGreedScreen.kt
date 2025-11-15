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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.etfmonitor.ui.components.LoadingCard
import com.etfmonitor.ui.components.ErrorCard
import com.etfmonitor.ui.components.IdleCard
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FearGreedScreen(
    onNavigateBack: () -> Unit,
    viewModel: FearGreedViewModel = viewModel(factory = FearGreedViewModel.factory(androidx.compose.ui.platform.LocalContext.current))
) {
    val state by viewModel.state.collectAsState()
    val selectedMarket by viewModel.selectedMarket.collectAsState()
    val fearGreedData by viewModel.fearGreedData.collectAsState()
    val showFirstRunDialog by viewModel.showFirstRunDialog.collectAsState()

    // 첫 실행 다이얼로그
    if (showFirstRunDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Fear & Greed Index 초기화") },
            text = { Text("Fear & Greed Index 데이터가 없습니다.\n1년치 데이터를 수집하시겠습니까? (약 1-2분 소요)") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onFirstRunDialogShown()
                    viewModel.initialize(365)
                }) {
                    Text("수집 시작")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onFirstRunDialogShown() }) {
                    Text("나중에")
                }
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
                                Button(onClick = { viewModel.initialize(365) }) {
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

                // Fear & Greed Index Chart
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Fear & Greed Index",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        FearGreedChart(
                            data = fearGreedData,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                        )
                    }
                }

                // Oscillator Chart
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "MACD Oscillator",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OscillatorChart(
                            data = fearGreedData,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FearGreedChart(
    data: List<com.etfmonitor.database.entities.FearGreedIndex>,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(true)
                    textColor = colorScheme.onSurface.hashCode()
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    textColor = colorScheme.onSurface.hashCode()
                    axisMinimum = 0f
                    axisMaximum = 1f
                }

                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            val entries = data.reversed().mapIndexed { index, item ->
                Entry(index.toFloat(), item.fearGreedValue.toFloat())
            }

            val dataSet = LineDataSet(entries, "Fear & Greed").apply {
                color = colorScheme.primary.hashCode()
                lineWidth = 2f
                setDrawCircles(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }

            chart.data = LineData(dataSet)
            chart.invalidate()
        },
        modifier = modifier
    )
}

@Composable
fun OscillatorChart(
    data: List<com.etfmonitor.database.entities.FearGreedIndex>,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(true)
                    textColor = colorScheme.onSurface.hashCode()
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    textColor = colorScheme.onSurface.hashCode()
                }

                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            val entries = data.reversed().mapIndexed { index, item ->
                Entry(index.toFloat(), item.oscillator.toFloat())
            }

            val dataSet = LineDataSet(entries, "Oscillator").apply {
                color = colorScheme.secondary.hashCode()
                lineWidth = 2f
                setDrawCircles(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }

            chart.data = LineData(dataSet)
            chart.invalidate()
        },
        modifier = modifier
    )
}
