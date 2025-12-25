package com.etfmonitor.feature.stock.domain.repository

import com.etfmonitor.feature.stock.domain.model.CashDepositTrend
import com.etfmonitor.feature.stock.domain.model.StockAggregatedTrend
import com.etfmonitor.feature.stock.domain.model.StockAmountRanking
import com.etfmonitor.feature.stock.domain.model.StockAnalysisResult
import com.etfmonitor.feature.stock.domain.model.StockChangeInfo

/**
 * Stock Statistics Repository Interface
 *
 * Domain 레이어에 정의된 종목 통계 Repository 인터페이스입니다.
 * 구현체는 Data 레이어(StockStatisticsRepositoryImpl)에서 제공합니다.
 *
 * ## 주요 기능
 * - 종목 금액순위 조회
 * - 신규/제외/비중변화 종목 조회
 * - 종목 분석 (ETF별 보유 분석)
 * - 원화예금 추이 조회
 *
 * ## 스레드 안전성
 * - 모든 suspend 함수는 withContext(Dispatchers.IO)로 IO 스레드에서 실행됩니다.
 */
interface StockStatisticsRepository {

    // ========== 통계 날짜 ==========

    /**
     * 통계 날짜 조회 (최신일, 전일)
     *
     * @return (최신일, 전일) 쌍 또는 null
     */
    suspend fun getStatisticsDates(): Pair<String, String>?

    // ========== 금액순위 ==========

    /**
     * 종목 금액순위 조회
     *
     * @return 금액순위 목록
     */
    suspend fun getStockAmountRanking(): List<StockAmountRanking>

    // ========== 종목 변화 ==========

    /**
     * 신규 편입 종목 조회
     *
     * @return 신규 편입 종목 목록
     */
    suspend fun getAllNewStocks(): List<StockChangeInfo>

    /**
     * 제외된 종목 조회
     *
     * @return 제외된 종목 목록
     */
    suspend fun getAllRemovedStocks(): List<StockChangeInfo>

    /**
     * 비중 증가 종목 조회
     *
     * @return 비중 증가 종목 목록
     */
    suspend fun getAllIncreasedStocks(): List<StockChangeInfo>

    /**
     * 비중 감소 종목 조회
     *
     * @return 비중 감소 종목 목록
     */
    suspend fun getAllDecreasedStocks(): List<StockChangeInfo>

    // ========== 종목 분석 ==========

    /**
     * 종목 검색 (ETF 보유 종목)
     *
     * @param query 검색어
     * @return 검색 결과 목록
     */
    suspend fun searchStocks(query: String): List<StockSearchResult>

    /**
     * 종목 분석 (ETF별 보유 분석)
     *
     * @param stockTicker 종목코드
     * @return 분석 결과 또는 null
     */
    suspend fun analyzeStock(stockTicker: String): StockAnalysisResult?

    // ========== 원화예금 추이 ==========

    /**
     * 원화예금 추이 조회
     *
     * @return 원화예금 추이 목록
     */
    suspend fun getCashDepositTrend(): List<CashDepositTrend>

    // ========== 종목 통합 추이 ==========

    /**
     * 종목 통합 추이 조회
     *
     * @param stockTicker 종목코드
     * @return 종목 통합 추이 또는 null
     */
    suspend fun getStockAggregatedTrend(stockTicker: String): StockAggregatedTrend?
}

/**
 * Stock Search Result
 *
 * 종목 검색 결과
 */
data class StockSearchResult(
    val stockTicker: String,
    val stockName: String
)
