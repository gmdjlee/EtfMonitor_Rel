package com.etfmonitor.ui.screens.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etfmonitor.database.entities.*

/**
 * Advanced Dashboard Screen - Dashboard Tab
 * Contains the main dashboard overview with key metrics and summaries
 */

@Composable
internal fun DashboardTab(data: AdvancedDashboardData) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 데이터 가용성
        item { DataAvailabilityCard(data.dataAvailability) }

        // 종합 신호
        item { OverallSignalCard(data.date, data.overallSignal) }

        // 핵심 지표 3개
        item { KeyMetricsRow(data.marketCapFlow, data.divergenceSummary, data.liquidityAnalysis) }

        // 섹터 심리 요약
        if (data.topGreedSectors.isNotEmpty() || data.topFearSectors.isNotEmpty()) {
            item { SectorSentimentSummary(data.topGreedSectors, data.topFearSectors) }
        }

        // 섹터 로테이션
        if (data.sectorRotationSignals.isNotEmpty()) {
            item { SectorRotationCard(data.sectorRotationSignals) }
        }

        // ETF 중복 경고
        if (data.highOverlapEtfs.isNotEmpty()) {
            item { EtfOverlapWarningCard(data.highOverlapEtfs.take(3)) }
        }
    }
}

@Composable
internal fun DataAvailabilityCard(dataAvailability: DataAvailability) {
    var expanded by remember { mutableStateOf(false) }

    val allAvailable = dataAvailability.holdingsData.available &&
            dataAvailability.stockAnalysisData.available &&
            dataAvailability.marketDepositData.available &&
            dataAvailability.fearGreedData.available

    val availableCount = listOf(
        dataAvailability.holdingsData.available,
        dataAvailability.stockAnalysisData.available,
        dataAvailability.marketDepositData.available,
        dataAvailability.fearGreedData.available,
        dataAvailability.etfData.available
    ).count { it }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (allAvailable) Color(0xFFE8F5E9) else Color(0xFFFFF8E1)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (allAvailable) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (allAvailable) GreenPositive else OrangeAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("데이터 소스: $availableCount/5", style = MaterialTheme.typography.titleSmall)
                }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                DataSourceRow("ETF 보유종목", dataAvailability.holdingsData)
                DataSourceRow("종목 수급분석", dataAvailability.stockAnalysisData)
                DataSourceRow("시장 예탁금", dataAvailability.marketDepositData)
                DataSourceRow("Fear & Greed", dataAvailability.fearGreedData)
                DataSourceRow("ETF 목록", dataAvailability.etfData)
            }
        }
    }
}

@Composable
private fun DataSourceRow(name: String, status: DataSourceStatus) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (status.available) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (status.available) GreenPositive else RedNegative,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(name, style = MaterialTheme.typography.bodySmall)
        }
        Text(status.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun OverallSignalCard(date: String, signal: OverallSignal) {
    val (backgroundColor, textColor, icon) = when (signal.direction) {
        SignalDirection.STRONG_BUY -> Triple(Color(0xFF1B5E20), Color.White, Icons.Default.TrendingUp)
        SignalDirection.BUY -> Triple(GreenPositive, Color.White, Icons.AutoMirrored.Filled.TrendingUp)
        SignalDirection.NEUTRAL -> Triple(Color(0xFF9E9E9E), Color.White, Icons.Default.TrendingFlat)
        SignalDirection.SELL -> Triple(OrangeAccent, Color.White, Icons.Default.TrendingDown)
        SignalDirection.STRONG_SELL -> Triple(Color(0xFFD32F2F), Color.White, Icons.Default.TrendingDown)
    }

    val signalText = when (signal.direction) {
        SignalDirection.STRONG_BUY -> "강력 매수"
        SignalDirection.BUY -> "매수 우위"
        SignalDirection.NEUTRAL -> "중립"
        SignalDirection.SELL -> "매도 우위"
        SignalDirection.STRONG_SELL -> "강력 매도"
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = backgroundColor)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date, style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = textColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text(signalText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = textColor)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { signal.strength.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = textColor,
                trackColor = textColor.copy(alpha = 0.3f)
            )
            if (signal.factors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(signal.factors.joinToString(" + "), style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.9f))
            }
        }
    }
}

@Composable
internal fun KeyMetricsRow(
    flow: MarketCapWeightedFlow?,
    divergence: MarketDivergenceSummary?,
    liquidity: LiquidityAnalysis?
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "시총가중",
            value = flow?.let { "${if (it.netFlow >= 0) "+" else ""}${it.netFlow}억" } ?: "-",
            isPositive = flow?.netFlow?.let { it >= 0 }
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "수급",
            value = divergence?.marketSentiment?.displayName ?: "-",
            isPositive = divergence?.marketSentiment?.let {
                it == MarketSentimentType.CONSENSUS_BULLISH || it == MarketSentimentType.STRONG_FOREIGN_LED || it == MarketSentimentType.STRONG_INSTITUTION_LED
            }
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "유동성",
            value = liquidity?.let { try { LiquiditySignal.valueOf(it.signal).displayName } catch (e: Exception) { "-" } } ?: "-",
            isPositive = liquidity?.signal?.let { it == LiquiditySignal.BULLISH_LIQUIDITY.name }
        )
    }
}

@Composable
internal fun SectorSentimentSummary(greed: List<SectorAnalysis>, fear: List<SectorAnalysis>) {
    SectionCard("섹터 심리") {
        if (greed.isNotEmpty()) {
            Text("탐욕", style = MaterialTheme.typography.labelSmall, color = GreenPositive)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(greed) { sector ->
                    SectorChip(sector.sectorName, sector.fearGreedValue, true)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (fear.isNotEmpty()) {
            Text("공포", style = MaterialTheme.typography.labelSmall, color = RedNegative)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(fear) { sector ->
                    SectorChip(sector.sectorName, sector.fearGreedValue, false)
                }
            }
        }
    }
}

@Composable
private fun SectorChip(name: String, value: Double, isGreed: Boolean) {
    val color = if (isGreed) GreenPositive else RedNegative
    Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.15f)) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodySmall, color = color)
            Spacer(modifier = Modifier.width(4.dp))
            Text(String.format("%.0f", value * 100), style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
        }
    }
}

@Composable
internal fun SectorRotationCard(signals: List<SectorRotationSignal>) {
    SectionCard("섹터 로테이션") {
        signals.take(3).forEach { signal ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(signal.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("${(signal.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
internal fun EtfOverlapWarningCard(overlaps: List<EtfCorrelationCache>) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ETF 중복 경고", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            overlaps.forEach { o ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${o.etf1Name} ↔ ${o.etf2Name}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${(o.overlapRatio * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = OrangeAccent)
                }
            }
        }
    }
}
