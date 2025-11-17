package com.etfmonitor.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 시장 과매수/과매도 데이터 엔티티
 * 코스피/코스닥 지수별 일별 oscillator 데이터 저장
 */
@Entity(tableName = "market_oscillator")
data class MarketOscillatorData(
    @PrimaryKey
    val id: String, // "KOSPI-2025-01-01" 또는 "KOSDAQ-2025-01-01" 형식
    val market: String, // "KOSPI" 또는 "KOSDAQ"
    val date: String, // "2025-01-01" 형식
    val indexValue: Double, // 지수 종가
    val oscillator: Double, // 과매수/과매도 지표 (-100.0 ~ 100.0)
    val lastUpdated: Long = System.currentTimeMillis()
)
