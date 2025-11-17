package com.etfmonitor

import android.app.Application
import androidx.room.Room
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.etfmonitor.database.AppDatabase
import com.etfmonitor.python.PyKrxClient
import com.etfmonitor.repository.DataRepository
import com.etfmonitor.repository.FearGreedRepository
import com.etfmonitor.repository.StockRepository
import com.etfmonitor.repository.StockAnalysisRepository
import com.etfmonitor.repository.MarketDepositRepository
import com.etfmonitor.repository.MarketOscillatorRepository
import com.etfmonitor.oscillator.python.OscillatorPyClient

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
                com.etfmonitor.database.MIGRATION_4_5,
                com.etfmonitor.database.MIGRATION_5_6,
                com.etfmonitor.database.MIGRATION_6_7
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

    val fearGreedRepository: FearGreedRepository by lazy {
        FearGreedRepository(
            fearGreedDao = database.fearGreedDao(),
            python = python
        )
    }

    val marketOscillatorRepository: MarketOscillatorRepository by lazy {
        MarketOscillatorRepository(
            dao = database.marketOscillatorDao(),
            pyClient = OscillatorPyClient(python)
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Schedule Market Oscillator update at 8:00 PM every day
        com.etfmonitor.worker.WorkManagerHelper.scheduleMarketOscillatorUpdate(
            context = this,
            hour = 20,
            minute = 0
        )
    }
}