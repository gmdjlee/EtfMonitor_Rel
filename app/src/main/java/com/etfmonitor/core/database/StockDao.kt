package com.etfmonitor.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.etfmonitor.core.database.entities.Stock
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {
    @Query("SELECT * FROM stocks ORDER BY name ASC")
    fun getAllStocks(): Flow<List<Stock>>

    @Query("SELECT * FROM stocks WHERE ticker = :ticker")
    suspend fun getStock(ticker: String): Stock?

    @Query("SELECT name FROM stocks WHERE ticker = :ticker")
    suspend fun getStockName(ticker: String): String?

    @Query("SELECT * FROM stocks WHERE name LIKE '%' || :query || '%' OR ticker LIKE '%' || :query || '%' ORDER BY name ASC LIMIT 50")
    fun searchStocks(query: String): Flow<List<Stock>>

    @Query("SELECT * FROM stocks WHERE is_etf_holding = 1 ORDER BY name ASC")
    fun getEtfHoldingStocks(): Flow<List<Stock>>

    @Query("SELECT * FROM stocks WHERE market = :market ORDER BY name ASC")
    fun getStocksByMarket(market: String): Flow<List<Stock>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stocks: List<Stock>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stock: Stock)

    /** ETF 보유 종목에서 동기화 (isEtfHolding=true로 upsert) */
    @Query("""
        INSERT OR REPLACE INTO stocks (ticker, name, market, sector, is_etf_holding, lastUpdated)
        VALUES (:ticker, :name, :market,
            COALESCE((SELECT sector FROM stocks WHERE ticker = :ticker), ''),
            1, :lastUpdated)
    """)
    suspend fun upsertFromHolding(ticker: String, name: String, market: String, lastUpdated: Long)

    /** 일괄 동기화용 */
    @Transaction
    suspend fun syncFromHoldings(holdings: List<Pair<String, String>>) {
        val now = System.currentTimeMillis()
        holdings.forEach { (ticker, name) ->
            val market = Stock.inferMarket(ticker)
            upsertFromHolding(ticker, name, market, now)
        }
    }

    @Query("DELETE FROM stocks")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM stocks")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM stocks WHERE is_etf_holding = 1")
    suspend fun getEtfHoldingCount(): Int

    @Query("SELECT MAX(lastUpdated) FROM stocks")
    suspend fun getLastUpdateTime(): Long?
}
