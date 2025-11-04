package com.etfmonitor.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.etfmonitor.database.entities.Etf
import com.etfmonitor.database.entities.Holding
import com.etfmonitor.database.entities.Setting

@Database(
    entities = [Etf::class, Holding::class, Setting::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): EtfDao
}