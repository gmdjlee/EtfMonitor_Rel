package com.etfmonitor.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.etfmonitor.database.entities.StockAmountRanking
import com.etfmonitor.database.entities.StockChangeInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatisticsViewModel = viewModel(factory = StatisticsViewModel.Factory)
) {
    val dates by viewModel.dates.collectAsState()
    val amountRanking by viewModel.amountRanking.collectAsState()
    val newStocks by viewModel.newStocks.collectAsState()
    val removedStocks by viewModel.removedStocks.collectAsState()
    val increasedStocks by viewModel.increasedStocks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("금액 순위", "신규 편입", "제외", "비중 증가")

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
            TabRow(selectedTabIndex = selectedTab) {
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
                    0 -> AmountRankingTab(amountRanking, viewModel)
                    1 -> NewStocksTab(newStocks)
                    2 -> RemovedStocksTab(removedStocks)
                    3 -> IncreasedStocksTab(increasedStocks)
                }
            }
        }
    }
}

@Composable
private fun AmountRankingTab(
    rankings: List<StockAmountRanking>,
    viewModel: StatisticsViewModel
) {
    var sortAscending by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 정렬 버튼
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

        // 헤더
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

        // 데이터
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rankings.size) { index ->
                val item = rankings[index]
                AmountRankingCard(index + 1, item)
            }
        }
    }
}

@Composable
private fun AmountRankingCard(rank: Int, item: StockAmountRanking) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
private fun NewStocksTab(stocks: List<StockChangeInfo>) {
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
                StockChangeCard(stock, HoldingStatus.NEW)
            }
        }
    }
}

@Composable
private fun RemovedStocksTab(stocks: List<StockChangeInfo>) {
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
                StockChangeCard(stock, HoldingStatus.REMOVED)
            }
        }
    }
}

@Composable
private fun IncreasedStocksTab(stocks: List<StockChangeInfo>) {
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
                StockChangeCard(stock, HoldingStatus.INCREASE)
            }
        }
    }
}

@Composable
private fun StockChangeCard(stock: StockChangeInfo, status: HoldingStatus) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
                    HoldingStatus.INCREASE -> {
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

// HoldingStatus enum (이미 entities에 있으면 import)
enum class HoldingStatus {
    NEW, INCREASE, DECREASE, MAINTAIN, REMOVED
}