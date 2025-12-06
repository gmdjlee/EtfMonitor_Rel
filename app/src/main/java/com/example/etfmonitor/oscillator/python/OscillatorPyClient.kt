package com.etfmonitor.oscillator.python

import android.util.Log
import com.chaquo.python.Python
import com.etfmonitor.oscillator.model.DemarkTDData
import com.etfmonitor.oscillator.model.ElderImpulseData
import com.etfmonitor.oscillator.model.MarketDepositData
import com.etfmonitor.oscillator.model.StockData
import com.etfmonitor.oscillator.model.TrendSignalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Python 기반 오실레이터 데이터 클라이언트
 *
 * Production 최적화:
 * - @Singleton: Hilt가 단일 인스턴스 관리
 * - @Inject: 생성자 주입으로 의존성 명확화
 * - withTimeout: 모든 Python 호출에 30초 타임아웃 적용
 * - withContext(Dispatchers.IO): 모든 Python 호출을 IO 스레드에서 실행
 * - kotlinx.serialization: 타입 안전한 JSON 파싱
 */
@Singleton
class OscillatorPyClient @Inject constructor(private val python: Python) {

    companion object {
        private const val TAG = "OscillatorPyClient"
        private const val TIMEOUT_MS = 30_000L
        private const val MARKET_OSCILLATOR_TIMEOUT_MS = 180_000L  // 3분 - 시장 전체 종목 분석에 필요
    }

    // kotlinx.serialization 설정
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    // JSON 파싱용 DTO 클래스들
    @Serializable
    private data class SearchStockResponse(
        val ticker: String = "",
        val name: String = "",
        val error: String? = null
    )

    @Serializable
    private data class StockAnalysisResponse(
        val ticker: String = "",
        val name: String = "",
        val dates: List<String> = emptyList(),
        @SerialName("market_cap") val marketCap: List<Long> = emptyList(),
        @SerialName("foreign_5d") val foreign5d: List<Long> = emptyList(),
        @SerialName("institution_5d") val institution5d: List<Long> = emptyList(),
        val error: String? = null
    )

    @Serializable
    private data class MarketDepositResponse(
        val dates: List<String> = emptyList(),
        @SerialName("deposit_amounts") val depositAmounts: List<Double> = emptyList(),
        @SerialName("deposit_changes") val depositChanges: List<Double> = emptyList(),
        @SerialName("credit_amounts") val creditAmounts: List<Double> = emptyList(),
        @SerialName("credit_changes") val creditChanges: List<Double> = emptyList(),
        val error: String? = null
    )

    @Serializable
    private data class StockListItem(
        val ticker: String,
        val name: String
    )

    @Serializable
    private data class TrendSignalResponse(
        val ticker: String = "",
        val name: String = "",
        val interval: String = "",
        val dates: List<String> = emptyList(),
        val open: List<Double> = emptyList(),
        val high: List<Double> = emptyList(),
        val low: List<Double> = emptyList(),
        val close: List<Double> = emptyList(),
        val volume: List<Long> = emptyList(),
        val ma: List<Double> = emptyList(),
        val cmf: List<Double> = emptyList(),
        @SerialName("fear_greed") val fearGreed: List<Double> = emptyList(),
        @SerialName("buy_signal") val buySignal: List<Int> = emptyList(),
        @SerialName("aux_buy_signal") val auxBuySignal: List<Int> = emptyList(),
        @SerialName("sell_signal") val sellSignal: List<Int> = emptyList(),
        @SerialName("aux_sell_signal") val auxSellSignal: List<Int> = emptyList(),
        val error: String? = null
    )

    @Serializable
    private data class ElderImpulseResponse(
        val ticker: String = "",
        val name: String = "",
        val interval: String = "",
        val dates: List<String> = emptyList(),
        val close: List<Double> = emptyList(),
        @SerialName("market_cap") val marketCap: List<Long> = emptyList(),
        val ema: List<Double> = emptyList(),
        val macd: List<Double> = emptyList(),
        @SerialName("macd_signal") val macdSignal: List<Double> = emptyList(),
        @SerialName("macd_hist") val macdHist: List<Double> = emptyList(),
        val impulse: List<Int> = emptyList(),
        val error: String? = null
    )

