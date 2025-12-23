package com.etfmonitor.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 차트 분석 검색 히스토리
 *
 * @param feature 검색 기능 구분 (oscillator, stocks_hub, analysis, ai_analysis 등)
 */
@Entity(
    tableName = "search_history",
    indices = [Index(value = ["ticker", "feature"], unique = true)]
)
data class SearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ticker: String,
    val name: String,
    val market: String,
    val feature: String = "default",
    val searchedAt: Long = System.currentTimeMillis()
)

/**
 * 검색 기능 구분 상수
 */
object SearchFeature {
    const val OSCILLATOR = "oscillator"
    const val STOCKS_HUB = "stocks_hub"
    const val ANALYSIS = "analysis"
    const val AI_ANALYSIS = "ai_analysis"
    const val DEFAULT = "default"
}
