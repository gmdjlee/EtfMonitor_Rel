package com.etfmonitor.feature.stock.presentation.financial

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.etfmonitor.feature.stock.domain.model.financial.FinancialSummary
import com.etfmonitor.feature.stock.domain.model.financial.formatNumber
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary Card
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
                SummaryRow("매출액", formatNumber(summary.latestRevenue ?: 0L))
                SummaryRow("영업이익", formatNumber(summary.latestOperatingProfit ?: 0L))
                SummaryRow("당기순이익", formatNumber(summary.latestNetIncome ?: 0L))
            }
        }

        // Income Bar Chart
        if (summary.hasProfitabilityData) {
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
                        updateIncomeBarChart(chart, summary)
                    }
                )
            }
        }

        // Growth Rate Line Chart
        if (summary.hasGrowthData) {
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
                        updateGrowthLineChart(chart, summary)
                    }
                )
            }
        }

        // Asset Growth Line Chart
        if (summary.hasAssetGrowthData) {
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
                        updateAssetGrowthChart(chart, summary)
                    }
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
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

    chart.axisLeft.setDrawGridLines(true)
    chart.axisRight.isEnabled = false

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
    chart.axisLeft.setDrawGridLines(true)
    chart.axisRight.isEnabled = false
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
    chart.axisLeft.setDrawGridLines(true)
    chart.axisRight.isEnabled = false
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
