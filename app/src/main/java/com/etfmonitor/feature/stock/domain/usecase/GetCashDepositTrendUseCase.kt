package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.feature.stock.domain.model.CashDepositTrend
import com.etfmonitor.feature.stock.domain.repository.StockStatisticsRepository
import javax.inject.Inject

/**
 * Get Cash Deposit Trend Use Case
 *
 * 원화예금 추이를 조회하는 유스케이스입니다.
 *
 * @property repository Stock Statistics Repository
 */
class GetCashDepositTrendUseCase @Inject constructor(
    private val repository: StockStatisticsRepository
) {
    /**
     * 원화예금 추이 조회
     *
     * @return 원화예금 추이 목록
     */
    suspend operator fun invoke(): List<CashDepositTrend> {
        return repository.getCashDepositTrend()
    }
}
