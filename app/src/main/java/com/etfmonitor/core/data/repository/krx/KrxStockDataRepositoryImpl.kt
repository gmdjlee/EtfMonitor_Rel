package com.etfmonitor.core.data.repository.krx

import com.etfmonitor.core.analysis.TechnicalAnalysisEngine
import com.etfmonitor.core.analysis.model.*
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.data.krx.adapter.DateAdapter
import com.etfmonitor.core.data.krx.adapter.KrxRepositoryBase
import com.etfmonitor.core.database.StockDao
import com.etfmonitor.core.domain.repository.StockDataRepository
import com.krxkt.KrxStock
import com.krxkt.model.Market
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * KRX Stock Data Repository Implementation
 *
 * Replaces OscillatorPyClient with native kotlin_krx + Kotlin computations.
 *
 * ## Data Sources
 * - OHLCV: `KrxStock.getOhlcvByTicker()`
 * - Market cap: `KrxStock.getMarketCap()` + approximation from close * shares
 * - Investor trading: `KrxStock.getTradingByInvestor()` with 5-day rolling sum
 * - Stock list: `KrxStock.getTickerList()` with name lookup
 *
 * ## Computations (TechnicalAnalysisEngine)
 * - Resampling: weekly/monthly OHLCV aggregation
 * - CMF: Chaikin Money Flow
 * - Fear & Greed: Multi-factor sentiment index
 * - Signals: Buy/sell signal generation
 * - Elder Impulse: EMA + MACD slope analysis
 * - DeMark TD: Consecutive close vs. t-4 counting
 *
 * ## Trade-offs
 * - Market cap approximation: `close[i] * sharesOutstanding` (single latest cap call)
 *   - Acceptable: ElderImpulse/DemarkTD use it for display only
 *   - OscillatorCalculator: Proportional errors cancel in ratio calculations
 * - Stock search: Uses existing DB-based `StockRepository.searchStocks()`
 *
 * @see TechnicalAnalysisEngine Pure Kotlin computation engine
 * @see KrxRepositoryBase Timeout + error handling wrapper
 */
