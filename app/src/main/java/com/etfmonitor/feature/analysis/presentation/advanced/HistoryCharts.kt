package com.etfmonitor.feature.analysis.presentation.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etfmonitor.core.database.entities.*
import kotlin.math.abs

/**
 * Advanced Dashboard Screen - History Chart Components
 * Contains MarketCapFlowHistoryCard, LiquidityHistoryCard, SectorHistoryCard
 */

/**
 * 시총 가중 흐름 히스토리 차트 (막대그래프)
 */
@Composable
internal fun MarketCapFlowHistoryCard(history: List<MarketCapFlowHistoryItem>) {
    if (history.isEmpty()) return

    SectionCard("시총가중 흐름 추이 (최근 ${history.size}일)") {
        val maxValue = history.maxOfOrNull { maxOf(abs(it.netFlow), it.inflow, it.outflow) } ?: 1.0

        Column(modifier = Modifier.fillMaxWidth()) {
            // 차트 헤더
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LegendItem("유입", GreenPositive)
                LegendItem("유출", RedNegative)
                LegendItem("순흐름", BlueAccent)
            }

            // 막대 차트
            history.takeLast(15).forEach { item ->
                HistoryBarRow(
                    date = item.date.takeLast(5),
                    netFlow = item.netFlow,
                    maxValue = maxValue
                )
            }

            // 요약 통계
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            val avgNetFlow = history.map { it.netFlow }.average()
            val positiveCount = history.count { it.netFlow > 0 }
            val totalInflow = history.sumOf { it.inflow }
            val totalOutflow = history.sumOf { it.outflow }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("평균 순흐름", "${String.format("%+.0f", avgNetFlow)}억")
                StatItem("양수일", "$positiveCount/${history.size}일")
                StatItem("총유입", "${String.format("%.0f", totalInflow)}억")
                StatItem("총유출", "${String.format("%.0f", totalOutflow)}억")
            }
        }
    }
}

@Composable
private fun HistoryBarRow(date: String, netFlow: Double, maxValue: Double) {
    val barMaxWidth = 0.7f
    val normalizedValue = (abs(netFlow) / maxValue).coerceIn(0.0, 1.0).toFloat() * barMaxWidth
    val isPositive = netFlow >= 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            date,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(40.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(modifier = Modifier.weight(1f).height(16.dp)) {
            // 중앙선
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            // 막대
            Box(
                modifier = Modifier
                    .align(if (isPositive) Alignment.CenterStart else Alignment.CenterEnd)
                    .fillMaxHeight(0.8f)
                    .fillMaxWidth(normalizedValue)
                    .offset(x = if (isPositive) 0.dp else 0.dp)
                    .padding(start = if (isPositive) (0.5f - normalizedValue / 2).coerceIn(0f, 0.5f).let { it * 100 }.dp else 0.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isPositive) GreenPositive else RedNegative)
            )
        }

        Text(
            String.format("%+.0f", netFlow),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(50.dp),
            textAlign = TextAlign.End,
            color = if (isPositive) GreenPositive else RedNegative
        )
    }
}

/**
 * 유동성 분석 히스토리 차트
 */
@Composable
internal fun LiquidityHistoryCard(history: List<LiquidityAnalysis>) {
    if (history.isEmpty()) return

    SectionCard("유동성 분석 추이 (최근 ${history.size}일)") {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 테이블 헤더
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(vertical = 6.dp, horizontal = 4.dp)
            ) {
                Text("날짜", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
                Text("예탁금", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("신용", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("신호", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp), textAlign = TextAlign.Center)
            }

            // 데이터 행
            history.take(10).forEachIndexed { index, item ->
                val backgroundColor = if (index % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                val signal = try { LiquiditySignal.valueOf(item.signal) } catch (e: Exception) { LiquiditySignal.NEUTRAL }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor)
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.date.takeLast(5),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(70.dp)
                    )
                    Text(
                        formatTrillion(item.depositAmount),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        formatTrillion(item.creditAmount),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        signal.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(60.dp),
                        textAlign = TextAlign.Center,
                        color = when (signal) {
                            LiquiditySignal.BULLISH_LIQUIDITY -> GreenPositive
                            LiquiditySignal.BEARISH_LEVERAGE -> RedNegative
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            // 예탁금 추이 그래프
            if (history.size >= 3) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("예탁금 추이", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                SimpleLiquiditySparkline(
                    data = history.reversed().map { it.depositAmount },
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                )
            }
        }
    }
}

@Composable
private fun SimpleLiquiditySparkline(data: List<Double>, modifier: Modifier) {
    if (data.isEmpty()) return

    val minValue = data.minOrNull() ?: 0.0
    val maxValue = data.maxOrNull() ?: 1.0
    val range = (maxValue - minValue).coerceAtLeast(1.0)

    Row(
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        data.forEach { value ->
            val height = ((value - minValue) / range).coerceIn(0.1, 1.0).toFloat()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(height)
                    .padding(horizontal = 1.dp)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(BlueAccent.copy(alpha = 0.7f))
            )
        }
    }
}

/**
 * 섹터 Fear & Greed 히스토리 차트
 */
@Composable
internal fun SectorHistoryCard(sectorHistory: Map<String, List<SectorAnalysis>>) {
    if (sectorHistory.isEmpty()) return

    SectionCard("섹터별 심리 추이") {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 섹터별 히스토리 표시 (상위 6개 섹터만)
            sectorHistory.entries
                .filter { it.value.size >= 2 }
                .sortedByDescending { it.value.firstOrNull()?.fearGreedValue ?: 0.0 }
                .take(6)
                .forEach { (sector, history) ->
                    SectorHistoryRow(sector, history)
                }

            // 전체 평균 추이
            if (sectorHistory.values.any { it.size >= 2 }) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                val dates = sectorHistory.values.flatten().map { it.date }.distinct().sorted().takeLast(7)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("날짜별 평균", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    dates.forEach { date ->
                        val avgValue = sectorHistory.values.flatten()
                            .filter { it.date == date }
                            .map { it.fearGreedValue }
                            .takeIf { it.isNotEmpty() }
                            ?.average() ?: 0.5

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(getFearGreedColor(avgValue)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${(avgValue * 100).toInt()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                date.takeLast(2),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectorHistoryRow(sectorCode: String, history: List<SectorAnalysis>) {
    val sectorName = SectorMapping.getSectorDisplayName(sectorCode)
    val latestValue = history.firstOrNull()?.fearGreedValue ?: 0.5
    val previousValue = history.getOrNull(1)?.fearGreedValue ?: latestValue
    val change = latestValue - previousValue

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            sectorName,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(80.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // 미니 히스토리 바
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            history.reversed().takeLast(7).forEach { analysis ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(getFearGreedColor(analysis.fearGreedValue))
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 현재값 및 변화
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${(latestValue * 100).toInt()}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = getFearGreedColor(latestValue)
            )
            if (abs(change) > 0.01) {
                Text(
                    String.format("%+.0f", change * 100),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (change > 0) GreenPositive else RedNegative
                )
            }
        }
    }
}
