package com.etfmonitor.ui.screens.advanced

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etfmonitor.R
import com.etfmonitor.database.entities.*

/**
 * Advanced Dashboard Screen - Sector Fear & Greed Tab
 * Contains sector-level fear and greed analysis
 */

@Composable
internal fun SectorFearGreedTab(
    data: AdvancedDashboardData,
    sectorHistory: Map<String, List<SectorAnalysis>> = emptyMap()
) {
    val allSectors = data.allSectorAnalyses.sortedByDescending { it.fearGreedValue }

    if (allSectors.isEmpty()) {
        EmptyStateCard(stringResource(R.string.advanced_needs_sector_data), Icons.Default.PieChart)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 섹터 히스토리
        if (sectorHistory.isNotEmpty()) {
            item { SectorHistoryCard(sectorHistory) }
        }

        // 전체 시장 심리
        item {
            val avgFearGreed = allSectors.map { it.fearGreedValue }.average()
            val sentiment = when {
                avgFearGreed > 0.8 -> stringResource(R.string.advanced_extreme_greed)
                avgFearGreed > 0.6 -> stringResource(R.string.advanced_greed_level)
                avgFearGreed > 0.4 -> stringResource(R.string.advanced_neutral_level)
                avgFearGreed > 0.2 -> stringResource(R.string.advanced_fear_level)
                else -> stringResource(R.string.advanced_extreme_fear)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = getFearGreedColor(avgFearGreed).copy(alpha = 0.15f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.advanced_overall_market_sentiment), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        String.format("%.0f", avgFearGreed * 100),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = getFearGreedColor(avgFearGreed)
                    )
                    Text(sentiment, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        // 섹터 히트맵
        item {
            SectionCard(stringResource(R.string.advanced_sector_fg_index)) {
                allSectors.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { sector ->
                            SectorHeatmapItem(
                                modifier = Modifier.weight(1f),
                                sector = sector
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // 탐욕 상위 섹터
        val greedSectors = allSectors.filter { it.fearGreedValue > 0.6 }
        if (greedSectors.isNotEmpty()) {
            item {
                SectionCard(stringResource(R.string.advanced_greed_sectors), GreenPositive) {
                    greedSectors.take(5).forEachIndexed { idx, sector ->
                        SectorDetailRow(idx + 1, sector)
                    }
                }
            }
        }

        // 공포 상위 섹터
        val fearSectors = allSectors.filter { it.fearGreedValue < 0.4 }.sortedBy { it.fearGreedValue }
        if (fearSectors.isNotEmpty()) {
            item {
                SectionCard(stringResource(R.string.advanced_fear_sectors), RedNegative) {
                    fearSectors.take(5).forEachIndexed { idx, sector ->
                        SectorDetailRow(idx + 1, sector)
                    }
                }
            }
        }

        // 섹터 로테이션 신호
        if (data.sectorRotationSignals.isNotEmpty()) {
            item {
                SectionCard(stringResource(R.string.advanced_sector_rotation_signal)) {
                    data.sectorRotationSignals.take(3).forEach { signal ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                signal.description,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${(signal.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SectorHeatmapItem(modifier: Modifier, sector: SectorAnalysis) {
    val color = getFearGreedColor(sector.fearGreedValue)

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                sector.sectorName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                String.format("%.0f", sector.fearGreedValue * 100),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
internal fun SectorDetailRow(rank: Int, sector: SectorAnalysis) {
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
            Text(sector.sectorName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                stringResource(R.string.advanced_sector_new_removed, sector.newEntries, sector.removals),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            String.format("%.0f", sector.fearGreedValue * 100),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = getFearGreedColor(sector.fearGreedValue)
        )
    }
}
