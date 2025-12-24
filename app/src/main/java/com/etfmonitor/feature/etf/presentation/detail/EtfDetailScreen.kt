package com.etfmonitor.feature.etf.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.feature.etf.domain.model.HoldingStatus
import com.etfmonitor.feature.etf.domain.model.HoldingWithComparison
import com.etfmonitor.core.ui.theme.*
import com.etfmonitor.core.common.util.AmountFormatter

/**
 * ETF Detail Screen - Moss Green Nature Theme
 * Shows holding comparisons with status badges
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EtfDetailScreen(
    etfTicker: String,
    onNavigateBack: () -> Unit,
    onStockClick: (String) -> Unit,
    viewModel: EtfDetailViewModel = hiltViewModel()
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
                        if (state is EtfDetailState.Success) {
                            val comparison = (state as EtfDetailState.Success).comparison
                            Text(
                                "$etfTicker | 수집기간: ${comparison.collectionStartDate} ~ ${comparison.collectionEndDate}",
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
        when (val s = state) {
            is EtfDetailState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                }
            }
            is EtfDetailState.Success -> {
                ComparisonList(
                    items = s.comparison.items,
                    onStockClick = onStockClick,
                    modifier = Modifier.padding(padding)
                )
            }
            is EtfDetailState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(MaterialTheme.spacing.large),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.extendedShapes.cardLarge,
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MaterialTheme.spacing.large),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                s.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
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
        contentPadding = PaddingValues(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
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
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
                .padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            // Header: Stock name and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.stockName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        item.stockTicker,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(item.status)
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Weight comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WeightInfo("이전", item.previousWeight, Modifier.weight(1f))
                WeightInfo("현재", item.currentWeight, Modifier.weight(1f))
                ChangeInfo(item.change, Modifier.weight(1f))
            }

            // Amount display
            if (item.currentAmount > 0) {
                Text(
                    "평가금액: ${AmountFormatter.format(item.currentAmount)}",
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
        HoldingStatus.INCREASE -> "증가" to MaterialTheme.colorScheme.tertiary
        HoldingStatus.DECREASE -> "감소" to MaterialTheme.colorScheme.error
        HoldingStatus.MAINTAIN -> "유지" to MaterialTheme.colorScheme.outline
        HoldingStatus.REMOVED -> "제외" to MaterialTheme.colorScheme.outline
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
                change > 0.01f -> MaterialTheme.colorScheme.tertiary
                change < -0.01f -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
