package com.etfmonitor.feature.market.domain.repository

import com.etfmonitor.feature.market.domain.model.MarketIndex
import kotlinx.coroutines.flow.Flow

/**
 * 시장 지수 Repository 인터페이스
 */
interface MarketIndexRepository {
    /**
     * 특정 시장의 모든 데이터 조회
     */
    fun getAllByMarket(market: String): Flow<List<MarketIndex>>

    /**
     * 특정 시장의 특정 날짜 데이터 조회
     */
    suspend fun getByMarketAndDate(market: String, date: String): MarketIndex?

    /**
     * 특정 시장의 최근 N개 데이터 조회
     */
    fun getRecentByMarket(market: String, limit: Int): Flow<List<MarketIndex>>

    /**
     * 특정 시장의 기간별 데이터 조회
     */
    fun getByMarketAndDateRange(market: String, startDate: String, endDate: String): Flow<List<MarketIndex>>

    /**
     * 특정 시장의 기간별 데이터 조회 (suspend)
     */
    suspend fun getByMarketAndDateRangeSuspend(market: String, startDate: String, endDate: String): List<MarketIndex>

    /**
     * 모든 시장의 특정 날짜 데이터 조회
     */
    suspend fun getByDate(date: String): List<MarketIndex>

    /**
     * 데이터 삽입/업데이트
     */
    suspend fun insertAll(indices: List<MarketIndex>)
    suspend fun insert(index: MarketIndex)

    /**
     * 특정 시장 데이터 삭제
     */
    suspend fun deleteByMarket(market: String)

    /**
     * 모든 데이터 삭제
     */
    suspend fun deleteAll()

    /**
     * 특정 시장의 데이터 개수
     */
    suspend fun getCountByMarket(market: String): Int

    /**
     * 특정 시장의 최신 날짜
     */
    suspend fun getLatestDate(market: String): String?

    /**
     * 특정 시장의 최종 업데이트 시간
     */
    suspend fun getLastUpdateTime(market: String): Long?

    /**
     * 데이터 존재 여부 확인
     */
    suspend fun hasData(market: String): Boolean

    /**
     * 최근 N일의 데이터 존재 여부 확인
     */
    suspend fun hasDataSince(market: String, startDate: String): Boolean

    /**
     * 모든 날짜 목록 조회
     */
    suspend fun getAllDates(): List<String>

    /**
     * 시장 지수 데이터 초기화
     *
     * @param days 수집할 일수 (기본 30일)
     * @return 저장된 레코드 수
     */
    suspend fun initializeMarketIndex(days: Int = 30): Result<Int>

    /**
     * 시장 지수 데이터 업데이트
     *
     * @param days 수집할 일수 (기본 30일)
     * @return 저장된 레코드 수
     */
    suspend fun updateMarketIndex(days: Int = 30): Result<Int>
}
