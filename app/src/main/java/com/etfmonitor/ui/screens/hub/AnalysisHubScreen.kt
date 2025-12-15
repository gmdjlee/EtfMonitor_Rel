package com.etfmonitor.ui.screens.hub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.etfmonitor.ui.components.TabNavigationBar
import com.etfmonitor.ui.screens.aianalysis.NewAIAnalysisViewModel
import com.etfmonitor.ui.screens.aianalysis.NewAIAnalysisState
import com.etfmonitor.ui.screens.prediction.PredictionViewModel
import com.etfmonitor.ui.screens.prediction.PredictionState
import com.etfmonitor.ui.screens.advanced.AdvancedDashboardViewModel
import com.etfmonitor.ui.screens.advanced.AdvancedDashboardState
import kotlinx.coroutines.launch

/**
 * Analysis Hub Screen - 분석
 *
 * Consolidates:
 * - AI 시장 분석
 * - ML 주가 예측
 * - 고급 분석
 */

private val ANALYSIS_TABS = listOf("AI 분석", "ML 예측", "고급 분석")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisHubScreen(
    navController: NavHostController,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToOscillator: (String) -> Unit,
    aiAnalysisViewModel: NewAIAnalysisViewModel = hiltViewModel(),
    predictionViewModel: PredictionViewModel = hiltViewModel(),
    advancedDashboardViewModel: AdvancedDashboardViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { ANALYSIS_TABS.size })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        HubHeader(
            title = "분석",
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onSettingsClick = onNavigateToSettings
        )

        // Tab Navigation
        TabNavigationBar(
            tabs = ANALYSIS_TABS,
            selectedIndex = pagerState.currentPage,
            onTabSelected = { index ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(index)
                }
            }
        )

        // Pager Content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> AIAnalysisHubContent(
                    viewModel = aiAnalysisViewModel,
                    onNavigateToOscillator = onNavigateToOscillator
                )
                1 -> PredictionHubContent(viewModel = predictionViewModel)
                2 -> AdvancedDashboardHubContent(
                    viewModel = advancedDashboardViewModel,
                    navController = navController
                )
            }
        }
    }
}

