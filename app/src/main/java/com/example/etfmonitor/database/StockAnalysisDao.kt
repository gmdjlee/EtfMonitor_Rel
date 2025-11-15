package com.etfmonitor.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.database.entities.StockAnalysisData

@Dao
interface StockAnalysisDao {
    @Query("SELECT * FROM stock_analysis_data WHERE ticker = :ticker")
    suspend fun getAnalysisData(ticker: String): StockAnalysisData?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysisData(data: StockAnalysisData)

    @Query("DELETE FROM stock_analysis_data WHERE ticker = :ticker")
    suspend fun deleteAnalysisData(ticker: String)

    @Query("DELETE FROM stock_analysis_data")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM stock_analysis_data")
    suspend fun getCount(): Int

    @Query("SELECT * FROM stock_analysis_data ORDER BY lastUpdated DESC")
    suspend fun getAllAnalysisData(): List<StockAnalysisData>
}
