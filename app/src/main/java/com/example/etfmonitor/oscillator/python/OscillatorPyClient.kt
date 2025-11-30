package com.etfmonitor.oscillator.python

import android.util.Log
import com.chaquo.python.Python
import com.etfmonitor.oscillator.model.MarketDepositData
import com.etfmonitor.oscillator.model.StockData
import com.etfmonitor.oscillator.model.TrendSignalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class OscillatorPyClient(private val python: Python) {

    companion object {
        private const val TAG = "OscillatorPyClient"
    }

    private val stocksModule by lazy {
        Log.d(TAG, "Loading stocks module")
        python.getModule("stocks")
    }

    private val depositModule by lazy {
        Log.d(TAG, "Loading deposit_scraper module")
        python.getModule("deposit_scraper")
    }

    private val marketModule by lazy {
        Log.d(TAG, "Loading market module")
        python.getModule("market")
    }

    private val trendSignalModule by lazy {
        Log.d(TAG, "Loading trend_signal module")
        python.getModule("trend_signal")
    }

    /**
     * 종목 검색
     */
    suspend fun searchStock(query: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "searchStock: $query")
            val jsonStr = stocksModule.callAttr("search_stock_wrapper", query).toString()
            val jsonObj = JSONObject(jsonStr)

            if (jsonObj.has("error")) {
                Log.e(TAG, "Search error: ${jsonObj.getString("error")}")
                return@withContext null
            }

            val ticker = jsonObj.getString("ticker")
            val name = jsonObj.getString("name")
            Log.d(TAG, "Found stock: $ticker - $name")

            Pair(ticker, name)
        } catch (e: Exception) {
            Log.e(TAG, "searchStock error", e)
            null
        }
    }

    /**
     * 종목 분석 데이터 수집
     */
    suspend fun getStockAnalysis(ticker: String, days: Int = 180): StockData? =
        withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "getStockAnalysis: $ticker, $days days")
            val jsonStr = stocksModule.callAttr("get_stock_analysis", ticker, days).toString()
            val jsonObj = JSONObject(jsonStr)

            if (jsonObj.has("error")) {
                Log.e(TAG, "Analysis error: ${jsonObj.getString("error")}")
                return@withContext null
            }

            val dates = jsonObj.getJSONArray("dates").let { arr ->
                List(arr.length()) { arr.getString(it) }
            }

            val marketCap = jsonObj.getJSONArray("market_cap").let { arr ->
                List(arr.length()) { arr.getLong(it) }
            }

            val foreign5d = jsonObj.getJSONArray("foreign_5d").let { arr ->
                List(arr.length()) { arr.getLong(it) }
            }

            val institution5d = jsonObj.getJSONArray("institution_5d").let { arr ->
                List(arr.length()) { arr.getLong(it) }
            }

            val stockData = StockData(
                ticker = jsonObj.getString("ticker"),
                name = jsonObj.getString("name"),
                dates = dates,
                marketCap = marketCap,
                foreign5d = foreign5d,
                institution5d = institution5d
            )

            Log.d(TAG, "Stock analysis complete: ${stockData.name}, ${dates.size} data points")
            stockData
        } catch (e: Exception) {
            Log.e(TAG, "getStockAnalysis error", e)
            null
        }
    }

    /**
     * 증시 자금 동향 데이터 수집
     */
    suspend fun getMarketDepositData(numPages: Int = 5): MarketDepositData? =
        withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "getMarketDepositData: $numPages pages")
            val jsonStr = depositModule.callAttr("get_market_deposit_data", numPages).toString()
            val jsonObj = JSONObject(jsonStr)

            if (jsonObj.has("error")) {
                Log.e(TAG, "Market data error: ${jsonObj.getString("error")}")
                return@withContext null
            }

            val dates = jsonObj.getJSONArray("dates").let { arr ->
                List(arr.length()) { arr.getString(it) }
            }

            val depositAmounts = jsonObj.getJSONArray("deposit_amounts").let { arr ->
                List(arr.length()) { arr.getDouble(it) }
            }

            val depositChanges = jsonObj.getJSONArray("deposit_changes").let { arr ->
                List(arr.length()) { arr.getDouble(it) }
            }

            val creditAmounts = jsonObj.getJSONArray("credit_amounts").let { arr ->
                List(arr.length()) { arr.getDouble(it) }
            }

            val creditChanges = jsonObj.getJSONArray("credit_changes").let { arr ->
                List(arr.length()) { arr.getDouble(it) }
            }

            val depositData = MarketDepositData(
                dates = dates,
                depositAmounts = depositAmounts,
                depositChanges = depositChanges,
                creditAmounts = creditAmounts,
                creditChanges = creditChanges
            )

            Log.d(TAG, "Market deposit data complete: ${dates.size} data points")
            depositData
        } catch (e: Exception) {
            Log.e(TAG, "getMarketDepositData error", e)
            null
        }
    }

    /**
     * 최신 증시 자금 동향
     */
    suspend fun getLatestMarketData(): MarketDepositData? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "getLatestMarketData")
            val jsonStr = depositModule.callAttr("get_latest_market_data").toString()
            val jsonObj = JSONObject(jsonStr)

            if (jsonObj.has("error")) {
                Log.e(TAG, "Latest market data error: ${jsonObj.getString("error")}")
                return@withContext null
            }

            val dates = jsonObj.getJSONArray("dates").let { arr ->
                List(arr.length()) { arr.getString(it) }
            }

            val depositAmounts = jsonObj.getJSONArray("deposit_amounts").let { arr ->
                List(arr.length()) { arr.getDouble(it) }
            }

            val depositChanges = jsonObj.getJSONArray("deposit_changes").let { arr ->
                List(arr.length()) { arr.getDouble(it) }
            }

            val creditAmounts = jsonObj.getJSONArray("credit_amounts").let { arr ->
                List(arr.length()) { arr.getDouble(it) }
            }

            val creditChanges = jsonObj.getJSONArray("credit_changes").let { arr ->
                List(arr.length()) { arr.getDouble(it) }
            }

            MarketDepositData(
                dates = dates,
                depositAmounts = depositAmounts,
                depositChanges = depositChanges,
                creditAmounts = creditAmounts,
                creditChanges = creditChanges
            )
        } catch (e: Exception) {
            Log.e(TAG, "getLatestMarketData error", e)
            null
        }
    }

    /**
     * 전체 종목 리스트 가져오기 (자동완성용)
     */
    suspend fun getAllStocksList(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "getAllStocksList")
            val jsonStr = stocksModule.callAttr("get_all_stocks_list").toString()

            if (jsonStr.contains("\"error\"")) {
                Log.e(TAG, "Error getting stocks list")
                return@withContext emptyList()
            }

            val jsonArray = org.json.JSONArray(jsonStr)
            List(jsonArray.length()) { i ->
                val obj = jsonArray.getJSONObject(i)
                Pair(obj.getString("ticker"), obj.getString("name"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAllStocksList error", e)
            emptyList()
        }
    }

    /**
     * 시장 과매수/과매도 지표 조회
     *
     * @param market "KOSPI" 또는 "KOSDAQ"
     * @param startDate YYYYMMDD 형식
     * @param endDate YYYYMMDD 형식
     * @return JSON 문자열
     */
    suspend fun getMarketOscillator(
        market: String,
        startDate: String,
        endDate: String
    ): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "getMarketOscillator: $market, $startDate ~ $endDate")
            val jsonStr = marketModule.callAttr(
                "get_market_oscillator",
                market,
                startDate,
                endDate
            ).toString()

            Log.d(TAG, "Market oscillator data retrieved for $market")
            jsonStr
        } catch (e: Exception) {
            Log.e(TAG, "getMarketOscillator error", e)
            """{"error": "${e.message}"}"""
        }
    }

    /**
     * 추세 시그널 분석 데이터 수집
     *
     * @param ticker 종목 코드
     * @param days 분석 기간 (일)
     * @param interval 주기 ("d"=일별, "w"=주별)
     * @return TrendSignalData 또는 null
     */
    suspend fun getTrendSignalData(
        ticker: String,
        days: Int = 365,
        interval: String = "w"
    ): TrendSignalData? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "getTrendSignalData: $ticker, $days days, interval: $interval")
            val jsonStr = trendSignalModule.callAttr(
                "get_trend_signal_analysis",
                ticker,
                days,
                interval
            ).toString()

            val jsonObj = JSONObject(jsonStr)

            if (jsonObj.has("error")) {
                Log.e(TAG, "Trend signal error: ${jsonObj.getString("error")}")
                return@withContext null
            }

            val dates = jsonObj.getJSONArray("dates").let { arr ->
                List(arr.length()) { arr.getString(it) }
            }

            val open = jsonObj.getJSONArray("open").let { arr ->
                List(arr.length()) { arr.getDouble(it) }
            }

            val high = jsonObj.getJSONArray("high").let { arr ->
                List(arr.length()) { arr.getDouble(it) }
            }

            val low = jsonObj.getJSONArray("low").let { arr ->
                List(arr.length()) { arr.getDouble(it) }
            }

            val close = jsonObj.getJSONArray("close").let { arr ->
                List(arr.length()) { arr.getDouble(it) }
            }

            val volume = jsonObj.getJSONArray("volume").let { arr ->
                List(arr.length()) { arr.getLong(it) }
            }

            val ma = jsonObj.getJSONArray("ma").let { arr ->
                List(arr.length()) { arr.getDouble(it) }
            }

            val cmf = jsonObj.getJSONArray("cmf").let { arr ->
                List(arr.length()) { arr.getDouble(it) }
            }

            val fearGreed = jsonObj.getJSONArray("fear_greed").let { arr ->
                List(arr.length()) { arr.getDouble(it) }
            }

            val buySignal = jsonObj.getJSONArray("buy_signal").let { arr ->
                List(arr.length()) { arr.getInt(it) }
            }

            val auxBuySignal = jsonObj.getJSONArray("aux_buy_signal").let { arr ->
                List(arr.length()) { arr.getInt(it) }
            }

            val sellSignal = jsonObj.getJSONArray("sell_signal").let { arr ->
                List(arr.length()) { arr.getInt(it) }
            }

            val auxSellSignal = jsonObj.getJSONArray("aux_sell_signal").let { arr ->
                List(arr.length()) { arr.getInt(it) }
            }

            val trendSignalData = TrendSignalData(
                ticker = jsonObj.getString("ticker"),
                name = jsonObj.getString("name"),
                interval = jsonObj.getString("interval"),
                dates = dates,
                open = open,
                high = high,
                low = low,
                close = close,
                volume = volume,
                ma = ma,
                cmf = cmf,
                fearGreed = fearGreed,
                buySignal = buySignal,
                auxBuySignal = auxBuySignal,
                sellSignal = sellSignal,
                auxSellSignal = auxSellSignal
            )

            Log.d(TAG, "Trend signal data complete: ${trendSignalData.name}, ${dates.size} data points")
            trendSignalData
        } catch (e: Exception) {
            Log.e(TAG, "getTrendSignalData error", e)
            null
        }
    }
}
