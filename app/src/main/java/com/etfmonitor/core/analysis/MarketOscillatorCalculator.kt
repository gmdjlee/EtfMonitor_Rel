package com.etfmonitor.core.analysis

import com.etfmonitor.core.common.util.AppLogger
import com.krxkt.KrxIndex
import com.krxkt.KrxStock
import com.krxkt.model.IndexOhlcv
import com.krxkt.model.Market
import com.krxkt.model.StockOhlcvHistory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 시장 과매수/과매도 지표 계산기
 *
 * Python market.py Oscillator 클래스를 대체하는 Kotlin 구현:
 * - kotlin_krx를 사용하여 지수 및 구성종목 데이터 수집
 * - AD-003 proxy: top-N market cap으로 지수 구성종목 근사
 * - Oscillator 계산: (거래량 비율 + 가격 변동 비율) / 2
 */
@Singleton
class MarketOscillatorCalculator @Inject constructor(
    private val krxIndex: KrxIndex,
    private val krxStock: KrxStock
) {

    companion object {
        private val logger = AppLogger.getLogger("MarketOscillatorCalc")
        private const val CONCURRENCY_LIMIT = 3  // KRX Akamai WAF rate-limit 대응: 동시 요청 최대 3개
        private const val PER_REQUEST_DELAY_MS = 500L  // 요청 간 딜레이 (rate limit 방지)
        private const val COMPONENT_COUNT = 200  // Top N 종목 수 (KOSPI 200, KOSDAQ 150 근사)

        private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }

    /**
     * 시장 oscillator 분석 실행
     *
     * @param market "KOSPI" 또는 "KOSDAQ"
     * @param startDate 시작일 (yyyyMMdd)
     * @param endDate 종료일 (yyyyMMdd)
     * @return OscillatorResult 또는 null
     */
    suspend fun analyze(market: String, startDate: String, endDate: String): OscillatorResult? = withContext(Dispatchers.IO) {
        try {
            logger.d("Analyzing $market oscillator: $startDate ~ $endDate")

            // 1. 지수 데이터 수집
            val indexData = getIndexData(market, startDate, endDate)
            if (indexData.isEmpty()) {
                logger.e("No index data for $market")
                return@withContext null
            }

            logger.d("Index data: ${indexData.size} records")

            // 2. 구성종목 데이터 수집 (AD-003: top-N market cap)
            val (closePrices, volumes) = getComponentData(market, startDate, endDate, indexData.map { it.date })
            if (closePrices.isEmpty() || volumes.isEmpty()) {
                logger.e("No component data for $market")
                return@withContext null
            }

            logger.d("Component data: ${closePrices.values.first().size} stocks")

            // 3. Oscillator 계산
            val oscillator = calculateOscillator(closePrices, volumes)
            if (oscillator.isEmpty()) {
                logger.e("Oscillator calculation failed for $market")
                return@withContext null
            }

            // 4. 결과 반환
            val oscillatorPct = oscillator.map { it * 100.0 }
            OscillatorResult(
                market = market,
                dates = indexData.map { it.date },
                indexValues = indexData.map { it.close },
                oscillator = oscillatorPct,
                stats = OscillatorStats(
                    mean = oscillatorPct.average(),
                    max = oscillatorPct.maxOrNull() ?: 0.0,
                    min = oscillatorPct.minOrNull() ?: 0.0,
                    latest = oscillatorPct.lastOrNull() ?: 0.0
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Oscillator analysis failed for $market", e)
            null
        }
    }

    /**
     * 지수 OHLCV 데이터 수집
     */
    private suspend fun getIndexData(market: String, startDate: String, endDate: String): List<IndexOhlcv> {
        return try {
            when (market.uppercase()) {
                "KOSPI" -> krxIndex.getKospi(startDate, endDate)
                "KOSDAQ" -> krxIndex.getKosdaq(startDate, endDate)
                else -> {
                    logger.e("Invalid market: $market")
                    emptyList()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Index data fetch failed for $market", e)
            emptyList()
        }
    }

    /**
     * 구성종목 데이터 수집 (AD-003: top-N market cap proxy)
     *
     * Semaphore(CONCURRENCY_LIMIT) 기반 병렬 처리:
     * - 첫 번째 종목은 동기 처리 → kotlin_krx TickerCache ISIN 워밍업
     * - 나머지 종목은 async/awaitAll + Semaphore 로 병렬 수집
     * - 개별 종목 실패는 null 반환 후 집계 시 제외 (전체 실패 방지)
     *
     * @return Pair<종가맵, 거래량맵> - Map<종목코드, List<Double/Long>>
     */
    private suspend fun getComponentData(
        market: String,
        startDate: String,
        endDate: String,
        indexDates: List<String>
    ): Pair<Map<String, List<Double?>>, Map<String, List<Long?>>> {
        try {
            // 1. AD-003: 최신 날짜 기준 시가총액 상위 N개 종목 조회
            val latestDate = indexDates.lastOrNull() ?: endDate
            val marketEnum = when (market.uppercase()) {
                "KOSPI" -> Market.KOSPI
                "KOSDAQ" -> Market.KOSDAQ
                else -> Market.ALL
            }

            val marketCaps = krxStock.getMarketCap(latestDate, marketEnum)
                .sortedByDescending { it.marketCap }
                .take(COMPONENT_COUNT)

            val tickers = marketCaps.map { it.ticker }
            logger.d("$market components (top $COMPONENT_COUNT by market cap): ${tickers.size} tickers")

            if (tickers.isEmpty()) {
                return Pair(emptyMap(), emptyMap())
            }

            // 2. 각 종목의 OHLCV 데이터 수집 (Semaphore 병렬 처리)
            val semaphore = Semaphore(CONCURRENCY_LIMIT)

            // 결과 타입: 성공 시 ticker → aligned 데이터, 실패 시 null
            data class TickerResult(
                val ticker: String,
                val closes: List<Double?>,
                val volumes: List<Long?>
            )

            // 첫 번째 종목 동기 처리: kotlin_krx TickerCache ISIN 워밍업
            // (첫 요청이 ISIN 조회를 수행하므로 병렬 첫 요청 충돌 방지)
            val warmupTicker = tickers.first()
            var warmupResult: TickerResult? = null
            try {
                val ohlcv = krxStock.getOhlcvByTicker(startDate, endDate, warmupTicker)
                if (ohlcv.isNotEmpty()) {
                    val aligned = alignToIndexDates(ohlcv, indexDates)
                    warmupResult = TickerResult(
                        ticker = warmupTicker,
                        closes = aligned.map { it.close },
                        volumes = aligned.map { it.volume }
                    )
                }
                logger.d("ISIN cache warmed up with first ticker: $warmupTicker")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w("Warmup ticker $warmupTicker failed: ${e.message}")
            }

            // 나머지 종목 병렬 처리
            val remainingTickers = tickers.drop(1)
            val parallelResults: List<TickerResult?> = coroutineScope {
                remainingTickers.map { ticker ->
                    async {
                        semaphore.withPermit {
                            val result = try {
                                val ohlcv = krxStock.getOhlcvByTicker(startDate, endDate, ticker)
                                if (ohlcv.isNotEmpty()) {
                                    val aligned = alignToIndexDates(ohlcv, indexDates)
                                    TickerResult(
                                        ticker = ticker,
                                        closes = aligned.map { it.close },
                                        volumes = aligned.map { it.volume }
                                    )
                                } else {
                                    null
                                }
                            } catch (e: CancellationException) {
                                throw e  // 코루틴 취소는 반드시 전파
                            } catch (e: Exception) {
                                logger.w("Failed to fetch OHLCV for $ticker: ${e.message}")
                                null  // 개별 종목 오류는 null 반환 후 집계 시 제외
                            }
                            // KRX Akamai rate limit 방지: 요청 간 딜레이
                            delay(PER_REQUEST_DELAY_MS)
                            result
                        }
                    }
                }.awaitAll()
            }

            // 결과 집계
            val closePrices = mutableMapOf<String, List<Double?>>()
            val volumes = mutableMapOf<String, List<Long?>>()

            warmupResult?.let {
                closePrices[it.ticker] = it.closes
                volumes[it.ticker] = it.volumes
            }
            parallelResults.filterNotNull().forEach { result ->
                closePrices[result.ticker] = result.closes
                volumes[result.ticker] = result.volumes
            }

            logger.d("Collected data for ${closePrices.size} / ${tickers.size} components")
            return Pair(closePrices, volumes)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Component data fetch failed", e)
            return Pair(emptyMap(), emptyMap())
        }
    }

    /**
     * 종목 OHLCV를 지수 날짜에 정렬
     */
    private fun alignToIndexDates(ohlcv: List<StockOhlcvHistory>, indexDates: List<String>): List<AlignedData> {
        val ohlcvMap = ohlcv.associateBy { it.date }

        return indexDates.map { date ->
            val data = ohlcvMap[date]
            AlignedData(
                date = date,
                close = data?.close?.toDouble(),
                volume = data?.volume
            )
        }
    }

    /**
     * Oscillator 계산
     *
     * 알고리즘:
     * 1. 각 종목의 일간 변화율 계산
     * 2. 상승 종목 vs 하락 종목 구분
     * 3. volume ratio = 상승 거래량 / 전체 거래량
     * 4. point ratio = 상승 변화율 합 / 전체 변화율 합
     * 5. oscillator = (volume ratio + point ratio) / 2
     * 6. 범위 조정: avg > 0.5면 그대로, 아니면 avg - 1
     */
    private fun calculateOscillator(
        closePrices: Map<String, List<Double?>>,
        volumes: Map<String, List<Long?>>
    ): List<Double> {
        if (closePrices.isEmpty() || volumes.isEmpty()) {
            return emptyList()
        }

        val numDates = closePrices.values.first().size
        val result = mutableListOf<Double>()

        for (i in 0 until numDates) {
            if (i == 0) {
                // 첫날은 변화율 계산 불가 (이전 데이터 없음)
                result.add(0.0)
                continue
            }

            var upVolume = 0.0
            var downVolume = 0.0
            var gainedPoints = 0.0
            var lostPoints = 0.0

            // 각 종목의 변화율 및 거래량 집계
            for (ticker in closePrices.keys) {
                val closeList = closePrices[ticker] ?: continue
                val volumeList = volumes[ticker] ?: continue

                val prevClose = closeList.getOrNull(i - 1)
                val currClose = closeList.getOrNull(i)
                val currVolume = volumeList.getOrNull(i)

                if (prevClose == null || currClose == null || prevClose == 0.0) {
                    continue
                }

                // 변화율 계산: (현재가 - 전일가) / 전일가
                val change = (currClose - prevClose) / prevClose
                val vol = (currVolume ?: 0L).toDouble()

                when {
                    change > 0 -> {
                        upVolume += vol
                        gainedPoints += change
                    }
                    change < 0 -> {
                        downVolume += vol
                        lostPoints += kotlin.math.abs(change)
                    }
                }
            }

            // Ratio 계산
            val totalVolume = upVolume + downVolume
            val totalPoints = gainedPoints + lostPoints

            val volumeRatio = if (totalVolume > 0) upVolume / totalVolume else 0.5
            val pointsRatio = if (totalPoints > 0) gainedPoints / totalPoints else 0.5

            // 평균
            val avg = (volumeRatio + pointsRatio) / 2.0

            // 범위 조정: avg > 0.5면 그대로, 아니면 avg - 1 ([-0.5, 0.5] 범위로 매핑)
            val oscillator = if (avg > 0.5) avg else avg - 1.0

            result.add(oscillator)
        }

        return result
    }

    /**
     * 정렬된 데이터 (nullable)
     */
    private data class AlignedData(
        val date: String,
        val close: Double?,
        val volume: Long?
    )

    /**
     * Oscillator 분석 결과
     */
    data class OscillatorResult(
        val market: String,
        val dates: List<String>,
        val indexValues: List<Double>,
        val oscillator: List<Double>,
        val stats: OscillatorStats
    )

    /**
     * Oscillator 통계
     */
    data class OscillatorStats(
        val mean: Double,
        val max: Double,
        val min: Double,
        val latest: Double
    )
}
