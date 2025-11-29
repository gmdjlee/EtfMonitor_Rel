package com.etfmonitor.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ML 모델 기반 주가 상승 예측 결과 엔티티
 * ETF 구성 변화 데이터로 학습한 모델의 예측 결과 저장
 */
@Entity(tableName = "stock_predictions")
data class StockPrediction(
    @PrimaryKey
    val id: String, // "{ticker}-{predictionDate}" 형식

    val ticker: String,           // 종목 코드
    val name: String,             // 종목명
    val predictionDate: String,   // 예측 생성 날짜 (YYYY-MM-DD)

    val status: String,           // ETF 변화 상태: NEW, INCREASED, DECREASED, REMOVED
    val confidence: Double,       // 상승 예측 신뢰도 (0.0 ~ 1.0)
    val weightChange: Double,     // 비중 변화율 (%)
    val etfCount: Int,            // 포함된 ETF 수
    val amountBillion: Double,    // 편입 금액 (10억 단위)

    val daysAfter: Int,           // 예측 기간 (일)
    val priceThreshold: Double,   // 상승 판단 기준 (%)
    val modelType: String,        // 사용된 모델 타입 (random_forest, gradient_boosting)

    val actualPriceChange: Double? = null,  // 실제 주가 변화율 (검증용, 나중에 업데이트)
    val wasCorrect: Boolean? = null,        // 예측 정확 여부 (검증용)

    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 예측 결과 요약 정보
 */
data class PredictionSummary(
    val predictionDate: String,
    val totalPredictions: Int,
    val avgConfidence: Double,
    val modelAccuracy: Double?,
    val verifiedCount: Int
)

/**
 * ML 모델 학습 결과
 */
data class TrainingResult(
    val success: Boolean,
    val modelType: String,
    val sampleCount: Int,
    val accuracy: Double,
    val precision: Double,
    val recall: Double,
    val featureImportance: Map<String, Double>,
    val errorMessage: String? = null
)

/**
 * 종목별 ETF 변화 정보 (예측 입력용)
 */
data class StockChangeData(
    val ticker: String,
    val name: String,
    val status: String,           // NEW, INCREASED, DECREASED, REMOVED
    val weightChange: Double,     // 비중 변화율 (%)
    val etfCount: Int,            // ETF 수
    val totalAmount: Long,        // 총 편입 금액 (원)
    val date: String              // 날짜
)
