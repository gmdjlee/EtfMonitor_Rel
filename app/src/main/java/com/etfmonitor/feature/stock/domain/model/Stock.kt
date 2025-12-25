package com.etfmonitor.feature.stock.domain.model

/**
 * 네트워크 오류를 나타내는 예외
 *
 * 종목 데이터 수집 시 네트워크 문제로 실패한 경우 발생합니다.
 */
class NetworkException(message: String) : Exception(message)

/**
 * Stock Domain Model
 *
 * 종목 정보를 나타내는 도메인 모델입니다.
 * Database Entity와 분리되어 있으며, Mapper를 통해 변환됩니다.
 *
 * @property ticker 종목코드
 * @property name 종목명
 * @property market 시장 (KOSPI, KOSDAQ)
 * @property isEtfHolding ETF 보유 종목 여부
 * @property lastUpdated 마지막 업데이트 시간
 */
data class Stock(
    val ticker: String,
    val name: String,
    val market: String,
    val isEtfHolding: Boolean = false,
    val lastUpdated: Long = 0L
) {
    companion object {
        /**
         * 시장 추론
         * 종목코드 첫자리로 시장 추론 (KOSPI: 0-3, KOSDAQ: 나머지)
         */
        fun inferMarket(ticker: String): String {
            return when {
                ticker.isEmpty() -> "UNKNOWN"
                ticker.first() in '0'..'3' -> "KOSPI"
                else -> "KOSDAQ"
            }
        }
    }
}
