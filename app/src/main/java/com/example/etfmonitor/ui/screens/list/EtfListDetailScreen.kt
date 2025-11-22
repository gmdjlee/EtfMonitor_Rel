package com.etfmonitor.ui.screens.list

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.database.entities.Etf
import com.etfmonitor.ui.adaptive.AdaptiveListDetailLayout
import com.etfmonitor.ui.screens.detail.DetailScreen
import com.etfmonitor.ui.theme.spacing

/**
 * ETF List-Detail Screen with Adaptive Supporting Pane
 * Material Design 3 canonical layout:
 * - On small screens: Shows list and detail as separate screens
 * - On large screens: Shows list and detail side by side
 */
@Composable
fun EtfListDetailScreen(
    onNavigateToStockTrend: (etfTicker: String, stockTicker: String) -> Unit,
    viewModel: EtfListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var selectedEtf by remember { mutableStateOf<Etf?>(null) }

    AdaptiveListDetailLayout(
        selectedItem = selectedEtf,
        onItemSelected = { etf -> selectedEtf = etf },
        listContent = { onEtfClick ->
            EtfListPane(
                state = state,
                searchQuery = searchQuery,
                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                onClearSearch = viewModel::clearSearch,
                onEtfClick = onEtfClick
            )
        },
        detailContent = { etf ->
            DetailScreen(
                etfTicker = etf.ticker,
                onNavigateBack = { selectedEtf = null },
                onStockClick = { stockTicker ->
                    onNavigateToStockTrend(etf.ticker, stockTicker)
                }
            )
        }
    )
}

/**
 * ETF List Pane - List content for adaptive layout
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EtfListPane(
    state: ListState,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onEtfClick: (Etf) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ETF 목록") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.medium),
                placeholder = { Text("ETF 검색...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = onClearSearch) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "지우기"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )

            // Content
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
                    EtfList(
                        etfs = s.etfs,
                        onEtfClick = onEtfClick
                    )
                }

                is ListState.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "검색 결과가 없습니다",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
    }
}

/**
 * ETF List - Scrollable list of ETF cards
 */
@Composable
private fun EtfList(
    etfs: List<Etf>,
    onEtfClick: (Etf) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
    ) {
        items(etfs, key = { it.ticker }) { etf ->
            EtfCard(
                etf = etf,
                onClick = { onEtfClick(etf) }
            )
        }
    }
}

/**
 * ETF Card - Individual ETF item card
 */
@Composable
private fun EtfCard(
    etf: Etf,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
            ) {
                Text(
                    text = etf.ticker,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = etf.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
