package com.etfmonitor.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.MainActivity
import com.etfmonitor.R
import com.etfmonitor.feature.etf.domain.model.DataProgress
import com.etfmonitor.feature.etf.domain.repository.EtfRepository
import com.etfmonitor.feature.market.domain.repository.BloodIndicatorRepository
import com.etfmonitor.feature.market.domain.repository.FearGreedRepository
import com.etfmonitor.feature.market.domain.repository.MarketDepositRepository
import com.etfmonitor.feature.market.domain.repository.MarketIndexRepository
import com.etfmonitor.feature.market.domain.repository.MarketOscillatorRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Production Level DataCollectionService
 *
 * 최적화 포인트:
 * - @AndroidEntryPoint: Hilt가 Service에 의존성 자동 주입
 * - @Inject: Repository 자동 주입
 */
@AndroidEntryPoint
class DataCollectionService : Service() {

    @Inject
    lateinit var etfRepository: EtfRepository

    @Inject
    lateinit var fearGreedRepository: FearGreedRepository

    @Inject
    lateinit var marketOscillatorRepository: MarketOscillatorRepository

    @Inject
    lateinit var marketIndexRepository: MarketIndexRepository

    @Inject
    lateinit var marketDepositRepository: MarketDepositRepository

    @Inject
    lateinit var bloodIndicatorRepository: BloodIndicatorRepository

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    // WakeLock to keep CPU awake during background data sync
    private var wakeLock: PowerManager.WakeLock? = null
    private val powerManager by lazy {
        getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    // Unified initialization parameters (stored from intent extras)
    private var pendingDepositPages: Int? = null
    private var pendingFearGreedDays: Int? = null
    private var pendingOscillatorDays: Int? = null
    private var pendingMarketIndexDays: Int? = null
    private var pendingBloodIndicatorDays: Int? = null

    companion object {
        private val logger = AppLogger.getLogger("DataCollectSvc")
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "data_collection_channel"
        private const val CHANNEL_NAME = "데이터 수집"
        private const val WAKELOCK_TAG = "EtfMonitor:DataCollectionWakeLock"
        private const val WAKELOCK_TIMEOUT_MS = 180 * 60 * 1000L  // 3 hours max (FearGreed 90d + Oscillator 365d)

        const val ACTION_INITIALIZE = "action_initialize"
        const val ACTION_INITIALIZE_ALL = "action_initialize_all"
        const val ACTION_UPDATE = "action_update"
        const val ACTION_STOP = "action_stop"
        const val EXTRA_DAYS = "extra_days"
        const val EXTRA_DEPOSIT_PAGES = "extra_deposit_pages"
        const val EXTRA_FEAR_GREED_DAYS = "extra_fear_greed_days"
        const val EXTRA_OSCILLATOR_DAYS = "extra_oscillator_days"
        const val EXTRA_MARKET_INDEX_DAYS = "extra_market_index_days"
        const val EXTRA_BLOOD_INDICATOR_DAYS = "extra_blood_indicator_days"

        fun startInitialize(context: Context, days: Int) {
            val intent = Intent(context, DataCollectionService::class.java).apply {
                action = ACTION_INITIALIZE
                putExtra(EXTRA_DAYS, days)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Unified initialization - handles all data types in background
         * This method is safe for screen-off and app background scenarios
         */
        fun startInitializeAll(
            context: Context,
            etfDays: Int,
            depositPages: Int?,
            fearGreedDays: Int?,
            oscillatorDays: Int?,
            marketIndexDays: Int?,
            bloodIndicatorDays: Int? = null
        ) {
            val intent = Intent(context, DataCollectionService::class.java).apply {
                action = ACTION_INITIALIZE_ALL
                putExtra(EXTRA_DAYS, etfDays)
                depositPages?.let { putExtra(EXTRA_DEPOSIT_PAGES, it) }
                fearGreedDays?.let { putExtra(EXTRA_FEAR_GREED_DAYS, it) }
                oscillatorDays?.let { putExtra(EXTRA_OSCILLATOR_DAYS, it) }
                marketIndexDays?.let { putExtra(EXTRA_MARKET_INDEX_DAYS, it) }
                bloodIndicatorDays?.let { putExtra(EXTRA_BLOOD_INDICATOR_DAYS, it) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startUpdate(context: Context) {
            val intent = Intent(context, DataCollectionService::class.java).apply {
                action = ACTION_UPDATE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DataCollectionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        logger.d("Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.d("onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_INITIALIZE -> {
                val days = intent.getIntExtra(EXTRA_DAYS, 25)
                acquireWakeLock()
                CollectionState.startCollection(isInitialize = true, initialMessage = "초기화 준비 중...")
                startForeground(NOTIFICATION_ID, createNotification("초기화 준비 중...", 0))
                startInitialization(days)
            }
            ACTION_INITIALIZE_ALL -> {
                val etfDays = intent.getIntExtra(EXTRA_DAYS, 25)
                pendingDepositPages = if (intent.hasExtra(EXTRA_DEPOSIT_PAGES)) {
                    intent.getIntExtra(EXTRA_DEPOSIT_PAGES, 5)
                } else null
                pendingFearGreedDays = if (intent.hasExtra(EXTRA_FEAR_GREED_DAYS)) {
                    intent.getIntExtra(EXTRA_FEAR_GREED_DAYS, 90)
                } else null
                pendingOscillatorDays = if (intent.hasExtra(EXTRA_OSCILLATOR_DAYS)) {
                    intent.getIntExtra(EXTRA_OSCILLATOR_DAYS, 365)
                } else null
                pendingMarketIndexDays = if (intent.hasExtra(EXTRA_MARKET_INDEX_DAYS)) {
                    intent.getIntExtra(EXTRA_MARKET_INDEX_DAYS, 30)
                } else null
                pendingBloodIndicatorDays = if (intent.hasExtra(EXTRA_BLOOD_INDICATOR_DAYS)) {
                    intent.getIntExtra(EXTRA_BLOOD_INDICATOR_DAYS, 365)
                } else null
                acquireWakeLock()
                CollectionState.startCollection(isInitialize = true, initialMessage = "통합 초기화 준비 중...")
                startForeground(NOTIFICATION_ID, createNotification("통합 초기화 준비 중...", 0))
                startUnifiedInitialization(etfDays)
            }
            ACTION_UPDATE -> {
                acquireWakeLock()
                CollectionState.startCollection(isInitialize = false, initialMessage = "업데이트 준비 중...")
                startForeground(NOTIFICATION_ID, createNotification("업데이트 준비 중...", 0))
                startUpdate()
            }
            ACTION_STOP -> {
                CollectionState.reset()
                releaseWakeLock()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKELOCK_TAG
            ).apply {
                setReferenceCounted(false)
                acquire(WAKELOCK_TIMEOUT_MS)
            }
            logger.d("WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                logger.d("WakeLock released")
            }
        }
        wakeLock = null
    }

    private fun startInitialization(days: Int) {
        serviceScope.launch {
            logger.d("Starting initialization with $days days")

            // ETF 데이터 초기화 (단독으로 실행, 완료 후 HomeViewModel이 다음 단계 처리)
            etfRepository.initializeData(days)
                .catch { e ->
                    logger.e("Error in ETF initialization", e)
                    val errorMsg = "ETF 초기화 실패: ${e.message}"
                    CollectionState.error(errorMsg)
                    updateNotification(errorMsg, 0, isError = true)
                    stopSelf()
                }
                .collect { progress ->
                    when (progress) {
                        is DataProgress.Loading -> {
                            CollectionState.updateProgress(progress.message, progress.progress)
                            updateNotification(progress.message, progress.progress)
                        }
                        is DataProgress.Success -> {
                            // ETF 초기화 완료, 시장 지수 초기화 시작
                            logger.d("ETF initialization completed: ${progress.message}")
                            initializeMarketIndex(days)
                        }
                        is DataProgress.Error -> {
                            CollectionState.error(progress.message)
                            updateNotification(progress.message, 0, isError = true)
                            stopSelf()
                        }
                    }
                }
        }
    }

    private fun initializeMarketIndex(days: Int) {
        serviceScope.launch {
            try {
                logger.d("Starting Market Index initialization")
                updateNotification("시장 지수 데이터 수집 중...", 91)

                val result = marketIndexRepository.initializeMarketIndex(days)

                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    val successMsg = "초기화 완료! 시장 지수 ${count}개 데이터"
                    logger.d(successMsg)
                    CollectionState.complete(successMsg)
                    updateNotification(successMsg, 100, isComplete = true)
                } else {
                    val errorMsg = "시장 지수 초기화 실패: ${result.exceptionOrNull()?.message}"
                    logger.e(errorMsg)
                    CollectionState.error(errorMsg)
                    updateNotification(errorMsg, 0, isError = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Error in Market Index initialization", e)
                val errorMsg = "시장 지수 초기화 실패: ${e.message}"
                CollectionState.error(errorMsg)
                updateNotification(errorMsg, 0, isError = true)
            } finally {
                releaseWakeLock()
                stopSelf()
            }
        }
    }

    /**
     * Unified initialization - runs all data collection in sequence within the service
     * This is safe for screen-off and app background scenarios
     */
    private fun startUnifiedInitialization(etfDays: Int) {
        serviceScope.launch {
            logger.d("Starting unified initialization - ETF: $etfDays days, " +
                     "Deposit: $pendingDepositPages pages, " +
                     "FearGreed: $pendingFearGreedDays days, " +
                     "Oscillator: $pendingOscillatorDays days, " +
                     "MarketIndex: $pendingMarketIndexDays days, " +
                     "BloodIndicator: $pendingBloodIndicatorDays days")

            // Step 1: ETF 데이터 초기화
            etfRepository.initializeData(etfDays)
                .catch { e ->
                    logger.e("Error in ETF initialization", e)
                    val errorMsg = "ETF 초기화 실패: ${e.message}"
                    CollectionState.error(errorMsg)
                    updateNotification(errorMsg, 0, isError = true)
                    releaseWakeLock()
                    stopSelf()
                }
                .collect { progress ->
                    when (progress) {
                        is DataProgress.Loading -> {
                            CollectionState.updateProgress(progress.message, (progress.progress * 0.3).toInt())
                            updateNotification(progress.message, (progress.progress * 0.3).toInt())
                        }
                        is DataProgress.Success -> {
                            logger.d("ETF initialization completed: ${progress.message}")
                            // Continue to Market Index
                            initializeMarketIndexForUnified(etfDays)
                        }
                        is DataProgress.Error -> {
                            CollectionState.error(progress.message)
                            updateNotification(progress.message, 0, isError = true)
                            releaseWakeLock()
                            stopSelf()
                        }
                    }
                }
        }
    }

    private fun initializeMarketIndexForUnified(etfDays: Int) {
        serviceScope.launch {
            val marketIndexDays = pendingMarketIndexDays
            if (marketIndexDays != null) {
                try {
                    logger.d("Starting Market Index initialization (unified): $marketIndexDays days")
                    CollectionState.updateProgress("시장 지수 데이터 수집 중...", 35)
                    updateNotification("시장 지수 데이터 수집 중...", 35)

                    val result = marketIndexRepository.initializeMarketIndex(marketIndexDays)

                    if (result.isSuccess) {
                        val count = result.getOrNull() ?: 0
                        logger.d("Market Index initialization completed: $count records")
                    } else {
                        val errorMsg = "시장 지수 초기화 실패: ${result.exceptionOrNull()?.message}"
                        logger.e(errorMsg)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e("Error in Market Index initialization", e)
                }
            } else {
                logger.d("Market Index initialization skipped (not selected)")
            }
            // Continue to next step
            continueWithDeposit()
        }
    }

    private fun continueWithDeposit() {
        serviceScope.launch {
            val depositPages = pendingDepositPages
            if (depositPages != null) {
                try {
                    logger.d("Starting Market Deposit initialization: $depositPages pages")
                    CollectionState.updateProgress("증시 자금 동향 수집 중...", 45)
                    updateNotification("증시 자금 동향 수집 중...", 45)

                    val result = marketDepositRepository.initializeDeposits(depositPages) { message, progress ->
                        val adjustedProgress = 45 + (progress * 0.1).toInt()
                        CollectionState.updateProgress(message, adjustedProgress)
                        updateNotification(message, adjustedProgress)
                    }

                    if (result.isSuccess) {
                        logger.d("Market Deposit initialization completed: ${result.getOrNull()} records")
                    } else {
                        logger.e("Market Deposit initialization failed: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e("Error in Market Deposit initialization", e)
                }
            }
            // Continue to Fear & Greed
            continueWithFearGreed()
        }
    }

    private fun continueWithFearGreed() {
        serviceScope.launch {
            val fearGreedDays = pendingFearGreedDays
            if (fearGreedDays != null) {
                try {
                    logger.d("Starting Fear & Greed initialization: $fearGreedDays days")
                    CollectionState.updateProgress("Fear & Greed Index 수집 중...", 60)
                    updateNotification("Fear & Greed Index 수집 중...", 60)

                    val result = fearGreedRepository.initializeFearGreed(fearGreedDays) { message, progress ->
                        val adjustedProgress = 60 + (progress * 0.2).toInt()
                        CollectionState.updateProgress(message, adjustedProgress)
                        updateNotification(message, adjustedProgress)
                    }

                    if (result.isSuccess) {
                        logger.d("Fear & Greed initialization completed: ${result.getOrNull()} records")
                    } else {
                        logger.e("Fear & Greed initialization failed: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e("Error in Fear & Greed initialization", e)
                }
            }
            // Continue to Market Oscillator
            continueWithOscillator()
        }
    }

    private fun continueWithOscillator() {
        serviceScope.launch {
            val oscillatorDays = pendingOscillatorDays
            if (oscillatorDays != null) {
                try {
                    logger.d("Starting Market Oscillator initialization: $oscillatorDays days")
                    CollectionState.updateProgress("과매수/과매도 지표 수집 중...", 80)
                    updateNotification("과매수/과매도 지표 수집 중...", 80)

                    val kospiResult = marketOscillatorRepository.initializeMarketData("KOSPI", oscillatorDays) { message, progress ->
                        val adjustedProgress = 80 + (progress * 0.05).toInt()
                        CollectionState.updateProgress("KOSPI $message", adjustedProgress)
                        updateNotification("KOSPI $message", adjustedProgress)
                    }

                    val kosdaqResult = marketOscillatorRepository.initializeMarketData("KOSDAQ", oscillatorDays) { message, progress ->
                        val adjustedProgress = 85 + (progress * 0.05).toInt()
                        CollectionState.updateProgress("KOSDAQ $message", adjustedProgress)
                        updateNotification("KOSDAQ $message", adjustedProgress)
                    }

                    if (kospiResult.isSuccess && kosdaqResult.isSuccess) {
                        val totalCount = (kospiResult.getOrNull() ?: 0) + (kosdaqResult.getOrNull() ?: 0)
                        logger.d("Market Oscillator initialization completed: $totalCount records")
                    } else {
                        logger.e("Market Oscillator initialization failed")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e("Error in Market Oscillator initialization", e)
                }
            }
            // Continue to Blood Indicator
            continueWithBloodIndicator()
        }
    }

    private fun continueWithBloodIndicator() {
        serviceScope.launch {
            val bloodIndicatorDays = pendingBloodIndicatorDays
            if (bloodIndicatorDays != null) {
                try {
                    logger.d("Starting Blood Indicator initialization: $bloodIndicatorDays days")
                    CollectionState.updateProgress("Blood Indicator 수집 중...", 92)
                    updateNotification("Blood Indicator 수집 중...", 92)

                    val result = bloodIndicatorRepository.initializeBloodIndicator(bloodIndicatorDays) { message, progress ->
                        val adjustedProgress = 92 + (progress * 0.08).toInt()
                        CollectionState.updateProgress(message, adjustedProgress)
                        updateNotification(message, adjustedProgress)
                    }

                    if (result.isSuccess) {
                        val count = result.getOrNull() ?: 0
                        logger.d("Blood Indicator initialization completed: $count records")
                    } else {
                        logger.e("Blood Indicator initialization failed: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e("Error in Blood Indicator initialization", e)
                }
            }
            // All done
            finishUnifiedInitialization()
        }
    }

    private fun finishUnifiedInitialization() {
        val successMsg = "모든 데이터 초기화 완료"
        logger.d(successMsg)
        CollectionState.complete(successMsg)
        updateNotification(successMsg, 100, isComplete = true)

        // Clear pending values
        pendingDepositPages = null
        pendingFearGreedDays = null
        pendingOscillatorDays = null
        pendingMarketIndexDays = null
        pendingBloodIndicatorDays = null

        releaseWakeLock()
        stopSelf()
    }

    private fun startUpdate() {
        serviceScope.launch {
            logger.d("Starting update")

            // Step 1: ETF 데이터 업데이트
            etfRepository.updateData()
                .catch { e ->
                    logger.e("Error in ETF update", e)
                    val errorMsg = "ETF 업데이트 실패: ${e.message}"
                    CollectionState.error(errorMsg)
                    updateNotification(errorMsg, 0, isError = true)
                    stopSelf()
                }
                .collect { progress ->
                    when (progress) {
                        is DataProgress.Loading -> {
                            CollectionState.updateProgress(progress.message, progress.progress)
                            updateNotification(progress.message, progress.progress)
                        }
                        is DataProgress.Success -> {
                            // ETF 업데이트 완료, 시장 지수 업데이트 시작
                            logger.d("ETF update completed: ${progress.message}")
                            updateMarketIndex()
                        }
                        is DataProgress.Error -> {
                            CollectionState.error(progress.message)
                            updateNotification(progress.message, 0, isError = true)
                            stopSelf()
                        }
                    }
                }
        }
    }

    private fun updateMarketIndex() {
        serviceScope.launch {
            try {
                logger.d("Starting Market Index update")
                updateNotification("시장 지수 데이터 업데이트 중...", 30)

                val result = marketIndexRepository.updateMarketIndex(30)

                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    logger.d("Market Index update completed: $count records")
                    // Market Index 완료 후 Fear & Greed 업데이트 시작
                    updateFearGreed(fearGreedRepository)
                } else {
                    val errorMsg = "시장 지수 업데이트 실패: ${result.exceptionOrNull()?.message}"
                    logger.e(errorMsg)
                    CollectionState.error(errorMsg)
                    updateNotification(errorMsg, 0, isError = true)
                    stopSelf()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Error in Market Index update", e)
                val errorMsg = "시장 지수 업데이트 실패: ${e.message}"
                CollectionState.error(errorMsg)
                updateNotification(errorMsg, 0, isError = true)
                stopSelf()
            }
        }
    }

    private fun updateFearGreed(fearGreedRepository: FearGreedRepository) {
        serviceScope.launch {
            try {
                logger.d("Starting Fear & Greed update")
                updateNotification("Fear & Greed 데이터 업데이트 중...", 50)

                val result = fearGreedRepository.updateFearGreed()

                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    logger.d("Fear & Greed update completed: $count records")
                    // Fear & Greed 완료 후 MarketOscillator 업데이트 시작
                    updateMarketOscillator()
                } else {
                    val errorMsg = "Fear & Greed 업데이트 실패: ${result.exceptionOrNull()?.message}"
                    logger.e(errorMsg)
                    CollectionState.error(errorMsg)
                    updateNotification(errorMsg, 0, isError = true)
                    stopSelf()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Error in Fear & Greed update", e)
                val errorMsg = "Fear & Greed 업데이트 실패: ${e.message}"
                CollectionState.error(errorMsg)
                updateNotification(errorMsg, 0, isError = true)
                stopSelf()
            }
        }
    }

    private fun updateMarketOscillator() {
        serviceScope.launch {
            try {
                logger.d("Starting Market Oscillator update")
                updateNotification("과매수/과매도 데이터 업데이트 중...", 70)

                // KOSPI와 KOSDAQ 데이터 업데이트
                val kospiResult = marketOscillatorRepository.updateMarketData("KOSPI")
                val kosdaqResult = marketOscillatorRepository.updateMarketData("KOSDAQ")

                if (kospiResult.isSuccess && kosdaqResult.isSuccess) {
                    val kospiCount = kospiResult.getOrNull() ?: 0
                    val kosdaqCount = kosdaqResult.getOrNull() ?: 0
                    logger.d("Market Oscillator update completed: ${kospiCount + kosdaqCount} records")
                    // Continue to Blood Indicator update
                    updateBloodIndicator()
                } else {
                    val errorMsg = "과매수/과매도 업데이트 실패"
                    logger.e(errorMsg)
                    CollectionState.error(errorMsg)
                    updateNotification(errorMsg, 0, isError = true)
                    releaseWakeLock()
                    stopSelf()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Error in Market Oscillator update", e)
                val errorMsg = "과매수/과매도 업데이트 실패: ${e.message}"
                CollectionState.error(errorMsg)
                updateNotification(errorMsg, 0, isError = true)
                releaseWakeLock()
                stopSelf()
            }
        }
    }

    private fun updateBloodIndicator() {
        serviceScope.launch {
            try {
                logger.d("Starting Blood Indicator update")
                updateNotification("Blood Indicator 업데이트 중...", 85)

                val result = bloodIndicatorRepository.updateBloodIndicator()

                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    val successMsg = "업데이트 완료! Blood Indicator $count 개 데이터"
                    logger.d(successMsg)
                    CollectionState.complete(successMsg)
                    updateNotification(successMsg, 100, isComplete = true)
                } else {
                    val errorMsg = "Blood Indicator 업데이트 실패: ${result.exceptionOrNull()?.message}"
                    logger.e(errorMsg)
                    CollectionState.error(errorMsg)
                    updateNotification(errorMsg, 0, isError = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Error in Blood Indicator update", e)
                val errorMsg = "Blood Indicator 업데이트 실패: ${e.message}"
                CollectionState.error(errorMsg)
                updateNotification(errorMsg, 0, isError = true)
            } finally {
                releaseWakeLock()
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ETF 데이터 수집 진행 상황"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(
        message: String,
        progress: Int,
        isComplete: Boolean = false,
        isError: Boolean = false
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Market Monitor")
        .setContentText(message)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(!isComplete && !isError)
        .setAutoCancel(isComplete || isError)
        .apply {
            if (!isComplete && !isError) {
                setProgress(100, progress, false)
            }

            // ✅ 앱으로 돌아가기 - singleTop으로 기존 Activity 재사용
            val intent = Intent(this@DataCollectionService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this@DataCollectionService,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            setContentIntent(pendingIntent)
        }
        .build()

    private fun updateNotification(
        message: String,
        progress: Int,
        isComplete: Boolean = false,
        isError: Boolean = false
    ) {
        val notification = createNotification(message, progress, isComplete, isError)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        logger.d("Service destroyed")
        releaseWakeLock()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}