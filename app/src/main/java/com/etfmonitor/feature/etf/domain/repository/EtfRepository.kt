package com.etfmonitor.feature.etf.domain.repository

import com.etfmonitor.feature.etf.domain.model.ComparisonResult
import com.etfmonitor.feature.etf.domain.model.DataProgress
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

    /**
     * ETF 보유 종목 비교 분석 (날짜 범위 지정)
     *
     * 지정된 날짜 범위 내에서 가장 최근과 가장 오래된 데이터를 비교합니다.
     *
     * @param etfTicker ETF 종목코드
     * @param startDate 시작일 (yyyy-MM-dd)
     * @param endDate 종료일 (yyyy-MM-dd)
     * @return 비교 결과 또는 null (데이터가 없는 경우)
     */
    suspend fun getComparisonInRange(
        etfTicker: String,
        startDate: String,
        endDate: String
    ): ComparisonResult?

    /**
     * 전체 데이터의 날짜 목록 조회
     *
     * @param limit 최대 조회 개수
     * @return 날짜 목록 (내림차순)
     */
    suspend fun getAvailableDates(limit: Int = 100): List<String>

    // ========== Data Collection ==========

    /**
     * 초기 데이터 수집
     *
     * 지정된 일수만큼의 영업일에 대해 ETF 데이터를 수집합니다.
     *
     * @param days 수집할 영업일 수 (기본값: 25일)
     * @return 진행 상태 Flow
     */
    fun initializeData(days: Int = 25): Flow<DataProgress>

    /**
     * 데이터 업데이트
     *
     * 마지막 수집일 이후의 새로운 영업일 데이터만 수집합니다.
     *
     * @return 진행 상태 Flow
     */
    fun updateData(): Flow<DataProgress>

    /**
     * 데이터베이스 초기화
     *
     * 모든 ETF 및 Holdings 데이터를 삭제합니다.
     */
    suspend fun resetDatabase()

    /**
     * 지정 기간 외의 데이터 삭제
     *
     * 새 기간의 시작일 이전 데이터만 삭제하여:
     * - 기존 기간 내 데이터는 유지
     * - initializeData() 호출 시 빈 날짜만 수집
     *
     * @param days 유지할 기간 (일)
     * @return 삭제된 날짜 수
     */
    suspend fun trimDataToPeriod(days: Int): Int

    /**
     * 기본 수집 일수 조회
     *
     * @return 기본 수집 일수
     */
    suspend fun getDefaultDays(): Int

    /**
     * 기본 수집 일수 설정
     *
     * @param days 기본 수집 일수
     */
    suspend fun setDefaultDays(days: Int)

    /**
     * 테마 키워드 목록 조회
     *
     * @return 테마 키워드 목록
     */
    suspend fun getThemes(): List<String>

    /**
     * 테마 키워드 추가
     *
     * @param theme 추가할 테마
     */
    suspend fun addTheme(theme: String)

    /**
     * 테마 키워드 삭제
     *
     * @param theme 삭제할 테마
     */
    suspend fun removeTheme(theme: String)

    /**
     * 제외 키워드 목록 조회
     *
     * @return 제외 키워드 목록
     */
    suspend fun getExclusions(): List<String>

    /**
     * 제외 키워드 추가
     *
     * @param keyword 추가할 키워드
     */
    suspend fun addExclusion(keyword: String)

    /**
     * 제외 키워드 삭제
     *
     * @param keyword 삭제할 키워드
     */
    suspend fun removeExclusion(keyword: String)
}
