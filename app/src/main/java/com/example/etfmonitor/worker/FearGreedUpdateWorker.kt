package com.etfmonitor.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.etfmonitor.EtfMonitorApp

/**
 * 매일 지정된 시간에 Fear & Greed Index 데이터를 업데이트하는 Worker
 */
class FearGreedUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "FearGreedUpdateWorker"
        const val WORK_NAME = "fear_greed_update_work"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting Fear & Greed Index database update...")

            val app = applicationContext as? EtfMonitorApp
            if (app == null) {
                Log.e(TAG, "Application context is not EtfMonitorApp")
                return Result.failure()
            }

            // Use singleton repository from EtfMonitorApp for optimized memory usage
            val result = app.fearGreedRepository.updateFearGreed()

            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                Log.d(TAG, "Successfully updated $count Fear & Greed Index records")
                Result.success()
            } else {
                val error = result.exceptionOrNull()
                Log.e(TAG, "Failed to update Fear & Greed Index: ${error?.message}", error)
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in FearGreedUpdateWorker", e)
            Result.failure()
        }
    }
}
