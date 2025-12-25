package com.etfmonitor.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 차트 분석 검색 히스토리
 *
 * historyType: 검색 히스토리 유형
 * - STATISTICS: ETF 통계탭 분석
 * - STOCK: 종목 메뉴
 * - AI_ANALYSIS: AI 분석 종목-지표
 */
@Entity(
    tableName = "search_history",
    indices = [
        Index(value = ["historyType"]),
        Index(value = ["historyType", "searchedAt"])
    ]
)
data class SearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ticker: String,
    val name: String,
    val market: String,
    val historyType: String = SearchHistoryType.STATISTICS,
    val searchedAt: Long = System.currentTimeMillis()
)

/**
 * 검색 히스토리 유형 상수
 */
object SearchHistoryType {
    const val STATISTICS = "STATISTICS"     // ETF 통계탭 분석
    const val STOCK = "STOCK"               // 종목 메뉴
    const val AI_ANALYSIS = "AI_ANALYSIS"   // AI 분석 종목-지표
}
