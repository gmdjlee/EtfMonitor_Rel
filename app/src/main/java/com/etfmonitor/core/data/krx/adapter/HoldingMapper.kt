package com.etfmonitor.core.data.krx.adapter

import com.krxkt.model.EtfPortfolio
import com.etfmonitor.core.database.entities.Holding

object HoldingMapper {
    /**
     * Maps EtfPortfolio to Holding using factory method.
     *
     * CRITICAL: Always use Holding.create() factory (CLAUDE.md Critical Rule #1)
     *
     * Note on precision: EtfPortfolio.amount is Long (raw won). Holding stores
     * amount as compressed Int (millions). For Korean market values, typical
     * ETF component amounts (<16 trillion won) fit within Float precision
     * after million-unit conversion. This is an accepted trade-off.
     */
    fun fromEtfPortfolio(
        etfTicker: String,
        date: String,
        portfolio: EtfPortfolio
    ): Holding {
        return Holding.create(
            etfTicker = etfTicker,
            stockTicker = portfolio.ticker,
            stockName = portfolio.name,  // FIX C2: parameter name is stockName, not name
            date = date,
            weight = portfolio.weight?.toFloat() ?: 0f,
            amount = portfolio.amount.toFloat()  // FIX C3: Long->Float documented
        )
    }
}
