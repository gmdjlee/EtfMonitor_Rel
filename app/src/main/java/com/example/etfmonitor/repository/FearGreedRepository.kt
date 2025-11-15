package com.etfmonitor.repository

import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.PyObject
import com.etfmonitor.database.FearGreedDao
import com.etfmonitor.database.entities.FearGreedIndex
import com.etfmonitor.utils.DateFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class FearGreedRepository(
    private val fearGreedDao: FearGreedDao,
    private val python: Python
) {
    companion object {
        private const val TAG = "FearGreedRepository"
        private const val DATA_EXPIRY_HOURS = 12 // 12시간 후 데이터 만료
    }

    fun getAllByMarket(market: String): Flow<List<FearGreedIndex>> =
        fearGreedDao.getAllByMarket(market)

    fun getRecentByMarket(market: String, limit: Int = 365): Flow<List<FearGreedIndex>> =
        fearGreedDao.getRecentByMarket(market, limit)

    fun getByMarketAndDateRange(market: String, startDate: String, endDate: String): Flow<List<FearGreedIndex>> =
        fearGreedDao.getByMarketAndDateRange(market, startDate, endDate)

    suspend fun getByMarketAndDate(market: String, date: String): FearGreedIndex? =
        fearGreedDao.getByMarketAndDate(market, date)

    suspend fun getCountByMarket(market: String): Int = fearGreedDao.getCountByMarket(market)

    suspend fun getLatestDate(market: String): String? = fearGreedDao.getLatestDate(market)

    suspend fun getLastUpdateTime(market: String): Long? = fearGreedDao.getLastUpdateTime(market)

    /**
     * Fear & Greed Index 데이터 초기화 (지정된 기간 동안의 데이터 수집)
     * @param days 데이터 수집 기간 (기본 365일)
     */
    suspend fun initializeFearGreed(days: Int = 365): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing Fear & Greed Index data for $days days...")

            // 날짜 범위 계산
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(days.toLong())

            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val startStr = startDate.format(formatter)
            val endStr = endDate.format(formatter)

            // Python에서 Fear & Greed 데이터 가져오기
            val fearGreedData = try {
                calculateFearGreed(startStr, endStr)
            } catch (e: Exception) {
                Log.e(TAG, "Python call failed", e)
                return@withContext Result.failure(Exception("Fear & Greed 계산 실패: ${e.message}", e))
            }

            if (fearGreedData.isEmpty()) {
                Log.e(TAG, "No Fear & Greed data calculated")
                return@withContext Result.failure(Exception("계산된 데이터가 없습니다"))
            }

            // DB에 저장
            fearGreedDao.deleteAll()
            fearGreedDao.insertAll(fearGreedData)

            Log.d(TAG, "Successfully initialized ${fearGreedData.size} Fear & Greed records")
            Result.success(fearGreedData.size)
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.w(TAG, "Initialization cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Fear & Greed data", e)
            Result.failure(e)
        }
    }

    /**
     * Fear & Greed Index 데이터 업데이트 (최근 데이터만 갱신)
     */
    suspend fun updateFearGreed(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Updating Fear & Greed Index data...")

            // 최근 30일 데이터 갱신
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(30)

            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val startStr = startDate.format(formatter)
            val endStr = endDate.format(formatter)

            // Python에서 Fear & Greed 데이터 가져오기
            val fearGreedData = try {
                calculateFearGreed(startStr, endStr)
            } catch (e: Exception) {
                Log.e(TAG, "Python call failed", e)
                return@withContext Result.failure(Exception("Fear & Greed 계산 실패: ${e.message}", e))
            }

            if (fearGreedData.isEmpty()) {
                Log.e(TAG, "No Fear & Greed data calculated")
                return@withContext Result.failure(Exception("계산된 데이터가 없습니다"))
            }

            // DB에 저장 (REPLACE 전략으로 중복 제거)
            fearGreedDao.insertAll(fearGreedData)

            Log.d(TAG, "Successfully updated ${fearGreedData.size} Fear & Greed records")
            Result.success(fearGreedData.size)
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.w(TAG, "Update cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Fear & Greed data", e)
            Result.failure(e)
        }
    }

    /**
     * Python 스크립트를 호출하여 Fear & Greed Index 계산
     */
    private suspend fun calculateFearGreed(startDate: String, endDate: String): List<FearGreedIndex> =
        withContext(Dispatchers.IO) {
            try {
                val module = python.getModule("feargreed")

                // combine 함수 호출하여 데이터 수집
                val combineFunc = module["combine"]
                val dfObject = combineFunc?.call(startDate, endDate) as? PyObject

                if (dfObject == null) {
                    Log.e(TAG, "Failed to get combined data from Python")
                    return@withContext emptyList()
                }

                // analyze 함수 호출하여 분석
                val analyzeFunc = module["analyze"]
                val result = analyzeFunc?.call(dfObject) as? PyObject

                if (result == null) {
                    Log.e(TAG, "Failed to analyze data from Python")
                    return@withContext emptyList()
                }

                // 결과 파싱 (KOSPI, KOSDAQ)
                val indices = mutableListOf<FearGreedIndex>()

                // KOSPI 데이터
                val kospiDf = result.asList()?.get(0) as? PyObject
                if (kospiDf != null && kospiDf.toString() != "None") {
                    indices.addAll(parseFearGreedData(kospiDf, "KOSPI"))
                }

                // KOSDAQ 데이터
                val kosdaqDf = result.asList()?.get(1) as? PyObject
                if (kosdaqDf != null && kosdaqDf.toString() != "None") {
                    indices.addAll(parseFearGreedData(kosdaqDf, "KOSDAQ"))
                }

                indices
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating Fear & Greed", e)
                emptyList()
            }
        }

    /**
     * Python DataFrame을 FearGreedIndex 리스트로 변환
     */
    private fun parseFearGreedData(df: PyObject, market: String): List<FearGreedIndex> {
        try {
            val indices = mutableListOf<FearGreedIndex>()

            // DataFrame을 딕셔너리 리스트로 변환
            val recordsFunc = df["to_dict"]
            val records = recordsFunc?.call("records") as? PyObject

            if (records == null) {
                Log.e(TAG, "Failed to convert DataFrame to records")
                return emptyList()
            }

            val recordsList = records.asList()

            for (record in recordsList) {
                try {
                    val recordMap = record.asMap()

                    val date = recordMap["거래일"]?.toString() ?: continue
                    val indexValue = recordMap[market]?.toDouble() ?: continue
                    val fg = recordMap["FG"]?.toDouble() ?: continue
                    val osc = recordMap["Osc"]?.toDouble() ?: continue
                    val rsi = recordMap["RSI"]?.toDouble() ?: 0.0
                    val mom = recordMap["Mom"]?.toDouble() ?: 0.0
                    val pcr = recordMap["PCR"]?.toDouble() ?: 0.0
                    val vol = recordMap["Vol"]?.toDouble() ?: 0.0
                    val spread = recordMap["Spread"]?.toDouble() ?: 0.0

                    // 날짜 형식 변환 (Timestamp -> YYYY-MM-DD)
                    val formattedDate = formatDate(date)

                    indices.add(
                        FearGreedIndex(
                            id = "$market-$formattedDate",
                            market = market,
                            date = formattedDate,
                            indexValue = indexValue,
                            fearGreedValue = fg,
                            oscillator = osc,
                            rsi = rsi,
                            momentum = mom,
                            putCallRatio = pcr,
                            volatility = vol,
                            spread = spread,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse record: $e")
                    continue
                }
            }

            return indices
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Fear & Greed data", e)
            return emptyList()
        }
    }

    /**
     * 날짜 형식 변환 (다양한 형식을 YYYY-MM-DD로 변환)
     */
    private fun formatDate(dateStr: String): String {
        return try {
            // Timestamp 형식 처리 (예: "2024-01-01 00:00:00")
            if (dateStr.contains(" ")) {
                dateStr.substring(0, 10)
            } else if (dateStr.contains("-")) {
                dateStr // 이미 YYYY-MM-DD 형식
            } else if (dateStr.length == 8) {
                // YYYYMMDD 형식
                "${dateStr.substring(0, 4)}-${dateStr.substring(4, 6)}-${dateStr.substring(6, 8)}"
            } else {
                dateStr
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to format date: $dateStr", e)
            dateStr
        }
    }

    /**
     * 데이터 업데이트가 필요한지 확인
     */
    private suspend fun shouldUpdateData(market: String): Boolean {
        val lastUpdate = getLastUpdateTime(market) ?: return true
        val hoursSinceUpdate = (System.currentTimeMillis() - lastUpdate) / (1000 * 60 * 60)

        if (hoursSinceUpdate >= DATA_EXPIRY_HOURS) {
            return true
        }

        val today = DateFormatter.formatToday()
        val latestDate = getLatestDate(market)

        return latestDate != today
    }
}
