package com.etfmonitor.ui.screens.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etfmonitor.database.entities.*

/**
 * Advanced Dashboard Screen - Divergence Tab
 * Contains supply/demand divergence analysis between foreign and institutional investors
 */

@Composable
internal fun DivergenceTab(data: AdvancedDashboardData) {
    val divergence = data.divergenceSummary

    if (divergence == null) {
        EmptyStateCard("종목 수급 분석 데이터가 필요합니다", Icons.Default.CompareArrows)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 시장 심리 카드
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("시장 심리", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        divergence.marketSentiment.displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // 분포 현황
        item {
            SectionCard("수급 분포") {
                val total = divergence.foreignBullishCount + divergence.institutionBullishCount +
                        divergence.alignedBullishCount + divergence.alignedBearishCount + divergence.neutralCount

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DivergenceStatItem("외국인↑", divergence.foreignBullishCount, BlueAccent)
                    DivergenceStatItem("기관↑", divergence.institutionBullishCount, OrangeAccent)
                    DivergenceStatItem("동반↑", divergence.alignedBullishCount, GreenPositive)
                    DivergenceStatItem("동반↓", divergence.alignedBearishCount, RedNegative)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 분포 막대
                Row(
                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))
                ) {
                    val items = listOf(
                        divergence.foreignBullishCount to BlueAccent,
                        divergence.institutionBullishCount to OrangeAccent,
                        divergence.alignedBullishCount to GreenPositive,
                        divergence.alignedBearishCount to RedNegative,
                        divergence.neutralCount to Color.Gray
                    )
                    items.forEach { (count, color) ->
                        if (count > 0 && total > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(count.toFloat() / total)
                                    .fillMaxHeight()
                                    .background(color)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "총 $total 종목 분석",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        }

        // 외국인 강세 종목
        if (divergence.topForeignBullish.isNotEmpty()) {
            item {
                SectionCard("외국인 강세 종목 (기관 매도)", BlueAccent) {
                    divergence.topForeignBullish.take(5).forEach { stock ->
                        DivergenceStockRow(stock)
                    }
                }
            }
        }

        // 기관 강세 종목
        if (divergence.topInstitutionBullish.isNotEmpty()) {
            item {
                SectionCard("기관 강세 종목 (외국인 매도)", OrangeAccent) {
                    divergence.topInstitutionBullish.take(5).forEach { stock ->
                        DivergenceStockRow(stock)
                    }
                }
            }
        }
    }
}

@Composable
internal fun DivergenceStatItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun DivergenceStockRow(stock: SupplyDemandDivergence) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stock.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "외국인: ${formatAmount(stock.foreign5d / 100)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = BlueAccent
                )
                Text(
                    "기관: ${formatAmount(stock.institution5d / 100)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrangeAccent
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                String.format("%.2f", stock.divergenceScore),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text("Divergence", style = MaterialTheme.typography.labelSmall)
        }
    }
}
