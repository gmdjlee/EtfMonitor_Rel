package com.etfmonitor.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.database.entities.HoldingStatus

/**
 * Statistics Screen - Main Entry Point
 * Shows comprehensive ETF statistics with multiple tabs:
 * - 금액 순위 (Amount Ranking)
 * - 신규 편입 (New Stocks)
 * - 제외 (Removed Stocks)
 * - 비중 증가 (Increased Stocks)
 * - 비중 감소 (Decreased Stocks)
 * - 원화예금 (Cash Deposit)
 * - 분석 (Analysis)
 *
 * Tab components are split into separate files:
 * - RankingTab.kt: AmountRankingTab, AmountRankingCard, SortableHeaderText
 * - StockChangeTab.kt: StockChangeTab, StockChangeCard, StatusBadge, WeightInfo, ChangeInfo
 * - CashDepositTab.kt: CashDepositTrendTab, CashDepositSummaryCard, CashDepositChartCard
 * - AnalysisTab.kt: StockAnalysisTab, StockAnalysisSummaryCard, StockAnalysisStatisticsCard
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit,
    onStockClick: (String) -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val dates by viewModel.dates.collectAsState()
    val amountRanking by viewModel.amountRanking.collectAsState()
    val newStocks by viewModel.newStocks.collectAsState()
    val removedStocks by viewModel.removedStocks.collectAsState()
    val increasedStocks by viewModel.increasedStocks.collectAsState()
    val decreasedStocks by viewModel.decreasedStocks.collectAsState()
    val cashDepositTrend by viewModel.cashDepositTrend.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // 종목 분석 상태
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("금액 순위", "신규 편입", "제외", "비중 증가", "비중 감소", "원화예금", "분석")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "전체 통계",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        dates?.let { (prev, curr) ->
                            Text(
                                "$prev → $curr",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                }
            } else {
                when (selectedTab) {
                    0 -> AmountRankingTab(amountRanking, viewModel, onStockClick)
                    1 -> StockChangeTab(newStocks, HoldingStatus.NEW, onStockClick)
                    2 -> StockChangeTab(removedStocks, HoldingStatus.REMOVED, onStockClick)
                    3 -> StockChangeTab(increasedStocks, HoldingStatus.INCREASE, onStockClick)
                    4 -> StockChangeTab(decreasedStocks, HoldingStatus.DECREASE, onStockClick)
                    5 -> CashDepositTrendTab(cashDepositTrend)
                    6 -> StockAnalysisTab(
                        searchQuery = searchQuery,
                        searchResults = searchResults,
                        analysisResult = analysisResult,
                        isAnalyzing = isAnalyzing,
                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                        onSearchAndAnalyze = { viewModel.searchAndAnalyze(it) },
                        onStockSelect = { viewModel.analyzeStock(it) },
                        onClearAnalysis = { viewModel.clearAnalysis() },
                        onStockClick = onStockClick
                    )
                }
            }
        }
    }
}
