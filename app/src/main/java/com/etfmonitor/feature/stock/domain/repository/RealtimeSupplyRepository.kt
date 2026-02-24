package com.etfmonitor.feature.stock.domain.repository

import com.etfmonitor.feature.stock.domain.model.RealtimeSupplyData

interface RealtimeSupplyRepository {
    suspend fun getRealtimeSupply(ticker: String, useCache: Boolean = true): Result<RealtimeSupplyData>
    suspend fun clearCache(ticker: String)
}
