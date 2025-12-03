package com.etfmonitor.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.database.entities.SectorAnalysis
import kotlinx.coroutines.flow.Flow

@Dao
interface SectorAnalysisDao {

    // ==================== Insert ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(analysis: SectorAnalysis)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(analyses: List<SectorAnalysis>)

    // ==================== Query ====================

    /**
     * 특정 날짜의 모든 섹터 분석 결과 조회
     */
    @Query("SELECT * FROM sector_analysis WHERE date = :date ORDER BY fearGreedValue DESC")
    suspend fun getByDate(date: String): List<SectorAnalysis>

    /**
     * 특정 날짜의 모든 섹터 분석 결과 (Flow)
     */
    @Query("SELECT * FROM sector_analysis WHERE date = :date ORDER BY fearGreedValue DESC")
    fun observeByDate(date: String): Flow<List<SectorAnalysis>>

    /**
     * 특정 섹터의 분석 이력 조회
     */
    @Query("SELECT * FROM sector_analysis WHERE sector = :sector ORDER BY date DESC LIMIT :limit")
    suspend fun getBySector(sector: String, limit: Int = 30): List<SectorAnalysis>

    /**
     * 특정 섹터의 분석 이력 (Flow)
     */
    @Query("SELECT * FROM sector_analysis WHERE sector = :sector ORDER BY date DESC LIMIT :limit")
    fun observeBySector(sector: String, limit: Int = 30): Flow<List<SectorAnalysis>>

    /**
     * 특정 날짜, 특정 섹터의 분석 결과 조회
     */
    @Query("SELECT * FROM sector_analysis WHERE sector = :sector AND date = :date")
    suspend fun getBySectorAndDate(sector: String, date: String): SectorAnalysis?

    /**
     * 기간 내 섹터 분석 결과 조회
     */
    @Query("""
        SELECT * FROM sector_analysis
        WHERE date BETWEEN :startDate AND :endDate
        ORDER BY date DESC, fearGreedValue DESC
    """)
    suspend fun getByDateRange(startDate: String, endDate: String): List<SectorAnalysis>

    /**
     * 탐욕 상위 섹터 조회 (특정 날짜)
     */
    @Query("""
        SELECT * FROM sector_analysis
        WHERE date = :date AND fearGreedValue > 0.6
        ORDER BY fearGreedValue DESC
        LIMIT :limit
    """)
    suspend fun getTopGreedSectors(date: String, limit: Int = 5): List<SectorAnalysis>

    /**
     * 공포 상위 섹터 조회 (특정 날짜)
     */
    @Query("""
        SELECT * FROM sector_analysis
        WHERE date = :date AND fearGreedValue < 0.4
        ORDER BY fearGreedValue ASC
        LIMIT :limit
    """)
    suspend fun getTopFearSectors(date: String, limit: Int = 5): List<SectorAnalysis>

    /**
     * ETF 유입이 많은 섹터 조회 (특정 날짜)
     */
    @Query("""
        SELECT * FROM sector_analysis
        WHERE date = :date AND newEntries > 0
        ORDER BY newEntries DESC
        LIMIT :limit
    """)
    suspend fun getTopInflowSectors(date: String, limit: Int = 5): List<SectorAnalysis>

    /**
     * ETF 유출이 많은 섹터 조회 (특정 날짜)
     */
    @Query("""
        SELECT * FROM sector_analysis
        WHERE date = :date AND removals > 0
        ORDER BY removals DESC
        LIMIT :limit
    """)
    suspend fun getTopOutflowSectors(date: String, limit: Int = 5): List<SectorAnalysis>

    /**
     * 최근 분석 날짜 조회
     */
    @Query("SELECT MAX(date) FROM sector_analysis")
    suspend fun getLatestDate(): String?

    /**
     * 모든 고유 섹터 목록 조회
     */
    @Query("SELECT DISTINCT sector FROM sector_analysis ORDER BY sector")
    suspend fun getAllSectors(): List<String>

    /**
     * 섹터별 평균 Fear & Greed 값 조회 (기간)
     */
    @Query("""
        SELECT sector, AVG(fearGreedValue) as avgValue
        FROM sector_analysis
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY sector
        ORDER BY avgValue DESC
    """)
    suspend fun getAvgFearGreedBySector(startDate: String, endDate: String): List<SectorAvgValue>

    // ==================== Delete ====================

    @Query("DELETE FROM sector_analysis WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM sector_analysis WHERE date < :date")
    suspend fun deleteOldData(date: String)

    @Query("DELETE FROM sector_analysis")
    suspend fun deleteAll()

    // ==================== Count ====================

    @Query("SELECT COUNT(*) FROM sector_analysis")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(DISTINCT date) FROM sector_analysis")
    suspend fun getDateCount(): Int
}

/**
 * 섹터별 평균값 결과
 */
data class SectorAvgValue(
    val sector: String,
    val avgValue: Double
)
