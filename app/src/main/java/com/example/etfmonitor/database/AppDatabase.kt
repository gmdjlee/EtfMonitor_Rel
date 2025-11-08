package com.etfmonitor.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.etfmonitor.database.entities.Etf
import com.etfmonitor.database.entities.Holding
import com.etfmonitor.database.entities.Setting
import com.etfmonitor.database.entities.Stock

@Database(
    entities = [Etf::class, Holding::class, Setting::class, Stock::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): EtfDao
    abstract fun stockDao(): StockDao
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