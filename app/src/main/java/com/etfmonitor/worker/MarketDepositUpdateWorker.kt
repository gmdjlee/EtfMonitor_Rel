package com.etfmonitor.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.etfmonitor.repository.MarketDepositRepository
import com.etfmonitor.utils.AppLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production Level MarketDepositUpdateWorker
 *
 * 최적화 포인트:
 * - @HiltWorker: Hilt가 Worker에 의존성 자동 주입
 * - @AssistedInject: WorkManager Context/Params와 Repository를 함께 주입
 * - withContext(Dispatchers.IO): IO 작업 명시적 격리
 */
@HiltWorker
class MarketDepositUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val marketDepositRepository: MarketDepositRepository
) : CoroutineWorker(context, params) {

    companion object {
        private val logger = AppLogger.getLogger("MarketDepositUpdateWorker")
        const val WORK_NAME = "market_deposit_update_work"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            logger.d("Starting market deposit database update...")

            // 10페이지 정도 데이터 수집 (약 100일치)
            val result = marketDepositRepository.updateDeposits(numPages = 10)

            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                logger.d("Successfully updated $count market deposit records")
                Result.success()
            } else {
                val error = result.exceptionOrNull()
                error?.let { logger.e("Failed to update market deposits: ${it.message}", it) }
                    ?: logger.e("Failed to update market deposits: unknown error")
                Result.retry()
            }
        } catch (e: Exception) {
            logger.e("Error in MarketDepositUpdateWorker", e)
            Result.failure()
        }
    }
}
