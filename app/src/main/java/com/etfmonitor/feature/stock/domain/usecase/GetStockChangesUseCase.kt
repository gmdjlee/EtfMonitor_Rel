package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.feature.stock.domain.model.StockChangeInfo
import com.etfmonitor.feature.stock.domain.repository.StockStatisticsRepository
import javax.inject.Inject

/**
 * Get Stock Changes Use Case
 *
 * 신규 편입/제외/비중 변화 종목을 조회하는 유스케이스입니다.
 *
 * @property repository Stock Statistics Repository
 */
class GetStockChangesUseCase @Inject constructor(
    private val repository: StockStatisticsRepository
) {
    /**
     * 신규 편입 종목 조회
     */
    suspend fun getNewStocks(): List<StockChangeInfo> {
        return repository.getAllNewStocks()
    }

    /**
     * 제외된 종목 조회
     */
    suspend fun getRemovedStocks(): List<StockChangeInfo> {
        return repository.getAllRemovedStocks()
    }

    /**
     * 비중 증가 종목 조회
     */
    suspend fun getIncreasedStocks(): List<StockChangeInfo> {
        return repository.getAllIncreasedStocks()
    }

    /**
     * 비중 감소 종목 조회
     */
    suspend fun getDecreasedStocks(): List<StockChangeInfo> {
        return repository.getAllDecreasedStocks()
    }
}
