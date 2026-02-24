package com.etfmonitor.core.data.repository.krx

import com.etfmonitor.core.analysis.TechnicalAnalysisEngine
import com.etfmonitor.core.analysis.model.*
import com.etfmonitor.core.common.model.SharesType
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.data.krx.adapter.DateAdapter
import com.etfmonitor.core.data.krx.adapter.KrxRepositoryBase
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.StockDao
import com.etfmonitor.core.domain.repository.StockDataRepository
import com.etfmonitor.core.network.kiwoom.KiwoomApiClient
import com.etfmonitor.core.network.kiwoom.KiwoomApiKeyProvider
import com.etfmonitor.core.network.kiwoom.StockBasicInfoResponse
import com.krxkt.KrxStock
import com.krxkt.model.Market
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
    private val stockDao: StockDao,
    private val kiwoomApiClient: KiwoomApiClient,
    private val kiwoomApiKeyProvider: KiwoomApiKeyProvider,
    private val json: Json,
    private val etfDao: EtfDao
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

        } catch (e: CancellationException) {
            throw e
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

            // 2. Get shares for market cap calculation based on user setting
            val (shares, sharesSource) = fetchSharesBySettings(ticker, dates)
            if (shares > 0) {
                logger.d("Market cap source: $sharesSource, shares=$shares")
            }

            // 3. Approximate market cap history: close[i] * shares
            val marketCap = close.map { c -> (c * shares).toLong() }

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

                    // Rolling sum on newest-first data directly (StockApp 방식)
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

        } catch (e: CancellationException) {
            throw e
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

        } catch (e: CancellationException) {
            throw e
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

        } catch (e: CancellationException) {
            throw e
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
            // cmfPeriod: daily=20 (reference default), weekly=4 (reference weekly default)
            val cmfPeriod = if (interval == "w") 4 else 20
            val signalResult = TechnicalAnalysisEngine.generateSignals(
                dates = ohlcvData.dates,
                high = ohlcvData.high,
                low = ohlcvData.low,
                close = ohlcvData.close,
                volume = ohlcvData.volume,
                maPeriod = 20,
                cmfPeriod = cmfPeriod
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

        } catch (e: CancellationException) {
            throw e
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

            // 2. Get market cap based on user setting
            val (sharesForElder, _) = fetchSharesBySettings(ticker, ohlcvData.dates)
            val marketCap = ohlcvData.close.map { c -> (c * sharesForElder).toLong() }

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

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("getElderImpulseData error: ${e.message}", e)
            null
        }
    }

    // ============================================================
    // Shared: Shares lookup based on user setting
    // ============================================================

    /**
     * Fetch shares count based on the user's SharesType setting.
     *
     * @return Pair of (shares count, source label) e.g. (1234567L, "kiwoom_floating")
     */
    private suspend fun fetchSharesBySettings(
        ticker: String,
        dates: List<String>
    ): Pair<Long, String> {
        val sharesTypeName = etfDao.getSetting("shares_type") ?: SharesType.FLOATING.name
        val sharesType = try { SharesType.valueOf(sharesTypeName) } catch (_: Exception) { SharesType.FLOATING }

        when (sharesType) {
            SharesType.FLOATING -> {
                val floatingShares = fetchFloatingShares(ticker)
                if (floatingShares > 0) {
                    logger.d("Using floatingShares from Kiwoom ka10001: $floatingShares")
                    return Pair(floatingShares, "kiwoom_floating")
                }
            }
            SharesType.OUTSTANDING -> {
                for (i in 0 until minOf(7, dates.size)) {
                    val candidateDate = dates[i]
                    val capResult = krxCall(TIMEOUT_30S) {
                        krxStock.getMarketCap(candidateDate, Market.ALL)
                    }
                    if (capResult.isSuccess) {
                        val cap = capResult.getOrNull()?.find { it.ticker == ticker }
                        if (cap != null) {
                            val shares = when {
                                cap.sharesOutstanding > 0 -> cap.sharesOutstanding
                                cap.marketCap > 0 && cap.close > 0 -> cap.marketCap / cap.close
                                else -> 0L
                            }
                            if (shares > 0) {
                                logger.d("Using sharesOutstanding from KRX: $shares (date=$candidateDate)")
                                return Pair(shares, "krx_outstanding")
                            }
                        }
                    }
                }
            }
        }

        logger.e("Failed to retrieve shares data for $ticker (mode=$sharesType)")
        return Pair(0L, "none")
    }

    // ============================================================
    // Kiwoom ka10001 — 유통주식수 (floating shares)
    // ============================================================

    /**
     * Fetch floating shares from Kiwoom ka10001 (주식 기본정보).
     *
     * @return Floating shares count (actual shares, not 천주), or 0L if unavailable.
     */
    private suspend fun fetchFloatingShares(ticker: String): Long {
        try {
            val config = kiwoomApiKeyProvider.getConfig()
            if (!config.isValid()) {
                logger.d("Kiwoom API not configured, skipping floatingShares fetch")
                return 0L
            }

            val result = kiwoomApiClient.call(
                apiId = "ka10001",
                url = "/api/dostk/stkinfo",
                body = mapOf("stk_cd" to ticker),
                appKey = config.appKey,
                secretKey = config.secretKey,
                baseUrl = config.getBaseUrl()
            ) { responseJson ->
                json.decodeFromString<StockBasicInfoResponse>(responseJson)
            }

            if (result.isSuccess) {
                val response = result.getOrNull()
                val floStk = response?.floStk?.replace(",", "")?.trim()?.toLongOrNull() ?: 0L
                val floatingShares = if (floStk > 0) floStk * 1000 else 0L  // 천주 → 실제 주식수
                if (floatingShares > 0) {
                    logger.d("Kiwoom ka10001 floatingShares for $ticker: $floatingShares (flo_stk=$floStk 천주)")
                }
                return floatingShares
            } else {
                logger.w("Kiwoom ka10001 failed for $ticker: ${result.exceptionOrNull()?.message}")
                return 0L
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w("fetchFloatingShares error for $ticker: ${e.message}")
            return 0L
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

            // 2. Get market cap based on user setting
            val (sharesForTD, _) = fetchSharesBySettings(ticker, ohlcvData.dates)
            val marketCap = ohlcvData.close.map { c -> (c * sharesForTD).toLong() }

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

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("getDemarkTDData error: ${e.message}", e)
            null
        }
    }
}
