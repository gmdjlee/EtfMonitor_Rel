package com.etfmonitor.repository

import android.util.Log
import com.etfmonitor.database.EtfDao
import com.etfmonitor.database.entities.*
import com.etfmonitor.python.PyKrxClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DataRepository(
    private val dao: EtfDao,
    private val pyKrx: PyKrxClient
) {

    companion object {
        private const val TAG = "DataRepository"
        private const val PARALLEL_LIMIT = 5
    }

    // ========== ETF List ==========

    fun getAllEtfs(): Flow<List<Etf>> = dao.getAllEtfs()

    fun searchEtfs(query: String): Flow<List<Etf>> = dao.searchEtfs(query)

    suspend fun hasData(): Boolean {
        val count = dao.getEtfCount()
        Log.d(TAG, "hasData: count = $count")
        return count > 0
    }

    suspend fun getLatestDate(): String? {
        val date = dao.getLatestDate()
        Log.d(TAG, "getLatestDate: $date")
        return date
    }

    // ========== ETF Info ==========

    // ✅ ETF 정보 가져오기 메서드 추가
    suspend fun getEtf(ticker: String): Etf? {
        return dao.getEtf(ticker)
    }

    // ========== ETF Detail ==========

    suspend fun getComparison(etfTicker: String): ComparisonResult? {
        val dates = dao.getDates(etfTicker)

        Log.d(TAG, "getComparison for $etfTicker: ${dates.size} dates available")

        if (dates.isEmpty()) {
            Log.d(TAG, "No dates found for $etfTicker")
            return null
        }

        if (dates.size == 1) {
            Log.d(TAG, "Only one date available: ${dates[0]}")
            val current = dao.getHoldings(etfTicker, dates[0])
            return ComparisonResult(
                etfTicker = etfTicker,
                currentDate = dates[0],
                previousDate = "N/A",
                items = current.map {
                    HoldingWithComparison(
                        stockTicker = it.stockTicker,
                        stockName = it.stockName,
                        previousWeight = 0f,
                        currentWeight = it.weight,
                        change = it.weight,
                        currentAmount = it.amount,
                        status = HoldingStatus.NEW
                    )
                }
            )
        }

        val currentDate = dates[0]
        val previousDate = dates[1]

        Log.d(TAG, "Comparing: $previousDate vs $currentDate")

        val current = dao.getHoldings(etfTicker, currentDate)
        val previous = dao.getHoldings(etfTicker, previousDate)

        Log.d(TAG, "Current holdings: ${current.size}, Previous holdings: ${previous.size}")

        val currentMap = current.associateBy { it.stockTicker }
        val previousMap = previous.associateBy { it.stockTicker }

        val allTickers = (currentMap.keys + previousMap.keys).toSet()

        val items = allTickers.map { ticker ->
            val curr = currentMap[ticker]
            val prev = previousMap[ticker]

            when {
                curr != null && prev == null -> {
                    Log.d(TAG, "NEW: ${curr.stockName}")
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
                    Log.d(TAG, "REMOVED: ${prev.stockName}")
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
                        change > 0.01f -> HoldingStatus.INCREASE
                        change < -0.01f -> HoldingStatus.DECREASE
                        else -> HoldingStatus.MAINTAIN
                    }

                    if (status != HoldingStatus.MAINTAIN) {
                        Log.d(TAG, "${status.name}: ${curr.stockName} ($prevWeight% → $currWeight%)")
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
                    Log.e(TAG, "Unexpected case for ticker: $ticker")
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

        Log.d(TAG, "Comparison result: ${items.size} items")
        val statusCount = items.groupBy { it.status }.mapValues { it.value.size }
        Log.d(TAG, "Status counts: $statusCount")

        return ComparisonResult(
            etfTicker = etfTicker,
            currentDate = currentDate,
            previousDate = previousDate,
            items = items
        )
    }

    // ========== Stats ==========

    suspend fun getOverlapStocks(limit: Int = 50): List<OverlapStockDisplay> {
        val latestDate = dao.getLatestDate() ?: return emptyList()
        return dao.getOverlapStocks(latestDate, limit).map {
            OverlapStockDisplay(
                stockName = it.stockName,
                etfCount = it.etfCount,
                etfList = it.etfList.split(",")
            )
        }
    }

    suspend fun getAmountRanking(limit: Int = 50): List<AmountRank> {
        val latestDate = dao.getLatestDate() ?: return emptyList()
        return dao.getAmountRanking(latestDate, limit)
    }

    suspend fun getStockAmountRanking(): List<StockAmountRanking> {
        val latestDate = dao.getLatestDate() ?: return emptyList()
        return dao.getStockAmountRanking(latestDate)
    }

    suspend fun getAllNewStocks(): List<StockChangeInfo> {
        val dates = dao.getLatestTwoDates()
        if (dates.size < 2) return emptyList()
        return dao.getAllNewStocks(dates[0], dates[1])
    }

    suspend fun getAllRemovedStocks(): List<StockChangeInfo> {
        val dates = dao.getLatestTwoDates()
        if (dates.size < 2) return emptyList()
        return dao.getAllRemovedStocks(dates[0], dates[1])
    }

    suspend fun getAllIncreasedStocks(): List<StockChangeInfo> {
        val dates = dao.getLatestTwoDates()
        if (dates.size < 2) return emptyList()
        return dao.getAllIncreasedStocks(dates[0], dates[1])
    }

    suspend fun getStatisticsDates(): Pair<String, String>? {
        val dates = dao.getLatestTwoDates()
        return if (dates.size >= 2) Pair(dates[1], dates[0]) else null
    }

    // ========== Stock Trend ==========

    suspend fun getStockTrend(etfTicker: String, stockTicker: String): StockTrend? {
        val timeSeries = dao.getHoldingTimeSeries(etfTicker, stockTicker)

        if (timeSeries.isEmpty()) return null

        val firstDate = timeSeries.first().date
        val stockName = dao.getHoldings(etfTicker, firstDate)
            .find { it.stockTicker == stockTicker }
            ?.stockName ?: stockTicker

        return StockTrend(
            etfTicker = etfTicker,
            stockTicker = stockTicker,
            stockName = stockName,
            timeSeries = timeSeries
        )
    }

    // ========== Data Collection (최적화) ==========

    fun initializeData(days: Int = 25) = flow {
        try {
            Log.d(TAG, "initializeData: START")
            emit(DataProgress.Loading("초기화 시작", 0))

            initializeDefaultSettings()

            emit(DataProgress.Loading("영업일 계산 중", 5))
            val businessDays = pyKrx.getBusinessDays(days)
            Log.d(TAG, "Business days found: ${businessDays.size}")

            if (businessDays.isEmpty()) {
                Log.e(TAG, "No business days found")
                emit(DataProgress.Error("영업일을 찾을 수 없습니다"))
                return@flow
            }

            emit(DataProgress.Loading("영업일 ${businessDays.size}일 발견", 10))

            val themes = getThemes()
            val exclusions = getExclusions()
            val includeKeywords = themes + listOf("액티브")

            if (includeKeywords.isEmpty()) {
                Log.e(TAG, "ERROR: Include keywords is empty!")
                emit(DataProgress.Error("포함 키워드가 없습니다."))
                return@flow
            }

            var totalEtfs = 0
            var totalHoldings = 0
            val startTime = System.currentTimeMillis()

            // ✅ 전체 진행률 계산을 위한 변수
            val totalDays = businessDays.size

            businessDays.forEachIndexed { index, date ->
                // ✅ 전체 기준 진행률 계산 (10% ~ 90%)
                val baseProgress = 10
                val progressRange = 80
                val progress = baseProgress + ((index + 1) * progressRange / totalDays)

                emit(DataProgress.Loading("데이터 수집 중 (${index + 1}/$totalDays) $date", progress))
                Log.d(TAG, "Processing date: $date (${index + 1}/$totalDays) - Progress: $progress%")

                val dateYYYYMMDD = date.replace("-", "")

                val validEtfs = pyKrx.getFilteredEtfList(
                    date = dateYYYYMMDD,
                    includeKeywords = includeKeywords,
                    excludeKeywords = exclusions
                )

                Log.d(TAG, "Filtered ETFs for $date: ${validEtfs.size}")

                if (validEtfs.isEmpty()) {
                    Log.w(TAG, "No valid ETFs for $date")
                }

                val dateStartTime = System.currentTimeMillis()
                val results = processEtfsInParallel(validEtfs, dateYYYYMMDD, date)

                results.forEach { result ->
                    if (result.holdings.isNotEmpty()) {
                        totalEtfs++
                        totalHoldings += result.holdings.size
                    }
                }

                val dateElapsed = System.currentTimeMillis() - dateStartTime
                Log.d(TAG, "Date $date processed in ${dateElapsed}ms (${results.size} ETFs)")

                delay(50)
            }

            val totalElapsed = (System.currentTimeMillis() - startTime) / 1000
            Log.d(TAG, "initializeData: COMPLETE in ${totalElapsed}s - ETFs: $totalEtfs, Holdings: $totalHoldings")

            if (totalEtfs == 0) {
                emit(DataProgress.Error("수집된 ETF가 없습니다."))
            } else {
                emit(DataProgress.Success("초기화 완료! ETF ${totalEtfs}개 수집 (${totalElapsed}초 소요)"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "initializeData: ERROR", e)
            emit(DataProgress.Error("초기화 실패: ${e.message}"))
        }
    }

    fun updateData() = flow {
        try {
            Log.d(TAG, "updateData: START")
            emit(DataProgress.Loading("업데이트 시작", 0))

            val lastDate = dao.getLatestDate()
            if (lastDate == null) {
                emit(DataProgress.Error("데이터가 없습니다."))
                return@flow
            }

            emit(DataProgress.Loading("마지막 수집일: $lastDate", 10))

            val businessDays = pyKrx.getBusinessDays(10)
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

            // ✅ 전체 진행률 계산
            val totalDays = newDays.size

            newDays.forEachIndexed { index, date ->
                // ✅ 전체 기준 진행률 (20% ~ 90%)
                val baseProgress = 20
                val progressRange = 70
                val progress = baseProgress + ((index + 1) * progressRange / totalDays)

                emit(DataProgress.Loading("데이터 수집 중 (${index + 1}/$totalDays) $date", progress))
                Log.d(TAG, "Processing date: $date (${index + 1}/$totalDays) - Progress: $progress%")

                val dateYYYYMMDD = date.replace("-", "")

                val validEtfs = pyKrx.getFilteredEtfList(
                    date = dateYYYYMMDD,
                    includeKeywords = includeKeywords,
                    excludeKeywords = exclusions
                )

                val results = processEtfsInParallel(validEtfs, dateYYYYMMDD, date)
                totalEtfs += results.count { it.holdings.isNotEmpty() }

                delay(50)
            }

            val totalElapsed = (System.currentTimeMillis() - startTime) / 1000
            emit(DataProgress.Success("업데이트 완료! ETF ${totalEtfs}개 수집 (${totalElapsed}초 소요)"))
        } catch (e: Exception) {
            Log.e(TAG, "updateData: ERROR", e)
            emit(DataProgress.Error("업데이트 실패: ${e.message}"))
        }
    }

    /**
     * ETF들을 병렬로 처리
     */
    private suspend fun processEtfsInParallel(
        etfs: List<Etf>,
        dateYYYYMMDD: String,
        formattedDate: String
    ): List<EtfProcessResult> = coroutineScope {
        etfs.chunked(PARALLEL_LIMIT).flatMap { chunk ->
            chunk.map { etf ->
                async {
                    try {
                        val existingHoldings = dao.getHoldings(etf.ticker, formattedDate)
                        if (existingHoldings.isNotEmpty()) {
                            Log.d(TAG, "Skipping ${etf.ticker} - data already exists")
                            return@async EtfProcessResult(etf.ticker, existingHoldings)
                        }

                        dao.insertEtf(etf)

                        val holdings = pyKrx.getHoldings(etf.ticker, dateYYYYMMDD)

                        if (holdings.isNotEmpty()) {
                            dao.insertHoldings(holdings)
                            Log.d(TAG, "✓ ${etf.ticker}: ${holdings.size} holdings")
                        } else {
                            Log.d(TAG, "✗ ${etf.ticker}: no holdings")
                        }

                        EtfProcessResult(etf.ticker, holdings)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing ${etf.ticker}: ${e.message}")
                        EtfProcessResult(etf.ticker, emptyList())
                    }
                }
            }.awaitAll()
        }
    }

    // ========== Settings ==========

    suspend fun getDefaultDays(): Int {
        val saved = dao.getSetting("default_days")
        return saved?.toIntOrNull() ?: 25  // 기본값: 25일
    }

    suspend fun setDefaultDays(days: Int) {
        dao.saveSetting(Setting("default_days", days.toString()))
        Log.d(TAG, "Default days set to: $days")
    }

    // getThemes와 getExclusions에도 로깅 추가
    suspend fun getThemes(): List<String> {
        Log.d(TAG, "  getThemes() called")
        val saved = dao.getSetting("themes")
        Log.d(TAG, "    DB returned: '$saved'")

        val themes = if (saved != null && saved.isNotBlank()) {
            saved.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            Log.w(TAG, "    ⚠️ No themes in DB, using defaults")
            defaultThemes()
        }

        Log.d(TAG, "    Returning (${themes.size}): ${themes.take(5)}...")
        return themes
    }

    suspend fun addTheme(theme: String) {
        val current = getThemes().toMutableList()
        if (!current.contains(theme)) {
            current.add(theme)
            dao.saveSetting(Setting("themes", current.joinToString(",")))
        }
    }

    suspend fun removeTheme(theme: String) {
        val current = getThemes().toMutableList()
        current.remove(theme)
        dao.saveSetting(Setting("themes", current.joinToString(",")))
    }

    suspend fun getExclusions(): List<String> {
        Log.d(TAG, "  getExclusions() called")
        val saved = dao.getSetting("exclusions")
        Log.d(TAG, "    DB returned: '$saved'")

        val exclusions = if (saved != null && saved.isNotBlank()) {
            saved.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            Log.w(TAG, "    ⚠️ No exclusions in DB, using defaults")
            defaultExclusions()
        }

        Log.d(TAG, "    Returning (${exclusions.size}): ${exclusions.take(5)}...")
        return exclusions
    }

    suspend fun addExclusion(keyword: String) {
        val current = getExclusions().toMutableList()
        if (!current.contains(keyword)) {
            current.add(keyword)
            dao.saveSetting(Setting("exclusions", current.joinToString(",")))
        }
    }

    suspend fun removeExclusion(keyword: String) {
        val current = getExclusions().toMutableList()
        current.remove(keyword)
        dao.saveSetting(Setting("exclusions", current.joinToString(",")))
    }

    suspend fun resetDatabase() {
        dao.clearAllEtfs()
        dao.clearAllHoldings()
    }

    private suspend fun initializeDefaultSettings() {
        Log.d(TAG, "  initializeDefaultSettings() called")

        val existingThemes = dao.getSetting("themes")
        if (existingThemes == null) {
            val themes = defaultThemes().joinToString(",")
            dao.saveSetting(Setting("themes", themes))
            Log.d(TAG, "    ✓ Saved default themes (${themes.length} chars)")
            Log.d(TAG, "      First 100 chars: ${themes.take(100)}")
        } else {
            Log.d(TAG, "    ✓ Themes already exist (${existingThemes.length} chars)")
        }

        val existingExclusions = dao.getSetting("exclusions")
        if (existingExclusions == null) {
            val exclusions = defaultExclusions().joinToString(",")
            dao.saveSetting(Setting("exclusions", exclusions))
            Log.d(TAG, "    ✓ Saved default exclusions (${exclusions.length} chars)")
            Log.d(TAG, "      Content: $exclusions")
        } else {
            Log.d(TAG, "    ✓ Exclusions already exist (${existingExclusions.length} chars)")
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

    // ✅ Data class 추가
    private data class EtfProcessResult(
        val ticker: String,
        val holdings: List<Holding>
    )
}

// Data classes

data class ComparisonResult(
    val etfTicker: String,
    val currentDate: String,
    val previousDate: String,
    val items: List<HoldingWithComparison>
)

data class OverlapStockDisplay(
    val stockName: String,
    val etfCount: Int,
    val etfList: List<String>
)

data class ThemeStat(
    val theme: String,
    val etfCount: Int,
    val stockCount: Int,
    val etfList: List<String>
)

data class StockTrend(
    val etfTicker: String,
    val stockTicker: String,
    val stockName: String,
    val timeSeries: List<HoldingTimeSeries>
)

sealed class DataProgress {
    data class Loading(val message: String, val progress: Int) : DataProgress()
    data class Success(val message: String) : DataProgress()
    data class Error(val message: String) : DataProgress()
}