    @Serializable
    private data class DemarkTDResponse(
        val ticker: String = "",
        val name: String = "",
        val interval: String = "",
        @SerialName("interval_name") val intervalName: String = "",
        val dates: List<String> = emptyList(),
        val close: List<Double> = emptyList(),
        @SerialName("market_cap") val marketCap: List<Long> = emptyList(),
        @SerialName("td_sell") val tdSell: List<Int> = emptyList(),
        @SerialName("td_buy") val tdBuy: List<Int> = emptyList(),
        val error: String? = null
    )

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
            withTimeout(TIMEOUT_MS) {
                Log.d(TAG, "searchStock: $query")
                val jsonStr = stocksModule.callAttr("search_stock_wrapper", query).toString()
                val response = json.decodeFromString<SearchStockResponse>(jsonStr)

                if (response.error != null) {
                    Log.e(TAG, "Search error: ${response.error}")
                    return@withTimeout null
                }

                Log.d(TAG, "Found stock: ${response.ticker} - ${response.name}")
                Pair(response.ticker, response.name)
            }
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
                withTimeout(TIMEOUT_MS) {
                    Log.d(TAG, "getStockAnalysis: $ticker, $days days")
                    val jsonStr = stocksModule.callAttr("get_stock_analysis", ticker, days).toString()
                    val response = json.decodeFromString<StockAnalysisResponse>(jsonStr)

                    if (response.error != null) {
                        Log.e(TAG, "Analysis error: ${response.error}")
                        return@withTimeout null
                    }

                    StockData(
                        ticker = response.ticker,
                        name = response.name,
                        dates = response.dates,
                        marketCap = response.marketCap,
                        foreign5d = response.foreign5d,
                        institution5d = response.institution5d
                    ).also {
                        Log.d(TAG, "Stock analysis complete: ${it.name}, ${response.dates.size} data points")
                    }
                }
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
                withTimeout(TIMEOUT_MS) {
                    Log.d(TAG, "getMarketDepositData: $numPages pages")
                    val jsonStr = depositModule.callAttr("get_market_deposit_data", numPages).toString()
                    val response = json.decodeFromString<MarketDepositResponse>(jsonStr)

                    if (response.error != null) {
                        Log.e(TAG, "Market data error: ${response.error}")
                        return@withTimeout null
                    }

                    MarketDepositData(
                        dates = response.dates,
                        depositAmounts = response.depositAmounts,
                        depositChanges = response.depositChanges,
                        creditAmounts = response.creditAmounts,
                        creditChanges = response.creditChanges
                    ).also {
                        Log.d(TAG, "Market deposit data complete: ${response.dates.size} data points")
                    }
                }
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
            withTimeout(TIMEOUT_MS) {
                Log.d(TAG, "getLatestMarketData")
                val jsonStr = depositModule.callAttr("get_latest_market_data").toString()
                val response = json.decodeFromString<MarketDepositResponse>(jsonStr)

                if (response.error != null) {
                    Log.e(TAG, "Latest market data error: ${response.error}")
                    return@withTimeout null
                }

                MarketDepositData(
                    dates = response.dates,
                    depositAmounts = response.depositAmounts,
                    depositChanges = response.depositChanges,
                    creditAmounts = response.creditAmounts,
                    creditChanges = response.creditChanges
                )
            }
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
            withTimeout(TIMEOUT_MS) {
                Log.d(TAG, "getAllStocksList")
                val jsonStr = stocksModule.callAttr("get_all_stocks_list").toString()

                if (jsonStr.contains("\"error\"")) {
                    Log.e(TAG, "Error getting stocks list")
                    return@withTimeout emptyList()
                }

                val stockList = json.decodeFromString<List<StockListItem>>(jsonStr)
                stockList.map { Pair(it.ticker, it.name) }
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
            // 시장 오실레이터는 전체 구성종목 데이터를 수집해야 하므로 더 긴 타임아웃 사용
            withTimeout(MARKET_OSCILLATOR_TIMEOUT_MS) {
                Log.d(TAG, "getMarketOscillator: $market, $startDate ~ $endDate (timeout: ${MARKET_OSCILLATOR_TIMEOUT_MS}ms)")
                val jsonStr = marketModule.callAttr(
                    "get_market_oscillator",
                    market,
                    startDate,
                    endDate
                ).toString()

                Log.d(TAG, "Market oscillator data retrieved for $market")
                jsonStr
            }
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
            withTimeout(TIMEOUT_MS) {
                Log.d(TAG, "getTrendSignalData: $ticker, $days days, interval: $interval")
                val jsonStr = trendSignalModule.callAttr(
                    "get_trend_signal_analysis",
                    ticker,
                    days,
                    interval
                ).toString()

                val response = json.decodeFromString<TrendSignalResponse>(jsonStr)

                if (response.error != null) {
                    Log.e(TAG, "Trend signal error: ${response.error}")
                    return@withTimeout null
                }

                TrendSignalData(
                    ticker = response.ticker,
                    name = response.name,
                    interval = response.interval,
                    dates = response.dates,
                    open = response.open,
                    high = response.high,
                    low = response.low,
                    close = response.close,
                    volume = response.volume,
                    ma = response.ma,
                    cmf = response.cmf,
                    fearGreed = response.fearGreed,
                    buySignal = response.buySignal,
                    auxBuySignal = response.auxBuySignal,
                    sellSignal = response.sellSignal,
                    auxSellSignal = response.auxSellSignal
                ).also {
                    Log.d(TAG, "Trend signal data complete: ${it.name}, ${response.dates.size} data points")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getTrendSignalData error", e)
            null
        }
    }

    /**
     * Elder Impulse System 분석 데이터 수집 (주봉 기준)
     *
     * @param ticker 종목 코드
     * @param days 분석 기간 (일)
     * @return ElderImpulseData 또는 null
     */
    suspend fun getElderImpulseData(
        ticker: String,
        days: Int = 365
    ): ElderImpulseData? = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                Log.d(TAG, "getElderImpulseData: $ticker, $days days")
                val jsonStr = trendSignalModule.callAttr(
                    "get_elder_impulse_analysis",
                    ticker,
                    days
                ).toString()

                val response = json.decodeFromString<ElderImpulseResponse>(jsonStr)

                if (response.error != null) {
                    Log.e(TAG, "Elder Impulse error: ${response.error}")
                    return@withTimeout null
                }

                ElderImpulseData(
                    ticker = response.ticker,
                    name = response.name,
                    interval = response.interval,
                    dates = response.dates,
                    close = response.close,
                    marketCap = response.marketCap,
                    ema = response.ema,
                    macd = response.macd,
                    macdSignal = response.macdSignal,
                    macdHist = response.macdHist,
                    impulse = response.impulse
                ).also {
                    Log.d(TAG, "Elder Impulse data complete: ${it.name}, ${response.dates.size} data points")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getElderImpulseData error", e)
            null
        }
    }

