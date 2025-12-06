package com.etfmonitor.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.database.entities.EtfCorrelationCache
import kotlinx.coroutines.flow.Flow

@Dao
interface EtfCorrelationDao {

    // ==================== Insert ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(correlation: EtfCorrelationCache)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(correlations: List<EtfCorrelationCache>)

    // ==================== Query ====================

    /**
     * 특정 날짜의 모든 상관관계 조회
     */
    @Query("SELECT * FROM etf_correlation_cache WHERE date = :date ORDER BY overlapRatio DESC")
    suspend fun getByDate(date: String): List<EtfCorrelationCache>

    /**
     * 특정 날짜의 모든 상관관계 (Flow)
     */
    @Query("SELECT * FROM etf_correlation_cache WHERE date = :date ORDER BY overlapRatio DESC")
    fun observeByDate(date: String): Flow<List<EtfCorrelationCache>>

    /**
     * 특정 ETF 쌍의 상관관계 조회
     */
    @Query("""
        SELECT * FROM etf_correlation_cache
        WHERE date = :date
          AND ((etf1Ticker = :etf1 AND etf2Ticker = :etf2)
               OR (etf1Ticker = :etf2 AND etf2Ticker = :etf1))
    """)
    suspend fun getByEtfPair(etf1: String, etf2: String, date: String): EtfCorrelationCache?

    /**
     * 특정 ETF가 포함된 모든 상관관계 조회
     */
    @Query("""
        SELECT * FROM etf_correlation_cache
        WHERE date = :date
          AND (etf1Ticker = :etfTicker OR etf2Ticker = :etfTicker)
        ORDER BY overlapRatio DESC
    """)
    suspend fun getByEtf(etfTicker: String, date: String): List<EtfCorrelationCache>

    /**
     * 높은 중복률 ETF 쌍 조회
     */
    @Query("""
        SELECT * FROM etf_correlation_cache
        WHERE date = :date AND overlapRatio >= :threshold
        ORDER BY overlapRatio DESC
        LIMIT :limit
    """)
    suspend fun getHighOverlapPairs(
        date: String,
        threshold: Double = 0.7,
        limit: Int = 20
    ): List<EtfCorrelationCache>

    /**
     * 낮은 상관관계(분산 효과 높음) ETF 쌍 조회
     */
    @Query("""
        SELECT * FROM etf_correlation_cache
        WHERE date = :date AND overlapRatio <= :threshold
        ORDER BY overlapRatio ASC
        LIMIT :limit
    """)
    suspend fun getLowCorrelationPairs(
        date: String,
        threshold: Double = 0.3,
        limit: Int = 20
    ): List<EtfCorrelationCache>

    /**
     * 특정 ETF들 간의 상관관계 매트릭스 조회
     */
    @Query("""
        SELECT * FROM etf_correlation_cache
        WHERE date = :date
          AND etf1Ticker IN (:etfTickers)
          AND etf2Ticker IN (:etfTickers)
    """)
    suspend fun getCorrelationMatrix(
        etfTickers: List<String>,
        date: String
    ): List<EtfCorrelationCache>

    /**
     * 상관관계 이력 조회 (특정 ETF 쌍)
     */
    @Query("""
        SELECT * FROM etf_correlation_cache
        WHERE ((etf1Ticker = :etf1 AND etf2Ticker = :etf2)
               OR (etf1Ticker = :etf2 AND etf2Ticker = :etf1))
        ORDER BY date DESC
        LIMIT :limit
    """)
    suspend fun getCorrelationHistory(
        etf1: String,
        etf2: String,
        limit: Int = 30
    ): List<EtfCorrelationCache>

    /**
     * 최근 상관관계 날짜 조회
     */
    @Query("SELECT MAX(date) FROM etf_correlation_cache")
    suspend fun getLatestDate(): String?

    /**
     * 고유 ETF 목록 조회 (상관관계에 포함된)
     */
    @Query("""
        SELECT DISTINCT etf1Ticker FROM etf_correlation_cache WHERE date = :date
        UNION
        SELECT DISTINCT etf2Ticker FROM etf_correlation_cache WHERE date = :date
    """)
    suspend fun getDistinctEtfs(date: String): List<String>

    /**
     * 평균 상관관계 조회 (특정 ETF)
     */
    @Query("""
        SELECT AVG(overlapRatio) FROM etf_correlation_cache
        WHERE date = :date
          AND (etf1Ticker = :etfTicker OR etf2Ticker = :etfTicker)
    """)
    suspend fun getAvgCorrelation(etfTicker: String, date: String): Double?

    // ==================== Delete ====================

    @Query("DELETE FROM etf_correlation_cache WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM etf_correlation_cache WHERE date < :date")
    suspend fun deleteOldData(date: String)

    @Query("DELETE FROM etf_correlation_cache")
    suspend fun deleteAll()

    // ==================== Count ====================

    @Query("SELECT COUNT(*) FROM etf_correlation_cache")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM etf_correlation_cache WHERE date = :date")
    suspend fun getCountByDate(date: String): Int
}
