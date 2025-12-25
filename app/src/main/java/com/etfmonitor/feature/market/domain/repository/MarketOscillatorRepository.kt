package com.etfmonitor.feature.market.domain.repository

import com.etfmonitor.feature.market.domain.model.MarketOscillator
import kotlinx.coroutines.flow.Flow

/**
 * Market Oscillator Repository Interface
 */
interface MarketOscillatorRepository {
    /**
     * 특정 시장의 모든 데이터 조회
     */
    fun getMarketData(market: String): Flow<List<MarketOscillator>>

    /**
     * 특정 시장의 최근 N일 데이터 조회
     */
    fun getRecentData(market: String, limit: Int = 15): Flow<List<MarketOscillator>>

    /**
     * 특정 시장의 날짜 범위 데이터 조회
     */
    fun getDataByDateRange(
        market: String,
        startDate: String,
        endDate: String
    ): Flow<List<MarketOscillator>>

    /**
     * 특정 시장의 최신 데이터 조회
     */
    suspend fun getLatestData(market: String): MarketOscillator?

    /**
     * 특정 시장의 데이터 개수 조회
     */
    suspend fun getDataCount(market: String): Int

    /**
     * 시장 오실레이터 데이터 초기화
     *
     * @param market 시장 코드 (KOSPI, KOSDAQ)
     * @param days 수집할 일수 (기본 365일)
     * @param onProgress 진행 상황 콜백
     * @return 저장된 레코드 수
     */
    suspend fun initializeMarketData(
        market: String,
        days: Int = 365,
        onProgress: ((String, Int) -> Unit)? = null
    ): Result<Int>

    /**
     * 시장 오실레이터 데이터 업데이트 (최근 30일)
     */
    suspend fun updateMarketData(market: String): Result<Int>

    /**
     * 특정 시장 데이터 삭제
     */
    suspend fun deleteMarketData(market: String)

    /**
     * 모든 데이터 삭제
     */
    suspend fun deleteAll()

    /**
     * 다이얼로그 닫힘 상태 확인
     */
    suspend fun isDialogDismissed(): Boolean

    /**
     * 다이얼로그 닫힘 상태 저장
     */
    suspend fun saveDialogDismissed()
}
