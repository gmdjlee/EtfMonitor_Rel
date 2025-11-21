package com.etfmonitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.etfmonitor.database.EtfDao
import com.etfmonitor.repository.MarketDepositRepository
import com.etfmonitor.repository.StockRepository
import com.etfmonitor.ui.Navigation
import com.etfmonitor.ui.theme.EtfMonitorTheme
import com.etfmonitor.ui.theme.ThemeManager
import com.etfmonitor.worker.WorkManagerHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Production Level MainActivity
 *
 * 최적화 포인트:
 * 1. @AndroidEntryPoint: Hilt가 Activity에 의존성 자동 주입
 * 2. @Inject: 필요한 Repository와 DAO를 생성자 주입
 * 3. EtfMonitorApp 캐스팅 제거: 타입 안정성 향상
 *
 * 기존 문제점 해결:
 * - 직접 Application 캐스팅 제거
 * - Hilt가 자동으로 의존성 주입하여 코드 간결화
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var stockRepository: StockRepository

    @Inject
    lateinit var marketDepositRepository: MarketDepositRepository

    @Inject
    lateinit var etfDao: EtfDao

    @Inject
    lateinit var themeManager: ThemeManager

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

        // Market Deposit DB 초기화 및 WorkManager 설정
        initializeMarketDepositDatabase()

        // 테마 설정 로드
        loadThemeSetting()

        setContent {
            val darkThemeSetting by themeManager.isDarkTheme.collectAsState()
            val systemDarkTheme = isSystemInDarkTheme()

            // 테마 결정: null이면 시스템 설정, 아니면 사용자 설정
            val useDarkTheme = darkThemeSetting ?: systemDarkTheme

            EtfMonitorTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Navigation()
                }
            }
        }
    }

    private fun loadThemeSetting() {
        lifecycleScope.launch {
            try {
                val darkThemeStr = etfDao.getSetting("dark_theme")
                val isDark = when (darkThemeStr) {
                    "true" -> true
                    "false" -> false
                    else -> null // 시스템 설정 따름
                }
                themeManager.setDarkTheme(isDark)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading theme setting", e)
            }
        }
    }

    private fun initializeStockDatabase() {
        lifecycleScope.launch {
            try {
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
                val hourStr = etfDao.getSetting("stock_update_hour")
                val minuteStr = etfDao.getSetting("stock_update_minute")

                val hour = hourStr?.toIntOrNull() ?: 1 // 기본값: 새벽 1시
                val minute = minuteStr?.toIntOrNull() ?: 0

                // 기본값 저장 (설정이 없는 경우)
                if (hourStr == null) {
                    etfDao.saveSetting(com.etfmonitor.database.entities.Setting("stock_update_hour", hour.toString()))
                }
                if (minuteStr == null) {
                    etfDao.saveSetting(com.etfmonitor.database.entities.Setting("stock_update_minute", minute.toString()))
                }

                // WorkManager 스케줄 설정
                WorkManagerHelper.scheduleStockUpdate(this@MainActivity, hour, minute)
                Log.d("MainActivity", "WorkManager scheduled for $hour:${String.format("%02d", minute)}")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error initializing stock database", e)
            }
        }
    }

    private fun initializeMarketDepositDatabase() {
        lifecycleScope.launch {
            try {
                // Market Deposit DB가 비어있으면 초기화
                val depositCount = marketDepositRepository.getDepositCount()
                if (depositCount == 0) {
                    Log.d("MainActivity", "Initializing market deposit database...")
                    val result = marketDepositRepository.initializeDeposits(numPages = 10)
                    if (result.isSuccess) {
                        val count = result.getOrNull() ?: 0
                        Log.d("MainActivity", "Market deposit database initialized with $count records")
                    } else {
                        Log.e("MainActivity", "Failed to initialize market deposit database: ${result.exceptionOrNull()?.message}")
                    }
                } else {
                    Log.d("MainActivity", "Market deposit database already has $depositCount records")
                }

                // WorkManager 스케줄 설정 (설정된 시간 로드)
                val hourStr = etfDao.getSetting("market_deposit_update_hour")
                val minuteStr = etfDao.getSetting("market_deposit_update_minute")

                val hour = hourStr?.toIntOrNull() ?: 2 // 기본값: 새벽 2시
                val minute = minuteStr?.toIntOrNull() ?: 0

                // 기본값 저장 (설정이 없는 경우)
                if (hourStr == null) {
                    etfDao.saveSetting(com.etfmonitor.database.entities.Setting("market_deposit_update_hour", hour.toString()))
                }
                if (minuteStr == null) {
                    etfDao.saveSetting(com.etfmonitor.database.entities.Setting("market_deposit_update_minute", minute.toString()))
                }

                // WorkManager 스케줄 설정
                WorkManagerHelper.scheduleMarketDepositUpdate(this@MainActivity, hour, minute)
                Log.d("MainActivity", "Market deposit WorkManager scheduled for $hour:${String.format("%02d", minute)}")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error initializing market deposit database", e)
            }
        }
    }
}