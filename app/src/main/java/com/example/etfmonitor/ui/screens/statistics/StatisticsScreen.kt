package com.etfmonitor.ui.screens.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.database.entities.CashDepositTrend
import com.etfmonitor.database.entities.HoldingStatus
import com.etfmonitor.database.entities.StockAmountRanking
import com.etfmonitor.database.entities.StockChangeInfo
import com.etfmonitor.ui.screens.statistics.SortColumn
import com.etfmonitor.ui.theme.*
import com.etfmonitor.ui.utils.AmountFormatter
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Statistics Screen - Moss Green Nature Theme
 * Shows comprehensive ETF statistics with multiple tabs
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

    // ✅ 종목 분석 상태
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
                    1 -> NewStocksTab(newStocks, onStockClick)
                    2 -> RemovedStocksTab(removedStocks, onStockClick)
                    3 -> IncreasedStocksTab(increasedStocks, onStockClick)
                    4 -> DecreasedStocksTab(decreasedStocks, onStockClick)
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

@Composable
private fun AmountRankingTab(
    rankings: List<StockAmountRanking>,
    viewModel: StatisticsViewModel,
    onStockClick: (String) -> Unit
) {
    val sortColumn by viewModel.sortColumn.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "총 ${rankings.size}개 종목",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "열 클릭으로 정렬",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Header card
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.extraSmall),
            shape = MaterialTheme.extendedShapes.card,
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(MaterialTheme.spacing.small),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
                Text("순위", Modifier.weight(0.5f), style = MaterialTheme.typography.labelSmall)

                SortableHeaderText(
                    text = "종목명",
                    column = SortColumn.STOCK_NAME,
                    currentColumn = sortColumn,
                    ascending = sortAscending,
                    modifier = Modifier.weight(2f),
                    onClick = { viewModel.sortAmountRankingBy(SortColumn.STOCK_NAME) }
                )

                SortableHeaderText(
                    text = "금액",
                    column = SortColumn.TOTAL_AMOUNT,
                    currentColumn = sortColumn,
                    ascending = sortAscending,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    onClick = { viewModel.sortAmountRankingBy(SortColumn.TOTAL_AMOUNT) }
                )

                SortableHeaderText(
                    text = "ETF수",
                    column = SortColumn.ETF_COUNT,
                    currentColumn = sortColumn,
                    ascending = sortAscending,
                    modifier = Modifier.weight(0.6f),
                    textAlign = TextAlign.Center,
                    onClick = { viewModel.sortAmountRankingBy(SortColumn.ETF_COUNT) }
                )

                SortableHeaderText(
                    text = "신규",
                    column = SortColumn.NEW_ETF_COUNT,
                    currentColumn = sortColumn,
                    ascending = sortAscending,
                    modifier = Modifier.weight(0.5f),
                    textAlign = TextAlign.Center,
                    onClick = { viewModel.sortAmountRankingBy(SortColumn.NEW_ETF_COUNT) }
                )

                SortableHeaderText(
                    text = "증가",
                    column = SortColumn.INCREASED_ETF_COUNT,
                    currentColumn = sortColumn,
                    ascending = sortAscending,
                    modifier = Modifier.weight(0.5f),
                    textAlign = TextAlign.Center,
                    onClick = { viewModel.sortAmountRankingBy(SortColumn.INCREASED_ETF_COUNT) }
                )

                SortableHeaderText(
                    text = "감소",
                    column = SortColumn.DECREASED_ETF_COUNT,
                    currentColumn = sortColumn,
                    ascending = sortAscending,
                    modifier = Modifier.weight(0.5f),
                    textAlign = TextAlign.Center,
                    onClick = { viewModel.sortAmountRankingBy(SortColumn.DECREASED_ETF_COUNT) }
                )

                SortableHeaderText(
                    text = "제외",
                    column = SortColumn.REMOVED_ETF_COUNT,
                    currentColumn = sortColumn,
                    ascending = sortAscending,
                    modifier = Modifier.weight(0.5f),
                    textAlign = TextAlign.Center,
                    onClick = { viewModel.sortAmountRankingBy(SortColumn.REMOVED_ETF_COUNT) }
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            items(rankings.size) { index ->
                val item = rankings[index]
                AmountRankingCard(index + 1, item, onStockClick)
            }
        }
    }
}

