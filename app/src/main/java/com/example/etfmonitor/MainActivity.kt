package com.etfmonitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.etfmonitor.repository.StockRepository
import com.etfmonitor.ui.Navigation
import com.etfmonitor.ui.theme.EtfMonitorTheme
import com.etfmonitor.worker.WorkManagerHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // ✅ 알림 권한 요청 (Android 13+)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 권한 허용됨
        } else {
            // 권한 거부됨 - 사용자에게 알림
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Android 13 이상에서 알림 권한 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Stock DB 초기화 및 WorkManager 설정
        initializeStockDatabase()

        setContent {
            EtfMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Navigation()
                }
            }
        }
    }

    private fun initializeStockDatabase() {
        lifecycleScope.launch {
            try {
                val app = application as EtfMonitorApp
                val stockDao = app.database.stockDao()
                val stockRepository = StockRepository(stockDao, app.python)

                // Stock DB가 비어있으면 초기화
                val stockCount = stockRepository.getStockCount()
                if (stockCount == 0) {
                    Log.d("MainActivity", "Initializing stock database...")
                    val result = stockRepository.initializeStocks()
                    if (result.isSuccess) {
                        val count = result.getOrNull() ?: 0
                        Log.d("MainActivity", "Stock database initialized with $count stocks")
                    } else {
                        Log.e("MainActivity", "Failed to initialize stock database: ${result.exceptionOrNull()?.message}")
                    }
                } else {
                    Log.d("MainActivity", "Stock database already has $stockCount stocks")
                }

                // WorkManager 스케줄 설정 (설정된 시간 로드)
                val dao = app.database.dao()
                val hourStr = dao.getSetting("stock_update_hour")
                val minuteStr = dao.getSetting("stock_update_minute")

                val hour = hourStr?.toIntOrNull() ?: 1 // 기본값: 새벽 1시
                val minute = minuteStr?.toIntOrNull() ?: 0

                // 기본값 저장 (설정이 없는 경우)
                if (hourStr == null) {
                    dao.saveSetting(com.etfmonitor.database.entities.Setting("stock_update_hour", hour.toString()))
                }
                if (minuteStr == null) {
                    dao.saveSetting(com.etfmonitor.database.entities.Setting("stock_update_minute", minute.toString()))
                }

                // WorkManager 스케줄 설정
                WorkManagerHelper.scheduleStockUpdate(this@MainActivity, hour, minute)
                Log.d("MainActivity", "WorkManager scheduled for $hour:${String.format("%02d", minute)}")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error initializing stock database", e)
            }
        }
    }
}