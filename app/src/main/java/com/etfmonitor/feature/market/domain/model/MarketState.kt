package com.etfmonitor.feature.market.domain.model

/**
 * Fear & Greed 화면 상태
 */
sealed class FearGreedViewState {
    data object Loading : FearGreedViewState()
    data class Idle(val hasData: Boolean, val latestDate: String?) : FearGreedViewState()
    data class Initializing(val message: String, val progress: Int) : FearGreedViewState()
    data class Updating(val message: String) : FearGreedViewState()
    data class Success(val message: String) : FearGreedViewState()
    data class Error(val message: String) : FearGreedViewState()
}

/**
 * 시장 과매수/과매도 화면 상태
 */
sealed class MarketOscillatorViewState {
    data object Loading : MarketOscillatorViewState()
    data class Idle(val hasData: Boolean, val latestDate: String?) : MarketOscillatorViewState()
    data class Initializing(val message: String, val progress: Int) : MarketOscillatorViewState()
    data class Updating(val message: String) : MarketOscillatorViewState()
    data class Success(val message: String) : MarketOscillatorViewState()
    data class Error(val message: String) : MarketOscillatorViewState()
}

/**
 * 증시 자금 동향 화면 상태
 */
sealed class MarketDepositViewState {
    data object Idle : MarketDepositViewState()
    data object Loading : MarketDepositViewState()
    data class Success(
        val data: MarketDepositTrend,
        val analysis: String
    ) : MarketDepositViewState()
    data class Error(val message: String) : MarketDepositViewState()
}
