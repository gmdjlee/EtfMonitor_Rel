package com.etfmonitor.ui.screens.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.database.entities.Etf
import com.etfmonitor.database.entities.HoldingStatus
import com.etfmonitor.ui.components.TabNavigationBar
import com.etfmonitor.ui.screens.list.EtfListViewModel
import com.etfmonitor.ui.screens.list.ListState
import com.etfmonitor.ui.screens.statistics.StatisticsViewModel
import com.etfmonitor.ui.screens.statistics.AmountRankingTab
import com.etfmonitor.ui.screens.statistics.StockChangeTab
import com.etfmonitor.ui.screens.statistics.CashDepositTrendTab
import com.etfmonitor.ui.screens.statistics.StockAnalysisTab
import kotlinx.coroutines.launch

/**
 * ETF Hub Screen
 *
 * Consolidates:
 * - ETF 테마 목록
 * - ETF 전체 통계
 */

private val ETF_TABS = listOf("테마 목록", "통계")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EtfHubScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onEtfClick: (String) -> Unit,
    onStockClick: (String) -> Unit,
    onNavigateToOscillator: (String) -> Unit,
    listViewModel: EtfListViewModel = hiltViewModel(),
    statisticsViewModel: StatisticsViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { ETF_TABS.size })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        HubHeader(
            title = "ETF",
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onSettingsClick = onNavigateToSettings
        )

        // Tab Navigation
        TabNavigationBar(
            tabs = ETF_TABS,
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
                0 -> EtfListHubContent(
                    viewModel = listViewModel,
                    onEtfClick = onEtfClick
                )
                1 -> StatisticsHubContent(
                    viewModel = statisticsViewModel,
                    onStockClick = onStockClick,
                    onNavigateToOscillator = onNavigateToOscillator
                )
            }
        }
    }
}

@Composable
private fun EtfListHubContent(
    viewModel: EtfListViewModel,
    onEtfClick: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()

    when (val s = state) {
        is ListState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is ListState.Success -> {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(s.etfs, key = { _, etf -> etf.ticker }) { index, etf ->
                    EtfListItemCompact(
                        rank = index + 1,
                        etf = etf,
                        onClick = { onEtfClick(etf.ticker) }
                    )
                }
            }
        }
        is ListState.Empty -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ETF 데이터가 없습니다",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        is ListState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = s.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EtfListItemCompact(
    rank: Int,
    etf: Etf,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.02f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rank badge
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = if (rank <= 3) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = rank.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (rank <= 3) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // ETF info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = etf.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = etf.ticker,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Chevron
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StatisticsHubContent(
    viewModel: StatisticsViewModel,
    onStockClick: (String) -> Unit,
    onNavigateToOscillator: (String) -> Unit
) {
    // ViewModel states
    val amountRanking by viewModel.amountRanking.collectAsState()
    val newStocks by viewModel.newStocks.collectAsState()
    val removedStocks by viewModel.removedStocks.collectAsState()
    val increasedStocks by viewModel.increasedStocks.collectAsState()
    val decreasedStocks by viewModel.decreasedStocks.collectAsState()
    val cashDepositTrend by viewModel.cashDepositTrend.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Analysis tab states
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        stringResource(R.string.statistics_tab_amount_ranking),
        stringResource(R.string.statistics_tab_new),
        stringResource(R.string.statistics_tab_removed),
        stringResource(R.string.statistics_tab_increased),
        stringResource(R.string.statistics_tab_decreased),
        stringResource(R.string.statistics_tab_cash_deposit),
        stringResource(R.string.statistics_tab_analysis)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Sub-tab navigation
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 16.dp
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
                CircularProgressIndicator()
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

