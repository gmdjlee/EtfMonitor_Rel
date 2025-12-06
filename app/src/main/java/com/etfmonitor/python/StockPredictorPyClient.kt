package com.etfmonitor.python

import android.content.Context
import com.chaquo.python.Python
import com.etfmonitor.utils.AppLogger
import com.etfmonitor.database.entities.StockChangeData
import com.etfmonitor.database.entities.StockPrediction
import com.etfmonitor.database.entities.TrainingResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ETF 구성 변화 기반 주가 상승 예측을 위한 Python 클라이언트
 * stock_predictor.py 모듈 사용
 */
@Singleton
class StockPredictorPyClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val logger = AppLogger.getLogger("StockPredictorPy")
        private const val TIMEOUT_MS = 120_000L  // ML 학습은 오래 걸릴 수 있음
    }

    private val python = Python.getInstance()
    private val module = python.getModule("stock_predictor")
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * ML 모델 학습 및 예측 수행
     *
     * @param historicalChanges 과거 종목 변화 데이터 (학습용)
     * @param currentChanges 현재 종목 변화 데이터 (예측용)
     * @param daysAfter 예측 기간 (기본 5일)
     * @param priceThreshold 상승 판단 기준 (기본 3%)
     * @param modelType 모델 타입 ("random_forest" 또는 "gradient_boosting")
     * @param minConfidence 최소 신뢰도 (기본 0.6)
     * @return 예측 결과 리스트
     */
    suspend fun trainAndPredict(
        historicalChanges: List<StockChangeData>,
        currentChanges: List<StockChangeData>,
        daysAfter: Int = 5,
        priceThreshold: Double = 3.0,
        modelType: String = "random_forest",
        minConfidence: Double = 0.6
    ): PredictionResponse = withContext(Dispatchers.IO) {
        try {
            logger.d( "Training model with ${historicalChanges.size} historical samples")
            logger.d( "Predicting for ${currentChanges.size} current changes")

            withTimeout(TIMEOUT_MS) {
                val historicalJson = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(StockChangeDataDto.serializer()),
                    historicalChanges.map { it.toDto() }
                )
                val currentJson = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(StockChangeDataDto.serializer()),
                    currentChanges.map { it.toDto() }
                )

                val result = module.callAttr(
                    "train_and_predict",
                    historicalJson,
                    currentJson,
                    daysAfter,
                    priceThreshold,
                    modelType,
                    minConfidence
                ).toString()

                val response = json.decodeFromString<PredictionResponseDto>(result)

                if (!response.success) {
                    logger.e( "Prediction failed: ${response.error}")
                    return@withTimeout PredictionResponse(
                        success = false,
                        errorMessage = response.error,
                        predictions = emptyList()
                    )
                }

                val predictions = response.predictions.map { dto ->
                    StockPrediction(
                        id = "${dto.ticker}-${LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}",
                        ticker = dto.ticker,
                        name = dto.name,
                        predictionDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                        status = dto.status,
                        confidence = dto.confidence,
                        weightChange = dto.weight_change,
                        etfCount = dto.etf_count,
                        amountBillion = dto.amount_billion,
                        daysAfter = daysAfter,
                        priceThreshold = priceThreshold,
                        modelType = modelType
                    )
                }

                logger.d( "Successfully predicted ${predictions.size} rising stocks")

                PredictionResponse(
                    success = true,
                    predictions = predictions,
                    trainingResult = response.training?.let { training ->
                        TrainingResult(
                            success = true,
                            modelType = modelType,
                            sampleCount = training.sample_count ?: 0,
                            accuracy = training.accuracy ?: 0.0,
                            precision = training.precision ?: 0.0,
                            recall = training.recall ?: 0.0,
                            featureImportance = training.feature_importance ?: emptyMap()
                        )
                    },
                    totalAnalyzed = response.prediction?.total_analyzed ?: 0,
                    predictedCount = response.prediction?.predicted_rising_count ?: 0
                )
            }
        } catch (e: Exception) {
            logger.e( "Error in trainAndPredict", e)
            PredictionResponse(
                success = false,
                errorMessage = e.message ?: "Unknown error",
                predictions = emptyList()
            )
        }
    }

    /**
     * 모델만 학습 (캐시에 저장)
     */
    suspend fun trainModel(
        historicalChanges: List<StockChangeData>,
        daysAfter: Int = 5,
        priceThreshold: Double = 3.0,
        modelType: String = "random_forest"
    ): TrainingResult = withContext(Dispatchers.IO) {
        try {
            logger.d( "Training model with ${historicalChanges.size} samples")

            withTimeout(TIMEOUT_MS) {
                val historicalJson = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(StockChangeDataDto.serializer()),
                    historicalChanges.map { it.toDto() }
                )

                val result = module.callAttr(
                    "train_model",
                    historicalJson,
                    daysAfter,
                    priceThreshold,
                    modelType
                ).toString()

                val response = json.decodeFromString<TrainResultDto>(result)

                if (!response.success) {
                    logger.e( "Training failed: ${response.error}")
                    return@withTimeout TrainingResult(
                        success = false,
                        modelType = modelType,
                        sampleCount = response.sample_count ?: 0,
                        accuracy = 0.0,
                        precision = 0.0,
                        recall = 0.0,
                        featureImportance = emptyMap(),
                        errorMessage = response.error
                    )
                }

                logger.d( "Model trained successfully. Accuracy: ${response.accuracy}")

                TrainingResult(
                    success = true,
                    modelType = modelType,
                    sampleCount = response.sample_count ?: 0,
                    accuracy = response.accuracy ?: 0.0,
                    precision = response.precision ?: 0.0,
                    recall = response.recall ?: 0.0,
                    featureImportance = response.feature_importance ?: emptyMap()
                )
            }
        } catch (e: Exception) {
            logger.e( "Error training model", e)
            TrainingResult(
                success = false,
                modelType = modelType,
                sampleCount = 0,
                accuracy = 0.0,
                precision = 0.0,
                recall = 0.0,
                featureImportance = emptyMap(),
                errorMessage = e.message
            )
        }
    }

    /**
     * 학습된 모델로 예측만 수행
     */
    suspend fun predict(
        currentChanges: List<StockChangeData>,
        daysAfter: Int = 5,
        priceThreshold: Double = 3.0,
        modelType: String = "random_forest",
        minConfidence: Double = 0.6
    ): List<StockPrediction> = withContext(Dispatchers.IO) {
        try {
            logger.d( "Predicting for ${currentChanges.size} changes")

            withTimeout(TIMEOUT_MS) {
                val currentJson = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(StockChangeDataDto.serializer()),
                    currentChanges.map { it.toDto() }
                )

                val result = module.callAttr(
                    "predict_rising_stocks",
                    currentJson,
                    daysAfter,
                    priceThreshold,
                    modelType,
                    minConfidence
                ).toString()

                val response = json.decodeFromString<PredictResultDto>(result)

                if (!response.success) {
                    logger.e( "Prediction failed: ${response.error}")
                    return@withTimeout emptyList()
                }

                response.predictions.map { dto ->
                    StockPrediction(
                        id = "${dto.ticker}-${LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}",
                        ticker = dto.ticker,
                        name = dto.name,
                        predictionDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                        status = dto.status,
                        confidence = dto.confidence,
                        weightChange = dto.weight_change,
                        etfCount = dto.etf_count,
                        amountBillion = dto.amount_billion,
                        daysAfter = daysAfter,
                        priceThreshold = priceThreshold,
                        modelType = modelType
                    )
                }
            }
        } catch (e: Exception) {
            logger.e( "Error predicting", e)
            emptyList()
        }
    }

    /**
     * 모델 캐시 상태 확인
     */
    suspend fun getModelStatus(): ModelStatus = withContext(Dispatchers.IO) {
        try {
            val result = module.callAttr("get_model_status").toString()
            val status = json.decodeFromString<ModelStatusDto>(result)
            ModelStatus(
                cachedModels = status.cached_models,
                modelCount = status.model_count
            )
        } catch (e: Exception) {
            logger.e( "Error getting model status", e)
            ModelStatus(emptyList(), 0)
        }
    }

    /**
     * 모델 캐시 초기화
     */
    suspend fun clearModelCache(): Boolean = withContext(Dispatchers.IO) {
        try {
            module.callAttr("clear_model_cache")
            true
        } catch (e: Exception) {
            logger.e( "Error clearing model cache", e)
            false
        }
    }

    // ========== 내부 DTO 클래스들 ==========

    @Serializable
    private data class StockChangeDataDto(
        val ticker: String,
        val name: String,
        val status: String,
        val weight_change: Double,
        val etf_count: Int,
        val total_amount: Long,
        val date: String
    )

    private fun StockChangeData.toDto() = StockChangeDataDto(
        ticker = ticker,
        name = name,
        status = status,
        weight_change = weightChange,
        etf_count = etfCount,
        total_amount = totalAmount,
        date = date
    )

    @Serializable
    private data class PredictionResponseDto(
        val success: Boolean,
        val error: String? = null,
        val training: TrainingDto? = null,
        val prediction: PredictionMetaDto? = null,
        val predictions: List<PredictionDto> = emptyList()
    )

    @Serializable
    private data class TrainingDto(
        val accuracy: Double? = null,
        val precision: Double? = null,
        val recall: Double? = null,
        val sample_count: Int? = null,
        val feature_importance: Map<String, Double>? = null
    )

    @Serializable
    private data class PredictionMetaDto(
        val total_analyzed: Int = 0,
        val predicted_rising_count: Int = 0,
        val min_confidence: Double = 0.0,
        val days_after: Int = 5,
        val price_threshold: Double = 3.0
    )

    @Serializable
    private data class PredictionDto(
        val ticker: String,
        val name: String,
        val status: String,
        val confidence: Double,
        val weight_change: Double,
        val etf_count: Int,
        val amount_billion: Double
    )

    @Serializable
    private data class TrainResultDto(
        val success: Boolean,
        val error: String? = null,
        val model_type: String? = null,
        val sample_count: Int? = null,
        val accuracy: Double? = null,
        val precision: Double? = null,
        val recall: Double? = null,
        val feature_importance: Map<String, Double>? = null
    )

    @Serializable
    private data class PredictResultDto(
        val success: Boolean,
        val error: String? = null,
        val total_analyzed: Int = 0,
        val predicted_rising_count: Int = 0,
        val predictions: List<PredictionDto> = emptyList()
    )

    @Serializable
    private data class ModelStatusDto(
        val cached_models: List<String>,
        val model_count: Int
    )
}

/**
 * 예측 응답 데이터
 */
data class PredictionResponse(
    val success: Boolean,
    val errorMessage: String? = null,
    val predictions: List<StockPrediction>,
    val trainingResult: TrainingResult? = null,
    val totalAnalyzed: Int = 0,
    val predictedCount: Int = 0
)

/**
 * 모델 상태 데이터
 */
data class ModelStatus(
    val cachedModels: List<String>,
    val modelCount: Int
)
