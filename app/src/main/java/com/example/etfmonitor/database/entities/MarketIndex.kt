package com.etfmonitor.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 시장 지수 데이터 엔티티
 * KOSPI/KOSDAQ 일별 종가 저장
 * ETF 통계와의 상관관계 분석을 위한 별도 테이블
 */
@Entity(tableName = "market_index")
data class MarketIndex(
    @PrimaryKey
    val id: String, // "KOSPI-2025-01-01" 또는 "KOSDAQ-2025-01-01" 형식
    val market: String, // "KOSPI" 또는 "KOSDAQ"
    val date: String, // "2025-01-01" 형식
    val closePrice: Double, // 종가
    val openPrice: Double, // 시가
    val highPrice: Double, // 고가
    val lowPrice: Double, // 저가
    val volume: Long, // 거래량
    val changeRate: Double, // 등락률 (%)
    val lastUpdated: Long = System.currentTimeMillis()
)
