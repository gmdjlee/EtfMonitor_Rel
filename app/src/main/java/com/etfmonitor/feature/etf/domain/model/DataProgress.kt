package com.etfmonitor.feature.etf.domain.model

/**
 * 데이터 수집 진행 상태
 *
 * 초기화 및 업데이트 진행 상황을 나타내는 sealed class입니다.
 */
sealed class DataProgress {
    /**
     * 진행 중 상태
     * @property message 진행 상황 메시지
     * @property progress 진행률 (0-100)
     */
    data class Loading(val message: String, val progress: Int) : DataProgress()

    /**
     * 완료 상태
     * @property message 완료 메시지
     */
    data class Success(val message: String) : DataProgress()

    /**
     * 오류 상태
     * @property message 오류 메시지
     */
    data class Error(val message: String) : DataProgress()
}
