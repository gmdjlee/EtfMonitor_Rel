package com.etfmonitor.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.etfmonitor.database.entities.Etf
import com.etfmonitor.database.entities.FearGreedIndex
import com.etfmonitor.database.entities.Holding
import com.etfmonitor.database.entities.MarketDeposit
import com.etfmonitor.database.entities.MarketOscillatorData
import com.etfmonitor.database.entities.SearchHistory
import com.etfmonitor.database.entities.Setting
import com.etfmonitor.database.entities.Stock
import com.etfmonitor.database.entities.StockAnalysisData

@Database(
    entities = [Etf::class, Holding::class, Setting::class, Stock::class, MarketDeposit::class, StockAnalysisData::class, SearchHistory::class, FearGreedIndex::class, MarketOscillatorData::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): EtfDao
    abstract fun stockDao(): StockDao
    abstract fun marketDepositDao(): MarketDepositDao
    abstract fun stockAnalysisDao(): StockAnalysisDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun fearGreedDao(): FearGreedDao
    abstract fun marketOscillatorDao(): MarketOscillatorDao
}

/**
 * Migration from version 1 to 2: Add Stock table
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS stocks (
                ticker TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                market TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * Migration from version 2 to 3: Add MarketDeposit table
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS market_deposits (
                date TEXT PRIMARY KEY NOT NULL,
                depositAmount REAL NOT NULL,
                depositChange REAL NOT NULL,
                creditAmount REAL NOT NULL,
                creditChange REAL NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * Migration from version 3 to 4: Add StockAnalysisData table
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS stock_analysis_data (
                ticker TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                dates TEXT NOT NULL,
                marketCap TEXT NOT NULL,
                foreign5d TEXT NOT NULL,
                institution5d TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL,
                dataStartDate TEXT NOT NULL,
                dataEndDate TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * Migration from version 4 to 5: Add SearchHistory table
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS search_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                ticker TEXT NOT NULL,
                name TEXT NOT NULL,
                market TEXT NOT NULL,
                searchedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * Migration from version 5 to 6: Add FearGreedIndex table
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS fear_greed_index (
                id TEXT PRIMARY KEY NOT NULL,
                market TEXT NOT NULL,
                date TEXT NOT NULL,
                indexValue REAL NOT NULL,
                fearGreedValue REAL NOT NULL,
                oscillator REAL NOT NULL,
                rsi REAL NOT NULL,
                momentum REAL NOT NULL,
                putCallRatio REAL NOT NULL,
                volatility REAL NOT NULL,
                spread REAL NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * Migration from version 6 to 7: Add MarketOscillator table
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS market_oscillator (
                id TEXT PRIMARY KEY NOT NULL,
                market TEXT NOT NULL,
                date TEXT NOT NULL,
                indexValue REAL NOT NULL,
                oscillator REAL NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}