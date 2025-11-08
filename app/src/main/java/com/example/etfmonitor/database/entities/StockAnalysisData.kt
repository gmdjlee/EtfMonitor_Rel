package com.etfmonitor.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_analysis_data")
data class StockAnalysisData(
    @PrimaryKey
    val ticker: String,
    val name: String,
    val dates: List<String>, // TypeConverter로 변환
    val marketCap: List<Long>, // TypeConverter로 변환
    val foreign5d: List<Long>, // TypeConverter로 변환
    val institution5d: List<Long>, // TypeConverter로 변환
    val lastUpdated: Long = System.currentTimeMillis(),
    val dataStartDate: String, // 데이터 시작 날짜
    val dataEndDate: String // 데이터 종료 날짜
)
