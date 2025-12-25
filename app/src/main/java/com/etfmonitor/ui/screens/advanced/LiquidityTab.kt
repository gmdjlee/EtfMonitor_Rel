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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etfmonitor.R
import com.etfmonitor.core.database.entities.*

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
        EmptyStateCard(stringResource(R.string.advanced_needs_deposit_data), Icons.Default.AccountBalance)
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
            item { PredictionAccuracyCard(stringResource(R.string.advanced_liquidity_signal), accuracy) }
        }

        // 핵심 지표 카드
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LiquidityMetricCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.advanced_deposit),
                    value = formatTrillion(liquidity.depositAmount),
                    change = liquidity.depositChange,
                    isPositiveGood = true
                )
                LiquidityMetricCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.advanced_credit_balance),
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
                    title = stringResource(R.string.advanced_liquidity_signal),
                    value = signal.displayName,
                    color = when (signal) {
                        LiquiditySignal.BULLISH_LIQUIDITY -> GreenPositive
                        LiquiditySignal.BEARISH_LEVERAGE -> RedNegative
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                SignalCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.advanced_leverage_risk),
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
            SectionCard(stringResource(R.string.advanced_ratio_analysis)) {
                RatioProgressItem(
                    title = stringResource(R.string.advanced_deposit_to_marketcap),
                    value = liquidity.depositToMarketCapRatio,
                    maxValue = 5.0,
                    suffix = "%",
                    color = GreenPositive
                )

                Spacer(modifier = Modifier.height(16.dp))

                RatioProgressItem(
                    title = stringResource(R.string.advanced_credit_to_deposit),
                    value = liquidity.creditToDepositRatio,
                    maxValue = 100.0,
                    suffix = "%",
                    color = when {
                        liquidity.creditToDepositRatio > 50 -> RedNegative
                        liquidity.creditToDepositRatio > 30 -> OrangeAccent
                        else -> GreenPositive
                    },
                    thresholds = listOf(30.0 to stringResource(R.string.advanced_normal), 50.0 to stringResource(R.string.advanced_caution))
                )
            }
        }

        // 역사적 백분위
        item {
            SectionCard(stringResource(R.string.advanced_historical_position)) {
                val percentile = (100 - liquidity.historicalPercentile).toInt()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.advanced_current_liquidity), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.advanced_top_percentile, percentile),
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
                    Text(stringResource(R.string.advanced_low), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.advanced_high), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 시장 시총
        item {
            SectionCard(stringResource(R.string.advanced_market_cap_total)) {
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
