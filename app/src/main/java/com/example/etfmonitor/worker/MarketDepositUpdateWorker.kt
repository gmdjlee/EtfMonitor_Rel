package com.etfmonitor.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.etfmonitor.EtfMonitorApp
import com.etfmonitor.repository.MarketDepositRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 매일 지정된 시간에 증시 자금 데이터 DB를 업데이트하는 Worker
 */
class MarketDepositUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MarketDepositUpdateWorker"
        const val WORK_NAME = "market_deposit_update_work"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting market deposit database update...")

            val app = applicationContext as EtfMonitorApp
            val marketDepositRepository = MarketDepositRepository(
                marketDepositDao = app.database.marketDepositDao(),
                python = app.python
            )

            // 10페이지 정도 데이터 수집 (약 100일치)
            val result = marketDepositRepository.updateDeposits(numPages = 10)

            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                Log.d(TAG, "Successfully updated $count market deposit records")
                Result.success()
            } else {
                Log.e(TAG, "Failed to update market deposits: ${result.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in MarketDepositUpdateWorker", e)
            Result.failure()
        }
    }
}