@Composable
private fun AmountRankingCard(
    rank: Int,
    item: StockAmountRanking,
    onStockClick: (String) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onStockClick(item.stockTicker) },
        shape = MaterialTheme.extendedShapes.card,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(MaterialTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$rank",
                Modifier.weight(0.5f),
                style = MaterialTheme.typography.bodyLarge,
                color = when (rank) {
                    1 -> MaterialTheme.colorScheme.primary
                    2 -> MaterialTheme.colorScheme.secondary
                    3 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Column(Modifier.weight(2f)) {
                Text(
                    item.stockName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    item.stockTicker,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                AmountFormatter.format(item.totalAmount, showUnit = true),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End
            )
            Text(
                "${item.etfCount}",
                Modifier.weight(0.6f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                "${item.newEtfCount}",
                Modifier.weight(0.5f),
                style = MaterialTheme.typography.bodySmall,
                color = if (item.newEtfCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                "${item.increasedEtfCount}",
                Modifier.weight(0.5f),
                style = MaterialTheme.typography.bodySmall,
                color = if (item.increasedEtfCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                "${item.decreasedEtfCount}",
                Modifier.weight(0.5f),
                style = MaterialTheme.typography.bodySmall,
                color = if (item.decreasedEtfCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                "${item.removedEtfCount}",
                Modifier.weight(0.5f),
                style = MaterialTheme.typography.bodySmall,
                color = if (item.removedEtfCount > 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NewStocksTab(
    stocks: List<StockChangeInfo>,
    onStockClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "총 ${stocks.size}개 종목",
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        LazyColumn(
            contentPadding = PaddingValues(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            items(stocks) { stock ->
                StockChangeCard(stock, HoldingStatus.NEW, onStockClick)
            }
        }
    }
}

@Composable
private fun RemovedStocksTab(
    stocks: List<StockChangeInfo>,
    onStockClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "총 ${stocks.size}개 종목",
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        LazyColumn(
            contentPadding = PaddingValues(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            items(stocks) { stock ->
                StockChangeCard(stock, HoldingStatus.REMOVED, onStockClick)
            }
        }
    }
}

@Composable
private fun IncreasedStocksTab(
    stocks: List<StockChangeInfo>,
    onStockClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "총 ${stocks.size}개 종목",
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        LazyColumn(
            contentPadding = PaddingValues(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            items(stocks) { stock ->
                StockChangeCard(stock, HoldingStatus.INCREASE, onStockClick)
            }
        }
    }
}

@Composable
private fun DecreasedStocksTab(
    stocks: List<StockChangeInfo>,
    onStockClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "총 ${stocks.size}개 종목",
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        LazyColumn(
            contentPadding = PaddingValues(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            items(stocks) { stock ->
                StockChangeCard(stock, HoldingStatus.DECREASE, onStockClick)
            }
        }
    }
}

@Composable
private fun CashDepositTrendTab(trend: List<CashDepositTrend>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
    ) {
        if (trend.isEmpty()) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.extendedShapes.cardLarge
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.spacing.large),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "원화예금 데이터가 없습니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            CashDepositSummaryCard(trend)
            CashDepositChartCard(trend)
            CashDepositDataTable(trend)
        }
    }
}

@Composable
private fun CashDepositSummaryCard(trend: List<CashDepositTrend>) {
    val first = trend.first()
    val last = trend.last()
    val change = last.totalAmount - first.totalAmount

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.extendedShapes.cardLarge,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Text(
                "원화예금 요약",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(
                    label = "현재 총액",
                    value = AmountFormatter.format(last.totalAmount)
                )
                SummaryItem(
                    label = "변동액",
                    value = AmountFormatter.formatChange(change)
                )
                SummaryItem(
                    label = "ETF 수",
                    value = "${last.etfCount}개"
                )
            }
        }
    }
}

@Composable
private fun CashDepositChartCard(trend: List<CashDepositTrend>) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.extendedShapes.cardLarge
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Text(
                "원화예금 추이 (억원)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            val modelProducer = remember { CartesianChartModelProducer() }
            val scope = rememberCoroutineScope()
            val dateLabelsKey = remember { ExtraStore.Key<List<String>>() }

            LaunchedEffect(trend) {
                scope.launch(Dispatchers.Default) {
                    modelProducer.runTransaction {
                        lineSeries {
                            series(trend.map { (it.totalAmount / 100_000_000).toDouble() })
                        }
                        extras { extraStore ->
                            extraStore[dateLabelsKey] = trend.map { formatDateForChart(it.date) }
                        }
                    }
                }
            }

            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(),
                    startAxis = rememberStartAxis(
                        label = rememberTextComponent(
                            color = MaterialTheme.colorScheme.onSurface,
                            textSize = 10.sp
                        )
                    ),
                    bottomAxis = rememberBottomAxis(
                        label = rememberTextComponent(
                            color = MaterialTheme.colorScheme.onSurface,
                            textSize = 10.sp
                        ),
                        valueFormatter = { x, chartValues, _ ->
                            val dateLabels = chartValues.model.extraStore.getOrNull(dateLabelsKey)
                            val index = x.toInt()
                            if (dateLabels != null && index >= 0 && index < dateLabels.size) {
                                dateLabels[index]
                            } else {
                                ""
                            }
                        },
                        itemPlacer = remember {
                            HorizontalAxis.ItemPlacer.default(
                                spacing = 2,
                                addExtremeLabelPadding = true
                            )
                        }
                    )
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
    }
}

@Composable
private fun CashDepositDataTable(trend: List<CashDepositTrend>) {
    val maxAmount = trend.maxOfOrNull { it.totalAmount } ?: 0f
    val headerText = AmountFormatter.getTableHeader(maxAmount)

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.extendedShapes.cardLarge
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.spacing.medium)) {
            Text(
                "상세 데이터",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(MaterialTheme.spacing.small))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
                Text("날짜", Modifier.weight(2f), style = MaterialTheme.typography.labelSmall)
                Text(headerText, Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall)
                Text("ETF수", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = MaterialTheme.spacing.extraSmall),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            trend.reversed().forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MaterialTheme.spacing.extraSmall),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                ) {
                    Text(
                        item.date,
                        Modifier.weight(2f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        AmountFormatter.formatForTable(item.totalAmount, maxAmount),
                        Modifier.weight(1.5f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${item.etfCount}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

private fun formatDateForChart(date: String): String {
    return try {
        val parts = date.split("-")
        if (parts.size == 3) {
            "${parts[1]}/${parts[2]}"
        } else {
            date
        }
    } catch (e: Exception) {
        date
    }
}

@Composable
private fun StockChangeCard(
    stock: StockChangeInfo,
    status: HoldingStatus,
    onStockClick: (String) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onStockClick(stock.stockTicker) },
        shape = MaterialTheme.extendedShapes.card,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(MaterialTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stock.stockName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stock.stockTicker,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(status)
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Text(
                "ETF: ${stock.etfName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                when (status) {
                    HoldingStatus.NEW -> {
                        WeightInfo("비중", stock.currentWeight, Modifier.weight(1f))
                    }
                    HoldingStatus.REMOVED -> {
                        WeightInfo("이전", stock.previousWeight, Modifier.weight(1f))
                    }
                    HoldingStatus.INCREASE, HoldingStatus.DECREASE -> {
                        WeightInfo("이전", stock.previousWeight, Modifier.weight(1f))
                        WeightInfo("현재", stock.currentWeight, Modifier.weight(1f))
                        ChangeInfo(stock.change, Modifier.weight(1f))
                    }
                    else -> {}
                }
            }

            if (stock.currentAmount > 0) {
                Text(
                    "평가금액: ${AmountFormatter.format(stock.currentAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: HoldingStatus) {
    val (text, color) = when (status) {
        HoldingStatus.NEW -> "신규" to MaterialTheme.colorScheme.primary
        HoldingStatus.REMOVED -> "제외" to MaterialTheme.colorScheme.outline
        HoldingStatus.INCREASE -> "증가" to MaterialTheme.colorScheme.tertiary
        HoldingStatus.DECREASE -> "감소" to MaterialTheme.colorScheme.error
        else -> "유지" to MaterialTheme.colorScheme.outline
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.extendedShapes.badge,
        tonalElevation = 1.dp
    ) {
        Text(
            text,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.small,
                vertical = 4.dp
            ),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun WeightInfo(label: String, weight: Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            String.format("%.2f%%", weight),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ChangeInfo(change: Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "변동",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            String.format("%+.2f%%", change),
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                change > 0 -> MaterialTheme.colorScheme.tertiary
                change < 0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

// ✅ 종목 분석 탭
@Composable
private fun StockAnalysisTab(
    searchQuery: String,
    searchResults: List<com.etfmonitor.database.StockSearchResult>,
    analysisResult: com.etfmonitor.database.entities.StockAnalysisResult?,
    isAnalyzing: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchAndAnalyze: (String) -> Unit,
    onStockSelect: (String) -> Unit,
    onClearAnalysis: () -> Unit,
    onStockClick: (String) -> Unit
) {
    var textFieldValue by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
    ) {
        // 검색 입력 - Box로 감싸서 드롭다운 오버레이
        Box(modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.extendedShapes.cardLarge
            ) {
                Column(
                    modifier = Modifier.padding(MaterialTheme.spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                ) {
                    Text(
                        "종목 분석",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = {
                            textFieldValue = it
                            onSearchQueryChange(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                "종목명 또는 티커",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        placeholder = {
                            Text(
                                "예: 삼성전자, 005930",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        trailingIcon = {
                            Row(
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (textFieldValue.isNotBlank()) {
                                    IconButton(onClick = {
                                        textFieldValue = ""
                                        onSearchQueryChange("")
                                    }) {
                                        Icon(
                                            Icons.Default.Close,
                                            "지우기",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        if (textFieldValue.isNotBlank() && !isAnalyzing) {
                                            onSearchAndAnalyze(textFieldValue)
                                        }
                                    },
                                    enabled = textFieldValue.isNotBlank() && !isAnalyzing
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        "검색",
                                        tint = if (textFieldValue.isNotBlank() && !isAnalyzing) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                        }
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (textFieldValue.isNotBlank() && !isAnalyzing) {
                                    onSearchAndAnalyze(textFieldValue)
                                }
                            }
                        ),
                        shape = MaterialTheme.extendedShapes.searchBar,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }

            // 자동완성 드롭다운 - 오버레이
            if (searchResults.isNotEmpty() && textFieldValue.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 140.dp)
                        .heightIn(max = 300.dp),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 8.dp
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = MaterialTheme.extendedShapes.cardLarge
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(searchResults) { result ->
                            ListItem(
                                headlineContent = { Text(result.stockName) },
                                supportingContent = {
                                    Text(
                                        result.stockTicker,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                modifier = Modifier.clickable {
                                    textFieldValue = result.stockName
                                    onSearchQueryChange("")
                                    onStockSelect(result.stockTicker)
                                }
                            )
                            if (result != searchResults.last()) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }

        // 분석 중 표시
        if (isAnalyzing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 분석 결과 표시
        analysisResult?.let { result ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
            ) {
                item {
                    StockAnalysisSummaryCard(result, onClearAnalysis)
                }

                item {
                    StockAnalysisStatisticsCard(result)
                }

                item {
                    StockAnalysisDetailsCard(result, onStockClick)
                }
            }
        }

        // 초기 안내 메시지
        if (!isAnalyzing && analysisResult == null && searchQuery.isEmpty()) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.extendedShapes.cardLarge
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.spacing.extraLarge),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "종목을 검색하여 ETF 편입 현황을 분석하세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun StockAnalysisSummaryCard(
    result: com.etfmonitor.database.entities.StockAnalysisResult,
    onClearAnalysis: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.extendedShapes.cardLarge,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        result.stockName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        result.stockTicker,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = onClearAnalysis) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(
                    label = "포함 ETF",
                    value = "${result.currentEtfCount}개"
                )
                SummaryItem(
                    label = "평가금액",
                    value = AmountFormatter.format(result.totalAmount, showUnit = true)
                )
                SummaryItem(
                    label = "평균 비중",
                    value = String.format("%.2f%%", result.avgWeight)
                )
            }
        }
    }
}

@Composable
private fun StockAnalysisStatisticsCard(
    result: com.etfmonitor.database.entities.StockAnalysisResult
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.extendedShapes.cardLarge
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Text(
                "ETF 편입 변동",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "신규 편입",
                    value = "${result.newIncludedCount}",
                    color = MaterialTheme.colorScheme.primary
                )
                StatItem(
                    label = "비중 증가",
                    value = "${result.increasedCount}",
                    color = MaterialTheme.colorScheme.tertiary
                )
                StatItem(
                    label = "비중 감소",
                    value = "${result.decreasedCount}",
                    color = MaterialTheme.colorScheme.error
                )
                StatItem(
                    label = "제외",
                    value = "${result.removedCount}",
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (result.previousEtfCount > 0) {
                val change = result.currentEtfCount - result.previousEtfCount
                Text(
                    "이전 대비: ${if (change >= 0) "+" else ""}$change ETF",
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        change > 0 -> MaterialTheme.colorScheme.tertiary
                        change < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = color
        )
    }
}

@Composable
private fun StockAnalysisDetailsCard(
    result: com.etfmonitor.database.entities.StockAnalysisResult,
    onStockClick: (String) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.extendedShapes.cardLarge
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Text(
                "ETF별 상세 현황 (${result.etfDetails.size}개)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )

            result.etfDetails.forEach { detail ->
                StockAnalysisDetailItem(detail, onStockClick)
            }
        }
    }
}

@Composable
private fun StockAnalysisDetailItem(
    detail: com.etfmonitor.database.entities.StockEtfDetail,
    onStockClick: (String) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onStockClick(detail.etfTicker) },
        shape = MaterialTheme.extendedShapes.card,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(MaterialTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        detail.etfName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        detail.etfTicker,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(detail.status)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                when (detail.status) {
                    HoldingStatus.NEW -> {
                        WeightInfo("비중", detail.currentWeight, Modifier.weight(1f))
                    }
                    HoldingStatus.REMOVED -> {
                        WeightInfo("이전", detail.previousWeight, Modifier.weight(1f))
                    }
                    else -> {
                        WeightInfo("이전", detail.previousWeight, Modifier.weight(1f))
                        WeightInfo("현재", detail.currentWeight, Modifier.weight(1f))
                        ChangeInfo(detail.change, Modifier.weight(1f))
                    }
                }
            }

            if (detail.amount > 0) {
                Text(
                    "평가금액: ${AmountFormatter.format(detail.amount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

/**
 * 정렬 가능한 헤더 텍스트 컴포넌트
 */
@Composable
private fun SortableHeaderText(
    text: String,
    column: SortColumn,
    currentColumn: SortColumn,
    ascending: Boolean,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier.clickable(onClick = onClick),
        horizontalArrangement = when (textAlign) {
            TextAlign.Center -> Arrangement.Center
            TextAlign.End -> Arrangement.End
            else -> Arrangement.Start
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (column == currentColumn) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }
        )
        if (column == currentColumn) {
            Icon(
                imageVector = if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = if (ascending) "오름차순" else "내림차순",
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
