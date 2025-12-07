package com.etfmonitor.database.entities

import androidx.room.Entity

/**
 * 가격 데이터 캐시 엔티티
 * ML 예측을 위한 주가 변화 데이터 저장
 * 배치 조회 결과를 캐싱하여 반복 API 호출 방지
 */
@Entity(
    tableName = "price_cache",
    primaryKeys = ["ticker", "date"]
)
data class PriceCache(
    val ticker: String,           // 종목 코드
    val date: String,             // 날짜 (YYYY-MM-DD)
    val closePrice: Double,       // 종가
    val priceChange5d: Double?,   // 5일 후 가격 변화율 (%)
    val priceChange10d: Double?,  // 10일 후 가격 변화율 (%)
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * 캐시 유효 기간 (7일)
         */
        const val CACHE_VALIDITY_MS = 7 * 24 * 60 * 60 * 1000L
    }

    /**
     * 캐시가 유효한지 확인
     */
    fun isValid(): Boolean {
        return System.currentTimeMillis() - updatedAt < CACHE_VALIDITY_MS
    }
}
