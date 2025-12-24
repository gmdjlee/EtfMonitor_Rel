package com.etfmonitor.feature.market.domain.repository

import com.etfmonitor.feature.market.domain.model.FearGreedIndex
import kotlinx.coroutines.flow.Flow

/**
 * Fear & Greed Index Repository Interface
 */
interface FearGreedRepository {
    /**
     * 특정 시장의 모든 Fear & Greed 데이터 조회
     */
    fun getAllByMarket(market: String): Flow<List<FearGreedIndex>>

    /**
     * 특정 시장의 최근 N일 데이터 조회
     */
    fun getRecentByMarket(market: String, limit: Int = 365): Flow<List<FearGreedIndex>>

    /**
     * 특정 시장, 날짜 범위의 데이터 조회
     */
    fun getByMarketAndDateRange(
        market: String,
        startDate: String,
        endDate: String
    ): Flow<List<FearGreedIndex>>

    /**
     * 특정 시장, 날짜의 데이터 조회
     */
    suspend fun getByMarketAndDate(market: String, date: String): FearGreedIndex?

    /**
     * 특정 시장의 데이터 개수 조회
     */
    suspend fun getCountByMarket(market: String): Int

    /**
     * 특정 시장의 최신 날짜 조회
     */
    suspend fun getLatestDate(market: String): String?

    /**
     * 특정 시장의 마지막 업데이트 시간 조회
     */
    suspend fun getLastUpdateTime(market: String): Long?

    /**
     * Fear & Greed 데이터 초기화
     *
     * @param days 수집할 일수 (실제로는 3배 수집)
     * @param onProgress 진행 상황 콜백
     * @return 저장된 레코드 수
     */
    suspend fun initializeFearGreed(
        days: Int = 365,
        onProgress: ((String, Int) -> Unit)? = null
    ): Result<Int>

    /**
     * Fear & Greed 데이터 업데이트
     */
    suspend fun updateFearGreed(): Result<Int>
}
