package com.etfmonitor.feature.etf.data.repository

import com.etfmonitor.feature.etf.data.datasource.EtfLocalDataSource
import com.etfmonitor.feature.etf.data.mapper.EtfMapper.toDomain
import com.etfmonitor.feature.etf.domain.model.ComparisonResult
import com.etfmonitor.feature.etf.domain.model.DataProgress
import com.etfmonitor.feature.etf.domain.model.DataStatus
import com.etfmonitor.feature.etf.domain.model.Etf
import com.etfmonitor.feature.etf.domain.model.HoldingStatus
import com.etfmonitor.feature.etf.domain.model.HoldingWithComparison
import com.etfmonitor.feature.etf.domain.repository.EtfRepository
import com.etfmonitor.core.common.util.AmountFormatter
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.database.DailyEtfStatisticsDao
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.StockDao
import com.etfmonitor.core.database.entities.DailyEtfStatistics
import com.etfmonitor.core.database.entities.Holding
import com.etfmonitor.core.database.entities.Setting
import com.etfmonitor.core.domain.usecase.krx.GetKrxBusinessDaysUseCase
import com.etfmonitor.core.domain.usecase.krx.GetKrxEtfHoldingsUseCase
import com.etfmonitor.core.domain.usecase.krx.GetKrxEtfListUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ETF Repository Implementation
 *
 * Domain 레이어의 EtfRepository 인터페이스를 구현합니다.
 * EtfLocalDataSource로부터 Entity를 받아 Domain Model로 변환합니다.
 *
 * ## 성능 최적화
 * - 모든 Flow는 flowOn(Dispatchers.IO)로 실행
 * - 모든 suspend 함수는 withContext(Dispatchers.IO)로 실행
 *
 * ## 비교 분석 로직
 * - 최근 2일간의 보유 종목을 비교하여 상태(NEW/INCREASE/DECREASE/MAINTAIN/REMOVED) 결정
 * - 변화량 임계값: 0.01% (WEIGHT_CHANGE_THRESHOLD)
 */
