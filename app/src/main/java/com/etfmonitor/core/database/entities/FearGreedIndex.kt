package com.etfmonitor.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "fear_greed_index", indices = [Index(value = ["date"]), Index(value = ["market", "date"])])
data class FearGreedIndex(
    @PrimaryKey
    val id: String, // "KOSPI-2024-01-01" 또는 "KOSDAQ-2024-01-01" 형식
    val market: String, // "KOSPI" 또는 "KOSDAQ"
    val date: String, // "2024-01-01" 형식
    val indexValue: Double, // 지수 종가
    val fearGreedValue: Double, // Fear & Greed 지수 (0.0 ~ 1.0)
    val oscillator: Double, // MACD Oscillator
    val rsi: Double, // RSI 값
    val momentum: Double, // 모멘텀 (정규화 전)
    val putCallRatio: Double, // Put-Call Ratio
    val volatility: Double, // VKOSPI (변동성)
    val spread: Double, // 10년국채 - 5년국채
    val lastUpdated: Long = System.currentTimeMillis()
)
