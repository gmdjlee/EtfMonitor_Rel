package com.etfmonitor.feature.market.data.repository

import com.etfmonitor.core.analysis.FearGreedCalculator
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.common.util.DateFormatter
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.FearGreedDao
import com.etfmonitor.core.database.entities.FearGreedIndex as FearGreedEntity
import com.etfmonitor.core.database.entities.Setting
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDomain
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toFearGreedDomainList
import com.etfmonitor.feature.market.domain.model.FearGreedIndex
import com.etfmonitor.feature.market.domain.repository.FearGreedRepository
import com.krxkt.KrxIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.SortedSet
import java.util.TreeSet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fear & Greed Repository Implementation
 *
 * Replaces Python feargreed.py with direct kotlin_krx API calls + FearGreedCalculator.
 *
 * Data pipeline:
 *   1. Fetch 7 KRX datasets in parallel (call/put options, bond5y, bond10y, vkospi, kospi, kosdaq)
 *   2. Merge by date using Map<String, MergedDayData>
 *   3. Apply 5-day rolling mean to raw option volumes
 *   4. Feed merged rows into FearGreedCalculator.calcFearGreed()
 *   5. Filter results where fearGreedValue.isFinite() (removes NaN warm-up rows)
 *   6. Persist to DB
 *
 * Notes:
 *  - kotlin_krx returns dates in "yyyyMMdd" format; converted to "yyyy-MM-dd" for DB storage
 *  - Option data (Call/Put) missing → fatal error (mirrors Python: `if call is None or put is None`)
 *  - KOSPI/KOSDAQ index missing → that market's results are skipped (optional in Python too)
 *  - Overall timeout: 90s (7 parallel API calls, each individually bounded)
 */
