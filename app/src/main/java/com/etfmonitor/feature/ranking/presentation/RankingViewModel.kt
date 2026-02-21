package com.etfmonitor.feature.ranking.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.core.network.kiwoom.KiwoomApiError
import com.etfmonitor.core.network.kiwoom.KiwoomApiKeyProvider
import com.etfmonitor.core.network.kiwoom.KiwoomInvestmentMode
import com.etfmonitor.feature.ranking.domain.model.*
import com.etfmonitor.feature.ranking.domain.usecase.GetRankingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class RankingState {
    data object Loading : RankingState()
    data object NoApiKey : RankingState()
    data class Success(val result: RankingResult) : RankingState()
    data class Error(val message: String) : RankingState()
}

@HiltViewModel
class RankingViewModel @Inject constructor(
    private val getRankingUseCase: GetRankingUseCase,
    private val kiwoomApiKeyProvider: KiwoomApiKeyProvider
) : ViewModel() {

    private val _state = MutableStateFlow<RankingState>(RankingState.Loading)
    val state: StateFlow<RankingState> = _state.asStateFlow()

    private val _rankingType = MutableStateFlow(RankingType.DAILY_VOLUME_TOP)
    val rankingType: StateFlow<RankingType> = _rankingType.asStateFlow()

    private val _marketType = MutableStateFlow(MarketType.KOSPI)
    val marketType: StateFlow<MarketType> = _marketType.asStateFlow()

    private val _exchangeType = MutableStateFlow(ExchangeType.KRX_MOCK)
    val exchangeType: StateFlow<ExchangeType> = _exchangeType.asStateFlow()

    private val _itemCount = MutableStateFlow(ItemCount.TEN)
    val itemCount: StateFlow<ItemCount> = _itemCount.asStateFlow()

    private val _investmentMode = MutableStateFlow(KiwoomInvestmentMode.MOCK)
    val investmentMode: StateFlow<KiwoomInvestmentMode> = _investmentMode.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var _fullResult: RankingResult? = null

    private val _orderBookDirection = MutableStateFlow(OrderBookDirection.BUY)
    val orderBookDirection: StateFlow<OrderBookDirection> = _orderBookDirection.asStateFlow()

    private val _investorType = MutableStateFlow(InvestorType.FOREIGN)
    val investorType: StateFlow<InvestorType> = _investorType.asStateFlow()

    private val _tradeDirection = MutableStateFlow(TradeDirection.NET_BUY)
    val tradeDirection: StateFlow<TradeDirection> = _tradeDirection.asStateFlow()

    private val _valueType = MutableStateFlow(ValueType.AMOUNT)
    val valueType: StateFlow<ValueType> = _valueType.asStateFlow()

    private val _excludeEtf = MutableStateFlow(false)
    val excludeEtf: StateFlow<Boolean> = _excludeEtf.asStateFlow()

    init {
        observeApiKeyChanges()
    }

    private fun observeApiKeyChanges() {
        viewModelScope.launch {
            kiwoomApiKeyProvider.configFlow
                .distinctUntilChanged()
                .collect { config ->
                    if (!config.isValid()) {
                        _state.value = RankingState.NoApiKey
                        return@collect
                    }

                    _investmentMode.value = config.investmentMode

                    _exchangeType.value = when (config.investmentMode) {
                        KiwoomInvestmentMode.MOCK -> ExchangeType.KRX_MOCK
                        KiwoomInvestmentMode.PRODUCTION -> ExchangeType.KRX
                    }

                    loadRanking()
                }
        }
    }

    fun onRankingTypeChange(type: RankingType) {
        _rankingType.value = type
        loadRanking()
    }

    fun onMarketTypeChange(type: MarketType) {
        _marketType.value = type
        loadRanking()
    }

    fun onExchangeTypeChange(type: ExchangeType) {
        _exchangeType.value = type
        loadRanking()
    }

    fun onOrderBookDirectionChange(direction: OrderBookDirection) {
        _orderBookDirection.value = direction
        loadRanking()
    }

    fun onInvestorTypeChange(type: InvestorType) {
        _investorType.value = type
        loadRanking()
    }

    fun onTradeDirectionChange(direction: TradeDirection) {
        _tradeDirection.value = direction
        loadRanking()
    }

    fun onValueTypeChange(type: ValueType) {
        _valueType.value = type
        loadRanking()
    }

    fun onExcludeEtfChange(exclude: Boolean) {
        _excludeEtf.value = exclude
        applyLocalFilters()
    }

    fun onItemCountChange(count: ItemCount) {
        _itemCount.value = count
        applyLocalFilters()
    }

    private fun applyLocalFilters() {
        val fullResult = _fullResult ?: run {
            loadRanking()
            return
        }

        val filteredItems = fullResult.items
            .let { items ->
                if (_excludeEtf.value) {
                    items.filterNot { isEtfOrEtn(it.name) }
                } else {
                    items
                }
            }
            .take(_itemCount.value.value)

        _state.value = RankingState.Success(fullResult.copy(items = filteredItems))
    }

    private fun isEtfOrEtn(name: String): Boolean {
        val upperName = name.uppercase()
        return ETF_BRAND_PATTERNS.any { pattern -> upperName.startsWith(pattern) } ||
            upperName.contains("ETF") ||
            upperName.contains("ETN")
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadRanking()
            _isRefreshing.value = false
        }
    }

    private fun loadRanking() {
        viewModelScope.launch {
            _state.value = RankingState.Loading

            val result = getRankingUseCase(
                rankingType = _rankingType.value,
                marketType = _marketType.value,
                exchangeType = _exchangeType.value,
                itemCount = ItemCount.THIRTY,
                orderBookDirection = _orderBookDirection.value,
                investorType = _investorType.value,
                tradeDirection = _tradeDirection.value,
                valueType = _valueType.value
            )

            result.fold(
                onSuccess = { data ->
                    _fullResult = data
                    applyLocalFilters()
                },
                onFailure = { error ->
                    _fullResult = null
                    val message = when (error) {
                        is KiwoomApiError.NoApiKeyError -> {
                            _state.value = RankingState.NoApiKey
                            return@fold
                        }
                        is KiwoomApiError.NetworkError -> error.message
                        is KiwoomApiError.AuthError -> "인증 오류: ${error.message}"
                        is KiwoomApiError.ApiCallError -> "API 오류: ${error.message}"
                        else -> error.message ?: "알 수 없는 오류"
                    }
                    _state.value = RankingState.Error(message)
                }
            )
        }
    }

    fun getAvailableExchangeTypes(): List<ExchangeType> {
        return when (_investmentMode.value) {
            KiwoomInvestmentMode.MOCK -> listOf(ExchangeType.KRX_MOCK)
            KiwoomInvestmentMode.PRODUCTION -> listOf(ExchangeType.KRX, ExchangeType.NXT)
        }
    }

    fun isOrderBookSurgeType(): Boolean {
        return _rankingType.value == RankingType.ORDER_BOOK_SURGE
    }

    fun isForeignInstitutionType(): Boolean {
        return _rankingType.value == RankingType.FOREIGN_INSTITUTION_TOP
    }

    fun getAvailableMarketTypes(): List<MarketType> {
        return if (_rankingType.value == RankingType.FOREIGN_INSTITUTION_TOP) {
            listOf(MarketType.KOSPI, MarketType.KOSDAQ, MarketType.ALL)
        } else {
            listOf(MarketType.KOSPI, MarketType.KOSDAQ)
        }
    }

    companion object {
        private val ETF_BRAND_PATTERNS = listOf(
            "KODEX", "TIGER", "ARIRANG", "KINDEX", "KBSTAR",
            "HANARO", "ACE", "SOL", "KOSEF", "TREX",
            "SMART", "TIMEFOLIO", "RISE", "PLUS", "FOCUS",
            "WOORI", "BNK", "파워", "TRUE", "QV"
        )
    }
}
