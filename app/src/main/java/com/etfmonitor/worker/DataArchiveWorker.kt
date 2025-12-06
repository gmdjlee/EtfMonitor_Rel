package com.etfmonitor.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.etfmonitor.utils.DataArchiver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
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
        private const val TAG = "DataArchiveWorker"
        const val WORK_NAME = "data_archive_work"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting data archiving...")

            // 아카이빙 실행
            val result = archiver.archiveData()

            if (result.success) {
                Log.d(TAG, "Archiving completed successfully")
                Log.d(TAG, "- Deleted: ${result.deletedRecords} records")
                Log.d(TAG, "- Weekly compressed: ${result.weeklyCompressed} records")
                Log.d(TAG, "- Monthly compressed: ${result.monthlyCompressed} records")
                Log.d(TAG, "- Total records: ${result.totalRecords}")

                Result.success()
            } else {
                Log.e(TAG, "Archiving failed: ${result.error}")
                Result.failure()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during archiving", e)
            if (runAttemptCount < 3) {
                Log.d(TAG, "Retrying... (attempt ${runAttemptCount + 1}/3)")
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
