package com.etfmonitor.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.etfmonitor.repository.DataProgress
import com.etfmonitor.repository.DataRepository
import com.etfmonitor.core.common.util.AppLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.withContext

/**
 * ETF 데이터 업데이트 Worker
 *
 * ETF Holdings 데이터를 백그라운드에서 자동으로 업데이트합니다.
 * 마지막 수집일 이후의 새로운 영업일 데이터만 수집합니다.
 *
 * 스케줄링:
 * - 기본 시간: 00:30 (자정 30분)
 * - 주기: 매일
 * - 조건: 네트워크 연결 필요
 *
 * @see DataRepository.updateData
 */
@HiltWorker
class EtfUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dataRepository: DataRepository
) : CoroutineWorker(context, params) {

    companion object {
        private val logger = AppLogger.getLogger("EtfUpdateWorker")
        const val WORK_NAME = "etf_update_work"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            logger.d("Starting ETF data update...")

            var lastProgress: DataProgress? = null

            dataRepository.updateData()
                .catch { e ->
                    logger.e("Error during ETF update", e)
                    lastProgress = DataProgress.Error(e.message ?: "Unknown error")
                }
                .collect { progress ->
                    lastProgress = progress
                    when (progress) {
                        is DataProgress.Loading -> {
                            logger.d("ETF update progress: ${progress.message} (${progress.progress}%)")
                        }
                        is DataProgress.Success -> {
                            logger.d("ETF update success: ${progress.message}")
                        }
                        is DataProgress.Error -> {
                            logger.e("ETF update error: ${progress.message}")
                        }
                    }
                }

            when (lastProgress) {
                is DataProgress.Success -> {
                    logger.d("ETF data update completed successfully")
                    Result.success()
                }
                is DataProgress.Error -> {
                    val errorMsg = (lastProgress as DataProgress.Error).message
                    logger.e("ETF data update failed: $errorMsg")
                    // 재시도 가능한 에러인 경우 retry
                    if (runAttemptCount < 3) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
                else -> {
                    logger.w("ETF update ended with unexpected state")
                    Result.success()
                }
            }
        } catch (e: Exception) {
            logger.e("Error in EtfUpdateWorker", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
