package com.etfmonitor.feature.etf.domain.repository

import com.etfmonitor.feature.etf.domain.model.ComparisonResult
import com.etfmonitor.feature.etf.domain.model.DataStatus
import com.etfmonitor.feature.etf.domain.model.Etf
import kotlinx.coroutines.flow.Flow

/**
 * ETF Repository Interface
 *
 * Domain 레이어에 정의된 Repository 인터페이스입니다.
 * 구현체는 Data 레이어(EtfRepositoryImpl)에서 제공합니다.
 *
 * ## 스레드 안전성
 * - Flow 반환 함수는 flowOn(Dispatchers.IO)로 백그라운드에서 실행됩니다.
 * - suspend 함수는 withContext(Dispatchers.IO)로 IO 스레드에서 실행됩니다.
 */
interface EtfRepository {

    // ========== ETF List ==========

    /**
     * 모든 ETF 목록 조회
     *
     * @return ETF 목록 Flow
     */
    fun getAllEtfs(): Flow<List<Etf>>

    /**
     * ETF 검색
     *
     * @param query 검색어 (ticker 또는 name)
     * @return 검색 결과 Flow
     */
    fun searchEtfs(query: String): Flow<List<Etf>>

    // ========== Data Status ==========

    /**
     * 데이터 존재 여부 확인
     *
     * @return 데이터가 있으면 true
     */
    suspend fun hasData(): Boolean

    /**
     * 데이터 상태 조회 (데이터 유무 및 최신 날짜)
     *
     * @return DataStatus 객체
     */
    suspend fun getDataStatus(): DataStatus

    /**
     * 최신 데이터 날짜 조회
     *
     * @return 최신 날짜 (yyyy-MM-dd) 또는 null
     */
    suspend fun getLatestDate(): String?

    // ========== ETF Detail ==========

    /**
     * ETF 정보 조회
     *
     * @param ticker ETF 종목코드
     * @return ETF 정보 또는 null
     */
    suspend fun getEtf(ticker: String): Etf?

    /**
     * ETF 보유 종목 비교 분석
     *
     * 최근 2일간의 보유 종목 변화를 비교하여 신규/증가/감소/유지/제외 상태를 분석합니다.
     *
     * @param etfTicker ETF 종목코드
     * @return 비교 결과 또는 null (데이터가 없는 경우)
     */
    suspend fun getComparison(etfTicker: String): ComparisonResult?
}
