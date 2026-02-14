package com.etfmonitor.core.domain.usecase.krx

import com.etfmonitor.core.analysis.model.StockOhlcvData
import com.etfmonitor.core.domain.repository.StockDataRepository
import javax.inject.Inject

/**
 * UseCase for retrieving stock OHLCV data via kotlin_krx.
 *
 * T-012/T-013 MIGRATION: Replaces OscillatorPyClient.getStockOhlcv().
 * Wraps KrxStock.getOhlcvByTicker() + TechnicalAnalysisEngine resampling (weekly/monthly).
 *
 * Supports daily ("d"), weekly ("w"), and monthly ("m") intervals.
 */
class GetStockOhlcvUseCase @Inject constructor(
    private val stockDataRepository: StockDataRepository
) {
    suspend operator fun invoke(
        ticker: String,
        days: Int = 180,
        interval: String = "d"
    ): StockOhlcvData? {
        return stockDataRepository.getStockOhlcv(ticker, days, interval)
    }
}
