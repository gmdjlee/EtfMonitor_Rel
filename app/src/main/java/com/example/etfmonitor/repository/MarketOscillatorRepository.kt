package com.etfmonitor.repository

import android.util.Log
import com.etfmonitor.database.MarketOscillatorDao
import com.etfmonitor.database.entities.MarketOscillatorData
import com.etfmonitor.oscillator.python.OscillatorPyClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 시장 과매수/과매도 데이터 Repository
 */
class MarketOscillatorRepository(
    private val dao: MarketOscillatorDao,
    private val pyClient: OscillatorPyClient
) {
    companion object {
        private const val TAG = "MarketOscillatorRepo"
        private const val DEFAULT_KEEP_DAYS = 365 // 기본 1년치 데이터 유지
    }

    /**
     * 특정 시장의 모든 데이터 조회
     */
    fun getMarketData(market: String): Flow<List<MarketOscillatorData>> {
        return dao.getMarketData(market)
    }

    /**
     * 특정 시장의 최근 N일 데이터 조회
     */
    fun getRecentData(market: String, limit: Int = 15): Flow<List<MarketOscillatorData>> {
        return dao.getRecentData(market, limit)
    }

    /**
     * 특정 시장의 특정 기간 데이터 조회
     */
    fun getDataByDateRange(
        market: String,
        startDate: String,
        endDate: String
    ): Flow<List<MarketOscillatorData>> {
        return dao.getDataByDateRange(market, startDate, endDate)
    }

    /**
     * 특정 시장의 최신 데이터 조회
     */
    suspend fun getLatestData(market: String): MarketOscillatorData? {
        return dao.getLatestData(market)
    }

    /**
     * 특정 시장의 데이터 개수 조회
     */
    suspend fun getDataCount(market: String): Int {
        return dao.getDataCount(market)
    }

    /**
     * 초기 데이터 수집 (12개월)
     */
    suspend fun initializeMarketData(
        market: String,
        days: Int = 365,
        onProgress: ((String, Int) -> Unit)? = null
    ): Result<Int> {
        return try {
            Log.d(TAG, "Initializing $market data for $days days")
            onProgress?.invoke("$market 데이터 수집 준비 중...", 0)

            // 종료일: 오늘
            val endDate = LocalDate.now()
            // 시작일: days일 전
            val startDate = endDate.minusDays(days.toLong())

            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val startDateStr = startDate.format(formatter)
            val endDateStr = endDate.format(formatter)

            // Python에서 데이터 수집
            onProgress?.invoke("$market 시장 지수 데이터 수집 중...", 30)
            val jsonStr = pyClient.getMarketOscillator(market, startDateStr, endDateStr)
            val jsonObj = JSONObject(jsonStr)

            if (jsonObj.has("error")) {
                val error = jsonObj.getString("error")
                Log.e(TAG, "Error fetching market data: $error")
                return Result.failure(Exception(error))
            }

            onProgress?.invoke("$market 데이터 처리 중...", 70)

            // JSON 파싱
            val dates = jsonObj.getJSONArray("dates").let { arr ->
                List(arr.length()) { arr.getString(it) }
            }
            val indexValues = jsonObj.getJSONArray("index").let { arr ->
                List(arr.length()) { arr.getDouble(it) }
            }
            val oscillators = jsonObj.getJSONArray("oscillator").let { arr ->
                List(arr.length()) { arr.getDouble(it) }
            }

            // Entity 리스트 생성
            val dataList = dates.indices.map { i ->
                MarketOscillatorData(
                    id = "${market}-${dates[i]}",
                    market = market,
                    date = dates[i],
                    indexValue = indexValues[i],
                    oscillator = oscillators[i]
                )
            }

            // DB에 저장
            onProgress?.invoke("$market 데이터베이스 저장 중...", 90)
            dao.insertAll(dataList)

            Log.d(TAG, "Initialized $market with ${dataList.size} data points")
            onProgress?.invoke("$market 완료", 100)
            Result.success(dataList.size)

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing market data", e)
            Result.failure(e)
        }
    }

    /**
     * 데이터 업데이트 (최근 30일)
     */
    suspend fun updateMarketData(market: String): Result<Int> {
        return try {
            Log.d(TAG, "Updating $market data")

            // 최근 30일 데이터 수집
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(30)

            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val startDateStr = startDate.format(formatter)
            val endDateStr = endDate.format(formatter)

            // Python에서 데이터 수집
            val jsonStr = pyClient.getMarketOscillator(market, startDateStr, endDateStr)
            val jsonObj = JSONObject(jsonStr)

            if (jsonObj.has("error")) {
                val error = jsonObj.getString("error")
                Log.e(TAG, "Error updating market data: $error")
                return Result.failure(Exception(error))
            }

            // JSON 파싱
            val dates = jsonObj.getJSONArray("dates").let { arr ->
                List(arr.length()) { arr.getString(it) }
            }
            val indexValues = jsonObj.getJSONArray("index").let { arr ->
                List(arr.length()) { arr.getDouble(it) }
            }
            val oscillators = jsonObj.getJSONArray("oscillator").let { arr ->
                List(arr.length()) { arr.getDouble(it) }
            }

            // Entity 리스트 생성
            val dataList = dates.indices.map { i ->
                MarketOscillatorData(
                    id = "${market}-${dates[i]}",
                    market = market,
                    date = dates[i],
                    indexValue = indexValues[i],
                    oscillator = oscillators[i]
                )
            }

            // DB에 저장 (REPLACE 전략으로 업데이트)
            dao.insertAll(dataList)

            // 오래된 데이터 삭제 (1년치만 유지)
            dao.deleteOldData(market, DEFAULT_KEEP_DAYS)

            Log.d(TAG, "Updated $market with ${dataList.size} data points")
            Result.success(dataList.size)

        } catch (e: Exception) {
            Log.e(TAG, "Error updating market data", e)
            Result.failure(e)
        }
    }

    /**
     * 특정 시장 데이터 삭제
     */
    suspend fun deleteMarketData(market: String) {
        dao.deleteMarketData(market)
    }

    /**
     * 모든 데이터 삭제
     */
    suspend fun deleteAll() {
        dao.deleteAll()
    }
}
