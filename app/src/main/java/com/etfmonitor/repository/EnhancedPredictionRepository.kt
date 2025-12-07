package com.etfmonitor.repository

import com.etfmonitor.database.EtfDao
import com.etfmonitor.database.EnhancedPredictionDao
import com.etfmonitor.database.PriceCacheDao
import com.etfmonitor.database.entities.EnhancedPrediction
import com.etfmonitor.database.entities.EnhancedPredictionConfig
import com.etfmonitor.database.entities.StockChangeData
import com.etfmonitor.database.entities.StockChangeInfo
import com.etfmonitor.python.EnhancedPredictionResponse
import com.etfmonitor.python.EnhancedPredictorClient
import com.etfmonitor.python.EnhancedModelStatus
import com.etfmonitor.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 향상된 ML 예측 Repository
 * 28개 Feature와 앙상블 모델을 사용한 예측 관리
 * 기존 StockPredictionRepository와 병행 운영 (하위 호환)
 */
@Singleton
class EnhancedPredictionRepository @Inject constructor(
    private val enhancedPredictionDao: EnhancedPredictionDao,
    private val priceCacheDao: PriceCacheDao,
    private val etfDao: EtfDao,
    private val enhancedPredictorClient: EnhancedPredictorClient
) {
    companion object {
        private val logger = AppLogger.getLogger("EnhancedPredictionRepo")
        private const val MIN_TRAINING_SAMPLES = 20
        private const val DEFAULT_HISTORICAL_DAYS = 60
    }

    /**
     * 최신 향상된 예측 결과 조회 (Flow)
     */
    fun getLatestPredictions(): Flow<List<EnhancedPrediction>> {
        return enhancedPredictionDao.getLatestPredictions()
    }

    /**
     * 특정 날짜의 향상된 예측 결과 조회 (Flow)
     */
    fun getPredictionsByDate(date: String): Flow<List<EnhancedPrediction>> {
        return enhancedPredictionDao.getPredictionsByDate(date)
    }

    /**
     * 최신 향상된 예측 결과 조회 (suspend)
     */
    suspend fun getLatestPredictionsSuspend(): List<EnhancedPrediction> = withContext(Dispatchers.IO) {
        enhancedPredictionDao.getLatestPredictionsSuspend()
    }

    /**
     * 향상된 ML 예측 실행
     *
     * @param config 예측 설정
     * @return 예측 응답
     */
    suspend fun runEnhancedPrediction(
        config: EnhancedPredictionConfig = EnhancedPredictionConfig()
    ): EnhancedPredictionResponse = withContext(Dispatchers.IO) {
        try {
            logger.d("Starting enhanced prediction with config: $config")

            // 1. 날짜 정보 수집
            val dates = etfDao.getLatestTwoDates()
            if (dates.size < 2) {
                logger.e("Not enough dates for enhanced prediction")
                return@withContext EnhancedPredictionResponse(
                    success = false,
                    errorMessage = "데이터가 부족합니다. 최소 2일 이상의 ETF 데이터가 필요합니다.",
                    predictions = emptyList()
                )
            }

            val currentDate = dates[0]
            val previousDate = dates[1]

            logger.d("Enhanced prediction dates: current=$currentDate, previous=$previousDate")

            // 2. 과거 학습 데이터 수집
            val historicalChanges = collectHistoricalChanges(DEFAULT_HISTORICAL_DAYS)
            if (historicalChanges.size < MIN_TRAINING_SAMPLES) {
                logger.w("Not enough training data: ${historicalChanges.size}")
                return@withContext EnhancedPredictionResponse(
                    success = false,
                    errorMessage = "학습 데이터가 부족합니다. ${historicalChanges.size}개 샘플 (최소 ${MIN_TRAINING_SAMPLES}개 필요)",
                    predictions = emptyList()
                )
            }

            // 3. 현재 예측 대상 데이터 수집
            val currentChanges = collectCurrentChanges(currentDate, previousDate)
            if (currentChanges.isEmpty()) {
                logger.w("No current changes to predict")
                return@withContext EnhancedPredictionResponse(
                    success = false,
                    errorMessage = "예측할 종목 변화 데이터가 없습니다.",
                    predictions = emptyList()
                )
            }

            logger.d("Enhanced samples: historical=${historicalChanges.size}, current=${currentChanges.size}")

            // 4. 향상된 ML 모델 학습 및 예측
            val response = enhancedPredictorClient.trainAndPredict(
                historicalChanges = historicalChanges,
                currentChanges = currentChanges,
                config = config
            )

            // 5. 예측 결과 저장
            if (response.success && response.predictions.isNotEmpty()) {
                enhancedPredictionDao.insertPredictions(response.predictions)
                logger.d("Saved ${response.predictions.size} enhanced predictions")
            }

            response
        } catch (e: Exception) {
            logger.e("Error running enhanced prediction", e)
            EnhancedPredictionResponse(
                success = false,
                errorMessage = "예측 실행 중 오류: ${e.message}",
                predictions = emptyList()
            )
        }
    }

    /**
     * 고신뢰도 예측 조회
     */
    suspend fun getHighConfidencePredictions(
        minConfidence: Double = 0.7,
        limit: Int = 50
    ): List<EnhancedPrediction> = withContext(Dispatchers.IO) {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        enhancedPredictionDao.getHighConfidencePredictions(today, minConfidence, limit)
    }

    /**
     * 특정 상태의 예측 조회
     */
    suspend fun getPredictionsByStatus(
        status: String,
        limit: Int = 50
    ): List<EnhancedPrediction> = withContext(Dispatchers.IO) {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        enhancedPredictionDao.getPredictionsByStatus(today, status, limit)
    }

    /**
     * 과거 종목 변화 데이터 수집 (학습용)
     */
    private suspend fun collectHistoricalChanges(days: Int): List<StockChangeData> {
        val changes = mutableListOf<StockChangeData>()

        try {
            val allDates = getAllAvailableDates()
            if (allDates.size < 2) return changes

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

            logger.d("Collected ${changes.size} historical changes from $days days")
        } catch (e: Exception) {
            logger.e("Error collecting historical changes", e)
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

            // 비중 감소 종목
            val decreasedStocks = etfDao.getAllDecreasedStocks(currentDate, previousDate)
            changes.addAll(decreasedStocks.map { it.toStockChangeData("DECREASED", currentDate) })

            logger.d("Collected ${changes.size} current changes for enhanced prediction")
        } catch (e: Exception) {
            logger.e("Error collecting current changes", e)
        }

        return changes.distinctBy { "${it.ticker}-${it.status}" }
    }

    /**
     * 사용 가능한 모든 날짜 조회
     */
    private suspend fun getAllAvailableDates(): List<String> {
        return try {
            val dates = etfDao.getAllDistinctDates(100)
            logger.d("Available dates: ${dates.size} dates found")
            dates
        } catch (e: Exception) {
            logger.e("Error getting available dates", e)
            emptyList()
        }
    }

    /**
     * 예측 결과 검증 (실제 주가 변화와 비교)
     */
    suspend fun verifyPredictions(): Int = withContext(Dispatchers.IO) {
        try {
            val pendingPredictions = enhancedPredictionDao.getPendingVerification()
            logger.d("Verifying ${pendingPredictions.size} enhanced predictions")

            var verifiedCount = 0
            // 향후 구현: 실제 주가 변화율 조회하여 검증
            verifiedCount
        } catch (e: Exception) {
            logger.e("Error verifying enhanced predictions", e)
            0
        }
    }

    /**
     * 모델 정확도 조회
     */
    suspend fun getModelAccuracy(): Double? = withContext(Dispatchers.IO) {
        enhancedPredictionDao.getModelAccuracy()
    }

    /**
     * 모델별 정확도 조회
     */
    suspend fun getAccuracyByModelType() = withContext(Dispatchers.IO) {
        enhancedPredictionDao.getAccuracyByModelType()
    }

    /**
     * 예측 날짜 목록 조회
     */
    suspend fun getPredictionDates(limit: Int = 30): List<String> = withContext(Dispatchers.IO) {
        enhancedPredictionDao.getPredictionDates(limit)
    }

    /**
     * 특정 종목의 예측 이력 조회
     */
    suspend fun getPredictionsByTicker(ticker: String): List<EnhancedPrediction> = withContext(Dispatchers.IO) {
        enhancedPredictionDao.getPredictionsByTicker(ticker)
    }

    /**
     * 오래된 예측 삭제
     */
    suspend fun cleanupOldPredictions(keepDays: Int = 90) = withContext(Dispatchers.IO) {
        try {
            val cutoffDate = LocalDate.now().minusDays(keepDays.toLong())
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
            enhancedPredictionDao.deletePredictionsBeforeDate(cutoffDate)
            logger.d("Cleaned up enhanced predictions before $cutoffDate")
        } catch (e: Exception) {
            logger.e("Error cleaning up enhanced predictions", e)
        }
    }

    /**
     * 가격 캐시 정리
     */
    suspend fun cleanupPriceCache(daysToKeep: Int = 7) = withContext(Dispatchers.IO) {
        try {
            val cutoff = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
            priceCacheDao.deleteOldCache(cutoff)
            logger.d("Cleaned up price cache older than $daysToKeep days")
        } catch (e: Exception) {
            logger.e("Error cleaning up price cache", e)
        }
    }

    /**
     * 모델 상태 조회
     */
    suspend fun getModelStatus(): EnhancedModelStatus = withContext(Dispatchers.IO) {
        enhancedPredictorClient.getModelStatus()
    }

    /**
     * 모델 캐시 초기화
     */
    suspend fun clearModelCache(): Boolean = withContext(Dispatchers.IO) {
        enhancedPredictorClient.clearModelCache()
    }

    // ========== 헬퍼 함수 ==========

    private fun StockChangeInfo.toStockChangeData(status: String, date: String): StockChangeData {
        return StockChangeData(
            ticker = stockTicker,
            name = stockName,
            status = status,
            weightChange = change.toDouble(),
            etfCount = 1,
            totalAmount = currentAmount.toLong(),
            date = date
        )
    }
}
