package com.etfmonitor.ui.screens.aianalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etfmonitor.core.analysis.AIStockIndicatorInterpretation
import com.etfmonitor.core.analysis.IndicatorStockCorrelation
import com.etfmonitor.core.analysis.MarketIndicatorType
import com.etfmonitor.core.analysis.SignalType
import com.etfmonitor.core.analysis.StockIndicatorCorrelationResult
import com.etfmonitor.core.analysis.StockMetricType
import com.etfmonitor.core.ui.component.StockSearchItem
import com.etfmonitor.core.ui.component.UnifiedStockSearchField
import com.etfmonitor.database.entities.AIChatMessage
import com.etfmonitor.database.entities.AIChatSession
import com.etfmonitor.database.entities.CorrelationAnalysisResult
import com.etfmonitor.database.entities.SearchHistory
import com.etfmonitor.database.entities.Stock
import com.etfmonitor.database.entities.StockIndicatorAIResult
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Shared Analysis UI Components
 *
 * These components are used by both NewAIAnalysisScreen and AnalysisHubScreen
 * to eliminate code duplication.
 */

// ========== Extension Functions ==========

/**
 * Converts signal string to SignalType
 */
fun String.toSignalType(): SignalType {
    return when (this.uppercase()) {
        "STRONG_BUY", "강력매수" -> SignalType.STRONG_BUY
        "BUY", "매수" -> SignalType.BUY
        "NEUTRAL", "중립" -> SignalType.NEUTRAL
        "SELL", "매도" -> SignalType.SELL
        "STRONG_SELL", "강력매도" -> SignalType.STRONG_SELL
        else -> SignalType.NEUTRAL
    }
}

/**
 * Converts SignalType to Korean display name
 */
fun SignalType.toKorean(): String {
    return when (this) {
        SignalType.STRONG_BUY -> "강력 매수"
        SignalType.BUY -> "매수"
        SignalType.NEUTRAL -> "중립"
        SignalType.SELL -> "매도"
        SignalType.STRONG_SELL -> "강력 매도"
    }
}

/**
 * Gets display name for indicator type
 */
fun getIndicatorDisplayName(indicatorType: String): String {
    return try {
        MarketIndicatorType.valueOf(indicatorType).displayName
    } catch (e: Exception) {
        indicatorType
    }
}

/**
 * Gets display name for stock metric type
 */
fun getMetricDisplayName(metricType: String): String {
    return try {
        StockMetricType.valueOf(metricType).displayName
    } catch (e: Exception) {
        metricType
    }
}

/**
 * Formats timestamp to relative time string
 */
fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "방금 전"
        diff < 3600_000 -> "${diff / 60_000}분 전"
        diff < 86400_000 -> "${diff / 3600_000}시간 전"
        else -> "${diff / 86400_000}일 전"
    }
}

// ========== Signal Indicator ==========

@Composable
fun SignalIndicator(
    signal: String,
    confidence: Double,
    upProbability: Double,
    downProbability: Double
) {
    val signalType = signal.toSignalType()
    val signalColor = when (signalType) {
        SignalType.STRONG_BUY -> Color(0xFF1B5E20)
        SignalType.BUY -> Color(0xFF4CAF50)
        SignalType.NEUTRAL -> Color(0xFF757575)
        SignalType.SELL -> Color(0xFFE53935)
        SignalType.STRONG_SELL -> Color(0xFFB71C1C)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = signalColor.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                signalType.toKorean(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = signalColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "신뢰도",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${String.format("%.0f", confidence * 100)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "상승 확률",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        "${String.format("%.0f", upProbability)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "하락 확률",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE53935)
                    )
                    Text(
                        "${String.format("%.0f", downProbability)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE53935)
                    )
                }
            }
        }
    }
}

// ========== Market Selector ==========

