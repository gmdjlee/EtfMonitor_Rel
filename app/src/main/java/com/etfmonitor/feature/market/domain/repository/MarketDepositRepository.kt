package com.etfmonitor.feature.market.domain.repository

import com.etfmonitor.feature.market.domain.model.MarketDeposit
import com.etfmonitor.feature.market.domain.model.MarketDepositData
import kotlinx.coroutines.flow.Flow

/**
 * Market Deposit Repository Interface
 */
interface MarketDepositRepository {
    /**
     * 모든 예탁금 데이터 조회
     */
    fun getAllDeposits(): Flow<List<MarketDeposit>>

    /**
     * 최근 N개 예탁금 데이터 조회
     */
    fun getRecentDeposits(limit: Int = 100): Flow<List<MarketDeposit>>

    /**
     * 날짜 범위로 예탁금 데이터 조회
     *
     * @param startDate 시작 날짜 (yyyy-MM-dd 형식)
     * @param endDate 종료 날짜 (yyyy-MM-dd 형식)
     * @return 해당 기간의 예탁금 데이터 Flow
     */
    fun getByDateRange(startDate: String, endDate: String): Flow<List<MarketDeposit>>

    /**
     * 특정 날짜의 예탁금 데이터 조회
     */
    suspend fun getDepositByDate(date: String): MarketDeposit?

    /**
     * 예탁금 데이터 개수 조회
     */
    suspend fun getDepositCount(): Int

    /**
     * 마지막 업데이트 시간 조회
     */
    suspend fun getLastUpdateTime(): Long?

    /**
     * 예탁금 데이터 초기화
     *
     * @param numPages 수집할 페이지 수
     * @param onProgress 진행 상황 콜백
     * @return 저장된 레코드 수
     */
    suspend fun initializeDeposits(
        numPages: Int = 10,
        onProgress: ((String, Int) -> Unit)? = null
    ): Result<Int>

    /**
     * 예탁금 데이터 업데이트
     */
    suspend fun updateDeposits(numPages: Int = 10): Result<Int>

    /**
     * 예탁금 데이터 스마트 조회 (캐시 확인 후 필요시 업데이트)
     *
     * 12시간 캐싱 전략:
     * - 캐시가 유효하면 DB에서 반환
     * - 캐시가 만료되었거나 없으면 Python으로 갱신 후 반환
     */
    suspend fun getOrUpdateMarketData(limit: Int = 100): MarketDepositData?
}
