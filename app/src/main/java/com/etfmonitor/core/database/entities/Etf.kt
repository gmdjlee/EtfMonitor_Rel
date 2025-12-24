package com.etfmonitor.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "etfs")
data class Etf(
    @PrimaryKey
    val ticker: String,
    val name: String
)
