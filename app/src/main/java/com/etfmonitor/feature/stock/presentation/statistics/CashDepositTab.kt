package com.etfmonitor.feature.stock.presentation.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etfmonitor.feature.stock.domain.model.CashDepositTrend
import com.etfmonitor.core.ui.component.ChartCard
import com.etfmonitor.core.ui.theme.*
import com.etfmonitor.core.common.util.AmountFormatter
import androidx.compose.ui.graphics.Color as ComposeColor
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Statistics Screen - Cash Deposit Tab Components
 * Contains CashDepositTrendTab and related components for displaying cash deposit trends
 */

@Composable
internal fun CashDepositTrendTab(trend: List<CashDepositTrend>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
    ) {
        if (trend.isEmpty()) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.extendedShapes.cardLarge
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.spacing.large),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "원화예금 데이터가 없습니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            CashDepositSummaryCard(trend)
            CashDepositChartCard(trend)
            CashDepositDataTable(trend)
        }
    }
}

@Composable
internal fun CashDepositSummaryCard(trend: List<CashDepositTrend>) {
    val first = trend.first()
    val last = trend.last()
    val change = last.totalAmount - first.totalAmount

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.extendedShapes.cardLarge,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Text(
                "원화예금 요약",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(
                    label = "현재 총액",
                    value = AmountFormatter.format(last.totalAmount)
                )
                SummaryItem(
                    label = "변동액",
                    value = AmountFormatter.formatChange(change)
                )
                SummaryItem(
                    label = "ETF 수",
                    value = "${last.etfCount}개"
                )
            }
        }
    }
}

@Composable
internal fun CashDepositChartCard(trend: List<CashDepositTrend>) {
    ChartCard(
        title = "원화예금 추이 (억원)",
        modifier = Modifier.fillMaxWidth()
    ) {
        val modelProducer = remember { CartesianChartModelProducer() }
        val scope = rememberCoroutineScope()
        val dateLabelsKey = remember { ExtraStore.Key<List<String>>() }

        LaunchedEffect(trend) {
            scope.launch(Dispatchers.Default) {
                modelProducer.runTransaction {
                    lineSeries {
                        series(trend.map { (it.totalAmount / 100_000_000).toDouble() })
                    }
                    extras { extraStore ->
                        extraStore[dateLabelsKey] = trend.map { formatDateForChart(it.date) }
                    }
                }
            }
        }

        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = rememberStartAxis(
                    label = rememberTextComponent(
                        color = ComposeColor.Black,
                        textSize = 10.sp
                    )
                ),
                bottomAxis = rememberBottomAxis(
                    label = rememberTextComponent(
                        color = ComposeColor.Black,
                        textSize = 10.sp
                    ),
                    valueFormatter = { x, chartValues, _ ->
                        val dateLabels = chartValues.model.extraStore.getOrNull(dateLabelsKey)
                        val index = x.toInt()
                        if (dateLabels != null && index >= 0 && index < dateLabels.size) {
                            dateLabels[index]
                        } else {
                            ""
                        }
                    },
                    itemPlacer = remember {
                        HorizontalAxis.ItemPlacer.default(
                            spacing = 2,
                            addExtremeLabelPadding = true
                        )
                    }
                )
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
    }
}

@Composable
internal fun CashDepositDataTable(trend: List<CashDepositTrend>) {
    val maxAmount = trend.maxOfOrNull { it.totalAmount } ?: 0f
    val headerText = AmountFormatter.getTableHeader(maxAmount)

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.extendedShapes.cardLarge
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.spacing.medium)) {
            Text(
                "상세 데이터",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(MaterialTheme.spacing.small))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
                Text("날짜", Modifier.weight(2f), style = MaterialTheme.typography.labelSmall)
                Text(headerText, Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall)
                Text("ETF수", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = MaterialTheme.spacing.extraSmall),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            trend.reversed().take(5).forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MaterialTheme.spacing.extraSmall),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                ) {
                    Text(
                        item.date,
                        Modifier.weight(2f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        AmountFormatter.formatForTable(item.totalAmount, maxAmount),
                        Modifier.weight(1.5f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${item.etfCount}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
internal fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

internal fun formatDateForChart(date: String): String {
    return try {
        val parts = date.split("-")
        if (parts.size == 3) {
            "${parts[1]}/${parts[2]}"
        } else {
            date
        }
    } catch (e: Exception) {
        date
    }
}
