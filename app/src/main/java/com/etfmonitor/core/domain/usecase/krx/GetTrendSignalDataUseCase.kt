package com.etfmonitor.core.domain.usecase.krx

import com.etfmonitor.core.analysis.model.TrendSignalData
import com.etfmonitor.core.domain.repository.StockDataRepository
import javax.inject.Inject

/**
 * UseCase for retrieving trend signal analysis data via kotlin_krx.
 *
 * T-012/T-013 MIGRATION: Replaces OscillatorPyClient.getTrendSignalData() in OscillatorViewModel.
 * Wraps KrxStock.getOhlcvByTicker() + TechnicalAnalysisEngine.generateSignals().
 *
 * Includes OHLCV + MA + CMF + Fear&Greed + buy/sell signals.
 */
class GetTrendSignalDataUseCase @Inject constructor(
    private val stockDataRepository: StockDataRepository
) {
    suspend operator fun invoke(
        ticker: String,
        days: Int = 365,
        interval: String = "w"
    ): TrendSignalData? {
        return stockDataRepository.getTrendSignalData(ticker, days, interval)
    }
}
