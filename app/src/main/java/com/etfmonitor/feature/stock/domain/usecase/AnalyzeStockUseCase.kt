package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.feature.stock.domain.model.StockAnalysisResult
import com.etfmonitor.feature.stock.domain.repository.StockStatisticsRepository
import com.etfmonitor.feature.stock.domain.repository.StockSearchResult
import javax.inject.Inject

/**
 * Analyze Stock Use Case
 *
 * 특정 종목의 ETF별 보유 현황을 분석하는 유스케이스입니다.
 *
 * @property repository Stock Statistics Repository
 */
class AnalyzeStockUseCase @Inject constructor(
    private val repository: StockStatisticsRepository
) {
    /**
     * 종목 분석 (ETF별 보유 분석)
     *
     * @param stockTicker 종목코드
     * @return 분석 결과 또는 null
     */
    suspend operator fun invoke(stockTicker: String): StockAnalysisResult? {
        return repository.analyzeStock(stockTicker)
    }

    /**
     * 종목 검색 (ETF 보유 종목)
     *
     * @param query 검색어
     * @return 검색 결과 목록
     */
    suspend fun searchStocks(query: String): List<StockSearchResult> {
        return repository.searchStocks(query)
    }
}
