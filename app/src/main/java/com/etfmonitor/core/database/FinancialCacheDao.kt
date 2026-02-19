package com.etfmonitor.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.core.database.entities.FinancialCache

@Dao
interface FinancialCacheDao {
    @Query("SELECT * FROM financial_cache WHERE ticker = :ticker")
    suspend fun get(ticker: String): FinancialCache?

    @Query("SELECT * FROM financial_cache")
    suspend fun getAllOnce(): List<FinancialCache>

    @Query("SELECT * FROM financial_cache WHERE cachedAt BETWEEN :startMs AND :endMs")
    suspend fun getInDateRange(startMs: Long, endMs: Long): List<FinancialCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: FinancialCache)

    @Query("DELETE FROM financial_cache WHERE ticker = :ticker")
    suspend fun delete(ticker: String)

    @Query("DELETE FROM financial_cache WHERE cachedAt < :threshold")
    suspend fun deleteExpired(threshold: Long)

    @Query("DELETE FROM financial_cache")
    suspend fun deleteAll()
}
