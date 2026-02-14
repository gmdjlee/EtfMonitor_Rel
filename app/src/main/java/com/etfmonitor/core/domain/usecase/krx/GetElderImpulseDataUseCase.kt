package com.etfmonitor.core.domain.usecase.krx

import com.etfmonitor.core.analysis.model.ElderImpulseData
import com.etfmonitor.core.domain.repository.StockDataRepository
import javax.inject.Inject

/**
 * UseCase for retrieving Elder Impulse System analysis data via kotlin_krx.
 *
 * T-012/T-013 MIGRATION: Replaces OscillatorPyClient.getElderImpulseData() in OscillatorViewModel.
 * Wraps KrxStock.getOhlcvByTicker() + TechnicalAnalysisEngine.calculateElderImpulse().
 *
 * Includes EMA13 + MACD + impulse signals (1=bull, 0=neutral, -1=bear).
 */
class GetElderImpulseDataUseCase @Inject constructor(
    private val stockDataRepository: StockDataRepository
) {
    suspend operator fun invoke(
        ticker: String,
        days: Int = 365,
        interval: String = "w"
    ): ElderImpulseData? {
        return stockDataRepository.getElderImpulseData(ticker, days, interval)
    }
}
