package com.etfmonitor.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Database migration tests for ETF Monitor.
 *
 * Tests all 20 migrations (v1 -> v21) to ensure:
 * 1. Schema changes are applied correctly
 * 2. Data is preserved during migration
 * 3. No exceptions occur during migration
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val testDb = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * All migrations defined in AppDatabase.
     */
    private val allMigrations = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
        MIGRATION_19_20,
        MIGRATION_20_21
    )

    // ===========================================
    // Individual Migration Tests
    // ===========================================

    @Test
    @Throws(IOException::class)
    fun migrate1To2_addsStocksTable() {
        // Create database at version 1
        var db = helper.createDatabase(testDb, 1).apply {
            // Insert test data into etfs table
            execSQL(
                """INSERT INTO etfs (ticker, name) VALUES ('069500', 'KODEX 200')"""
            )
            close()
        }

        // Run migration 1 -> 2
        db = helper.runMigrationsAndValidate(testDb, 2, true, MIGRATION_1_2)

        // Verify stocks table exists
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='stocks'")
        assertEquals("stocks table should exist after migration 1->2", 1, cursor.count)
        cursor.close()

        // Verify etfs data is preserved
        val etfCursor = db.query("SELECT * FROM etfs WHERE ticker = '069500'")
        assertTrue("ETF data should be preserved after migration", etfCursor.moveToFirst())
        assertEquals("KODEX 200", etfCursor.getString(etfCursor.getColumnIndex("name")))
        etfCursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3_addsMarketDepositsTable() {
        helper.createDatabase(testDb, 2).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='market_deposits'")
        assertEquals("market_deposits table should exist after migration 2->3", 1, cursor.count)
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4_addsStockAnalysisDataTable() {
        helper.createDatabase(testDb, 3).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='stock_analysis_data'")
        assertEquals("stock_analysis_data table should exist after migration 3->4", 1, cursor.count)
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate4To5_addsSearchHistoryTable() {
        helper.createDatabase(testDb, 4).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 5, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='search_history'")
        assertEquals("search_history table should exist after migration 4->5", 1, cursor.count)
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate5To6_addsFearGreedIndexTable() {
        helper.createDatabase(testDb, 5).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 6, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='fear_greed_index'")
        assertEquals("fear_greed_index table should exist after migration 5->6", 1, cursor.count)
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate6To7_addsMarketOscillatorTable() {
        helper.createDatabase(testDb, 6).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 7, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='market_oscillator'")
        assertEquals("market_oscillator table should exist after migration 6->7", 1, cursor.count)
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate7To8_optimizesHoldingTableStructure() {
        // Create v7 database with old holdings schema
        var db = helper.createDatabase(testDb, 7).apply {
            // Insert test holding with old schema (weight as REAL, amount as REAL)
            execSQL(
                """INSERT INTO holdings (etfTicker, stockTicker, stockName, date, weight, amount)
                   VALUES ('069500', '005930', '삼성전자', '2025-01-15', 0.25, 50000000000)"""
            )
            close()
        }

        // Run migration 7 -> 8
        db = helper.runMigrationsAndValidate(testDb, 8, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)

        // Verify new columns exist
        val cursor = db.query("PRAGMA table_info(holdings)")
        val columnNames = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columnNames.add(cursor.getString(cursor.getColumnIndex("name")))
        }
        cursor.close()

        assertTrue("holdings should have weightBps column", columnNames.contains("weightBps"))
        assertTrue("holdings should have amountMillion column", columnNames.contains("amountMillion"))
        assertTrue("holdings should have snapshotType column", columnNames.contains("snapshotType"))

        // Verify data conversion (25% -> 2500 bps, 50B KRW -> 50000 million)
        val dataCursor = db.query("SELECT * FROM holdings WHERE stockTicker = '005930'")
        assertTrue("Holding data should be preserved", dataCursor.moveToFirst())

        val weightBps = dataCursor.getInt(dataCursor.getColumnIndex("weightBps"))
        val amountMillion = dataCursor.getInt(dataCursor.getColumnIndex("amountMillion"))

        assertEquals("weight should be converted to basis points", 2500, weightBps)
        assertEquals("amount should be converted to millions", 50000, amountMillion)
        dataCursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate8To9_addsMarketIndexTable() {
        helper.createDatabase(testDb, 8).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 9, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='market_index'")
        assertEquals("market_index table should exist after migration 8->9", 1, cursor.count)
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate9To10_addsDailyEtfStatisticsTable() {
        helper.createDatabase(testDb, 9).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 10, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='daily_etf_statistics'")
        assertEquals("daily_etf_statistics table should exist after migration 9->10", 1, cursor.count)
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11_addsAIAnalysisTables() {
        helper.createDatabase(testDb, 10).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 11, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)

        // Verify all AI tables exist
        val tables = listOf(
            "correlation_analysis_result",
            "ai_analysis_result",
            "ai_chat_session",
            "ai_chat_message"
        )

        for (table in tables) {
            val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'")
            assertEquals("$table table should exist after migration 10->11", 1, cursor.count)
            cursor.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate11To12_addsStockPredictionsTable() {
        helper.createDatabase(testDb, 11).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 12, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
            MIGRATION_11_12)

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='stock_predictions'")
        assertEquals("stock_predictions table should exist after migration 11->12", 1, cursor.count)
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate12To13_stockMasterIntegration() {
        // Create v12 database with test data
        var db = helper.createDatabase(testDb, 12).apply {
            // Insert stock
            execSQL("INSERT INTO stocks (ticker, name, market, lastUpdated) VALUES ('005930', '삼성전자', 'KOSPI', 0)")
            // Insert stock_analysis_data with old schema (has name column)
            execSQL(
                """INSERT INTO stock_analysis_data (ticker, name, dates, marketCap, foreign5d, institution5d, lastUpdated, dataStartDate, dataEndDate)
                   VALUES ('005930', '삼성전자', '[]', '[]', '[]', '[]', 0, '2025-01-01', '2025-01-15')"""
            )
            close()
        }

        // Run migration
        db = helper.runMigrationsAndValidate(testDb, 13, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
            MIGRATION_11_12, MIGRATION_12_13)

        // Verify stocks table has new columns
        val stocksCursor = db.query("PRAGMA table_info(stocks)")
        val stockColumns = mutableListOf<String>()
        while (stocksCursor.moveToNext()) {
            stockColumns.add(stocksCursor.getString(stocksCursor.getColumnIndex("name")))
        }
        stocksCursor.close()

        assertTrue("stocks should have sector column", stockColumns.contains("sector"))
        assertTrue("stocks should have is_etf_holding column", stockColumns.contains("is_etf_holding"))

        // Verify stock_analysis_data no longer has name column
        val analysisCursor = db.query("PRAGMA table_info(stock_analysis_data)")
        val analysisColumns = mutableListOf<String>()
        while (analysisCursor.moveToNext()) {
            analysisColumns.add(analysisCursor.getString(analysisCursor.getColumnIndex("name")))
        }
        analysisCursor.close()

        assertFalse("stock_analysis_data should NOT have name column after migration 12->13",
            analysisColumns.contains("name"))
    }

    @Test
    @Throws(IOException::class)
    fun migrate13To14_addsAdvancedAnalysisTables() {
        helper.createDatabase(testDb, 13).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 14, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
            MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)

        val tables = listOf("sector_analysis", "etf_correlation_cache", "liquidity_analysis")

        for (table in tables) {
            val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'")
            assertEquals("$table table should exist after migration 13->14", 1, cursor.count)
            cursor.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate14To15_addsEnhancedPredictionTables() {
        helper.createDatabase(testDb, 14).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 15, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
            MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)

        val tables = listOf("price_cache", "enhanced_predictions")

        for (table in tables) {
            val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'")
            assertEquals("$table table should exist after migration 14->15", 1, cursor.count)
            cursor.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate15To16_addsStockIndicatorAIResultTable() {
        helper.createDatabase(testDb, 15).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 16, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
            MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='stock_indicator_ai_result'")
        assertEquals("stock_indicator_ai_result table should exist after migration 15->16", 1, cursor.count)
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate16To17_addsHistoryTypeColumn() {
        helper.createDatabase(testDb, 16).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 17, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
            MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
            MIGRATION_16_17)

        // Verify historyType column exists
        val cursor = db.query("PRAGMA table_info(search_history)")
        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndex("name")))
        }
        cursor.close()

        assertTrue("search_history should have historyType column after migration 16->17",
            columns.contains("historyType"))
    }

    @Test
    @Throws(IOException::class)
    fun migrate17To18_addsBloodIndicatorTable() {
        helper.createDatabase(testDb, 17).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 18, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
            MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
            MIGRATION_16_17, MIGRATION_17_18)

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='blood_indicator'")
        assertEquals("blood_indicator table should exist after migration 17->18", 1, cursor.count)
        cursor.close()

        // Verify expected columns from v1 schema: id, date, bloodValue, irx, hygYield, tenYearYield, spreadValue, spyClose, signalType, lastUpdated
        val schemaCursor = db.query("PRAGMA table_info(blood_indicator)")
        val columnNames = mutableListOf<String>()
        while (schemaCursor.moveToNext()) {
            columnNames.add(schemaCursor.getString(schemaCursor.getColumnIndex("name")))
        }
        schemaCursor.close()

        assertTrue("blood_indicator should have id column", columnNames.contains("id"))
        assertTrue("blood_indicator should have date column", columnNames.contains("date"))
        assertTrue("blood_indicator should have bloodValue column", columnNames.contains("bloodValue"))
        assertTrue("blood_indicator should have signalType column", columnNames.contains("signalType"))
    }

    @Test
    @Throws(IOException::class)
    fun migrate18To19_updatesBloodIndicatorSchemaForFredApi() {
        helper.createDatabase(testDb, 18).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 19, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
            MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
            MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)

        // Verify new schema columns exist (FRED API v2.0)
        val cursor = db.query("PRAGMA table_info(blood_indicator)")
        val columnNames = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columnNames.add(cursor.getString(cursor.getColumnIndex("name")))
        }
        cursor.close()

        assertTrue("blood_indicator should have bloodSma column", columnNames.contains("bloodSma"))
        assertTrue("blood_indicator should have us03my column", columnNames.contains("us03my"))
        assertTrue("blood_indicator should have highYieldSpread column", columnNames.contains("highYieldSpread"))
        assertTrue("blood_indicator should have signalColor column", columnNames.contains("signalColor"))

        // Old columns removed in v18->19
        assertFalse("blood_indicator should NOT have irx column after migration 18->19",
            columnNames.contains("irx"))
        assertFalse("blood_indicator should NOT have hygYield column after migration 18->19",
            columnNames.contains("hygYield"))
        assertFalse("blood_indicator should NOT have tenYearYield column after migration 18->19",
            columnNames.contains("tenYearYield"))
        assertFalse("blood_indicator should NOT have spreadValue column after migration 18->19",
            columnNames.contains("spreadValue"))
    }

    @Test
    @Throws(IOException::class)
    fun migrate19To20_addsFinancialCacheTable() {
        helper.createDatabase(testDb, 19).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 20, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
            MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
            MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='financial_cache'")
        assertEquals("financial_cache table should exist after migration 19->20", 1, cursor.count)
        cursor.close()

        // Verify columns
        val schemaCursor = db.query("PRAGMA table_info(financial_cache)")
        val columnNames = mutableListOf<String>()
        while (schemaCursor.moveToNext()) {
            columnNames.add(schemaCursor.getString(schemaCursor.getColumnIndex("name")))
        }
        schemaCursor.close()

        assertTrue("financial_cache should have ticker column", columnNames.contains("ticker"))
        assertTrue("financial_cache should have name column", columnNames.contains("name"))
        assertTrue("financial_cache should have data column", columnNames.contains("data"))
        assertTrue("financial_cache should have cachedAt column", columnNames.contains("cachedAt"))
    }

    @Test
    @Throws(IOException::class)
    fun migrate20To21_addsPerformanceIndices() {
        val db = helper.createDatabase(testDb, 20).apply {
            // Insert test data into tables that will get indices
            execSQL("INSERT INTO fear_greed_index (date, value, rsiScore, macdScore, volumeScore, priceStrengthScore, safeHavenScore) VALUES ('2026-02-20', 50.0, 50.0, 50.0, 50.0, 50.0, 50.0)")
            execSQL("INSERT INTO market_oscillator (date, oscillatorValue) VALUES ('2026-02-20', 0.5)")
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(testDb, 21, true,
            MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21)

        // Verify indices were created by checking sqlite_master
        val indexCursor = migratedDb.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'idx_%'"
        )
        val indices = mutableListOf<String>()
        while (indexCursor.moveToNext()) {
            indices.add(indexCursor.getString(0))
        }
        indexCursor.close()

        assertTrue("idx_fear_greed_date should exist", indices.contains("idx_fear_greed_date"))
        assertTrue("idx_market_oscillator_date should exist", indices.contains("idx_market_oscillator_date"))

        // Verify data is preserved after migration
        val dataCursor = migratedDb.query("SELECT COUNT(*) FROM fear_greed_index")
        dataCursor.moveToFirst()
        assertEquals("Data should be preserved after index migration", 1, dataCursor.getInt(0))
        dataCursor.close()
    }

    // ===========================================
    // Full Migration Test
    // ===========================================

    @Test
    @Throws(IOException::class)
    fun migrateAll_version1To21() {
        // Create v1 database with initial data
        helper.createDatabase(testDb, 1).apply {
            execSQL("INSERT INTO etfs (ticker, name) VALUES ('069500', 'KODEX 200')")
            execSQL("INSERT INTO etfs (ticker, name) VALUES ('102110', 'TIGER 200')")
            execSQL("INSERT INTO holdings (etfTicker, stockTicker, stockName, date, weight, amount) VALUES ('069500', '005930', '삼성전자', '2025-01-15', 0.25, 50000000000)")
            close()
        }

        // Run all migrations
        val db = helper.runMigrationsAndValidate(testDb, 21, true, *allMigrations)

        // Verify final schema has all expected tables
        val expectedTables = listOf(
            "etfs", "holdings", "settings", "stocks", "market_deposits",
            "stock_analysis_data", "search_history", "fear_greed_index",
            "market_oscillator", "market_index", "daily_etf_statistics",
            "correlation_analysis_result", "ai_analysis_result",
            "ai_chat_session", "ai_chat_message", "stock_predictions",
            "sector_analysis", "etf_correlation_cache", "liquidity_analysis",
            "price_cache", "enhanced_predictions", "stock_indicator_ai_result",
            "blood_indicator", "financial_cache"
        )

        for (table in expectedTables) {
            val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'")
            assertEquals("$table table should exist after full migration", 1, cursor.count)
            cursor.close()
        }

        // Verify ETF data is preserved
        val etfCursor = db.query("SELECT COUNT(*) as count FROM etfs")
        etfCursor.moveToFirst()
        assertEquals("ETF data should be preserved after full migration", 2, etfCursor.getInt(0))
        etfCursor.close()

        // Verify holding data is converted correctly
        val holdingCursor = db.query("SELECT weightBps, amountMillion FROM holdings WHERE stockTicker = '005930'")
        holdingCursor.moveToFirst()
        val weightBps = holdingCursor.getInt(0)
        val amountMillion = holdingCursor.getInt(1)
        holdingCursor.close()

        assertEquals("weight should be 2500 bps", 2500, weightBps)
        assertEquals("amount should be 50000 million", 50000, amountMillion)
    }
}
