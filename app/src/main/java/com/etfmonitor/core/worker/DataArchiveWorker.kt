package com.etfmonitor.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.common.util.DataArchiver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 데이터 아카이빙 백그라운드 워커
 *
 * 주기적으로 실행되어 오래된 데이터를 압축/정리:
 * - 실행 주기: 월 1회 (첫째 주 일요일 새벽 3시 권장)
 * - 5년 이상 데이터 삭제
 * - 3~5년 데이터를 월별 스냅샷으로 압축
 * - 1~3년 데이터를 주별 스냅샷으로 압축
 */
@HiltWorker
class DataArchiveWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val archiver: DataArchiver
) : CoroutineWorker(context, params) {

    companion object {
        private val logger = AppLogger.getLogger("DataArchiveWorker")
        const val WORK_NAME = "data_archive_work"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            logger.d("Starting data archiving...")

            // 아카이빙 실행
            val result = archiver.archiveData()

            if (result.success) {
                logger.d("Archiving completed successfully")
                logger.d("- Deleted: ${result.deletedRecords} records")
                logger.d("- Weekly compressed: ${result.weeklyCompressed} records")
                logger.d("- Monthly compressed: ${result.monthlyCompressed} records")
                logger.d("- Total records: ${result.totalRecords}")

                Result.success()
            } else {
                logger.e("Archiving failed: ${result.error}")
                Result.failure()
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Error during archiving", e)
            if (runAttemptCount < 3) {
                logger.d("Retrying... (attempt ${runAttemptCount + 1}/3)")
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
