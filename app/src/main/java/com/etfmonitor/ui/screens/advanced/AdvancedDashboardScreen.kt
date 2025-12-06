package com.etfmonitor.ui.screens.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch

/**
 * Advanced Dashboard Screen - Main Entry Point
 * Provides comprehensive advanced market analysis across multiple tabs:
 * - 대시보드 (Dashboard): Overview with key metrics and signals
 * - 시총가중 (Market Cap Flow): Market cap weighted ETF flow analysis
 * - 수급분석 (Divergence): Supply/demand divergence analysis
 * - 유동성 (Liquidity): Market liquidity analysis
 * - 섹터심리 (Sector Fear & Greed): Sector-level sentiment analysis
 * - ETF상관 (ETF Correlation): ETF overlap and correlation analysis
 *
 * Tab components are split into separate files:
 * - CommonComponents.kt: Colors, utilities, shared composables
 * - DashboardTab.kt: Main dashboard overview
 * - MarketCapFlowTab.kt: Market cap flow analysis
 * - DivergenceTab.kt: Supply/demand divergence
 * - LiquidityTab.kt: Liquidity analysis
 * - SectorFearGreedTab.kt: Sector sentiment
 * - EtfCorrelationTab.kt: ETF correlation
 * - HistoryCharts.kt: History chart components
 * - PredictionAccuracyUI.kt: Prediction accuracy UI
 */

// 탭 정의
private enum class AdvancedTab(val title: String, val icon: ImageVector) {
    DASHBOARD("대시보드", Icons.Default.Dashboard),
    MARKET_CAP_FLOW("시총가중", Icons.AutoMirrored.Filled.TrendingUp),
    DIVERGENCE("수급분석", Icons.Default.CompareArrows),
    LIQUIDITY("유동성", Icons.Default.AccountBalance),
    SECTOR_FG("섹터심리", Icons.Default.PieChart),
    ETF_CORRELATION("ETF상관", Icons.Default.GridView)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedDashboardScreen(
    navController: NavHostController,
    viewModel: AdvancedDashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pagerState = rememberPagerState(pageCount = { AdvancedTab.entries.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("고급 분석") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 탭 바
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 8.dp,
                divider = {}
            ) {
                AdvancedTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        icon = { Icon(tab.icon, contentDescription = tab.title, modifier = Modifier.size(20.dp)) }
                    )
                }
            }

            // 컨텐츠
            Box(modifier = Modifier.fillMaxSize()) {
                when (val currentState = state) {
                    is AdvancedDashboardState.Loading -> LoadingContent()
                    is AdvancedDashboardState.Error -> ErrorContent(currentState.message) { viewModel.loadDashboard() }
                    is AdvancedDashboardState.Success -> {
                        // 히스토리 데이터 수집
                        val marketCapFlowHistory by viewModel.marketCapFlowHistory.collectAsState()
                        val liquidityHistory by viewModel.liquidityHistory.collectAsState()
                        val sectorHistory by viewModel.sectorHistory.collectAsState()

                        // 예측 정확도 데이터 수집
                        val marketCapFlowAccuracy by viewModel.marketCapFlowAccuracy.collectAsState()
                        val liquidityAccuracy by viewModel.liquidityAccuracy.collectAsState()

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            when (AdvancedTab.entries[page]) {
                                AdvancedTab.DASHBOARD -> DashboardTab(currentState.data)
                                AdvancedTab.MARKET_CAP_FLOW -> MarketCapFlowTab(
                                    data = currentState.data,
                                    history = marketCapFlowHistory,
                                    accuracy = marketCapFlowAccuracy
                                )
                                AdvancedTab.DIVERGENCE -> DivergenceTab(currentState.data)
                                AdvancedTab.LIQUIDITY -> LiquidityTab(
                                    data = currentState.data,
                                    history = liquidityHistory,
                                    accuracy = liquidityAccuracy
                                )
                                AdvancedTab.SECTOR_FG -> SectorFearGreedTab(currentState.data, sectorHistory)
                                AdvancedTab.ETF_CORRELATION -> EtfCorrelationTab(currentState.data)
                            }
                        }
                    }
                }

                // 로딩 오버레이
                if (isRefreshing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
