package com.etfmonitor.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.etfmonitor.repository.MarketOscillatorRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production Level MarketOscillatorUpdateWorker
 *
 * 최적화 포인트:
 * - @HiltWorker: Hilt가 Worker에 의존성 자동 주입
 * - @AssistedInject: WorkManager Context/Params와 Repository를 함께 주입
 * - withContext(Dispatchers.IO): IO 작업 명시적 격리
 */
@HiltWorker
class MarketOscillatorUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val marketOscillatorRepository: MarketOscillatorRepository
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MarketOscillatorWorker"
        const val WORK_NAME = "market_oscillator_update_work"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting market oscillator database update...")

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
