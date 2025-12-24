package com.etfmonitor.feature.home.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.etfmonitor.R
import com.etfmonitor.core.ui.theme.*
import com.etfmonitor.feature.home.domain.model.HomeSummary

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
                stringResource(R.string.home_market_status),
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
                        stringResource(R.string.home_market_fund),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        summary.depositChange?.let {
                            Text(
                                stringResource(R.string.home_deposit_format, formatChange(it)),
                                style = MaterialTheme.typography.bodySmall,
                                color = getChangeColor(it)
                            )
                        }
                        summary.creditChange?.let {
                            Text(
                                stringResource(R.string.home_credit_format, formatChange(it)),
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
                        stringResource(R.string.label_fear_greed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        summary.kospiFearGreed?.let {
                            Text(
                                stringResource(R.string.home_kospi_format, String.format("%.2f", it)),
                                style = MaterialTheme.typography.bodySmall,
                                color = getFearGreedColor(it)
                            )
                        }
                        summary.kosdaqFearGreed?.let {
                            Text(
                                stringResource(R.string.home_kosdaq_format, String.format("%.2f", it)),
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
                        stringResource(R.string.home_market_state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        summary.kospiStatus?.let { status ->
                            Text(
                                stringResource(R.string.home_kospi_format, getStatusText(status)),
                                style = MaterialTheme.typography.bodySmall,
                                color = getStatusColor(status)
                            )
                        }
                        summary.kosdaqStatus?.let { status ->
                            Text(
                                stringResource(R.string.home_kosdaq_format, getStatusText(status)),
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
    // 이미 한국어로 제공되므로 그대로 반환
    return status
}

@Composable
internal fun getStatusColor(status: String): Color {
    return when (status) {
        "과매수" -> MaterialTheme.colorScheme.error
        "과매도" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
}
