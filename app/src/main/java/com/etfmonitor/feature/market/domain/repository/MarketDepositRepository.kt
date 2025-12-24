package com.etfmonitor.feature.market.domain.repository

import com.etfmonitor.feature.market.domain.model.MarketDeposit
import com.etfmonitor.feature.market.domain.model.MarketDepositTrend
import kotlinx.coroutines.flow.Flow

/**
 * 증시 자금 Repository 인터페이스
 */
interface MarketDepositRepository {
    /**
     * 모든 증시 자금 데이터 조회
     */
    fun getAllDeposits(): Flow<List<MarketDeposit>>

    /**
     * 최근 N개 증시 자금 데이터 조회
     */
    fun getRecentDeposits(limit: Int = 100): Flow<List<MarketDeposit>>

    /**
     * 특정 날짜의 증시 자금 데이터 조회
     */
    suspend fun getDepositByDate(date: String): MarketDeposit?

    /**
     * 증시 자금 데이터 개수 조회
     */
    suspend fun getDepositCount(): Int

    /**
     * 마지막 업데이트 시간 조회
     */
    suspend fun getLastUpdateTime(): Long?

    /**
     * 증시 자금 데이터 초기화 (Python에서 가져와서 DB에 저장)
     *
     * @param numPages 수집할 페이지 수 (기본 10)
     * @param onProgress 진행률 콜백
     * @return 저장된 레코드 수
     */
    suspend fun initializeDeposits(
        numPages: Int = 10,
        onProgress: ((String, Int) -> Unit)? = null
    ): Result<Int>

    /**
     * 증시 자금 데이터 업데이트
     */
    suspend fun updateDeposits(numPages: Int = 10): Result<Int>

    /**
     * 증시 자금 데이터 가져오기 (스마트 업데이트)
     * DB에 데이터가 있고 최신이면 DB에서, 없거나 오래되면 업데이트
     *
     * @param limit 최대 조회 개수
     * @return 증시 자금 동향 데이터 (차트/통계용)
     */
    suspend fun getOrUpdateMarketData(limit: Int = 100): MarketDepositTrend?
}