    /**
     * DeMark TD Setup 분석 데이터 수집
     *
     * @param ticker 종목 코드
     * @param days 분석 기간 (일)
     * @param interval 주기 ("d"=일별, "w"=주별, "m"=월별)
     * @return DemarkTDData 또는 null
     */
    suspend fun getDemarkTDData(
        ticker: String,
        days: Int = 365,
        interval: String = "w"
    ): DemarkTDData? = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                Log.d(TAG, "getDemarkTDData: $ticker, $days days, interval: $interval")
                val jsonStr = trendSignalModule.callAttr(
                    "get_demark_td_analysis",
                    ticker,
                    days,
                    interval
                ).toString()

                val response = json.decodeFromString<DemarkTDResponse>(jsonStr)

                if (response.error != null) {
                    Log.e(TAG, "DeMark TD error: ${response.error}")
                    return@withTimeout null
                }

                DemarkTDData(
                    ticker = response.ticker,
                    name = response.name,
                    interval = response.interval,
                    intervalName = response.intervalName,
                    dates = response.dates,
                    close = response.close,
                    marketCap = response.marketCap,
                    tdSell = response.tdSell,
                    tdBuy = response.tdBuy
                ).also {
                    Log.d(TAG, "DeMark TD data complete: ${it.name}, ${response.dates.size} data points, ${it.intervalName}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getDemarkTDData error", e)
            null
        }
    }
}
