package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.feature.stock.domain.model.StockAmountRanking
import com.etfmonitor.feature.stock.domain.repository.StockStatisticsRepository
import javax.inject.Inject

/**
 * Get Stock Ranking Use Case
 *
 * 종목 금액순위를 조회하는 유스케이스입니다.
 *
 * @property repository Stock Statistics Repository
 */
class GetStockRankingUseCase @Inject constructor(
    private val repository: StockStatisticsRepository
) {
    /**
     * 종목 금액순위 조회
     *
     * @return 금액순위 목록
     */
    suspend operator fun invoke(): List<StockAmountRanking> {
        return repository.getStockAmountRanking()
    }
}
