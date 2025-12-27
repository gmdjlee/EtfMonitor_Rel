package com.etfmonitor.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 28개 Feature 기반 향상된 ML 예측 결과 엔티티
 *
 * 앙상블 모델(XGBoost, LightGBM, RandomForest, GradientBoosting)을 사용하여
 * ETF 편입/이탈, 수급, 기술적 지표 등 28개 특성으로 주가 상승 확률 예측
 */
@Entity(tableName = "enhanced_predictions")
data class EnhancedPrediction(
    @PrimaryKey
    val id: String,  // "{ticker}-{date}"
    val ticker: String,
    val name: String,
    val predictionDate: String,
    val confidence: Double,
    val status: String,  // "UP" or "DOWN"
    val keyFactors: String,  // JSON string of key contributing factors
    val riskScore: Double,
    val featureValues: String,  // JSON string of 28 feature values
    val modelType: String,  // "voting", "xgboost", "lightgbm", "random_forest", "gradient_boosting"
    @ColumnInfo(defaultValue = "5")
    val daysAfter: Int = 5,
    @ColumnInfo(defaultValue = "3.0")
    val priceThreshold: Double = 3.0,
    val actualPriceChange: Double? = null,
    val wasCorrect: Boolean? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun createId(ticker: String, date: String): String = "$ticker-$date"
    }
}
