package com.etfmonitor.feature.home.domain.repository

import com.etfmonitor.feature.home.domain.model.DataInitializationConfig
import com.etfmonitor.feature.home.domain.model.DataStatus
import com.etfmonitor.feature.home.domain.model.HomeSummary

/**
 * Home 기능의 Repository 인터페이스
 *
 * 홈 화면에서 필요한 데이터 접근을 추상화합니다.
 * Domain 레이어에 위치하여 Data 레이어의 구현체에 의존하지 않습니다.
 */
interface HomeRepository {

    /**
     * ETF 데이터 존재 여부 확인
     */
    suspend fun hasEtfData(): Boolean

    /**
     * 마지막 데이터 업데이트 날짜 조회
     */
    suspend fun getLatestDate(): String?

    /**
     * 홈 화면 요약 데이터 조회
     */
    suspend fun getHomeSummary(): HomeSummary?

    /**
     * 모든 데이터 상태 확인
     */
    suspend fun getDataStatus(): DataStatus

    /**
     * 설정 값 조회
     */
    suspend fun getSetting(key: String): String?

    /**
     * 설정 값 저장
     */
    suspend fun saveSetting(key: String, value: String)

    /**
     * 첫 실행 여부 확인 및 다이얼로그 표시 여부 결정
     *
     * @return 통합 초기화 다이얼로그 표시 필요 여부
     */
    suspend fun shouldShowUnifiedInitDialog(): Boolean

    /**
     * 기본 수집 일수 조회
     */
    suspend fun getDefaultDays(): Int
}
