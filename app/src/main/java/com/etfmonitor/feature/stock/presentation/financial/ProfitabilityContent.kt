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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.etfmonitor.R
import com.etfmonitor.core.ui.component.CustomMarkerView
import com.etfmonitor.feature.stock.domain.model.financial.FinancialSummary
import com.etfmonitor.feature.stock.domain.model.financial.formatNumber
import com.etfmonitor.feature.stock.domain.model.financial.formatPercent
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter

@Composable
fun ProfitabilityContent(
    summary: FinancialSummary,
    modifier: Modifier = Modifier
) {
    if (!summary.hasProfitabilityData && !summary.hasGrowthData && !summary.hasAssetGrowthData) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "수익성 데이터가 없습니다.",
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
        // Summary Card with growth rates
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${summary.name} 수익성 요약",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()
                SummaryRowWithGrowth(
                    label = "매출액",
                    value = formatNumber(summary.latestRevenue ?: 0L),
                    growthRate = summary.revenueGrowthRates.lastOrNull()
                )
                SummaryRowWithGrowth(
                    label = "영업이익",
                    value = formatNumber(summary.latestOperatingProfit ?: 0L),
                    growthRate = summary.operatingProfitGrowthRates.lastOrNull()
                )
                SummaryRowWithGrowth(
                    label = "당기순이익",
                    value = formatNumber(summary.latestNetIncome ?: 0L),
                    growthRate = summary.netIncomeGrowthRates.lastOrNull()
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

        // Income Bar Chart
        if (trimmedSummary.hasProfitabilityData) {
            ChartCard(title = "손익 추이") {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    factory = { context ->
                        BarChart(context).apply {
                            setupCommonChartProperties(this)
                        }
                    },
                    update = { chart ->
                        updateIncomeBarChart(chart, trimmedSummary)
                    }
                )
            }
        }

        // Growth Rate Line Chart
        if (trimmedSummary.hasGrowthData) {
            ChartCard(title = "성장률 추이") {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    factory = { context ->
                        LineChart(context).apply {
                            setupCommonChartProperties(this)
                        }
                    },
                    update = { chart ->
                        updateGrowthLineChart(chart, trimmedSummary)
                    }
                )
            }
        }

        // Asset Growth Line Chart
        if (trimmedSummary.hasAssetGrowthData) {
            ChartCard(title = "자산 성장률") {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    factory = { context ->
                        LineChart(context).apply {
                            setupCommonChartProperties(this)
                        }
                    },
                    update = { chart ->
                        updateAssetGrowthChart(chart, trimmedSummary)
                    }
                )
            }
        }
    }
}

@Composable
private fun SummaryRowWithGrowth(
    label: String,
    value: String,
    growthRate: Double?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        if (growthRate != null && growthRate != 0.0) {
            val growthColor = when {
                growthRate > 0 -> Color(0xFFF44336) // 한국 주식: 상승=빨강
                growthRate < 0 -> Color(0xFF2196F3) // 하락=파랑
                else -> Color.Unspecified
            }
            val prefix = if (growthRate > 0) "+" else ""
            Text(
                text = "$prefix${formatPercent(growthRate)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = growthColor,
                textAlign = TextAlign.End,
                modifier = Modifier.width(72.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(72.dp))
        }
    }
}

@Composable
internal fun QuarterSelector(
    totalQuarters: Int,
    selectedCount: Int,
    onSelect: (Int) -> Unit
) {
    val options = buildList {
        add(FinancialSummary.MIN_DISPLAY_QUARTERS)
        if (totalQuarters > 8) add(8)
        if (totalQuarters > FinancialSummary.MIN_DISPLAY_QUARTERS) add(totalQuarters)
    }.distinct()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "표시 분기",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        options.forEach { count ->
            val label = if (count == totalQuarters) "전체(${count}Q)" else "${count}Q"
            FilterChip(
                selected = selectedCount == count,
                onClick = { onSelect(count) },
                label = {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            )
        }
    }
}

@Composable
internal fun ChartCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

private fun setupCommonChartProperties(chart: com.github.mikephil.charting.charts.Chart<*>) {
    chart.description.isEnabled = false
    chart.legend.isEnabled = true
    chart.setExtraOffsets(8f, 8f, 8f, 8f)
    if (chart is BarChart) {
        chart.setFitBars(true)
    }
}

