package com.etfmonitor.feature.ranking.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.core.network.kiwoom.KiwoomInvestmentMode
import com.etfmonitor.core.ui.component.HubHeader
import com.etfmonitor.core.ui.theme.LocalExtendedColors
import com.etfmonitor.feature.ranking.domain.model.*
import java.text.NumberFormat
import java.util.Locale

private val koreanNumberFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    viewModel: RankingViewModel = hiltViewModel(),
    onStockClick: (ticker: String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val rankingType by viewModel.rankingType.collectAsState()
    val marketType by viewModel.marketType.collectAsState()
    val exchangeType by viewModel.exchangeType.collectAsState()
    val itemCount by viewModel.itemCount.collectAsState()
    val investmentMode by viewModel.investmentMode.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val orderBookDirection by viewModel.orderBookDirection.collectAsState()
    val investorType by viewModel.investorType.collectAsState()
    val tradeDirection by viewModel.tradeDirection.collectAsState()
    val valueType by viewModel.valueType.collectAsState()
    val excludeEtf by viewModel.excludeEtf.collectAsState()

    val availableMarketTypes = remember(rankingType) { viewModel.getAvailableMarketTypes() }
    val availableExchangeTypes = remember(investmentMode) { viewModel.getAvailableExchangeTypes() }
    val isOrderBookSurge = remember(rankingType) { viewModel.isOrderBookSurgeType() }
    val isForeignInstitution = remember(rankingType) { viewModel.isForeignInstitutionType() }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        HubHeader(
            title = "순위",
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme
        )

        // Ranking type selector
        RankingTypeSelector(
            selectedType = rankingType,
            onTypeSelected = viewModel::onRankingTypeChange
        )

        // Market type tabs
        MarketTypeTabs(
            selectedMarket = marketType,
            availableMarkets = availableMarketTypes,
            onMarketSelected = viewModel::onMarketTypeChange
        )

        // Exchange type tabs (only in Production mode)
        if (investmentMode == KiwoomInvestmentMode.PRODUCTION) {
            ExchangeTypeTabs(
                selectedExchange = exchangeType,
                availableExchanges = availableExchangeTypes,
                onExchangeSelected = viewModel::onExchangeTypeChange
            )
        }

        // Filter row
        FilterRow(
            selectedCount = itemCount,
            onCountSelected = viewModel::onItemCountChange,
            excludeEtf = excludeEtf,
            onExcludeEtfChange = viewModel::onExcludeEtfChange,
            isOrderBookSurgeType = isOrderBookSurge,
            orderBookDirection = orderBookDirection,
            onOrderBookDirectionChange = viewModel::onOrderBookDirectionChange,
            isForeignInstitutionType = isForeignInstitution,
            investorType = investorType,
            tradeDirection = tradeDirection,
            valueType = valueType,
            onInvestorTypeChange = viewModel::onInvestorTypeChange,
            onTradeDirectionChange = viewModel::onTradeDirectionChange,
            onValueTypeChange = viewModel::onValueTypeChange
        )

        // Content
        when (val currentState = state) {
            is RankingState.Loading -> {
                LoadingContent()
            }
            is RankingState.NoApiKey -> {
                NoApiKeyContent(onNavigateToSettings = onNavigateToSettings)
            }
            is RankingState.Success -> {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    RankingTable(
                        result = currentState.result,
                        onItemClick = { item -> onStockClick(item.ticker) }
                    )
                }
            }
            is RankingState.Error -> {
                ErrorContent(
                    message = currentState.message,
                    onRetry = viewModel::refresh
                )
            }
        }
    }
}

@Composable
private fun RankingTypeSelector(
    selectedType: RankingType,
    onTypeSelected: (RankingType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedType.displayName)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            RankingType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.displayName) },
                    onClick = {
                        onTypeSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MarketTypeTabs(
    selectedMarket: MarketType,
    availableMarkets: List<MarketType>,
    onMarketSelected: (MarketType) -> Unit
) {
    val selectedIndex = availableMarkets.indexOf(selectedMarket).coerceAtLeast(0)

    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = Modifier.fillMaxWidth()
    ) {
        availableMarkets.forEachIndexed { index, market ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onMarketSelected(market) },
                text = { Text(market.displayName) }
            )
        }
    }
}

@Composable
private fun ExchangeTypeTabs(
    selectedExchange: ExchangeType,
    availableExchanges: List<ExchangeType>,
    onExchangeSelected: (ExchangeType) -> Unit
) {
    val selectedIndex = availableExchanges.indexOf(selectedExchange).coerceAtLeast(0)

    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        availableExchanges.forEachIndexed { index, exchange ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onExchangeSelected(exchange) },
                text = { Text(exchange.displayName) }
            )
        }
    }
}

