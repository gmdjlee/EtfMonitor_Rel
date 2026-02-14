package com.etfmonitor.core.domain.repository

import com.etfmonitor.core.analysis.model.*

/**
 * Stock Data Repository Interface
 *
 * Abstracts kotlin_krx data fetching + technical analysis computations.
 * Replaces OscillatorPyClient for stock feature operations.
 *
 * ## Responsibilities
 * - OHLCV data retrieval with resampling
 * - Market cap + investor trading data
 * - Technical indicator computation (CMF, Fear&Greed, signals)
 * - Elder Impulse System
 * - DeMark TD Setup
 * - Stock list management
 *
 * ## Implementation
 * - KrxStockDataRepositoryImpl: Uses kotlin_krx KrxStock + TechnicalAnalysisEngine
 */
interface StockDataRepository {

    /**
     * Get stock OHLCV data with optional resampling
     *
     * @param ticker Stock ticker (6 digits)
     * @param days Analysis period (fetches days * interval multiplier)
     * @param interval "d" (daily), "w" (weekly), "m" (monthly)
     * @return StockOhlcvData or null if failed
     */
    suspend fun getStockOhlcv(ticker: String, days: Int, interval: String = "d"): StockOhlcvData?

    /**
     * Get stock analysis data (market cap + foreign/institution 5-day rolling sum)
     *
     * @param ticker Stock ticker
     * @param days Analysis period
     * @return StockData or null if failed
     */
    suspend fun getStockAnalysisData(ticker: String, days: Int): StockData?

    /**
     * Get all stocks list (ticker, name pairs)
     *
     * @return List of (ticker, name) pairs
     */
    suspend fun getAllStocksList(): List<Pair<String, String>>

    /**
     * Get stock name by ticker
     *
     * @param ticker Stock ticker
     * @return Stock name or null if not found
     */
    suspend fun getStockName(ticker: String): String?

    /**
     * Get trend signal analysis data
     *
     * Combines OHLCV + MA + CMF + Fear&Greed + buy/sell signals
     *
     * @param ticker Stock ticker
     * @param days Analysis period (fetches days * interval multiplier)
     * @param interval "d" (daily), "w" (weekly)
     * @return TrendSignalData or null if failed
     */
    suspend fun getTrendSignalData(ticker: String, days: Int = 365, interval: String = "w"): TrendSignalData?

    /**
     * Get Elder Impulse System analysis data
     *
     * @param ticker Stock ticker
     * @param days Analysis period
     * @param interval "d" (daily), "w" (weekly)
     * @return ElderImpulseData or null if failed
     */
    suspend fun getElderImpulseData(ticker: String, days: Int = 365, interval: String = "w"): ElderImpulseData?

    /**
     * Get DeMark TD Setup analysis data
     *
     * @param ticker Stock ticker
     * @param days Analysis period
     * @param interval "d" (daily), "w" (weekly), "m" (monthly)
     * @return DemarkTDData or null if failed
     */
    suspend fun getDemarkTDData(ticker: String, days: Int = 365, interval: String = "w"): DemarkTDData?
}