@Composable
fun AnalysisMarketSelector(
    selectedMarket: String,
    onMarketSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("KOSPI", "KOSDAQ").forEach { market ->
            FilterChip(
                selected = market == selectedMarket,
                onClick = { onMarketSelect(market) },
                label = { Text(market) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ========== Period Selector ==========

@Composable
fun TimeSeriesPeriodSelector(
    period: Int,
    onPeriodChange: (Int) -> Unit
) {
    val periodOptions = listOf(7, 14, 30, 60, 90, 180, 365)

    Column {
        Text(
            "분석 기간: ${period}일",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            periodOptions.forEach { days ->
                FilterChip(
                    selected = period == days,
                    onClick = { onPeriodChange(days) },
                    label = {
                        Text(
                            when (days) {
                                7 -> "1주"
                                14 -> "2주"
                                30 -> "1개월"
                                60 -> "2개월"
                                90 -> "3개월"
                                180 -> "6개월"
                                365 -> "1년"
                                else -> "${days}일"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }
    }
}

// ========== Error Card ==========

@Composable
fun AnalysisErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "닫기"
                )
            }
        }
    }
}

// ========== Analysis Buttons ==========

@Composable
fun AnalysisButtons(
    state: NewAIAnalysisState,
    isApiKeyConfigured: Boolean,
    hasCorrelationResult: Boolean,
    onRunCorrelation: () -> Unit,
    onRunFullAnalysis: () -> Unit,
    onInterpretWithAI: () -> Unit
) {
    val isLoading = state is NewAIAnalysisState.AnalyzingCorrelation ||
            state is NewAIAnalysisState.AnalyzingFull ||
            state is NewAIAnalysisState.InterpretingWithAI

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onRunCorrelation,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (state is NewAIAnalysisState.AnalyzingCorrelation) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Analytics, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("상관관계 분석 (로컬)")
        }

        Button(
            onClick = onRunFullAnalysis,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && isApiKeyConfigured
        ) {
            if (state is NewAIAnalysisState.AnalyzingFull) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.Psychology, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("전체 분석 (상관관계 + AI)")
        }

        if (hasCorrelationResult && isApiKeyConfigured) {
            TextButton(
                onClick = onInterpretWithAI,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (state is NewAIAnalysisState.InterpretingWithAI) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI 해석 추가")
            }
        }
    }
}

// ========== Stock Indicator Correlation Buttons ==========

@Composable
fun StockIndicatorCorrelationButtons(
    state: NewAIAnalysisState,
    isApiKeyConfigured: Boolean,
    hasSelectedStock: Boolean,
    hasCorrelationResult: Boolean,
    onRunAnalysis: () -> Unit,
    onRunFullAnalysis: () -> Unit,
    onInterpretWithAI: () -> Unit
) {
    val isLoading = state is NewAIAnalysisState.AnalyzingStockIndicatorCorrelation ||
            state is NewAIAnalysisState.AnalyzingStockIndicatorCorrelationFull ||
            state is NewAIAnalysisState.InterpretingStockIndicatorCorrelation

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onRunAnalysis,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && hasSelectedStock
        ) {
            if (state is NewAIAnalysisState.AnalyzingStockIndicatorCorrelation) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Analytics, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("상관관계 분석 (로컬)")
        }

        Button(
            onClick = onRunFullAnalysis,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && isApiKeyConfigured && hasSelectedStock
        ) {
            if (state is NewAIAnalysisState.AnalyzingStockIndicatorCorrelationFull) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.Psychology, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("전체 분석 (+ AI)")
        }

        if (hasCorrelationResult && isApiKeyConfigured) {
            TextButton(
                onClick = onInterpretWithAI,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (state is NewAIAnalysisState.InterpretingStockIndicatorCorrelation) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI 해석 추가")
            }
        }
    }
}

// ========== Stock Search Section ==========

