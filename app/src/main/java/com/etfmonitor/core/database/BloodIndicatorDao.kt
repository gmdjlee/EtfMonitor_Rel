package com.etfmonitor.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.core.database.entities.BloodIndicator
import kotlinx.coroutines.flow.Flow

/**
 * Blood Indicator DAO
 * US Treasury 기반 시장 건강도 지표 데이터 액세스
 */
@Dao
interface BloodIndicatorDao {
    @Query("SELECT * FROM blood_indicator ORDER BY date DESC")
    fun getAll(): Flow<List<BloodIndicator>>

    @Query("SELECT * FROM blood_indicator WHERE date = :date")
    suspend fun getByDate(date: String): BloodIndicator?

    @Query("SELECT * FROM blood_indicator ORDER BY date DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<BloodIndicator>>

    @Query("SELECT * FROM blood_indicator WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun getByDateRange(startDate: String, endDate: String): Flow<List<BloodIndicator>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(indicators: List<BloodIndicator>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(indicator: BloodIndicator)

    @Query("DELETE FROM blood_indicator")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM blood_indicator")
    suspend fun getCount(): Int

    @Query("SELECT MAX(date) FROM blood_indicator")
    suspend fun getLatestDate(): String?

    @Query("SELECT MIN(date) FROM blood_indicator")
    suspend fun getEarliestDate(): String?

    @Query("SELECT MAX(lastUpdated) FROM blood_indicator")
    suspend fun getLastUpdateTime(): Long?
}
