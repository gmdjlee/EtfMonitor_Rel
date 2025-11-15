package com.etfmonitor.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.etfmonitor.EtfMonitorApp

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

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting market deposit database update...")

            val app = applicationContext as? EtfMonitorApp
            if (app == null) {
                Log.e(TAG, "Application context is not EtfMonitorApp")
                return Result.failure()
            }

            // Use singleton repository from EtfMonitorApp for optimized memory usage
            // 10페이지 정도 데이터 수집 (약 100일치)
            val result = app.marketDepositRepository.updateDeposits(numPages = 10)

            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                Log.d(TAG, "Successfully updated $count market deposit records")
                Result.success()
            } else {
                val error = result.exceptionOrNull()
                Log.e(TAG, "Failed to update market deposits: ${error?.message}", error)
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in MarketDepositUpdateWorker", e)
            Result.failure()
        }
    }
}
