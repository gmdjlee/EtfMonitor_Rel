package com.etfmonitor.feature.market.presentation.deposit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.feature.market.domain.model.MarketDepositTrend
import com.etfmonitor.feature.market.domain.model.MarketDepositViewState
import com.etfmonitor.ui.components.LoadingCard
import com.etfmonitor.ui.components.ErrorCard
import com.etfmonitor.oscillator.model.MarketDepositData

/**
 * Market Deposit Screen (Clean Architecture)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketDepositScreen(
    onNavigateBack: () -> Unit,
    viewModel: MarketDepositViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.market_deposit_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshData() },
                        enabled = state !is MarketDepositViewState.Loading
                    ) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.nav_refresh))
                    }
                },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val currentState = state) {
                is MarketDepositViewState.Loading -> {
                    LoadingCard(message = stringResource(R.string.data_collecting))
                }

                is MarketDepositViewState.Success -> {
                    // Convert to legacy data format for chart
                    val legacyData = MarketDepositData(
                        dates = currentState.data.dates,
                        depositAmounts = currentState.data.depositAmounts,
                        depositChanges = currentState.data.depositChanges,
                        creditAmounts = currentState.data.creditAmounts,
                        creditChanges = currentState.data.creditChanges
                    )

                    // Analysis Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                stringResource(R.string.market_deposit_analysis),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                currentState.analysis,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }

                    // Chart
                    com.etfmonitor.ui.components.MarketDepositChart(
                        data = legacyData,
                        latestDate = currentState.data.dates.lastOrNull()
                    )

                    // Latest Data Summary
                    if (currentState.data.dates.isNotEmpty()) {
                        val lastIdx = currentState.data.dates.size - 1

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    stringResource(R.string.market_deposit_latest_data, currentState.data.dates[lastIdx]),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                HorizontalDivider()

                                // Customer Deposit
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            stringResource(R.string.market_deposit_customer),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            String.format("%.0f억원", currentState.data.depositAmounts[lastIdx]),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    val depositChange = currentState.data.depositChanges[lastIdx]
                                    Text(
                                        String.format("%+.0f억", depositChange),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (depositChange > 0) Color(0xFF388E3C) else Color(0xFFD32F2F),
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                HorizontalDivider()

                                // Credit Balance
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            stringResource(R.string.market_deposit_credit),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            String.format("%.0f억원", currentState.data.creditAmounts[lastIdx]),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    val creditChange = currentState.data.creditChanges[lastIdx]
                                    Text(
                                        String.format("%+.0f억", creditChange),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (creditChange > 0) Color(0xFF388E3C) else Color(0xFFD32F2F),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Data Details
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    stringResource(R.string.market_deposit_details),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    stringResource(R.string.market_deposit_data_points, currentState.data.dates.size),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    stringResource(R.string.market_deposit_date_range, currentState.data.dates.first(), currentState.data.dates.last()),
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                HorizontalDivider()

                                Text(
                                    stringResource(R.string.market_deposit_recent_5_days),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )

                                val recentCount = minOf(5, currentState.data.dates.size)
                                val startIdx = maxOf(0, currentState.data.dates.size - recentCount)

                                for (i in (currentState.data.dates.size - 1) downTo startIdx) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            currentState.data.dates[i],
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            String.format("%.0f억", currentState.data.depositAmounts[i]),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            String.format("%.0f억", currentState.data.creditAmounts[i]),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                is MarketDepositViewState.Error -> {
                    ErrorCard(message = currentState.message)
                }

                is MarketDepositViewState.Idle -> {
                    // Not used (auto-load)
                }
            }
        }
    }
}