@Singleton
class KrxStockDataRepositoryImpl @Inject constructor(
    private val krxStock: KrxStock,
    private val stockDao: StockDao
) : KrxRepositoryBase(), StockDataRepository {

    companion object {
        private val logger = AppLogger.getLogger("KrxStockDataRepo")
        private const val TIMEOUT_30S = 30_000L

        /**
         * Calculate rolling sum for investor trading data
         *
         * @param values Daily trading values
         * @param window Rolling window size (default: 5 days)
         * @return List of rolling sums
         */
        private fun calculateRollingSum(values: List<Long>, window: Int = 5): List<Long> {
            return values.mapIndexed { index, _ ->
                if (index < window - 1) {
                    // Not enough data for full window, use partial sum
                    values.subList(0, index + 1).sum()
                } else {
                    // Full window rolling sum
                    values.subList(index - window + 1, index + 1).sum()
                }
            }
        }
    }

    // ============================================================
    // OHLCV Data
    // ============================================================

    override suspend fun getStockOhlcv(
        ticker: String,
        days: Int,
        interval: String
    ): StockOhlcvData? = withContext(Dispatchers.IO) {
        try {
            logger.d("getStockOhlcv: $ticker, $days days, interval=$interval")

            // Fetch extra days for resampled intervals (2x weekly, 3x monthly)
            val fetchDays = when (interval) {
                "w" -> days * 2
                "m" -> days * 3
                else -> days
            }

            val end = LocalDate.now()
            val start = end.minusDays(fetchDays.toLong())

            // Fetch OHLCV from kotlin_krx
            val result = krxCall(TIMEOUT_30S) {
                krxStock.getOhlcvByTicker(
                    startDate = DateAdapter.toKrxFormat(start),
                    endDate = DateAdapter.toKrxFormat(end),
                    ticker = ticker
                )
            }

            if (result.isFailure) {
                logger.e("getOhlcvByTicker failed: ${result.exceptionOrNull()?.message}")
                return@withContext null
            }

            val ohlcvList = result.getOrNull() ?: return@withContext null

            if (ohlcvList.isEmpty()) {
                logger.e("Empty OHLCV data for $ticker")
                return@withContext null
            }

            // Extract data (convert to appropriate types)
            var dates = ohlcvList.map { it.date }
            var open = ohlcvList.map { it.open.toDouble() }
            var high = ohlcvList.map { it.high.toDouble() }
            var low = ohlcvList.map { it.low.toDouble() }
            var close = ohlcvList.map { it.close.toDouble() }
            var volume = ohlcvList.map { it.volume }

            // Resample if needed
            if (interval == "w") {
                val resampled = TechnicalAnalysisEngine.resampleWeekly(dates, open, high, low, close, volume)
                dates = resampled.map { it.date }
                open = resampled.map { it.open }
                high = resampled.map { it.high }
                low = resampled.map { it.low }
                close = resampled.map { it.close }
                volume = resampled.map { it.volume }
            } else if (interval == "m") {
                val resampled = TechnicalAnalysisEngine.resampleMonthly(dates, open, high, low, close, volume)
                dates = resampled.map { it.date }
                open = resampled.map { it.open }
                high = resampled.map { it.high }
                low = resampled.map { it.low }
                close = resampled.map { it.close }
                volume = resampled.map { it.volume }
            }

            // Get stock name (ticker resolution from cache)
            val name = getStockName(ticker) ?: ticker

            StockOhlcvData(
                ticker = ticker,
                name = name,
                dates = dates,
                open = open,
                high = high,
                low = low,
                close = close,
                volume = volume
            ).also {
                logger.d("OHLCV data complete: $name, ${dates.size} records, interval=$interval")
            }

        } catch (e: Exception) {
            logger.e("getStockOhlcv error: ${e.message}", e)
            null
        }
    }

    // ============================================================
    // Stock Analysis Data (Market Cap + Investor Trading)
    // ============================================================

    override suspend fun getStockAnalysisData(
        ticker: String,
        days: Int
    ): StockData? = withContext(Dispatchers.IO) {
        try {
            logger.d("getStockAnalysisData: $ticker, $days days")

            val end = LocalDate.now()
            val start = end.minusDays(days.toLong())

            // 1. Get OHLCV for close prices
            logger.d("Requesting OHLCV: ${DateAdapter.toKrxFormat(start)} to ${DateAdapter.toKrxFormat(end)} ($days days)")

            val ohlcvResult = krxCall(TIMEOUT_30S) {
                krxStock.getOhlcvByTicker(
                    DateAdapter.toKrxFormat(start),
                    DateAdapter.toKrxFormat(end),
                    ticker
                )
            }

            if (ohlcvResult.isFailure) {
                logger.e("getOhlcvByTicker failed: ${ohlcvResult.exceptionOrNull()?.message}")
                return@withContext null
            }

            val ohlcvList = ohlcvResult.getOrNull() ?: return@withContext null
            if (ohlcvList.isEmpty()) {
                logger.e("Empty OHLCV data for $ticker")
                return@withContext null
            }

            logger.d("OHLCV data received: ${ohlcvList.size} records")
            logger.d("  First date: ${ohlcvList.firstOrNull()?.date}")
            logger.d("  Last date: ${ohlcvList.lastOrNull()?.date}")

            val dates = ohlcvList.map { it.date }
            val close = ohlcvList.map { it.close }

            // 2. Get latest market cap for shares outstanding
            // Use latest business day from OHLCV data (not end date which could be weekend/holiday)
            // NOTE: kotlin_krx returns dates in REVERSE chronological order (newest first)
            // NOTE: Market cap data may have significant lag, so we try up to 30 recent business days

            var sharesOutstanding = 0L
            var successfulDate: String? = null

            // Try up to 30 most recent business days to find when market cap data becomes available
            for (i in 0 until minOf(7, dates.size)) {
                val candidateDate = dates[i]
                logger.d("Attempting market cap query for date: $candidateDate (index=$i)")

                val capResult = krxCall(TIMEOUT_30S) {
                    krxStock.getMarketCap(candidateDate, Market.ALL)
                }

                if (capResult.isSuccess) {
                    val caps = capResult.getOrNull() ?: emptyList()
                    logger.d("getMarketCap returned ${caps.size} records for date $candidateDate")

                    if (caps.isNotEmpty()) {
                        val cap = caps.find { it.ticker == ticker }
                        if (cap != null) {

                            sharesOutstanding = if (cap.sharesOutstanding > 0) {
                                // Use actual shares outstanding from KRX API
                                cap.sharesOutstanding
                            } else {
                                logger.w("sharesOutstanding is 0, falling back to calculation")
                                if (cap.marketCap > 0 && cap.close > 0) {
                                    (cap.marketCap / cap.close)
                                } else {
                                    logger.e("Cannot calculate sharesOutstanding: marketCap=${cap.marketCap}, close=${cap.close}")
                                    0L
                                }
                            }

                            if (sharesOutstanding > 0) {
                                successfulDate = candidateDate
                                logger.d("✅ Successfully retrieved sharesOutstanding=$sharesOutstanding from date $candidateDate")
                                break  // Success! Stop trying
                            } else {
                                logger.w("sharesOutstanding is 0 for date $candidateDate, trying previous date...")
                            }
                        } else {
                            logger.w("Ticker $ticker not found in ${caps.size} market cap records for date $candidateDate")
                            logger.d("Available tickers sample: ${caps.take(5).map { it.ticker }}")
                            // Try next date
                        }
                    } else {
                        logger.w("getMarketCap returned 0 records for date $candidateDate, trying previous date...")
                    }
                } else {
                    logger.w("getMarketCap API failed for date $candidateDate: ${capResult.exceptionOrNull()?.message}")
                }
            }

            if (successfulDate != null) {
                logger.d("Using market cap data from date: $successfulDate (data lag handled)")
            } else {
                logger.e("Failed to retrieve market cap data from any of the 30 most recent business days")
                logger.e("  Tried dates from ${dates.firstOrNull()} to ${dates.getOrNull(29)}")
                logger.e("  This suggests the KRX Market Cap API may be unavailable or broken")
            }

            // 3. Approximate market cap history: close[i] * sharesOutstanding
            val marketCap = close.map { c -> (c * sharesOutstanding).toLong() }

            // 4. Get investor trading data (foreign + institution net buy)
            val tradingResult = krxCall(TIMEOUT_30S) {
                krxStock.getTradingByInvestor(
                    startDate = DateAdapter.toKrxFormat(start),
                    endDate = DateAdapter.toKrxFormat(end),
                    ticker = ticker,
                    valueType = com.krxkt.model.TradingValueType.VALUE,
                    askBidType = com.krxkt.model.AskBidType.NET_BUY
                )
            }

            val (foreign5d, institution5d) = if (tradingResult.isSuccess) {
                val tradingList = tradingResult.getOrNull() ?: emptyList()
                logger.d("getTradingByInvestor returned ${tradingList.size} records")

                if (tradingList.isEmpty()) {
                    logger.w("getTradingByInvestor returned empty list for $ticker")
                    Pair(List(dates.size) { 0L }, List(dates.size) { 0L })
                } else {
                    // Align trading data to OHLCV dates
                    val tradingMap = tradingList.associateBy { it.date }
                    val foreignDaily = dates.map { date -> tradingMap[date]?.foreigner ?: 0L }
                    val institutionDaily = dates.map { date -> tradingMap[date]?.institutionalTotal ?: 0L }

                    logger.d("  foreignDaily sample (first 3): ${foreignDaily.take(3)}")
                    logger.d("  institutionDaily sample (first 3): ${institutionDaily.take(3)}")

                    // Calculate 5-day rolling sum
                    val foreign5dSum = calculateRollingSum(foreignDaily, 5)
                    val institution5dSum = calculateRollingSum(institutionDaily, 5)

                    Pair(foreign5dSum, institution5dSum)
                }
            } else {
                logger.w("getTradingByInvestor failed, using zeros: ${tradingResult.exceptionOrNull()?.message}")
                Pair(List(dates.size) { 0L }, List(dates.size) { 0L })
            }

            // Get stock name
            val name = getStockName(ticker) ?: ticker

            StockData(
                ticker = ticker,
                name = name,
                dates = dates,
                marketCap = marketCap,
                foreign5d = foreign5d,
                institution5d = institution5d
            )

        } catch (e: Exception) {
            logger.e("getStockAnalysisData error: ${e.message}", e)
            null
        }
    }

    // ============================================================
    // Stock List
    // ============================================================

    override suspend fun getAllStocksList(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            logger.d("getAllStocksList")

            val result = krxCall(TIMEOUT_30S) {
                krxStock.getTickerList(DateAdapter.today(), Market.ALL)
            }

            if (result.isFailure) {
                logger.e("getTickerList failed: ${result.exceptionOrNull()?.message}")
                return@withContext emptyList()
            }

            val tickers = result.getOrNull() ?: return@withContext emptyList()

            tickers.map { Pair(it.ticker, it.name) }.also {
                logger.d("Retrieved ${it.size} stocks")
            }

        } catch (e: Exception) {
            logger.e("getAllStocksList error: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun getStockName(ticker: String): String? = withContext(Dispatchers.IO) {
        try {
            // Try local DB first (fast, no network)
            stockDao.getStockName(ticker)?.let { return@withContext it }

            // Fall back to KRX API only if not found locally
            val result = krxCall(TIMEOUT_30S) {
                krxStock.getTickerList(DateAdapter.today(), Market.ALL)
            }

            if (result.isSuccess) {
                val tickers = result.getOrNull() ?: return@withContext null
                tickers.find { it.ticker == ticker }?.name
            } else {
                null
            }

        } catch (e: Exception) {
            logger.e("getStockName error: ${e.message}", e)
            null
        }
    }

    // ============================================================
    // Trend Signal Analysis
    // ============================================================

    override suspend fun getTrendSignalData(
        ticker: String,
        days: Int,
        interval: String
    ): TrendSignalData? = withContext(Dispatchers.IO) {
        try {
            logger.d("getTrendSignalData: $ticker, $days days, interval=$interval")

            // 1. Get OHLCV data
            val ohlcvData = getStockOhlcv(ticker, days, interval) ?: return@withContext null

            // 2. Generate signals (MA + CMF + Fear&Greed + buy/sell)
            val signalResult = TechnicalAnalysisEngine.generateSignals(
                dates = ohlcvData.dates,
                high = ohlcvData.high,
                low = ohlcvData.low,
                close = ohlcvData.close,
                volume = ohlcvData.volume,
                maPeriod = 20,
                cmfPeriod = 4
            )

            TrendSignalData(
                ticker = ticker,
                name = ohlcvData.name,
                interval = interval,
                dates = ohlcvData.dates,
                open = ohlcvData.open,
                high = ohlcvData.high,
                low = ohlcvData.low,
                close = ohlcvData.close,
                volume = ohlcvData.volume,
                ma = signalResult.ma,
                cmf = signalResult.cmf,
                fearGreed = signalResult.fearGreed,
                buySignal = signalResult.buySignal,
                auxBuySignal = signalResult.auxBuySignal,
                sellSignal = signalResult.sellSignal,
                auxSellSignal = signalResult.auxSellSignal
            ).also {
                logger.d("Trend signal data complete: ${it.name}, ${it.dates.size} records")
            }

        } catch (e: Exception) {
            logger.e("getTrendSignalData error: ${e.message}", e)
            null
        }
    }

    // ============================================================
    // Elder Impulse System
    // ============================================================

    override suspend fun getElderImpulseData(
        ticker: String,
        days: Int,
        interval: String
    ): ElderImpulseData? = withContext(Dispatchers.IO) {
        try {
            logger.d("getElderImpulseData: $ticker, $days days, interval=$interval")

            // 1. Get OHLCV data
            val ohlcvData = getStockOhlcv(ticker, days, interval) ?: return@withContext null

            // 2. Get market cap approximation (retry up to 7 OHLCV dates to handle weekends/holidays)
            var sharesOutstanding = 0L
            for (i in 0 until minOf(7, ohlcvData.dates.size)) {
                val candidateDate = ohlcvData.dates[i]
                val capResult = krxCall(TIMEOUT_30S) {
                    krxStock.getMarketCap(candidateDate, Market.ALL)
                }
                if (capResult.isSuccess) {
                    val cap = capResult.getOrNull()?.find { it.ticker == ticker }
                    if (cap != null) {
                        sharesOutstanding = if (cap.sharesOutstanding > 0) {
                            cap.sharesOutstanding
                        } else if (cap.marketCap > 0 && cap.close > 0) {
                            (cap.marketCap / cap.close)
                        } else {
                            0L
                        }
                        if (sharesOutstanding > 0) break
                    }
                }
            }

            val marketCap = ohlcvData.close.map { c -> (c * sharesOutstanding).toLong() }

            // 3. Calculate Elder Impulse
            val impulseResult = TechnicalAnalysisEngine.calculateElderImpulse(
                close = ohlcvData.close,
                emaPeriod = 13
            )

            ElderImpulseData(
                ticker = ticker,
                name = ohlcvData.name,
                interval = interval,
                dates = ohlcvData.dates,
                close = ohlcvData.close,
                marketCap = marketCap,
                ema = impulseResult.ema,
                macd = impulseResult.macd,
                macdSignal = impulseResult.macdSignal,
                macdHist = impulseResult.macdHist,
                impulse = impulseResult.impulse
            ).also {
                logger.d("Elder Impulse data complete: ${it.name}, ${it.dates.size} records")
            }

        } catch (e: Exception) {
            logger.e("getElderImpulseData error: ${e.message}", e)
            null
        }
    }

    // ============================================================
    // DeMark TD Setup
    // ============================================================

    override suspend fun getDemarkTDData(
        ticker: String,
        days: Int,
        interval: String
    ): DemarkTDData? = withContext(Dispatchers.IO) {
        try {
            logger.d("getDemarkTDData: $ticker, $days days, interval=$interval")

            // 1. Get OHLCV data
            val ohlcvData = getStockOhlcv(ticker, days, interval) ?: return@withContext null

            // 2. Get market cap approximation (retry up to 7 OHLCV dates to handle weekends/holidays)
            var sharesOutstanding = 0L
            for (i in 0 until minOf(7, ohlcvData.dates.size)) {
                val candidateDate = ohlcvData.dates[i]
                val capResult = krxCall(TIMEOUT_30S) {
                    krxStock.getMarketCap(candidateDate, Market.ALL)
                }
                if (capResult.isSuccess) {
                    val cap = capResult.getOrNull()?.find { it.ticker == ticker }
                    if (cap != null) {
                        sharesOutstanding = if (cap.sharesOutstanding > 0) {
                            cap.sharesOutstanding
                        } else if (cap.marketCap > 0 && cap.close > 0) {
                            (cap.marketCap / cap.close)
                        } else {
                            0L
                        }
                        if (sharesOutstanding > 0) break
                    }
                }
            }

            val marketCap = ohlcvData.close.map { c -> (c * sharesOutstanding).toLong() }

            // 3. Calculate DeMark TD Setup
            val tdResult = TechnicalAnalysisEngine.calculateDemarkTD(ohlcvData.close)

            val intervalName = when (interval) {
                "d" -> "일봉"
                "w" -> "주봉"
                "m" -> "월봉"
                else -> interval
            }

            DemarkTDData(
                ticker = ticker,
                name = ohlcvData.name,
                interval = interval,
                intervalName = intervalName,
                dates = ohlcvData.dates,
                close = ohlcvData.close,
                marketCap = marketCap,
                tdSell = tdResult.tdSell,
                tdBuy = tdResult.tdBuy
            ).also {
                logger.d("DeMark TD data complete: ${it.name}, ${it.dates.size} records, $intervalName")
            }

        } catch (e: Exception) {
            logger.e("getDemarkTDData error: ${e.message}", e)
            null
        }
    }
}
