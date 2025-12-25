package com.etfmonitor.feature.analysis.presentation.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
// PredictionAccuracy is defined in AdvancedDashboardViewModel.kt (same package)

/**
 * Advanced Dashboard Screen - Prediction Accuracy UI Components
 * Contains PredictionAccuracyCard and related components
 */

/**
 * 예측 정확도 카드 (공통)
 */
@Composable
internal fun PredictionAccuracyCard(
    title: String,
    accuracy: PredictionAccuracy?,
    modifier: Modifier = Modifier
) {
    if (accuracy == null) return

    var expanded by remember { mutableStateOf(false) }
    val hitRatePercent = (accuracy.hitRate * 100).toInt()
    val hitRateColor = when {
        hitRatePercent >= 70 -> GreenPositive
        hitRatePercent >= 50 -> OrangeAccent
        else -> RedNegative
    }

    SectionCard("$title 예측 정확도") {
        Column(modifier = modifier.fillMaxWidth()) {
            // 요약 정보
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "적중률",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${hitRatePercent}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = hitRateColor
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = GreenPositive,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "${accuracy.correctPredictions}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GreenPositive
                        )
                        Text(
                            "/",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${accuracy.totalPredictions}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "정확/전체",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 적중률 게이지
                Box(
                    modifier = Modifier.size(50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { accuracy.hitRate.toFloat() },
                        modifier = Modifier.fillMaxSize(),
                        color = hitRateColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeWidth = 6.dp
                    )
                    Text(
                        "${hitRatePercent}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 상세 보기 버튼
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "접기" else "상세 보기")
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 상세 내역
            if (expanded && accuracy.details.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 테이블 헤더
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                ) {
                    Text(
                        "날짜",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(60.dp)
                    )
                    Text(
                        "예측",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "실제",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "변동률",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(60.dp),
                        textAlign = TextAlign.End
                    )
                    Text(
                        "결과",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.Center
                    )
                }

                // 상세 내역 (최근 10건)
                accuracy.details.take(10).forEachIndexed { index, detail ->
                    val backgroundColor = if (index % 2 == 0) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(backgroundColor)
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            detail.date.takeLast(5),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(60.dp)
                        )
                        Text(
                            getPredictionDisplayName(detail.prediction),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            color = getPredictionColor(detail.prediction)
                        )
                        Text(
                            getResultDisplayName(detail.actualResult),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            color = getResultColor(detail.actualResult)
                        )
                        Text(
                            String.format("%+.2f%%", detail.actualChangeRate),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(60.dp),
                            textAlign = TextAlign.End,
                            color = if (detail.actualChangeRate >= 0) GreenPositive else RedNegative
                        )
                        Icon(
                            if (detail.isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (detail.isCorrect) GreenPositive else RedNegative,
                            modifier = Modifier
                                .width(40.dp)
                                .size(16.dp)
                        )
                    }
                }

                // 정확도 해석
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = hitRateColor.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = hitRateColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            getAccuracyInterpretation(hitRatePercent),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * 시총 가중 흐름 정확도 요약 카드 (간단 버전)
 */
@Composable
internal fun MarketCapFlowAccuracySummary(
    accuracy: PredictionAccuracy?,
    modifier: Modifier = Modifier
) {
    if (accuracy == null) return

    val hitRatePercent = (accuracy.hitRate * 100).toInt()
    val hitRateColor = when {
        hitRatePercent >= 70 -> GreenPositive
        hitRatePercent >= 50 -> OrangeAccent
        else -> RedNegative
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = hitRateColor.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "예측 적중률",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${hitRatePercent}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = hitRateColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "(${accuracy.correctPredictions}/${accuracy.totalPredictions})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 최근 5일 결과 아이콘
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                accuracy.details.take(5).forEach { detail ->
                    Icon(
                        if (detail.isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (detail.isCorrect) GreenPositive else RedNegative,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ==================== Helper Functions ====================

private fun getPredictionDisplayName(prediction: String): String = when (prediction) {
    "BUY" -> "매수"
    "SELL" -> "매도"
    "NEUTRAL" -> "중립"
    else -> prediction
}

private fun getResultDisplayName(result: String): String = when (result) {
    "UP" -> "상승"
    "DOWN" -> "하락"
    "FLAT" -> "보합"
    else -> result
}

private fun getPredictionColor(prediction: String): Color = when (prediction) {
    "BUY" -> GreenPositive
    "SELL" -> RedNegative
    else -> OrangeAccent
}

private fun getResultColor(result: String): Color = when (result) {
    "UP" -> GreenPositive
    "DOWN" -> RedNegative
    else -> OrangeAccent
}

private fun getAccuracyInterpretation(hitRate: Int): String = when {
    hitRate >= 70 -> "높은 적중률입니다. 이 지표를 신뢰할 수 있습니다."
    hitRate >= 60 -> "양호한 적중률입니다. 다른 지표와 함께 참고하세요."
    hitRate >= 50 -> "보통 수준입니다. 단독 사용보다 종합 분석을 권장합니다."
    else -> "적중률이 낮습니다. 이 지표는 참고용으로만 활용하세요."
}
