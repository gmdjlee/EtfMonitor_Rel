package com.etfmonitor.feature.analysis.presentation.aianalysis

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etfmonitor.core.analysis.MarketIndicatorType
import com.etfmonitor.core.analysis.StockMetricType
import com.etfmonitor.core.network.ai.SignalType
import com.etfmonitor.core.database.entities.SearchHistory
import com.etfmonitor.core.ui.component.StockSearchItem
import com.etfmonitor.core.ui.component.UnifiedStockSearchField

/**
 * Shared Analysis UI Components
 *
 * This file contains:
 * - Extension functions for signal type conversion
 * - Display name helpers for indicators and metrics
 * - SignalIndicator component
 * - Market and Period selectors
 * - Analysis buttons
 * - Stock search section
 *
 * Result cards are in AnalysisResultCards.kt
 * Chat components are in ChatComponents.kt
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
 * Gets color for SignalType
 */
fun getSignalColor(signalType: SignalType): Color {
    return when (signalType) {
        SignalType.STRONG_BUY -> Color(0xFF1B5E20)
        SignalType.BUY -> Color(0xFF4CAF50)
        SignalType.NEUTRAL -> Color(0xFF757575)
        SignalType.SELL -> Color(0xFFE53935)
        SignalType.STRONG_SELL -> Color(0xFFB71C1C)
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
    val signalColor = getSignalColor(signalType)

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
