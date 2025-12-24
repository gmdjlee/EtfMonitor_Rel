package com.etfmonitor.feature.stock.domain.model

/**
 * Stock Analysis Domain Model
 *
 * 종목 수급 분석 데이터를 나타내는 도메인 모델입니다.
 * Python에서 수집한 데이터를 캐싱하고 분석에 사용합니다.
 *
 * @property ticker 종목코드
 * @property name 종목명
 * @property dates 날짜 목록
 * @property marketCap 시가총액 목록
 * @property foreign5d 외국인 5일 누적 매매 목록
 * @property institution5d 기관 5일 누적 매매 목록
 */
data class StockAnalysis(
    val ticker: String,
    val name: String,
    val dates: List<String>,
    val marketCap: List<Long>,
    val foreign5d: List<Long>,
    val institution5d: List<Long>
) {
    val isEmpty: Boolean
        get() = dates.isEmpty()

    val latestDate: String?
        get() = dates.lastOrNull()

    val dataCount: Int
        get() = dates.size
}
