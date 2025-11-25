package com.etfmonitor.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.database.entities.DailyEtfStatistics
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyEtfStatisticsDao {
    /**
     * 모든 일별 통계 조회 (날짜 역순)
     */
    @Query("SELECT * FROM daily_etf_statistics ORDER BY date DESC")
    fun getAll(): Flow<List<DailyEtfStatistics>>

    /**
     * 특정 날짜의 통계 조회
     */
    @Query("SELECT * FROM daily_etf_statistics WHERE date = :date")
    suspend fun getByDate(date: String): DailyEtfStatistics?

    /**
     * 최근 N일의 통계 조회
     */
    @Query("SELECT * FROM daily_etf_statistics ORDER BY date DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<DailyEtfStatistics>>

    /**
     * 기간별 통계 조회
     */
    @Query("SELECT * FROM daily_etf_statistics WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun getByDateRange(startDate: String, endDate: String): Flow<List<DailyEtfStatistics>>

    /**
     * 기간별 통계 조회 (suspend)
     */
    @Query("SELECT * FROM daily_etf_statistics WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    suspend fun getByDateRangeSuspend(startDate: String, endDate: String): List<DailyEtfStatistics>

    /**
     * 데이터 삽입/업데이트
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(statistics: List<DailyEtfStatistics>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(statistic: DailyEtfStatistics)

    /**
     * 모든 데이터 삭제
     */
    @Query("DELETE FROM daily_etf_statistics")
    suspend fun deleteAll()

    /**
     * 특정 날짜 이전 데이터 삭제
     */
    @Query("DELETE FROM daily_etf_statistics WHERE date < :beforeDate")
    suspend fun deleteBeforeDate(beforeDate: String)

    /**
     * 데이터 개수
     */
    @Query("SELECT COUNT(*) FROM daily_etf_statistics")
    suspend fun getCount(): Int

    /**
     * 최신 날짜
     */
    @Query("SELECT MAX(date) FROM daily_etf_statistics")
    suspend fun getLatestDate(): String?

    /**
     * 최종 업데이트 시간
     */
    @Query("SELECT MAX(lastUpdated) FROM daily_etf_statistics")
    suspend fun getLastUpdateTime(): Long?

    /**
     * 모든 날짜 목록 조회
     */
    @Query("SELECT DISTINCT date FROM daily_etf_statistics ORDER BY date DESC")
    suspend fun getAllDates(): List<String>

    /**
     * 특정 기간의 평균 통계 계산
     */
    @Query("""
        SELECT
            AVG(newStockCount) as avgNewCount,
            AVG(removedStockCount) as avgRemovedCount,
            AVG(increasedStockCount) as avgIncreasedCount,
            AVG(decreasedStockCount) as avgDecreasedCount,
            AVG(cashDepositAmount) as avgCashDeposit
        FROM daily_etf_statistics
        WHERE date >= :startDate AND date <= :endDate
    """)
    suspend fun getAverageStatistics(startDate: String, endDate: String): AverageStatistics?
}

/**
 * 평균 통계 데이터 클래스
 */
data class AverageStatistics(
    val avgNewCount: Double,
    val avgRemovedCount: Double,
    val avgIncreasedCount: Double,
    val avgDecreasedCount: Double,
    val avgCashDeposit: Double
)
