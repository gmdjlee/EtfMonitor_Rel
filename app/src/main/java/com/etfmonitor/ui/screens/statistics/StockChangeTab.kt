package com.etfmonitor.ui.screens.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etfmonitor.database.entities.HoldingStatus
import com.etfmonitor.database.entities.StockChangeInfo
import com.etfmonitor.core.ui.theme.*
import com.etfmonitor.core.common.util.AmountFormatter

/**
 * Statistics Screen - Stock Change Tab Components
 * Contains StockChangeTab and related components for displaying stock changes
 * (new additions, removals, increases, decreases)
 */

/**
 * 통합된 종목 변화 탭 컴포넌트
 * @param stocks 종목 변화 목록
 * @param status 보유 상태 (NEW, REMOVED, INCREASE, DECREASE)
 * @param onStockClick 종목 클릭 핸들러
 */
@Composable
internal fun StockChangeTab(
    stocks: List<StockChangeInfo>,
    status: HoldingStatus,
    onStockClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "총 ${stocks.size}개 종목",
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        LazyColumn(
            contentPadding = PaddingValues(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            items(stocks) { stock ->
                StockChangeCard(stock, status, onStockClick)
            }
        }
    }
}

@Composable
internal fun StockChangeCard(
    stock: StockChangeInfo,
    status: HoldingStatus,
    onStockClick: (String) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onStockClick(stock.stockTicker) },
        shape = MaterialTheme.extendedShapes.card,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(MaterialTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stock.stockName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stock.stockTicker,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(status)
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Text(
                "ETF: ${stock.etfName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                when (status) {
                    HoldingStatus.NEW -> {
                        WeightInfo("비중", stock.currentWeight, Modifier.weight(1f))
                    }
                    HoldingStatus.REMOVED -> {
                        WeightInfo("이전", stock.previousWeight, Modifier.weight(1f))
                    }
                    HoldingStatus.INCREASE, HoldingStatus.DECREASE -> {
                        WeightInfo("이전", stock.previousWeight, Modifier.weight(1f))
                        WeightInfo("현재", stock.currentWeight, Modifier.weight(1f))
                        ChangeInfo(stock.change, Modifier.weight(1f))
                    }
                    else -> {}
                }
            }

            if (stock.currentAmount > 0) {
                Text(
                    "평가금액: ${AmountFormatter.format(stock.currentAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
internal fun StatusBadge(status: HoldingStatus) {
    val (text, color) = when (status) {
        HoldingStatus.NEW -> "신규" to MaterialTheme.colorScheme.primary
        HoldingStatus.REMOVED -> "제외" to MaterialTheme.colorScheme.outline
        HoldingStatus.INCREASE -> "증가" to MaterialTheme.colorScheme.tertiary
        HoldingStatus.DECREASE -> "감소" to MaterialTheme.colorScheme.error
        else -> "유지" to MaterialTheme.colorScheme.outline
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.extendedShapes.badge,
        tonalElevation = 1.dp
    ) {
        Text(
            text,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.small,
                vertical = 4.dp
            ),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
internal fun WeightInfo(label: String, weight: Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            String.format("%.2f%%", weight),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
internal fun ChangeInfo(change: Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "변동",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            String.format("%+.2f%%", change),
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                change > 0 -> MaterialTheme.colorScheme.tertiary
                change < 0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
