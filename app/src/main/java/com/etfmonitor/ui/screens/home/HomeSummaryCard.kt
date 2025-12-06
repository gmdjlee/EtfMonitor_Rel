package com.etfmonitor.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.etfmonitor.ui.theme.*

/**
 * Home Screen Summary Card
 * Displays market summary including deposit changes, Fear & Greed index, and market status
 */

@Composable
internal fun SummaryCard(summary: HomeSummary) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.extendedShapes.card,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Text(
                "시장 현황",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.spacing.small))

            // 증시 자금 동향
            if (summary.depositChange != null || summary.creditChange != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "증시 자금",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        summary.depositChange?.let {
                            Text(
                                "예탁금: ${formatChange(it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = getChangeColor(it)
                            )
                        }
                        summary.creditChange?.let {
                            Text(
                                "신용잔고: ${formatChange(it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = getChangeColor(it)
                            )
                        }
                    }
                }
            }

            // Fear & Greed Index
            if (summary.kospiFearGreed != null || summary.kosdaqFearGreed != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Fear & Greed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        summary.kospiFearGreed?.let {
                            Text(
                                "KOSPI: ${String.format("%.2f", it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = getFearGreedColor(it)
                            )
                        }
                        summary.kosdaqFearGreed?.let {
                            Text(
                                "KOSDAQ: ${String.format("%.2f", it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = getFearGreedColor(it)
                            )
                        }
                    }
                }
            }

            // 시장 과매수/과매도
            if (summary.kospiStatus != null || summary.kosdaqStatus != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "시장 상태",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        summary.kospiStatus?.let { status ->
                            Text(
                                "KOSPI: ${getStatusText(status)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = getStatusColor(status)
                            )
                        }
                        summary.kosdaqStatus?.let { status ->
                            Text(
                                "KOSDAQ: ${getStatusText(status)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = getStatusColor(status)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun formatChange(value: Double): String {
    val sign = if (value > 0) "+" else ""
    // 데이터가 이미 억원 단위이므로 그대로 사용
    return "$sign${String.format("%.0f", value)}억"
}

@Composable
internal fun getChangeColor(value: Double): Color {
    return when {
        value > 0 -> MaterialTheme.colorScheme.error  // 증가 = 빨강
        value < 0 -> MaterialTheme.colorScheme.primary  // 감소 = 파랑
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
internal fun getFearGreedColor(value: Double): Color {
    // Oscillator 값 기준 (-100 ~ 100 범위)
    return when {
        value >= 20 -> MaterialTheme.colorScheme.error  // Greed (상승 모멘텀)
        value <= -20 -> MaterialTheme.colorScheme.primary  // Fear (하락 모멘텀)
        else -> MaterialTheme.colorScheme.onSurface  // Neutral
    }
}

@Composable
internal fun getStatusText(status: String): String {
    return when (status) {
        "Overbought" -> "과매수"
        "Oversold" -> "과매도"
        else -> "중립"
    }
}

@Composable
internal fun getStatusColor(status: String): Color {
    return when (status) {
        "Overbought" -> MaterialTheme.colorScheme.error
        "Oversold" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
}