@Composable
private fun FilterRow(
    selectedCount: ItemCount,
    onCountSelected: (ItemCount) -> Unit,
    excludeEtf: Boolean,
    onExcludeEtfChange: (Boolean) -> Unit,
    isOrderBookSurgeType: Boolean,
    orderBookDirection: OrderBookDirection,
    onOrderBookDirectionChange: (OrderBookDirection) -> Unit,
    isForeignInstitutionType: Boolean,
    investorType: InvestorType,
    tradeDirection: TradeDirection,
    valueType: ValueType,
    onInvestorTypeChange: (InvestorType) -> Unit,
    onTradeDirectionChange: (TradeDirection) -> Unit,
    onValueTypeChange: (ValueType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ItemCount.entries.forEach { count ->
            FilterChip(
                selected = selectedCount == count,
                onClick = { onCountSelected(count) },
                label = { Text("${count.value}개") }
            )
        }

        Spacer(modifier = Modifier.width(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "ETF 제외",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(
                checked = excludeEtf,
                onCheckedChange = onExcludeEtfChange
            )
        }

        if (isOrderBookSurgeType) {
            Spacer(modifier = Modifier.width(8.dp))
            OrderBookDirection.entries.forEach { direction ->
                FilterChip(
                    selected = orderBookDirection == direction,
                    onClick = { onOrderBookDirectionChange(direction) },
                    label = { Text(direction.displayName) }
                )
            }
        }

        if (isForeignInstitutionType) {
            Spacer(modifier = Modifier.width(8.dp))
            InvestorType.entries.forEach { type ->
                FilterChip(
                    selected = investorType == type,
                    onClick = { onInvestorTypeChange(type) },
                    label = { Text(type.displayName) }
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TradeDirection.entries.forEach { direction ->
                FilterChip(
                    selected = tradeDirection == direction,
                    onClick = { onTradeDirectionChange(direction) },
                    label = { Text(direction.displayName) }
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            ValueType.entries.forEach { type ->
                FilterChip(
                    selected = valueType == type,
                    onClick = { onValueTypeChange(type) },
                    label = { Text(type.displayName) }
                )
            }
        }
    }
}

@Composable
private fun RankingTable(
    result: RankingResult,
    onItemClick: (RankingItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            RankingTableHeader(result)
        }

        itemsIndexed(result.items) { index, item ->
            RankingTableRow(
                item = item,
                result = result,
                onClick = { onItemClick(item) }
            )
            if (index < result.items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }

        if (result.items.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "데이터가 없습니다",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RankingTableHeader(result: RankingResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "순위",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp)
        )
        Text(
            text = "종목",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "현재가",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = getTypeSpecificHeader(result),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(80.dp)
        )
    }
}

private fun getTypeSpecificHeader(result: RankingResult): String {
    return when (result.rankingType) {
        RankingType.ORDER_BOOK_SURGE -> "급증률"
        RankingType.VOLUME_SURGE -> "급증률"
        RankingType.DAILY_VOLUME_TOP -> "거래량"
        RankingType.CREDIT_RATIO_TOP -> "신용비율"
        RankingType.FOREIGN_INSTITUTION_TOP -> {
            val investor = when (result.investorType) {
                InvestorType.FOREIGN -> "외인"
                InvestorType.INSTITUTION -> "기관"
                InvestorType.ALL -> "합계"
                null -> "외인"
            }
            val direction = when (result.tradeDirection) {
                TradeDirection.NET_BUY -> "순매수"
                TradeDirection.NET_SELL -> "순매도"
                null -> "순매수"
            }
            "$investor$direction"
        }
    }
}

@Composable
private fun RankingTableRow(
    item: RankingItem,
    result: RankingResult,
    onClick: () -> Unit
) {
    val extendedColors = LocalExtendedColors.current
    val priceColor = when (item.priceChangeSign) {
        "+" -> extendedColors.stockPriceUp
        "-" -> extendedColors.stockPriceDown
        else -> extendedColors.stockPriceNeutral
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${item.rank}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.ticker,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.width(80.dp)
        ) {
            Text(
                text = formatPrice(item.currentPrice),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatChange(item.priceChange, item.changeRate, item.priceChangeSign),
                style = MaterialTheme.typography.bodySmall,
                color = priceColor
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatTypeSpecificValue(item, result),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(80.dp)
        )
    }
}

private fun formatTypeSpecificValue(item: RankingItem, result: RankingResult): String {
    return when (result.rankingType) {
        RankingType.ORDER_BOOK_SURGE -> item.surgeRate?.let { "%.1f%%".format(it) } ?: "-"
        RankingType.VOLUME_SURGE -> item.surgeRate?.let { "%.1f%%".format(it) } ?: "-"
        RankingType.DAILY_VOLUME_TOP -> item.volume?.let { formatVolume(it) } ?: "-"
        RankingType.CREDIT_RATIO_TOP -> item.creditRatio?.let { "%.2f%%".format(it) } ?: "-"
        RankingType.FOREIGN_INSTITUTION_TOP -> {
            val value = item.netValue ?: item.foreignNetBuy ?: 0L
            if (result.valueType == ValueType.QUANTITY) {
                formatVolume(value)
            } else {
                formatAmount(value)
            }
        }
    }
}

private fun formatPrice(price: Long): String {
    if (price == 0L) return "-"
    return koreanNumberFormat.format(price)
}

private fun formatChange(change: Long, rate: Double, sign: String): String {
    if (change == 0L && rate == 0.0) return "-"
    val signStr = if (sign == "+") "+" else if (sign == "-") "" else ""
    return "$signStr${koreanNumberFormat.format(change)} (${String.format("%.2f", rate)}%)"
}

private fun formatVolume(volume: Long): String {
    return when {
        volume >= 100_000_000 -> String.format("%.1f억", volume / 100_000_000.0)
        volume >= 10_000 -> String.format("%.1f만", volume / 10_000.0)
        else -> koreanNumberFormat.format(volume)
    }
}

private fun formatAmount(amount: Long): String {
    return when {
        amount >= 100_000_000 -> String.format("%+.0f억", amount / 100_000_000.0)
        amount >= 10_000 -> String.format("%+.0f만", amount / 10_000.0)
        amount <= -100_000_000 -> String.format("%.0f억", amount / 100_000_000.0)
        amount <= -10_000 -> String.format("%.0f만", amount / 10_000.0)
        else -> koreanNumberFormat.format(amount)
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NoApiKeyContent(onNavigateToSettings: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "키움 API 키가 설정되지 않았습니다",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "설정 화면에서 키움 API 키를 입력해주세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onNavigateToSettings) {
                    Text("설정으로 이동")
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "오류 발생",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onRetry) {
                    Text("다시 시도")
                }
            }
        }
    }
}
