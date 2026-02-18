package com.etfmonitor.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.core.database.entities.StockAnalysisData
import com.etfmonitor.core.database.entities.StockAnalysisWithName

@Dao
interface StockAnalysisDao {
    @Deprecated("Use getAnalysisDataWithName() instead — this query returns name=null", ReplaceWith("getAnalysisDataWithName(ticker)"))
    @Query("SELECT * FROM stock_analysis_data WHERE ticker = :ticker")
    suspend fun getAnalysisData(ticker: String): StockAnalysisData?

    /** stocks 테이블과 JOIN하여 name 포함 조회 */
    @Query("""
        SELECT a.ticker, COALESCE(s.name, a.ticker) as name,
               a.dates, a.marketCap, a.foreign5d, a.institution5d,
               a.lastUpdated, a.dataStartDate, a.dataEndDate
        FROM stock_analysis_data a
        LEFT JOIN stocks s ON a.ticker = s.ticker
        WHERE a.ticker = :ticker
    """)
    suspend fun getAnalysisDataWithName(ticker: String): StockAnalysisWithName?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysisData(data: StockAnalysisData)

    @Query("DELETE FROM stock_analysis_data WHERE ticker = :ticker")
    suspend fun deleteAnalysisData(ticker: String)

    @Query("DELETE FROM stock_analysis_data")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM stock_analysis_data")
    suspend fun getCount(): Int

    @Query("SELECT * FROM stock_analysis_data ORDER BY lastUpdated DESC LIMIT 500")
    suspend fun getAllAnalysisData(): List<StockAnalysisData>

    /** stocks JOIN으로 전체 조회 */
    @Query("""
        SELECT a.ticker, COALESCE(s.name, a.ticker) as name,
               a.dates, a.marketCap, a.foreign5d, a.institution5d,
               a.lastUpdated, a.dataStartDate, a.dataEndDate
        FROM stock_analysis_data a
        LEFT JOIN stocks s ON a.ticker = s.ticker
        ORDER BY a.lastUpdated DESC
        LIMIT 500
    """)
    suspend fun getAllAnalysisDataWithName(): List<StockAnalysisWithName>
}
