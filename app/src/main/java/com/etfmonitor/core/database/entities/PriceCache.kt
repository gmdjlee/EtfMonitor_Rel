package com.etfmonitor.core.database.entities

import androidx.room.Entity

/**
 * ML 예측용 가격 캐시 엔티티
 *
 * 주가 변동률 계산을 위한 종가 데이터를 캐시하여
 * 예측 모델 학습 및 검증에 사용
 */
@Entity(
    tableName = "price_cache",
    primaryKeys = ["ticker", "date"]
)
data class PriceCache(
    val ticker: String,
    val date: String,
    val closePrice: Double,
    val priceChange5d: Double? = null,
    val priceChange10d: Double? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
