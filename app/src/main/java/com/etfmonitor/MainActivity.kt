package com.etfmonitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.etfmonitor.core.common.util.AppLogger
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.common.util.ApiConfigurationException
import com.etfmonitor.core.common.util.NetworkException
import com.etfmonitor.core.network.ai.ApiKeyProvider
import com.etfmonitor.core.network.python.PyKrxClient
import com.etfmonitor.feature.stock.domain.repository.StockRepository
import com.etfmonitor.navigation.Navigation
import com.etfmonitor.core.ui.theme.ChartColorSettings
import com.etfmonitor.core.ui.theme.EtfMonitorTheme
import com.etfmonitor.core.ui.theme.FontScaleSettings
import com.etfmonitor.core.ui.theme.SingleChartColorSettings
import com.etfmonitor.core.ui.theme.ThemeManager
import com.etfmonitor.core.ui.theme.createScaledTypography
import com.etfmonitor.core.worker.WorkManagerHelper
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

    companion object {
        private val logger = AppLogger.getLogger("MainActivity")
    }

    @Inject
    lateinit var stockRepository: StockRepository

    @Inject
    lateinit var etfDao: EtfDao

    @Inject
    lateinit var themeManager: ThemeManager

    @Inject
    lateinit var apiKeyProvider: ApiKeyProvider

    @Inject
    lateinit var pyKrxClient: PyKrxClient

    // 네트워크 에러 다이얼로그 상태
    private val showNetworkErrorDialog = mutableStateOf(false)
    private val networkErrorMessage = mutableStateOf("")

    // API 설정 필요 다이얼로그 상태
    private val showApiConfigDialog = mutableStateOf(false)
    private val apiConfigMessage = mutableStateOf("")

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

        // 중요: 첫 프레임 렌더링 후 초기화 작업 실행
        // SurfaceSyncGroup 타임아웃 에러 방지를 위해 setContent 이후로 지연
        window.decorView.post {
            // Stock DB 초기화 및 WorkManager 설정
            initializeStockDatabase()
            // 테마 설정 로드
            loadThemeSetting()
            // KIS API 클라이언트 초기화 (자격 증명이 설정된 경우)
            initializeKisApiIfConfigured()
        }

        setContent {
            val darkThemeSetting by themeManager.isDarkTheme.collectAsState()
            val fontScaleSettings by themeManager.fontScaleSettings.collectAsState()
            val systemDarkTheme = isSystemInDarkTheme()

            // 테마 결정: null이면 시스템 설정, 아니면 사용자 설정
            val useDarkTheme = darkThemeSetting ?: systemDarkTheme

            // 동적 Typography 생성
            val scaledTypography = createScaledTypography(
                displayScale = fontScaleSettings.displayScale,
                headlineScale = fontScaleSettings.headlineScale,
                titleScale = fontScaleSettings.titleScale,
                bodyScale = fontScaleSettings.bodyScale,
                labelScale = fontScaleSettings.labelScale
            )

            EtfMonitorTheme(
                darkTheme = useDarkTheme,
                typography = scaledTypography
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Navigation(
                        isDarkTheme = useDarkTheme,
                        onToggleTheme = { themeManager.setDarkTheme(!useDarkTheme) }
                    )

                    // 네트워크 에러 다이얼로그
                    if (showNetworkErrorDialog.value) {
                        AlertDialog(
                            onDismissRequest = { showNetworkErrorDialog.value = false },
                            title = { Text("네트워크 오류") },
                            text = { Text(networkErrorMessage.value) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showNetworkErrorDialog.value = false
                                        retryStockInitialization()
                                    }
                                ) {
                                    Text("재시도")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showNetworkErrorDialog.value = false }
                                ) {
                                    Text("나중에")
                                }
                            }
                        )
                    }

                    // API 설정 필요 다이얼로그
                    if (showApiConfigDialog.value) {
                        AlertDialog(
                            onDismissRequest = { showApiConfigDialog.value = false },
                            title = { Text("API 설정 필요") },
                            text = { Text(apiConfigMessage.value) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showApiConfigDialog.value = false
                                    }
                                ) {
                                    Text("확인")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    /**
     * 종목 데이터 초기화 재시도
     */
    private fun retryStockInitialization() {
        lifecycleScope.launch {
            try {
                logger.d("Retrying stock initialization...")
                val result = stockRepository.initializeStocks()
                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    logger.d("Stock database initialized with $count stocks")
                } else {
                    val exception = result.exceptionOrNull()
                    logger.e("Failed to initialize stock database: ${exception?.message}")
                    handleStockInitializationError(exception)
                }
            } catch (e: Exception) {
                logger.e("Error retrying stock initialization", e)
            }
        }
    }

    /**
     * 종목 데이터 초기화 에러 처리
     */
    private fun handleStockInitializationError(exception: Throwable?) {
        when (exception) {
            is ApiConfigurationException -> {
                apiConfigMessage.value = exception.message ?: "KIS API 설정이 필요합니다. 설정 화면에서 API 키를 입력해주세요."
                showApiConfigDialog.value = true
            }
            is NetworkException -> {
                networkErrorMessage.value = exception.message ?: "네트워크 연결을 확인해 주세요."
                showNetworkErrorDialog.value = true
            }
            else -> {
                // 기타 오류는 네트워크 오류로 표시
                networkErrorMessage.value = exception?.message ?: "데이터를 가져오는 중 오류가 발생했습니다."
                showNetworkErrorDialog.value = true
            }
        }
    }

    private fun loadThemeSetting() {
        lifecycleScope.launch {
            try {
                // 다크 테마 설정 로드
                val darkThemeStr = etfDao.getSetting("dark_theme")
                val isDark = when (darkThemeStr) {
                    "true" -> true
                    "false" -> false
                    else -> null // 시스템 설정 따름
                }
                themeManager.setDarkTheme(isDark)

                // 폰트 스케일 설정 로드
                val displayScale = etfDao.getSetting("font_scale_display")?.toFloatOrNull() ?: 1.0f
                val headlineScale = etfDao.getSetting("font_scale_headline")?.toFloatOrNull() ?: 1.0f
                val titleScale = etfDao.getSetting("font_scale_title")?.toFloatOrNull() ?: 1.0f
                val bodyScale = etfDao.getSetting("font_scale_body")?.toFloatOrNull() ?: 1.0f
                val labelScale = etfDao.getSetting("font_scale_label")?.toFloatOrNull() ?: 1.0f

                themeManager.setFontScaleSettings(
                    FontScaleSettings(
                        displayScale = displayScale,
                        headlineScale = headlineScale,
                        titleScale = titleScale,
                        bodyScale = bodyScale,
                        labelScale = labelScale
                    )
                )

                // 차트 색상 설정 로드
                loadChartColorSettings()
            } catch (e: Exception) {
                logger.e("Error loading theme setting", e)
            }
        }
    }

    private fun initializeStockDatabase() {
        lifecycleScope.launch {
            try {
                // Stock DB가 비어있으면 초기화
                val stockCount = stockRepository.getStockCount()
                if (stockCount == 0) {
                    logger.d("Initializing stock database...")
                    val result = stockRepository.initializeStocks()
                    if (result.isSuccess) {
                        val count = result.getOrNull() ?: 0
                        logger.d("Stock database initialized with $count stocks")
                    } else {
                        val exception = result.exceptionOrNull()
                        logger.e("Failed to initialize stock database: ${exception?.message}")
                        handleStockInitializationError(exception)
                    }
                } else {
                    logger.d("Stock database already has $stockCount stocks")
                }

                // WorkManager 스케줄 설정 (설정된 시간 로드)
                val hourStr = etfDao.getSetting("stock_update_hour")
                val minuteStr = etfDao.getSetting("stock_update_minute")

                val hour = hourStr?.toIntOrNull() ?: 1 // 기본값: 새벽 1시
                val minute = minuteStr?.toIntOrNull() ?: 0

                // 기본값 저장 (설정이 없는 경우)
                if (hourStr == null) {
                    etfDao.saveSetting(com.etfmonitor.core.database.entities.Setting("stock_update_hour", hour.toString()))
                }
                if (minuteStr == null) {
                    etfDao.saveSetting(com.etfmonitor.core.database.entities.Setting("stock_update_minute", minute.toString()))
                }

                // WorkManager 스케줄 설정
                WorkManagerHelper.scheduleStockUpdate(this@MainActivity, hour, minute)
                logger.d("WorkManager scheduled for $hour:${String.format("%02d", minute)}")

                // 데이터 아카이빙 스케줄 설정 (월 1회)
                WorkManagerHelper.scheduleDataArchiving(this@MainActivity)
                logger.d("Data archiving scheduled (monthly)")
            } catch (e: Exception) {
                logger.e("Error initializing stock database", e)
            }
        }
    }

    /**
     * KIS Open API 클라이언트 자동 초기화
     *
     * 저장된 자격 증명이 있으면 앱 시작 시 자동으로 Python KIS 클라이언트를 초기화합니다.
     * 이를 통해 다른 Python 모듈들이 KIS API를 사용할 수 있게 됩니다.
     */
    private fun initializeKisApiIfConfigured() {
        if (apiKeyProvider.isKisApiConfigured()) {
            lifecycleScope.launch {
                try {
                    val appKey = apiKeyProvider.getKisAppKey()
                    val appSecret = apiKeyProvider.getKisAppSecret()

                    if (!appKey.isNullOrBlank() && !appSecret.isNullOrBlank()) {
                        val success = pyKrxClient.initializeKisClient(appKey, appSecret)
                        if (success) {
                            logger.i("KIS API client initialized on app start")
                        } else {
                            logger.w("Failed to initialize KIS API client on app start")
                        }
                    }
                } catch (e: Exception) {
                    logger.e("Error initializing KIS API client", e)
                }
            }
        } else {
            logger.d("KIS API not configured, skipping initialization")
        }
    }

    private suspend fun loadChartColorSettings() {
        try {
            val default = ChartColorSettings()

            // 헬퍼 함수
            suspend fun loadColor(chart: String, prop: String, defaultVal: Int): Int =
                etfDao.getSetting("chart_${chart}_$prop")?.toIntOrNull() ?: defaultVal

            suspend fun loadOptionalColor(chart: String, prop: String): Int? =
                etfDao.getSetting("chart_${chart}_$prop")?.toIntOrNull()

            val settings = ChartColorSettings(
                marketCapOscillator = SingleChartColorSettings(
                    lineColor1 = loadColor("marketcap", "line1", default.marketCapOscillator.lineColor1),
                    lineColor2 = loadColor("marketcap", "line2", default.marketCapOscillator.lineColor2),
                    textColor = loadColor("marketcap", "text", default.marketCapOscillator.textColor),
                    legendColor = loadColor("marketcap", "legend", default.marketCapOscillator.legendColor)
                ),
                macd = SingleChartColorSettings(
                    lineColor1 = loadColor("macd", "line1", default.macd.lineColor1),
                    lineColor2 = loadColor("macd", "line2", default.macd.lineColor2),
                    positiveColor = loadColor("macd", "positive", default.macd.positiveColor),
                    negativeColor = loadColor("macd", "negative", default.macd.negativeColor),
                    textColor = loadColor("macd", "text", default.macd.textColor),
                    legendColor = loadColor("macd", "legend", default.macd.legendColor)
                ),
                marketDeposit = SingleChartColorSettings(
                    lineColor1 = loadColor("deposit", "line1", default.marketDeposit.lineColor1),
                    lineColor2 = loadColor("deposit", "line2", default.marketDeposit.lineColor2),
                    textColor = loadColor("deposit", "text", default.marketDeposit.textColor),
                    legendColor = loadColor("deposit", "legend", default.marketDeposit.legendColor)
                ),
                fearGreed = SingleChartColorSettings(
                    lineColor1 = loadColor("feargreed", "line1", default.fearGreed.lineColor1),
                    lineColor2 = loadColor("feargreed", "line2", default.fearGreed.lineColor2),
                    textColor = loadColor("feargreed", "text", default.fearGreed.textColor),
                    legendColor = loadColor("feargreed", "legend", default.fearGreed.legendColor)
                )
            )

            themeManager.setChartColorSettings(settings)
            logger.d("Chart color settings loaded")
        } catch (e: Exception) {
            logger.e("Error loading chart color settings", e)
        }
    }
}