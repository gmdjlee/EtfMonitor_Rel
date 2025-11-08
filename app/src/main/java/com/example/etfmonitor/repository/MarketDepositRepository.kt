package com.etfmonitor.repository

import android.util.Log
import com.chaquo.python.Python
import com.etfmonitor.database.MarketDepositDao
import com.etfmonitor.database.entities.MarketDeposit
import com.etfmonitor.oscillator.python.OscillatorPyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MarketDepositRepository(
    private val marketDepositDao: MarketDepositDao,
    private val python: Python
) {
    companion object {
        private const val TAG = "MarketDepositRepository"
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
            val marketData = pyClient.getMarketDepositData(numPages)

            if (marketData == null) {
                Log.e(TAG, "Failed to get market deposit data from Python")
                return@withContext Result.failure(Exception("Python 모듈 호출 실패"))
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
}
