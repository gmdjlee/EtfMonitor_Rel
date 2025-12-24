package com.etfmonitor.feature.market.presentation.hub

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.database.entities.MarketOscillatorData
import com.etfmonitor.ui.components.TabNavigationBar
import com.etfmonitor.ui.components.MarketDepositChart
import com.etfmonitor.ui.screens.hub.HubHeader
import com.etfmonitor.feature.market.presentation.feargreed.FearGreedContent
import com.etfmonitor.feature.market.presentation.feargreed.FearGreedViewModel
import com.etfmonitor.feature.market.presentation.oscillator.MarketOscillatorViewModel
import com.etfmonitor.feature.market.presentation.oscillator.MarketOscillatorState
import com.etfmonitor.feature.market.presentation.deposit.MarketDepositViewModel
import com.etfmonitor.feature.market.presentation.deposit.MarketDepositState
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
    val overboughtThreshold by viewModel.overboughtThreshold.collectAsState()
    val oversoldThreshold by viewModel.oversoldThreshold.collectAsState()
    val bodyScale by viewModel.bodyScale.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Market selection chips
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.market_select),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedMarket == "KOSPI",
                        onClick = { viewModel.onSelectedMarketChanged("KOSPI") },
                        label = { Text(stringResource(R.string.market_kospi)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedMarket == "KOSDAQ",
                        onClick = { viewModel.onSelectedMarketChanged("KOSDAQ") },
                        label = { Text(stringResource(R.string.market_kosdaq)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // State handling
        when (val currentState = state) {
            is MarketOscillatorState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is MarketOscillatorState.Initializing -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Text(currentState.message)
                        Text(
                            stringResource(R.string.progress_percent, currentState.progress),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            is MarketOscillatorState.Updating -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(currentState.message)
                    }
                }
            }
            is MarketOscillatorState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        currentState.message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            is MarketOscillatorState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        currentState.message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            is MarketOscillatorState.Idle -> {
                if (!currentState.hasData) {
                    NoDataCard(message = stringResource(R.string.market_oscillator_no_data))
                }
            }
        }

        // Show latest data and table if available
        if (marketData.isNotEmpty()) {
            val latest = marketData.firstOrNull()
            if (latest != null) {
                OscillatorLatestDataCard(
                    latest = latest,
                    overboughtThreshold = overboughtThreshold,
                    oversoldThreshold = oversoldThreshold
                )
            }

            // Data Table
            OscillatorDataTable(
                data = marketData,
                overboughtThreshold = overboughtThreshold,
                oversoldThreshold = oversoldThreshold,
                bodyScale = bodyScale
            )
        }
    }
}

