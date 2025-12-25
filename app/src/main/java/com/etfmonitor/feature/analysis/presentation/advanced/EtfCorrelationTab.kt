package com.etfmonitor.feature.analysis.presentation.advanced

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etfmonitor.R
import com.etfmonitor.core.database.entities.*
import kotlinx.serialization.json.Json

/**
 * Advanced Dashboard Screen - ETF Correlation Tab
 * Contains ETF overlap and correlation analysis with detailed interpretation
 */

private val json = Json { ignoreUnknownKeys = true }

@Composable
internal fun EtfCorrelationTab(
    data: AdvancedDashboardData,
    isCalculating: Boolean = false,
    onCalculate: (() -> Unit)? = null
) {
    val overlaps = data.highOverlapEtfs

    if (overlaps.isEmpty()) {
        if (isCalculating) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.advanced_calculating_correlation))
                }
            }
        } else {
            EmptyStateCard(
                message = stringResource(R.string.advanced_needs_correlation_data),
                icon = Icons.Default.GridView,
                actionLabel = stringResource(R.string.advanced_calculate_correlation),
                onAction = onCalculate
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 종합 요약
        item {
            CorrelationSummaryCard(overlaps)
        }

        // 분산 투자 권고 (80% 이상 중복이 있을 때)
        if (overlaps.any { it.overlapRatio > 0.8 }) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = OrangeAccent
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                stringResource(R.string.advanced_diversification_advice),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(R.string.advanced_diversification_warning),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // 높은 중복률 ETF 쌍 (상세 해석 포함)
        item {
            SectionCard(stringResource(R.string.advanced_high_overlap_pairs)) {
                Text(
                    stringResource(R.string.advanced_overlap_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                overlaps.take(10).forEach { pair ->
                    EtfCorrelationRowWithInterpretation(pair)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // 상관관계 범례
        item {
            SectionCard(stringResource(R.string.advanced_correlation_legend)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CorrelationLegendItemExtended(
                        label = stringResource(R.string.advanced_overlap_very_high),
                        color = Color(0xFFB71C1C),
                        description = stringResource(R.string.advanced_suggest_choose_one)
                    )
                    CorrelationLegendItemExtended(
                        label = stringResource(R.string.advanced_overlap_high),
                        color = Color(0xFFD32F2F),
                        description = stringResource(R.string.advanced_suggest_compare_cost)
                    )
                    CorrelationLegendItemExtended(
                        label = stringResource(R.string.advanced_overlap_medium),
                        color = OrangeAccent,
                        description = stringResource(R.string.advanced_suggest_both_ok)
                    )
                    CorrelationLegendItemExtended(
                        label = stringResource(R.string.advanced_overlap_low),
                        color = GreenPositive,
                        description = stringResource(R.string.advanced_suggest_excellent_pair)
                    )
                }
            }
        }
    }
}

@Composable
internal fun CorrelationSummaryCard(overlaps: List<EtfCorrelationCache>) {
    val highOverlapCount = overlaps.count { it.overlapRatio > 0.7 }
    val avgOverlap = if (overlaps.isNotEmpty()) overlaps.map { it.overlapRatio }.average() else 0.0

    val portfolioStatus = when {
        overlaps.any { it.overlapRatio > 0.8 } -> stringResource(R.string.advanced_portfolio_needs_review)
        avgOverlap > 0.5 -> stringResource(R.string.advanced_portfolio_good)
        else -> stringResource(R.string.advanced_portfolio_excellent)
    }

    val statusColor = when {
        overlaps.any { it.overlapRatio > 0.8 } -> Color(0xFFD32F2F)
        avgOverlap > 0.5 -> OrangeAccent
        else -> GreenPositive
    }

    SectionCard(stringResource(R.string.advanced_summary_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    stringResource(R.string.advanced_summary_analyzed_pairs, overlaps.size),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.advanced_summary_high_overlap_count, highOverlapCount),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.advanced_summary_avg_overlap, avgOverlap * 100),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.advanced_portfolio_status),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        portfolioStatus,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }
        }
    }
}

