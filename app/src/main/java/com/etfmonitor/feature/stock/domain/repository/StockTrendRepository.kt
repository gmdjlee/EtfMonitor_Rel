package com.etfmonitor.feature.stock.domain.repository

import com.etfmonitor.feature.stock.domain.model.StockTrend

/**
 * Stock Trend Repository Interface
 *
 * Domain 레이어에 정의된 종목 추이 Repository 인터페이스입니다.
 * 구현체는 Data 레이어(StockTrendRepositoryImpl)에서 제공합니다.
 *
 * ## 주요 기능
 * - ETF 내 종목의 시계열 추이 조회
 *
 * ## 스레드 안전성
 * - 모든 suspend 함수는 withContext(Dispatchers.IO)로 IO 스레드에서 실행됩니다.
 */
interface StockTrendRepository {

    /**
     * ETF 내 종목 추이 조회
     *
     * @param etfTicker ETF 종목코드
     * @param stockTicker 종목코드
     * @return 종목 추이 또는 null
     */
    suspend fun getStockTrend(etfTicker: String, stockTicker: String): StockTrend?
}
