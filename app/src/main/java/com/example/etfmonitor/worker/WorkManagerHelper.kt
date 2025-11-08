package com.etfmonitor.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WorkManagerHelper {
    private const val TAG = "WorkManagerHelper"

    /**
     * 매일 지정된 시간에 주식 DB 업데이트 작업 스케줄링
     *
     * @param context Context
     * @param hour 업데이트할 시간 (0-23)
     * @param minute 업데이트할 분 (0-59)
     */
    fun scheduleStockUpdate(context: Context, hour: Int, minute: Int) {
        Log.d(TAG, "Scheduling stock update for ${hour}:${minute}")

        // 다음 실행 시간 계산
        val currentTime = Calendar.getInstance()
        val scheduledTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)

            // 이미 지난 시간이면 다음날로 설정
            if (before(currentTime)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val initialDelay = scheduledTime.timeInMillis - currentTime.timeInMillis

        Log.d(TAG, "Initial delay: ${initialDelay / 1000 / 60} minutes")

        // Constraints 설정 (네트워크 필요)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // 매일 반복 작업 생성
        val workRequest = PeriodicWorkRequestBuilder<StockUpdateWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(StockUpdateWorker.WORK_NAME)
            .build()

        // 기존 작업 취소 후 새로 등록
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(StockUpdateWorker.WORK_NAME)
            enqueueUniquePeriodicWork(
                StockUpdateWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }

        Log.d(TAG, "Stock update scheduled successfully")
    }

    /**
     * 스케줄링 취소
     */
    fun cancelStockUpdate(context: Context) {
        Log.d(TAG, "Cancelling stock update schedule")
        WorkManager.getInstance(context).cancelUniqueWork(StockUpdateWorker.WORK_NAME)
    }

    /**
     * 즉시 수동 업데이트 실행
     */
    fun runStockUpdateNow(context: Context) {
        Log.d(TAG, "Running stock update immediately")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<StockUpdateWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    /**
     * 매일 지정된 시간에 증시 자금 DB 업데이트 작업 스케줄링
     *
     * @param context Context
     * @param hour 업데이트할 시간 (0-23)
     * @param minute 업데이트할 분 (0-59)
     */
    fun scheduleMarketDepositUpdate(context: Context, hour: Int, minute: Int) {
        Log.d(TAG, "Scheduling market deposit update for ${hour}:${minute}")

        // 다음 실행 시간 계산
        val currentTime = Calendar.getInstance()
        val scheduledTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)

            // 이미 지난 시간이면 다음날로 설정
            if (before(currentTime)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val initialDelay = scheduledTime.timeInMillis - currentTime.timeInMillis

        Log.d(TAG, "Initial delay: ${initialDelay / 1000 / 60} minutes")

        // Constraints 설정 (네트워크 필요)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // 매일 반복 작업 생성
        val workRequest = PeriodicWorkRequestBuilder<MarketDepositUpdateWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(MarketDepositUpdateWorker.WORK_NAME)
            .build()

        // 기존 작업 취소 후 새로 등록
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(MarketDepositUpdateWorker.WORK_NAME)
            enqueueUniquePeriodicWork(
                MarketDepositUpdateWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }

        Log.d(TAG, "Market deposit update scheduled successfully")
    }

    /**
     * 증시 자금 스케줄링 취소
     */
    fun cancelMarketDepositUpdate(context: Context) {
        Log.d(TAG, "Cancelling market deposit update schedule")
        WorkManager.getInstance(context).cancelUniqueWork(MarketDepositUpdateWorker.WORK_NAME)
    }

    /**
     * 즉시 증시 자금 수동 업데이트 실행
     */
    fun runMarketDepositUpdateNow(context: Context) {
        Log.d(TAG, "Running market deposit update immediately")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<MarketDepositUpdateWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
