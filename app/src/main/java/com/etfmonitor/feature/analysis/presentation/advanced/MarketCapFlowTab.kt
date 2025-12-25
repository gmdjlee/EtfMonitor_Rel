package com.etfmonitor.feature.analysis.presentation.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etfmonitor.R
import com.etfmonitor.core.database.entities.*

/**
 * Advanced Dashboard Screen - Market Cap Flow Tab
 * Contains market cap weighted ETF flow analysis
 */

@Composable
internal fun MarketCapFlowTab(
    data: AdvancedDashboardData,
    history: List<MarketCapFlowHistoryItem> = emptyList(),
    accuracy: PredictionAccuracy? = null
) {
    val flow = data.marketCapFlow

    if (flow == null) {
        EmptyStateCard(stringResource(R.string.advanced_needs_etf_holdings), Icons.Default.BarChart)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 예측 정확도 카드 (데이터가 있을 경우)
        if (accuracy != null) {
            item { PredictionAccuracyCard(stringResource(R.string.advanced_market_cap_flow), accuracy) }
        }

        // 히스토리 차트 (데이터가 있을 경우)
        if (history.isNotEmpty()) {
            item { MarketCapFlowHistoryCard(history) }
        }

        // 핵심 지표 카드
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (flow.netFlow >= 0) GreenPositive.copy(alpha = 0.1f)
                    else RedNegative.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.advanced_net_fund_flow), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${if (flow.netFlow >= 0) "+" else ""}${formatAmount(flow.netFlow)}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (flow.netFlow >= 0) GreenPositive else RedNegative
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FlowStatItem(stringResource(R.string.advanced_inflow), "+${formatAmount(flow.totalInflow)}", GreenPositive)
                        FlowStatItem(stringResource(R.string.advanced_outflow), "-${formatAmount(flow.totalOutflow)}", RedNegative)
                    }
                }
            }
        }

        // 시총 규모별 분포
        item {
            SectionCard(stringResource(R.string.advanced_size_by_category)) {
                MarketCapSize.entries.forEach { size ->
                    val inflow = flow.inflowBySize[size] ?: 0L
                    val outflow = flow.outflowBySize[size] ?: 0L
                    val net = inflow - outflow
                    val maxValue = maxOf(flow.totalInflow, flow.totalOutflow).coerceAtLeast(1L)

                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(size.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${if (net >= 0) "+" else ""}${formatAmount(net)}",
                                fontWeight = FontWeight.Bold,
                                color = if (net >= 0) GreenPositive else RedNegative
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowProgressBar(inflow, outflow, maxValue)
                    }
                }
            }
        }

        // 상위 유입 종목
        if (flow.topInflowStocks.isNotEmpty()) {
            item {
                SectionCard(stringResource(R.string.advanced_top_inflow)) {
                    flow.topInflowStocks.take(10).forEachIndexed { idx, stock ->
                        StockFlowRow(idx + 1, stock, true)
                    }
                }
            }
        }

        // 상위 유출 종목
        if (flow.topOutflowStocks.isNotEmpty()) {
            item {
                SectionCard(stringResource(R.string.advanced_top_outflow)) {
                    flow.topOutflowStocks.take(10).forEachIndexed { idx, stock ->
                        StockFlowRow(idx + 1, stock, false)
                    }
                }
            }
        }
    }
}

@Composable
internal fun FlowStatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun FlowProgressBar(inflow: Long, outflow: Long, maxValue: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (inflow > 0) {
            Box(
                modifier = Modifier
                    .weight((inflow.toFloat() / maxValue).coerceIn(0.01f, 1f))
                    .fillMaxHeight()
                    .background(GreenPositive)
            )
        }
        if (outflow > 0) {
            Box(
                modifier = Modifier
                    .weight((outflow.toFloat() / maxValue).coerceIn(0.01f, 1f))
                    .fillMaxHeight()
                    .background(RedNegative)
            )
        }
        // 남은 공간 채우기
        val remaining = 1f - ((inflow + outflow).toFloat() / maxValue).coerceIn(0f, 1f)
        if (remaining > 0.01f) {
            Spacer(modifier = Modifier.weight(remaining))
        }
    }
}

@Composable
internal fun StockFlowRow(rank: Int, stock: StockFlow, isInflow: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$rank.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(stock.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                stringResource(R.string.advanced_stock_marketcap_etf, formatMarketCap(stock.marketCap), stock.etfCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "${if (isInflow) "+" else ""}${formatAmount(stock.flowAmount)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (isInflow) GreenPositive else RedNegative
        )
    }
}
