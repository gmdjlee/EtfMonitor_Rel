package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.feature.stock.domain.model.financial.FinancialSummary
import com.etfmonitor.feature.stock.domain.model.financial.toSummary
import com.etfmonitor.feature.stock.domain.repository.FinancialRepository
import javax.inject.Inject

class GetFinancialSummaryUseCase @Inject constructor(
    private val repository: FinancialRepository
) {
    suspend operator fun invoke(
        ticker: String,
        name: String,
        useCache: Boolean = true
    ): Result<FinancialSummary> {
        return repository.getFinancialData(ticker, name, useCache).map { it.toSummary() }
    }

    suspend fun refresh(
        ticker: String,
        name: String
    ): Result<FinancialSummary> {
        return repository.refreshFinancialData(ticker, name).map { it.toSummary() }
    }
}
