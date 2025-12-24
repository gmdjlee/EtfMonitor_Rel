package com.etfmonitor.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.etfmonitor.feature.market.domain.repository.MarketIndexRepository
import com.etfmonitor.core.common.util.AppLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 시장 지수(KOSPI/KOSDAQ) 데이터 업데이트 Worker
 *
 * 매일 지정된 시간에 시장 지수 데이터를 자동으로 업데이트합니다.
 * 상관관계 분석 등 시장 지수 기반 분석 기능에 필요합니다.
 */
@HiltWorker
class MarketIndexUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val marketIndexRepository: MarketIndexRepository
) : CoroutineWorker(context, params) {

    companion object {
        private val logger = AppLogger.getLogger("MarketIndexUpdateWorker")
        const val WORK_NAME = "market_index_update_work"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            logger.d("Starting market index database update...")

            // 최근 30일 데이터 업데이트
            val result = marketIndexRepository.updateMarketIndex(days = 30)

            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                logger.d("Successfully updated $count market index records")
                Result.success()
            } else {
                val error = result.exceptionOrNull()
                error?.let { logger.e("Failed to update market index: ${it.message}", it) }
                    ?: logger.e("Failed to update market index: unknown error")
                Result.retry()
            }
        } catch (e: Exception) {
            logger.e("Error in MarketIndexUpdateWorker", e)
            Result.failure()
        }
    }
}
