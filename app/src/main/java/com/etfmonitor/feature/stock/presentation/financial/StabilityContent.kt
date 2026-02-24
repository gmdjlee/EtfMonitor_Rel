package com.etfmonitor.feature.stock.presentation.financial

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.etfmonitor.R
import com.etfmonitor.core.ui.component.CustomMarkerView
import com.etfmonitor.feature.stock.domain.model.financial.FinancialSummary
import com.etfmonitor.feature.stock.domain.model.financial.formatPercent
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter

@Composable
fun StabilityContent(
    summary: FinancialSummary,
    modifier: Modifier = Modifier
) {
    if (!summary.hasStabilityData) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "안정성 데이터가 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val totalQuarters = summary.periods.size
    var selectedQuarterCount by remember(totalQuarters) { mutableIntStateOf(totalQuarters) }
    val trimmedSummary = remember(summary, selectedQuarterCount) {
        summary.trimToLast(selectedQuarterCount)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary Card with evaluation
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${summary.name} 안정성 요약",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()

                val latestDebt = summary.latestDebtRatio ?: 0.0
                val latestCurrent = summary.latestCurrentRatio ?: 0.0
                val latestBorrowing = summary.borrowingDependencies.lastOrNull() ?: 0.0

                StabilityRow(
                    label = "부채비율",
                    value = formatPercent(latestDebt),
                    evaluation = evaluateDebtRatio(latestDebt)
                )
                StabilityRow(
                    label = "유동비율",
                    value = formatPercent(latestCurrent),
                    evaluation = evaluateCurrentRatio(latestCurrent)
                )
                StabilityRow(
                    label = "차입금 의존도",
                    value = formatPercent(latestBorrowing),
                    evaluation = evaluateBorrowingDependency(latestBorrowing)
                )
            }
        }

        // Quarter selector
        if (totalQuarters > FinancialSummary.MIN_DISPLAY_QUARTERS) {
            QuarterSelector(
                totalQuarters = totalQuarters,
                selectedCount = selectedQuarterCount,
                onSelect = { selectedQuarterCount = it }
            )
        }

        // Combined stability chart
        ChartCard(title = "안정성 지표 추이") {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                factory = { context ->
                    LineChart(context).apply {
                        description.isEnabled = false
                        legend.isEnabled = true
                        setExtraOffsets(8f, 8f, 8f, 8f)
                    }
                },
                update = { chart ->
                    updateCombinedStabilityChart(chart, trimmedSummary)
                }
            )
        }

        // Individual charts
        if (trimmedSummary.debtRatios.any { it != 0.0 }) {
            IndividualRatioChart(
                title = "부채비율",
                data = trimmedSummary.debtRatios,
                labels = trimmedSummary.displayPeriods,
                colorHex = "#F44336"
            )
        }

        if (trimmedSummary.currentRatios.any { it != 0.0 }) {
            IndividualRatioChart(
                title = "유동비율",
                data = trimmedSummary.currentRatios,
                labels = trimmedSummary.displayPeriods,
                colorHex = "#4CAF50"
            )
        }

        if (trimmedSummary.borrowingDependencies.any { it != 0.0 }) {
            IndividualRatioChart(
                title = "차입금 의존도",
                data = trimmedSummary.borrowingDependencies,
                labels = trimmedSummary.displayPeriods,
                colorHex = "#FF9800"
            )
        }
    }
}

