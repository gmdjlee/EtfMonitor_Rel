package com.etfmonitor.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.etfmonitor.core.database.AppDatabase
import com.etfmonitor.core.database.BackupDao
import com.etfmonitor.core.database.entities.Etf
import com.etfmonitor.core.database.entities.Holding
import com.etfmonitor.core.database.entities.MarketDeposit
import com.etfmonitor.core.database.entities.PriceCache
import com.etfmonitor.core.database.entities.Setting
import com.etfmonitor.core.database.entities.Stock
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
 * Integration tests for BackupDao using an in-memory Room database.
 *
 * BackupDao is a backup/restore DAO with 60+ queries. Key risks:
 * - OOM: getAllHoldings() has NO LIMIT — P0 issue documented in PROJECT_REVIEW.md
 * - @Transaction with OnConflictStrategy.IGNORE: duplicates must be skipped
 * - Date range filtering must honor startDate and endDate boundaries
 * - Insert-then-query patterns verify atomicity via @Transaction
 *
 * Tests deliberately document the OOM-risk unbounded queries and verify
 * that the current behavior is correct even if unsafe for large datasets.
 */
@RunWith(AndroidJUnit4::class)
class BackupDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: BackupDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.backupDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    // ====================== ETF backup queries ======================

    @Test
    fun getAllEtfs_onEmptyDb_returnsEmptyList() = runBlocking {
        val etfs = dao.getAllEtfs()
        assertTrue(etfs.isEmpty())
    }

    @Test
    fun insertEtfsIgnore_thenGetAllEtfs_returnsInsertedEtfs() = runBlocking {
        val etfs = listOf(
            Etf("069500", "KODEX 200"),
            Etf("102110", "TIGER 200")
        )
        dao.insertEtfsIgnore(etfs)

        val result = dao.getAllEtfs()
        assertEquals(2, result.size)

        val tickers = result.map { it.ticker }.toSet()
        assertTrue(tickers.contains("069500"))
        assertTrue(tickers.contains("102110"))
    }

    @Test
    fun insertEtfsIgnore_withDuplicates_skipsDuplicates() = runBlocking {
        // OnConflictStrategy.IGNORE: duplicate primary key is silently skipped
        dao.insertEtfsIgnore(listOf(Etf("069500", "KODEX 200")))
        val rowIds = dao.insertEtfsIgnore(listOf(Etf("069500", "KODEX 200 Duplicate")))

        // rowId of -1 indicates the row was ignored (duplicate skipped)
        assertEquals(-1L, rowIds[0])

        val count = dao.getEtfCount()
        assertEquals(1, count)

        // Original value preserved, not overwritten
        val tickers = dao.getAllEtfTickers()
        assertEquals(1, tickers.size)
        assertEquals("069500", tickers[0])
    }

    @Test
    fun getEtfCount_returnsCorrectCount() = runBlocking {
        assertEquals(0, dao.getEtfCount())

        dao.insertEtfsIgnore(listOf(Etf("069500", "KODEX 200")))
        assertEquals(1, dao.getEtfCount())

        dao.insertEtfsIgnore(listOf(Etf("102110", "TIGER 200")))
        assertEquals(2, dao.getEtfCount())
    }

    @Test
    fun getAllEtfTickers_returnsOnlyTickers() = runBlocking {
        dao.insertEtfsIgnore(listOf(
            Etf("069500", "KODEX 200"),
            Etf("102110", "TIGER 200")
        ))

        val tickers = dao.getAllEtfTickers()
        assertEquals(2, tickers.size)
        assertTrue(tickers.containsAll(listOf("069500", "102110")))
    }

    // ====================== Stock backup queries ======================

    @Test
    fun insertStocksIgnore_thenGetAllStocks_returnsAllStocks() = runBlocking {
        val stocks = listOf(
            Stock("005930", "삼성전자", "KOSPI"),
            Stock("000660", "SK하이닉스", "KOSPI")
        )
        dao.insertStocksIgnore(stocks)

        val result = dao.getAllStocks()
        assertEquals(2, result.size)
    }

    @Test
    fun insertStocksIgnore_withDuplicates_skipsDuplicates() = runBlocking {
        dao.insertStocksIgnore(listOf(Stock("005930", "삼성전자", "KOSPI")))
        dao.insertStocksIgnore(listOf(Stock("005930", "삼성전자 Duplicate", "KOSPI")))

        val count = dao.getStockCount()
        assertEquals(1, count)
    }

    @Test
    fun getAllStockTickers_returnsOnlyTickers() = runBlocking {
        dao.insertStocksIgnore(listOf(
            Stock("005930", "삼성전자", "KOSPI"),
            Stock("035420", "네이버", "KOSDAQ")
        ))

        val tickers = dao.getAllStockTickers()
        assertEquals(2, tickers.size)
        assertTrue(tickers.containsAll(listOf("005930", "035420")))
    }

    // ====================== Setting backup queries ======================

    @Test
    fun insertSettingsIgnore_thenGetAllSettings_returnsAllSettings() = runBlocking {
        val settings = listOf(
            Setting("theme", "dark"),
            Setting("language", "ko")
        )
        dao.insertSettingsIgnore(settings)

        val result = dao.getAllSettings()
        assertEquals(2, result.size)
    }

    @Test
    fun insertSettingsIgnore_withDuplicates_skipsDuplicates() = runBlocking {
        dao.insertSettingsIgnore(listOf(Setting("theme", "dark")))
        dao.insertSettingsIgnore(listOf(Setting("theme", "light"))) // same key

        val count = dao.getSettingCount()
        assertEquals(1, count)

        val settings = dao.getAllSettings()
        assertEquals("dark", settings[0].value) // Original value preserved
    }

    @Test
    fun getAllSettingKeys_returnsOnlyKeys() = runBlocking {
        dao.insertSettingsIgnore(listOf(Setting("theme", "dark"), Setting("sync", "enabled")))

        val keys = dao.getAllSettingKeys()
        assertEquals(2, keys.size)
        assertTrue(keys.containsAll(listOf("theme", "sync")))
    }

    // ====================== Holding backup queries ======================

    @Test
    fun getAllHoldings_onEmptyDb_returnsEmptyList() = runBlocking {
        // NOTE: getAllHoldings() has NO LIMIT — documented OOM risk (P0 in PROJECT_REVIEW.md)
        // This test confirms correctness for small datasets
        val holdings = dao.getAllHoldings()
        assertTrue(holdings.isEmpty())
    }

    @Test
    fun insertHoldingsIgnore_thenGetAllHoldings_returnsAllHoldings() = runBlocking {
        dao.insertHoldingsIgnore(listOf(
            Holding.create("069500", "005930", "삼성전자", "2026-02-20", 0.25f, 2_500_000_000f),
            Holding.create("069500", "000660", "SK하이닉스", "2026-02-20", 0.12f, 1_200_000_000f)
        ))

        val holdings = dao.getAllHoldings()
        assertEquals(2, holdings.size)
    }

    @Test
    fun insertHoldingsIgnore_withDuplicates_skipsDuplicates() = runBlocking {
        // Holding primary key: (etfTicker, stockTicker, date)
        val holding = Holding.create("069500", "005930", "삼성전자", "2026-02-20", 0.25f, 2_500_000_000f)
        dao.insertHoldingsIgnore(listOf(holding))

        // Same primary key, different weight — IGNORE means original is kept
        val duplicate = Holding.create("069500", "005930", "삼성전자", "2026-02-20", 0.30f, 3_000_000_000f)
        val rowIds = dao.insertHoldingsIgnore(listOf(duplicate))
        assertEquals(-1L, rowIds[0])

        val count = dao.getHoldingCount()
        assertEquals(1, count)
    }

    @Test
    fun getHoldingsByDateRange_returnsHoldingsWithinBoundaries() = runBlocking {
        dao.insertHoldingsIgnore(listOf(
            Holding.create("069500", "005930", "삼성전자", "2026-02-17", 0.25f, 2_500_000_000f),
            Holding.create("069500", "005930", "삼성전자", "2026-02-18", 0.25f, 2_500_000_000f),
            Holding.create("069500", "005930", "삼성전자", "2026-02-19", 0.25f, 2_500_000_000f),
            Holding.create("069500", "005930", "삼성전자", "2026-02-20", 0.25f, 2_500_000_000f)
        ))

        val holdings = dao.getHoldingsByDateRange("2026-02-18", "2026-02-19")
        assertEquals(2, holdings.size)
        assertTrue(holdings.all { it.date >= "2026-02-18" && it.date <= "2026-02-19" })
    }

    @Test
    fun getHoldingsByDateRange_excludesOutsideBoundaries() = runBlocking {
        dao.insertHoldingsIgnore(listOf(
            Holding.create("069500", "005930", "삼성전자", "2026-02-17", 0.25f, 2_500_000_000f),
            Holding.create("069500", "005930", "삼성전자", "2026-02-20", 0.25f, 2_500_000_000f)
        ))

        val holdings = dao.getHoldingsByDateRange("2026-02-18", "2026-02-19")
        assertTrue(holdings.isEmpty())
    }

    @Test
    fun getHoldingMinDate_returnsEarliestDate() = runBlocking {
        dao.insertHoldingsIgnore(listOf(
            Holding.create("069500", "005930", "삼성전자", "2026-02-18", 0.25f, 2_500_000_000f),
            Holding.create("069500", "000660", "SK하이닉스", "2026-02-20", 0.12f, 1_200_000_000f)
        ))

        val minDate = dao.getHoldingMinDate()
        assertEquals("2026-02-18", minDate)
    }

    @Test
    fun getHoldingMaxDate_returnsLatestDate() = runBlocking {
        dao.insertHoldingsIgnore(listOf(
            Holding.create("069500", "005930", "삼성전자", "2026-02-18", 0.25f, 2_500_000_000f),
            Holding.create("069500", "000660", "SK하이닉스", "2026-02-20", 0.12f, 1_200_000_000f)
        ))

        val maxDate = dao.getHoldingMaxDate()
        assertEquals("2026-02-20", maxDate)
    }

    @Test
    fun getHoldingMinDate_onEmptyDb_returnsNull() = runBlocking {
        val minDate = dao.getHoldingMinDate()
        assertNull(minDate)
    }

    @Test
    fun getHoldingMaxDate_onEmptyDb_returnsNull() = runBlocking {
        val maxDate = dao.getHoldingMaxDate()
        assertNull(maxDate)
    }

    @Test
    fun getAllHoldingKeys_returnsCompositeKeys() = runBlocking {
        dao.insertHoldingsIgnore(listOf(
            Holding.create("069500", "005930", "삼성전자", "2026-02-20", 0.25f, 2_500_000_000f),
            Holding.create("069500", "000660", "SK하이닉스", "2026-02-20", 0.12f, 1_200_000_000f)
        ))

        val keys = dao.getAllHoldingKeys()
        assertEquals(2, keys.size)
        // Format: "etfTicker-stockTicker-date"
        assertTrue(keys.contains("069500-005930-2026-02-20"))
        assertTrue(keys.contains("069500-000660-2026-02-20"))
    }

    // ====================== MarketDeposit backup queries ======================

    @Test
    fun insertMarketDepositsIgnore_thenGetAll_returnsAllDeposits() = runBlocking {
        val deposits = listOf(
            MarketDeposit("2026-02-18", 500.0, 10.0, 200.0, 5.0),
            MarketDeposit("2026-02-19", 510.0, 10.0, 205.0, 5.0),
            MarketDeposit("2026-02-20", 520.0, 10.0, 210.0, 5.0)
        )
        dao.insertMarketDepositsIgnore(deposits)

        val result = dao.getAllMarketDeposits()
        assertEquals(3, result.size)
    }

    @Test
    fun getMarketDepositsByDateRange_returnsCorrectRange() = runBlocking {
        dao.insertMarketDepositsIgnore(listOf(
            MarketDeposit("2026-02-17", 490.0, 8.0, 195.0, 3.0),
            MarketDeposit("2026-02-18", 500.0, 10.0, 200.0, 5.0),
            MarketDeposit("2026-02-19", 510.0, 10.0, 205.0, 5.0),
            MarketDeposit("2026-02-20", 520.0, 10.0, 210.0, 5.0)
        ))

        val result = dao.getMarketDepositsByDateRange("2026-02-18", "2026-02-19")
        assertEquals(2, result.size)
        assertTrue(result.all { it.date >= "2026-02-18" && it.date <= "2026-02-19" })
    }

    @Test
    fun getMarketDepositMinMaxDate_returnsCorrectBoundaries() = runBlocking {
        dao.insertMarketDepositsIgnore(listOf(
            MarketDeposit("2026-02-18", 500.0, 10.0, 200.0, 5.0),
            MarketDeposit("2026-02-20", 520.0, 10.0, 210.0, 5.0)
        ))

        assertEquals("2026-02-18", dao.getMarketDepositMinDate())
        assertEquals("2026-02-20", dao.getMarketDepositMaxDate())
    }

    @Test
    fun insertMarketDepositsIgnore_withDuplicates_skipsDuplicates() = runBlocking {
        // MarketDeposit primary key = date
        dao.insertMarketDepositsIgnore(listOf(MarketDeposit("2026-02-20", 500.0, 10.0, 200.0, 5.0)))
        dao.insertMarketDepositsIgnore(listOf(MarketDeposit("2026-02-20", 999.0, 99.0, 999.0, 99.0))) // duplicate

        val count = dao.getMarketDepositCount()
        assertEquals(1, count)

        // Original preserved
        val deposits = dao.getAllMarketDeposits()
        assertEquals(500.0, deposits[0].depositAmount, 0.01)
    }

    // ====================== PriceCache backup queries ======================

    @Test
    fun insertPriceCachesIgnore_thenGetAll_returnsAllCaches() = runBlocking {
        val caches = listOf(
            PriceCache("005930", "2026-02-18", 73000.0),
            PriceCache("005930", "2026-02-19", 74000.0),
            PriceCache("005930", "2026-02-20", 75000.0)
        )
        dao.insertPriceCachesIgnore(caches)

        val result = dao.getAllPriceCaches()
        assertEquals(3, result.size)
    }

    @Test
    fun getPriceCachesByDateRange_returnsCorrectRange() = runBlocking {
        dao.insertPriceCachesIgnore(listOf(
            PriceCache("005930", "2026-02-17", 72000.0),
            PriceCache("005930", "2026-02-18", 73000.0),
            PriceCache("005930", "2026-02-19", 74000.0),
            PriceCache("005930", "2026-02-20", 75000.0)
        ))

        val result = dao.getPriceCachesByDateRange("2026-02-18", "2026-02-19")
        assertEquals(2, result.size)
        assertTrue(result.all { it.date >= "2026-02-18" && it.date <= "2026-02-19" })
    }

    @Test
    fun getPriceCacheMinMaxDate_returnsCorrectBoundaries() = runBlocking {
        dao.insertPriceCachesIgnore(listOf(
            PriceCache("005930", "2026-02-18", 73000.0),
            PriceCache("005930", "2026-02-20", 75000.0)
        ))

        assertEquals("2026-02-18", dao.getPriceCacheMinDate())
        assertEquals("2026-02-20", dao.getPriceCacheMaxDate())
    }

    @Test
    fun insertPriceCachesIgnore_withDuplicates_skipsDuplicates() = runBlocking {
        // PriceCache primary key: (ticker, date)
        dao.insertPriceCachesIgnore(listOf(PriceCache("005930", "2026-02-20", 75000.0)))
        dao.insertPriceCachesIgnore(listOf(PriceCache("005930", "2026-02-20", 99999.0))) // duplicate

        val count = dao.getPriceCacheCount()
        assertEquals(1, count)

        // Original price preserved
        val caches = dao.getAllPriceCaches()
        assertEquals(75000.0, caches[0].closePrice, 0.01)
    }

    @Test
    fun getAllPriceCacheKeys_returnsCompositeKeys() = runBlocking {
        dao.insertPriceCachesIgnore(listOf(
            PriceCache("005930", "2026-02-18", 73000.0),
            PriceCache("000660", "2026-02-20", 200000.0)
        ))

        val keys = dao.getAllPriceCacheKeys()
        assertEquals(2, keys.size)
        // Format: "ticker-date"
        assertTrue(keys.contains("005930-2026-02-18"))
        assertTrue(keys.contains("000660-2026-02-20"))
    }

    // ====================== Global date range ======================

    @Test
    fun getGlobalDateRange_withHoldingsAndDeposits_returnsGlobalMinMax() = runBlocking {
        // holdings: 2026-02-18 to 2026-02-20
        dao.insertHoldingsIgnore(listOf(
            Holding.create("069500", "005930", "삼성전자", "2026-02-18", 0.25f, 2_500_000_000f),
            Holding.create("069500", "005930", "삼성전자", "2026-02-20", 0.25f, 2_500_000_000f)
        ))

        // market_deposits: 2026-02-15 to 2026-02-19 — extends the min boundary
        dao.insertMarketDepositsIgnore(listOf(
            MarketDeposit("2026-02-15", 490.0, 8.0, 195.0, 3.0),
            MarketDeposit("2026-02-19", 510.0, 10.0, 205.0, 5.0)
        ))

        val range = dao.getGlobalDateRange()
        assertNotNull(range)
        // min across all tables = "2026-02-15" (from market_deposits)
        assertEquals("2026-02-15", range!!.minDate)
        // max across all tables = "2026-02-20" (from holdings)
        assertEquals("2026-02-20", range.maxDate)
    }

    @Test
    fun getGlobalDateRange_onEmptyDb_returnsNullDates() = runBlocking {
        // All UNION subqueries return NULL min/max on empty tables
        val range = dao.getGlobalDateRange()
        // The result row will exist but minDate/maxDate will be null
        if (range != null) {
            assertNull(range.minDate)
            assertNull(range.maxDate)
        }
        // If Room returns null for the whole query when all values are null, that's also acceptable
    }
}
