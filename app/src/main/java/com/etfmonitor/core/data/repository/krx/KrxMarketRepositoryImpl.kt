package com.etfmonitor.core.data.repository.krx

import com.etfmonitor.core.data.krx.adapter.*
import com.krxkt.KrxStock
import com.krxkt.model.Market
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KrxMarketRepositoryImpl @Inject constructor(
    private val krxStock: KrxStock
) : KrxRepositoryBase() {

    // FIX S4: Index ticker constants for clarity
    companion object {
        const val KOSPI_200_INDEX = "1028"
        const val KOSDAQ_150_INDEX = "2203"
    }

    /**
     * AD-003: Index components via top-N market cap proxy.
     * Maps index ticker to market and returns top stocks by market cap.
     *
     * FIX W1: Uses 180s timeout for large data collection (2000+ stocks)
     */
    suspend fun getIndexComponents(
        indexTicker: String,
        date: String = DateAdapter.today(),
        topN: Int = 200
    ): Result<List<String>> = krxCall(
        timeoutMs = 180_000L  // 180s timeout (CLAUDE.md Critical Rule #3 - Oscillator pattern)
    ) {
        val market = when (indexTicker) {
            KOSPI_200_INDEX -> Market.KOSPI
            KOSDAQ_150_INDEX -> Market.KOSDAQ
            else -> Market.ALL
        }
        krxStock.getMarketCap(date, market)
            .sortedByDescending { it.marketCap }
            .take(topN)
            .map { it.ticker }
    }
}
