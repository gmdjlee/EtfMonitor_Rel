package com.etfmonitor.core.worker

import android.content.Context
import androidx.work.*
import com.etfmonitor.core.common.util.AppLogger
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WorkManagerHelper {
    private val logger = AppLogger.getLogger("WorkManagerHelper")

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
        logger.d("Scheduling $taskName update for ${hour}:${minute}")

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

        logger.d("Initial delay: ${initialDelay / 1000 / 60} minutes")

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

        logger.d("$taskName update scheduled successfully")
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
        logger.d("Cancelling $taskName update schedule")
        WorkManager.getInstance(context).cancelUniqueWork(workName)
    }

    /**
     * 즉시 수동 업데이트 실행 (Generic)
     */
    private inline fun <reified W : CoroutineWorker> runUpdateNow(
        context: Context,
        taskName: String
    ) {
        logger.d("Running $taskName update immediately")

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

    // ==================== ETF Update Worker ====================

    /**
     * 매일 지정된 시간에 ETF 데이터 업데이트 작업 스케줄링
     *
     * ETF Holdings 데이터를 매일 자동으로 업데이트합니다.
     * 마지막 수집일 이후의 새로운 영업일 데이터만 수집합니다.
     *
     * @param context Context
     * @param hour 업데이트할 시간 (0-23), 기본값: 0 (자정)
     * @param minute 업데이트할 분 (0-59), 기본값: 30
     */
    fun scheduleEtfUpdate(context: Context, hour: Int = 0, minute: Int = 30) {
        scheduleDailyUpdate<EtfUpdateWorker>(
            context = context,
            hour = hour,
            minute = minute,
            workName = EtfUpdateWorker.WORK_NAME,
            taskName = "etf"
        )
    }

    /**
     * ETF 데이터 스케줄링 취소
     */
    fun cancelEtfUpdate(context: Context) {
        cancelUpdate(context, EtfUpdateWorker.WORK_NAME, "etf")
    }

    /**
     * 즉시 ETF 데이터 수동 업데이트 실행
     */
    fun runEtfUpdateNow(context: Context) {
        runUpdateNow<EtfUpdateWorker>(context, "etf")
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
     * 매일 지정된 시간에 시장 지수 DB 업데이트 작업 스케줄링
     *
     * @param context Context
     * @param hour 업데이트할 시간 (0-23)
     * @param minute 업데이트할 분 (0-59)
     */
    fun scheduleMarketIndexUpdate(context: Context, hour: Int, minute: Int) {
        scheduleDailyUpdate<MarketIndexUpdateWorker>(
            context = context,
            hour = hour,
            minute = minute,
            workName = MarketIndexUpdateWorker.WORK_NAME,
            taskName = "market index"
        )
    }

    /**
     * 시장 지수 스케줄링 취소
     */
    fun cancelMarketIndexUpdate(context: Context) {
        cancelUpdate(context, MarketIndexUpdateWorker.WORK_NAME, "market index")
    }

    /**
     * 즉시 시장 지수 수동 업데이트 실행
     */
    fun runMarketIndexUpdateNow(context: Context) {
        runUpdateNow<MarketIndexUpdateWorker>(context, "market index")
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
        logger.d("Scheduling data archiving (monthly at 3:00 AM)")

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

        logger.d("Data archiving scheduled successfully")
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
        logger.d("Running data archiving immediately")

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DataArchiveWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    // ==================== 고급 분석 Worker ====================

    /**
     * 매일 지정된 시간에 고급 분석 작업 스케줄링
     *
     * 수행 작업:
     * - 시총 가중 ETF 흐름 분석
     * - 외국인/기관 수급 Divergence 분석
     * - 유동성 분석 (예탁금/시총 비율)
     * - 섹터별 Fear & Greed 분석
     * - ETF 간 상관관계 분석
     *
     * @param context Context
     * @param hour 업데이트할 시간 (0-23), 기본값: 18 (장 마감 후)
     * @param minute 업데이트할 분 (0-59), 기본값: 30
     */
    fun scheduleAdvancedAnalysis(context: Context, hour: Int = 18, minute: Int = 30) {
        scheduleDailyUpdate<AdvancedAnalysisWorker>(
            context = context,
            hour = hour,
            minute = minute,
            workName = AdvancedAnalysisWorker.WORK_NAME,
            taskName = "advanced analysis"
        )
    }

    /**
     * 고급 분석 스케줄링 취소
     */
    fun cancelAdvancedAnalysis(context: Context) {
        cancelUpdate(context, AdvancedAnalysisWorker.WORK_NAME, "advanced analysis")
    }

    /**
     * 즉시 고급 분석 수동 실행
     */
    fun runAdvancedAnalysisNow(context: Context) {
        runUpdateNow<AdvancedAnalysisWorker>(context, "advanced analysis")
    }

    /**
     * ETF 데이터 수집 완료 후 고급 분석 체인 실행
     *
     * ETF 수집 → 고급 분석 순서로 실행
     * 수집이 완료된 후 자동으로 분석 시작
     */
    fun runEtfCollectionThenAdvancedAnalysis(context: Context) {
        logger.d("Scheduling ETF collection followed by advanced analysis")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // 고급 분석 작업 (ETF 수집 후 실행)
        val analysisRequest = OneTimeWorkRequestBuilder<AdvancedAnalysisWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueue(analysisRequest)

        logger.d("Advanced analysis chained after ETF collection")
    }
}