@Composable
fun StockSearchSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<Pair<String, String>>,
    isSearching: Boolean,
    selectedStock: Pair<String, String>?,
    detectedMarket: String? = null,
    searchHistory: List<SearchHistory> = emptyList(),
    onSelectStock: (String, String) -> Unit,
    onClearStock: () -> Unit,
    onSelectFromHistory: ((String, String) -> Unit)? = null
) {
    Column {
        Text(
            "종목 선택",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (selectedStock != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                selectedStock.second,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            if (detectedMarket != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = if (detectedMarket == "KOSPI")
                                        Color(0xFF1976D2).copy(alpha = 0.15f)
                                    else
                                        Color(0xFFE64A19).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = detectedMarket,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (detectedMarket == "KOSPI")
                                            Color(0xFF1976D2)
                                        else
                                            Color(0xFFE64A19),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            "${selectedStock.first}${if (detectedMarket != null) " • ${detectedMarket} 지표로 분석" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(onClick = onClearStock) {
                        Icon(Icons.Default.Close, contentDescription = "선택 해제")
                    }
                }
            }
        } else {
            UnifiedStockSearchField(
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                searchResults = searchResults.map { (ticker, name) ->
                    StockSearchItem(ticker = ticker, name = name, market = "")
                },
                searchHistory = searchHistory,
                isSearching = isSearching,
                placeholder = "종목명 또는 종목코드 입력",
                onSelectStock = onSelectStock,
                onSelectFromHistory = onSelectFromHistory
            )
        }
    }
}

// ========== Stock Indicator Summary Card ==========

@Composable
fun StockIndicatorSummaryCard(
    result: StockIndicatorCorrelationResult
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
    correlations: List<IndicatorStockCorrelation>,
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
    topPositive: List<IndicatorStockCorrelation>,
    topNegative: List<IndicatorStockCorrelation>
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
    interpretation: AIStockIndicatorInterpretation
) {
    val signalType = interpretation.signal.toSignalType()
    val signalColor = when (signalType) {
        SignalType.STRONG_BUY -> Color(0xFF1B5E20)
        SignalType.BUY -> Color(0xFF4CAF50)
        SignalType.NEUTRAL -> Color(0xFF757575)
        SignalType.SELL -> Color(0xFFE53935)
        SignalType.STRONG_SELL -> Color(0xFFB71C1C)
    }

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
    result: CorrelationAnalysisResult,
    aiResult: com.etfmonitor.database.entities.AIAnalysisResult?
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

// ========== Chat Components ==========

@Composable
fun ChatScreen(
    messages: List<AIChatMessage>,
    isSending: Boolean,
    onSendMessage: (String) -> Unit,
    state: NewAIAnalysisState
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatMessageItem(message = message)
            }

            if (isSending) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AI가 답변 중...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (state is NewAIAnalysisState.ChatError) {
            Text(
                state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        Surface(tonalElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("메시지를 입력하세요") },
                    maxLines = 3,
                    enabled = !isSending,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank()) {
                            keyboardController?.hide()
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    })
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            keyboardController?.hide()
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !isSending
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "전송",
                        tint = if (inputText.isNotBlank() && !isSending)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: AIChatMessage) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            )
        ) {
            Text(
                message.content,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ========== Session Item ==========

@Composable
fun SessionItem(
    session: AIChatSession,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${session.market ?: "일반"} | ${session.messageCount}개 메시지",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("대화 삭제") },
            text = { Text("이 대화를 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("취소")
                }
            }
        )
    }
}

// ========== Stock Indicator AI History Item ==========

@Composable
fun StockIndicatorAIHistoryItem(
    item: StockIndicatorAIResult,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val signalColor = when (item.signal) {
                "STRONG_BUY" -> Color(0xFF4CAF50)
                "BUY" -> Color(0xFF8BC34A)
                "NEUTRAL" -> Color(0xFFFFB300)
                "SELL" -> Color(0xFFFF9800)
                "STRONG_SELL" -> Color(0xFFF44336)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(signalColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (item.signal) {
                        "STRONG_BUY", "BUY" -> Icons.Default.TrendingUp
                        "STRONG_SELL", "SELL" -> Icons.Default.TrendingDown
                        else -> Icons.Default.TrendingFlat
                    },
                    contentDescription = null,
                    tint = signalColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${item.stockName} (${item.ticker})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${item.analysisDate} | ${item.market}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.signal.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        color = signalColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "신뢰도 ${(item.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("분석 결과 삭제") },
            text = { Text("${item.stockName}의 분석 결과를 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("취소")
                }
            }
        )
    }
}
