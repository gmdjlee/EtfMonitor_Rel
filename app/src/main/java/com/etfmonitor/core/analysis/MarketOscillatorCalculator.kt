package com.etfmonitor.core.analysis

import com.etfmonitor.core.common.util.AppLogger
import com.krxkt.KrxIndex
import com.krxkt.KrxStock
import com.krxkt.model.IndexOhlcv
import com.krxkt.model.Market
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 시장 과매수/과매도 지표 계산기 (pykrx _calc() 직접 이식)
 *
 * Python pykrx 알고리즘:
 * 1. KOSPI200("1028")/KOSDAQ150("2203") 구성종목만 필터링
 * 2. vol_ratio = upVol / (upVol + downVol)
 * 3. pts_ratio = gained / (gained + lost)
 * 4. avg = (vol_ratio + pts_ratio) / 2
 * 5. oscillator = if (avg > 0.5) avg else (avg - 1.0)  ← 핵심 비선형 변환
 * 6. ×100 → 출력 범위: [-100, -50] ∪ (50, 100]
 */
@Singleton
class MarketOscillatorCalculator @Inject constructor(
    private val krxIndex: KrxIndex,
    private val krxStock: KrxStock
) {

    companion object {
        private val logger = AppLogger.getLogger("MarketOscillatorCalc")
        private const val PER_REQUEST_DELAY_MS = 500L  // 요청 간 딜레이 (rate limit 방지)

        /** KOSPI200/KOSDAQ150 지수 티커 */
        private val INDEX_TICKER = mapOf(
            "KOSPI" to KrxIndex.TICKER_KOSPI_200,   // "1028"
            "KOSDAQ" to KrxIndex.TICKER_KOSDAQ_150   // "2203"
        )
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

            // 2. 구성종목 Set 가져오기 (최신 거래일 기준)
            val latestDate = indexData.last().date
            val componentSet = getComponentSet(market, latestDate)
            if (componentSet.isEmpty()) {
                logger.e("No component tickers for $market (date=$latestDate)")
                return@withContext null
            }

            logger.d("Component set: ${componentSet.size} tickers for $market")

            // 3. 날짜별 oscillator 계산 (vol+pts 가중 + 비선형 변환)
            val indexDates = indexData.map { it.date }
            val (oscillatorValues, validDates) = computeOscillatorValues(indexDates, market, componentSet)
            if (oscillatorValues.isEmpty()) {
                logger.e("No oscillator data computed for $market")
                return@withContext null
            }

            logger.d("Oscillator data: ${oscillatorValues.size} days")

            // 유효 날짜에 해당하는 지수값 매핑
            val indexMap = indexData.associateBy { it.date }
            val indexValues = validDates.map { date -> indexMap[date]?.close ?: 0.0 }

            OscillatorResult(
                market = market,
                dates = validDates,
                indexValues = indexValues,
                oscillator = oscillatorValues,
                stats = OscillatorStats(
                    mean = oscillatorValues.average(),
                    max = oscillatorValues.maxOrNull() ?: 0.0,
                    min = oscillatorValues.minOrNull() ?: 0.0,
                    latest = oscillatorValues.lastOrNull() ?: 0.0
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
     * KOSPI200/KOSDAQ150 구성종목 티커 Set 가져오기
     *
     * @param market "KOSPI" 또는 "KOSDAQ"
     * @param date 조회일 (yyyyMMdd)
     * @return 구성종목 티커 Set
     */
    internal suspend fun getComponentSet(market: String, date: String): Set<String> {
        val indexTicker = INDEX_TICKER[market.uppercase()] ?: return emptySet()
        return try {
            krxIndex.getIndexPortfolioTickers(date, indexTicker).toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Failed to get component tickers for $market ($indexTicker)", e)
            emptySet()
        }
    }

    /**
     * 날짜별 Oscillator 값 계산 (Python pykrx _calc() 이식)
     *
     * 각 거래일마다:
     * 1. getMarketOhlcv() → 전체 종목
     * 2. componentSet 필터 → 구성종목만
     * 3. vol_ratio = upVol / (upVol + downVol)
     * 4. pts_ratio = gained / (gained + lost)
     * 5. avg = (vol_ratio + pts_ratio) / 2
     * 6. oscillator = if (avg > 0.5) avg else (avg - 1.0)  ← 비선형 변환
     * 7. ×100 스케일링
     *
     * @return Pair<oscillatorValues, validDates>
     */
    private suspend fun computeOscillatorValues(
        indexDates: List<String>,
        market: String,
        componentSet: Set<String>
    ): Pair<List<Double>, List<String>> {
        val marketEnum = when (market.uppercase()) {
            "KOSPI" -> Market.KOSPI
            "KOSDAQ" -> Market.KOSDAQ
            else -> Market.ALL
        }

        val oscillatorValues = mutableListOf<Double>()
        val validDates = mutableListOf<String>()

        for ((idx, date) in indexDates.withIndex()) {
            if (idx > 0) delay(PER_REQUEST_DELAY_MS)  // KRX rate limit

            try {
                val ohlcvList = krxStock.getMarketOhlcv(date, marketEnum)
                if (ohlcvList.isEmpty()) continue

                // 구성종목만 필터링
                val components = ohlcvList.filter { it.ticker in componentSet }
                if (components.isEmpty()) continue

                // vol_ratio, pts_ratio 계산
                var upVol = 0L
                var downVol = 0L
                var gained = 0.0
                var lost = 0.0

                for (stock in components) {
                    when {
                        stock.changeRate > 0 -> {
                            upVol += stock.volume
                            gained += stock.changeRate
                        }
                        stock.changeRate < 0 -> {
                            downVol += stock.volume
                            lost += -stock.changeRate
                        }
                    }
                }

                val totalVol = upVol + downVol
                val totalPts = gained + lost

                val volRatio = if (totalVol > 0L) upVol.toDouble() / totalVol else 0.5
                val ptsRatio = if (totalPts > 0.0) gained / totalPts else 0.5

                val avg = (volRatio + ptsRatio) / 2.0

                // 핵심 비선형 변환: np.where(avg > 0.5, avg, avg - 1)
                val oscillatorRaw = if (avg > 0.5) avg else (avg - 1.0)

                // ×100 스케일링 → [-100, -50] ∪ (50, 100]
                oscillatorValues.add(oscillatorRaw * 100.0)
                validDates.add(date)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w("Failed to fetch market OHLCV for $date: ${e.message}")
            }
        }

        return Pair(oscillatorValues, validDates)
    }

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
