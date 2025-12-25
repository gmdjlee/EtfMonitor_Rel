package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.core.analysis.model.StockData
import com.etfmonitor.feature.stock.domain.repository.StockAnalysisRepository
import javax.inject.Inject

/**
 * Get Stock Analysis Use Case
 *
 * 종목 수급 분석 데이터를 조회하는 유스케이스입니다.
 * 24시간 캐싱 로직을 통해 효율적으로 데이터를 관리합니다.
 *
 * @property repository Stock Analysis Repository
 */
class GetStockAnalysisUseCase @Inject constructor(
    private val repository: StockAnalysisRepository
) {
    /**
     * 종목 분석 데이터 조회
     *
     * @param ticker 종목코드
     * @param days 조회할 일수 (기본값: 180)
     * @return 분석 데이터 또는 null
     */
    suspend operator fun invoke(ticker: String, days: Int = 180): StockData? {
        return repository.getStockAnalysis(ticker, days)
    }
}
