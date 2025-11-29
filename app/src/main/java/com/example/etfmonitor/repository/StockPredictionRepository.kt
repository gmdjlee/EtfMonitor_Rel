package com.etfmonitor.repository

import android.util.Log
import com.etfmonitor.database.EtfDao
import com.etfmonitor.database.StockPredictionDao
import com.etfmonitor.database.entities.StockChangeData
import com.etfmonitor.database.entities.StockChangeInfo
import com.etfmonitor.database.entities.StockPrediction
import com.etfmonitor.database.entities.TrainingResult
import com.etfmonitor.python.PredictionResponse
import com.etfmonitor.python.StockPredictorPyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML 기반 주가 예측 Repository
 * ETF 구성 변화 데이터로 학습하고 상승 예상 종목 예측
 */
@Singleton
class StockPredictionRepository @Inject constructor(
    private val predictionDao: StockPredictionDao,
    private val etfDao: EtfDao,
    private val predictorClient: StockPredictorPyClient
) {
    companion object {
        private const val TAG = "StockPredictionRepo"
        private const val MIN_TRAINING_SAMPLES = 50
        private const val DEFAULT_DAYS_AFTER = 5
        private const val DEFAULT_PRICE_THRESHOLD = 3.0
        private const val DEFAULT_MIN_CONFIDENCE = 0.6
    }

    /**
     * 최신 예측 결과 조회 (Flow)
     */
    fun getLatestPredictions(): Flow<List<StockPrediction>> {
        return predictionDao.getLatestPredictions()
    }

    /**
     * 특정 날짜의 예측 결과 조회 (Flow)
     */
    fun getPredictionsByDate(date: String): Flow<List<StockPrediction>> {
        return predictionDao.getPredictionsByDate(date)
    }

    /**
     * 최신 예측 결과 조회 (suspend)
     */
    suspend fun getLatestPredictionsSuspend(): List<StockPrediction> = withContext(Dispatchers.IO) {
        predictionDao.getLatestPredictionsSuspend()
    }

    /**
     * ML 모델 학습 및 예측 실행
     *
     * @param daysAfter 예측 기간 (기본 5일 후)
     * @param priceThreshold 상승 판단 기준 (기본 3%)
     * @param minConfidence 최소 신뢰도 (기본 0.6)
     * @return 예측 응답
     */
    suspend fun runPrediction(
        daysAfter: Int = DEFAULT_DAYS_AFTER,
        priceThreshold: Double = DEFAULT_PRICE_THRESHOLD,
        minConfidence: Double = DEFAULT_MIN_CONFIDENCE
    ): PredictionResponse = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting prediction with daysAfter=$daysAfter, threshold=$priceThreshold")

            // 1. 날짜 정보 수집
            val dates = etfDao.getLatestTwoDates()
            if (dates.size < 2) {
                Log.e(TAG, "Not enough dates for prediction")
                return@withContext PredictionResponse(
                    success = false,
                    errorMessage = "데이터가 부족합니다. 최소 2일 이상의 ETF 데이터가 필요합니다.",
                    predictions = emptyList()
                )
            }

            val currentDate = dates[0]
            val previousDate = dates[1]

            Log.d(TAG, "Dates: current=$currentDate, previous=$previousDate")

            // 2. 과거 학습 데이터 수집 (최근 60일 데이터)
            val historicalChanges = collectHistoricalChanges(60)
            if (historicalChanges.size < MIN_TRAINING_SAMPLES) {
                Log.w(TAG, "Not enough training data: ${historicalChanges.size}")
                return@withContext PredictionResponse(
                    success = false,
                    errorMessage = "학습 데이터가 부족합니다. ${historicalChanges.size}개 샘플 (최소 ${MIN_TRAINING_SAMPLES}개 필요)",
                    predictions = emptyList()
                )
            }

            // 3. 현재 예측 대상 데이터 수집
            val currentChanges = collectCurrentChanges(currentDate, previousDate)
            if (currentChanges.isEmpty()) {
                Log.w(TAG, "No current changes to predict")
                return@withContext PredictionResponse(
                    success = false,
                    errorMessage = "예측할 종목 변화 데이터가 없습니다.",
                    predictions = emptyList()
                )
            }

            Log.d(TAG, "Historical samples: ${historicalChanges.size}, Current changes: ${currentChanges.size}")

            // 4. ML 모델 학습 및 예측
            val response = predictorClient.trainAndPredict(
                historicalChanges = historicalChanges,
                currentChanges = currentChanges,
                daysAfter = daysAfter,
                priceThreshold = priceThreshold,
                minConfidence = minConfidence
            )

            // 5. 예측 결과 저장
            if (response.success && response.predictions.isNotEmpty()) {
                predictionDao.insertPredictions(response.predictions)
                Log.d(TAG, "Saved ${response.predictions.size} predictions")
            }

            response
        } catch (e: Exception) {
            Log.e(TAG, "Error running prediction", e)
            PredictionResponse(
                success = false,
                errorMessage = "예측 실행 중 오류: ${e.message}",
                predictions = emptyList()
            )
        }
    }

    /**
     * 과거 종목 변화 데이터 수집 (학습용)
     */
    private suspend fun collectHistoricalChanges(days: Int): List<StockChangeData> {
        val changes = mutableListOf<StockChangeData>()

        try {
            // 모든 가용 날짜 조회
            val allDates = getAllAvailableDates()
            if (allDates.size < 2) return changes

            // 최근 N일 동안의 날짜 쌍으로 변화 데이터 수집
            val datesToProcess = allDates.take(days.coerceAtMost(allDates.size))

            for (i in 0 until datesToProcess.size - 1) {
                val currentDate = datesToProcess[i]
                val previousDate = datesToProcess[i + 1]

                // 신규 편입 종목
                val newStocks = etfDao.getAllNewStocks(currentDate, previousDate)
                changes.addAll(newStocks.map { it.toStockChangeData("NEW", currentDate) })

                // 비중 증가 종목
                val increasedStocks = etfDao.getAllIncreasedStocks(currentDate, previousDate)
                changes.addAll(increasedStocks.map { it.toStockChangeData("INCREASED", currentDate) })

                // 비중 감소 종목
                val decreasedStocks = etfDao.getAllDecreasedStocks(currentDate, previousDate)
                changes.addAll(decreasedStocks.map { it.toStockChangeData("DECREASED", currentDate) })

                // 제외 종목
                val removedStocks = etfDao.getAllRemovedStocks(currentDate, previousDate)
                changes.addAll(removedStocks.map { it.toStockChangeData("REMOVED", currentDate) })
            }

            Log.d(TAG, "Collected ${changes.size} historical changes from $days days")
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting historical changes", e)
        }

        return changes.distinctBy { "${it.ticker}-${it.date}-${it.status}" }
    }

    /**
     * 현재 종목 변화 데이터 수집 (예측용)
     */
    private suspend fun collectCurrentChanges(
        currentDate: String,
        previousDate: String
    ): List<StockChangeData> {
        val changes = mutableListOf<StockChangeData>()

        try {
            // 신규 편입 종목
            val newStocks = etfDao.getAllNewStocks(currentDate, previousDate)
            changes.addAll(newStocks.map { it.toStockChangeData("NEW", currentDate) })

            // 비중 증가 종목
            val increasedStocks = etfDao.getAllIncreasedStocks(currentDate, previousDate)
            changes.addAll(increasedStocks.map { it.toStockChangeData("INCREASED", currentDate) })

            // 비중 감소 종목 (상승 가능성 낮으나 분석에 포함)
            val decreasedStocks = etfDao.getAllDecreasedStocks(currentDate, previousDate)
            changes.addAll(decreasedStocks.map { it.toStockChangeData("DECREASED", currentDate) })

            Log.d(TAG, "Collected ${changes.size} current changes for prediction")
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting current changes", e)
        }

        return changes.distinctBy { "${it.ticker}-${it.status}" }
    }

    /**
     * 사용 가능한 모든 날짜 조회
     */
    private suspend fun getAllAvailableDates(): List<String> {
        return try {
            // Holdings 테이블에서 날짜 목록 조회
            val dates = mutableListOf<String>()
            val latestDates = etfDao.getLatestTwoDates()

            // 더 많은 날짜를 가져오기 위해 확장 (최대 100일)
            // Note: 실제로는 별도 쿼리가 필요할 수 있음
            dates.addAll(latestDates)

            // 날짜 내림차순 정렬
            dates.sortedDescending()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available dates", e)
            emptyList()
        }
    }

    /**
     * 예측 결과 검증 (실제 주가 변화와 비교)
     */
    suspend fun verifyPredictions(): Int = withContext(Dispatchers.IO) {
        try {
            val pendingPredictions = predictionDao.getPendingVerification()
            Log.d(TAG, "Verifying ${pendingPredictions.size} predictions")

            var verifiedCount = 0
            for (prediction in pendingPredictions) {
                // 예측 검증: 실제 주가 변화와 비교하여 모델 정확도 측정
                // 향후 구현 예정: PyKrx를 통해 실제 주가 변화율을 조회하여 예측 정확도 검증
                verifiedCount++
            }

            verifiedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying predictions", e)
            0
        }
    }

    /**
     * 모델 정확도 조회
     */
    suspend fun getModelAccuracy(): Double? = withContext(Dispatchers.IO) {
        predictionDao.getModelAccuracy()
    }

    /**
     * 예측 날짜 목록 조회
     */
    suspend fun getPredictionDates(limit: Int = 30): List<String> = withContext(Dispatchers.IO) {
        predictionDao.getPredictionDates(limit)
    }

    /**
     * 특정 종목의 예측 이력 조회
     */
    suspend fun getPredictionsByTicker(ticker: String): List<StockPrediction> = withContext(Dispatchers.IO) {
        predictionDao.getPredictionsByTicker(ticker)
    }

    /**
     * 예측 요약 정보 조회
     */
    suspend fun getPredictionSummaries(limit: Int = 30) = withContext(Dispatchers.IO) {
        predictionDao.getPredictionSummaries(limit)
    }

    /**
     * 오래된 예측 삭제
     */
    suspend fun cleanupOldPredictions(keepDays: Int = 90) = withContext(Dispatchers.IO) {
        try {
            val cutoffDate = LocalDate.now().minusDays(keepDays.toLong())
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
            predictionDao.deletePredictionsBeforeDate(cutoffDate)
            Log.d(TAG, "Cleaned up predictions before $cutoffDate")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up predictions", e)
        }
    }

    // ========== 헬퍼 함수 ==========

    private fun StockChangeInfo.toStockChangeData(status: String, date: String): StockChangeData {
        return StockChangeData(
            ticker = stockTicker,
            name = stockName,
            status = status,
            weightChange = change.toDouble(),
            etfCount = 1,  // 단일 ETF 변화이므로 1
            totalAmount = currentAmount.toLong(),
            date = date
        )
    }
}
