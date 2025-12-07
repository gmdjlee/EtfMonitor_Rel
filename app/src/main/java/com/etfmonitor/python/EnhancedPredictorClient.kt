package com.etfmonitor.python

import android.content.Context
import com.chaquo.python.Python
import com.etfmonitor.database.FearGreedDao
import com.etfmonitor.database.MarketIndexDao
import com.etfmonitor.database.MarketOscillatorDao
import com.etfmonitor.database.StockAnalysisDao
import com.etfmonitor.database.entities.EnhancedPrediction
import com.etfmonitor.database.entities.EnhancedPredictionConfig
import com.etfmonitor.database.entities.EnhancedTrainingResult
import com.etfmonitor.database.entities.MarketContextData
import com.etfmonitor.database.entities.StockChangeData
import com.etfmonitor.database.entities.StockTechData
import com.etfmonitor.utils.AppLogger
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
 * 향상된 ML 예측 Python 클라이언트
 * stock_predictor_v2.py 모듈 사용 - 28개 Feature, 앙상블 모델
 * 기존 120초 → 30초 (배치 처리로 최적화)
 */
@Singleton
class EnhancedPredictorClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val marketOscillatorDao: MarketOscillatorDao,
    private val fearGreedDao: FearGreedDao,
    private val marketIndexDao: MarketIndexDao,
    private val stockAnalysisDao: StockAnalysisDao
) {
    companion object {
        private val logger = AppLogger.getLogger("EnhancedPredictorClient")
        private const val TIMEOUT_MS = 60_000L  // 60초 (기존 120초에서 단축)
    }

    private val python = Python.getInstance()
    private val module = python.getModule("stock_predictor_v2")
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * 향상된 예측 실행 (학습 + 예측)
     */
    suspend fun trainAndPredict(
        historicalChanges: List<StockChangeData>,
        currentChanges: List<StockChangeData>,
        config: EnhancedPredictionConfig = EnhancedPredictionConfig()
    ): EnhancedPredictionResponse = withContext(Dispatchers.IO) {
        try {
            logger.d("Enhanced training with ${historicalChanges.size} historical, ${currentChanges.size} current")

            withTimeout(TIMEOUT_MS) {
                // 1. 시장 컨텍스트 데이터 수집
                val marketContext = collectMarketContext()
                val marketDataJson = json.encodeToString(MarketDataDto.serializer(), marketContext.toDto())

                // 2. 종목별 기술적 데이터 수집
                val allTickers = (historicalChanges.map { it.ticker } + currentChanges.map { it.ticker }).distinct()
                val stockData = collectStockData(allTickers)
                val stockDataJson = json.encodeToString(
                    kotlinx.serialization.builtins.MapSerializer(
                        kotlinx.serialization.builtins.serializer<String>(),
                        StockDataDto.serializer()
                    ),
                    stockData.mapValues { it.value.toDto() }
                )

                // 3. 변화 데이터 JSON 생성
                val histJson = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(StockChangeDataDto.serializer()),
                    historicalChanges.map { it.toDto() }
                )
                val currJson = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(StockChangeDataDto.serializer()),
                    currentChanges.map { it.toDto() }
                )

                // 4. Python 호출
                val result = module.callAttr(
                    "train_and_predict_enhanced",
                    histJson,
                    currJson,
                    marketDataJson,
                    stockDataJson,
                    config.daysAfter,
                    config.priceThreshold,
                    config.modelType,
                    config.minConfidence,
                    config.useCrossValidation
                ).toString()

                // 5. 결과 파싱
                val response = json.decodeFromString<EnhancedResponseDto>(result)

                if (!response.success) {
                    logger.e("Enhanced prediction failed: ${response.error}")
                    return@withTimeout EnhancedPredictionResponse(
                        success = false,
                        errorMessage = response.error,
                        predictions = emptyList()
                    )
                }

                // 6. 예측 결과 변환
                val predictions = response.predictions.map { dto ->
                    EnhancedPrediction(
                        id = "${dto.ticker}-${LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}",
                        ticker = dto.ticker,
                        name = dto.name,
                        predictionDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                        confidence = dto.confidence,
                        status = dto.status,
                        keyFactors = json.encodeToString(
                            kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.builtins.serializer<String>()),
                            dto.key_factors
                        ),
                        riskScore = dto.risk_score,
                        featureValues = json.encodeToString(
                            kotlinx.serialization.builtins.MapSerializer(
                                kotlinx.serialization.builtins.serializer<String>(),
                                kotlinx.serialization.builtins.serializer<Double>()
                            ),
                            dto.feature_values
                        ),
                        modelType = config.modelType,
                        daysAfter = config.daysAfter,
                        priceThreshold = config.priceThreshold
                    )
                }

                logger.d("Enhanced prediction success: ${predictions.size} predictions")

                EnhancedPredictionResponse(
                    success = true,
                    predictions = predictions,
                    trainingResult = response.training?.let { training ->
                        EnhancedTrainingResult(
                            success = true,
                            modelType = config.modelType,
                            sampleCount = training.sample_count ?: 0,
                            featureCount = training.feature_count ?: 28,
                            cvAccuracy = training.cv_accuracy ?: 0.0,
                            cvPrecision = training.cv_precision ?: 0.0,
                            cvRecall = training.cv_recall ?: 0.0,
                            cvF1 = training.cv_f1 ?: 0.0,
                            featureImportance = training.feature_importance ?: emptyMap(),
                            topFeatures = training.top_features ?: emptyList(),
                            trainingTimeMs = training.training_time_ms ?: 0L
                        )
                    },
                    totalAnalyzed = response.prediction?.total_analyzed ?: 0,
                    predictedCount = response.prediction?.predicted_rising_count ?: 0,
                    totalTimeMs = response.total_time_ms ?: 0L
                )
            }
        } catch (e: Exception) {
            logger.e("Error in enhanced trainAndPredict", e)
            EnhancedPredictionResponse(
                success = false,
                errorMessage = e.message ?: "Unknown error",
                predictions = emptyList()
            )
        }
    }

    /**
     * 시장 컨텍스트 데이터 수집
     */
    private suspend fun collectMarketContext(): MarketContextData {
        return try {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

            // 시장 오실레이터
            val oscillator = marketOscillatorDao.getLatestByMarket("KOSPI")?.oscillator ?: 50.0

            // Fear & Greed
            val fearGreed = fearGreedDao.getLatestByMarket("KOSPI")?.fearGreedValue ?: 0.5

            // 시장 수익률 (5일)
            val recentIndices = marketIndexDao.getRecentByMarket("KOSPI", 6)
            val marketReturn5d = if (recentIndices.size >= 2) {
                val latest = recentIndices.first().closePrice
                val prev = recentIndices.last().closePrice
                if (prev > 0) ((latest - prev) / prev) * 100 else 0.0
            } else 0.0

            MarketContextData(
                oscillator = oscillator,
                fearGreed = fearGreed,
                marketReturn5d = marketReturn5d
            )
        } catch (e: Exception) {
            logger.w("Failed to collect market context: ${e.message}")
            MarketContextData(oscillator = 50.0, fearGreed = 0.5, marketReturn5d = 0.0)
        }
    }

    /**
     * 종목별 기술적 데이터 수집
     */
    private suspend fun collectStockData(tickers: List<String>): Map<String, StockTechData> {
        val result = mutableMapOf<String, StockTechData>()

        try {
            for (ticker in tickers) {
                val analysis = stockAnalysisDao.getAnalysisData(ticker)
                if (analysis != null) {
                    // 기본 데이터 생성 (상세 기술적 지표는 Python에서 계산)
                    result[ticker] = StockTechData(
                        ticker = ticker,
                        foreign5d = analysis.foreign5d.lastOrNull() ?: 0L,
                        institution5d = analysis.institution5d.lastOrNull() ?: 0L,
                        marketCap = analysis.marketCap.lastOrNull() ?: 0L
                    )
                }
            }
        } catch (e: Exception) {
            logger.w("Failed to collect stock data: ${e.message}")
        }

        return result
    }

    /**
     * 모델 상태 조회
     */
    suspend fun getModelStatus(): EnhancedModelStatus = withContext(Dispatchers.IO) {
        try {
            val result = module.callAttr("get_model_status_v2").toString()
            val status = json.decodeFromString<ModelStatusDto>(result)
            EnhancedModelStatus(
                cachedModels = status.cached_models,
                modelCount = status.model_count,
                featureCount = status.feature_count,
                xgboostAvailable = status.xgboost_available,
                lightgbmAvailable = status.lightgbm_available,
                smoteAvailable = status.smote_available
            )
        } catch (e: Exception) {
            logger.e("Error getting model status", e)
            EnhancedModelStatus()
        }
    }

    /**
     * 모델 캐시 초기화
     */
    suspend fun clearModelCache(): Boolean = withContext(Dispatchers.IO) {
        try {
            module.callAttr("clear_model_cache_v2")
            true
        } catch (e: Exception) {
            logger.e("Error clearing model cache", e)
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
    private data class MarketDataDto(
        val oscillator: Double,
        val fear_greed: Double,
        val market_return_5d: Double
    )

    private fun MarketContextData.toDto() = MarketDataDto(
        oscillator = oscillator,
        fear_greed = fearGreed,
        market_return_5d = marketReturn5d
    )

    @Serializable
    private data class StockDataDto(
        val foreign_5d: Long,
        val institution_5d: Long,
        val market_cap: Long,
        val price_vs_ma20: Double = 1.0,
        val price_vs_ma60: Double = 1.0,
        val rsi: Double = 50.0,
        val macd_signal: Int = 0,
        val volume_ratio: Double = 1.0,
        val volatility: Double = 0.0,
        val return_5d: Double = 0.0,
        val return_20d: Double = 0.0,
        val return_60d: Double = 0.0
    )

    private fun StockTechData.toDto() = StockDataDto(
        foreign_5d = foreign5d,
        institution_5d = institution5d,
        market_cap = marketCap,
        price_vs_ma20 = priceVsMa20,
        price_vs_ma60 = priceVsMa60,
        rsi = rsi,
        macd_signal = macdSignal,
        volume_ratio = volumeRatio,
        volatility = volatility,
        return_5d = return5d,
        return_20d = return20d,
        return_60d = return60d
    )

    @Serializable
    private data class EnhancedResponseDto(
        val success: Boolean,
        val error: String? = null,
        val training: TrainingDto? = null,
        val prediction: PredictionMetaDto? = null,
        val predictions: List<EnhancedPredictionDto> = emptyList(),
        val total_time_ms: Long? = null
    )

    @Serializable
    private data class TrainingDto(
        val cv_accuracy: Double? = null,
        val cv_precision: Double? = null,
        val cv_recall: Double? = null,
        val cv_f1: Double? = null,
        val sample_count: Int? = null,
        val feature_count: Int? = null,
        val feature_importance: Map<String, Double>? = null,
        val top_features: List<String>? = null,
        val training_time_ms: Long? = null
    )

    @Serializable
    private data class PredictionMetaDto(
        val total_analyzed: Int = 0,
        val predicted_rising_count: Int = 0,
        val min_confidence: Double = 0.6,
        val days_after: Int = 5,
        val price_threshold: Double = 3.0,
        val inference_time_ms: Long = 0
    )

    @Serializable
    private data class EnhancedPredictionDto(
        val ticker: String,
        val name: String,
        val status: String,
        val confidence: Double,
        val key_factors: List<String> = emptyList(),
        val risk_score: Double = 0.0,
        val feature_values: Map<String, Double> = emptyMap()
    )

    @Serializable
    private data class ModelStatusDto(
        val cached_models: List<String> = emptyList(),
        val model_count: Int = 0,
        val feature_count: Int = 28,
        val xgboost_available: Boolean = false,
        val lightgbm_available: Boolean = false,
        val smote_available: Boolean = false
    )
}

/**
 * 향상된 예측 응답
 */
data class EnhancedPredictionResponse(
    val success: Boolean,
    val errorMessage: String? = null,
    val predictions: List<EnhancedPrediction>,
    val trainingResult: EnhancedTrainingResult? = null,
    val totalAnalyzed: Int = 0,
    val predictedCount: Int = 0,
    val totalTimeMs: Long = 0
)

/**
 * 향상된 모델 상태
 */
data class EnhancedModelStatus(
    val cachedModels: List<String> = emptyList(),
    val modelCount: Int = 0,
    val featureCount: Int = 28,
    val xgboostAvailable: Boolean = false,
    val lightgbmAvailable: Boolean = false,
    val smoteAvailable: Boolean = false
)
