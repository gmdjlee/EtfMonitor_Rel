package com.etfmonitor.feature.home.domain.model

/**
 * 통합 초기화 설정 데이터
 *
 * 앱 첫 실행 시 모든 데이터 수집 옵션을 담습니다.
 *
 * @property etfDays ETF 데이터 수집 기간 (일)
 * @property depositPages 증시 자금 동향 페이지 수 (null: 수집 안함)
 * @property fearGreedDays Fear & Greed 데이터 수집 기간 (일, null: 수집 안함)
 * @property oscillatorDays 과매수/과매도 데이터 수집 기간 (일, null: 수집 안함)
 * @property marketIndexDays 시장 지수 데이터 수집 기간 (일, null: 수집 안함)
 */
data class DataInitializationConfig(
    val etfDays: Int,
    val depositPages: Int?,
    val fearGreedDays: Int?,
    val oscillatorDays: Int?,
    val marketIndexDays: Int?
)

/**
 * 데이터 상태 정보
 *
 * @property hasEtfData ETF 데이터 존재 여부
 * @property hasDepositData 증시 자금 동향 데이터 존재 여부
 * @property hasFearGreedData Fear & Greed 데이터 존재 여부
 * @property hasOscillatorData 과매수/과매도 데이터 존재 여부
 */
data class DataStatus(
    val hasEtfData: Boolean,
    val hasDepositData: Boolean,
    val hasFearGreedData: Boolean,
    val hasOscillatorData: Boolean
) {
    val hasAnyData: Boolean
        get() = hasEtfData || hasDepositData || hasFearGreedData || hasOscillatorData

    val hasAllData: Boolean
        get() = hasEtfData && hasDepositData && hasFearGreedData && hasOscillatorData
}
