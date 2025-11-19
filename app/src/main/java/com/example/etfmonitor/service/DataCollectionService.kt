package com.etfmonitor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.etfmonitor.EtfMonitorApp
import com.etfmonitor.MainActivity
import com.etfmonitor.R
import com.etfmonitor.repository.DataProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class DataCollectionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    companion object {
        private const val TAG = "DataCollectionService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "data_collection_channel"
        private const val CHANNEL_NAME = "데이터 수집"

        const val ACTION_INITIALIZE = "action_initialize"
        const val ACTION_UPDATE = "action_update"
        const val ACTION_STOP = "action_stop"
        const val EXTRA_DAYS = "extra_days"

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
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_INITIALIZE -> {
                val days = intent.getIntExtra(EXTRA_DAYS, 25)
                CollectionState.startCollection(isInitialize = true)  // ✅ 전역 상태 업데이트
                startForeground(NOTIFICATION_ID, createNotification("초기화 준비 중...", 0))
                startInitialization(days)
            }
            ACTION_UPDATE -> {
                CollectionState.startCollection(isInitialize = false)  // ✅ 전역 상태 업데이트
                startForeground(NOTIFICATION_ID, createNotification("업데이트 준비 중...", 0))
                startUpdate()
            }
            ACTION_STOP -> {
                CollectionState.reset()  // ✅ 전역 상태 리셋
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startInitialization(days: Int) {
        serviceScope.launch {
            Log.d(TAG, "Starting initialization with $days days")
            val repository = EtfMonitorApp.instance.repository
            val fearGreedRepository = EtfMonitorApp.instance.fearGreedRepository

            // Step 1: ETF 데이터 초기화
            repository.initializeData(days)
                .catch { e ->
                    Log.e(TAG, "Error in ETF initialization", e)
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
                            // ETF 초기화 완료, Fear & Greed 초기화 시작
                            Log.d(TAG, "ETF initialization completed: ${progress.message}")
                            initializeFearGreed(days, fearGreedRepository)
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

    private fun initializeFearGreed(days: Int, fearGreedRepository: com.etfmonitor.repository.FearGreedRepository) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Starting Fear & Greed initialization with $days days")
                updateNotification("Fear & Greed 데이터 수집 중...", 0)

                val result = fearGreedRepository.initializeFearGreed(days)

                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    val successMsg = "Fear & Greed 초기화 완료! ${count}개 데이터 수집"
                    Log.d(TAG, successMsg)
                    CollectionState.complete(successMsg)
                    updateNotification(successMsg, 100, isComplete = true)
                } else {
                    val errorMsg = "Fear & Greed 초기화 실패: ${result.exceptionOrNull()?.message}"
                    Log.e(TAG, errorMsg)
                    CollectionState.error(errorMsg)
                    updateNotification(errorMsg, 0, isError = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Fear & Greed initialization", e)
                val errorMsg = "Fear & Greed 초기화 실패: ${e.message}"
                CollectionState.error(errorMsg)
                updateNotification(errorMsg, 0, isError = true)
            } finally {
                stopSelf()
            }
        }
    }

    private fun initializeMarketOscillator(days: Int) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Starting Market Oscillator initialization with $days days")
                updateNotification("과매수/과매도 데이터 수집 중...", 0)

                val marketOscillatorRepository = EtfMonitorApp.instance.marketOscillatorRepository

                // KOSPI와 KOSDAQ 데이터 수집
                val kospiResult = marketOscillatorRepository.initializeMarketData("KOSPI", days)
                val kosdaqResult = marketOscillatorRepository.initializeMarketData("KOSDAQ", days)

                if (kospiResult.isSuccess && kosdaqResult.isSuccess) {
                    val kospiCount = kospiResult.getOrNull() ?: 0
                    val kosdaqCount = kosdaqResult.getOrNull() ?: 0
                    val successMsg = "초기화 완료! 과매수/과매도 ${kospiCount + kosdaqCount}개 데이터 수집"
                    Log.d(TAG, successMsg)
                    CollectionState.complete(successMsg)
                    updateNotification(successMsg, 100, isComplete = true)
                } else {
                    val errorMsg = "과매수/과매도 초기화 실패"
                    Log.e(TAG, errorMsg)
                    CollectionState.error(errorMsg)
                    updateNotification(errorMsg, 0, isError = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Market Oscillator initialization", e)
                val errorMsg = "과매수/과매도 초기화 실패: ${e.message}"
                CollectionState.error(errorMsg)
                updateNotification(errorMsg, 0, isError = true)
            } finally {
                stopSelf()
            }
        }
    }

    private fun startUpdate() {
        serviceScope.launch {
            Log.d(TAG, "Starting update")
            val repository = EtfMonitorApp.instance.repository
            val fearGreedRepository = EtfMonitorApp.instance.fearGreedRepository

            // Step 1: ETF 데이터 업데이트
            repository.updateData()
                .catch { e ->
                    Log.e(TAG, "Error in ETF update", e)
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
                            // ETF 업데이트 완료, Fear & Greed 업데이트 시작
                            Log.d(TAG, "ETF update completed: ${progress.message}")
                            updateFearGreed(fearGreedRepository)
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

    private fun updateFearGreed(fearGreedRepository: com.etfmonitor.repository.FearGreedRepository) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Starting Fear & Greed update")
                updateNotification("Fear & Greed 데이터 업데이트 중...", 50)

                val result = fearGreedRepository.updateFearGreed()

                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    Log.d(TAG, "Fear & Greed update completed: $count records")
                    // Fear & Greed 완료 후 MarketOscillator 업데이트 시작
                    updateMarketOscillator()
                } else {
                    val errorMsg = "Fear & Greed 업데이트 실패: ${result.exceptionOrNull()?.message}"
                    Log.e(TAG, errorMsg)
                    CollectionState.error(errorMsg)
                    updateNotification(errorMsg, 0, isError = true)
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Fear & Greed update", e)
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
                Log.d(TAG, "Starting Market Oscillator update")
                updateNotification("과매수/과매도 데이터 업데이트 중...", 0)

                val marketOscillatorRepository = EtfMonitorApp.instance.marketOscillatorRepository

                // KOSPI와 KOSDAQ 데이터 업데이트
                val kospiResult = marketOscillatorRepository.updateMarketData("KOSPI")
                val kosdaqResult = marketOscillatorRepository.updateMarketData("KOSDAQ")

                if (kospiResult.isSuccess && kosdaqResult.isSuccess) {
                    val kospiCount = kospiResult.getOrNull() ?: 0
                    val kosdaqCount = kosdaqResult.getOrNull() ?: 0
                    val successMsg = "업데이트 완료! 과매수/과매도 ${kospiCount + kosdaqCount}개 데이터"
                    Log.d(TAG, successMsg)
                    CollectionState.complete(successMsg)
                    updateNotification(successMsg, 100, isComplete = true)
                } else {
                    val errorMsg = "과매수/과매도 업데이트 실패"
                    Log.e(TAG, errorMsg)
                    CollectionState.error(errorMsg)
                    updateNotification(errorMsg, 0, isError = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Market Oscillator update", e)
                val errorMsg = "과매수/과매도 업데이트 실패: ${e.message}"
                CollectionState.error(errorMsg)
                updateNotification(errorMsg, 0, isError = true)
            } finally {
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
        Log.d(TAG, "Service destroyed")
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}