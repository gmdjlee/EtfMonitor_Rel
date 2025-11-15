package com.etfmonitor.repository

import android.util.Log
import com.chaquo.python.Python
import com.etfmonitor.database.MarketDepositDao
import com.etfmonitor.database.entities.MarketDeposit
import com.etfmonitor.oscillator.model.MarketDepositData
import com.etfmonitor.oscillator.python.OscillatorPyClient
import com.etfmonitor.utils.DateFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class MarketDepositRepository(
    private val marketDepositDao: MarketDepositDao,
    private val python: Python
) {
    companion object {
        private const val TAG = "MarketDepositRepository"
        private const val DATA_EXPIRY_HOURS = 12 // 12시간 후 데이터 만료
    }

    private val pyClient by lazy {
        OscillatorPyClient(python)
    }

    fun getAllDeposits(): Flow<List<MarketDeposit>> = marketDepositDao.getAllDeposits()

    fun getRecentDeposits(limit: Int = 100): Flow<List<MarketDeposit>> =
        marketDepositDao.getRecentDeposits(limit)

    suspend fun getDepositByDate(date: String): MarketDeposit? =
        marketDepositDao.getDepositByDate(date)

    suspend fun getDepositCount(): Int = marketDepositDao.getCount()

    suspend fun getLastUpdateTime(): Long? = marketDepositDao.getLastUpdateTime()

    /**
     * 증시 자금 데이터 초기화 (Python에서 가져와서 DB에 저장)
     */
    suspend fun initializeDeposits(numPages: Int = 10): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing market deposit data from Python...")

            // Python에서 증시 자금 데이터 가져오기
            val marketData = try {
                pyClient.getMarketDepositData(numPages)
            } catch (e: Exception) {
                Log.e(TAG, "Python call failed", e)
                return@withContext Result.failure(Exception("Python 모듈 호출 실패: ${e.message}", e))
            }

            if (marketData == null) {
                Log.e(TAG, "Failed to get market deposit data from Python")
                return@withContext Result.failure(Exception("Python 모듈 호출 실패: null 반환"))
            }

            // MarketDepositData를 MarketDeposit 엔티티 리스트로 변환
            val deposits = marketData.dates.mapIndexed { index, date ->
                MarketDeposit(
                    date = date,
                    depositAmount = marketData.depositAmounts[index],
                    depositChange = marketData.depositChanges[index],
                    creditAmount = marketData.creditAmounts[index],
                    creditChange = marketData.creditChanges[index],
                    lastUpdated = System.currentTimeMillis()
                )
            }

            if (deposits.isEmpty()) {
                Log.e(TAG, "No deposit data to save")
                return@withContext Result.failure(Exception("데이터가 비어있습니다"))
            }

            // DB에 일괄 저장
            marketDepositDao.deleteAll()
            marketDepositDao.insertAll(deposits)

            Log.d(TAG, "Successfully initialized ${deposits.size} market deposit records")
            Result.success(deposits.size)
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.w(TAG, "Initialization cancelled")
            throw e // CancellationException은 다시 던져야 함
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing market deposits", e)
            Result.failure(e)
        }
    }

    /**
     * 증시 자금 데이터 업데이트
     */
    suspend fun updateDeposits(numPages: Int = 10): Result<Int> {
        return initializeDeposits(numPages) // 전체 갱신
    }

    /**
     * 증시 자금 데이터 가져오기 (스마트 업데이트)
     * DB에 데이터가 있고 최신이면 DB에서, 없거나 오래되면 업데이트
     */
    suspend fun getOrUpdateMarketData(limit: Int = 100): MarketDepositData? = withContext(Dispatchers.IO) {
        try {
            // 1. DB에서 기존 데이터 확인
            val existingDeposits = marketDepositDao.getRecentDeposits(limit).first()

            val today = DateFormatter.formatToday()
            val shouldUpdate = shouldUpdateMarketData(existingDeposits, today)

            if (!shouldUpdate && existingDeposits.isNotEmpty()) {
                Log.d(TAG, "Using cached market deposit data (${existingDeposits.size} records)")
                return@withContext convertToMarketDepositData(existingDeposits)
            }

            // 2. 업데이트 필요 - 최신 데이터만 가져오기
            Log.d(TAG, "Fetching latest market deposit data from Python...")
            val latestMarketData = try {
                pyClient.getLatestMarketData()
            } catch (e: Exception) {
                Log.e(TAG, "Python call failed", e)
                // Python 실패 시 캐시된 데이터라도 반환
                return@withContext if (existingDeposits.isNotEmpty()) {
                    Log.d(TAG, "Returning cached market data due to Python error")
                    convertToMarketDepositData(existingDeposits)
                } else {
                    null
                }
            }

            if (latestMarketData == null) {
                Log.e(TAG, "Failed to fetch latest market data from Python")
                // Python 실패 시 캐시된 데이터라도 반환
                return@withContext if (existingDeposits.isNotEmpty()) {
                    Log.d(TAG, "Returning stale cached market data")
                    convertToMarketDepositData(existingDeposits)
                } else {
                    null
                }
            }

            // 3. 새 데이터를 DB에 저장 (기존 데이터 유지하면서 병합)
            val newDeposits = latestMarketData.dates.mapIndexed { index, date ->
                MarketDeposit(
                    date = date,
                    depositAmount = latestMarketData.depositAmounts[index],
                    depositChange = latestMarketData.depositChanges[index],
                    creditAmount = latestMarketData.creditAmounts[index],
                    creditChange = latestMarketData.creditChanges[index],
                    lastUpdated = System.currentTimeMillis()
                )
            }

            // DB에 저장 (REPLACE 전략으로 중복 제거)
            marketDepositDao.insertAll(newDeposits)
            Log.d(TAG, "Saved ${newDeposits.size} new market deposit records to DB")

            // 4. 업데이트된 전체 데이터 반환
            val updatedDeposits = marketDepositDao.getRecentDeposits(limit).first()
            convertToMarketDepositData(updatedDeposits)
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.w(TAG, "Market data fetch cancelled")
            throw e // CancellationException은 다시 던져야 함
        } catch (e: Exception) {
            Log.e(TAG, "Error getting or updating market data", e)
            // 에러 시 DB에 데이터가 있으면 반환
            val existingDeposits = marketDepositDao.getRecentDeposits(limit).first()
            if (existingDeposits.isNotEmpty()) {
                convertToMarketDepositData(existingDeposits)
            } else {
                null
            }
        }
    }

    /**
     * 마켓 데이터 업데이트가 필요한지 확인
     */
    private fun shouldUpdateMarketData(deposits: List<MarketDeposit>, today: String): Boolean {
        if (deposits.isEmpty()) {
            Log.d(TAG, "No cached data, update needed")
            return true // 데이터가 없으면 업데이트 필요
        }

        // 1. 마지막 업데이트 시간 확인
        val lastUpdate = deposits.maxOfOrNull { it.lastUpdated } ?: 0L
        val hoursSinceUpdate = (System.currentTimeMillis() - lastUpdate) / (1000 * 60 * 60)

        if (hoursSinceUpdate >= DATA_EXPIRY_HOURS) {
            Log.d(TAG, "Data expired (${hoursSinceUpdate}h old), update needed")
            return true
        }

        // 2. 최신 날짜가 오늘이 아니면 업데이트 필요
        val latestDate = deposits.maxOfOrNull { it.date } ?: ""
        if (latestDate != today) {
            Log.d(TAG, "Latest date ($latestDate) != today ($today), update needed")
            return true
        }

        Log.d(TAG, "Data is fresh, no update needed")
        return false
    }

    /**
     * MarketDeposit 리스트를 MarketDepositData로 변환
     */
    private fun convertToMarketDepositData(deposits: List<MarketDeposit>): MarketDepositData {
        // 날짜순 정렬 (오래된 것부터)
        val sorted = deposits.sortedBy { it.date }

        return MarketDepositData(
            dates = sorted.map { it.date },
            depositAmounts = sorted.map { it.depositAmount },
            depositChanges = sorted.map { it.depositChange },
            creditAmounts = sorted.map { it.creditAmount },
            creditChanges = sorted.map { it.creditChange }
        )
    }
}
