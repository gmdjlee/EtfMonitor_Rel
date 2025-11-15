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

            repository.initializeData(days)
                .catch { e ->
                    Log.e(TAG, "Error in initialization", e)
                    val errorMsg = "초기화 실패: ${e.message}"
                    CollectionState.error(errorMsg)  // ✅ 전역 상태 업데이트
                    updateNotification(errorMsg, 0, isError = true)
                    stopSelf()
                }
                .collect { progress ->
                    when (progress) {
                        is DataProgress.Loading -> {
                            CollectionState.updateProgress(progress.message, progress.progress)  // ✅ 전역 상태 업데이트
                            updateNotification(progress.message, progress.progress)
                        }
                        is DataProgress.Success -> {
                            // ETF 초기화 완료
                            Log.d(TAG, "ETF initialization completed: ${progress.message}")
                            CollectionState.complete(progress.message)
                            updateNotification(progress.message, 100, isComplete = true)
                            stopSelf()
                        }
                        is DataProgress.Error -> {
                            CollectionState.error(progress.message)  // ✅ 전역 상태 업데이트
                            updateNotification(progress.message, 0, isError = true)
                            stopSelf()
                        }
                    }
                }
        }
    }

    private fun startUpdate() {
        serviceScope.launch {
            Log.d(TAG, "Starting update")
            val repository = EtfMonitorApp.instance.repository

            repository.updateData()
                .catch { e ->
                    Log.e(TAG, "Error in update", e)
                    val errorMsg = "업데이트 실패: ${e.message}"
                    CollectionState.error(errorMsg)  // ✅ 전역 상태 업데이트
                    updateNotification(errorMsg, 0, isError = true)
                    stopSelf()
                }
                .collect { progress ->
                    when (progress) {
                        is DataProgress.Loading -> {
                            CollectionState.updateProgress(progress.message, progress.progress)  // ✅ 전역 상태 업데이트
                            updateNotification(progress.message, progress.progress)
                        }
                        is DataProgress.Success -> {
                            CollectionState.complete(progress.message)  // ✅ 전역 상태 업데이트
                            updateNotification(progress.message, 100, isComplete = true)
                            stopSelf()
                        }
                        is DataProgress.Error -> {
                            CollectionState.error(progress.message)  // ✅ 전역 상태 업데이트
                            updateNotification(progress.message, 0, isError = true)
                            stopSelf()
                        }
                    }
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