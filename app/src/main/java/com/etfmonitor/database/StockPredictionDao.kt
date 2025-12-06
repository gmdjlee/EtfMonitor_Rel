package com.etfmonitor.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.etfmonitor.database.entities.PredictionSummary
import com.etfmonitor.database.entities.StockPrediction
import kotlinx.coroutines.flow.Flow

@Dao
interface StockPredictionDao {

    /**
     * 특정 날짜의 예측 결과 조회 (신뢰도 순)
     */
    @Query("""
        SELECT * FROM stock_predictions
        WHERE predictionDate = :date
        ORDER BY confidence DESC
    """)
    fun getPredictionsByDate(date: String): Flow<List<StockPrediction>>

    /**
     * 특정 날짜의 예측 결과 조회 (suspend)
     */
    @Query("""
        SELECT * FROM stock_predictions
        WHERE predictionDate = :date
        ORDER BY confidence DESC
    """)
    suspend fun getPredictionsByDateSuspend(date: String): List<StockPrediction>

    /**
     * 최신 예측 결과 조회
     */
    @Query("""
        SELECT * FROM stock_predictions
        WHERE predictionDate = (SELECT MAX(predictionDate) FROM stock_predictions)
        ORDER BY confidence DESC
    """)
    fun getLatestPredictions(): Flow<List<StockPrediction>>

    /**
     * 최신 예측 결과 조회 (suspend)
     */
    @Query("""
        SELECT * FROM stock_predictions
        WHERE predictionDate = (SELECT MAX(predictionDate) FROM stock_predictions)
        ORDER BY confidence DESC
    """)
    suspend fun getLatestPredictionsSuspend(): List<StockPrediction>

    /**
     * 특정 종목의 예측 이력 조회
     */
    @Query("""
        SELECT * FROM stock_predictions
        WHERE ticker = :ticker
        ORDER BY predictionDate DESC
        LIMIT :limit
    """)
    suspend fun getPredictionsByTicker(ticker: String, limit: Int = 30): List<StockPrediction>

    /**
     * 검증된 예측 결과만 조회 (실제 결과가 있는 것)
     */
    @Query("""
        SELECT * FROM stock_predictions
        WHERE wasCorrect IS NOT NULL
        ORDER BY predictionDate DESC
        LIMIT :limit
    """)
    suspend fun getVerifiedPredictions(limit: Int = 100): List<StockPrediction>

    /**
     * 모델 정확도 계산 (검증된 예측 기준)
     */
    @Query("""
        SELECT
            CAST(SUM(CASE WHEN wasCorrect = 1 THEN 1 ELSE 0 END) AS REAL) /
            CAST(COUNT(*) AS REAL) as accuracy
        FROM stock_predictions
        WHERE wasCorrect IS NOT NULL
    """)
    suspend fun getModelAccuracy(): Double?

    /**
     * 예측 날짜 목록 조회
     */
    @Query("""
        SELECT DISTINCT predictionDate
        FROM stock_predictions
        ORDER BY predictionDate DESC
        LIMIT :limit
    """)
    suspend fun getPredictionDates(limit: Int = 30): List<String>

    /**
     * 예측 결과 요약 조회
     */
    @Query("""
        SELECT
            predictionDate,
            COUNT(*) as totalPredictions,
            AVG(confidence) as avgConfidence,
            CAST(SUM(CASE WHEN wasCorrect = 1 THEN 1 ELSE 0 END) AS REAL) /
                NULLIF(SUM(CASE WHEN wasCorrect IS NOT NULL THEN 1 ELSE 0 END), 0) as modelAccuracy,
            SUM(CASE WHEN wasCorrect IS NOT NULL THEN 1 ELSE 0 END) as verifiedCount
        FROM stock_predictions
        GROUP BY predictionDate
        ORDER BY predictionDate DESC
        LIMIT :limit
    """)
    suspend fun getPredictionSummaries(limit: Int = 30): List<PredictionSummary>

    /**
     * 검증 대기 중인 예측 조회 (실제 결과 업데이트 필요)
     */
    @Query("""
        SELECT * FROM stock_predictions
        WHERE wasCorrect IS NULL
        AND date(predictionDate, '+' || daysAfter || ' days') <= date('now')
        ORDER BY predictionDate ASC
    """)
    suspend fun getPendingVerification(): List<StockPrediction>

    /**
     * 예측 저장 (새로 추가 또는 업데이트)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrediction(prediction: StockPrediction)

    /**
     * 여러 예측 저장
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPredictions(predictions: List<StockPrediction>)

    /**
     * 예측 결과 업데이트 (검증용)
     */
    @Update
    suspend fun updatePrediction(prediction: StockPrediction)

    /**
     * 실제 결과 업데이트
     */
    @Query("""
        UPDATE stock_predictions
        SET actualPriceChange = :actualChange,
            wasCorrect = CASE
                WHEN :actualChange >= priceThreshold THEN 1
                ELSE 0
            END
        WHERE id = :id
    """)
    suspend fun updateActualResult(id: String, actualChange: Double)

    /**
     * 특정 날짜의 예측 삭제
     */
    @Query("DELETE FROM stock_predictions WHERE predictionDate = :date")
    suspend fun deletePredictionsByDate(date: String)

    /**
     * 오래된 예측 삭제
     */
    @Query("DELETE FROM stock_predictions WHERE predictionDate < :beforeDate")
    suspend fun deletePredictionsBeforeDate(beforeDate: String)

    /**
     * 전체 예측 삭제
     */
    @Query("DELETE FROM stock_predictions")
    suspend fun deleteAllPredictions()

    /**
     * 예측 개수 조회
     */
    @Query("SELECT COUNT(*) FROM stock_predictions")
    suspend fun getPredictionCount(): Int

    /**
     * 최신 예측 날짜 조회
     */
    @Query("SELECT MAX(predictionDate) FROM stock_predictions")
    suspend fun getLatestPredictionDate(): String?
}
