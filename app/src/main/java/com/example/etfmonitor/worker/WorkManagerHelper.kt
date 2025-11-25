package com.etfmonitor.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WorkManagerHelper {
    private const val TAG = "WorkManagerHelper"

    /**
     * 매일 지정된 시간에 작업 스케줄링 (Generic)
     *
     * @param context Context
     * @param hour 업데이트할 시간 (0-23)
     * @param minute 업데이트할 분 (0-59)
     * @param workName 작업 고유 이름
     * @param taskName 로깅용 작업 이름
     */
    private inline fun <reified W : CoroutineWorker> scheduleDailyUpdate(
        context: Context,
        hour: Int,
        minute: Int,
        workName: String,
        taskName: String
    ) {
        Log.d(TAG, "Scheduling $taskName update for ${hour}:${minute}")

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
        val workRequest = PeriodicWorkRequestBuilder<W>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(workName)
            .build()

        // 기존 작업 취소 후 새로 등록
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(workName)
            enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }

        Log.d(TAG, "$taskName update scheduled successfully")
    }

    /**
     * 매일 지정된 시간에 주식 DB 업데이트 작업 스케줄링
     *
     * @param context Context
     * @param hour 업데이트할 시간 (0-23)
     * @param minute 업데이트할 분 (0-59)
     */
    fun scheduleStockUpdate(context: Context, hour: Int, minute: Int) {
        scheduleDailyUpdate<StockUpdateWorker>(
            context = context,
            hour = hour,
            minute = minute,
            workName = StockUpdateWorker.WORK_NAME,
            taskName = "stock"
        )
    }

    /**
     * 작업 취소 (Generic)
     */
    private fun cancelUpdate(context: Context, workName: String, taskName: String) {
        Log.d(TAG, "Cancelling $taskName update schedule")
        WorkManager.getInstance(context).cancelUniqueWork(workName)
    }

    /**
     * 즉시 수동 업데이트 실행 (Generic)
     */
    private inline fun <reified W : CoroutineWorker> runUpdateNow(
        context: Context,
        taskName: String
    ) {
        Log.d(TAG, "Running $taskName update immediately")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<W>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    /**
     * 스케줄링 취소
     */
    fun cancelStockUpdate(context: Context) {
        cancelUpdate(context, StockUpdateWorker.WORK_NAME, "stock")
    }

    /**
     * 즉시 수동 업데이트 실행
     */
    fun runStockUpdateNow(context: Context) {
        runUpdateNow<StockUpdateWorker>(context, "stock")
    }

    /**
     * 매일 지정된 시간에 증시 자금 DB 업데이트 작업 스케줄링
     *
     * @param context Context
     * @param hour 업데이트할 시간 (0-23)
     * @param minute 업데이트할 분 (0-59)
     */
    fun scheduleMarketDepositUpdate(context: Context, hour: Int, minute: Int) {
        scheduleDailyUpdate<MarketDepositUpdateWorker>(
            context = context,
            hour = hour,
            minute = minute,
            workName = MarketDepositUpdateWorker.WORK_NAME,
            taskName = "market deposit"
        )
    }

    /**
     * 증시 자금 스케줄링 취소
     */
    fun cancelMarketDepositUpdate(context: Context) {
        cancelUpdate(context, MarketDepositUpdateWorker.WORK_NAME, "market deposit")
    }

    /**
     * 즉시 증시 자금 수동 업데이트 실행
     */
    fun runMarketDepositUpdateNow(context: Context) {
        runUpdateNow<MarketDepositUpdateWorker>(context, "market deposit")
    }

    /**
     * 매일 지정된 시간에 Fear & Greed Index DB 업데이트 작업 스케줄링
     *
     * @param context Context
     * @param hour 업데이트할 시간 (0-23)
     * @param minute 업데이트할 분 (0-59)
     */
    fun scheduleFearGreedUpdate(context: Context, hour: Int, minute: Int) {
        scheduleDailyUpdate<FearGreedUpdateWorker>(
            context = context,
            hour = hour,
            minute = minute,
            workName = FearGreedUpdateWorker.WORK_NAME,
            taskName = "fear greed index"
        )
    }

    /**
     * Fear & Greed Index 스케줄링 취소
     */
    fun cancelFearGreedUpdate(context: Context) {
        cancelUpdate(context, FearGreedUpdateWorker.WORK_NAME, "fear greed index")
    }

    /**
     * 즉시 Fear & Greed Index 수동 업데이트 실행
     */
    fun runFearGreedUpdateNow(context: Context) {
        runUpdateNow<FearGreedUpdateWorker>(context, "fear greed index")
    }

    /**
     * 매일 지정된 시간에 시장 과매수/과매도 DB 업데이트 작업 스케줄링
     *
     * @param context Context
     * @param hour 업데이트할 시간 (0-23)
     * @param minute 업데이트할 분 (0-59)
     */
    fun scheduleMarketOscillatorUpdate(context: Context, hour: Int, minute: Int) {
        scheduleDailyUpdate<MarketOscillatorUpdateWorker>(
            context = context,
            hour = hour,
            minute = minute,
            workName = MarketOscillatorUpdateWorker.WORK_NAME,
            taskName = "market oscillator"
        )
    }

    /**
     * 시장 과매수/과매도 스케줄링 취소
     */
    fun cancelMarketOscillatorUpdate(context: Context) {
        cancelUpdate(context, MarketOscillatorUpdateWorker.WORK_NAME, "market oscillator")
    }

    /**
     * 즉시 시장 과매수/과매도 수동 업데이트 실행
     */
    fun runMarketOscillatorUpdateNow(context: Context) {
        runUpdateNow<MarketOscillatorUpdateWorker>(context, "market oscillator")
    }

    /**
     * 월 1회 데이터 아카이빙 작업 스케줄링
     * - 실행 주기: 매월 1일 새벽 3시
     * - 5년 이상 데이터 삭제
     * - 3~5년 데이터를 월별 스냅샷으로 압축
     * - 1~3년 데이터를 주별 스냅샷으로 압축
     *
     * @param context Context
     */
    fun scheduleDataArchiving(context: Context) {
        Log.d(TAG, "Scheduling data archiving (monthly at 3:00 AM)")

        // Constraints 설정 (배터리 충전 중, 네트워크 연결 시에만 실행)
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        // 매월 1회 반복 작업 생성 (30일 주기)
        val workRequest = PeriodicWorkRequestBuilder<DataArchiveWorker>(
            repeatInterval = 30,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setInitialDelay(calculateInitialDelayForMonthly(), TimeUnit.MILLISECONDS)
            .addTag(DataArchiveWorker.WORK_NAME)
            .build()

        // 기존 작업 취소 후 새로 등록
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(DataArchiveWorker.WORK_NAME)
            enqueueUniquePeriodicWork(
                DataArchiveWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        Log.d(TAG, "Data archiving scheduled successfully")
    }

    /**
     * 매월 1일 새벽 3시까지의 초기 지연 시간 계산
     */
    private fun calculateInitialDelayForMonthly(): Long {
        val currentTime = Calendar.getInstance()
        val scheduledTime = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)

            // 이미 지난 시간이면 다음 달로 설정
            if (before(currentTime)) {
                add(Calendar.MONTH, 1)
            }
        }

        return scheduledTime.timeInMillis - currentTime.timeInMillis
    }

    /**
     * 데이터 아카이빙 스케줄링 취소
     */
    fun cancelDataArchiving(context: Context) {
        cancelUpdate(context, DataArchiveWorker.WORK_NAME, "data archiving")
    }

    /**
     * 즉시 데이터 아카이빙 실행
     */
    fun runDataArchivingNow(context: Context) {
        Log.d(TAG, "Running data archiving immediately")

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DataArchiveWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
