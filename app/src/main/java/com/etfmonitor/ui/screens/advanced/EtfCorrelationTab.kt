package com.etfmonitor.ui.screens.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etfmonitor.database.entities.*

/**
 * Advanced Dashboard Screen - ETF Correlation Tab
 * Contains ETF overlap and correlation analysis
 */

@Composable
internal fun EtfCorrelationTab(data: AdvancedDashboardData) {
    val overlaps = data.highOverlapEtfs

    if (overlaps.isEmpty()) {
        EmptyStateCard("ETF 상관관계 분석 데이터가 필요합니다", Icons.Default.GridView)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 높은 중복률 ETF 쌍
        item {
            SectionCard("높은 중복률 ETF 쌍") {
                Text(
                    "중복률 70% 이상인 ETF 쌍은 분산 효과가 낮습니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                overlaps.take(10).forEach { pair ->
                    EtfCorrelationRow(pair)
                }
            }
        }

        // 분산 투자 권고
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
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = OrangeAccent
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "분산 투자 권고",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "80% 이상 중복되는 ETF가 있습니다. 동일 종목에 과도하게 투자될 수 있으니 포트폴리오 재검토를 권장합니다.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // 상관관계 범례
        item {
            SectionCard("상관관계 범례") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CorrelationLegendItem("높음 >70%", Color(0xFFD32F2F))
                    CorrelationLegendItem("보통 40-70%", OrangeAccent)
                    CorrelationLegendItem("낮음 <40%", GreenPositive)
                }
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
                    "공통 ${pair.commonStockCount}종목",
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
