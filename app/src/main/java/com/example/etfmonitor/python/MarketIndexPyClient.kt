package com.etfmonitor.python

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.etfmonitor.database.entities.MarketIndex
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * KOSPI/KOSDAQ 시장 지수 데이터 수집을 위한 Python 클라이언트
 * market_index_fetcher.py 모듈 사용
 */
@Singleton
class MarketIndexPyClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MarketIndexPyClient"
        private const val TIMEOUT_MS = 30_000L
    }

    private val python = Python.getInstance()
    private val module = python.getModule("market_index_fetcher")
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 지정된 기간 동안의 시장 지수 데이터 수집
     *
     * @param startDate 시작 날짜 (YYYYMMDD 형식)
     * @param endDate 종료 날짜 (YYYYMMDD 형식)
     * @param markets 수집할 시장 목록 (기본값: ["KOSPI", "KOSDAQ"])
     * @return 수집된 MarketIndex 리스트
     */
    suspend fun fetchMarketIndices(
        startDate: String,
        endDate: String,
        markets: List<String> = listOf("KOSPI", "KOSDAQ")
    ): List<MarketIndex> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching market indices: markets=$markets, from=$startDate to=$endDate")

            withTimeout(TIMEOUT_MS) {
                val result = module.callAttr(
                    "fetch_all_markets",
                    startDate,
                    endDate,
                    markets
                ).toString()

                val dtoList = json.decodeFromString<List<MarketIndexDto>>(result)

                dtoList.map { dto ->
                    MarketIndex(
                        date = dto.date,
                        market = dto.market,
                        closePrice = dto.closePrice,
                        openPrice = dto.openPrice,
                        highPrice = dto.highPrice,
                        lowPrice = dto.lowPrice,
                        volume = dto.volume,
                        changeRate = dto.changeRate,
                        lastUpdated = System.currentTimeMillis()
                    )
                }.also {
                    Log.d(TAG, "Successfully fetched ${it.size} market index records")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching market indices", e)
            emptyList()
        }
    }

    /**
     * 최근 N일의 시장 지수 데이터 수집
     *
     * @param days 수집할 일수 (기본 30일)
     * @param markets 수집할 시장 목록
     * @return 수집된 MarketIndex 리스트
     */
    suspend fun fetchRecentDays(
        days: Int = 30,
        markets: List<String> = listOf("KOSPI", "KOSDAQ")
    ): List<MarketIndex> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching recent $days days for markets: $markets")

            withTimeout(TIMEOUT_MS) {
                val result = module.callAttr(
                    "fetch_recent_days",
                    days,
                    markets
                ).toString()

                val dtoList = json.decodeFromString<List<MarketIndexDto>>(result)

                dtoList.map { dto ->
                    MarketIndex(
                        date = dto.date,
                        market = dto.market,
                        closePrice = dto.closePrice,
                        openPrice = dto.openPrice,
                        highPrice = dto.highPrice,
                        lowPrice = dto.lowPrice,
                        volume = dto.volume,
                        changeRate = dto.changeRate,
                        lastUpdated = System.currentTimeMillis()
                    )
                }.also {
                    Log.d(TAG, "Successfully fetched ${it.size} recent market index records")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching recent market indices", e)
            emptyList()
        }
    }

    /**
     * 특정 시장의 최신 지수 데이터 조회
     *
     * @param market "KOSPI" 또는 "KOSDAQ"
     * @return 최신 MarketIndex 또는 null
     */
    suspend fun getLatestIndex(market: String): MarketIndex? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting latest index for market: $market")

            withTimeout(TIMEOUT_MS) {
                val result = module.callAttr("get_latest_index", market)

                // Python에서 None을 반환하면 null로 처리
                if (result.toString() == "None") {
                    Log.w(TAG, "No latest index data found for $market")
                    return@withTimeout null
                }

                val dto = json.decodeFromString<MarketIndexDto>(result.toString())

                MarketIndex(
                    date = dto.date,
                    market = dto.market,
                    closePrice = dto.closePrice,
                    openPrice = dto.openPrice,
                    highPrice = dto.highPrice,
                    lowPrice = dto.lowPrice,
                    volume = dto.volume,
                    changeRate = dto.changeRate,
                    lastUpdated = System.currentTimeMillis()
                ).also {
                    Log.d(TAG, "Successfully fetched latest index for $market: date=${it.date}, close=${it.closePrice}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting latest index for $market", e)
            null
        }
    }

    /**
     * Python 모듈에서 받는 JSON 데이터 형식
     */
    @Serializable
    private data class MarketIndexDto(
        val date: String,
        val market: String,
        val closePrice: Double,
        val openPrice: Double,
        val highPrice: Double,
        val lowPrice: Double,
        val volume: Long,
        val changeRate: Double
    )
}