@Composable
private fun AIAnalysisHubContent(
    viewModel: NewAIAnalysisViewModel,
    onNavigateToOscillator: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val isApiKeyConfigured by viewModel.isApiKeyConfigured.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Loading states
        val isLoading = state is NewAIAnalysisState.AnalyzingCorrelation ||
                state is NewAIAnalysisState.AnalyzingFull ||
                state is NewAIAnalysisState.InterpretingWithAI

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (!isApiKeyConfigured) {
            // API key required
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "API 키 설정 필요",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "AI 분석을 사용하려면 설정에서 Claude 또는 Gemini API 키를 설정해주세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        } else {
            // Show latest analysis or prompt to analyze
            if (analysisResult != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "상관관계 분석 결과",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        analysisResult?.aiResult?.let { aiResult ->
                            Text(
                                text = aiResult.signal,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "신뢰도: ${(aiResult.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        } ?: run {
                            Text(
                                text = "상관관계 분석이 완료되었습니다. AI 해석을 실행하세요.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            } else {
                // No analysis yet
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "AI 분석 실행",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "AI가 시장 데이터를 분석하여 인사이트를 제공합니다",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Action button
            Button(
                onClick = { viewModel.runFullAnalysis() },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                enabled = isApiKeyConfigured && !isLoading
            ) {
                Icon(Icons.Default.Psychology, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("분석 실행")
            }
        }

        // Error state
        if (state is NewAIAnalysisState.Error) {
            val error = state as NewAIAnalysisState.Error
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun PredictionHubContent(
    viewModel: PredictionViewModel
) {
    val state by viewModel.state.collectAsState()
    val predictions by viewModel.predictions.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (state) {
            is PredictionState.Loading -> {
                val loadingState = state as PredictionState.Loading
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = loadingState.message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            is PredictionState.HasPredictions, is PredictionState.Success -> {
                val count = when (state) {
                    is PredictionState.HasPredictions -> (state as PredictionState.HasPredictions).count
                    is PredictionState.Success -> (state as PredictionState.Success).predictedCount
                    else -> predictions.size
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "주가 예측 결과",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${count}개 종목 예측 완료",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                // Show top predictions
                predictions.take(5).forEach { prediction ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = prediction.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Text(
                                    text = prediction.ticker,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = prediction.status,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (prediction.confidence > 0.7) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.secondary
                                    }
                                )
                                Text(
                                    text = "${(prediction.confidence * 100).toInt()}% 신뢰도",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            is PredictionState.NoPredictions, is PredictionState.Initial -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "ML 주가 예측",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "머신러닝을 사용하여 주가 방향을 예측합니다",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                Button(
                    onClick = { viewModel.runPrediction() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("예측 실행")
                }
            }
            is PredictionState.Error -> {
                val error = state as PredictionState.Error
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = error.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedDashboardHubContent(
    viewModel: AdvancedDashboardViewModel,
    navController: NavHostController
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (state) {
            is AdvancedDashboardState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is AdvancedDashboardState.Success -> {
                val data = (state as AdvancedDashboardState.Success).data

                // Overall Signal Card
                OverallSignalSummaryCard(data.date, data.overallSignal)

                // Market Cap Flow Summary
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "시가총액 흐름",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        data.marketCapFlow?.let { flow ->
                            val flowText = if (flow.netFlow >= 0) "+${flow.netFlow}억" else "${flow.netFlow}억"
                            Text(
                                text = "순 유입: $flowText",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (flow.netFlow >= 0)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "유입: ${flow.totalInflow}억 / 유출: ${flow.totalOutflow}억",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        } ?: Text(
                            text = "데이터 없음",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }

                // Divergence Analysis
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "수급 다이버전스",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        data.divergenceSummary?.let { divergence ->
                            Text(
                                text = divergence.marketSentiment.displayName,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "외국인 매수: ${divergence.foreignBullishCount} / 기관 매수: ${divergence.institutionBullishCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        } ?: Text(
                            text = "데이터 없음",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }

                // Liquidity Analysis
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "유동성 분석",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        data.liquidityAnalysis?.let { liquidity ->
                            val signalName = try {
                                com.etfmonitor.database.entities.LiquiditySignal.valueOf(liquidity.signal).displayName
                            } catch (e: Exception) {
                                liquidity.signal
                            }
                            Text(
                                text = signalName,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "예탁금: ${String.format("%,.0f", liquidity.depositAmount)}억 / 신용잔고: ${String.format("%,.0f", liquidity.creditAmount)}억",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        } ?: Text(
                            text = "데이터 없음",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }

                // Sector Summary
                if (data.topGreedSectors.isNotEmpty() || data.topFearSectors.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Text(
                                text = "섹터 심리",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            if (data.topGreedSectors.isNotEmpty()) {
                                Text(
                                    text = "탐욕 섹터",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                data.topGreedSectors.forEach { sector ->
                                    Text(
                                        text = "${sector.sectorName}: ${(sector.fearGreedValue * 100).toInt()}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            if (data.topFearSectors.isNotEmpty()) {
                                Text(
                                    text = "공포 섹터",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                data.topFearSectors.forEach { sector ->
                                    Text(
                                        text = "${sector.sectorName}: ${(sector.fearGreedValue * 100).toInt()}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Refresh button
                Button(
                    onClick = { viewModel.refresh() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("새로고침")
                }
            }
            is AdvancedDashboardState.Error -> {
                val error = state as AdvancedDashboardState.Error
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = error.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Retry button
                Button(
                    onClick = { viewModel.loadDashboard() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("다시 시도")
                }
            }
        }
    }
}

@Composable
private fun OverallSignalSummaryCard(date: String, signal: com.etfmonitor.ui.screens.advanced.OverallSignal) {
    val (backgroundColor, textColor) = when (signal.direction) {
        com.etfmonitor.ui.screens.advanced.SignalDirection.STRONG_BUY ->
            Pair(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
        com.etfmonitor.ui.screens.advanced.SignalDirection.BUY ->
            Pair(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), MaterialTheme.colorScheme.onPrimary)
        com.etfmonitor.ui.screens.advanced.SignalDirection.NEUTRAL ->
            Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        com.etfmonitor.ui.screens.advanced.SignalDirection.SELL ->
            Pair(MaterialTheme.colorScheme.error.copy(alpha = 0.8f), MaterialTheme.colorScheme.onError)
        com.etfmonitor.ui.screens.advanced.SignalDirection.STRONG_SELL ->
            Pair(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
    }

    val signalText = when (signal.direction) {
        com.etfmonitor.ui.screens.advanced.SignalDirection.STRONG_BUY -> "강력 매수"
        com.etfmonitor.ui.screens.advanced.SignalDirection.BUY -> "매수"
        com.etfmonitor.ui.screens.advanced.SignalDirection.NEUTRAL -> "중립"
        com.etfmonitor.ui.screens.advanced.SignalDirection.SELL -> "매도"
        com.etfmonitor.ui.screens.advanced.SignalDirection.STRONG_SELL -> "강력 매도"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = date,
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = signalText,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = textColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { signal.strength.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = textColor,
                trackColor = textColor.copy(alpha = 0.3f)
            )
            if (signal.factors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = signal.factors.joinToString(" + "),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.9f)
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "방금 전"
        diff < 3600_000 -> "${diff / 60_000}분 전"
        diff < 86400_000 -> "${diff / 3600_000}시간 전"
        else -> "${diff / 86400_000}일 전"
    }
}
