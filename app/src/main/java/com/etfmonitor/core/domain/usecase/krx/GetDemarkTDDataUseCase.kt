package com.etfmonitor.core.domain.usecase.krx

import com.etfmonitor.core.analysis.model.DemarkTDData
import com.etfmonitor.core.domain.repository.StockDataRepository
import javax.inject.Inject

/**
 * UseCase for retrieving DeMark TD Setup analysis data via kotlin_krx.
 *
 * T-012/T-013 MIGRATION: Replaces OscillatorPyClient.getDemarkTDData() in OscillatorViewModel.
 * Wraps KrxStock.getOhlcvByTicker() + TechnicalAnalysisEngine.calculateDemarkTD().
 *
 * Includes TD_Sell and TD_Buy counts (exhaustion signals at 9+).
 */
class GetDemarkTDDataUseCase @Inject constructor(
    private val stockDataRepository: StockDataRepository
) {
    suspend operator fun invoke(
        ticker: String,
        days: Int = 365,
        interval: String = "w"
    ): DemarkTDData? {
        return stockDataRepository.getDemarkTDData(ticker, days, interval)
    }
}
