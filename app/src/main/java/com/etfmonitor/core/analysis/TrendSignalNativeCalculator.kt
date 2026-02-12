package com.etfmonitor.core.analysis

import com.etfmonitor.core.analysis.model.DemarkTDData
import com.etfmonitor.core.analysis.model.ElderImpulseData
import com.etfmonitor.core.analysis.model.TrendSignalData
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.common.util.DateFormatter
import com.krxkt.KrxStock
import com.krxkt.model.Market
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 네이티브 추세 시그널 계산기
 *
 * trend_signal.py의 기능을 대체하는 네이티브 Kotlin 구현:
 * - 추세 시그널 분석 (MA, CMF, Fear & Greed, Buy/Sell 시그널)
 * - Elder Impulse System (EMA13 + MACD 히스토그램)
 * - DeMark TD Setup (매수/매도 피로 카운트)
 *
 * @property krxStock kotlin_krx Stock API
 */
@Singleton
class TrendSignalNativeCalculator @Inject constructor(
    private val krxStock: KrxStock
) {

    companion object {
        private val logger = AppLogger.getLogger("TrendSignalCalc")
        private const val TIMEOUT_MS = 30_000L
    }

    private val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * 추세 시그널 분석
     *
     * trend_signal.py get_trend_signal_analysis() 대체
     *
     * @param ticker 종목코드
     * @param days 분석 기간
     * @param interval "d"=일별, "w"=주별
     * @param maPeriod 이동평균 기간
     * @param cmfPeriod CMF 기간
     * @return TrendSignalData 또는 null
     */
    suspend fun calculateTrendSignal(
        ticker: String,
        days: Int = 180,
        interval: String = "w",
        maPeriod: Int = 20,
        cmfPeriod: Int = 4
    ): TrendSignalData? = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                logger.d("Trend analysis: $ticker, $days days, $interval")

                val ohlcv = fetchOhlcv(ticker, days, interval) ?: return@withTimeout null
                if (ohlcv.dates.size < maPeriod + 5) {
                    logger.e("Not enough data for trend analysis")
                    return@withTimeout null
                }

                // MA 계산
                val ma = TechnicalIndicators.rollingMean(ohlcv.close, maPeriod)

                // CMF 계산
                val cmf = TechnicalIndicators.calcCmf(
                    ohlcv.high, ohlcv.low, ohlcv.close, ohlcv.volume, cmfPeriod
                )

                // Fear & Greed 계산
                val fg = TechnicalIndicators.calcStockFearGreed(ohlcv.close, ohlcv.volume)

                // 전일 고저
                val prevHigh = listOf(0.0) + ohlcv.high.dropLast(1)
                val prevLow = listOf(0.0) + ohlcv.low.dropLast(1)

                // Buy/Sell 시그널 생성
                val buySignal = mutableListOf<Int>()
                val auxBuySignal = mutableListOf<Int>()
                val sellSignal = mutableListOf<Int>()
                val auxSellSignal = mutableListOf<Int>()

                for (i in ohlcv.dates.indices) {
                    val maVal = ma[i]
                    val cmfVal = cmf[i]

                    if (maVal.isNaN() || i == 0) {
                        buySignal.add(0)
                        auxBuySignal.add(0)
                        sellSignal.add(0)
                        auxSellSignal.add(0)
                        continue
                    }

                    // Buy conditions
                    val b1 = ohlcv.high[i] > prevHigh[i]  // High breakout
                    val b2 = ohlcv.close[i] > maVal         // Above MA
                    val b3 = cmfVal > 0                      // Money inflow
                    val bCnt = (if (b1) 1 else 0) + (if (b2) 1 else 0) + (if (b3) 1 else 0)

                    // Sell conditions
                    val s1 = ohlcv.low[i] < prevLow[i]      // Low breakdown
                    val s2 = ohlcv.close[i] < maVal           // Below MA
                    val s3 = cmfVal < 0                       // Money outflow
                    val sCnt = (if (s1) 1 else 0) + (if (s2) 1 else 0) + (if (s3) 1 else 0)

                    buySignal.add(if (bCnt == 3) 1 else 0)
                    auxBuySignal.add(if (bCnt == 2 && b2) 1 else 0)
                    sellSignal.add(if (sCnt == 3) 1 else 0)
                    auxSellSignal.add(if (sCnt == 2 && s2) 1 else 0)
                }

                // NaN이 아닌 유효 데이터만 필터링 (dropna 에뮬레이션)
                val validIndices = ma.indices.filter { !ma[it].isNaN() }
                if (validIndices.isEmpty()) {
                    logger.e("No valid data after indicator calculation")
                    return@withTimeout null
                }

                // 종목명 조회
                val stockName = getStockName(ticker)

                TrendSignalData(
                    ticker = ticker,
                    name = stockName,
                    interval = interval,
                    dates = validIndices.map { ohlcv.dates[it] },
                    open = validIndices.map { ohlcv.open[it] },
                    high = validIndices.map { ohlcv.high[it] },
                    low = validIndices.map { ohlcv.low[it] },
                    close = validIndices.map { ohlcv.close[it] },
                    volume = validIndices.map { ohlcv.volume[it] },
                    ma = validIndices.map { ma[it] },
                    cmf = validIndices.map { cmf[it] },
                    fearGreed = validIndices.map { fg[it] },
                    buySignal = validIndices.map { buySignal[it] },
                    auxBuySignal = validIndices.map { auxBuySignal[it] },
                    sellSignal = validIndices.map { sellSignal[it] },
                    auxSellSignal = validIndices.map { auxSellSignal[it] }
                ).also {
                    logger.d("Trend analysis complete: ${it.name}, ${it.dates.size} records")
                }
            }
        } catch (e: Exception) {
            logger.e("Trend analysis error for $ticker", e)
            null
        }
    }

    /**
     * Elder Impulse System 분석
     *
     * trend_signal.py get_elder_impulse_analysis() 대체
     *
     * EMA 기울기와 MACD 히스토그램 기울기로 추세 판별:
     * - bull(1): 둘 다 상승
     * - bear(-1): 둘 다 하락
     * - neutral(0): 혼조
     *
     * @param ticker 종목코드
     * @param days 분석 기간
     * @param interval "d" or "w"
     * @return ElderImpulseData 또는 null
     */
    suspend fun calculateElderImpulse(
        ticker: String,
        days: Int = 365,
        interval: String = "w"
    ): ElderImpulseData? = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                logger.d("Elder Impulse analysis: $ticker, $days days, $interval")

                val ohlcv = fetchOhlcv(ticker, days, interval) ?: return@withTimeout null
                if (ohlcv.dates.size < 30) {
                    logger.e("Not enough data for Elder Impulse")
                    return@withTimeout null
                }

                // EMA13
                val ema13 = TechnicalIndicators.calcEma(ohlcv.close, 13)

                // MACD (12, 26, 9)
                val (macdLine, macdSignal, macdHist) = TechnicalIndicators.calcMacd(ohlcv.close)

                // 시가총액 가져오기
                val marketCaps = fetchMarketCaps(ticker, ohlcv.dates)

                // Impulse 계산 (기울기 기반)
                val impulse = mutableListOf<Int>()
                for (i in ohlcv.dates.indices) {
                    if (i == 0) {
                        impulse.add(0) // 첫 날은 neutral
                        continue
                    }
                    val emaSlope = ema13[i] - ema13[i - 1]
                    val histSlope = macdHist[i] - macdHist[i - 1]

                    impulse.add(
                        when {
                            emaSlope > 0 && histSlope > 0 -> 1   // bull
                            emaSlope < 0 && histSlope < 0 -> -1  // bear
                            else -> 0                              // neutral
                        }
                    )
                }

                // dropna: EMA/MACD가 유효한 구간만 (충분한 데이터 이후)
                val validStart = minOf(26, ohlcv.dates.size - 1)
                val validIndices = (validStart until ohlcv.dates.size).toList()

                val stockName = getStockName(ticker)

                ElderImpulseData(
                    ticker = ticker,
                    name = stockName,
                    interval = interval,
                    dates = validIndices.map { ohlcv.dates[it] },
                    close = validIndices.map { ohlcv.close[it] },
                    marketCap = validIndices.map { marketCaps.getOrElse(it) { 0L } },
                    ema = validIndices.map { ema13[it] },
                    macd = validIndices.map { macdLine[it] },
                    macdSignal = validIndices.map { macdSignal[it] },
                    macdHist = validIndices.map { macdHist[it] },
                    impulse = validIndices.map { impulse[it] }
                ).also {
                    logger.d("Elder Impulse complete: ${it.name}, ${it.dates.size} records")
                }
            }
        } catch (e: Exception) {
            logger.e("Elder Impulse error for $ticker", e)
            null
        }
    }

    /**
     * DeMark TD Setup 분석
     *
     * trend_signal.py get_demark_td_analysis() 대체
     *
     * - Sell Setup: Close(t) > Close(t-4) 연속 시 카운트 +1
     * - Buy Setup: Close(t) < Close(t-4) 연속 시 카운트 +1
     *
     * @param ticker 종목코드
     * @param days 분석 기간
     * @param interval "d", "w", "m"
     * @return DemarkTDData 또는 null
     */
    suspend fun calculateDemarkTD(
        ticker: String,
        days: Int = 365,
        interval: String = "w"
    ): DemarkTDData? = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                logger.d("DeMark TD analysis: $ticker, $days days, $interval")

                val ohlcv = fetchOhlcv(ticker, days, interval) ?: return@withTimeout null
                if (ohlcv.dates.size < 5) {
                    logger.e("Not enough data for DeMark TD")
                    return@withTimeout null
                }

                val n = ohlcv.close.size
                val tdSell = IntArray(n)
                val tdBuy = IntArray(n)

                for (i in 4 until n) {
                    // Sell Setup: Close > Close(t-4)
                    tdSell[i] = if (ohlcv.close[i] > ohlcv.close[i - 4]) {
                        tdSell[i - 1] + 1
                    } else 0

                    // Buy Setup: Close < Close(t-4)
                    tdBuy[i] = if (ohlcv.close[i] < ohlcv.close[i - 4]) {
                        tdBuy[i - 1] + 1
                    } else 0
                }

                // 시가총액 가져오기
                val marketCaps = fetchMarketCaps(ticker, ohlcv.dates)

                val intervalName = when (interval) {
                    "d" -> "일봉"
                    "w" -> "주봉"
                    "m" -> "월봉"
                    else -> interval
                }

                val stockName = getStockName(ticker)

                DemarkTDData(
                    ticker = ticker,
                    name = stockName,
                    interval = interval,
                    intervalName = intervalName,
                    dates = ohlcv.dates,
                    close = ohlcv.close,
                    marketCap = (0 until n).map { marketCaps.getOrElse(it) { 0L } },
                    tdSell = tdSell.toList(),
                    tdBuy = tdBuy.toList()
                ).also {
                    logger.d("DeMark TD complete: ${it.name}, ${it.dates.size} records, $intervalName")
                }
            }
        } catch (e: Exception) {
            logger.e("DeMark TD error for $ticker", e)
            null
        }
    }

    // ======== Private helpers ========

    /**
     * OHLCV 데이터 로드 (인터벌별 리샘플링 포함)
     */
    private suspend fun fetchOhlcv(
        ticker: String,
        days: Int,
        interval: String
    ): OhlcvDataSet? {
        val extra = when (interval) {
            "m" -> days * 3
            "w" -> days * 2
            else -> days
        }

        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(extra.toLong())

        val ohlcvList = krxStock.getOhlcvByTicker(
            startDate.format(dateFormat),
            endDate.format(dateFormat),
            ticker
        )

        if (ohlcvList.isEmpty()) {
            logger.e("No OHLCV data for $ticker")
            return null
        }

        return when (interval) {
            "w" -> resampleWeekly(ohlcvList)
            "m" -> resampleMonthly(ohlcvList)
            else -> {
                OhlcvDataSet(
                    dates = ohlcvList.map { DateFormatter.formatFromYYYYMMDD(it.date) },
                    open = ohlcvList.map { it.open.toDouble() },
                    high = ohlcvList.map { it.high.toDouble() },
                    low = ohlcvList.map { it.low.toDouble() },
                    close = ohlcvList.map { it.close.toDouble() },
                    volume = ohlcvList.map { it.volume }
                )
            }
        }
    }

    /**
     * 시가총액 데이터 로드
     */
    private suspend fun fetchMarketCaps(
        ticker: String,
        dates: List<String>
    ): List<Long> {
        return try {
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays((dates.size * 2).toLong())
            val capData = krxStock.getMarketCap(endDate.format(dateFormat))
            val capForTicker = capData.find { it.ticker == ticker }

            if (capForTicker != null) {
                // 현재 시가총액을 모든 날짜에 적용 (근사)
                dates.map { capForTicker.marketCap }
            } else {
                dates.map { 0L }
            }
        } catch (e: Exception) {
            logger.w("Market cap fetch error for $ticker: ${e.message}")
            dates.map { 0L }
        }
    }

    /**
     * 종목명 조회
     */
    private suspend fun getStockName(ticker: String): String {
        return try {
            val today = LocalDate.now().format(dateFormat)
            val tickerList = krxStock.getTickerList(today)
            tickerList.find { it.ticker == ticker }?.name ?: ticker
        } catch (e: Exception) {
            ticker
        }
    }

    /**
     * 주별 리샘플링 (W-FRI 기준)
     */
    private fun resampleWeekly(
        ohlcvList: List<com.krxkt.model.StockOhlcvHistory>
    ): OhlcvDataSet {
        if (ohlcvList.isEmpty()) return OhlcvDataSet.EMPTY

        val weeks = mutableListOf<OhlcvRecord>()
        var weekOpen: Double? = null
        var weekHigh = Double.MIN_VALUE
        var weekLow = Double.MAX_VALUE
        var weekClose = 0.0
        var weekVolume = 0L
        var weekDate = ""

        for (ohlcv in ohlcvList) {
            val date = LocalDate.parse(ohlcv.date, dateFormat)
            if (weekOpen == null) weekOpen = ohlcv.open.toDouble()
            weekHigh = maxOf(weekHigh, ohlcv.high.toDouble())
            weekLow = minOf(weekLow, ohlcv.low.toDouble())
            weekClose = ohlcv.close.toDouble()
            weekVolume += ohlcv.volume
            weekDate = DateFormatter.formatFromYYYYMMDD(ohlcv.date)

            if (date.dayOfWeek == DayOfWeek.FRIDAY || ohlcv === ohlcvList.last()) {
                weeks.add(OhlcvRecord(weekDate, weekOpen, weekHigh, weekLow, weekClose, weekVolume))
                weekOpen = null
                weekHigh = Double.MIN_VALUE
                weekLow = Double.MAX_VALUE
                weekClose = 0.0
                weekVolume = 0L
            }
        }

        return OhlcvDataSet(
            dates = weeks.map { it.date },
            open = weeks.map { it.open },
            high = weeks.map { it.high },
            low = weeks.map { it.low },
            close = weeks.map { it.close },
            volume = weeks.map { it.volume }
        )
    }

    /**
     * 월별 리샘플링 (ME 기준)
     */
    private fun resampleMonthly(
        ohlcvList: List<com.krxkt.model.StockOhlcvHistory>
    ): OhlcvDataSet {
        if (ohlcvList.isEmpty()) return OhlcvDataSet.EMPTY

        val months = mutableListOf<OhlcvRecord>()
        var monthOpen: Double? = null
        var monthHigh = Double.MIN_VALUE
        var monthLow = Double.MAX_VALUE
        var monthClose = 0.0
        var monthVolume = 0L
        var monthDate = ""
        var currentMonth = -1

        for (ohlcv in ohlcvList) {
            val date = LocalDate.parse(ohlcv.date, dateFormat)
            val month = date.monthValue

            if (currentMonth != -1 && month != currentMonth && monthOpen != null) {
                months.add(OhlcvRecord(monthDate, monthOpen, monthHigh, monthLow, monthClose, monthVolume))
                monthOpen = null
                monthHigh = Double.MIN_VALUE
                monthLow = Double.MAX_VALUE
                monthVolume = 0L
            }

            currentMonth = month
            if (monthOpen == null) monthOpen = ohlcv.open.toDouble()
            monthHigh = maxOf(monthHigh, ohlcv.high.toDouble())
            monthLow = minOf(monthLow, ohlcv.low.toDouble())
            monthClose = ohlcv.close.toDouble()
            monthVolume += ohlcv.volume
            monthDate = DateFormatter.formatFromYYYYMMDD(ohlcv.date)
        }

        // 마지막 월
        if (monthOpen != null) {
            months.add(OhlcvRecord(monthDate, monthOpen, monthHigh, monthLow, monthClose, monthVolume))
        }

        return OhlcvDataSet(
            dates = months.map { it.date },
            open = months.map { it.open },
            high = months.map { it.high },
            low = months.map { it.low },
            close = months.map { it.close },
            volume = months.map { it.volume }
        )
    }

    /**
     * 내부 OHLCV 데이터셋
     */
    private data class OhlcvDataSet(
        val dates: List<String>,
        val open: List<Double>,
        val high: List<Double>,
        val low: List<Double>,
        val close: List<Double>,
        val volume: List<Long>
    ) {
        companion object {
            val EMPTY = OhlcvDataSet(
                emptyList(), emptyList(), emptyList(),
                emptyList(), emptyList(), emptyList()
            )
        }
    }

    private data class OhlcvRecord(
        val date: String,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Long
    )
}
