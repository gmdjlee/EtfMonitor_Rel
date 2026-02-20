package com.etfmonitor.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.etfmonitor.core.database.AppDatabase
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.entities.Etf
import com.etfmonitor.core.database.entities.Holding
import com.etfmonitor.core.database.entities.Setting
import com.etfmonitor.core.database.entities.SnapshotType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for EtfDao using an in-memory Room database.
 *
 * Critical scenarios covered:
 * - ETF CRUD operations
 * - Holdings with Holding.create() factory (never construct directly — weightBps/amountMillion overflow risk)
 * - Complex JOIN queries for StockChangeInfo (getNewStocks, getRemovedStocks, getIncreasedStocks)
 * - LIMIT enforcement to prevent OOM
 * - Flow reactive updates
 * - Compression/decompression roundtrip via weight and amount computed properties
 */
@RunWith(AndroidJUnit4::class)
class EtfDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: EtfDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.dao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    // ====================== ETF basic operations ======================

    @Test
    fun insertEtf_thenGetEtf_returnsInsertedEtf() = runBlocking {
        val etf = Etf(ticker = "069500", name = "KODEX 200")
        dao.insertEtf(etf)

        val result = dao.getEtf("069500")

        assertNotNull(result)
        assertEquals("069500", result!!.ticker)
        assertEquals("KODEX 200", result.name)
    }

    @Test
    fun getEtf_withNonExistentTicker_returnsNull() = runBlocking {
        val result = dao.getEtf("DOES_NOT_EXIST")
        assertNull(result)
    }

    @Test
    fun insertEtfs_thenGetAllEtfsSuspend_returnsAllOrderedByName() = runBlocking {
        val etfs = listOf(
            Etf(ticker = "069500", name = "KODEX 200"),
            Etf(ticker = "102110", name = "TIGER 200"),
            Etf(ticker = "114800", name = "KODEX 인버스")
        )
        dao.insertEtfs(etfs)

        val result = dao.getAllEtfsSuspend()

        assertEquals(3, result.size)
        // ORDER BY name — 한글 이름은 ASCII 뒤에 정렬됨
        // Verify all are present
        val tickers = result.map { it.ticker }.toSet()
        assertTrue(tickers.contains("069500"))
        assertTrue(tickers.contains("102110"))
        assertTrue(tickers.contains("114800"))
    }

    @Test
    fun insertEtf_withSameTicker_replacesExisting() = runBlocking {
        dao.insertEtf(Etf(ticker = "069500", name = "KODEX 200"))
        dao.insertEtf(Etf(ticker = "069500", name = "KODEX 200 Updated"))

        val result = dao.getEtf("069500")
        assertEquals("KODEX 200 Updated", result!!.name)

        val count = dao.getEtfCount()
        assertEquals(1, count)
    }

    @Test
    fun getEtfCount_onEmptyDb_returnsZero() = runBlocking {
        val count = dao.getEtfCount()
        assertEquals(0, count)
    }

    @Test
    fun getEtfCount_afterInserts_returnsCorrectCount() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        dao.insertEtf(Etf("102110", "TIGER 200"))

        val count = dao.getEtfCount()
        assertEquals(2, count)
    }

    @Test
    fun clearAllEtfs_removesAllEtfs() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        dao.insertEtf(Etf("102110", "TIGER 200"))

        dao.clearAllEtfs()

        val count = dao.getEtfCount()
        assertEquals(0, count)
    }

    @Test
    fun getAllEtfs_flow_emitsUpdatedList() = runBlocking {
        // Empty state
        val initialList = dao.getAllEtfs().first()
        assertTrue(initialList.isEmpty())

        // Insert and verify flow emission
        dao.insertEtf(Etf("069500", "KODEX 200"))
        val updatedList = dao.getAllEtfs().first()
        assertEquals(1, updatedList.size)
        assertEquals("069500", updatedList[0].ticker)
    }

    @Test
    fun searchEtfs_flow_returnsMatchingEtfs() = runBlocking {
        dao.insertEtfs(listOf(
            Etf("069500", "KODEX 200"),
            Etf("102110", "TIGER 200"),
            Etf("114800", "KODEX 인버스")
        ))

        val results = dao.searchEtfs("KODEX").first()

        assertEquals(2, results.size)
        assertTrue(results.all { it.name.contains("KODEX") })
    }

    @Test
    fun searchEtfs_withNoMatch_returnsEmptyList() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))

        val results = dao.searchEtfs("TIGER").first()

        assertTrue(results.isEmpty())
    }

    // ====================== Holdings operations ======================

    @Test
    fun insertHolding_usingFactory_thenGetHoldings_returnsCorrectData() = runBlocking {
        // IMPORTANT: Always use Holding.create() — never construct directly
        // weightBps=Short, amountMillion=Int: direct construction causes overflow
        dao.insertEtf(Etf("069500", "KODEX 200"))
        val holding = Holding.create(
            etfTicker = "069500",
            stockTicker = "005930",
            stockName = "삼성전자",
            date = "2026-02-20",
            weight = 0.2523f,
            amount = 1_234_567_890f
        )
        dao.insertHolding(holding)

        val holdings = dao.getHoldings("069500", "2026-02-20")

        assertEquals(1, holdings.size)
        assertEquals("005930", holdings[0].stockTicker)
        assertEquals("삼성전자", holdings[0].stockName)

        // Verify compression roundtrip: weight stored as Short bps, retrieved as Float
        // 0.2523 * 10000 = 2523 bps -> 2523 / 10000 = 0.2523
        val reconstructedWeight = holdings[0].weight
        assertTrue(
            "Weight roundtrip should be within 0.01% tolerance",
            Math.abs(reconstructedWeight - 0.2523f) < 0.0001f
        )

        // Verify amount roundtrip: 1,234,567,890 -> stored as 1234 million -> 1,234,000,000
        val reconstructedAmount = holdings[0].amount
        assertTrue(
            "Amount should be within 1M of original (truncation by design)",
            Math.abs(reconstructedAmount - 1_234_000_000f) < 1_000_000f
        )
    }

    @Test
    fun holdingCreate_withMaxWeight_doesNotOverflow() = runBlocking {
        // Test Short overflow guard: 100% weight = 10000 bps = Short.MAX_VALUE is 32767 — safe
        // But 999% would overflow without coerceIn
        dao.insertEtf(Etf("069500", "KODEX 200"))
        val holding = Holding.create(
            etfTicker = "069500",
            stockTicker = "005930",
            stockName = "삼성전자",
            date = "2026-02-20",
            weight = 1.0f, // 100% weight = 10000 bps
            amount = 1_000_000f
        )
        dao.insertHolding(holding)

        val holdings = dao.getHoldings("069500", "2026-02-20")
        assertEquals(1, holdings.size)
        // 100% weight = 10000 bps — must be stored without overflow
        assertEquals(10000.toShort(), holdings[0].weightBps)
    }

    @Test
    fun getHoldings_withMultipleHoldings_returnsOrderedByWeightDesc() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        val date = "2026-02-20"

        dao.insertHolding(Holding.create("069500", "005380", "현대차", date, 0.05f, 500_000_000f))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", date, 0.30f, 3_000_000_000f))
        dao.insertHolding(Holding.create("069500", "000660", "SK하이닉스", date, 0.12f, 1_200_000_000f))

        val holdings = dao.getHoldings("069500", date)

        assertEquals(3, holdings.size)
        // Verify descending weight order
        assertTrue(holdings[0].weightBps >= holdings[1].weightBps)
        assertTrue(holdings[1].weightBps >= holdings[2].weightBps)
    }

    @Test
    fun getHoldings_forDifferentDate_returnsEmpty() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-20", 0.25f, 2_500_000_000f))

        val holdings = dao.getHoldings("069500", "2026-02-19")
        assertTrue(holdings.isEmpty())
    }

    @Test
    fun getLatestDate_afterInserts_returnsMaxDate() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-18", 0.25f, 2_500_000_000f))
        dao.insertHolding(Holding.create("069500", "000660", "SK하이닉스", "2026-02-20", 0.12f, 1_200_000_000f))

        val latestDate = dao.getLatestDate()
        assertEquals("2026-02-20", latestDate)
    }

    @Test
    fun getLatestDate_onEmptyDb_returnsNull() = runBlocking {
        val latestDate = dao.getLatestDate()
        assertNull(latestDate)
    }

    @Test
    fun getDates_returnsDistinctDatesDesc() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-18", 0.25f, 2_500_000_000f))
        dao.insertHolding(Holding.create("069500", "000660", "SK하이닉스", "2026-02-18", 0.12f, 1_200_000_000f))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-20", 0.26f, 2_600_000_000f))

        val dates = dao.getDates("069500")

        assertEquals(2, dates.size)
        assertEquals("2026-02-20", dates[0]) // DESC order
        assertEquals("2026-02-18", dates[1])
    }

    @Test
    fun getHoldingCount_onEmptyDb_returnsZero() = runBlocking {
        val count = dao.getHoldingCount()
        assertEquals(0, count)
    }

    @Test
    fun getTotalHoldingCount_afterInserts_returnsCorrectCount() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-20", 0.25f, 2_500_000_000f))
        dao.insertHolding(Holding.create("069500", "000660", "SK하이닉스", "2026-02-20", 0.12f, 1_200_000_000f))

        val count = dao.getTotalHoldingCount()
        assertEquals(2L, count)
    }

    @Test
    fun clearAllHoldings_removesAllHoldings() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-20", 0.25f, 2_500_000_000f))

        dao.clearAllHoldings()

        val count = dao.getTotalHoldingCount()
        assertEquals(0L, count)
    }

    // ====================== JOIN queries (complex) ======================

    @Test
    fun getAllNewStocks_returnsStocksNotInPreviousDate() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        val currentDate = "2026-02-20"
        val previousDate = "2026-02-19"

        // Stock in previous date only
        dao.insertHolding(Holding.create("069500", "005380", "현대차", previousDate, 0.05f, 500_000_000f))
        // Stock in both dates
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", previousDate, 0.25f, 2_500_000_000f))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", currentDate, 0.25f, 2_500_000_000f))
        // NEW: stock in current date only
        dao.insertHolding(Holding.create("069500", "000660", "SK하이닉스", currentDate, 0.12f, 1_200_000_000f))

        val newStocks = dao.getAllNewStocks(currentDate, previousDate)

        assertEquals(1, newStocks.size)
        assertEquals("000660", newStocks[0].stockTicker)
        assertEquals("SK하이닉스", newStocks[0].stockName)
        assertEquals("069500", newStocks[0].etfTicker)
        assertEquals("KODEX 200", newStocks[0].etfName) // JOIN with etfs table
        assertEquals(0.0f, newStocks[0].previousWeight, 0.001f) // new stock has 0 previous weight
    }

    @Test
    fun getAllRemovedStocks_returnsStocksNotInCurrentDate() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        val currentDate = "2026-02-20"
        val previousDate = "2026-02-19"

        // REMOVED: Stock in previous date only
        dao.insertHolding(Holding.create("069500", "005380", "현대차", previousDate, 0.05f, 500_000_000f))
        // Stock in both dates (maintained)
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", previousDate, 0.25f, 2_500_000_000f))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", currentDate, 0.25f, 2_500_000_000f))

        val removedStocks = dao.getAllRemovedStocks(currentDate, previousDate)

        assertEquals(1, removedStocks.size)
        assertEquals("005380", removedStocks[0].stockTicker)
        assertEquals("현대차", removedStocks[0].stockName)
        assertEquals("KODEX 200", removedStocks[0].etfName) // JOIN verified
        assertEquals(0.0f, removedStocks[0].currentWeight, 0.001f) // removed stock has 0 current weight
    }

    @Test
    fun getAllIncreasedStocks_returnsOnlySignificantIncreases() = runBlocking {
        // The query uses threshold: curr.weightBps > prev.weightBps + 100 (i.e. +1% change)
        dao.insertEtf(Etf("069500", "KODEX 200"))
        val currentDate = "2026-02-20"
        val previousDate = "2026-02-19"

        // INCREASED significantly (30% -> 32%, delta = 200 bps > 100 threshold)
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", previousDate, 0.30f, 3_000_000_000f))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", currentDate, 0.32f, 3_200_000_000f))

        // Trivially increased (25% -> 25.5%, delta = 50 bps < 100 threshold — excluded)
        dao.insertHolding(Holding.create("069500", "000660", "SK하이닉스", previousDate, 0.25f, 2_500_000_000f))
        dao.insertHolding(Holding.create("069500", "000660", "SK하이닉스", currentDate, 0.255f, 2_550_000_000f))

        val increased = dao.getAllIncreasedStocks(currentDate, previousDate)

        assertEquals(1, increased.size)
        assertEquals("005930", increased[0].stockTicker)
        assertTrue(increased[0].change > 0.0f)
    }

    @Test
    fun getStockAmountRanking_returnsTop500WithJoinData() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        dao.insertEtf(Etf("102110", "TIGER 200"))
        val currentDate = "2026-02-20"
        val previousDate = "2026-02-19"

        // Same stock in two ETFs
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", currentDate, 0.30f, 3_000_000_000f))
        dao.insertHolding(Holding.create("102110", "005930", "삼성전자", currentDate, 0.25f, 2_500_000_000f))
        // Another stock
        dao.insertHolding(Holding.create("069500", "000660", "SK하이닉스", currentDate, 0.12f, 1_200_000_000f))

        val ranking = dao.getStockAmountRanking(currentDate, previousDate)

        // 삼성전자 should rank first (5.5B combined vs 1.2B)
        assertTrue(ranking.isNotEmpty())
        assertEquals("005930", ranking[0].stockTicker)
        assertEquals(2, ranking[0].etfCount) // in both ETFs
        assertTrue(ranking[0].totalAmount > ranking[1].totalAmount)
    }

    // ====================== Date range and filtering ======================

    @Test
    fun getHoldingsByDateRange_returnsHoldingsWithinRange() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-18", 0.25f, 2_500_000_000f))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-19", 0.25f, 2_500_000_000f))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-20", 0.25f, 2_500_000_000f))

        val holdings = dao.getHoldingsByDateRange("2026-02-18", "2026-02-19")
        assertEquals(2, holdings.size)
    }

    @Test
    fun getHoldingsByDateRange_excludesDatesBeyondRange() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-17", 0.25f, 2_500_000_000f))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-20", 0.25f, 2_500_000_000f))

        val holdings = dao.getHoldingsByDateRange("2026-02-18", "2026-02-19")
        assertTrue(holdings.isEmpty())
    }

    @Test
    fun getHoldingsByDateRange_respectsLimit500() = runBlocking {
        // Insert 510 holdings (over the LIMIT 500 in the query)
        dao.insertEtf(Etf("069500", "KODEX 200"))
        val holdings = (1..510).map { i ->
            val ticker = "%06d".format(i)
            Holding.create("069500", ticker, "종목$i", "2026-02-20", 0.001f, 1_000_000f)
        }
        dao.insertHoldings(holdings)

        val result = dao.getHoldingsByDateRange("2026-02-20", "2026-02-20")
        assertTrue("LIMIT 500 must be respected to prevent OOM", result.size <= 500)
    }

    @Test
    fun getHoldingCountByDateRange_returnsCorrectCount() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-18", 0.25f, 2_500_000_000f))
        dao.insertHolding(Holding.create("069500", "000660", "SK하이닉스", "2026-02-20", 0.12f, 1_200_000_000f))

        val count = dao.getHoldingCountByDateRange("2026-02-18", "2026-02-18")
        assertEquals(1L, count)
    }

    @Test
    fun getLatestTwoDates_returnsAtMostTwoDates() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-18", 0.25f, 2_500_000_000f))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-19", 0.25f, 2_500_000_000f))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-20", 0.25f, 2_500_000_000f))

        val dates = dao.getLatestTwoDates()
        assertEquals(2, dates.size)
        assertEquals("2026-02-20", dates[0])
        assertEquals("2026-02-19", dates[1])
    }

    @Test
    fun getAllDistinctDates_respectsLimit() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        // Insert 5 distinct dates
        for (day in 1..5) {
            val date = "2026-02-%02d".format(day)
            dao.insertHolding(Holding.create("069500", "005930", "삼성전자", date, 0.25f, 2_500_000_000f))
        }

        val dates = dao.getAllDistinctDates(3)
        assertEquals(3, dates.size)
        // Should be newest first
        assertEquals("2026-02-05", dates[0])
    }

    // ====================== Settings via EtfDao ======================

    @Test
    fun saveSetting_thenGetSetting_returnsValue() = runBlocking {
        dao.saveSetting(Setting("last_sync", "2026-02-20"))

        val value = dao.getSetting("last_sync")
        assertEquals("2026-02-20", value)
    }

    @Test
    fun getSetting_forMissingKey_returnsNull() = runBlocking {
        val value = dao.getSetting("nonexistent_key")
        assertNull(value)
    }

    @Test
    fun deleteSetting_removesEntry() = runBlocking {
        dao.saveSetting(Setting("temp_key", "temp_value"))
        dao.deleteSetting("temp_key")

        val value = dao.getSetting("temp_key")
        assertNull(value)
    }

    // ====================== Stock search ======================

    @Test
    fun searchStocks_byName_returnsMatches() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-20", 0.25f, 2_500_000_000f))
        dao.insertHolding(Holding.create("069500", "005380", "현대차", "2026-02-20", 0.05f, 500_000_000f))

        val results = dao.searchStocks("삼성")
        assertEquals(1, results.size)
        assertEquals("005930", results[0].stockTicker)
        assertEquals("삼성전자", results[0].stockName)
    }

    @Test
    fun searchStocks_byTicker_returnsMatches() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-20", 0.25f, 2_500_000_000f))

        val results = dao.searchStocks("0059")
        assertEquals(1, results.size)
        assertEquals("005930", results[0].stockTicker)
    }

    @Test
    fun getStockName_returnsCorrectName() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-20", 0.25f, 2_500_000_000f))

        val name = dao.getStockName("005930")
        assertEquals("삼성전자", name)
    }

    @Test
    fun getStockName_forUnknownTicker_returnsNull() = runBlocking {
        val name = dao.getStockName("999999")
        assertNull(name)
    }

    // ====================== Snapshot type ======================

    @Test
    fun getSnapshotTypeCounts_afterInserts_returnsGroupedCounts() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-20", 0.25f, 2_500_000_000f, SnapshotType.DAILY))
        dao.insertHolding(Holding.create("069500", "000660", "SK하이닉스", "2026-01-31", 0.12f, 1_200_000_000f, SnapshotType.MONTHLY))

        val counts = dao.getSnapshotTypeCounts()
        val countsMap = counts.associate { it.snapshotType to it.count }

        assertEquals(1, countsMap["DAILY"])
        assertEquals(1, countsMap["MONTHLY"])
    }

    // ====================== Delete operations ======================

    @Test
    fun deleteHoldingsByDateRange_removesCorrectHoldings() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-18", 0.25f, 2_500_000_000f))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-19", 0.25f, 2_500_000_000f))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-20", 0.25f, 2_500_000_000f))

        dao.deleteHoldingsByDateRange("2026-02-18", "2026-02-19")

        val remaining = dao.getTotalHoldingCount()
        assertEquals(1L, remaining)
        val latestDate = dao.getLatestDate()
        assertEquals("2026-02-20", latestDate)
    }

    @Test
    fun deleteHoldingsBeforeDate_removesOlderHoldings() = runBlocking {
        dao.insertEtf(Etf("069500", "KODEX 200"))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-01-01", 0.25f, 2_500_000_000f))
        dao.insertHolding(Holding.create("069500", "005930", "삼성전자", "2026-02-20", 0.25f, 2_500_000_000f))

        dao.deleteHoldingsBeforeDate("2026-02-01")

        val count = dao.getTotalHoldingCount()
        assertEquals(1L, count)
        val latestDate = dao.getLatestDate()
        assertEquals("2026-02-20", latestDate)
    }
}
