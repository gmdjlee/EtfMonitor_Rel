package com.example.etfmonitor.oscillator.python

import android.util.Log
import com.chaquo.python.Python
import com.example.etfmonitor.oscillator.model.MarketDepositData
import com.example.etfmonitor.oscillator.model.StockData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class OscillatorPyClient(private val python: Python) {

    companion object {
        private const val TAG = "OscillatorPyClient"
    }

    private val analyzerModule by lazy {
        Log.d(TAG, "Loading stock_analyzer module")
        python.getModule("stock_analyzer")
    }

    /**
     * 종목 검색
     */
    suspend fun searchStock(query: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "searchStock: $query")
            val jsonStr = analyzerModule.callAttr("search_stock_wrapper", query).toString()
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
            val jsonStr = analyzerModule.callAttr("get_stock_analysis", ticker, days).toString()
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
            val jsonStr = analyzerModule.callAttr("get_market_deposit_data", numPages).toString()
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
            val jsonStr = analyzerModule.callAttr("get_latest_market_data").toString()
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
            val jsonStr = analyzerModule.callAttr("get_all_stocks_list").toString()

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
}
