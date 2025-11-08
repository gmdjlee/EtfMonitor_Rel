package com.etfmonitor.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.etfmonitor.EtfMonitorApp
import com.etfmonitor.repository.StockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 매일 지정된 시간에 주식 종목 DB를 업데이트하는 Worker
 */
class StockUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "StockUpdateWorker"
        const val WORK_NAME = "stock_update_work"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting stock database update...")

            val app = applicationContext as EtfMonitorApp
            val stockRepository = StockRepository(
                stockDao = app.database.stockDao(),
                python = app.python
            )

            val result = stockRepository.updateStocks()

            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                Log.d(TAG, "Successfully updated $count stocks")
                Result.success()
            } else {
                Log.e(TAG, "Failed to update stocks: ${result.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in StockUpdateWorker", e)
            Result.failure()
        }
    }
}
