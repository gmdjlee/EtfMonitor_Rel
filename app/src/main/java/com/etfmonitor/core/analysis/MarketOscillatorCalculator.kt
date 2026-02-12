package com.etfmonitor.core.analysis

import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.common.util.DateFormatter
import com.krxkt.KrxIndex
import com.krxkt.KrxStock
import com.krxkt.model.Market
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 시장 과매수/과매도 오실레이터 계산기
 *
 * market.py Oscillator 클래스를 대체하는 네이티브 구현
 *
 * 계산 로직:
 * 1. 시장 구성종목 200+ 개의 OHLCV 수집
 * 2. 종목별 등락률 계산
 * 3. 상승종목/하락종목 거래량 비율 계산
 * 4. 상승폭/하락폭 비율 계산
 * 5. 두 비율의 평균으로 오실레이터 산출
 *
 * @property krxStock kotlin_krx Stock API
 * @property krxIndex kotlin_krx Index API
 */
@Singleton
class MarketOscillatorCalculator @Inject constructor(
    private val krxStock: KrxStock,
    private val krxIndex: KrxIndex
) {

    companion object {
        private val logger = AppLogger.getLogger("MktOscCalc")
        private const val TIMEOUT_MS = 180_000L  // 180초 (200+ 종목 수집)
        private const val BATCH_SIZE = 50
        private const val REQ_DELAY_MS = 300L
        private const val KRX_API_URL = "https://data.krx.co.kr/comm/bldAttendant/getJsonData.cmd"

        // 시장별 지수 구성종목 코드
        private val MARKET_CONFIG = mapOf(
            "KOSPI" to MarketConfig(
                indexTicker = KrxIndex.TICKER_KOSPI,
                componentCode = "1028",  // KOSPI 200
                market = Market.KOSPI
            ),
            "KOSDAQ" to MarketConfig(
                indexTicker = KrxIndex.TICKER_KOSDAQ,
                componentCode = "2203",  // KOSDAQ 150
                market = Market.KOSDAQ
            )
        )
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * 시장 오실레이터 분석 실행
     *
     * @param market "KOSPI" 또는 "KOSDAQ"
     * @param startDate 시작일 (yyyyMMdd)
     * @param endDate 종료일 (yyyyMMdd)
     * @return OscillatorAnalysisResult 또는 null
     */
    suspend fun analyze(
        market: String,
        startDate: String,
        endDate: String
    ): OscillatorAnalysisResult? = withContext(Dispatchers.IO) {
        try {
            val config = MARKET_CONFIG[market] ?: run {
                logger.e("Unknown market: $market")
                return@withContext null
            }

            withTimeout(TIMEOUT_MS) {
                logger.d("Analyzing $market oscillator: $startDate ~ $endDate")

                // 1. 지수 데이터 가져오기
                val indexData = getIndexData(market, startDate, endDate)
                if (indexData.isEmpty()) {
                    logger.e("Failed to fetch index data for $market")
                    return@withTimeout null
                }

                val tradingDates = indexData.map { it.first }
                val indexValues = indexData.map { it.second }

                // 2. 구성종목 티커 가져오기
                val componentTickers = getComponentTickers(config, endDate)
                if (componentTickers.isEmpty()) {
                    logger.e("Failed to fetch component tickers for $market")
                    return@withTimeout null
                }

                logger.d("$market: collecting ${componentTickers.size} components")

                // 3. 종목별 OHLCV 수집
                val stockData = collectComponentData(
                    componentTickers, startDate, endDate, tradingDates
                )

                if (stockData.isEmpty()) {
                    logger.e("Failed to collect component data for $market")
                    return@withTimeout null
                }

                logger.d("$market: collected ${stockData.size} components")

                // 4. 오실레이터 계산
                val oscillator = calculateOscillator(stockData, tradingDates)
                if (oscillator.isEmpty()) {
                    logger.e("Failed to calculate oscillator for $market")
                    return@withTimeout null
                }

                // 5. 결과 생성
                val dates = tradingDates.map { DateFormatter.formatFromYYYYMMDD(it) }
                val oscPct = oscillator.map { it * 100 }

                val result = OscillatorAnalysisResult(
                    market = market,
                    dates = dates,
                    indexValues = indexValues,
                    oscillator = oscPct,
                    stats = OscillatorStats(
                        mean = oscPct.average(),
                        max = oscPct.max(),
                        min = oscPct.min(),
                        latest = oscPct.last()
                    )
                )

                logger.d("$market oscillator complete: ${result.dates.size} data points")
                result
            }
        } catch (e: Exception) {
            logger.e("Oscillator analysis error for $market", e)
            null
        }
    }

    // ======== Private helpers ========

    /**
     * 지수 OHLCV 데이터 가져오기
     * @return List<Pair<date(yyyyMMdd), closeValue>>
     */
    private suspend fun getIndexData(
        market: String,
        startDate: String,
        endDate: String
    ): List<Pair<String, Double>> {
        return try {
            val indexData = when (market) {
                "KOSPI" -> krxIndex.getKospi(startDate, endDate)
                "KOSDAQ" -> krxIndex.getKosdaq(startDate, endDate)
                else -> emptyList()
            }
            indexData.map { Pair(it.date, it.close) }
        } catch (e: Exception) {
            logger.e("Index data fetch error for $market", e)
            emptyList()
        }
    }

    /**
     * 지수 구성종목 티커 가져오기
     *
     * 1차: KRX API 직접 호출로 인덱스 구성종목 조회
     * 2차(fallback): KrxStock.getTickerList()로 시장 전체 종목 제한 사용
     */
    private suspend fun getComponentTickers(
        config: MarketConfig,
        date: String
    ): List<String> {
        // 1차: KRX API 직접 호출
        val krxTickers = fetchIndexComponentsFromKrx(config.componentCode, date)
        if (krxTickers.isNotEmpty()) {
            return krxTickers
        }

        // 2차: fallback - 전체 시장 티커에서 제한
        logger.w("Falling back to full ticker list for ${config.market}")
        return try {
            val allTickers = krxStock.getTickerList(date, config.market)
            val limit = when (config.market) {
                Market.KOSPI -> 200
                Market.KOSDAQ -> 150
                else -> 200
            }
            allTickers.take(limit).map { it.ticker }
        } catch (e: Exception) {
            logger.e("Ticker list fallback error", e)
            emptyList()
        }
    }

    /**
     * KRX API에서 인덱스 구성종목 직접 조회
     *
     * pykrx의 get_index_portfolio_deposit_file() 동일 엔드포인트
     */
    private fun fetchIndexComponentsFromKrx(
        componentCode: String,
        date: String
    ): List<String> {
        try {
            // indIdx와 indIdx2 분리 (예: "1028" → "1" + "028")
            val indIdx = componentCode.substring(0, 1)
            val indIdx2 = componentCode.substring(1)

            val formBody = FormBody.Builder()
                .add("bld", "dbms/MDC/STAT/standard/MDCSTAT00601")
                .add("locale", "ko_KR")
                .add("indIdx", indIdx)
                .add("indIdx2", indIdx2)
                .add("trdDd", date)
                .add("money", "1")
                .add("csvxls_isNo", "false")
                .build()

            val request = Request.Builder()
                .url(KRX_API_URL)
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://data.krx.co.kr/contents/MDC/MDI/outerLoader/index.cmd")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val jsonObj = json.parseToJsonElement(body).jsonObject
            val outBlock = jsonObj["OutBlock_1"]?.jsonArray ?: return emptyList()

            val tickers = outBlock.mapNotNull { element ->
                element.jsonObject["ISU_SRT_CD"]?.jsonPrimitive?.content
            }.filter { it.length == 6 }

            logger.d("Fetched ${tickers.size} component tickers from KRX for code $componentCode")
            return tickers

        } catch (e: Exception) {
            logger.w("KRX component fetch failed: ${e.message}")
            return emptyList()
        }
    }

    /**
     * 구성종목 OHLCV 일괄 수집
     *
     * 배치 처리로 200+ 종목 OHLCV 수집
     */
    private suspend fun collectComponentData(
        tickers: List<String>,
        startDate: String,
        endDate: String,
        tradingDates: List<String>
    ): List<StockDailyData> {
        val result = mutableListOf<StockDailyData>()
        val dateSet = tradingDates.toSet()

        for (i in tickers.indices step BATCH_SIZE) {
            val batch = tickers.subList(i, minOf(i + BATCH_SIZE, tickers.size))

            for (ticker in batch) {
                try {
                    val ohlcv = krxStock.getOhlcvByTicker(startDate, endDate, ticker)
                    if (ohlcv.isNotEmpty()) {
                        // 거래일 기준으로 정렬 및 매핑
                        val closeMap = mutableMapOf<String, Double>()
                        val volumeMap = mutableMapOf<String, Long>()

                        for (item in ohlcv) {
                            if (item.date in dateSet) {
                                closeMap[item.date] = item.close.toDouble()
                                volumeMap[item.date] = item.volume
                            }
                        }

                        if (closeMap.isNotEmpty()) {
                            result.add(StockDailyData(ticker, closeMap, volumeMap))
                        }
                    }
                    delay(REQ_DELAY_MS)
                } catch (e: Exception) {
                    // 개별 종목 오류는 무시하고 계속 진행
                    continue
                }
            }
        }

        return result
    }

    /**
     * 오실레이터 계산
     *
     * Python market.py Oscillator._calc() 포팅:
     * 1. 종목별 등락률 계산
     * 2. 상승종목 거래량 합 / 전체 거래량 합 = 거래량 비율
     * 3. 상승폭 합 / (상승폭 합 + 하락폭 합) = 가격변동 비율
     * 4. 평균 = (거래량비율 + 가격변동비율) / 2
     * 5. avg > 0.5 → avg, else → avg - 1
     */
    private fun calculateOscillator(
        stockData: List<StockDailyData>,
        tradingDates: List<String>
    ): List<Double> {
        if (tradingDates.size < 2) return emptyList()

        val oscillator = mutableListOf<Double>()

        // 첫 날은 이전 데이터가 없으므로 0
        oscillator.add(0.0)

        for (dayIdx in 1 until tradingDates.size) {
            val today = tradingDates[dayIdx]
            val yesterday = tradingDates[dayIdx - 1]

            var upVol = 0.0
            var downVol = 0.0
            var gained = 0.0
            var lost = 0.0

            for (stock in stockData) {
                val todayClose = stock.closeMap[today] ?: continue
                val yesterdayClose = stock.closeMap[yesterday] ?: continue
                val todayVolume = stock.volumeMap[today] ?: 0L

                if (yesterdayClose <= 0) continue

                val change = (todayClose - yesterdayClose) / yesterdayClose

                if (change > 0) {
                    upVol += todayVolume
                    gained += change
                } else if (change < 0) {
                    downVol += todayVolume
                    lost += kotlin.math.abs(change)
                }
            }

            val totalVol = upVol + downVol
            val totalPts = gained + lost

            val volRatio = if (totalVol > 0) upVol / totalVol else 0.5
            val ptsRatio = if (totalPts > 0) gained / totalPts else 0.5
            val avg = (volRatio + ptsRatio) / 2.0

            oscillator.add(if (avg > 0.5) avg else avg - 1.0)
        }

        return oscillator
    }

    /**
     * 시장 설정
     */
    private data class MarketConfig(
        val indexTicker: String,
        val componentCode: String,
        val market: Market
    )

    /**
     * 종목별 일간 데이터
     */
    private data class StockDailyData(
        val ticker: String,
        val closeMap: Map<String, Double>,
        val volumeMap: Map<String, Long>
    )
}

/**
 * 오실레이터 분석 결과
 */
data class OscillatorAnalysisResult(
    val market: String,
    val dates: List<String>,
    val indexValues: List<Double>,
    val oscillator: List<Double>,
    val stats: OscillatorStats
)

/**
 * 오실레이터 통계
 */
data class OscillatorStats(
    val mean: Double,
    val max: Double,
    val min: Double,
    val latest: Double
)
