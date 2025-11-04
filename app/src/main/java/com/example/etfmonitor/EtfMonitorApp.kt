package com.etfmonitor

import android.app.Application
import androidx.room.Room
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.etfmonitor.database.AppDatabase
import com.etfmonitor.python.PyKrxClient
import com.etfmonitor.repository.DataRepository

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
            .fallbackToDestructiveMigration(dropAllTables = true)
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

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}