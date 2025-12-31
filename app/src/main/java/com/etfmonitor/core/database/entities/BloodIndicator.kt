package com.etfmonitor.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Blood Indicator Entity
 * US Treasury 기반 시장 건강도 지표 (v2.0 - FRED API)
 *
 * BLOOD = US03MY (3M T-Bill) / BAMLH0A0HYM2 (High Yield Spread)
 * - 100주 SMA 위 (RISK_ON): Green - 시장이 건강하고 위험 자산 선호
 * - 100주 SMA 아래 (RISK_OFF): Red - 시장 스트레스, 안전 자산 선호
 *
 * Data Sources:
 * - US03MY: Yahoo Finance (^IRX)
 * - BAMLH0A0HYM2: FRED API (free API key required)
 */
@Entity(
    tableName = "blood_indicator",
    indices = [
        Index(value = ["date"]),
        Index(value = ["signalType"])
    ]
)
data class BloodIndicator(
    @PrimaryKey
    val id: String,                    // "BLOOD-2024-01-01" format
    val date: String,                  // "2024-01-01" format
    val bloodValue: Double,            // Calculated BLOOD indicator value (US03MY / HighYieldSpread)
    val bloodSma: Double,              // 100-week SMA of BLOOD value
    val us03my: Double,                // 3-Month T-Bill Rate (^IRX from Yahoo)
    val highYieldSpread: Double,       // ICE BofA High Yield Spread (BAMLH0A0HYM2 from FRED)
    val spyClose: Double?,             // S&P 500 close (for reference chart)
    val signalType: String,            // "RISK_ON", "RISK_OFF", "NEUTRAL"
    val signalColor: String,           // "green", "red", "gray"
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        fun createId(date: String): String = "BLOOD-$date"
    }
}
