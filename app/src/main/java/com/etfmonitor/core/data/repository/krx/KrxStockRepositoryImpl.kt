package com.etfmonitor.core.data.repository.krx

import com.etfmonitor.core.data.krx.adapter.*
import com.krxkt.KrxStock
import com.krxkt.model.Market
import com.krxkt.model.MarketCap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KrxStockRepositoryImpl @Inject constructor(
    private val krxStock: KrxStock
) : KrxRepositoryBase() {

    suspend fun getStockList(
        date: String = DateAdapter.today(),
        market: Market = Market.ALL
    ): Result<List<String>> = krxCall {
        krxStock.getTickerList(date, market).map { it.ticker }
    }

    // FIX W4: Return full MarketCap objects, not stripped Pair<String, Long>
    suspend fun getMarketCap(
        date: String = DateAdapter.today(),
        market: Market = Market.ALL
    ): Result<List<MarketCap>> = krxCall {
        krxStock.getMarketCap(date, market)
    }
}
