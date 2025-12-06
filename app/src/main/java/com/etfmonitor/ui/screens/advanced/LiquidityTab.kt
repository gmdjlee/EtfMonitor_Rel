package com.etfmonitor.ui.screens.advanced

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etfmonitor.database.entities.*

/**
 * Advanced Dashboard Screen - Liquidity Tab
 * Contains market liquidity analysis including deposit and credit balance
 */

@Composable
internal fun LiquidityTab(
    data: AdvancedDashboardData,
    history: List<LiquidityAnalysis> = emptyList(),
    accuracy: PredictionAccuracy? = null
) {
    val liquidity = data.liquidityAnalysis

    if (liquidity == null) {
        EmptyStateCard("예탁금/시총 데이터가 필요합니다", Icons.Default.AccountBalance)
        return
    }

    val signal = try { LiquiditySignal.valueOf(liquidity.signal) } catch (e: Exception) { LiquiditySignal.NEUTRAL }
    val riskLevel = try { LeverageRiskLevel.valueOf(liquidity.riskLevel) } catch (e: Exception) { LeverageRiskLevel.MEDIUM }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 예측 정확도 카드
        if (accuracy != null) {
            item { PredictionAccuracyCard("유동성 신호", accuracy) }
        }

        // 핵심 지표 카드
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LiquidityMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "예탁금",
                    value = formatTrillion(liquidity.depositAmount),
                    change = liquidity.depositChange,
                    isPositiveGood = true
                )
                LiquidityMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "신용잔고",
                    value = formatTrillion(liquidity.creditAmount),
                    change = liquidity.creditChange,
                    isPositiveGood = false
                )
            }
        }

        // 신호 및 위험도
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SignalCard(
                    modifier = Modifier.weight(1f),
                    title = "유동성 신호",
                    value = signal.displayName,
                    color = when (signal) {
                        LiquiditySignal.BULLISH_LIQUIDITY -> GreenPositive
                        LiquiditySignal.BEARISH_LEVERAGE -> RedNegative
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                SignalCard(
                    modifier = Modifier.weight(1f),
                    title = "레버리지 위험",
                    value = riskLevel.displayName,
                    color = when (riskLevel) {
                        LeverageRiskLevel.LOW -> GreenPositive
                        LeverageRiskLevel.MEDIUM -> OrangeAccent
                        LeverageRiskLevel.HIGH -> RedNegative
                        LeverageRiskLevel.EXTREME -> Color(0xFFD32F2F)
                    }
                )
            }
        }

        // 비율 분석
        item {
            SectionCard("비율 분석") {
                RatioProgressItem(
                    title = "예탁금/시총 비율",
                    value = liquidity.depositToMarketCapRatio,
                    maxValue = 5.0,
                    suffix = "%",
                    color = GreenPositive
                )

                Spacer(modifier = Modifier.height(16.dp))

                RatioProgressItem(
                    title = "신용/예탁금 비율",
                    value = liquidity.creditToDepositRatio,
                    maxValue = 100.0,
                    suffix = "%",
                    color = when {
                        liquidity.creditToDepositRatio > 50 -> RedNegative
                        liquidity.creditToDepositRatio > 30 -> OrangeAccent
                        else -> GreenPositive
                    },
                    thresholds = listOf(30.0 to "보통", 50.0 to "주의")
                )
            }
        }

        // 역사적 백분위
        item {
            SectionCard("역사적 위치") {
                val percentile = (100 - liquidity.historicalPercentile).toInt()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("현재 유동성 수준", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "상위 $percentile%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { (percentile / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                    color = when {
                        percentile < 30 -> RedNegative
                        percentile < 70 -> OrangeAccent
                        else -> GreenPositive
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("낮음", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("높음", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 시장 시총
        item {
            SectionCard("시장 시가총액") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "${String.format("%.0f", liquidity.totalMarketCap / 10000.0)}조원",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 히스토리 데이터
        if (history.isNotEmpty()) {
            item { LiquidityHistoryCard(history) }
        }
    }
}

@Composable
internal fun LiquidityMetricCard(
    modifier: Modifier,
    title: String,
    value: String,
    change: Double,
    isPositiveGood: Boolean
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (change != 0.0) {
                val isGood = if (isPositiveGood) change > 0 else change < 0
                Text(
                    String.format("%+.0f억", change),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isGood) GreenPositive else RedNegative
                )
            }
        }
    }
}

@Composable
internal fun SignalCard(modifier: Modifier, title: String, value: String, color: Color) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
internal fun RatioProgressItem(
    title: String,
    value: Double,
    maxValue: Double,
    suffix: String,
    color: Color,
    thresholds: List<Pair<Double, String>> = emptyList()
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${String.format("%.2f", value)}$suffix",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (value / maxValue).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
