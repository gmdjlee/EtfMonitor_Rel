package com.etfmonitor.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "market_deposits")
data class MarketDeposit(
    @PrimaryKey
    val date: String, // "2024-01-01" 형식
    val depositAmount: Double, // 고객예탁금 (억원)
    val depositChange: Double, // 고객예탁금 전일대비 (억원)
    val creditAmount: Double, // 신용잔고 (억원)
    val creditChange: Double, // 신용잔고 전일대비 (억원)
    val lastUpdated: Long = System.currentTimeMillis()
)
