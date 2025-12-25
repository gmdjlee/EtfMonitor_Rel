package com.etfmonitor.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 종목 수급 분석 데이터 (캐시)
 * 종목명(name)은 stocks 테이블에서 JOIN으로 조회
 */
@Entity(tableName = "stock_analysis_data")
data class StockAnalysisData(
    @PrimaryKey
    val ticker: String,
    val dates: List<String>,         // TypeConverter 사용
    val marketCap: List<Long>,       // 시가총액
    val foreign5d: List<Long>,       // 외국인 5일 누적
    val institution5d: List<Long>,   // 기관 5일 누적
    val lastUpdated: Long = System.currentTimeMillis(),
    val dataStartDate: String,       // 데이터 시작 날짜
    val dataEndDate: String          // 데이터 종료 날짜
)

/** stocks JOIN 결과용 DTO */
data class StockAnalysisWithName(
    val ticker: String,
    val name: String,  // stocks 테이블에서 JOIN
    val dates: List<String>,
    val marketCap: List<Long>,
    val foreign5d: List<Long>,
    val institution5d: List<Long>,
    val lastUpdated: Long,
    val dataStartDate: String,
    val dataEndDate: String
)
