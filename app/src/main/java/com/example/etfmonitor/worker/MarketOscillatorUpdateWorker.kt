package com.etfmonitor.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.etfmonitor.EtfMonitorApp

/**
 * 매일 지정된 시간에 시장 과매수/과매도 데이터를 업데이트하는 Worker
 */
class MarketOscillatorUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MarketOscillatorWorker"
        const val WORK_NAME = "market_oscillator_update_work"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting market oscillator database update...")

            val app = applicationContext as? EtfMonitorApp
            if (app == null) {
                Log.e(TAG, "Application context is not EtfMonitorApp")
                return Result.failure()
            }

            // Use singleton repository from EtfMonitorApp for optimized memory usage
            val marketOscillatorRepository = app.marketOscillatorRepository

            // Update both KOSPI and KOSDAQ
            val kospiResult = marketOscillatorRepository.updateMarketData("KOSPI")
            val kosdaqResult = marketOscillatorRepository.updateMarketData("KOSDAQ")

            if (kospiResult.isSuccess && kosdaqResult.isSuccess) {
                val kospiCount = kospiResult.getOrNull() ?: 0
                val kosdaqCount = kosdaqResult.getOrNull() ?: 0
                Log.d(TAG, "Successfully updated KOSPI: $kospiCount, KOSDAQ: $kosdaqCount records")
                Result.success()
            } else {
                val error = kospiResult.exceptionOrNull() ?: kosdaqResult.exceptionOrNull()
                Log.e(TAG, "Failed to update market oscillator data: ${error?.message}", error)
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in MarketOscillatorUpdateWorker", e)
            Result.failure()
        }
    }
}
