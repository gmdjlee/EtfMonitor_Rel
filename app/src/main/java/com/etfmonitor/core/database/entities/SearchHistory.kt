package com.etfmonitor.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 차트 분석 검색 히스토리
 */
@Entity(tableName = "search_history")
data class SearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ticker: String,
    val name: String,
    val market: String,
    val searchedAt: Long = System.currentTimeMillis()
)
