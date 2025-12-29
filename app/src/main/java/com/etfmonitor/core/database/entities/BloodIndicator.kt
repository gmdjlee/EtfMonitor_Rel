package com.etfmonitor.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Blood Indicator Entity
 * US Treasury 기반 시장 건강도 지표
 *
 * BLOOD = IRX (3M T-Bill) / (HYG Yield - 10Y Treasury)
 * - 상승 추세 (RISK_ON): 시장이 건강하고 위험 자산 선호
 * - 하락 추세 (RISK_OFF): 시장 스트레스, 안전 자산 선호
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
    val bloodValue: Double,            // Calculated BLOOD indicator value
    val irx: Double,                   // 3-Month T-Bill Rate (^IRX)
    val hygYield: Double,              // HYG Dividend Yield
    val tenYearYield: Double,          // 10-Year Treasury Yield (^TNX)
    val spreadValue: Double,           // HYG Yield - 10Y Yield (denominator)
    val spyClose: Double?,             // S&P 500 close (for reference chart)
    val signalType: String,            // "RISK_ON", "RISK_OFF", "NEUTRAL"
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        fun createId(date: String): String = "BLOOD-$date"
    }
}
