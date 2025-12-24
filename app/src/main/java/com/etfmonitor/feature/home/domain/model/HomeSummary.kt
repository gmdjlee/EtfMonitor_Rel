package com.etfmonitor.feature.home.domain.model

/**
 * 홈 화면 요약 데이터 도메인 모델
 *
 * 홈 화면에 표시되는 시장 상태 요약 정보를 담습니다.
 *
 * @property depositChange 고객예탁금 증감 (억원)
 * @property creditChange 신용잔고 증감 (억원)
 * @property kospiFearGreed KOSPI Fear & Greed Oscillator 값 (-100 ~ 100)
 * @property kosdaqFearGreed KOSDAQ Fear & Greed Oscillator 값 (-100 ~ 100)
 * @property kospiOscillator KOSPI 과매수/과매도 오실레이터 값
 * @property kospiStatus KOSPI 시장 상태 (과매수/중립/과매도)
 * @property kosdaqOscillator KOSDAQ 과매수/과매도 오실레이터 값
 * @property kosdaqStatus KOSDAQ 시장 상태 (과매수/중립/과매도)
 */
data class HomeSummary(
    val depositChange: Double?,
    val creditChange: Double?,
    val kospiFearGreed: Double?,
    val kosdaqFearGreed: Double?,
    val kospiOscillator: Double?,
    val kospiStatus: String?,
    val kosdaqOscillator: Double?,
    val kosdaqStatus: String?
)
