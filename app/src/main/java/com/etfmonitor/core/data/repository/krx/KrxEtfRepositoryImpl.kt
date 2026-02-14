package com.etfmonitor.core.data.repository.krx

import com.etfmonitor.core.data.krx.adapter.*
import com.etfmonitor.core.database.entities.Holding
import com.krxkt.KrxEtf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KrxEtfRepositoryImpl @Inject constructor(
    private val krxEtf: KrxEtf
) : KrxRepositoryBase() {

    suspend fun getEtfList(date: String = DateAdapter.today()): Result<List<String>> = krxCall {
        krxEtf.getEtfTickerList(date).map { it.ticker }
    }

    suspend fun getEtfHoldings(
        ticker: String,
        date: String = DateAdapter.today()
    ): Result<List<Holding>> = krxCall {
        // FIX C1: Correct parameter order is (date, ticker), use named parameters for clarity
        krxEtf.getPortfolio(date = date, ticker = ticker).map { portfolio ->
            HoldingMapper.fromEtfPortfolio(ticker, date, portfolio)
        }
    }

    suspend fun getEtfName(ticker: String, date: String = DateAdapter.today()): Result<String> = krxCall {
        krxEtf.getEtfName(ticker, date) ?: ""
    }
}
