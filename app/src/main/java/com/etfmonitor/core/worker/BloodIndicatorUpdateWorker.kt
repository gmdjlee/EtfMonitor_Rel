package com.etfmonitor.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.feature.market.domain.repository.BloodIndicatorRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Blood Indicator Update Worker
 *
 * Background worker for periodic Blood Indicator data updates.
 * Fetches US market data (IRX, HYG, TNX, SPY) and calculates BLOOD indicator.
 */
@HiltWorker
class BloodIndicatorUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val bloodIndicatorRepository: BloodIndicatorRepository
) : CoroutineWorker(context, params) {

    companion object {
        private val logger = AppLogger.getLogger("BloodIndicatorWorker")
        const val WORK_NAME = "blood_indicator_update_work"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        logger.d("Starting Blood Indicator update work")

        try {
            val result = bloodIndicatorRepository.updateBloodIndicator()

            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                logger.d("Blood Indicator update completed: $count records")
                Result.success()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                logger.e("Blood Indicator update failed: $errorMsg")
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            logger.e("Error in Blood Indicator update work", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
