package com.etfmonitor.feature.home.domain.model

/**
 * 홈 화면 상태를 나타내는 sealed class
 *
 * ## 상태 종류
 * - [Loading]: 초기 로딩 중
 * - [Idle]: 데이터 대기 상태 (요약 정보 포함 가능)
 * - [Initializing]: 초기 데이터 수집 중 (진행률 표시)
 * - [Updating]: 데이터 업데이트 중 (진행률 표시)
 * - [Success]: 작업 성공 메시지
 * - [Error]: 오류 메시지
 */
sealed class HomeState {
    /**
     * 초기 로딩 상태
     */
    object Loading : HomeState()

    /**
     * 대기 상태 - 데이터가 있거나 없는 상태
     *
     * @property hasData 데이터 존재 여부
     * @property lastDate 마지막 업데이트 날짜
     * @property summary 홈 화면 요약 데이터 (null 가능)
     */
    data class Idle(
        val hasData: Boolean,
        val lastDate: String?,
        val summary: HomeSummary? = null
    ) : HomeState()

    /**
     * 초기화 진행 중 상태
     *
     * @property message 현재 진행 상태 메시지
     * @property progress 진행률 (0-100)
     */
    data class Initializing(val message: String, val progress: Int) : HomeState()

    /**
     * 업데이트 진행 중 상태
     *
     * @property message 현재 진행 상태 메시지
     * @property progress 진행률 (0-100)
     */
    data class Updating(val message: String, val progress: Int) : HomeState()

    /**
     * 작업 성공 상태
     *
     * @property message 성공 메시지
     */
    data class Success(val message: String) : HomeState()

    /**
     * 오류 상태
     *
     * @property message 오류 메시지
     */
    data class Error(val message: String) : HomeState()
}
