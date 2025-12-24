package com.etfmonitor.feature.market.domain.repository

import com.etfmonitor.feature.market.domain.model.FearGreed
import kotlinx.coroutines.flow.Flow

/**
 * Fear & Greed Repository 인터페이스
 */
interface FearGreedRepository {
    /**
     * 특정 시장의 모든 데이터 조회
     */
    fun getAllByMarket(market: String): Flow<List<FearGreed>>

    /**
     * 특정 시장의 최근 N일 데이터 조회
     */
    fun getRecentByMarket(market: String, limit: Int = 365): Flow<List<FearGreed>>

    /**
     * 특정 시장의 특정 기간 데이터 조회
     */
    fun getByMarketAndDateRange(market: String, startDate: String, endDate: String): Flow<List<FearGreed>>

    /**
     * 특정 시장의 특정 날짜 데이터 조회
     */
    suspend fun getByMarketAndDate(market: String, date: String): FearGreed?

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
     * Fear & Greed Index 데이터 초기화
     *
     * 주의: Python 분석 과정에서 대량의 데이터 손실이 발생하므로
     * 실제로는 약 3배의 데이터를 수집하여 원하는 기간만큼 남도록 합니다.
     *
     * @param days 데이터 수집 기간 (기본 365일)
     * @param onProgress 진행률 콜백 (message, progress)
     * @return 저장된 레코드 수
     */
    suspend fun initializeFearGreed(
        days: Int = 365,
        onProgress: ((String, Int) -> Unit)? = null
    ): Result<Int>

    /**
     * Fear & Greed Index 데이터 업데이트 (최근 데이터만 갱신)
     */
    suspend fun updateFearGreed(): Result<Int>
}
