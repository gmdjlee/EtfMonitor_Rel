package com.etfmonitor.ui.screens.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etfmonitor.database.entities.StockAmountRanking
import com.etfmonitor.ui.theme.*
import com.etfmonitor.ui.utils.AmountFormatter

/**
 * Statistics Screen - Ranking Tab Components
 * Contains AmountRankingTab and related components for stock ranking display
 */

@Composable
internal fun AmountRankingTab(
    rankings: List<StockAmountRanking>,
    viewModel: StatisticsViewModel,
    onStockClick: (String) -> Unit
) {
    val sortCriteria by viewModel.sortCriteria.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "총 ${rankings.size}개 종목",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (sortCriteria.isNotEmpty()) {
                    // 정렬 초기화 버튼
                    TextButton(
                        onClick = { viewModel.clearAllSorting() },
                        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.small)
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "정렬 초기화",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "초기화",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Text(
                    if (sortCriteria.isEmpty()) "열 클릭으로 정렬" else "정렬: ${sortCriteria.size}개 열",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Header card
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.extraSmall),
            shape = MaterialTheme.extendedShapes.card,
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(MaterialTheme.spacing.small),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
                Text("순위", Modifier.weight(0.5f), style = MaterialTheme.typography.labelSmall)

                SortableHeaderText(
                    text = "종목명",
                    column = SortColumn.STOCK_NAME,
                    sortOrder = viewModel.getSortOrder(SortColumn.STOCK_NAME),
                    priority = viewModel.getSortPriority(SortColumn.STOCK_NAME),
                    modifier = Modifier.weight(2f),
                    onClick = { viewModel.sortAmountRankingBy(SortColumn.STOCK_NAME) }
                )

                SortableHeaderText(
                    text = "금액",
                    column = SortColumn.TOTAL_AMOUNT,
                    sortOrder = viewModel.getSortOrder(SortColumn.TOTAL_AMOUNT),
                    priority = viewModel.getSortPriority(SortColumn.TOTAL_AMOUNT),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    onClick = { viewModel.sortAmountRankingBy(SortColumn.TOTAL_AMOUNT) }
                )

                SortableHeaderText(
                    text = "ETF수",
                    column = SortColumn.ETF_COUNT,
                    sortOrder = viewModel.getSortOrder(SortColumn.ETF_COUNT),
                    priority = viewModel.getSortPriority(SortColumn.ETF_COUNT),
                    modifier = Modifier.weight(0.6f),
                    textAlign = TextAlign.Center,
                    onClick = { viewModel.sortAmountRankingBy(SortColumn.ETF_COUNT) }
                )

                SortableHeaderText(
                    text = "신규",
                    column = SortColumn.NEW_ETF_COUNT,
                    sortOrder = viewModel.getSortOrder(SortColumn.NEW_ETF_COUNT),
                    priority = viewModel.getSortPriority(SortColumn.NEW_ETF_COUNT),
                    modifier = Modifier.weight(0.5f),
                    textAlign = TextAlign.Center,
                    onClick = { viewModel.sortAmountRankingBy(SortColumn.NEW_ETF_COUNT) }
                )

                SortableHeaderText(
                    text = "증가",
                    column = SortColumn.INCREASED_ETF_COUNT,
                    sortOrder = viewModel.getSortOrder(SortColumn.INCREASED_ETF_COUNT),
                    priority = viewModel.getSortPriority(SortColumn.INCREASED_ETF_COUNT),
                    modifier = Modifier.weight(0.5f),
                    textAlign = TextAlign.Center,
                    onClick = { viewModel.sortAmountRankingBy(SortColumn.INCREASED_ETF_COUNT) }
                )

                SortableHeaderText(
                    text = "감소",
                    column = SortColumn.DECREASED_ETF_COUNT,
                    sortOrder = viewModel.getSortOrder(SortColumn.DECREASED_ETF_COUNT),
                    priority = viewModel.getSortPriority(SortColumn.DECREASED_ETF_COUNT),
                    modifier = Modifier.weight(0.5f),
                    textAlign = TextAlign.Center,
                    onClick = { viewModel.sortAmountRankingBy(SortColumn.DECREASED_ETF_COUNT) }
                )

                SortableHeaderText(
                    text = "제외",
                    column = SortColumn.REMOVED_ETF_COUNT,
                    sortOrder = viewModel.getSortOrder(SortColumn.REMOVED_ETF_COUNT),
                    priority = viewModel.getSortPriority(SortColumn.REMOVED_ETF_COUNT),
                    modifier = Modifier.weight(0.5f),
                    textAlign = TextAlign.Center,
                    onClick = { viewModel.sortAmountRankingBy(SortColumn.REMOVED_ETF_COUNT) }
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            items(rankings.size) { index ->
                val item = rankings[index]
                AmountRankingCard(index + 1, item, onStockClick)
            }
        }
    }
}

@Composable
internal fun AmountRankingCard(
    rank: Int,
    item: StockAmountRanking,
    onStockClick: (String) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onStockClick(item.stockTicker) },
        shape = MaterialTheme.extendedShapes.card,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
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
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$rank",
                Modifier.weight(0.5f),
                style = MaterialTheme.typography.bodyLarge,
                color = when (rank) {
                    1 -> MaterialTheme.colorScheme.primary
                    2 -> MaterialTheme.colorScheme.secondary
                    3 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Column(Modifier.weight(2f)) {
                Text(
                    item.stockName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    item.stockTicker,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                AmountFormatter.format(item.totalAmount, showUnit = true),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End
            )
            Text(
                "${item.etfCount}",
                Modifier.weight(0.6f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                "${item.newEtfCount}",
                Modifier.weight(0.5f),
                style = MaterialTheme.typography.bodySmall,
                color = if (item.newEtfCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                "${item.increasedEtfCount}",
                Modifier.weight(0.5f),
                style = MaterialTheme.typography.bodySmall,
                color = if (item.increasedEtfCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                "${item.decreasedEtfCount}",
                Modifier.weight(0.5f),
                style = MaterialTheme.typography.bodySmall,
                color = if (item.decreasedEtfCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                "${item.removedEtfCount}",
                Modifier.weight(0.5f),
                style = MaterialTheme.typography.bodySmall,
                color = if (item.removedEtfCount > 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 정렬 가능한 헤더 텍스트 컴포넌트
 * - 3가지 상태 지원: 기본(정렬 없음), 내림차순, 오름차순
 * - 다중 컬럼 정렬 시 우선순위 표시
 */
@Composable
internal fun SortableHeaderText(
    text: String,
    column: SortColumn,
    sortOrder: SortOrder,
    priority: Int,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    onClick: () -> Unit
) {
    val isSorted = sortOrder != SortOrder.NONE

    Row(
        modifier = modifier.clickable(onClick = onClick),
        horizontalArrangement = when (textAlign) {
            TextAlign.Center -> Arrangement.Center
            TextAlign.End -> Arrangement.End
            else -> Arrangement.Start
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 정렬 우선순위 번호 (다중 컬럼 정렬 시)
        if (priority > 0) {
            Text(
                text = "$priority",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 2.dp)
            )
        }

        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSorted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }
        )

        // 정렬 상태 아이콘
        when (sortOrder) {
            SortOrder.DESCENDING -> {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = "내림차순",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            SortOrder.ASCENDING -> {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "오름차순",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            SortOrder.NONE -> {
                // 정렬 없음 - 아이콘 표시 안 함 (또는 옅은 UnfoldMore 아이콘)
                Icon(
                    imageVector = Icons.Default.UnfoldMore,
                    contentDescription = "정렬 가능",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                )
            }
        }
    }
}
