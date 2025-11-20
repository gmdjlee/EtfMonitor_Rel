package com.etfmonitor.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.etfmonitor.repository.FearGreedRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production Level FearGreedUpdateWorker
 *
 * 최적화 포인트:
 * - @HiltWorker: Hilt가 Worker에 의존성 자동 주입
 * - @AssistedInject: WorkManager Context/Params와 Repository를 함께 주입
 * - withContext(Dispatchers.IO): IO 작업 명시적 격리
 */
@HiltWorker
class FearGreedUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val fearGreedRepository: FearGreedRepository
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "FearGreedUpdateWorker"
        const val WORK_NAME = "fear_greed_update_work"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting Fear & Greed Index database update...")

            val result = fearGreedRepository.updateFearGreed()

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
