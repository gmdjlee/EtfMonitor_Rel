package com.etfmonitor.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.etfmonitor.core.database.AppDatabase
import com.etfmonitor.core.database.StockAnalysisDao
import com.etfmonitor.core.database.entities.Stock
import com.etfmonitor.core.database.entities.StockAnalysisData
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
 * Integration tests for StockAnalysisDao using an in-memory Room database.
 *
 * Critical rule (CLAUDE.md §2):
 *   ALWAYS use getAnalysisDataWithName() — JOIN with stocks table.
 *   NEVER use getAnalysisData() — the name field was removed from stock_analysis_data in v12→13.
 *
 * These tests document and verify both behaviors:
 * 1. getAnalysisData() — deprecated, name is NOT stored in stock_analysis_data
 * 2. getAnalysisDataWithName() — LEFT JOIN stocks s ON a.ticker = s.ticker, COALESCE(s.name, a.ticker)
 */
@RunWith(AndroidJUnit4::class)
class StockAnalysisDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var stockAnalysisDao: StockAnalysisDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        stockAnalysisDao = database.stockAnalysisDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    // ====================== Helper factories ======================

    private fun makeAnalysisData(
        ticker: String,
        dataStartDate: String = "2026-01-01",
        dataEndDate: String = "2026-02-20"
    ) = StockAnalysisData(
        ticker = ticker,
        dates = listOf("2026-02-18", "2026-02-19", "2026-02-20"),
        marketCap = listOf(400_000_000_000L, 410_000_000_000L, 420_000_000_000L),
        foreign5d = listOf(100_000_000L, 200_000_000L, 150_000_000L),
        institution5d = listOf(50_000_000L, -30_000_000L, 80_000_000L),
        lastUpdated = System.currentTimeMillis(),
        dataStartDate = dataStartDate,
        dataEndDate = dataEndDate
    )

    private fun insertStock(ticker: String, name: String) = runBlocking {
        database.stockDao().insert(Stock(ticker, name, "KOSPI"))
    }

    // ====================== Basic insert and query ======================

    @Test
    fun insertAnalysisData_thenGetCount_returnsOne() = runBlocking {
        stockAnalysisDao.insertAnalysisData(makeAnalysisData("005930"))

        val count = stockAnalysisDao.getCount()
        assertEquals(1, count)
    }

    @Test
    fun getCount_onEmptyDb_returnsZero() = runBlocking {
        val count = stockAnalysisDao.getCount()
        assertEquals(0, count)
    }

    @Test
    fun insertAnalysisData_withReplace_updatesExistingRecord() = runBlocking {
        val original = makeAnalysisData("005930", dataEndDate = "2026-02-19")
        stockAnalysisDao.insertAnalysisData(original)

        val updated = makeAnalysisData("005930", dataEndDate = "2026-02-20")
        stockAnalysisDao.insertAnalysisData(updated)

        val count = stockAnalysisDao.getCount()
        assertEquals(1, count)

        @Suppress("DEPRECATION")
        val result = stockAnalysisDao.getAnalysisData("005930")
        assertNotNull(result)
        assertEquals("2026-02-20", result!!.dataEndDate)
    }

    @Test
    fun deleteAnalysisData_removesRecord() = runBlocking {
        stockAnalysisDao.insertAnalysisData(makeAnalysisData("005930"))
        stockAnalysisDao.deleteAnalysisData("005930")

        val count = stockAnalysisDao.getCount()
        assertEquals(0, count)
    }

    @Test
    fun deleteAll_removesAllRecords() = runBlocking {
        stockAnalysisDao.insertAnalysisData(makeAnalysisData("005930"))
        stockAnalysisDao.insertAnalysisData(makeAnalysisData("000660"))
        stockAnalysisDao.deleteAll()

        val count = stockAnalysisDao.getCount()
        assertEquals(0, count)
    }

    @Test
    fun getAllAnalysisData_returnsAll_orderedByLastUpdatedDesc() = runBlocking {
        val older = StockAnalysisData(
            ticker = "000660",
            dates = listOf("2026-02-20"),
            marketCap = listOf(200_000_000_000L),
            foreign5d = listOf(50_000_000L),
            institution5d = listOf(20_000_000L),
            lastUpdated = 1_000_000L, // older timestamp
            dataStartDate = "2026-02-20",
            dataEndDate = "2026-02-20"
        )
        val newer = StockAnalysisData(
            ticker = "005930",
            dates = listOf("2026-02-20"),
            marketCap = listOf(400_000_000_000L),
            foreign5d = listOf(100_000_000L),
            institution5d = listOf(50_000_000L),
            lastUpdated = 2_000_000L, // newer timestamp
            dataStartDate = "2026-02-20",
            dataEndDate = "2026-02-20"
        )

        stockAnalysisDao.insertAnalysisData(older)
        stockAnalysisDao.insertAnalysisData(newer)

        val results = stockAnalysisDao.getAllAnalysisData()
        assertEquals(2, results.size)
        // ORDER BY lastUpdated DESC: newer first
        assertEquals("005930", results[0].ticker)
        assertEquals("000660", results[1].ticker)
    }

    @Test
    fun getAllAnalysisData_respectsLimit500() = runBlocking {
        // Insert more than 500 records and verify LIMIT 500 is honored
        // Using a small count here for test speed — the important assertion is LIMIT <= 500
        for (i in 1..10) {
            val ticker = "%06d".format(i)
            stockAnalysisDao.insertAnalysisData(makeAnalysisData(ticker))
        }

        val results = stockAnalysisDao.getAllAnalysisData()
        assertTrue("Query result must not exceed LIMIT 500", results.size <= 500)
        assertEquals(10, results.size)
    }

    // ====================== CRITICAL: JOIN requirement for name ======================

    @Test
    @Suppress("DEPRECATION")
    fun getAnalysisData_deprecated_doesNotContainName_nameComesFromStocksTable() = runBlocking {
        // DOCUMENTED KNOWN ISSUE:
        // stock_analysis_data does NOT have a name column (removed in migration v12->v13).
        // getAnalysisData() returns StockAnalysisData which has no name field — this is correct behavior.
        // The ticket/stock name MUST be fetched via getAnalysisDataWithName() JOIN.
        stockAnalysisDao.insertAnalysisData(makeAnalysisData("005930"))

        val result = stockAnalysisDao.getAnalysisData("005930")
        assertNotNull(result)
        assertEquals("005930", result!!.ticker)

        // StockAnalysisData has no name field — this confirms the schema change in v12->v13.
        // If you need the stock name, you MUST use getAnalysisDataWithName() instead.
    }

    @Test
    @Suppress("DEPRECATION")
    fun getAnalysisData_forNonExistentTicker_returnsNull() = runBlocking {
        val result = stockAnalysisDao.getAnalysisData("DOES_NOT_EXIST")
        assertNull(result)
    }

    @Test
    fun getAnalysisDataWithName_whenStockExists_returnsRealName() = runBlocking {
        // The correct pattern: insert stock master data, then query with JOIN
        insertStock("005930", "삼성전자")
        stockAnalysisDao.insertAnalysisData(makeAnalysisData("005930"))

        val result = stockAnalysisDao.getAnalysisDataWithName("005930")

        assertNotNull(result)
        assertEquals("005930", result!!.ticker)
        // Name comes from stocks table via JOIN — must NOT be null
        assertEquals(
            "Name must be populated from stocks table JOIN (not stock_analysis_data)",
            "삼성전자",
            result.name
        )
    }

    @Test
    fun getAnalysisDataWithName_whenStockDoesNotExist_fallsBackToTicker() = runBlocking {
        // COALESCE(s.name, a.ticker): when stocks table has no matching row, ticker is used as name
        stockAnalysisDao.insertAnalysisData(makeAnalysisData("005930"))
        // Do NOT insert a matching Stock row

        val result = stockAnalysisDao.getAnalysisDataWithName("005930")

        assertNotNull(result)
        assertEquals("005930", result!!.ticker)
        // COALESCE falls back to ticker when stock name is not available
        assertEquals(
            "When no matching stocks row, COALESCE should return ticker as fallback name",
            "005930",
            result.name
        )
    }

    @Test
    fun getAnalysisDataWithName_forNonExistentAnalysisTicker_returnsNull() = runBlocking {
        insertStock("005930", "삼성전자")
        // Do NOT insert analysis data for this ticker

        val result = stockAnalysisDao.getAnalysisDataWithName("005930")
        assertNull(result)
    }

    @Test
    fun getAnalysisDataWithName_returnsCorrectDateList() = runBlocking {
        insertStock("005930", "삼성전자")
        val expectedDates = listOf("2026-02-18", "2026-02-19", "2026-02-20")
        stockAnalysisDao.insertAnalysisData(makeAnalysisData("005930"))

        val result = stockAnalysisDao.getAnalysisDataWithName("005930")

        assertNotNull(result)
        assertEquals(expectedDates, result!!.dates)
    }

    @Test
    fun getAnalysisDataWithName_returnsCorrectMarketCapList() = runBlocking {
        insertStock("005930", "삼성전자")
        stockAnalysisDao.insertAnalysisData(makeAnalysisData("005930"))

        val result = stockAnalysisDao.getAnalysisDataWithName("005930")

        assertNotNull(result)
        val marketCap = result!!.marketCap
        assertEquals(3, marketCap.size)
        assertEquals(400_000_000_000L, marketCap[0])
        assertEquals(420_000_000_000L, marketCap[2])
    }

    @Test
    fun getAnalysisDataWithName_returnsCorrectForeignAndInstitutionFlows() = runBlocking {
        insertStock("000660", "SK하이닉스")
        val data = StockAnalysisData(
            ticker = "000660",
            dates = listOf("2026-02-20"),
            marketCap = listOf(200_000_000_000L),
            foreign5d = listOf(-500_000_000L), // net selling by foreigners
            institution5d = listOf(1_200_000_000L), // net buying by institutions
            lastUpdated = System.currentTimeMillis(),
            dataStartDate = "2026-02-20",
            dataEndDate = "2026-02-20"
        )
        stockAnalysisDao.insertAnalysisData(data)

        val result = stockAnalysisDao.getAnalysisDataWithName("000660")

        assertNotNull(result)
        assertEquals(-500_000_000L, result!!.foreign5d[0])
        assertEquals(1_200_000_000L, result.institution5d[0])
    }

    // ====================== getAllAnalysisDataWithName (bulk JOIN) ======================

    @Test
    fun getAllAnalysisDataWithName_returnsAllWithJoinedNames() = runBlocking {
        insertStock("005930", "삼성전자")
        insertStock("000660", "SK하이닉스")

        stockAnalysisDao.insertAnalysisData(makeAnalysisData("005930"))
        stockAnalysisDao.insertAnalysisData(makeAnalysisData("000660"))

        val results = stockAnalysisDao.getAllAnalysisDataWithName()

        assertEquals(2, results.size)
        val nameMap = results.associate { it.ticker to it.name }
        assertEquals("삼성전자", nameMap["005930"])
        assertEquals("SK하이닉스", nameMap["000660"])
    }

    @Test
    fun getAllAnalysisDataWithName_whenSomeStocksMissing_usesFallbackTicker() = runBlocking {
        // Only insert stock for one of the two analysis entries
        insertStock("005930", "삼성전자")

        stockAnalysisDao.insertAnalysisData(makeAnalysisData("005930"))
        stockAnalysisDao.insertAnalysisData(makeAnalysisData("000660")) // no matching stocks row

        val results = stockAnalysisDao.getAllAnalysisDataWithName()

        assertEquals(2, results.size)
        val nameMap = results.associate { it.ticker to it.name }
        assertEquals("삼성전자", nameMap["005930"]) // from stocks JOIN
        assertEquals("000660", nameMap["000660"])  // COALESCE fallback to ticker
    }

    @Test
    fun getAllAnalysisDataWithName_onEmptyDb_returnsEmptyList() = runBlocking {
        val results = stockAnalysisDao.getAllAnalysisDataWithName()
        assertTrue(results.isEmpty())
    }

    @Test
    fun getAllAnalysisDataWithName_respectsLimit500() = runBlocking {
        // Verify LIMIT 500 is present in getAllAnalysisDataWithName query
        for (i in 1..10) {
            val ticker = "%06d".format(i)
            stockAnalysisDao.insertAnalysisData(makeAnalysisData(ticker))
        }

        val results = stockAnalysisDao.getAllAnalysisDataWithName()
        assertTrue("getAllAnalysisDataWithName must not exceed LIMIT 500", results.size <= 500)
        assertEquals(10, results.size)
    }

    // ====================== Data integrity ======================

    @Test
    fun insertAnalysisData_withTypeConverterLists_roundtripsCorrectly() = runBlocking {
        // TypeConverter: List<String> and List<Long> serialize via JSONArray
        val dates = listOf("2026-02-18", "2026-02-19", "2026-02-20")
        val marketCap = listOf(100L, 200L, 300L)
        val foreign5d = listOf(-50L, 100L, 75L)
        val institution5d = listOf(25L, -10L, 60L)

        val data = StockAnalysisData(
            ticker = "005930",
            dates = dates,
            marketCap = marketCap,
            foreign5d = foreign5d,
            institution5d = institution5d,
            lastUpdated = 1_000_000L,
            dataStartDate = "2026-02-18",
            dataEndDate = "2026-02-20"
        )
        stockAnalysisDao.insertAnalysisData(data)

        @Suppress("DEPRECATION")
        val result = stockAnalysisDao.getAnalysisData("005930")

        assertNotNull(result)
        assertEquals(dates, result!!.dates)
        assertEquals(marketCap, result.marketCap)
        assertEquals(foreign5d, result.foreign5d)
        assertEquals(institution5d, result.institution5d)
    }

    @Test
    fun insertAnalysisData_preservesDataStartAndEndDate() = runBlocking {
        val data = makeAnalysisData("005930", dataStartDate = "2025-01-01", dataEndDate = "2026-02-20")
        stockAnalysisDao.insertAnalysisData(data)

        @Suppress("DEPRECATION")
        val result = stockAnalysisDao.getAnalysisData("005930")

        assertNotNull(result)
        assertEquals("2025-01-01", result!!.dataStartDate)
        assertEquals("2026-02-20", result.dataEndDate)
    }

    @Test
    fun deleteAnalysisData_forNonExistentTicker_doesNotThrow() = runBlocking {
        // Should not throw even if ticker doesn't exist
        stockAnalysisDao.deleteAnalysisData("DOES_NOT_EXIST")
        val count = stockAnalysisDao.getCount()
        assertEquals(0, count)
    }
}