@Singleton
class FearGreedRepositoryImpl @Inject constructor(
    private val fearGreedDao: FearGreedDao,
    private val etfDao: EtfDao,
    private val krxIndex: KrxIndex
) : FearGreedRepository {

    companion object {
        private val logger = AppLogger.getLogger("FearGreedRepoImpl")
        private const val KEY_DIALOG_DISMISSED = "fear_greed_dialog_dismissed"

        // Minimum rows required before analysis (mirrors Python `if len(df) < 15`)
        private const val MIN_ROWS = 15

        // KRX Akamai WAF rate-limit 대응: 배치 간 딜레이
        private const val KRX_BATCH_DELAY_MS = 2_000L
    }

    // =========================================================================
    // Read-only DB queries — unchanged
    // =========================================================================

    override fun getAllByMarket(market: String): Flow<List<FearGreedIndex>> =
        fearGreedDao.getAllByMarket(market)
            .map { it.toFearGreedDomainList() }
            .flowOn(Dispatchers.IO)

    override fun getRecentByMarket(market: String, limit: Int): Flow<List<FearGreedIndex>> =
        fearGreedDao.getRecentByMarket(market, limit)
            .map { it.toFearGreedDomainList() }
            .flowOn(Dispatchers.IO)

    override fun getByMarketAndDateRange(
        market: String,
        startDate: String,
        endDate: String
    ): Flow<List<FearGreedIndex>> =
        fearGreedDao.getByMarketAndDateRange(market, startDate, endDate)
            .map { it.toFearGreedDomainList() }
            .flowOn(Dispatchers.IO)

    override suspend fun getByMarketAndDate(market: String, date: String): FearGreedIndex? =
        withContext(Dispatchers.IO) {
            fearGreedDao.getByMarketAndDate(market, date)?.toDomain()
        }

    override suspend fun getCountByMarket(market: String): Int =
        withContext(Dispatchers.IO) {
            fearGreedDao.getCountByMarket(market)
        }

    override suspend fun getLatestDate(market: String): String? =
        withContext(Dispatchers.IO) {
            fearGreedDao.getLatestDate(market)
        }

    override suspend fun getLastUpdateTime(market: String): Long? =
        withContext(Dispatchers.IO) {
            fearGreedDao.getLastUpdateTime(market)
        }

    override suspend fun isDialogDismissed(): Boolean = withContext(Dispatchers.IO) {
        etfDao.getSetting(KEY_DIALOG_DISMISSED) == "true"
    }

    override suspend fun saveDialogDismissed() = withContext(Dispatchers.IO) {
        etfDao.saveSetting(Setting(KEY_DIALOG_DISMISSED, "true"))
    }

    // =========================================================================
    // Write operations — rewritten to use kotlin_krx
    // =========================================================================

    /**
     * Fear & Greed Index 데이터 초기화 (지정된 기간 동안의 데이터 수집)
     *
     * @param days 원하는 결과 데이터 기간 (기본 365일)
     *
     * MA warm-up 손실(최대 ~125일)을 보완하기 위해 3배 기간을 수집합니다.
     * KRX API 한계로 최대 730일(약 2년)까지만 수집합니다.
     */
    override suspend fun initializeFearGreed(
        days: Int,
        onProgress: ((String, Int) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val collectionDays = minOf(days * 3, 730)
            logger.d("Initializing Fear & Greed: requested=$days days, collecting=$collectionDays days")

            onProgress?.invoke("Fear & Greed Index 데이터 수집 준비 중...", 0)

            val yyyyMMdd = DateTimeFormatter.ofPattern("yyyyMMdd")
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(collectionDays.toLong())
            val startStr = startDate.format(yyyyMMdd)
            val endStr = endDate.format(yyyyMMdd)

            onProgress?.invoke("시장 데이터 수집 중...", 20)
            val fearGreedData = try {
                fetchAndCalculate(startStr, endStr, onProgress)
            } catch (e: Exception) {
                logger.e("fetchAndCalculate failed", e)
                return@withContext Result.failure(Exception("Fear & Greed 계산 실패: ${e.message}", e))
            }

            if (fearGreedData.isEmpty()) {
                logger.e("No Fear & Greed data calculated")
                return@withContext Result.failure(Exception("계산된 데이터가 없습니다"))
            }

            onProgress?.invoke("데이터베이스 저장 중...", 90)
            fearGreedDao.deleteAll()
            fearGreedDao.insertAll(fearGreedData)

            logger.d("Successfully initialized ${fearGreedData.size} Fear & Greed records")
            onProgress?.invoke("완료", 100)
            Result.success(fearGreedData.size)
        } catch (e: kotlinx.coroutines.CancellationException) {
            logger.w("Initialization cancelled")
            throw e
        } catch (e: Exception) {
            logger.e("Error initializing Fear & Greed data", e)
            Result.failure(e)
        }
    }

    /**
     * Fear & Greed Index 데이터 업데이트 (최근 데이터만 갱신)
     *
     * MA 손실 보완을 위해 150일 수집합니다.
     */
    override suspend fun updateFearGreed(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            logger.d("Updating Fear & Greed Index data...")

            val yyyyMMdd = DateTimeFormatter.ofPattern("yyyyMMdd")
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(150)
            val startStr = startDate.format(yyyyMMdd)
            val endStr = endDate.format(yyyyMMdd)

            val fearGreedData = try {
                fetchAndCalculate(startStr, endStr)
            } catch (e: Exception) {
                logger.e("fetchAndCalculate failed", e)
                return@withContext Result.failure(Exception("Fear & Greed 계산 실패: ${e.message}", e))
            }

            if (fearGreedData.isEmpty()) {
                logger.e("No Fear & Greed data calculated")
                return@withContext Result.failure(Exception("계산된 데이터가 없습니다"))
            }

            // REPLACE strategy eliminates duplicates
            fearGreedDao.insertAll(fearGreedData)

            logger.d("Successfully updated ${fearGreedData.size} Fear & Greed records")
            Result.success(fearGreedData.size)
        } catch (e: kotlinx.coroutines.CancellationException) {
            logger.w("Update cancelled")
            throw e
        } catch (e: Exception) {
            logger.e("Error updating Fear & Greed data", e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // Core fetch-and-calculate logic (replaces Python combine() + analyze())
    // =========================================================================

    /**
     * kotlin_krx API를 호출하여 Fear & Greed Index 계산
     *
     * Replaces Python `calculateFearGreed()` that called `module["combine"]` + `module["analyze"]`.
     *
     * @param startDate "yyyyMMdd"
     * @param endDate   "yyyyMMdd"
     */
    private suspend fun fetchAndCalculate(
        startDate: String,
        endDate: String,
        onProgress: ((String, Int) -> Unit)? = null
    ): List<FearGreedEntity> = withContext(Dispatchers.IO) {
        withTimeout(90_000L) {
            logger.d("fetchAndCalculate: $startDate ~ $endDate")

            // ------------------------------------------------------------------
            // Step 1: Fetch all 7 datasets in parallel (replaces Python KRXFetcher)
            // ------------------------------------------------------------------
            onProgress?.invoke("원시 데이터 수집 중...", 30)

            // KRX Akamai WAF rate-limit 대응: 7개 요청을 2 배치로 분할 (4+3)
            val (callVol, putVol, bond5y, bond10y, vkospi, kospi, kosdaq) = coroutineScope {
                // Batch 1: 옵션 + 채권 데이터 (4개)
                val callD = async { runCatching { krxIndex.getCallOptionVolume(startDate, endDate) } }
                val putD  = async { runCatching { krxIndex.getPutOptionVolume(startDate, endDate) } }
                val b5D   = async { runCatching { krxIndex.getBond5y(startDate, endDate) } }
                val b10D  = async { runCatching { krxIndex.getBond10y(startDate, endDate) } }
                val callResult = callD.await()
                val putResult = putD.await()
                val b5Result = b5D.await()
                val b10Result = b10D.await()

                // 배치 간 딜레이 (KRX Akamai rate limit 방지)
                delay(KRX_BATCH_DELAY_MS)

                // Batch 2: 변동성 + 지수 데이터 (3개)
                val vkD   = async { runCatching { krxIndex.getVkospi(startDate, endDate) } }
                val kpD   = async { runCatching { krxIndex.getKospi(startDate, endDate) } }
                val kqD   = async { runCatching { krxIndex.getKosdaq(startDate, endDate) } }

                FetchResults(
                    callResult, putResult, b5Result, b10Result,
                    vkD.await(), kpD.await(), kqD.await()
                )
            }

            // Option data is required — fatal if missing (mirrors Python)
            val callList = callVol.getOrElse {
                logger.e("getCallOptionVolume failed: ${it.message}")
                return@withTimeout emptyList()
            }
            val putList = putVol.getOrElse {
                logger.e("getPutOptionVolume failed: ${it.message}")
                return@withTimeout emptyList()
            }

            // Required derivative indices — fatal if missing
            val bond5yList = bond5y.getOrElse {
                logger.e("getBond5y failed: ${it.message}")
                return@withTimeout emptyList()
            }
            val bond10yList = bond10y.getOrElse {
                logger.e("getBond10y failed: ${it.message}")
                return@withTimeout emptyList()
            }
            val vkospiList = vkospi.getOrElse {
                logger.e("getVkospi failed: ${it.message}")
                return@withTimeout emptyList()
            }

            // Index data is optional — one market may be unavailable
            val kospiList  = kospi.getOrNull()
            val kosdaqList = kosdaq.getOrNull()

            if (kospiList == null)  logger.w("getKospi failed — KOSPI results will be skipped")
            if (kosdaqList == null) logger.w("getKosdaq failed — KOSDAQ results will be skipped")

            // ------------------------------------------------------------------
            // Step 2: Sort call/put chronologically and apply 5-day rolling mean
            //         (replaces Python call["전체"].rolling(5).mean())
            // ------------------------------------------------------------------
            val sortedCall  = callList.sortedBy { it.date }
            val sortedPut   = putList.sortedBy { it.date }

            val callMa5 = FearGreedCalculator.rollingMean5(sortedCall.map { it.totalVolume })
            val putMa5  = FearGreedCalculator.rollingMean5(sortedPut.map { it.totalVolume })

            // Map date → rolling mean value (NaN entries skipped — same as Python dropna)
            val callByDate: Map<String, Double> = sortedCall.indices
                .filter { callMa5[it].isFinite() }
                .associate { i -> sortedCall[i].date to callMa5[i] }

            val putByDate: Map<String, Double> = sortedPut.indices
                .filter { putMa5[it].isFinite() }
                .associate { i -> sortedPut[i].date to putMa5[i] }

            // ------------------------------------------------------------------
            // Step 3: Build lookup maps for the other 5 datasets
            // ------------------------------------------------------------------
            val bond5yByDate  = bond5yList.associate  { it.date to it.close }
            val bond10yByDate = bond10yList.associate { it.date to it.close }
            val vkospiByDate  = vkospiList.associate  { it.date to it.close }
            val kospiByDate   = kospiList?.associate  { it.date to it.close } ?: emptyMap()
            val kosdaqByDate  = kosdaqList?.associate { it.date to it.close } ?: emptyMap()

            // ------------------------------------------------------------------
            // Step 4: Build merged date set (outer join — then drop rows missing
            //         required fields, mirroring Python dropna(subset=req))
            // ------------------------------------------------------------------
            val allDates: TreeSet<String> = TreeSet<String>().apply {
                addAll(callByDate.keys)
                addAll(putByDate.keys)
                addAll(bond5yByDate.keys)
                addAll(bond10yByDate.keys)
                addAll(vkospiByDate.keys)
                addAll(kospiByDate.keys)
                addAll(kosdaqByDate.keys)
            }

            val mergedRows: List<MergedRow> = allDates.mapNotNull { date ->
                val call   = callByDate[date]    ?: return@mapNotNull null
                val put    = putByDate[date]     ?: return@mapNotNull null
                val b5     = bond5yByDate[date]  ?: return@mapNotNull null
                val b10    = bond10yByDate[date] ?: return@mapNotNull null
                val vix    = vkospiByDate[date]  ?: return@mapNotNull null
                MergedRow(
                    date    = date,
                    call    = call,
                    put     = put,
                    bond5y  = b5,
                    bond10y = b10,
                    vix     = vix,
                    kospi   = kospiByDate[date],
                    kosdaq  = kosdaqByDate[date]
                )
            }

            if (mergedRows.size < MIN_ROWS) {
                logger.e("Insufficient merged data: ${mergedRows.size} rows (min $MIN_ROWS required)")
                return@withTimeout emptyList()
            }

            logger.d("Merged rows: ${mergedRows.size}")
            onProgress?.invoke("Fear & Greed Index 분석 중...", 60)

            // ------------------------------------------------------------------
            // Step 5: Calculate Fear & Greed for each market
            //         (replaces Python _calc_fg() + analyze())
            // ------------------------------------------------------------------
            val results = mutableListOf<FearGreedEntity>()

            fun calcForMarket(market: String, indexValues: List<Double?>) {
                // Filter to rows where this market's index value is present
                val validPairs = mergedRows.zip(indexValues)
                    .filter { (_, idx) -> idx != null }

                if (validPairs.isEmpty()) {
                    logger.d("No $market data available — skipping")
                    return
                }

                val dayData: List<FearGreedCalculator.FearGreedDayData> = validPairs.map { (row, idx) ->
                    FearGreedCalculator.FearGreedDayData(
                        date       = DateFormatter.formatFromYYYYMMDD(row.date),
                        indexValue = idx!!,
                        call       = row.call,
                        put        = row.put,
                        vix        = row.vix,
                        bond5y     = row.bond5y,
                        bond10y    = row.bond10y
                    )
                }

                val fgResults = FearGreedCalculator.calcFearGreed(dayData)

                // Only keep fully-valid rows (filter NaN warm-up period, mirrors Python .dropna())
                val entities = fgResults.mapNotNull { fg ->
                    if (!fg.fearGreedValue.isFinite() || !fg.oscillator.isFinite()) return@mapNotNull null
                    FearGreedEntity(
                        id             = "$market-${fg.date}",
                        market         = market,
                        date           = fg.date,
                        indexValue     = fg.indexValue,
                        fearGreedValue = fg.fearGreedValue,
                        oscillator     = fg.oscillator,
                        rsi            = if (fg.rsi.isFinite()) fg.rsi else 0.0,
                        momentum       = if (fg.momentum.isFinite()) fg.momentum else 0.0,
                        putCallRatio   = if (fg.putCallRatio.isFinite()) fg.putCallRatio else 0.0,
                        volatility     = if (fg.volatility.isFinite()) fg.volatility else 0.0,
                        spread         = if (fg.spread.isFinite()) fg.spread else 0.0,
                        lastUpdated    = System.currentTimeMillis()
                    )
                }

                logger.d("$market: ${entities.size} valid records out of ${fgResults.size}")
                results.addAll(entities)
            }

            calcForMarket("KOSPI",  mergedRows.map { it.kospi })
            calcForMarket("KOSDAQ", mergedRows.map { it.kosdaq })

            if (results.isEmpty()) {
                logger.e("No Fear & Greed data calculated for any market")
            } else {
                logger.d("Total Fear & Greed records: ${results.size}")
            }

            onProgress?.invoke("데이터 파싱 중...", 80)
            results
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /** One row after merging all 7 datasets on date. */
    private data class MergedRow(
        val date: String,     // "yyyyMMdd"
        val call: Double,
        val put: Double,
        val bond5y: Double,
        val bond10y: Double,
        val vix: Double,
        val kospi: Double?,
        val kosdaq: Double?
    )

    /** Destructure helper for the 7 parallel fetch results. */
    private data class FetchResults(
        val callVol: Result<List<com.krxkt.model.OptionVolume>>,
        val putVol:  Result<List<com.krxkt.model.OptionVolume>>,
        val bond5y:  Result<List<com.krxkt.model.DerivativeIndex>>,
        val bond10y: Result<List<com.krxkt.model.DerivativeIndex>>,
        val vkospi:  Result<List<com.krxkt.model.DerivativeIndex>>,
        val kospi:   Result<List<com.krxkt.model.IndexOhlcv>>,
        val kosdaq:  Result<List<com.krxkt.model.IndexOhlcv>>
    )
}
