package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.feature.stock.domain.repository.StockStatisticsRepository
import javax.inject.Inject

/**
 * Get Statistics Dates Use Case
 *
 * 통계 날짜(최신일, 전일)를 조회하는 유스케이스입니다.
 *
 * @property repository Stock Statistics Repository
 */
class GetStatisticsDatesUseCase @Inject constructor(
    private val repository: StockStatisticsRepository
) {
    /**
     * 통계 날짜 조회
     *
     * @return (최신일, 전일) 쌍 또는 null
     */
    suspend operator fun invoke(): Pair<String, String>? {
        return repository.getStatisticsDates()
    }
}
