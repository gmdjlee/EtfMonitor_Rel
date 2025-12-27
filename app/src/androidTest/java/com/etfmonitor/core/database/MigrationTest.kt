package com.etfmonitor.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Database migration tests for ETF Monitor.
 *
 * Tests all 17 migrations (v1 -> v17) to ensure:
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
        MIGRATION_16_17
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
        assert(cursor.count == 1) { "stocks table should exist after migration 1->2" }
        cursor.close()

        // Verify etfs data is preserved
        val etfCursor = db.query("SELECT * FROM etfs WHERE ticker = '069500'")
        assert(etfCursor.moveToFirst()) { "ETF data should be preserved after migration" }
        assert(etfCursor.getString(etfCursor.getColumnIndex("name")) == "KODEX 200")
        etfCursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3_addsMarketDepositsTable() {
        helper.createDatabase(testDb, 2).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='market_deposits'")
        assert(cursor.count == 1) { "market_deposits table should exist after migration 2->3" }
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4_addsStockAnalysisDataTable() {
        helper.createDatabase(testDb, 3).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='stock_analysis_data'")
        assert(cursor.count == 1) { "stock_analysis_data table should exist after migration 3->4" }
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate4To5_addsSearchHistoryTable() {
        helper.createDatabase(testDb, 4).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 5, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='search_history'")
        assert(cursor.count == 1) { "search_history table should exist after migration 4->5" }
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate5To6_addsFearGreedIndexTable() {
        helper.createDatabase(testDb, 5).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 6, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='fear_greed_index'")
        assert(cursor.count == 1) { "fear_greed_index table should exist after migration 5->6" }
        cursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate6To7_addsMarketOscillatorTable() {
        helper.createDatabase(testDb, 6).apply { close() }
        val db = helper.runMigrationsAndValidate(testDb, 7, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='market_oscillator'")
        assert(cursor.count == 1) { "market_oscillator table should exist after migration 6->7" }
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

        assert(columnNames.contains("weightBps")) { "holdings should have weightBps column" }
        assert(columnNames.contains("amountMillion")) { "holdings should have amountMillion column" }
        assert(columnNames.contains("snapshotType")) { "holdings should have snapshotType column" }

        // Verify data conversion (25% -> 2500 bps, 50B KRW -> 50000 million)
        val dataCursor = db.query("SELECT * FROM holdings WHERE stockTicker = '005930'")
        assert(dataCursor.moveToFirst()) { "Holding data should be preserved" }

        val weightBps = dataCursor.getInt(dataCursor.getColumnIndex("weightBps"))
        val amountMillion = dataCursor.getInt(dataCursor.getColumnIndex("amountMillion"))

        assert(weightBps == 2500) { "weight should be converted to basis points: expected 2500, got $weightBps" }
        assert(amountMillion == 50000) { "amount should be converted to millions: expected 50000, got $amountMillion" }
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
        assert(cursor.count == 1) { "market_index table should exist after migration 8->9" }
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
        assert(cursor.count == 1) { "daily_etf_statistics table should exist after migration 9->10" }
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
            assert(cursor.count == 1) { "$table table should exist after migration 10->11" }
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
        assert(cursor.count == 1) { "stock_predictions table should exist after migration 11->12" }
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

        assert(stockColumns.contains("sector")) { "stocks should have sector column" }
        assert(stockColumns.contains("is_etf_holding")) { "stocks should have is_etf_holding column" }

        // Verify stock_analysis_data no longer has name column
        val analysisCursor = db.query("PRAGMA table_info(stock_analysis_data)")
        val analysisColumns = mutableListOf<String>()
        while (analysisCursor.moveToNext()) {
            analysisColumns.add(analysisCursor.getString(analysisCursor.getColumnIndex("name")))
        }
        analysisCursor.close()

        assert(!analysisColumns.contains("name")) { "stock_analysis_data should NOT have name column after migration 12->13" }
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
            assert(cursor.count == 1) { "$table table should exist after migration 13->14" }
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
            assert(cursor.count == 1) { "$table table should exist after migration 14->15" }
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
        assert(cursor.count == 1) { "stock_indicator_ai_result table should exist after migration 15->16" }
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

        assert(columns.contains("historyType")) { "search_history should have historyType column after migration 16->17" }
    }

    // ===========================================
    // Full Migration Test
    // ===========================================

    @Test
    @Throws(IOException::class)
    fun migrateAll_version1To17() {
        // Create v1 database with initial data
        helper.createDatabase(testDb, 1).apply {
            execSQL("INSERT INTO etfs (ticker, name) VALUES ('069500', 'KODEX 200')")
            execSQL("INSERT INTO etfs (ticker, name) VALUES ('102110', 'TIGER 200')")
            execSQL("INSERT INTO holdings (etfTicker, stockTicker, stockName, date, weight, amount) VALUES ('069500', '005930', '삼성전자', '2025-01-15', 0.25, 50000000000)")
            close()
        }

        // Run all migrations
        val db = helper.runMigrationsAndValidate(testDb, 17, true, *allMigrations)

        // Verify final schema has all expected tables
        val expectedTables = listOf(
            "etfs", "holdings", "settings", "stocks", "market_deposits",
            "stock_analysis_data", "search_history", "fear_greed_index",
            "market_oscillator", "market_index", "daily_etf_statistics",
            "correlation_analysis_result", "ai_analysis_result",
            "ai_chat_session", "ai_chat_message", "stock_predictions",
            "sector_analysis", "etf_correlation_cache", "liquidity_analysis",
            "price_cache", "enhanced_predictions", "stock_indicator_ai_result"
        )

        for (table in expectedTables) {
            val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'")
            assert(cursor.count == 1) { "$table table should exist after full migration" }
            cursor.close()
        }

        // Verify ETF data is preserved
        val etfCursor = db.query("SELECT COUNT(*) as count FROM etfs")
        etfCursor.moveToFirst()
        assert(etfCursor.getInt(0) == 2) { "ETF data should be preserved after full migration" }
        etfCursor.close()

        // Verify holding data is converted correctly
        val holdingCursor = db.query("SELECT weightBps, amountMillion FROM holdings WHERE stockTicker = '005930'")
        holdingCursor.moveToFirst()
        val weightBps = holdingCursor.getInt(0)
        val amountMillion = holdingCursor.getInt(1)
        holdingCursor.close()

        assert(weightBps == 2500) { "weight should be 2500 bps" }
        assert(amountMillion == 50000) { "amount should be 50000 million" }
    }
}
