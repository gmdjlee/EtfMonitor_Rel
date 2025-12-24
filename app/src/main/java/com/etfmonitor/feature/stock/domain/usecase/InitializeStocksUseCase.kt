package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.feature.stock.domain.repository.StockRepository
import javax.inject.Inject

/**
 * Initialize Stocks Use Case
 *
 * 종목 데이터를 초기화하는 유스케이스입니다.
 * Python에서 전체 종목 목록을 가져와 DB에 저장합니다.
 *
 * @property repository Stock Repository
 */
class InitializeStocksUseCase @Inject constructor(
    private val repository: StockRepository
) {
    /**
     * 종목 데이터 초기화
     *
     * @return Result.success(종목 수) 또는 Result.failure(Exception)
     */
    suspend operator fun invoke(): Result<Int> {
        return repository.initializeStocks()
    }
}
