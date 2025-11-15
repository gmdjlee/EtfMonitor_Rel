package com.etfmonitor.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stocks")
data class Stock(
    @PrimaryKey
    val ticker: String,
    val name: String,
    val market: String, // "KOSPI" or "KOSDAQ"
    val lastUpdated: Long = System.currentTimeMillis()
)
