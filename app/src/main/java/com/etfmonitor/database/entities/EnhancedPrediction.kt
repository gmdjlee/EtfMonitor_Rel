package com.etfmonitor.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 향상된 ML 예측 결과 엔티티
 * 28개 Feature와 앙상블 모델을 사용한 예측 결과 저장
 */
@Entity(tableName = "enhanced_predictions")
data class EnhancedPrediction(
    @PrimaryKey
    val id: String,                   // "{ticker}-{predictionDate}" 형식

    val ticker: String,               // 종목 코드
    val name: String,                 // 종목명
    val predictionDate: String,       // 예측 생성 날짜 (YYYY-MM-DD)

    val confidence: Double,           // 상승 예측 신뢰도 (0.0 ~ 1.0)
    val status: String,               // ETF 변화 상태: NEW, INCREASED, DECREASED, REMOVED
    val keyFactors: String,           // 주요 영향 요소 (JSON Array)
    val riskScore: Double,            // 위험 점수 (0.0 ~ 1.0)

    val featureValues: String,        // 주요 Feature 값들 (JSON Object)
    val modelType: String,            // 사용된 모델 타입 (voting, xgboost, lightgbm, etc.)

    val daysAfter: Int = 5,           // 예측 기간 (일)
    val priceThreshold: Double = 3.0, // 상승 판단 기준 (%)

    val actualPriceChange: Double? = null,  // 실제 주가 변화율 (검증용)
    val wasCorrect: Boolean? = null,        // 예측 정확 여부 (검증용)

    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 향상된 예측 학습 결과
 */
data class EnhancedTrainingResult(
    val success: Boolean,
    val modelType: String,
    val sampleCount: Int,
    val featureCount: Int,

    // CV 메트릭
    val cvAccuracy: Double,
    val cvPrecision: Double,
    val cvRecall: Double,
    val cvF1: Double,
    val cvStd: Double = 0.0,

    // Feature 중요도
    val featureImportance: Map<String, Double>,
    val topFeatures: List<String>,

    // 실행 정보
    val trainingTimeMs: Long,
    val xgboostAvailable: Boolean = false,
    val lightgbmAvailable: Boolean = false,
    val smoteAvailable: Boolean = false,

    val errorMessage: String? = null
)

/**
 * 향상된 예측 설정
 */
data class EnhancedPredictionConfig(
    val daysAfter: Int = 5,
    val priceThreshold: Double = 3.0,
    val minConfidence: Double = 0.6,
    val modelType: String = "voting",
    val useEnhancedFeatures: Boolean = true,
    val useCrossValidation: Boolean = true,
    val useSmote: Boolean = false
)

/**
 * 시장 컨텍스트 데이터
 */
data class MarketContextData(
    val oscillator: Double,       // 시장 오실레이터 (0-100)
    val fearGreed: Double,        // Fear & Greed 지수 (0-1)
    val marketReturn5d: Double,   // 시장 5일 수익률 (%)
    val kospiClose: Double = 0.0,
    val kosdaqClose: Double = 0.0
)

/**
 * 종목별 기술적 데이터
 */
data class StockTechData(
    val ticker: String,
    val priceVsMa20: Double = 1.0,
    val priceVsMa60: Double = 1.0,
    val rsi: Double = 50.0,
    val macdSignal: Int = 0,          // 1: golden cross, -1: dead cross, 0: neutral
    val volumeRatio: Double = 1.0,
    val volatility: Double = 0.0,
    val return5d: Double = 0.0,
    val return20d: Double = 0.0,
    val return60d: Double = 0.0,
    val foreign5d: Long = 0,
    val institution5d: Long = 0,
    val marketCap: Long = 0
)