@Composable
internal fun EtfCorrelationRowWithInterpretation(pair: EtfCorrelationCache) {
    var isExpanded by remember { mutableStateOf(false) }

    val overlapLevel = when {
        pair.overlapRatio > 0.8 -> OverlapLevel.VERY_HIGH
        pair.overlapRatio > 0.7 -> OverlapLevel.HIGH
        pair.overlapRatio > 0.4 -> OverlapLevel.MEDIUM
        else -> OverlapLevel.LOW
    }

    val overlapColor = when (overlapLevel) {
        OverlapLevel.VERY_HIGH -> Color(0xFFB71C1C)
        OverlapLevel.HIGH -> Color(0xFFD32F2F)
        OverlapLevel.MEDIUM -> OrangeAccent
        OverlapLevel.LOW -> GreenPositive
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(containerColor = overlapColor.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 기본 정보 행
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        pair.etf1Name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            pair.etf2Name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${(pair.overlapRatio * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = overlapColor
                    )
                    Text(
                        stringResource(R.string.advanced_common_stocks, pair.commonStockCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded)
                        stringResource(R.string.advanced_detail_collapse)
                    else
                        stringResource(R.string.advanced_detail_expand),
                    modifier = Modifier.padding(start = 8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 확장 가능한 상세 정보
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(
                        color = overlapColor.copy(alpha = 0.3f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // 중복률 해석
                    InterpretationSection(overlapLevel, pair)

                    // 비중 상관계수
                    Spacer(modifier = Modifier.height(12.dp))
                    WeightCorrelationSection(pair.weightCorrelation)

                    // 주요 공통 종목
                    if (pair.topCommonStocks.isNotBlank() && pair.topCommonStocks != "[]") {
                        Spacer(modifier = Modifier.height(12.dp))
                        TopCommonStocksSection(pair)
                    }

                    // 투자 제안
                    Spacer(modifier = Modifier.height(12.dp))
                    InvestmentSuggestionSection(overlapLevel)
                }
            }
        }
    }
}

@Composable
private fun InterpretationSection(overlapLevel: OverlapLevel, pair: EtfCorrelationCache) {
    val levelLabel = when (overlapLevel) {
        OverlapLevel.VERY_HIGH -> stringResource(R.string.advanced_overlap_very_high)
        OverlapLevel.HIGH -> stringResource(R.string.advanced_overlap_high)
        OverlapLevel.MEDIUM -> stringResource(R.string.advanced_overlap_medium)
        OverlapLevel.LOW -> stringResource(R.string.advanced_overlap_low)
    }

    val interpretation = when (overlapLevel) {
        OverlapLevel.VERY_HIGH -> stringResource(R.string.advanced_interpret_very_high)
        OverlapLevel.HIGH -> stringResource(R.string.advanced_interpret_high)
        OverlapLevel.MEDIUM -> stringResource(R.string.advanced_interpret_medium)
        OverlapLevel.LOW -> stringResource(R.string.advanced_interpret_low)
    }

    val levelColor = when (overlapLevel) {
        OverlapLevel.VERY_HIGH -> Color(0xFFB71C1C)
        OverlapLevel.HIGH -> Color(0xFFD32F2F)
        OverlapLevel.MEDIUM -> OrangeAccent
        OverlapLevel.LOW -> GreenPositive
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Analytics,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = levelColor
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                levelLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = levelColor
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            interpretation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 종목 수 정보
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                stringResource(R.string.advanced_etf1_stocks, pair.etf1StockCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.advanced_etf2_stocks, pair.etf2StockCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeightCorrelationSection(weightCorrelation: Double) {
    val corrLabel = when {
        weightCorrelation > 0.8 -> stringResource(R.string.advanced_weight_very_similar)
        weightCorrelation > 0.5 -> stringResource(R.string.advanced_weight_similar)
        weightCorrelation > 0.2 -> stringResource(R.string.advanced_weight_different)
        else -> stringResource(R.string.advanced_weight_very_different)
    }

    val corrColor = when {
        weightCorrelation > 0.8 -> Color(0xFFD32F2F)
        weightCorrelation > 0.5 -> OrangeAccent
        else -> GreenPositive
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Balance,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = corrColor
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                stringResource(R.string.advanced_weight_correlation),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                String.format("%+.2f", weightCorrelation),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = corrColor
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            corrLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TopCommonStocksSection(pair: EtfCorrelationCache) {
    val commonStocks = remember(pair.topCommonStocks) {
        try {
            json.decodeFromString<List<CommonStock>>(pair.topCommonStocks)
        } catch (e: Exception) {
            emptyList()
        }
    }

    if (commonStocks.isEmpty()) return

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.PieChart,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                stringResource(R.string.advanced_top_common_holdings),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        commonStocks.take(5).forEach { stock ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stock.name,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(
                        R.string.advanced_stock_weight_format,
                        "",
                        stock.etf1Weight,
                        stock.etf2Weight
                    ).trim(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InvestmentSuggestionSection(overlapLevel: OverlapLevel) {
    val (icon, suggestion, suggestionColor) = when (overlapLevel) {
        OverlapLevel.VERY_HIGH -> Triple(
            Icons.Default.Warning,
            stringResource(R.string.advanced_suggest_choose_one),
            Color(0xFFD32F2F)
        )
        OverlapLevel.HIGH -> Triple(
            Icons.Default.Info,
            stringResource(R.string.advanced_suggest_compare_cost),
            OrangeAccent
        )
        OverlapLevel.MEDIUM -> Triple(
            Icons.Default.CheckCircle,
            stringResource(R.string.advanced_suggest_both_ok),
            GreenPositive
        )
        OverlapLevel.LOW -> Triple(
            Icons.Default.ThumbUp,
            stringResource(R.string.advanced_suggest_excellent_pair),
            GreenPositive
        )
    }

    Surface(
        color = suggestionColor.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = suggestionColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    stringResource(R.string.advanced_investment_suggestion),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = suggestionColor
                )
                Text(
                    suggestion,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
internal fun EtfCorrelationRow(pair: EtfCorrelationCache) {
    val overlapColor = when {
        pair.overlapRatio > 0.7 -> Color(0xFFD32F2F)
        pair.overlapRatio > 0.4 -> OrangeAccent
        else -> GreenPositive
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = overlapColor.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    pair.etf1Name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SwapVert,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        pair.etf2Name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${(pair.overlapRatio * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = overlapColor
                )
                Text(
                    stringResource(R.string.advanced_common_stocks, pair.commonStockCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun CorrelationLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CorrelationLegendItemExtended(
    label: String,
    color: Color,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private enum class OverlapLevel {
    VERY_HIGH,  // > 80%
    HIGH,       // > 70%
    MEDIUM,     // > 40%
    LOW         // <= 40%
}
