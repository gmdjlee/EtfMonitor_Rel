package com.krxkt

import com.krxkt.api.KrxClient
import com.krxkt.api.KrxEndpoints
import com.krxkt.model.IndexInfo
import com.krxkt.model.IndexMarket
import com.krxkt.model.IndexOhlcv
import com.krxkt.parser.KrxJsonParser
import com.krxkt.util.DateUtils

/**
 * KRX 지수 데이터 API
 *
 * pykrx의 index 모듈과 호환되는 Kotlin 구현
 *
 * 주요 지수 티커:
 * - "1001" = KOSPI
 * - "1028" = KOSPI 200
 * - "2001" = KOSDAQ
 * - "2203" = KOSDAQ 150
 *
 * 티커 구조: [타입코드 1자리] + [지수코드 3자리]
 * - 타입코드: 1=KOSPI, 2=KOSDAQ, 3=파생, 4=테마
 *
 * 사용 예:
 * ```
 * val krxIndex = KrxIndex()
 *
 * // KOSPI 200 기간 조회
 * val history = krxIndex.getOhlcvByTicker("20210101", "20210131", "1028")
 *
 * // 지수 목록 조회
 * val indexList = krxIndex.getIndexList("20210122")
 * ```
 *
 * @param client HTTP 클라이언트 (테스트용 주입 가능)
 */
class KrxIndex(
    private val client: KrxClient = KrxClient()
) {
    /**
     * 지수 OHLCV 기간 조회
     *
     * pykrx: index.get_index_ohlcv("20210101", "20210131", "1028")
     *
     * 주의: KRX API는 최대 2년 조회 제한이 있음
     *
     * @param startDate 시작 날짜 (yyyyMMdd)
     * @param endDate 종료 날짜 (yyyyMMdd)
     * @param ticker 지수 티커 (예: "1028" = KOSPI 200)
     * @return 날짜별 OHLCV 리스트
     */
    suspend fun getOhlcvByTicker(
        startDate: String,
        endDate: String,
        ticker: String
    ): List<IndexOhlcv> {
        DateUtils.validateDateRange(startDate, endDate)
        require(ticker.length >= 2) { "Invalid index ticker: $ticker" }

        // 티커 파싱: "1028" → indIdx="1", indIdx2="028"
        // KRX MDCSTAT00301 파라미터: indIdx=시장구분, indIdx2=지수코드
        val indIdx = ticker.substring(0, 1)
        val indIdx2 = ticker.substring(1).padStart(3, '0')

        val params = mapOf(
            "bld" to KrxEndpoints.Bld.INDEX_OHLCV,
            "indIdx" to indIdx,
            "indIdx2" to indIdx2,
            "strtDd" to startDate,
            "endDd" to endDate
        )

        val response = client.post(params)
        val jsonArray = KrxJsonParser.parseOutBlock(response)

        return jsonArray.mapNotNull { IndexOhlcv.fromJson(it) }
    }

    /**
     * 지수 목록 조회
     *
     * @param date 조회 날짜 (yyyyMMdd)
     * @param market 시장 구분 (기본: ALL)
     * @return 지수 정보 리스트
     */
    suspend fun getIndexList(
        date: String,
        market: IndexMarket = IndexMarket.ALL
    ): List<IndexInfo> {
        DateUtils.validateDate(date)

        val params = buildMap {
            put("bld", KrxEndpoints.Bld.INDEX_LIST)
            put("trdDd", date)
            if (market != IndexMarket.ALL) {
                put("indTpCd", market.code)
            }
        }

        val response = client.post(params)
        val jsonArray = KrxJsonParser.parseOutBlock(response)

        return jsonArray.mapNotNull { IndexInfo.fromJson(it) }
    }

    /**
     * 지수 이름 조회
     *
     * @param ticker 지수 티커 (예: "1028")
     * @param date 기준 날짜
     * @return 지수명 (예: "코스피 200"), 없으면 null
     */
    suspend fun getIndexName(ticker: String, date: String): String? {
        val indexList = getIndexList(date, IndexMarket.ALL)
        return indexList.find { it.ticker == ticker }?.name
    }

    /**
     * KOSPI 지수 조회
     *
     * @param startDate 시작 날짜 (yyyyMMdd)
     * @param endDate 종료 날짜 (yyyyMMdd)
     * @return KOSPI 지수 OHLCV 리스트
     */
    suspend fun getKospi(startDate: String, endDate: String): List<IndexOhlcv> {
        return getOhlcvByTicker(startDate, endDate, TICKER_KOSPI)
    }

    /**
     * KOSPI 200 지수 조회
     *
     * @param startDate 시작 날짜 (yyyyMMdd)
     * @param endDate 종료 날짜 (yyyyMMdd)
     * @return KOSPI 200 지수 OHLCV 리스트
     */
    suspend fun getKospi200(startDate: String, endDate: String): List<IndexOhlcv> {
        return getOhlcvByTicker(startDate, endDate, TICKER_KOSPI_200)
    }

    /**
     * KOSDAQ 지수 조회
     *
     * @param startDate 시작 날짜 (yyyyMMdd)
     * @param endDate 종료 날짜 (yyyyMMdd)
     * @return KOSDAQ 지수 OHLCV 리스트
     */
    suspend fun getKosdaq(startDate: String, endDate: String): List<IndexOhlcv> {
        return getOhlcvByTicker(startDate, endDate, TICKER_KOSDAQ)
    }

    /**
     * KOSDAQ 150 지수 조회
     *
     * @param startDate 시작 날짜 (yyyyMMdd)
     * @param endDate 종료 날짜 (yyyyMMdd)
     * @return KOSDAQ 150 지수 OHLCV 리스트
     */
    suspend fun getKosdaq150(startDate: String, endDate: String): List<IndexOhlcv> {
        return getOhlcvByTicker(startDate, endDate, TICKER_KOSDAQ_150)
    }

    /**
     * 리소스 정리
     */
    fun close() {
        client.close()
    }

    companion object {
        /** KOSPI 티커 */
        const val TICKER_KOSPI = "1001"

        /** KOSPI 200 티커 */
        const val TICKER_KOSPI_200 = "1028"

        /** KOSPI 대형주 티커 */
        const val TICKER_KOSPI_LARGE = "1002"

        /** KOSPI 중형주 티커 */
        const val TICKER_KOSPI_MID = "1003"

        /** KOSPI 소형주 티커 */
        const val TICKER_KOSPI_SMALL = "1004"

        /** KOSDAQ 티커 */
        const val TICKER_KOSDAQ = "2001"

        /** KOSDAQ 150 티커 */
        const val TICKER_KOSDAQ_150 = "2203"
    }
}
