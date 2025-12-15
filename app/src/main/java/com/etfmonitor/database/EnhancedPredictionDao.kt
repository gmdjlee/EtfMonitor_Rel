package com.etfmonitor.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.database.entities.EnhancedPrediction
import kotlinx.coroutines.flow.Flow

/**
 * 향상된 예측 결과 DAO
 * 28개 Feature 기반 앙상블 모델 예측 결과 관리
 */
@Dao
interface EnhancedPredictionDao {

    /**
     * 최신 예측 결과 조회 (Flow)
     */
    @Query("""
        SELECT * FROM enhanced_predictions
        WHERE predictionDate = (SELECT MAX(predictionDate) FROM enhanced_predictions)
        ORDER BY confidence DESC
        LIMIT 100
    """)
    fun getLatestPredictions(): Flow<List<EnhancedPrediction>>

    /**
     * 특정 날짜의 예측 결과 조회 (Flow)
     */
    @Query("""
        SELECT * FROM enhanced_predictions
        WHERE predictionDate = :date
        ORDER BY confidence DESC
        LIMIT 100
    """)
    fun getPredictionsByDate(date: String): Flow<List<EnhancedPrediction>>

    /**
     * 특정 종목의 예측 이력 조회
     */
    @Query("""
        SELECT * FROM enhanced_predictions
        WHERE ticker = :ticker
        ORDER BY predictionDate DESC
        LIMIT 30
    """)
    suspend fun getPredictionsByTicker(ticker: String): List<EnhancedPrediction>

    /**
     * 예측 결과 저장
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPredictions(predictions: List<EnhancedPrediction>)

    /**
     * 단일 예측 결과 저장
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prediction: EnhancedPrediction)

    /**
     * 예측 결과 업데이트 (실제 가격 변화 검증용)
     */
    @Query("""
        UPDATE enhanced_predictions
        SET actualPriceChange = :actualChange, wasCorrect = :wasCorrect
        WHERE id = :id
    """)
    suspend fun updateVerification(id: String, actualChange: Double, wasCorrect: Boolean)

    /**
     * 검증 대기 중인 예측 조회
     */
    @Query("""
        SELECT * FROM enhanced_predictions
        WHERE actualPriceChange IS NULL
        AND predictionDate < date('now', '-5 days')
        ORDER BY predictionDate ASC
        LIMIT 100
    """)
    suspend fun getPendingVerification(): List<EnhancedPrediction>

    /**
     * 모델 정확도 계산
     */
    @Query("""
        SELECT CAST(SUM(CASE WHEN wasCorrect = 1 THEN 1 ELSE 0 END) AS REAL) /
               CAST(COUNT(*) AS REAL)
        FROM enhanced_predictions
        WHERE wasCorrect IS NOT NULL
    """)
    suspend fun getModelAccuracy(): Double?

    /**
     * 모델별 정확도 조회
     */
    @Query("""
        SELECT modelType,
               CAST(SUM(CASE WHEN wasCorrect = 1 THEN 1 ELSE 0 END) AS REAL) / CAST(COUNT(*) AS REAL) as accuracy,
               COUNT(*) as totalCount
        FROM enhanced_predictions
        WHERE wasCorrect IS NOT NULL
        GROUP BY modelType
    """)
    suspend fun getAccuracyByModelType(): List<ModelAccuracyResult>

    /**
     * 예측 날짜 목록 조회
     */
    @Query("SELECT DISTINCT predictionDate FROM enhanced_predictions ORDER BY predictionDate DESC LIMIT :limit")
    suspend fun getPredictionDates(limit: Int = 30): List<String>

    /**
     * 오래된 예측 삭제
     */
    @Query("DELETE FROM enhanced_predictions WHERE predictionDate < :cutoffDate")
    suspend fun deletePredictionsBeforeDate(cutoffDate: String)

    /**
     * 모든 예측 삭제
     */
    @Query("DELETE FROM enhanced_predictions")
    suspend fun deleteAll()

    /**
     * 예측 개수 조회
     */
    @Query("SELECT COUNT(*) FROM enhanced_predictions")
    suspend fun getPredictionCount(): Int

    /**
     * 특정 신뢰도 이상의 예측 조회
     */
    @Query("""
        SELECT * FROM enhanced_predictions
        WHERE predictionDate = :date AND confidence >= :minConfidence
        ORDER BY confidence DESC
        LIMIT :limit
    """)
    suspend fun getHighConfidencePredictions(
        date: String,
        minConfidence: Double = 0.7,
        limit: Int = 50
    ): List<EnhancedPrediction>

    /**
     * 특정 상태의 예측 조회
     */
    @Query("""
        SELECT * FROM enhanced_predictions
        WHERE predictionDate = :date AND status = :status
        ORDER BY confidence DESC
        LIMIT :limit
    """)
    suspend fun getPredictionsByStatus(date: String, status: String, limit: Int = 50): List<EnhancedPrediction>
}

/**
 * 모델별 정확도 결과
 */
data class ModelAccuracyResult(
    val modelType: String,
    val accuracy: Double,
    val totalCount: Int
)
