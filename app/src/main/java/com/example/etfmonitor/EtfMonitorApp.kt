package com.etfmonitor

import android.app.Application
import androidx.room.Room
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.etfmonitor.database.AppDatabase
import com.etfmonitor.python.PyKrxClient
import com.etfmonitor.repository.DataRepository
import com.etfmonitor.repository.StockRepository
import com.etfmonitor.repository.StockAnalysisRepository
import com.etfmonitor.repository.MarketDepositRepository

class EtfMonitorApp : Application() {

    companion object {
        lateinit var instance: EtfMonitorApp
            private set
    }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "etf_monitor.db"
        )
            .addMigrations(
                com.etfmonitor.database.MIGRATION_1_2,
                com.etfmonitor.database.MIGRATION_2_3,
                com.etfmonitor.database.MIGRATION_3_4,
                com.etfmonitor.database.MIGRATION_4_5
            )
            .build()
    }

    val python: Python by lazy {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        Python.getInstance()
    }

    val pyKrxClient: PyKrxClient by lazy {
        PyKrxClient(python)
    }

    val repository: DataRepository by lazy {
        DataRepository(database.dao(), pyKrxClient)
    }

    /**
     * Singleton repositories for optimized memory usage
     */
    val stockRepository: StockRepository by lazy {
        StockRepository(
            stockDao = database.stockDao(),
            python = python
        )
    }

    val stockAnalysisRepository: StockAnalysisRepository by lazy {
        StockAnalysisRepository(
            stockAnalysisDao = database.stockAnalysisDao(),
            python = python
        )
    }

    val marketDepositRepository: MarketDepositRepository by lazy {
        MarketDepositRepository(
            marketDepositDao = database.marketDepositDao(),
            python = python
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}