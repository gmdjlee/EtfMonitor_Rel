package com.etfmonitor.feature.etf.presentation.hub

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.feature.etf.domain.model.Etf
import com.etfmonitor.core.database.entities.HoldingStatus
import com.etfmonitor.core.database.entities.SearchHistory
import com.etfmonitor.core.ui.component.TabNavigationBar
import com.etfmonitor.core.ui.component.HubHeader
import com.etfmonitor.feature.etf.presentation.list.EtfListViewModel
import com.etfmonitor.feature.etf.presentation.list.EtfListState
import com.etfmonitor.feature.stock.presentation.statistics.StatisticsViewModel
import com.etfmonitor.feature.stock.presentation.statistics.AmountRankingTab
import com.etfmonitor.feature.stock.presentation.statistics.StockChangeTab
import com.etfmonitor.feature.stock.presentation.statistics.CashDepositTrendTab
import com.etfmonitor.feature.stock.presentation.statistics.StockAnalysisTab
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
    onNavigateToStocks: (String) -> Unit,
    initialStockTicker: String? = null,
    listViewModel: EtfListViewModel = hiltViewModel(),
    statisticsViewModel: StatisticsViewModel = hiltViewModel()
) {
    // Start on Statistics tab (1) if initialStockTicker is provided
    val initialPage = if (initialStockTicker != null) 1 else 0
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { ETF_TABS.size }
    )
    val coroutineScope = rememberCoroutineScope()

    // Trigger stock analysis and navigate to Statistics tab when initialStockTicker is provided
    LaunchedEffect(initialStockTicker) {
        if (initialStockTicker != null) {
            // Navigate to Statistics tab (page 1)
            pagerState.scrollToPage(1)
            // Trigger analysis (skip history save when navigating via FAB)
            statisticsViewModel.analyzeStock(initialStockTicker, saveHistory = false)
        }
    }

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
                    onNavigateToStocks = onNavigateToStocks,
                    initialStockTicker = initialStockTicker
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
    val searchQuery by viewModel.searchQuery.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        // 검색 필드
        EtfSearchField(
            searchQuery = searchQuery,
            onSearchQueryChanged = viewModel::onSearchQueryChanged,
            onClearSearch = viewModel::onClearSearch,
            onSearchDone = { keyboardController?.hide() }
        )

        when (val s = state) {
            is EtfListState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is EtfListState.Success -> {
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
            is EtfListState.Empty -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "검색 결과가 없습니다" else "ETF 데이터가 없습니다",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is EtfListState.Error -> {
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
}

@Composable
private fun EtfSearchField(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSearchDone: () -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChanged,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = {
            Text(
                "ETF 검색...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "검색",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = onClearSearch) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "지우기",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearchDone() })
    )
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
    onNavigateToStocks: (String) -> Unit,
    initialStockTicker: String? = null
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
    val searchHistory by viewModel.searchHistory.collectAsState(initial = emptyList())

    // Start on Analysis tab (6) if initialStockTicker is provided
    var selectedTab by remember { mutableIntStateOf(if (initialStockTicker != null) 6 else 0) }

    // Force navigate to Analysis tab (6) when initialStockTicker changes
    LaunchedEffect(initialStockTicker) {
        if (initialStockTicker != null) {
            selectedTab = 6
        }
    }

    val tabs = listOf(
        stringResource(R.string.statistics_tab_amount_ranking),
        stringResource(R.string.statistics_tab_new),
        stringResource(R.string.statistics_tab_removed),
        stringResource(R.string.statistics_tab_increased),
        stringResource(R.string.statistics_tab_decreased),
        stringResource(R.string.statistics_tab_cash_deposit),
        stringResource(R.string.statistics_tab_analysis)
    )

    // FAB 표시 조건: 분석 탭에서 분석 결과가 있을 때
    val showFab = selectedTab == 6 && analysisResult != null

    Box(modifier = Modifier.fillMaxSize()) {
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
                        searchHistory = searchHistory,
                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                        onSearchAndAnalyze = { viewModel.searchAndAnalyze(it) },
                        onStockSelect = { viewModel.analyzeStock(it) },
                        onClearAnalysis = { viewModel.clearAnalysis() },
                        onStockClick = onStockClick
                    )
                }
            }
        }

        // Floating Action Button for navigating to stock analysis
        if (showFab && analysisResult != null) {
            ExtendedFloatingActionButton(
                onClick = { onNavigateToStocks(analysisResult!!.stockTicker) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = {
                    Icon(Icons.Default.ShowChart, contentDescription = null)
                },
                text = {
                    Text(stringResource(R.string.fab_stock_analysis))
                }
            )
        }
    }
}

