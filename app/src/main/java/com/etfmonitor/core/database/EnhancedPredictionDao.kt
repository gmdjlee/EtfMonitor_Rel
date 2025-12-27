package com.etfmonitor.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.core.database.entities.EnhancedPrediction
import kotlinx.coroutines.flow.Flow

/**
 * 향상된 ML 예측 결과 DAO
 */
@Dao
interface EnhancedPredictionDao {
    @Query("SELECT * FROM enhanced_predictions ORDER BY predictionDate DESC, confidence DESC")
    fun getAllPredictions(): Flow<List<EnhancedPrediction>>

    @Query("SELECT * FROM enhanced_predictions WHERE predictionDate = :date ORDER BY confidence DESC")
    fun getPredictionsByDate(date: String): Flow<List<EnhancedPrediction>>

    @Query("SELECT * FROM enhanced_predictions WHERE ticker = :ticker ORDER BY predictionDate DESC")
    fun getPredictionsByTicker(ticker: String): Flow<List<EnhancedPrediction>>

    @Query("SELECT * FROM enhanced_predictions WHERE id = :id")
    suspend fun getPrediction(id: String): EnhancedPrediction?

    @Query("SELECT * FROM enhanced_predictions WHERE ticker = :ticker AND predictionDate = :date")
    suspend fun getPrediction(ticker: String, date: String): EnhancedPrediction?

    @Query("""
        SELECT * FROM enhanced_predictions
        WHERE predictionDate = (SELECT MAX(predictionDate) FROM enhanced_predictions)
        ORDER BY confidence DESC
        LIMIT :limit
    """)
    suspend fun getLatestPredictions(limit: Int = 100): List<EnhancedPrediction>

    @Query("""
        SELECT * FROM enhanced_predictions
        WHERE predictionDate = (SELECT MAX(predictionDate) FROM enhanced_predictions)
        AND status = :status
        ORDER BY confidence DESC
        LIMIT :limit
    """)
    suspend fun getLatestPredictionsByStatus(status: String, limit: Int = 100): List<EnhancedPrediction>

    @Query("SELECT MAX(predictionDate) FROM enhanced_predictions")
    suspend fun getLatestPredictionDate(): String?

    @Query("SELECT COUNT(*) FROM enhanced_predictions WHERE predictionDate = :date")
    suspend fun getPredictionCountByDate(date: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prediction: EnhancedPrediction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(predictions: List<EnhancedPrediction>)

    @Query("UPDATE enhanced_predictions SET actualPriceChange = :priceChange, wasCorrect = :wasCorrect WHERE id = :id")
    suspend fun updateActualResult(id: String, priceChange: Double, wasCorrect: Boolean)

    @Query("DELETE FROM enhanced_predictions WHERE predictionDate = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM enhanced_predictions WHERE predictionDate < :date")
    suspend fun deleteOldPredictions(date: String)

    @Query("DELETE FROM enhanced_predictions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM enhanced_predictions")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM enhanced_predictions WHERE wasCorrect = 1")
    suspend fun getCorrectCount(): Int

    @Query("SELECT AVG(CASE WHEN wasCorrect = 1 THEN 1.0 ELSE 0.0 END) FROM enhanced_predictions WHERE wasCorrect IS NOT NULL")
    suspend fun getAccuracy(): Double?
}
