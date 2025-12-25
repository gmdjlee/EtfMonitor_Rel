package com.etfmonitor.core.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etfmonitor.R
import com.etfmonitor.core.analysis.TrendSignalCalculator
import com.etfmonitor.core.analysis.model.FearGreedState
import com.etfmonitor.core.analysis.model.TrendSignalAnalysis
import com.etfmonitor.core.analysis.model.TrendSignalData
import com.etfmonitor.core.analysis.model.TrendTradeSignal
import com.etfmonitor.core.network.python.OscillatorPyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Quick Chart Analysis Section
 *
 * 종목 추이 화면에서 빠른 차트 분석 결과를 표시하는 섹션
 * TrendSignal 분석 결과를 간략하게 보여줍니다.
 */
@Composable
fun QuickChartAnalysisSection(
    stockTicker: String,
    pyClient: OscillatorPyClient,
    modifier: Modifier = Modifier,
    onNavigateToOscillator: ((String) -> Unit)? = null
) {
    var analysisState by remember { mutableStateOf<QuickAnalysisState>(QuickAnalysisState.Loading) }
    var expanded by remember { mutableStateOf(true) }

    // Load data when ticker changes
    LaunchedEffect(stockTicker) {
        analysisState = QuickAnalysisState.Loading
        analysisState = withContext(Dispatchers.IO) {
            try {
                val trendData = pyClient.getTrendSignalData(stockTicker, days = 365, interval = "w")
                if (trendData != null && trendData.dates.isNotEmpty()) {
                    val analysis = TrendSignalCalculator.analyze(trendData)
                    QuickAnalysisState.Success(trendData, analysis)
                } else {
                    QuickAnalysisState.Error("데이터 없음")
                }
            } catch (e: Exception) {
                QuickAnalysisState.Error(e.message ?: "알 수 없는 오류")
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with expand/collapse
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ShowChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        stringResource(R.string.quick_chart_analysis_section_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "접기" else "펼치기"
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))

                when (val state = analysisState) {
                    is QuickAnalysisState.Loading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.quick_chart_analysis_loading),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    is QuickAnalysisState.Error -> {
                        Text(
                            stringResource(R.string.quick_chart_analysis_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    is QuickAnalysisState.Success -> {
                        QuickAnalysisContent(
                            analysis = state.analysis,
                            onNavigateToOscillator = onNavigateToOscillator?.let { { it(stockTicker) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAnalysisContent(
    analysis: TrendSignalAnalysis,
    onNavigateToOscillator: (() -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Signal Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "매매 신호",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SignalBadge(analysis.signal)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Trend Description
        Text(
            analysis.trendDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Key Indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IndicatorChip(
                label = "현재가",
                value = String.format("%,.0f", analysis.currentPrice)
            )
            IndicatorChip(
                label = "MA",
                value = String.format("%,.0f", analysis.maPrice)
            )
            IndicatorChip(
                label = "CMF",
                value = String.format("%.2f", analysis.cmfValue)
            )
        }

        // Fear & Greed State
        val fearGreedState = FearGreedState.fromValue(analysis.fearGreedValue)
        val fearGreedColor = getFearGreedColor(fearGreedState)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Fear & Greed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                color = fearGreedColor.copy(alpha = 0.15f),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    "${String.format("%.2f", analysis.fearGreedValue)} - ${fearGreedState.displayName}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = fearGreedColor
                )
            }
        }

        // Recommendation
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Text(
                analysis.recommendation,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        // Navigate to detail button
        if (onNavigateToOscillator != null) {
            TextButton(
                onClick = onNavigateToOscillator,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    stringResource(R.string.quick_chart_analysis_view_detail),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun SignalBadge(signal: TrendTradeSignal) {
    val (text, color) = when (signal) {
        TrendTradeSignal.STRONG_BUY -> "강력매수" to Color(0xFF4CAF50)
        TrendTradeSignal.BUY -> "매수" to Color(0xFF8BC34A)
        TrendTradeSignal.NEUTRAL -> "중립" to Color(0xFF9E9E9E)
        TrendTradeSignal.SELL -> "매도" to Color(0xFFFF9800)
        TrendTradeSignal.STRONG_SELL -> "강력매도" to Color(0xFFF44336)
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun IndicatorChip(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun getFearGreedColor(state: FearGreedState): Color = when (state) {
    FearGreedState.EXTREME_FEAR -> Color(0xFFF44336)
    FearGreedState.FEAR -> Color(0xFFFF9800)
    FearGreedState.NEUTRAL -> Color(0xFF9E9E9E)
    FearGreedState.GREED -> Color(0xFF8BC34A)
    FearGreedState.EXTREME_GREED -> Color(0xFF4CAF50)
}

private sealed class QuickAnalysisState {
    data object Loading : QuickAnalysisState()
    data class Success(val data: TrendSignalData, val analysis: TrendSignalAnalysis) : QuickAnalysisState()
    data class Error(val message: String) : QuickAnalysisState()
}
