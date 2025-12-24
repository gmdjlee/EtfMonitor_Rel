package com.etfmonitor.feature.stock.domain.repository

import com.etfmonitor.feature.stock.domain.model.Stock
import kotlinx.coroutines.flow.Flow

/**
 * Stock Repository Interface
 *
 * Domain 레이어에 정의된 종목 마스터 Repository 인터페이스입니다.
 * 구현체는 Data 레이어(StockRepositoryImpl)에서 제공합니다.
 *
 * ## 주요 기능
 * - 전체 종목 관리 (stocks 테이블)
 * - ETF 보유 종목 자동 동기화
 * - 종목명 조회
 *
 * ## 스레드 안전성
 * - Flow 반환 함수는 flowOn(Dispatchers.IO)로 백그라운드에서 실행됩니다.
 * - suspend 함수는 withContext(Dispatchers.IO)로 IO 스레드에서 실행됩니다.
 */
interface StockRepository {

    // ========== 조회 ==========

    /**
     * 모든 종목 조회
     *
     * @return 종목 목록 Flow
     */
    fun getAllStocks(): Flow<List<Stock>>

    /**
     * 종목 검색
     *
     * @param query 검색어 (ticker 또는 name)
     * @return 검색 결과 Flow
     */
    fun searchStocks(query: String): Flow<List<Stock>>

    /**
     * ETF 보유 종목 조회
     *
     * @return ETF가 보유한 종목 목록 Flow
     */
    fun getEtfHoldingStocks(): Flow<List<Stock>>

    /**
     * 시장별 종목 조회
     *
     * @param market 시장 (KOSPI, KOSDAQ)
     * @return 시장별 종목 목록 Flow
     */
    fun getStocksByMarket(market: String): Flow<List<Stock>>

    /**
     * 종목 조회
     *
     * @param ticker 종목코드
     * @return 종목 정보 또는 null
     */
    suspend fun getStock(ticker: String): Stock?

    /**
     * 종목명 조회
     *
     * @param ticker 종목코드
     * @return 종목명 (없으면 ticker 반환)
     */
    suspend fun getStockName(ticker: String): String

    /**
     * 종목 수 조회
     *
     * @return 종목 수
     */
    suspend fun getStockCount(): Int

    /**
     * ETF 보유 종목 수 조회
     *
     * @return ETF 보유 종목 수
     */
    suspend fun getEtfHoldingCount(): Int

    /**
     * 마지막 업데이트 시간 조회
     *
     * @return 마지막 업데이트 시간 (milliseconds) 또는 null
     */
    suspend fun getLastUpdateTime(): Long?

    // ========== ETF 보유 종목 동기화 ==========

    /**
     * 단일 종목 동기화 (ETF 보유 종목에서 호출)
     *
     * @param ticker 종목코드
     * @param name 종목명
     */
    suspend fun syncFromHolding(ticker: String, name: String)

    /**
     * 일괄 종목 동기화 (ETF 데이터 수집 후 호출)
     *
     * @param holdings (종목코드, 종목명) 쌍 목록
     */
    suspend fun syncFromHoldings(holdings: List<Pair<String, String>>)

    // ========== 전체 종목 초기화 ==========

    /**
     * 종목 데이터 초기화 (Python에서 가져와서 DB에 저장)
     *
     * @return Result.success(종목 수) 또는 Result.failure(Exception)
     */
    suspend fun initializeStocks(): Result<Int>

    /**
     * 종목 데이터 업데이트
     *
     * @return Result.success(종목 수) 또는 Result.failure(Exception)
     */
    suspend fun updateStocks(): Result<Int>
}