@Composable
private fun MarketDepositHubContent(
    viewModel: MarketDepositViewModel
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (val currentState = state) {
            is MarketDepositState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.data_collecting))
                    }
                }
            }
            is MarketDepositState.Idle -> {
                NoDataCard(message = stringResource(R.string.market_deposit_no_data))
            }
            is MarketDepositState.Success -> {
                val depositData = currentState.data

                // 분석 카드
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
                    data = depositData,
                    latestDate = depositData.dates.lastOrNull()
                )

                // 최신 데이터 요약
                if (depositData.dates.isNotEmpty()) {
                    val lastIdx = depositData.dates.size - 1

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                stringResource(R.string.market_deposit_latest_data, depositData.dates[lastIdx]),
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
                                        String.format("%.0f억원", depositData.depositAmounts[lastIdx]),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                val depositChange = depositData.depositChanges[lastIdx]
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
                                        String.format("%.0f억원", depositData.creditAmounts[lastIdx]),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                val creditChange = depositData.creditChanges[lastIdx]
                                Text(
                                    String.format("%+.0f억", creditChange),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (creditChange > 0) Color(0xFF388E3C) else Color(0xFFD32F2F),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // 최근 5일 데이터
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                stringResource(R.string.market_deposit_recent_5_days),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )

                            val recentCount = minOf(5, depositData.dates.size)
                            val startIdx = maxOf(0, depositData.dates.size - recentCount)

                            for (i in (depositData.dates.size - 1) downTo startIdx) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        depositData.dates[i],
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        String.format("%.0f억", depositData.depositAmounts[i]),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        String.format("%.0f억", depositData.creditAmounts[i]),
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
                NoDataCard(message = currentState.message)
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

@Composable
private fun OscillatorLatestDataCard(
    latest: MarketOscillatorData,
    overboughtThreshold: Double,
    oversoldThreshold: Double
) {
    val cardBackground = Color(0xFFFFFBFE)
    val textColor = Color(0xFF1C1B1F)
    val dividerColor = Color(0xFFCAC4D0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBackground)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "최신 데이터 (${latest.date})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            HorizontalDivider(color = dividerColor)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("지수", style = MaterialTheme.typography.bodyMedium, color = textColor)
                Text(
                    String.format("%.2f", latest.indexValue),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Oscillator", style = MaterialTheme.typography.bodyMedium, color = textColor)
                Text(
                    String.format("%.2f%%", latest.oscillator),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        latest.oscillator >= overboughtThreshold -> Color.Red
                        latest.oscillator <= oversoldThreshold -> Color.Blue
                        else -> textColor
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("상태", style = MaterialTheme.typography.bodyMedium, color = textColor)
                val status = when {
                    latest.oscillator >= overboughtThreshold -> "과매수"
                    latest.oscillator <= oversoldThreshold -> "과매도"
                    else -> "중립"
                }
                val statusColor = when {
                    latest.oscillator >= overboughtThreshold -> Color.Red
                    latest.oscillator <= oversoldThreshold -> Color.Blue
                    else -> textColor
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun OscillatorDataTable(
    data: List<MarketOscillatorData>,
    overboughtThreshold: Double,
    oversoldThreshold: Double,
    bodyScale: Float
) {
    val cardBackground = Color(0xFFFFFBFE)
    val textColor = Color(0xFF1C1B1F)
    val secondaryTextColor = Color(0xFF49454F)
    val headerBackground = Color(0xFFE7E0EC)
    val dividerColor = Color(0xFFCAC4D0)

    val dateFontSize = (11 * bodyScale).sp
    val valueFontSize = (11 * bodyScale).sp
    val statusFontSize = (10 * bodyScale).sp

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBackground)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "과매수/과매도 내역",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            Text(
                "표시 기간: 최근 ${data.size}일",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor
            )

            HorizontalDivider(color = dividerColor)

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBackground)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "날짜",
                    modifier = Modifier.weight(0.4f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = textColor
                )
                Text(
                    "지수",
                    modifier = Modifier.weight(0.3f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    color = textColor
                )
                Text(
                    "Oscillator",
                    modifier = Modifier.weight(0.3f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    color = textColor
                )
                Text(
                    "상태",
                    modifier = Modifier.weight(0.25f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = textColor
                )
            }

            // Table Rows
            data.forEach { item ->
                val status = when {
                    item.oscillator >= overboughtThreshold -> "과매수"
                    item.oscillator <= oversoldThreshold -> "과매도"
                    else -> "중립"
                }
                val statusColor = when {
                    item.oscillator >= overboughtThreshold -> Color.Red
                    item.oscillator <= oversoldThreshold -> Color.Blue
                    else -> textColor
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.date,
                        modifier = Modifier.weight(0.4f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = dateFontSize,
                        textAlign = TextAlign.Center,
                        color = textColor
                    )
                    Text(
                        String.format("%.0f", item.indexValue),
                        modifier = Modifier.weight(0.3f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = valueFontSize,
                        textAlign = TextAlign.End,
                        color = textColor
                    )
                    Text(
                        String.format("%.1f%%", item.oscillator),
                        modifier = Modifier.weight(0.3f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = valueFontSize,
                        fontWeight = if (status != "중립") FontWeight.Bold else FontWeight.Normal,
                        color = statusColor,
                        textAlign = TextAlign.End
                    )
                    Text(
                        status,
                        modifier = Modifier.weight(0.25f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = statusFontSize,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        textAlign = TextAlign.Center
                    )
                }

                if (item != data.last()) {
                    HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
                }
            }
        }
    }
}
