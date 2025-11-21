package com.etfmonitor.ui.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.database.entities.HoldingStatus
import com.etfmonitor.database.entities.HoldingWithComparison
import com.etfmonitor.ui.utils.AmountFormatter  // ✅ 추가

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    etfTicker: String,
    onNavigateBack: () -> Unit,
    onStockClick: (String) -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val etfName by viewModel.etfName.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            etfName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (state is DetailState.Success) {
                            val comparison = (state as DetailState.Success).comparison
                            Text(
                                "$etfTicker | ${comparison.previousDate} → ${comparison.currentDate}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        } else {
                            Text(
                                etfTicker,
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
        when (val s = state) {
            is DetailState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is DetailState.Success -> {
                ComparisonList(
                    items = s.comparison.items,
                    onStockClick = onStockClick,
                    modifier = Modifier.padding(padding)
                )
            }
            is DetailState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ComparisonList(
    items: List<HoldingWithComparison>,
    onStockClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.stockTicker }) { item ->
            ComparisonCard(
                item = item,
                onClick = { onStockClick(item.stockTicker) }
            )
        }
    }
}

@Composable
private fun ComparisonCard(
    item: HoldingWithComparison,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.stockName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        item.stockTicker,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(item.status)
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WeightInfo("이전", item.previousWeight, Modifier.weight(1f))
                WeightInfo("현재", item.currentWeight, Modifier.weight(1f))
                ChangeInfo(item.change, Modifier.weight(1f))
            }

            // ✅ 개선: 동적 단위 표시
            if (item.currentAmount > 0) {
                Text(
                    "평가금액: ${AmountFormatter.format(item.currentAmount)}",
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
        HoldingStatus.INCREASE -> "증가" to MaterialTheme.colorScheme.tertiary
        HoldingStatus.DECREASE -> "감소" to MaterialTheme.colorScheme.error
        HoldingStatus.MAINTAIN -> "유지" to MaterialTheme.colorScheme.outline
        HoldingStatus.REMOVED -> "제외" to MaterialTheme.colorScheme.outline
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
                change > 0.01f -> MaterialTheme.colorScheme.tertiary
                change < -0.01f -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}