package com.etfmonitor.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 종목 마스터 테이블
 * ETF 보유 종목 및 수급 분석의 기준 데이터
 */
@Entity(tableName = "stocks")
data class Stock(
    @PrimaryKey
    val ticker: String,
    val name: String,
    val market: String,  // "KOSPI" or "KOSDAQ"
    @ColumnInfo(defaultValue = "")
    val sector: String = "",  // 섹터 (반도체, 바이오 등)
    @ColumnInfo(name = "is_etf_holding", defaultValue = "0")
    val isEtfHolding: Boolean = false,  // ETF 편입 종목 여부
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        /** ETF 보유 종목에서 Stock 생성 (자동 동기화용) */
        fun fromHolding(ticker: String, name: String): Stock {
            val market = inferMarket(ticker)
            return Stock(
                ticker = ticker,
                name = name,
                market = market,
                isEtfHolding = true
            )
        }

        /** ticker로 시장 추정 */
        fun inferMarket(ticker: String): String = when {
            ticker.startsWith("0") || ticker.startsWith("1") ||
            ticker.startsWith("2") || ticker.startsWith("3") -> "KOSPI"
            else -> "KOSDAQ"
        }
    }
}
