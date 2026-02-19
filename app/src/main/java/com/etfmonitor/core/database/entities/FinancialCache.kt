package com.etfmonitor.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "financial_cache")
data class FinancialCache(
    @PrimaryKey val ticker: String,
    val name: String,
    val data: String,
    val cachedAt: Long = System.currentTimeMillis()
)