private fun updateIncomeBarChart(chart: BarChart, summary: FinancialSummary) {
    val revenueEntries = summary.revenues.mapIndexed { i, v -> BarEntry(i.toFloat(), v.toFloat()) }
    val opProfitEntries = summary.operatingProfits.mapIndexed { i, v -> BarEntry(i.toFloat(), v.toFloat()) }
    val netIncomeEntries = summary.netIncomes.mapIndexed { i, v -> BarEntry(i.toFloat(), v.toFloat()) }

    val revenueSet = BarDataSet(revenueEntries, "매출액").apply {
        color = AndroidColor.parseColor("#4CAF50")
    }
    val opProfitSet = BarDataSet(opProfitEntries, "영업이익").apply {
        color = AndroidColor.parseColor("#2196F3")
    }
    val netIncomeSet = BarDataSet(netIncomeEntries, "당기순이익").apply {
        color = AndroidColor.parseColor("#FF9800")
    }

    listOf(revenueSet, opProfitSet, netIncomeSet).forEach {
        it.setDrawValues(false)
    }

    val groupSpace = 0.1f
    val barSpace = 0.02f
    val barWidth = 0.26f

    chart.data = BarData(revenueSet, opProfitSet, netIncomeSet).apply {
        this.barWidth = barWidth
    }

    chart.xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        valueFormatter = IndexAxisValueFormatter(summary.displayPeriods)
        granularity = 1f
        setDrawGridLines(false)
        setCenterAxisLabels(true)
        axisMinimum = 0f
        axisMaximum = summary.displayPeriods.size.toFloat()
    }

    chart.axisLeft.apply {
        setDrawGridLines(true)
        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return formatNumber(value.toLong())
            }
        }
    }
    chart.axisRight.isEnabled = false

    chart.marker = CustomMarkerView(
        chart.context, R.layout.marker_view, summary.displayPeriods
    ) { value -> formatNumber(value.toLong()) }
    chart.isHighlightPerTapEnabled = true

    chart.groupBars(0f, groupSpace, barSpace)
    chart.invalidate()
}

private fun updateGrowthLineChart(chart: LineChart, summary: FinancialSummary) {
    val dataSets = mutableListOf<LineDataSet>()

    val revenueGrowth = summary.revenueGrowthRates.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
    dataSets.add(createLineDataSet(revenueGrowth, "매출액 증가율", "#4CAF50"))

    val opProfitGrowth = summary.operatingProfitGrowthRates.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
    dataSets.add(createLineDataSet(opProfitGrowth, "영업이익 증가율", "#2196F3"))

    val netIncomeGrowth = summary.netIncomeGrowthRates.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
    dataSets.add(createLineDataSet(netIncomeGrowth, "순이익 증가율", "#FF9800"))

    chart.data = LineData(dataSets.toList())
    setupLineChartXAxis(chart, summary.displayPeriods)
    chart.axisLeft.apply {
        setDrawGridLines(true)
        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = "%.1f%%".format(value)
        }
    }
    chart.axisRight.isEnabled = false

    chart.marker = CustomMarkerView(
        chart.context, R.layout.marker_view, summary.displayPeriods
    ) { value -> "%.1f%%".format(value) }
    chart.isHighlightPerTapEnabled = true

    chart.invalidate()
}

private fun updateAssetGrowthChart(chart: LineChart, summary: FinancialSummary) {
    val dataSets = mutableListOf<LineDataSet>()

    val equityGrowth = summary.equityGrowthRates.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
    dataSets.add(createLineDataSet(equityGrowth, "자기자본 증가율", "#9C27B0"))

    val totalAssetsGrowth = summary.totalAssetsGrowthRates.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
    dataSets.add(createLineDataSet(totalAssetsGrowth, "총자산 증가율", "#00BCD4"))

    chart.data = LineData(dataSets.toList())
    setupLineChartXAxis(chart, summary.displayPeriods)
    chart.axisLeft.apply {
        setDrawGridLines(true)
        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = "%.1f%%".format(value)
        }
    }
    chart.axisRight.isEnabled = false

    chart.marker = CustomMarkerView(
        chart.context, R.layout.marker_view, summary.displayPeriods
    ) { value -> "%.1f%%".format(value) }
    chart.isHighlightPerTapEnabled = true

    chart.invalidate()
}

private fun createLineDataSet(entries: List<Entry>, label: String, colorHex: String): LineDataSet {
    return LineDataSet(entries, label).apply {
        color = AndroidColor.parseColor(colorHex)
        setCircleColor(AndroidColor.parseColor(colorHex))
        lineWidth = 2f
        circleRadius = 3f
        setDrawValues(false)
        setDrawCircleHole(false)
        mode = LineDataSet.Mode.CUBIC_BEZIER
    }
}

private fun setupLineChartXAxis(chart: LineChart, labels: List<String>) {
    chart.xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        valueFormatter = IndexAxisValueFormatter(labels)
        granularity = 1f
        setDrawGridLines(false)
    }
}
