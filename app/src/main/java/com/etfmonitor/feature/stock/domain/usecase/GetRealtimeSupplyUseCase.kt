package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.feature.stock.domain.model.RealtimeSupplySignal
import com.etfmonitor.feature.stock.domain.model.RealtimeSupplySummary
import com.etfmonitor.feature.stock.domain.model.TradingHours
import com.etfmonitor.feature.stock.domain.repository.RealtimeSupplyRepository
import javax.inject.Inject

class GetRealtimeSupplyUseCase @Inject constructor(
    private val repository: RealtimeSupplyRepository
) {
    suspend operator fun invoke(ticker: String, useCache: Boolean = true): Result<RealtimeSupplySummary> {
        return repository.getRealtimeSupply(ticker, useCache).map { data ->
            RealtimeSupplySummary(
                data = data,
                signal = data.signal,
                signalDescription = getSignalDescription(data.signal),
                isTradingHours = TradingHours.isTradingHours()
            )
        }
    }

    private fun getSignalDescription(signal: RealtimeSupplySignal): String = when (signal) {
        RealtimeSupplySignal.STRONG_BUY -> "외국인/기관 강한 순매수 - 매우 긍정적"
        RealtimeSupplySignal.BUY -> "외국인/기관 순매수 우세 - 긍정적"
        RealtimeSupplySignal.NEUTRAL -> "매수/매도 균형 - 중립"
        RealtimeSupplySignal.SELL -> "외국인/기관 순매도 우세 - 부정적"
        RealtimeSupplySignal.STRONG_SELL -> "외국인/기관 강한 순매도 - 매우 부정적"
    }
}