@Composable
private fun StabilityRow(
    label: String,
    value: String,
    evaluation: Pair<String, Color>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Surface(
                color = evaluation.second.copy(alpha = 0.15f),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    text = evaluation.first,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = evaluation.second,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun IndividualRatioChart(
    title: String,
    data: List<Double>,
    labels: List<String>,
    colorHex: String
) {
    ChartCard(title = title) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            factory = { context ->
                LineChart(context).apply {
                    description.isEnabled = false
                    legend.isEnabled = false
                    setExtraOffsets(8f, 8f, 8f, 8f)
                }
            },
            update = { chart ->
                val entries = data.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
                val dataSet = LineDataSet(entries, title).apply {
                    color = AndroidColor.parseColor(colorHex)
                    setCircleColor(AndroidColor.parseColor(colorHex))
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawValues(false)
                    setDrawCircleHole(false)
                    setDrawFilled(true)
                    fillColor = AndroidColor.parseColor(colorHex)
                    fillAlpha = 30
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }

                chart.data = LineData(dataSet)
                chart.xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    valueFormatter = IndexAxisValueFormatter(labels)
                    granularity = 1f
                    setDrawGridLines(false)
                }
                chart.axisLeft.apply {
                    setDrawGridLines(true)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String = "%.0f%%".format(value)
                    }
                }
                chart.axisRight.isEnabled = false

                chart.marker = CustomMarkerView(
                    chart.context, R.layout.marker_view, labels
                ) { value -> "%.1f%%".format(value) }
                chart.isHighlightPerTapEnabled = true

                chart.invalidate()
            }
        )
    }
}

private fun updateCombinedStabilityChart(chart: LineChart, summary: FinancialSummary) {
    val dataSets = mutableListOf<LineDataSet>()

    val debtEntries = summary.debtRatios.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
    dataSets.add(LineDataSet(debtEntries, "부채비율").apply {
        color = AndroidColor.parseColor("#F44336")
        setCircleColor(AndroidColor.parseColor("#F44336"))
        lineWidth = 2f; circleRadius = 3f; setDrawValues(false); setDrawCircleHole(false)
        mode = LineDataSet.Mode.CUBIC_BEZIER
    })

    val currentEntries = summary.currentRatios.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
    dataSets.add(LineDataSet(currentEntries, "유동비율").apply {
        color = AndroidColor.parseColor("#4CAF50")
        setCircleColor(AndroidColor.parseColor("#4CAF50"))
        lineWidth = 2f; circleRadius = 3f; setDrawValues(false); setDrawCircleHole(false)
        mode = LineDataSet.Mode.CUBIC_BEZIER
    })

    val borrowingEntries = summary.borrowingDependencies.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
    dataSets.add(LineDataSet(borrowingEntries, "차입금의존도").apply {
        color = AndroidColor.parseColor("#FF9800")
        setCircleColor(AndroidColor.parseColor("#FF9800"))
        lineWidth = 2f; circleRadius = 3f; setDrawValues(false); setDrawCircleHole(false)
        mode = LineDataSet.Mode.CUBIC_BEZIER
    })

    chart.data = LineData(dataSets.toList())
    chart.xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        valueFormatter = IndexAxisValueFormatter(summary.displayPeriods)
        granularity = 1f
        setDrawGridLines(false)
    }
    chart.axisLeft.apply {
        setDrawGridLines(true)
        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = "%.0f%%".format(value)
        }
    }
    chart.axisRight.isEnabled = false

    chart.marker = CustomMarkerView(
        chart.context, R.layout.marker_view, summary.displayPeriods
    ) { value -> "%.1f%%".format(value) }
    chart.isHighlightPerTapEnabled = true

    chart.invalidate()
}

// Evaluation functions
private val colorGreen = Color(0xFF4CAF50)
private val colorOrange = Color(0xFFFF9800)
private val colorRed = Color(0xFFF44336)

private fun evaluateDebtRatio(value: Double): Pair<String, Color> = when {
    value < 100.0 -> "양호" to colorGreen
    value < 200.0 -> "보통" to colorOrange
    else -> "주의" to colorRed
}

private fun evaluateCurrentRatio(value: Double): Pair<String, Color> = when {
    value >= 200.0 -> "양호" to colorGreen
    value >= 100.0 -> "보통" to colorOrange
    else -> "주의" to colorRed
}

private fun evaluateBorrowingDependency(value: Double): Pair<String, Color> = when {
    value < 30.0 -> "양호" to colorGreen
    value < 50.0 -> "보통" to colorOrange
    else -> "주의" to colorRed
}