@Singleton
class EtfRepositoryImpl @Inject constructor(
    private val localDataSource: EtfLocalDataSource,
    private val etfDao: EtfDao,
    private val dailyEtfStatisticsDao: DailyEtfStatisticsDao,
    private val stockDao: StockDao,
    private val getKrxBusinessDaysUseCase: GetKrxBusinessDaysUseCase,
    private val getKrxEtfHoldingsUseCase: GetKrxEtfHoldingsUseCase,
    private val getKrxEtfListUseCase: GetKrxEtfListUseCase
) : EtfRepository {

    companion object {
        private val logger = AppLogger.getLogger("EtfRepositoryImpl")
        // Holding weight change threshold for status determination (in percentage points)
        private const val WEIGHT_CHANGE_THRESHOLD = 0.01f
        private const val PARALLEL_LIMIT = 3  // KRX Akamai WAF rate-limit 대응: 동시 요청 최대 3개
        private const val PER_CHUNK_DELAY_MS = 500L  // 청크 간 딜레이 (rate limit 방지)
        // Basis points threshold for statistics (1% = 100 bps)
        private const val WEIGHT_CHANGE_THRESHOLD_BPS = 100
    }

    // One-shot flag: normalize yyyyMMdd → yyyy-MM-dd in holdings table (Critical Rule #10)
    @Volatile
    private var dateFormatNormalized = false

    // 키워드 변경 시 Flow 재평가 트리거
    private val _keywordVersion = MutableStateFlow(0L)

    private suspend fun ensureDateFormatNormalized() {
        if (!dateFormatNormalized) {
            etfDao.normalizeDateFormat()
            dateFormatNormalized = true
        }
    }

    // ========== ETF List ==========

    override fun getAllEtfs(): Flow<List<Etf>> =
        localDataSource.getAllEtfs()
            .map { entities -> entities.toDomain() }
            .flowOn(Dispatchers.IO)

    override fun getVisibleEtfs(): Flow<List<Etf>> =
        combine(
            localDataSource.getAllEtfs().map { entities -> entities.toDomain() },
            _keywordVersion
        ) { etfs, _ ->
            applyKeywordFilter(etfs)
        }.flowOn(Dispatchers.IO)

    override fun searchEtfs(query: String): Flow<List<Etf>> =
        localDataSource.searchEtfs(query)
            .map { entities -> entities.toDomain() }
            .flowOn(Dispatchers.IO)

    override fun searchVisibleEtfs(query: String): Flow<List<Etf>> =
        combine(
            localDataSource.searchEtfs(query).map { entities -> entities.toDomain() },
            _keywordVersion
        ) { etfs, _ ->
            applyKeywordFilter(etfs)
        }.flowOn(Dispatchers.IO)

    // ========== Data Status ==========

    override suspend fun hasData(): Boolean = withContext(Dispatchers.IO) {
        val count = localDataSource.getEtfCount()
        logger.d("hasData: count = $count")
        count > 0
    }

    override suspend fun getDataStatus(): DataStatus = withContext(Dispatchers.IO) {
        val count = localDataSource.getEtfCount()
        val latestDate = localDataSource.getLatestDate()
        DataStatus(
            hasData = count > 0,
            latestDate = latestDate
        )
    }

    override suspend fun getLatestDate(): String? = withContext(Dispatchers.IO) {
        val date = localDataSource.getLatestDate()
        logger.d("getLatestDate: $date")
        date
    }

    // ========== ETF Detail ==========

    override suspend fun getEtf(ticker: String): Etf? = withContext(Dispatchers.IO) {
        localDataSource.getEtf(ticker)?.toDomain()
    }

    override suspend fun getComparison(etfTicker: String): ComparisonResult? = withContext(Dispatchers.IO) {
        ensureDateFormatNormalized()
        val dates = localDataSource.getDates(etfTicker)

        logger.d("getComparison for $etfTicker: ${dates.size} dates available")

        if (dates.isEmpty()) {
            logger.d("No dates found for $etfTicker")
            return@withContext null
        }

        if (dates.size == 1) {
            logger.d("Only one date available: ${dates[0]}")
            val current = localDataSource.getHoldings(etfTicker, dates[0])
            return@withContext ComparisonResult(
                etfTicker = etfTicker,
                currentDate = dates[0],
                previousDate = "N/A",
                items = current.map { holding ->
                    HoldingWithComparison(
                        stockTicker = holding.stockTicker,
                        stockName = holding.stockName,
                        previousWeight = 0f,
                        currentWeight = holding.weight,
                        change = holding.weight,
                        currentAmount = holding.amount,
                        status = HoldingStatus.NEW
                    )
                },
                collectionStartDate = dates[0],
                collectionEndDate = dates[0]
            )
        }

        val currentDate = dates[0]
        val previousDate = dates[1]

        logger.d("Comparing: $previousDate vs $currentDate")

        val current = localDataSource.getHoldings(etfTicker, currentDate)
        val previous = localDataSource.getHoldings(etfTicker, previousDate)

        logger.d("Current holdings: ${current.size}, Previous holdings: ${previous.size}")

        val currentMap = current.associateBy { it.stockTicker }
        val previousMap = previous.associateBy { it.stockTicker }

        val allTickers = (currentMap.keys + previousMap.keys).toSet()

        val items = allTickers.map { ticker ->
            val curr = currentMap[ticker]
            val prev = previousMap[ticker]

            when {
                curr != null && prev == null -> {
                    logger.d("NEW: ${curr.stockName}")
                    HoldingWithComparison(
                        stockTicker = ticker,
                        stockName = curr.stockName,
                        previousWeight = 0f,
                        currentWeight = curr.weight,
                        change = curr.weight,
                        currentAmount = curr.amount,
                        status = HoldingStatus.NEW
                    )
                }
                curr == null && prev != null -> {
                    logger.d("REMOVED: ${prev.stockName}")
                    HoldingWithComparison(
                        stockTicker = ticker,
                        stockName = prev.stockName,
                        previousWeight = prev.weight,
                        currentWeight = 0f,
                        change = -prev.weight,
                        currentAmount = 0f,
                        status = HoldingStatus.REMOVED
                    )
                }
                curr != null && prev != null -> {
                    val prevWeight = prev.weight
                    val currWeight = curr.weight
                    val change = currWeight - prevWeight

                    val status = when {
                        change > WEIGHT_CHANGE_THRESHOLD -> HoldingStatus.INCREASE
                        change < -WEIGHT_CHANGE_THRESHOLD -> HoldingStatus.DECREASE
                        else -> HoldingStatus.MAINTAIN
                    }

                    if (status != HoldingStatus.MAINTAIN) {
                        logger.d("${status.name}: ${curr.stockName} ($prevWeight% → $currWeight%)")
                    }

                    HoldingWithComparison(
                        stockTicker = ticker,
                        stockName = curr.stockName,
                        previousWeight = prevWeight,
                        currentWeight = currWeight,
                        change = change,
                        currentAmount = curr.amount,
                        status = status
                    )
                }
                else -> {
                    logger.e("Unexpected case for ticker: $ticker")
                    HoldingWithComparison(
                        stockTicker = ticker,
                        stockName = curr?.stockName ?: prev?.stockName ?: ticker,
                        previousWeight = 0f,
                        currentWeight = 0f,
                        change = 0f,
                        currentAmount = 0f,
                        status = HoldingStatus.MAINTAIN
                    )
                }
            }
        }
            .sortedWith(
                compareByDescending<HoldingWithComparison> { it.status == HoldingStatus.NEW }
                    .thenByDescending { it.status == HoldingStatus.REMOVED }
                    .thenByDescending { it.currentWeight }
            )

        logger.d("Comparison result: ${items.size} items")
        val statusCount = items.groupBy { it.status }.mapValues { it.value.size }
        logger.d("Status counts: $statusCount")

        ComparisonResult(
            etfTicker = etfTicker,
            currentDate = currentDate,
            previousDate = previousDate,
            items = items,
            collectionStartDate = dates.last(),  // 가장 오래된 날짜
            collectionEndDate = dates.first()    // 가장 최신 날짜
        )
    }

    override suspend fun getComparisonInRange(
        etfTicker: String,
        startDate: String,
        endDate: String
    ): ComparisonResult? = withContext(Dispatchers.IO) {
        ensureDateFormatNormalized()
        val allDates = localDataSource.getDates(etfTicker)
        val datesInRange = allDates.filter { it in startDate..endDate }

        logger.d("getComparisonInRange for $etfTicker: ${datesInRange.size} dates in range ($startDate ~ $endDate)")

        if (datesInRange.isEmpty()) {
            logger.d("No dates found in range for $etfTicker")
            return@withContext null
        }

        if (datesInRange.size == 1) {
            logger.d("Only one date in range: ${datesInRange[0]}")
            val current = localDataSource.getHoldings(etfTicker, datesInRange[0])
            return@withContext ComparisonResult(
                etfTicker = etfTicker,
                currentDate = datesInRange[0],
                previousDate = "N/A",
                items = current.map { holding ->
                    HoldingWithComparison(
                        stockTicker = holding.stockTicker,
                        stockName = holding.stockName,
                        previousWeight = 0f,
                        currentWeight = holding.weight,
                        change = holding.weight,
                        currentAmount = holding.amount,
                        status = HoldingStatus.NEW
                    )
                },
                collectionStartDate = datesInRange[0],
                collectionEndDate = datesInRange[0]
            )
        }

        // 범위 내에서 가장 최신과 가장 오래된 날짜 비교
        val currentDate = datesInRange.first()  // 최신 날짜 (내림차순이므로 첫 번째)
        val previousDate = datesInRange.last()  // 가장 오래된 날짜

        logger.d("Comparing in range: $previousDate vs $currentDate")

        val current = localDataSource.getHoldings(etfTicker, currentDate)
        val previous = localDataSource.getHoldings(etfTicker, previousDate)

        logger.d("Current holdings: ${current.size}, Previous holdings: ${previous.size}")

        val currentMap = current.associateBy { it.stockTicker }
        val previousMap = previous.associateBy { it.stockTicker }

        val allTickers = (currentMap.keys + previousMap.keys).toSet()

        val items = allTickers.map { ticker ->
            val curr = currentMap[ticker]
            val prev = previousMap[ticker]

            when {
                curr != null && prev == null -> {
                    HoldingWithComparison(
                        stockTicker = ticker,
                        stockName = curr.stockName,
                        previousWeight = 0f,
                        currentWeight = curr.weight,
                        change = curr.weight,
                        currentAmount = curr.amount,
                        status = HoldingStatus.NEW
                    )
                }
                curr == null && prev != null -> {
                    HoldingWithComparison(
                        stockTicker = ticker,
                        stockName = prev.stockName,
                        previousWeight = prev.weight,
                        currentWeight = 0f,
                        change = -prev.weight,
                        currentAmount = 0f,
                        status = HoldingStatus.REMOVED
                    )
                }
                curr != null && prev != null -> {
                    val prevWeight = prev.weight
                    val currWeight = curr.weight
                    val change = currWeight - prevWeight

                    val status = when {
                        change > WEIGHT_CHANGE_THRESHOLD -> HoldingStatus.INCREASE
                        change < -WEIGHT_CHANGE_THRESHOLD -> HoldingStatus.DECREASE
                        else -> HoldingStatus.MAINTAIN
                    }

                    HoldingWithComparison(
                        stockTicker = ticker,
                        stockName = curr.stockName,
                        previousWeight = prevWeight,
                        currentWeight = currWeight,
                        change = change,
                        currentAmount = curr.amount,
                        status = status
                    )
                }
                else -> {
                    HoldingWithComparison(
                        stockTicker = ticker,
                        stockName = curr?.stockName ?: prev?.stockName ?: ticker,
                        previousWeight = 0f,
                        currentWeight = 0f,
                        change = 0f,
                        currentAmount = 0f,
                        status = HoldingStatus.MAINTAIN
                    )
                }
            }
        }
            .sortedWith(
                compareByDescending<HoldingWithComparison> { it.status == HoldingStatus.NEW }
                    .thenByDescending { it.status == HoldingStatus.REMOVED }
                    .thenByDescending { it.currentWeight }
            )

        logger.d("Comparison result in range: ${items.size} items")

        ComparisonResult(
            etfTicker = etfTicker,
            currentDate = currentDate,
            previousDate = previousDate,
            items = items,
            collectionStartDate = datesInRange.last(),
            collectionEndDate = datesInRange.first()
        )
    }

    override suspend fun getAvailableDates(limit: Int): List<String> = withContext(Dispatchers.IO) {
        localDataSource.getAllAvailableDates(limit)
    }

    // ========== Data Collection ==========

    override fun initializeData(days: Int) = flow {
        try {
            logger.d("initializeData: START")
            emit(DataProgress.Loading("초기화 시작", 0))

            initializeDefaultSettings()

            emit(DataProgress.Loading("영업일 계산 중", 5))
            val businessDays = getKrxBusinessDaysUseCase(days).getOrElse { emptyList() }
            logger.d("Business days found: ${businessDays.size}")

            if (businessDays.isEmpty()) {
                logger.e("No business days found")
                emit(DataProgress.Error("영업일을 찾을 수 없습니다"))
                return@flow
            }

            emit(DataProgress.Loading("영업일 ${businessDays.size}일 발견", 10))

            val themes = getThemes()
            val exclusions = getExclusions()
            val includeKeywords = themes + listOf("액티브")

            if (includeKeywords.isEmpty()) {
                logger.e("ERROR: Include keywords is empty!")
                emit(DataProgress.Error("포함 키워드가 없습니다."))
                return@flow
            }

            var totalEtfs = 0
            var totalHoldings = 0
            val startTime = System.currentTimeMillis()
            val totalDays = businessDays.size

            businessDays.forEachIndexed { index, date ->
                val baseProgress = 10
                val progressRange = 80
                val progress = baseProgress + ((index + 1) * progressRange / totalDays)

                emit(DataProgress.Loading("데이터 수집 중 (${index + 1}/$totalDays) $date", progress))
                logger.d("Processing date: $date (${index + 1}/$totalDays) - Progress: $progress%")

                val dateYYYYMMDD = date.replace("-", "")

                val validEtfs = getKrxEtfListUseCase(
                    date = dateYYYYMMDD,
                    includeKeywords = includeKeywords,
                    excludeKeywords = exclusions
                ).getOrElse {
                    logger.e("kotlin_krx ETF list failed for $dateYYYYMMDD")
                    emptyList()
                }

                logger.d("Filtered ETFs for $date: ${validEtfs.size}")

                if (validEtfs.isEmpty()) {
                    logger.w("No valid ETFs for $date")
                }

                val dateStartTime = System.currentTimeMillis()
                val results = processEtfsInParallel(validEtfs, dateYYYYMMDD, date)

                results.forEach { result ->
                    if (result.holdings.isNotEmpty()) {
                        totalEtfs++
                        totalHoldings += result.holdings.size
                    }
                }

                try {
                    val dailyStats = calculateDailyStatisticsImproved(date, results)
                    dailyEtfStatisticsDao.insert(dailyStats)
                    logger.d("Daily statistics saved for $date: " +
                            "newStocks=${dailyStats.newStockCount}, " +
                            "removed=${dailyStats.removedStockCount}, " +
                            "increased=${dailyStats.increasedStockCount}, " +
                            "decreased=${dailyStats.decreasedStockCount}")
                } catch (e: Exception) {
                    logger.e("Failed to save daily statistics for $date", e)
                }

                val dateElapsed = System.currentTimeMillis() - dateStartTime
                logger.d("Date $date processed in ${dateElapsed}ms (${results.size} ETFs)")

                delay(50)
            }

            val totalElapsed = (System.currentTimeMillis() - startTime) / 1000
            logger.d("initializeData: COMPLETE in ${totalElapsed}s - ETFs: $totalEtfs, Holdings: $totalHoldings")

            if (totalEtfs == 0) {
                emit(DataProgress.Error("수집된 ETF가 없습니다."))
            } else {
                emit(DataProgress.Success("초기화 완료! ETF ${totalEtfs}개 수집 (${totalElapsed}초 소요)"))
            }
        } catch (e: Exception) {
            logger.e("initializeData: ERROR", e)
            emit(DataProgress.Error("초기화 실패: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    override fun updateData() = flow {
        try {
            logger.d("updateData: START")
            emit(DataProgress.Loading("업데이트 시작", 0))

            val lastDate = etfDao.getLatestDate()
            if (lastDate == null) {
                emit(DataProgress.Error("데이터가 없습니다."))
                return@flow
            }

            emit(DataProgress.Loading("마지막 수집일: $lastDate", 10))

            val businessDays = getKrxBusinessDaysUseCase(10).getOrElse { emptyList() }
            val newDays = businessDays.filter { it > lastDate }

            if (newDays.isEmpty()) {
                emit(DataProgress.Success("이미 최신 데이터입니다"))
                return@flow
            }

            emit(DataProgress.Loading("새로운 영업일 ${newDays.size}일 발견", 20))

            val themes = getThemes()
            val exclusions = getExclusions()
            val includeKeywords = themes + listOf("액티브")

            if (includeKeywords.isEmpty()) {
                emit(DataProgress.Error("포함 키워드가 없습니다."))
                return@flow
            }

            var totalEtfs = 0
            val startTime = System.currentTimeMillis()
            val totalDays = newDays.size

            newDays.forEachIndexed { index, date ->
                val baseProgress = 20
                val progressRange = 70
                val progress = baseProgress + ((index + 1) * progressRange / totalDays)

                emit(DataProgress.Loading("데이터 수집 중 (${index + 1}/$totalDays) $date", progress))
                logger.d("Processing date: $date (${index + 1}/$totalDays) - Progress: $progress%")

                val dateYYYYMMDD = date.replace("-", "")

                val validEtfs = getKrxEtfListUseCase(
                    date = dateYYYYMMDD,
                    includeKeywords = includeKeywords,
                    excludeKeywords = exclusions
                ).getOrElse {
                    logger.e("kotlin_krx ETF list failed for $dateYYYYMMDD")
                    emptyList()
                }

                val results = processEtfsInParallel(validEtfs, dateYYYYMMDD, date)
                totalEtfs += results.count { it.holdings.isNotEmpty() }

                try {
                    val dailyStats = calculateDailyStatisticsImproved(date, results)
                    dailyEtfStatisticsDao.insert(dailyStats)
                    logger.d("Daily statistics saved for $date: " +
                            "newStocks=${dailyStats.newStockCount}, " +
                            "removed=${dailyStats.removedStockCount}, " +
                            "increased=${dailyStats.increasedStockCount}, " +
                            "decreased=${dailyStats.decreasedStockCount}")
                } catch (e: Exception) {
                    logger.e("Failed to save daily statistics for $date", e)
                }

                delay(50)
            }

            val totalElapsed = (System.currentTimeMillis() - startTime) / 1000
            emit(DataProgress.Success("업데이트 완료! ETF ${totalEtfs}개 수집 (${totalElapsed}초 소요)"))
        } catch (e: Exception) {
            logger.e("updateData: ERROR", e)
            emit(DataProgress.Error("업데이트 실패: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun resetDatabase() = withContext(Dispatchers.IO) {
        logger.d("resetDatabase: Clearing all ETF data")
        etfDao.clearAllHoldings()
        etfDao.clearAllEtfs()
        dailyEtfStatisticsDao.deleteAll()
        logger.d("resetDatabase: Complete")
    }

    override suspend fun trimDataToPeriod(days: Int): Int = withContext(Dispatchers.IO) {
        val startDate = LocalDate.now().minusDays(days.toLong())
        val startDateStr = startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        logger.d("trimDataToPeriod: Keeping data from $startDateStr (last $days days)")

        val allDates = etfDao.getAllDistinctDates(500)
        val datesToDelete = allDates.filter { it < startDateStr }
        val deletedCount = datesToDelete.size

        if (deletedCount > 0) {
            etfDao.deleteHoldingsBeforeDate(startDateStr)
            dailyEtfStatisticsDao.deleteBeforeDate(startDateStr)
            logger.d("trimDataToPeriod: Deleted data before $startDateStr ($deletedCount dates)")
        } else {
            logger.d("trimDataToPeriod: No data to delete")
        }

        deletedCount
    }

    // ========== Settings ==========

    override suspend fun getDefaultDays(): Int = withContext(Dispatchers.IO) {
        val saved = etfDao.getSetting("default_days")
        saved?.toIntOrNull() ?: 25
    }

    override suspend fun setDefaultDays(days: Int) = withContext(Dispatchers.IO) {
        etfDao.saveSetting(Setting("default_days", days.toString()))
        logger.d("Default days set to: $days")
    }

    override suspend fun getThemes(): List<String> = withContext(Dispatchers.IO) {
        logger.d("getThemes() called")
        val saved = etfDao.getSetting("themes")
        logger.d("DB returned: '$saved'")

        val themes = if (saved != null && saved.isNotBlank()) {
            saved.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            logger.w("No themes in DB, using defaults")
            defaultThemes()
        }

        logger.d("Returning (${themes.size}): ${themes.take(5)}...")
        themes
    }

    override suspend fun addTheme(theme: String) = withContext(Dispatchers.IO) {
        val current = getThemes().toMutableList()
        if (!current.contains(theme)) {
            current.add(theme)
            etfDao.saveSetting(Setting("themes", current.joinToString(",")))
            _keywordVersion.value = System.currentTimeMillis()
        }
    }

    override suspend fun removeTheme(theme: String) = withContext(Dispatchers.IO) {
        val current = getThemes().toMutableList()
        current.remove(theme)
        etfDao.saveSetting(Setting("themes", current.joinToString(",")))
        _keywordVersion.value = System.currentTimeMillis()
    }

    override suspend fun getExclusions(): List<String> = withContext(Dispatchers.IO) {
        logger.d("getExclusions() called")
        val saved = etfDao.getSetting("exclusions")
        logger.d("DB returned: '$saved'")

        val exclusions = if (saved != null && saved.isNotBlank()) {
            saved.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            logger.w("No exclusions in DB, using defaults")
            defaultExclusions()
        }

        logger.d("Returning (${exclusions.size}): ${exclusions.take(5)}...")
        exclusions
    }

    override suspend fun addExclusion(keyword: String) = withContext(Dispatchers.IO) {
        val current = getExclusions().toMutableList()
        if (!current.contains(keyword)) {
            current.add(keyword)
            etfDao.saveSetting(Setting("exclusions", current.joinToString(",")))
            _keywordVersion.value = System.currentTimeMillis()
        }
    }

    override suspend fun removeExclusion(keyword: String) = withContext(Dispatchers.IO) {
        val current = getExclusions().toMutableList()
        current.remove(keyword)
        etfDao.saveSetting(Setting("exclusions", current.joinToString(",")))
        _keywordVersion.value = System.currentTimeMillis()
    }

    // ========== Keyword Filtering ==========

    private suspend fun applyKeywordFilter(etfs: List<Etf>): List<Etf> {
        val themes = getThemes()
        val exclusions = getExclusions()
        val includeKeywords = themes + listOf("액티브")
        return etfs.filter { etf ->
            val includeMatch = includeKeywords.any { kw ->
                etf.name.contains(kw, ignoreCase = true)
            }
            val excludeMatch = exclusions.any { kw ->
                etf.name.contains(kw, ignoreCase = true)
            }
            includeMatch && !excludeMatch
        }
    }

    override suspend fun collectForNewKeyword(keyword: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            logger.d("collectForNewKeyword: START for '$keyword'")

            // 1. 최신 영업일 조회 (최근 5일 내 가장 최근 영업일)
            val businessDays = getKrxBusinessDaysUseCase(5).getOrElse { emptyList() }
            if (businessDays.isEmpty()) {
                return@withContext Result.failure(Exception("영업일을 찾을 수 없습니다"))
            }
            val latestDate = businessDays.first()
            val dateYYYYMMDD = latestDate.replace("-", "")

            // 2. 해당 키워드만으로 ETF 목록 조회
            val exclusions = getExclusions()
            val validEtfs = getKrxEtfListUseCase(
                date = dateYYYYMMDD,
                includeKeywords = listOf(keyword),
                excludeKeywords = exclusions
            ).getOrElse {
                logger.e("kotlin_krx ETF list failed for keyword '$keyword'")
                emptyList()
            }

            if (validEtfs.isEmpty()) {
                logger.d("collectForNewKeyword: No ETFs found for '$keyword'")
                return@withContext Result.success(0)
            }

            // 3. 기존 DB에 없는 ETF만 필터
            val existingTickers = etfDao.getAllEtfsSuspend().map { it.ticker }.toSet()
            val newEtfs = validEtfs.filter { it.ticker !in existingTickers }

            if (newEtfs.isEmpty()) {
                logger.d("collectForNewKeyword: All ${validEtfs.size} ETFs already in DB")
                return@withContext Result.success(0)
            }

            logger.d("collectForNewKeyword: ${newEtfs.size} new ETFs to collect")

            // 4. 새 ETF + holdings 수집 (rate limiting 포함)
            val results = processEtfsInParallel(newEtfs, dateYYYYMMDD, latestDate)
            val addedCount = results.count { it.holdings.isNotEmpty() }

            logger.d("collectForNewKeyword: COMPLETE - $addedCount ETFs added for '$keyword'")
            Result.success(addedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("collectForNewKeyword: ERROR", e)
            Result.failure(e)
        }
    }

    // ========== Private Helpers ==========

    private suspend fun processEtfsInParallel(
        etfs: List<com.etfmonitor.core.database.entities.Etf>,
        dateYYYYMMDD: String,
        formattedDate: String
    ): List<EtfProcessResult> = coroutineScope {
        val allStocksToSync = mutableListOf<Pair<String, String>>()

        val results = etfs.chunked(PARALLEL_LIMIT).flatMap { chunk ->
            val chunkResults = chunk.map { etf ->
                async {
                    try {
                        val existingHoldings = etfDao.getHoldings(etf.ticker, formattedDate)
                        if (existingHoldings.isNotEmpty()) {
                            logger.d("Skipping ${etf.ticker} - data already exists")
                            return@async EtfProcessResult(etf.ticker, existingHoldings)
                        }

                        etfDao.insertEtf(etf)
                        val holdings = getKrxEtfHoldingsUseCase(etf.ticker, dateYYYYMMDD)
                            .getOrElse { emptyList() }

                        if (holdings.isNotEmpty()) {
                            etfDao.insertHoldings(holdings)
                            logger.d("✓ ${etf.ticker}: ${holdings.size} holdings")
                        }

                        EtfProcessResult(etf.ticker, holdings)
                    } catch (e: Exception) {
                        logger.e("Error processing ${etf.ticker}: ${e.message}")
                        EtfProcessResult(etf.ticker, emptyList())
                    }
                }
            }.awaitAll()
            delay(PER_CHUNK_DELAY_MS)  // KRX Akamai rate limit 방지
            chunkResults
        }

        results.flatMap { it.holdings }
            .distinctBy { it.stockTicker }
            .forEach { holding ->
                allStocksToSync.add(holding.stockTicker to holding.stockName)
            }

        if (allStocksToSync.isNotEmpty()) {
            try {
                stockDao.syncFromHoldings(allStocksToSync)
                logger.d("Synced ${allStocksToSync.size} stocks to master")
            } catch (e: Exception) {
                logger.e("Failed to sync stocks: ${e.message}")
            }
        }

        results
    }

    private suspend fun calculateDailyStatisticsImproved(
        date: String,
        currentResults: List<EtfProcessResult>
    ): DailyEtfStatistics = withContext(Dispatchers.IO) {
        logger.d("calculateDailyStatisticsImproved for $date")

        val currentHoldingsMap = mutableMapOf<String, Holding>()
        var totalHoldingAmount = 0L
        var cashDepositAmount = 0L
        var validEtfCount = 0

        currentResults.forEach { result ->
            if (result.holdings.isNotEmpty()) {
                validEtfCount++
                result.holdings.forEach { holding ->
                    val key = "${holding.etfTicker}:${holding.stockTicker}"
                    currentHoldingsMap[key] = holding

                    if (isCashDeposit(holding.stockName)) {
                        cashDepositAmount += holding.amount.toLong()
                    } else {
                        totalHoldingAmount += holding.amount.toLong()
                    }
                }
            }
        }

        val previousDate = getPreviousBusinessDay(date)
        logger.d("Previous business day: $previousDate")

        val previousHoldingsMap = if (previousDate != null) {
            try {
                val previousHoldings = etfDao.getHoldingsByDateRange(previousDate, previousDate)
                previousHoldings.associateBy { "${it.etfTicker}:${it.stockTicker}" }
            } catch (e: Exception) {
                logger.w("Failed to get previous holdings: ${e.message}")
                emptyMap()
            }
        } else {
            emptyMap()
        }

        var newStockCount = 0
        var newStockAmount = 0L
        var removedStockCount = 0
        var removedStockAmount = 0L
        var increasedStockCount = 0
        var increasedStockAmount = 0L
        var decreasedStockCount = 0
        var decreasedStockAmount = 0L

        val processedStocks = mutableSetOf<String>()

        currentHoldingsMap.forEach { (key, currentHolding) ->
            val stockTicker = currentHolding.stockTicker

            if (isCashDeposit(currentHolding.stockName)) {
                return@forEach
            }

            val previousHolding = previousHoldingsMap[key]

            when {
                previousHolding == null -> {
                    if (!processedStocks.contains("NEW:$stockTicker")) {
                        newStockCount++
                        processedStocks.add("NEW:$stockTicker")
                    }
                    newStockAmount += currentHolding.amount.toLong()
                }
                currentHolding.weightBps > previousHolding.weightBps + WEIGHT_CHANGE_THRESHOLD_BPS -> {
                    if (!processedStocks.contains("INC:$stockTicker")) {
                        increasedStockCount++
                        processedStocks.add("INC:$stockTicker")
                    }
                    increasedStockAmount += currentHolding.amount.toLong()
                }
                currentHolding.weightBps < previousHolding.weightBps - WEIGHT_CHANGE_THRESHOLD_BPS -> {
                    if (!processedStocks.contains("DEC:$stockTicker")) {
                        decreasedStockCount++
                        processedStocks.add("DEC:$stockTicker")
                    }
                    decreasedStockAmount += currentHolding.amount.toLong()
                }
            }
        }

        previousHoldingsMap.forEach { (key, previousHolding) ->
            val stockTicker = previousHolding.stockTicker

            if (isCashDeposit(previousHolding.stockName)) {
                return@forEach
            }

            if (!currentHoldingsMap.containsKey(key)) {
                if (!processedStocks.contains("REM:$stockTicker")) {
                    removedStockCount++
                    processedStocks.add("REM:$stockTicker")
                }
                removedStockAmount += previousHolding.amount.toLong()
            }
        }

        val previousStats = if (previousDate != null) {
            try {
                dailyEtfStatisticsDao.getByDate(previousDate)
            } catch (e: Exception) {
                null
            }
        } else null

        val cashDepositChange = if (previousStats != null) {
            cashDepositAmount - previousStats.cashDepositAmount
        } else 0L

        val cashDepositChangeRate = if (previousStats != null && previousStats.cashDepositAmount > 0) {
            (cashDepositChange.toDouble() / previousStats.cashDepositAmount) * 100
        } else 0.0

        logger.d("Statistics calculated: " +
                "new=$newStockCount (${formatAmount(newStockAmount)}), " +
                "removed=$removedStockCount (${formatAmount(removedStockAmount)}), " +
                "increased=$increasedStockCount (${formatAmount(increasedStockAmount)}), " +
                "decreased=$decreasedStockCount (${formatAmount(decreasedStockAmount)}), " +
                "cash=${formatAmount(cashDepositAmount)} (${String.format("%.2f", cashDepositChangeRate)}%)")

        DailyEtfStatistics(
            date = date,
            newStockCount = newStockCount,
            newStockAmount = newStockAmount,
            removedStockCount = removedStockCount,
            removedStockAmount = removedStockAmount,
            increasedStockCount = increasedStockCount,
            increasedStockAmount = increasedStockAmount,
            decreasedStockCount = decreasedStockCount,
            decreasedStockAmount = decreasedStockAmount,
            cashDepositAmount = cashDepositAmount,
            cashDepositChange = cashDepositChange,
            cashDepositChangeRate = cashDepositChangeRate,
            totalEtfCount = validEtfCount,
            totalHoldingAmount = totalHoldingAmount
        )
    }

    private fun isCashDeposit(stockName: String): Boolean {
        val lowerName = stockName.lowercase()
        return lowerName.contains("원화예금") ||
                lowerName.contains("현금") ||
                lowerName.contains("cash") ||
                lowerName.contains("예금") ||
                lowerName.contains("krw")
    }

    private fun formatAmount(amount: Long): String = AmountFormatter.formatLong(amount)

    private suspend fun getPreviousBusinessDay(date: String): String? = withContext(Dispatchers.IO) {
        try {
            val allDates = dailyEtfStatisticsDao.getAllDates()
            val sortedDates = allDates.sorted()
            val index = sortedDates.indexOf(date)

            if (index > 0) {
                return@withContext sortedDates[index - 1]
            }

            val holdingDates = etfDao.getLatestTwoDates()
            val currentIndex = holdingDates.indexOf(date)
            if (currentIndex >= 0 && currentIndex < holdingDates.size - 1) {
                return@withContext holdingDates[currentIndex + 1]
            }

            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w("Failed to get previous business day: ${e.message}")
            null
        }
    }

    private suspend fun initializeDefaultSettings() = withContext(Dispatchers.IO) {
        logger.d("initializeDefaultSettings() called")

        val existingThemes = etfDao.getSetting("themes")
        if (existingThemes == null) {
            val themes = defaultThemes().joinToString(",")
            etfDao.saveSetting(Setting("themes", themes))
            logger.d("Saved default themes (${themes.length} chars)")
        } else {
            logger.d("Themes already exist (${existingThemes.length} chars)")
        }

        val existingExclusions = etfDao.getSetting("exclusions")
        if (existingExclusions == null) {
            val exclusions = defaultExclusions().joinToString(",")
            etfDao.saveSetting(Setting("exclusions", exclusions))
            logger.d("Saved default exclusions (${exclusions.length} chars)")
        } else {
            logger.d("Exclusions already exist (${existingExclusions.length} chars)")
        }
    }

    private fun defaultThemes() = listOf(
        "반도체", "바이오", "혁신기술", "배당성장", "신재생",
        "2차전지", "AI", "조선", "테크", "수출", "로봇",
        "컬처", "밸류업", "친환경", "소비", "이노베이션",
        "메모리", "비메모리", "인공지능", "전기차", "배터리",
        "ESG", "탄소중립", "메타버스", "블록체인", "헬스케어",
        "IT", "성장"
    )

    private fun defaultExclusions() = listOf(
        "인버스", "레버리지", "곱버스", "2X", "3X",
        "글로벌", "차이나", "채권", "달러", "China",
        "아시아", "미국", "일본", "금리", "금융채", "회사채"
    )

    private data class EtfProcessResult(
        val ticker: String,
        val holdings: List<Holding>
    )
}
