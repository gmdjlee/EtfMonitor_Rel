package com.etfmonitor.feature.analysis.presentation.aianalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etfmonitor.feature.analysis.domain.model.AIAnalysis
import com.etfmonitor.feature.analysis.domain.model.CorrelationAnalysis
import com.etfmonitor.feature.analysis.domain.model.DetailedIndicatorCorrelation
import com.etfmonitor.feature.analysis.domain.model.StockIndicatorCorrelation
import com.etfmonitor.feature.analysis.domain.model.StockIndicatorInterpretation
import kotlin.math.abs

/**
 * Analysis Result Card Components
 *
 * Contains all card components for displaying analysis results:
 * - StockIndicatorSummaryCard
 * - CorrelationCategoryCard
 * - CorrelationBarItem
 * - TopCorrelationsCard
 * - StockIndicatorAIInterpretationCard
 * - CorrelationResultCard
 * - CorrelationItem
 * - AIInterpretationCard
 */

// ========== Stock Indicator Summary Card ==========

@Composable
fun StockIndicatorSummaryCard(
    result: StockIndicatorCorrelation
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Assessment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "상관관계 분석 결과",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "${result.stockName} (${result.ticker})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${result.market} | ${result.startDate} ~ ${result.endDate} | ${result.totalDataPoints}일",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                result.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ========== Correlation Category Card ==========

@Composable
fun CorrelationCategoryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    correlations: List<DetailedIndicatorCorrelation>,
    color: Color
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            correlations.forEach { correlation ->
                CorrelationBarItem(
                    label = getIndicatorDisplayName(correlation.indicatorType) +
                            " vs " + getMetricDisplayName(correlation.stockMetricType),
                    value = correlation.correlation,
                    leadLagDays = correlation.leadLagDays,
                    color = color
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// ========== Correlation Bar Item ==========

@Composable
fun CorrelationBarItem(
    label: String,
    value: Double,
    leadLagDays: Int = 0,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.bodySmall)
                if (leadLagDays != 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    val isLeading = leadLagDays > 0
                    Surface(
                        color = if (isLeading)
                            Color(0xFF2196F3).copy(alpha = 0.15f)
                        else
                            Color(0xFFFF9800).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isLeading)
                                    Icons.Default.TrendingUp
                                else
                                    Icons.Default.TrendingDown,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = if (isLeading) Color(0xFF2196F3) else Color(0xFFFF9800)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (isLeading)
                                    "${leadLagDays}일 선행"
                                else
                                    "${-leadLagDays}일 후행",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isLeading) Color(0xFF2196F3) else Color(0xFFFF9800),
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
            Text(
                String.format("%+.3f", value),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = when {
                    value > 0.3 -> Color(0xFF4CAF50)
                    value < -0.3 -> Color(0xFFE53935)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val barWidth = abs(value).coerceIn(0.0, 1.0).toFloat()
            val barColor = if (value >= 0) Color(0xFF4CAF50) else Color(0xFFE53935)

            Box(
                modifier = Modifier
                    .fillMaxWidth(barWidth)
                    .fillMaxHeight()
                    .align(if (value >= 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor.copy(alpha = 0.7f))
            )
        }
    }
}

// ========== Top Correlations Card ==========

@Composable
fun TopCorrelationsCard(
    topPositive: List<DetailedIndicatorCorrelation>,
    topNegative: List<DetailedIndicatorCorrelation>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFA726)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "주요 상관관계",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (topPositive.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "강한 양의 상관관계",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF4CAF50)
                )
                topPositive.take(3).forEach { correlation ->
                    Text(
                        "- ${correlation.description}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (topNegative.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "강한 음의 상관관계",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFE53935)
                )
                topNegative.take(3).forEach { correlation ->
                    Text(
                        "- ${correlation.description}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// ========== Stock Indicator AI Interpretation Card ==========

@Composable
fun StockIndicatorAIInterpretationCard(
    interpretation: StockIndicatorInterpretation
) {
    val signalType = interpretation.signal.toSignalType()
    val signalColor = getSignalColor(signalType)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "AI 상관관계 분석",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "${interpretation.name} (${interpretation.ticker}) | ${interpretation.period}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            SignalIndicator(
                signal = interpretation.signal,
                confidence = interpretation.confidence,
                upProbability = interpretation.upProbability,
                downProbability = interpretation.downProbability
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (interpretation.marketSentimentImpact.isNotBlank()) {
                Text(
                    "시장 심리 영향",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF6750A4)
                )
                Text(
                    interpretation.marketSentimentImpact,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (interpretation.fundFlowImpact.isNotBlank()) {
                Text(
                    "자금 흐름 영향",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF388E3C)
                )
                Text(
                    interpretation.fundFlowImpact,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (interpretation.etfFlowImpact.isNotBlank()) {
                Text(
                    "ETF 수급 영향",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFE64A19)
                )
                Text(
                    interpretation.etfFlowImpact,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (interpretation.keyCorrelations.isNotEmpty()) {
                Text(
                    "핵심 상관관계",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                interpretation.keyCorrelations.forEach { correlation ->
                    Text(
                        "- $correlation",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                "분석 근거",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                interpretation.reasoning,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "권장사항",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                interpretation.recommendation,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                val riskColor = when (interpretation.riskLevel) {
                    "LOW" -> Color(0xFF4CAF50)
                    "HIGH" -> Color(0xFFE53935)
                    else -> Color(0xFFFFA726)
                }
                Text(
                    "위험도: ${interpretation.riskLevel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = riskColor
                )
            }
        }
    }
}

// ========== Correlation Result Card ==========

@Composable
fun CorrelationResultCard(
    result: CorrelationAnalysis,
    aiResult: AIAnalysis?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "상관관계 분석 결과",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${result.market} | ${result.analysisDate} | ${result.periodDays}일간",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            SignalIndicator(
                signal = if (aiResult != null) aiResult.signal else result.signal,
                confidence = if (aiResult != null) aiResult.confidence else result.confidence,
                upProbability = if (aiResult != null) aiResult.upProbability else result.upProbability,
                downProbability = if (aiResult != null) aiResult.downProbability else result.downProbability
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "주요 상관관계",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            CorrelationItem("ETF 순편입 vs 지수", result.etfNetFlowCorrelation)
            CorrelationItem("원화예금 변화 vs 지수", result.cashDepositCorrelation)
            result.fearGreedCorrelation?.let {
                CorrelationItem("Fear&Greed vs 지수", it)
            }
            result.oscillatorCorrelation?.let {
                CorrelationItem("Oscillator vs 지수", it)
            }
        }
    }
}

@Composable
fun CorrelationItem(label: String, value: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            String.format("%+.3f", value),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = when {
                value > 0.3 -> Color(0xFF4CAF50)
                value < -0.3 -> Color(0xFFE53935)
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

// ========== AI Interpretation Card ==========

@Composable
fun AIInterpretationCard(
    signal: String,
    confidence: Double,
    upProbability: Double,
    downProbability: Double,
    reasoning: String,
    recommendation: String,
    riskLevel: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "AI 분석",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "분석 근거",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                reasoning,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "권장사항",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                recommendation,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                val riskColor = when (riskLevel) {
                    "LOW" -> Color(0xFF4CAF50)
                    "HIGH" -> Color(0xFFE53935)
                    else -> Color(0xFFFFA726)
                }
                Text(
                    "위험도: $riskLevel",
                    style = MaterialTheme.typography.labelSmall,
                    color = riskColor
                )
            }
        }
    }
}
