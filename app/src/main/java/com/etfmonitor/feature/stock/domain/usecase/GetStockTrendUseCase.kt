package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.feature.stock.domain.model.StockTrend
import com.etfmonitor.feature.stock.domain.repository.StockTrendRepository
import javax.inject.Inject

/**
 * Get Stock Trend Use Case
 *
 * ETF 내 종목의 시계열 추이를 조회하는 유스케이스입니다.
 *
 * @property repository Stock Trend Repository
 */
class GetStockTrendUseCase @Inject constructor(
    private val repository: StockTrendRepository
) {
    /**
     * ETF 내 종목 추이 조회
     *
     * @param etfTicker ETF 종목코드
     * @param stockTicker 종목코드
     * @return 종목 추이 또는 null
     */
    suspend operator fun invoke(etfTicker: String, stockTicker: String): StockTrend? {
        return repository.getStockTrend(etfTicker, stockTicker)
    }
}
