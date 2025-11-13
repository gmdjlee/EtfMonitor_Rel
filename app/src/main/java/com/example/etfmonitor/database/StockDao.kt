package com.etfmonitor.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.database.entities.Stock
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {
    @Query("SELECT * FROM stocks ORDER BY name ASC")
    fun getAllStocks(): Flow<List<Stock>>

    @Query("SELECT * FROM stocks WHERE ticker = :ticker")
    suspend fun getStock(ticker: String): Stock?

    @Query("SELECT * FROM stocks WHERE name LIKE '%' || :query || '%' OR ticker LIKE '%' || :query || '%' ORDER BY name ASC LIMIT 50")
    fun searchStocks(query: String): Flow<List<Stock>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stocks: List<Stock>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stock: Stock)

    @Query("DELETE FROM stocks")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM stocks")
    suspend fun getCount(): Int

    @Query("SELECT MAX(lastUpdated) FROM stocks")
    suspend fun getLastUpdateTime(): Long?
}
