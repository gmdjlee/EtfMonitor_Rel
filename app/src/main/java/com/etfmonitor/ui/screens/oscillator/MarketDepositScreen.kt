package com.etfmonitor.ui.screens.oscillator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.etfmonitor.ui.components.MarketDepositChart
import com.etfmonitor.ui.components.LoadingCard
import com.etfmonitor.ui.components.ErrorCard

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
            // 상태별 UI
            when (val currentState = state) {
                is MarketDepositState.Loading -> {
                    LoadingCard(message = stringResource(R.string.data_collecting))
                }

                is MarketDepositState.Success -> {
                    // 시장 분석 카드
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

                    // 증시 자금 동향 차트
                    MarketDepositChart(
                        data = currentState.data,
                        latestDate = currentState.data.dates.lastOrNull()
                    )

                    // 최신 데이터 요약
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

                                // 고객예탁금
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

                                // 신용잔고
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

                        // 데이터 상세
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

                is MarketDepositState.Error -> {
                    ErrorCard(message = currentState.message)
                }

                is MarketDepositState.Idle -> {
                    // 사용하지 않음 (자동 로드)
                }
            }
        }
    }
}
