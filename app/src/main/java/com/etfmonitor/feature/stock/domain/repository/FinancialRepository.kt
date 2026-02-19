package com.etfmonitor.feature.stock.domain.repository

import com.etfmonitor.feature.stock.domain.model.financial.FinancialData

interface FinancialRepository {
    suspend fun getFinancialData(ticker: String, name: String, useCache: Boolean = true): Result<FinancialData>
    suspend fun refreshFinancialData(ticker: String, name: String): Result<FinancialData>
    suspend fun clearCache(ticker: String)
    suspend fun clearExpiredCache()
}
