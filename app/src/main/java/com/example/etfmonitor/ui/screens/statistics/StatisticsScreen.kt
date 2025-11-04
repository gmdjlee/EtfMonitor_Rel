package com.etfmonitor.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.etfmonitor.database.entities.CashDepositTrend
import com.etfmonitor.database.entities.StockAmountRanking
import com.etfmonitor.database.entities.StockChangeInfo
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit,
    onStockClick: (String) -> Unit,  // ✅ 추가
    viewModel: StatisticsViewModel = viewModel(factory = StatisticsViewModel.Factory)
) {
    val dates by viewModel.dates.collectAsState()
    val amountRanking by viewModel.amountRanking.collectAsState()
    val newStocks by viewModel.newStocks.collectAsState()
    val removedStocks by viewModel.removedStocks.collectAsState()
    val increasedStocks by viewModel.increasedStocks.collectAsState()
    val decreasedStocks by viewModel.decreasedStocks.collectAsState()  // ✅ 추가
    val cashDepositTrend by viewModel.cashDepositTrend.collectAsState()  // ✅ 추가
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("금액 순위", "신규 편입", "제외", "비중 증가", "비중 감소", "원화예금")  // ✅ 탭 추가

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("전체 통계", style = MaterialTheme.typography.titleMedium)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab) {  // ✅ TabRow → ScrollableTabRow
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            if (isLoading) {
                Box(
                    Modifier.fillMaxSize(),
                    Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedTab) {
                    0 -> AmountRankingTab(amountRanking, viewModel, onStockClick)  // ✅ 클릭 추가
                    1 -> NewStocksTab(newStocks, onStockClick)  // ✅ 클릭 추가
                    2 -> RemovedStocksTab(removedStocks, onStockClick)  // ✅ 클릭 추가
                    3 -> IncreasedStocksTab(increasedStocks, onStockClick)  // ✅ 클릭 추가
                    4 -> DecreasedStocksTab(decreasedStocks, onStockClick)  // ✅ 새 탭
                    5 -> CashDepositTrendTab(cashDepositTrend)  // ✅ 새 탭
                }
            }
        }
    }
}

@Composable
private fun AmountRankingTab(
    rankings: List<StockAmountRanking>,
    viewModel: StatisticsViewModel,
    onStockClick: (String) -> Unit  // ✅ 추가
) {
    var sortAscending by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "총 ${rankings.size}개 종목",
                style = MaterialTheme.typography.titleSmall
            )
            IconButton(
                onClick = {
                    sortAscending = !sortAscending
                    viewModel.sortAmountRanking(sortAscending)
                }
            ) {
                Icon(
                    if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    if (sortAscending) "오름차순" else "내림차순"
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("순위", Modifier.weight(0.5f), style = MaterialTheme.typography.labelSmall)
                Text("종목명", Modifier.weight(2f), style = MaterialTheme.typography.labelSmall)
                Text("금액(억)", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                Text("ETF수", Modifier.weight(0.7f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rankings.size) { index ->
                val item = rankings[index]
                AmountRankingCard(index + 1, item, onStockClick)  // ✅ 클릭 추가
            }
        }
    }
}

@Composable
private fun AmountRankingCard(
    rank: Int,
    item: StockAmountRanking,
    onStockClick: (String) -> Unit  // ✅ 추가
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onStockClick(item.stockTicker) }  // ✅ 클릭 핸들러
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                Text(item.stockName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    item.stockTicker,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                String.format("%.1f", item.totalAmount / 100_000_000),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
            Text(
                "${item.etfCount}",
                Modifier.weight(0.7f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NewStocksTab(
    stocks: List<StockChangeInfo>,
    onStockClick: (String) -> Unit  // ✅ 추가
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "총 ${stocks.size}개 종목",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleSmall
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(stocks) { stock ->
                StockChangeCard(stock, HoldingStatus.NEW, onStockClick)  // ✅ 클릭 추가
            }
        }
    }
}

@Composable
private fun RemovedStocksTab(
    stocks: List<StockChangeInfo>,
    onStockClick: (String) -> Unit  // ✅ 추가
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "총 ${stocks.size}개 종목",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleSmall
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(stocks) { stock ->
                StockChangeCard(stock, HoldingStatus.REMOVED, onStockClick)  // ✅ 클릭 추가
            }
        }
    }
}

@Composable
private fun IncreasedStocksTab(
    stocks: List<StockChangeInfo>,
    onStockClick: (String) -> Unit  // ✅ 추가
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "총 ${stocks.size}개 종목",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleSmall
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(stocks) { stock ->
                StockChangeCard(stock, HoldingStatus.INCREASE, onStockClick)  // ✅ 클릭 추가
            }
        }
    }
}

// ✅ 비중 감소 탭 추가
@Composable
private fun DecreasedStocksTab(
    stocks: List<StockChangeInfo>,
    onStockClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "총 ${stocks.size}개 종목",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleSmall
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(stocks) { stock ->
                StockChangeCard(stock, HoldingStatus.DECREASE, onStockClick)
            }
        }
    }
}

// ✅ 원화예금 추이 탭 추가
@Composable
private fun CashDepositTrendTab(trend: List<CashDepositTrend>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (trend.isEmpty()) {
            Text("원화예금 데이터가 없습니다", style = MaterialTheme.typography.bodyMedium)
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("원화예금 요약", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(
                    label = "현재 총액",
                    value = String.format("%.1f억", last.totalAmount / 100_000_000)
                )
                SummaryItem(
                    label = "변동액",
                    value = String.format("%+.1f억", change / 100_000_000)
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("원화예금 추이 (억원)", style = MaterialTheme.typography.titleMedium)

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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("상세 데이터", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("날짜", Modifier.weight(2f), style = MaterialTheme.typography.labelSmall)
                Text("금액(억)", Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall)
                Text("ETF수", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            trend.reversed().forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(item.date, Modifier.weight(2f), style = MaterialTheme.typography.bodySmall)
                    Text(
                        String.format("%.2f", item.totalAmount / 100_000_000),
                        Modifier.weight(1.5f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "${item.etfCount}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.titleMedium)
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
    onStockClick: (String) -> Unit  // ✅ 추가
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onStockClick(stock.stockTicker) }  // ✅ 클릭 핸들러
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stock.stockName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stock.stockTicker,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(status)
            }

            HorizontalDivider()

            Text(
                "ETF: ${stock.etfName}",
                style = MaterialTheme.typography.bodySmall
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
                    "평가금액: ${String.format("%.2f", stock.currentAmount / 100_000_000)}억원",
                    style = MaterialTheme.typography.bodySmall,
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
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun WeightInfo(label: String, weight: Float, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(
            String.format("%.2f%%", weight),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ChangeInfo(change: Float, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("변동", style = MaterialTheme.typography.labelSmall)
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

enum class HoldingStatus {
    NEW, INCREASE, DECREASE, MAINTAIN, REMOVED
}