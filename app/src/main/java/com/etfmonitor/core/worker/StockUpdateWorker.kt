package com.etfmonitor.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.etfmonitor.repository.StockRepository
import com.etfmonitor.core.common.util.AppLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production Level StockUpdateWorker
 *
 * 최적화 포인트:
 * 1. @HiltWorker: Hilt가 Worker에 의존성 자동 주입
 * 2. @AssistedInject: WorkManager Context/Params와 Repository를 함께 주입
 * 3. withContext(Dispatchers.IO): IO 작업 명시적 격리
 *
 * 기존 문제점 해결:
 * - EtfMonitorApp 캐스팅 제거: 타입 안정성 향상
 * - 직접 의존성 접근 제거: Hilt가 자동 주입하여 테스트 용이성 증가
 */
@HiltWorker
class StockUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val stockRepository: StockRepository
) : CoroutineWorker(context, params) {

    companion object {
        private val logger = AppLogger.getLogger("StockUpdateWorker")
        const val WORK_NAME = "stock_update_work"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            logger.d("Starting stock database update...")

            val result = stockRepository.updateStocks()

            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                logger.d("Successfully updated $count stocks")
                Result.success()
            } else {
                logger.e("Failed to update stocks: ${result.exceptionOrNull()?.message}")
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            logger.e("Error in StockUpdateWorker", e)
            Result.failure()
        }
    }
}
