package com.etfmonitor.core.domain.usecase.krx

import com.etfmonitor.core.data.repository.krx.KrxEtfRepositoryImpl
import com.etfmonitor.core.database.entities.Holding
import javax.inject.Inject

/**
 * UseCase for retrieving ETF holdings (portfolio composition) via kotlin_krx.
 *
 * PHASE 3 MIGRATION (T-011): Replaces PyKrxClient.getHoldings() in EtfRepositoryImpl.
 * Wraps KrxEtf.getPortfolio() (maps from pykrx get_etf_portfolio_deposit_file).
 *
 * TECHNICAL DEBT (C2): Injects concrete KrxEtfRepositoryImpl instead of interface.
 * Rationale: Coexistence phase shortcut. Clean Architecture interfaces deferred to Phase 3 completion.
 */
class GetKrxEtfHoldingsUseCase @Inject constructor(
    private val krxEtfRepository: KrxEtfRepositoryImpl
) {
    suspend operator fun invoke(
        ticker: String,
        date: String
    ): Result<List<Holding>> {
        return krxEtfRepository.getEtfHoldings(ticker, date)
    }
}
