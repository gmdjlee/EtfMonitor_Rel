package com.etfmonitor.ui.screens.hub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.ui.components.TabNavigationBar
import com.etfmonitor.ui.screens.feargreed.FearGreedContent
import com.etfmonitor.ui.screens.feargreed.FearGreedViewModel
import com.etfmonitor.ui.screens.marketoscillator.MarketOscillatorViewModel
import com.etfmonitor.ui.screens.oscillator.MarketDepositViewModel
import kotlinx.coroutines.launch

/**
 * Market Indicator Hub Screen - 시장 지표
 *
 * Consolidates:
 * - Fear & Greed Index
 * - 시장 과매수/과매도
 * - 증시 자금 동향
 */

private val MARKET_INDICATOR_TABS = listOf("Fear & Greed", "과매수/과매도", "자금 동향")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketIndicatorHubScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToSettings: () -> Unit,
    fearGreedViewModel: FearGreedViewModel = hiltViewModel(),
    marketOscillatorViewModel: MarketOscillatorViewModel = hiltViewModel(),
    marketDepositViewModel: MarketDepositViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { MARKET_INDICATOR_TABS.size })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        HubHeader(
            title = "시장 지표",
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onSettingsClick = onNavigateToSettings
        )

        // Tab Navigation
        TabNavigationBar(
            tabs = MARKET_INDICATOR_TABS,
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
                0 -> FearGreedContent(viewModel = fearGreedViewModel)
                1 -> MarketOscillatorHubContent(viewModel = marketOscillatorViewModel)
                2 -> MarketDepositHubContent(viewModel = marketDepositViewModel)
            }
        }
    }
}

@Composable
private fun MarketOscillatorHubContent(
    viewModel: MarketOscillatorViewModel
) {
    val state by viewModel.state.collectAsState()
    val selectedMarket by viewModel.selectedMarket.collectAsState()
    val marketData by viewModel.marketData.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Market selection chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedMarket == "KOSPI",
                onClick = { viewModel.onSelectedMarketChanged("KOSPI") },
                label = { Text("KOSPI") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedMarket == "KOSDAQ",
                onClick = { viewModel.onSelectedMarketChanged("KOSDAQ") },
                label = { Text("KOSDAQ") },
                modifier = Modifier.weight(1f)
            )
        }

        // State handling
        when (state) {
            is com.etfmonitor.ui.screens.marketoscillator.MarketOscillatorState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is com.etfmonitor.ui.screens.marketoscillator.MarketOscillatorState.Idle -> {
                val idle = state as com.etfmonitor.ui.screens.marketoscillator.MarketOscillatorState.Idle
                if (!idle.hasData) {
                    NoDataCard(message = "데이터를 수집하려면 설정에서 초기화해주세요.")
                }
            }
            else -> {}
        }

        // Show latest data if available
        if (marketData.isNotEmpty()) {
            val latest = marketData.firstOrNull()
            if (latest != null) {
                // Summary card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "시장 상태",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = latest.status,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Oscillator: ${String.format("%.2f", latest.oscillator)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketDepositHubContent(
    viewModel: MarketDepositViewModel
) {
    val state by viewModel.state.collectAsState()
    val depositData by viewModel.depositData.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (state) {
            is com.etfmonitor.ui.screens.oscillator.MarketDepositState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is com.etfmonitor.ui.screens.oscillator.MarketDepositState.Idle -> {
                val idle = state as com.etfmonitor.ui.screens.oscillator.MarketDepositState.Idle
                if (!idle.hasData) {
                    NoDataCard(message = "데이터를 수집하려면 설정에서 초기화해주세요.")
                }
            }
            else -> {}
        }

        // Show latest data if available
        if (depositData.isNotEmpty()) {
            val latest = depositData.firstOrNull()
            if (latest != null) {
                // Deposit summary
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "증시 예탁금",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "${String.format("%.1f", latest.deposit / 10000)}조원",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "신용융자",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "${String.format("%.1f", latest.credit / 10000)}조원",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoDataCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
