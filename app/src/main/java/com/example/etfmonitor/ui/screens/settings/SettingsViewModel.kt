package com.etfmonitor.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.ai.AIProvider
import com.etfmonitor.ai.ApiKeyProvider
import com.etfmonitor.database.EtfDao
import com.etfmonitor.database.entities.Setting
import com.etfmonitor.repository.AIAnalysisRepository
import com.etfmonitor.repository.DataRepository
import com.etfmonitor.repository.FearGreedRepository
import com.etfmonitor.repository.MarketDepositRepository
import com.etfmonitor.repository.StockRepository
import com.etfmonitor.ui.theme.ChartColorSettings
import com.etfmonitor.ui.theme.FontScaleSettings
import com.etfmonitor.ui.theme.SingleChartColorSettings
import com.etfmonitor.ui.theme.ThemeManager
import com.etfmonitor.worker.WorkManagerHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StockUpdateSettings(
    val updateHour: Int = 1,
    val updateMinute: Int = 0,
    val lastUpdateTime: Long? = null,
    val stockCount: Int = 0,
    val isUpdating: Boolean = false
)

data class MarketDepositUpdateSettings(
    val updateHour: Int = 2, // 기본값: 새벽 2시
    val updateMinute: Int = 0,
    val lastUpdateTime: Long? = null,
    val depositCount: Int = 0,
    val isUpdating: Boolean = false
)

data class FearGreedUpdateSettings(
    val updateHour: Int = 3, // 기본값: 새벽 3시
    val updateMinute: Int = 0,
    val lastUpdateTime: Long? = null,
    val kospiCount: Int = 0,
    val kosdaqCount: Int = 0,
    val isUpdating: Boolean = false
)

data class MarketOscillatorUpdateSettings(
    val updateHour: Int = 4, // 기본값: 새벽 4시
    val updateMinute: Int = 0,
    val lastUpdateTime: Long? = null,
    val kospiCount: Int = 0,
    val kosdaqCount: Int = 0,
    val isUpdating: Boolean = false
)

/**
 * API 키 테스트 상태
 */
sealed class ApiKeyTestState {
    object Idle : ApiKeyTestState()
    object Testing : ApiKeyTestState()
    object Success : ApiKeyTestState()
    data class Error(val message: String) : ApiKeyTestState()
}

/**
 * Production Level SettingsViewModel with Hilt
 *
 * 최적화 포인트:
 * 1. @HiltViewModel: Hilt가 ViewModel 생명주기 자동 관리
 * 2. @Inject: 생성자 주입으로 의존성 명확화
 * 3. @ApplicationContext: Application Context 직접 주입
 * 4. Factory 패턴 제거: Hilt가 자동으로 ViewModel 생성
 * 5. AndroidViewModel → ViewModel: Application 직접 주입 제거
 *
 * 기존 문제점 해결:
 * - EtfMonitorApp.instance 제거: 메모리 누수 위험 제거
 * - 수동 Factory 제거: Hilt가 자동으로 관리하여 코드 간결화
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: DataRepository,
    private val stockRepository: StockRepository,
    private val marketDepositRepository: MarketDepositRepository,
    private val fearGreedRepository: FearGreedRepository,
    private val marketOscillatorRepository: com.etfmonitor.repository.MarketOscillatorRepository,
    private val aiAnalysisRepository: AIAnalysisRepository,
    private val apiKeyProvider: ApiKeyProvider,
    private val etfDao: EtfDao,
    private val themeManager: ThemeManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _themes = MutableStateFlow<List<String>>(emptyList())
    val themes: StateFlow<List<String>> = _themes.asStateFlow()

    private val _exclusions = MutableStateFlow<List<String>>(emptyList())
    val exclusions: StateFlow<List<String>> = _exclusions.asStateFlow()

    private val _defaultDays = MutableStateFlow(25)
    val defaultDays: StateFlow<Int> = _defaultDays.asStateFlow()

    private val _stockUpdateSettings = MutableStateFlow(StockUpdateSettings())
    val stockUpdateSettings: StateFlow<StockUpdateSettings> = _stockUpdateSettings.asStateFlow()

    private val _marketDepositUpdateSettings = MutableStateFlow(MarketDepositUpdateSettings())
    val marketDepositUpdateSettings: StateFlow<MarketDepositUpdateSettings> = _marketDepositUpdateSettings.asStateFlow()

    private val _fearGreedUpdateSettings = MutableStateFlow(FearGreedUpdateSettings())
    val fearGreedUpdateSettings: StateFlow<FearGreedUpdateSettings> = _fearGreedUpdateSettings.asStateFlow()

    private val _marketOscillatorUpdateSettings = MutableStateFlow(MarketOscillatorUpdateSettings())
    val marketOscillatorUpdateSettings: StateFlow<MarketOscillatorUpdateSettings> = _marketOscillatorUpdateSettings.asStateFlow()

    private val _searchHistoryLimit = MutableStateFlow(15)
    val searchHistoryLimit: StateFlow<Int> = _searchHistoryLimit.asStateFlow()

    private val _fearGreedPeriodDays = MutableStateFlow(365) // 기본값: 12개월
    val fearGreedPeriodDays: StateFlow<Int> = _fearGreedPeriodDays.asStateFlow()

    private val _marketOscillatorPeriodDays = MutableStateFlow(365) // 기본값: 12개월
    val marketOscillatorPeriodDays: StateFlow<Int> = _marketOscillatorPeriodDays.asStateFlow()

    // General settings
    private val _isDarkTheme = MutableStateFlow<Boolean?>(null) // null = 시스템 설정 따름
    val isDarkTheme: StateFlow<Boolean?> = _isDarkTheme.asStateFlow()

    private val _fontScaleSettings = MutableStateFlow(FontScaleSettings())
    val fontScaleSettings: StateFlow<FontScaleSettings> = _fontScaleSettings.asStateFlow()

    // 차트 색상 설정
    private val _chartColorSettings = MutableStateFlow(ChartColorSettings())
    val chartColorSettings: StateFlow<ChartColorSettings> = _chartColorSettings.asStateFlow()

    // AI API 키 설정
    private val _selectedProvider = MutableStateFlow(AIProvider.CLAUDE)
    val selectedProvider: StateFlow<AIProvider> = _selectedProvider.asStateFlow()

    private val _isClaudeApiKeyConfigured = MutableStateFlow(false)
    val isClaudeApiKeyConfigured: StateFlow<Boolean> = _isClaudeApiKeyConfigured.asStateFlow()

    private val _isGeminiApiKeyConfigured = MutableStateFlow(false)
    val isGeminiApiKeyConfigured: StateFlow<Boolean> = _isGeminiApiKeyConfigured.asStateFlow()

    private val _apiKeyTestState = MutableStateFlow<ApiKeyTestState>(ApiKeyTestState.Idle)
    val apiKeyTestState: StateFlow<ApiKeyTestState> = _apiKeyTestState.asStateFlow()

    // 하위 호환성을 위한 deprecated 프로퍼티
    @Deprecated("Use isClaudeApiKeyConfigured or isGeminiApiKeyConfigured")
    val isApiKeyConfigured: StateFlow<Boolean>
        get() = when (_selectedProvider.value) {
            AIProvider.CLAUDE -> _isClaudeApiKeyConfigured
            AIProvider.GEMINI -> _isGeminiApiKeyConfigured
        }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        loadSettings()
        loadStockInfo()
        loadMarketDepositInfo()
        loadFearGreedInfo()
        loadMarketOscillatorInfo()
        checkApiKeyStatus()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _themes.value = repository.getThemes()
            _exclusions.value = repository.getExclusions()
            _defaultDays.value = repository.getDefaultDays()

            // 검색 히스토리 개수 로드
            val historyLimitStr = etfDao.getSetting("search_history_limit")
            _searchHistoryLimit.value = historyLimitStr?.toIntOrNull() ?: 15

            // Fear & Greed 데이터 수집 기간 로드
            val fearGreedPeriodStr = etfDao.getSetting("fear_greed_period_days")
            _fearGreedPeriodDays.value = fearGreedPeriodStr?.toIntOrNull() ?: 365 // 기본값: 12개월

            // 과매수/과매도 데이터 수집 기간 로드
            val marketOscillatorPeriodStr = etfDao.getSetting("market_oscillator_period_days")
            _marketOscillatorPeriodDays.value = marketOscillatorPeriodStr?.toIntOrNull() ?: 365 // 기본값: 12개월

            // Stock 업데이트 시간 로드
            val stockHourStr = etfDao.getSetting("stock_update_hour")
            val stockMinuteStr = etfDao.getSetting("stock_update_minute")

            val stockHour = stockHourStr?.toIntOrNull() ?: 1 // 기본값: 새벽 1시
            val stockMinute = stockMinuteStr?.toIntOrNull() ?: 0

            _stockUpdateSettings.value = _stockUpdateSettings.value.copy(
                updateHour = stockHour,
                updateMinute = stockMinute
            )

            // 스케줄 재설정
            WorkManagerHelper.scheduleStockUpdate(context, stockHour, stockMinute)

            // Market Deposit 업데이트 시간 로드
            val depositHourStr = etfDao.getSetting("market_deposit_update_hour")
            val depositMinuteStr = etfDao.getSetting("market_deposit_update_minute")

            val depositHour = depositHourStr?.toIntOrNull() ?: 2 // 기본값: 새벽 2시
            val depositMinute = depositMinuteStr?.toIntOrNull() ?: 0

            _marketDepositUpdateSettings.value = _marketDepositUpdateSettings.value.copy(
                updateHour = depositHour,
                updateMinute = depositMinute
            )

            // 스케줄 재설정
            WorkManagerHelper.scheduleMarketDepositUpdate(context, depositHour, depositMinute)

            // Fear & Greed 업데이트 시간 로드
            val fearGreedHourStr = etfDao.getSetting("fear_greed_update_hour")
            val fearGreedMinuteStr = etfDao.getSetting("fear_greed_update_minute")

            val fearGreedHour = fearGreedHourStr?.toIntOrNull() ?: 3 // 기본값: 새벽 3시
            val fearGreedMinute = fearGreedMinuteStr?.toIntOrNull() ?: 0

            _fearGreedUpdateSettings.value = _fearGreedUpdateSettings.value.copy(
                updateHour = fearGreedHour,
                updateMinute = fearGreedMinute
            )

            // 스케줄 재설정
            WorkManagerHelper.scheduleFearGreedUpdate(context, fearGreedHour, fearGreedMinute)

            // 과매수/과매도 업데이트 시간 로드
            val marketOscillatorHourStr = etfDao.getSetting("market_oscillator_update_hour")
            val marketOscillatorMinuteStr = etfDao.getSetting("market_oscillator_update_minute")

            val marketOscillatorHour = marketOscillatorHourStr?.toIntOrNull() ?: 4 // 기본값: 새벽 4시
            val marketOscillatorMinute = marketOscillatorMinuteStr?.toIntOrNull() ?: 0

            _marketOscillatorUpdateSettings.value = _marketOscillatorUpdateSettings.value.copy(
                updateHour = marketOscillatorHour,
                updateMinute = marketOscillatorMinute
            )

            // 스케줄 재설정
            WorkManagerHelper.scheduleMarketOscillatorUpdate(context, marketOscillatorHour, marketOscillatorMinute)

            // General settings 로드
            val darkThemeStr = etfDao.getSetting("dark_theme")
            _isDarkTheme.value = when (darkThemeStr) {
                "true" -> true
                "false" -> false
                else -> null // 시스템 설정 따름
            }

            // 폰트 스케일 설정 로드
            val displayScale = etfDao.getSetting("font_scale_display")?.toFloatOrNull() ?: 1.0f
            val headlineScale = etfDao.getSetting("font_scale_headline")?.toFloatOrNull() ?: 1.0f
            val titleScale = etfDao.getSetting("font_scale_title")?.toFloatOrNull() ?: 1.0f
            val bodyScale = etfDao.getSetting("font_scale_body")?.toFloatOrNull() ?: 1.0f
            val labelScale = etfDao.getSetting("font_scale_label")?.toFloatOrNull() ?: 1.0f

            _fontScaleSettings.value = FontScaleSettings(
                displayScale = displayScale,
                headlineScale = headlineScale,
                titleScale = titleScale,
                bodyScale = bodyScale,
                labelScale = labelScale
            )

            // 차트 색상 설정 로드
            loadChartColorSettings()
        }
    }

    private suspend fun loadChartColorSettings() {
        val marketCapLine1 = etfDao.getSetting("chart_marketcap_line1")?.toIntOrNull()
        val marketCapLine2 = etfDao.getSetting("chart_marketcap_line2")?.toIntOrNull()
        val marketCapText = etfDao.getSetting("chart_marketcap_text")?.toIntOrNull()
        val marketCapLegend = etfDao.getSetting("chart_marketcap_legend")?.toIntOrNull()

        val macdLine1 = etfDao.getSetting("chart_macd_line1")?.toIntOrNull()
        val macdLine2 = etfDao.getSetting("chart_macd_line2")?.toIntOrNull()
        val macdPositive = etfDao.getSetting("chart_macd_positive")?.toIntOrNull()
        val macdNegative = etfDao.getSetting("chart_macd_negative")?.toIntOrNull()
        val macdText = etfDao.getSetting("chart_macd_text")?.toIntOrNull()
        val macdLegend = etfDao.getSetting("chart_macd_legend")?.toIntOrNull()

        val depositLine1 = etfDao.getSetting("chart_deposit_line1")?.toIntOrNull()
        val depositLine2 = etfDao.getSetting("chart_deposit_line2")?.toIntOrNull()
        val depositText = etfDao.getSetting("chart_deposit_text")?.toIntOrNull()
        val depositLegend = etfDao.getSetting("chart_deposit_legend")?.toIntOrNull()

        val fearGreedLine1 = etfDao.getSetting("chart_feargreed_line1")?.toIntOrNull()
        val fearGreedLine2 = etfDao.getSetting("chart_feargreed_line2")?.toIntOrNull()
        val fearGreedText = etfDao.getSetting("chart_feargreed_text")?.toIntOrNull()
        val fearGreedLegend = etfDao.getSetting("chart_feargreed_legend")?.toIntOrNull()

        val defaultSettings = ChartColorSettings()

        val settings = ChartColorSettings(
            marketCapOscillator = SingleChartColorSettings(
                lineColor1 = marketCapLine1 ?: defaultSettings.marketCapOscillator.lineColor1,
                lineColor2 = marketCapLine2 ?: defaultSettings.marketCapOscillator.lineColor2,
                textColor = marketCapText,
                legendColor = marketCapLegend
            ),
            macd = SingleChartColorSettings(
                lineColor1 = macdLine1 ?: defaultSettings.macd.lineColor1,
                lineColor2 = macdLine2 ?: defaultSettings.macd.lineColor2,
                positiveColor = macdPositive ?: defaultSettings.macd.positiveColor,
                negativeColor = macdNegative ?: defaultSettings.macd.negativeColor,
                textColor = macdText,
                legendColor = macdLegend
            ),
            marketDeposit = SingleChartColorSettings(
                lineColor1 = depositLine1 ?: defaultSettings.marketDeposit.lineColor1,
                lineColor2 = depositLine2 ?: defaultSettings.marketDeposit.lineColor2,
                textColor = depositText,
                legendColor = depositLegend
            ),
            fearGreed = SingleChartColorSettings(
                lineColor1 = fearGreedLine1 ?: defaultSettings.fearGreed.lineColor1,
                lineColor2 = fearGreedLine2 ?: defaultSettings.fearGreed.lineColor2,
                textColor = fearGreedText,
                legendColor = fearGreedLegend
            )
        )

        _chartColorSettings.value = settings
        themeManager.setChartColorSettings(settings)
    }

    private fun loadStockInfo() {
        viewModelScope.launch {
            try {
                val count = stockRepository.getStockCount()
                val lastUpdate = stockRepository.getLastUpdateTime()

                _stockUpdateSettings.value = _stockUpdateSettings.value.copy(
                    stockCount = count,
                    lastUpdateTime = lastUpdate
                )
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Error loading stock info", e)
            }
        }
    }

    private fun loadMarketDepositInfo() {
        viewModelScope.launch {
            try {
                val count = marketDepositRepository.getDepositCount()
                val lastUpdate = marketDepositRepository.getLastUpdateTime()

                _marketDepositUpdateSettings.value = _marketDepositUpdateSettings.value.copy(
                    depositCount = count,
                    lastUpdateTime = lastUpdate
                )
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Error loading market deposit info", e)
            }
        }
    }

    private fun loadFearGreedInfo() {
        viewModelScope.launch {
            try {
                val kospiCount = fearGreedRepository.getCountByMarket("KOSPI")
                val kosdaqCount = fearGreedRepository.getCountByMarket("KOSDAQ")
                val kospiLastUpdate = fearGreedRepository.getLastUpdateTime("KOSPI")
                val kosdaqLastUpdate = fearGreedRepository.getLastUpdateTime("KOSDAQ")
                val lastUpdate = maxOf(kospiLastUpdate ?: 0L, kosdaqLastUpdate ?: 0L).takeIf { it > 0L }

                _fearGreedUpdateSettings.value = _fearGreedUpdateSettings.value.copy(
                    kospiCount = kospiCount,
                    kosdaqCount = kosdaqCount,
                    lastUpdateTime = lastUpdate
                )
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Error loading fear greed info", e)
            }
        }
    }

    private fun loadMarketOscillatorInfo() {
        viewModelScope.launch {
            try {
                val kospiCount = marketOscillatorRepository.getDataCount("KOSPI")
                val kosdaqCount = marketOscillatorRepository.getDataCount("KOSDAQ")
                val kospiLatest = marketOscillatorRepository.getLatestData("KOSPI")
                val kosdaqLatest = marketOscillatorRepository.getLatestData("KOSDAQ")
                val kospiLastUpdate = kospiLatest?.lastUpdated
                val kosdaqLastUpdate = kosdaqLatest?.lastUpdated
                val lastUpdate = maxOf(kospiLastUpdate ?: 0L, kosdaqLastUpdate ?: 0L).takeIf { it > 0L }

                _marketOscillatorUpdateSettings.value = _marketOscillatorUpdateSettings.value.copy(
                    kospiCount = kospiCount,
                    kosdaqCount = kosdaqCount,
                    lastUpdateTime = lastUpdate
                )
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Error loading market oscillator info", e)
            }
        }
    }

    // ✅ 기본 수집 기간 설정 메서드 추가
    fun setDefaultDays(days: Int) {
        viewModelScope.launch {
            repository.setDefaultDays(days)
            _defaultDays.value = days
            _message.value = "기본 수집 기간이 ${days}일로 설정되었습니다"
        }
    }

    fun addTheme(theme: String) {
        if (theme.isBlank()) {
            _message.value = "키워드를 입력하세요"
            return
        }
        viewModelScope.launch {
            repository.addTheme(theme)
            _themes.value = repository.getThemes()
            _message.value = "테마 추가됨: $theme"
        }
    }

    fun removeTheme(theme: String) {
        viewModelScope.launch {
            repository.removeTheme(theme)
            _themes.value = repository.getThemes()
            _message.value = "테마 제거됨: $theme"
        }
    }

    fun addExclusion(keyword: String) {
        if (keyword.isBlank()) {
            _message.value = "키워드를 입력하세요"
            return
        }
        viewModelScope.launch {
            repository.addExclusion(keyword)
            _exclusions.value = repository.getExclusions()
            _message.value = "제외 키워드 추가됨: $keyword"
        }
    }

    fun removeExclusion(keyword: String) {
        viewModelScope.launch {
            repository.removeExclusion(keyword)
            _exclusions.value = repository.getExclusions()
            _message.value = "제외 키워드 제거됨: $keyword"
        }
    }

    fun resetDatabase() {
        viewModelScope.launch {
            repository.resetDatabase()
            _message.value = "데이터베이스가 초기화되었습니다"
        }
    }

    fun setUpdateTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("stock_update_hour", hour.toString()))
                etfDao.saveSetting(Setting("stock_update_minute", minute.toString()))

                _stockUpdateSettings.value = _stockUpdateSettings.value.copy(
                    updateHour = hour,
                    updateMinute = minute
                )

                WorkManagerHelper.scheduleStockUpdate(context, hour, minute)
                _message.value = "업데이트 시간이 ${hour}:${String.format("%02d", minute)}로 설정되었습니다"
            } catch (e: Exception) {
                _message.value = "시간 설정 실패: ${e.message}"
            }
        }
    }

    fun updateStocksNow() {
        viewModelScope.launch {
            try {
                _stockUpdateSettings.value = _stockUpdateSettings.value.copy(isUpdating = true)
                _message.value = "종목 데이터 업데이트 중..."

                val result = stockRepository.updateStocks()

                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    loadStockInfo()
                    _message.value = "업데이트 완료: ${count}개 종목"
                } else {
                    _message.value = "업데이트 실패: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _message.value = "오류 발생: ${e.message}"
            } finally {
                _stockUpdateSettings.value = _stockUpdateSettings.value.copy(isUpdating = false)
            }
        }
    }

    fun setMarketDepositUpdateTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("market_deposit_update_hour", hour.toString()))
                etfDao.saveSetting(Setting("market_deposit_update_minute", minute.toString()))

                _marketDepositUpdateSettings.value = _marketDepositUpdateSettings.value.copy(
                    updateHour = hour,
                    updateMinute = minute
                )

                WorkManagerHelper.scheduleMarketDepositUpdate(context, hour, minute)
                _message.value = "증시 자금 업데이트 시간이 ${hour}:${String.format("%02d", minute)}로 설정되었습니다"
            } catch (e: Exception) {
                _message.value = "시간 설정 실패: ${e.message}"
            }
        }
    }

    fun updateMarketDepositsNow() {
        viewModelScope.launch {
            try {
                _marketDepositUpdateSettings.value = _marketDepositUpdateSettings.value.copy(isUpdating = true)
                _message.value = "증시 자금 데이터 업데이트 중..."

                val result = marketDepositRepository.updateDeposits(numPages = 10)

                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    loadMarketDepositInfo()
                    _message.value = "업데이트 완료: ${count}개 데이터"
                } else {
                    _message.value = "업데이트 실패: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _message.value = "오류 발생: ${e.message}"
            } finally {
                _marketDepositUpdateSettings.value = _marketDepositUpdateSettings.value.copy(isUpdating = false)
            }
        }
    }

    fun setFearGreedUpdateTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("fear_greed_update_hour", hour.toString()))
                etfDao.saveSetting(Setting("fear_greed_update_minute", minute.toString()))

                _fearGreedUpdateSettings.value = _fearGreedUpdateSettings.value.copy(
                    updateHour = hour,
                    updateMinute = minute
                )

                WorkManagerHelper.scheduleFearGreedUpdate(context, hour, minute)
                _message.value = "Fear & Greed Index 업데이트 시간이 ${hour}:${String.format("%02d", minute)}로 설정되었습니다"
            } catch (e: Exception) {
                _message.value = "시간 설정 실패: ${e.message}"
            }
        }
    }

    fun updateFearGreedNow() {
        viewModelScope.launch {
            try {
                _fearGreedUpdateSettings.value = _fearGreedUpdateSettings.value.copy(isUpdating = true)
                _message.value = "Fear & Greed Index 업데이트 중..."

                val result = fearGreedRepository.updateFearGreed()

                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    loadFearGreedInfo()
                    _message.value = "업데이트 완료: ${count}개 데이터"
                } else {
                    _message.value = "업데이트 실패: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _message.value = "오류 발생: ${e.message}"
            } finally {
                _fearGreedUpdateSettings.value = _fearGreedUpdateSettings.value.copy(isUpdating = false)
            }
        }
    }

    fun setSearchHistoryLimit(limit: Int) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("search_history_limit", limit.toString()))
                _searchHistoryLimit.value = limit
                _message.value = "검색 히스토리가 최대 ${limit}개로 설정되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setFearGreedPeriodDays(days: Int) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("fear_greed_period_days", days.toString()))
                _fearGreedPeriodDays.value = days
                val monthText = when (days) {
                    180 -> "6개월"
                    365 -> "12개월"
                    540 -> "18개월"
                    730 -> "24개월"
                    else -> "${days}일"
                }
                _message.value = "Fear & Greed Index 데이터 수집 기간이 ${monthText}로 설정되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setMarketOscillatorUpdateTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("market_oscillator_update_hour", hour.toString()))
                etfDao.saveSetting(Setting("market_oscillator_update_minute", minute.toString()))

                _marketOscillatorUpdateSettings.value = _marketOscillatorUpdateSettings.value.copy(
                    updateHour = hour,
                    updateMinute = minute
                )

                WorkManagerHelper.scheduleMarketOscillatorUpdate(context, hour, minute)
                _message.value = "과매수/과매도 업데이트 시간이 ${hour}:${String.format("%02d", minute)}로 설정되었습니다"
            } catch (e: Exception) {
                _message.value = "시간 설정 실패: ${e.message}"
            }
        }
    }

    fun updateMarketOscillatorsNow() {
        viewModelScope.launch {
            try {
                _marketOscillatorUpdateSettings.value = _marketOscillatorUpdateSettings.value.copy(isUpdating = true)
                _message.value = "과매수/과매도 데이터 업데이트 중..."

                val kospiResult = marketOscillatorRepository.updateMarketData("KOSPI")
                val kosdaqResult = marketOscillatorRepository.updateMarketData("KOSDAQ")

                if (kospiResult.isSuccess && kosdaqResult.isSuccess) {
                    val kospiCount = kospiResult.getOrNull() ?: 0
                    val kosdaqCount = kosdaqResult.getOrNull() ?: 0
                    loadMarketOscillatorInfo()
                    _message.value = "업데이트 완료: KOSPI ${kospiCount}개, KOSDAQ ${kosdaqCount}개"
                } else {
                    val errors = mutableListOf<String>()
                    if (kospiResult.isFailure) errors.add("KOSPI: ${kospiResult.exceptionOrNull()?.message}")
                    if (kosdaqResult.isFailure) errors.add("KOSDAQ: ${kosdaqResult.exceptionOrNull()?.message}")
                    _message.value = "업데이트 실패: ${errors.joinToString(", ")}"
                }
            } catch (e: Exception) {
                _message.value = "오류 발생: ${e.message}"
            } finally {
                _marketOscillatorUpdateSettings.value = _marketOscillatorUpdateSettings.value.copy(isUpdating = false)
            }
        }
    }

    fun setMarketOscillatorPeriodDays(days: Int) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("market_oscillator_period_days", days.toString()))
                _marketOscillatorPeriodDays.value = days
                val monthText = when (days) {
                    180 -> "6개월"
                    365 -> "12개월"
                    540 -> "18개월"
                    730 -> "24개월"
                    else -> "${days}일"
                }
                _message.value = "과매수/과매도 데이터 수집 기간이 ${monthText}로 설정되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun initializeData(days: Int) {
        viewModelScope.launch {
            try {
                com.etfmonitor.service.DataCollectionService.startInitialize(context, days)
                _message.value = "데이터 초기화를 시작합니다"
            } catch (e: Exception) {
                _message.value = "초기화 실패: ${e.message}"
            }
        }
    }

    fun updateData() {
        viewModelScope.launch {
            try {
                com.etfmonitor.service.DataCollectionService.startUpdate(context)
                _message.value = "데이터 업데이트를 시작합니다"
            } catch (e: Exception) {
                _message.value = "업데이트 실패: ${e.message}"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    // General settings methods
    fun setDarkTheme(isDark: Boolean?) {
        viewModelScope.launch {
            try {
                val value = when (isDark) {
                    true -> "true"
                    false -> "false"
                    null -> "system"
                }
                etfDao.saveSetting(Setting("dark_theme", value))
                _isDarkTheme.value = isDark
                // ThemeManager 업데이트하여 즉시 테마 적용
                themeManager.setDarkTheme(isDark)
                val themeText = when (isDark) {
                    true -> "다크 모드"
                    false -> "라이트 모드"
                    null -> "시스템 설정"
                }
                _message.value = "테마가 ${themeText}로 변경되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setDisplayScale(scale: Float) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("font_scale_display", scale.toString()))
                _fontScaleSettings.value = _fontScaleSettings.value.copy(displayScale = scale)
                themeManager.setDisplayScale(scale)
                _message.value = "Display 폰트 크기가 ${(scale * 100).toInt()}%로 설정되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setHeadlineScale(scale: Float) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("font_scale_headline", scale.toString()))
                _fontScaleSettings.value = _fontScaleSettings.value.copy(headlineScale = scale)
                themeManager.setHeadlineScale(scale)
                _message.value = "Headline 폰트 크기가 ${(scale * 100).toInt()}%로 설정되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setTitleScale(scale: Float) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("font_scale_title", scale.toString()))
                _fontScaleSettings.value = _fontScaleSettings.value.copy(titleScale = scale)
                themeManager.setTitleScale(scale)
                _message.value = "Title 폰트 크기가 ${(scale * 100).toInt()}%로 설정되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setBodyScale(scale: Float) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("font_scale_body", scale.toString()))
                _fontScaleSettings.value = _fontScaleSettings.value.copy(bodyScale = scale)
                themeManager.setBodyScale(scale)
                _message.value = "Body 폰트 크기가 ${(scale * 100).toInt()}%로 설정되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setLabelScale(scale: Float) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("font_scale_label", scale.toString()))
                _fontScaleSettings.value = _fontScaleSettings.value.copy(labelScale = scale)
                themeManager.setLabelScale(scale)
                _message.value = "Label 폰트 크기가 ${(scale * 100).toInt()}%로 설정되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    // 차트 색상 설정 메서드들
    fun setMarketCapOscillatorLineColor1(color: Int) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("chart_marketcap_line1", color.toString()))
                val updated = _chartColorSettings.value.marketCapOscillator.copy(lineColor1 = color)
                _chartColorSettings.value = _chartColorSettings.value.copy(marketCapOscillator = updated)
                themeManager.setMarketCapOscillatorColors(updated)
                _message.value = "시가총액 라인 색상이 변경되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setMarketCapOscillatorLineColor2(color: Int) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("chart_marketcap_line2", color.toString()))
                val updated = _chartColorSettings.value.marketCapOscillator.copy(lineColor2 = color)
                _chartColorSettings.value = _chartColorSettings.value.copy(marketCapOscillator = updated)
                themeManager.setMarketCapOscillatorColors(updated)
                _message.value = "오실레이터 라인 색상이 변경되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setMarketCapOscillatorTextColor(color: Int?) {
        viewModelScope.launch {
            try {
                if (color != null) {
                    etfDao.saveSetting(Setting("chart_marketcap_text", color.toString()))
                } else {
                    etfDao.deleteSetting("chart_marketcap_text")
                }
                val updated = _chartColorSettings.value.marketCapOscillator.copy(textColor = color)
                _chartColorSettings.value = _chartColorSettings.value.copy(marketCapOscillator = updated)
                themeManager.setMarketCapOscillatorColors(updated)
                _message.value = "시가총액 차트 텍스트 색상이 변경되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setMarketCapOscillatorLegendColor(color: Int?) {
        viewModelScope.launch {
            try {
                if (color != null) {
                    etfDao.saveSetting(Setting("chart_marketcap_legend", color.toString()))
                } else {
                    etfDao.deleteSetting("chart_marketcap_legend")
                }
                val updated = _chartColorSettings.value.marketCapOscillator.copy(legendColor = color)
                _chartColorSettings.value = _chartColorSettings.value.copy(marketCapOscillator = updated)
                themeManager.setMarketCapOscillatorColors(updated)
                _message.value = "시가총액 차트 범례 색상이 변경되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setMacdLineColor1(color: Int) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("chart_macd_line1", color.toString()))
                val updated = _chartColorSettings.value.macd.copy(lineColor1 = color)
                _chartColorSettings.value = _chartColorSettings.value.copy(macd = updated)
                themeManager.setMacdColors(updated)
                _message.value = "MACD 라인 색상이 변경되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setMacdLineColor2(color: Int) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("chart_macd_line2", color.toString()))
                val updated = _chartColorSettings.value.macd.copy(lineColor2 = color)
                _chartColorSettings.value = _chartColorSettings.value.copy(macd = updated)
                themeManager.setMacdColors(updated)
                _message.value = "Signal 라인 색상이 변경되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setMacdPositiveColor(color: Int) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("chart_macd_positive", color.toString()))
                val updated = _chartColorSettings.value.macd.copy(positiveColor = color)
                _chartColorSettings.value = _chartColorSettings.value.copy(macd = updated)
                themeManager.setMacdColors(updated)
                _message.value = "MACD 양수 색상이 변경되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setMacdNegativeColor(color: Int) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("chart_macd_negative", color.toString()))
                val updated = _chartColorSettings.value.macd.copy(negativeColor = color)
                _chartColorSettings.value = _chartColorSettings.value.copy(macd = updated)
                themeManager.setMacdColors(updated)
                _message.value = "MACD 음수 색상이 변경되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setMacdTextColor(color: Int?) {
        viewModelScope.launch {
            try {
                if (color != null) {
                    etfDao.saveSetting(Setting("chart_macd_text", color.toString()))
                } else {
                    etfDao.deleteSetting("chart_macd_text")
                }
                val updated = _chartColorSettings.value.macd.copy(textColor = color)
                _chartColorSettings.value = _chartColorSettings.value.copy(macd = updated)
                themeManager.setMacdColors(updated)
                _message.value = "MACD 텍스트 색상이 변경되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setMacdLegendColor(color: Int?) {
        viewModelScope.launch {
            try {
                if (color != null) {
                    etfDao.saveSetting(Setting("chart_macd_legend", color.toString()))
                } else {
                    etfDao.deleteSetting("chart_macd_legend")
                }
                val updated = _chartColorSettings.value.macd.copy(legendColor = color)
                _chartColorSettings.value = _chartColorSettings.value.copy(macd = updated)
                themeManager.setMacdColors(updated)
                _message.value = "MACD 범례 색상이 변경되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setMarketDepositLineColor1(color: Int) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("chart_deposit_line1", color.toString()))
                val updated = _chartColorSettings.value.marketDeposit.copy(lineColor1 = color)
                _chartColorSettings.value = _chartColorSettings.value.copy(marketDeposit = updated)
                themeManager.setMarketDepositColors(updated)
                _message.value = "고객예탁금 라인 색상이 변경되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setMarketDepositLineColor2(color: Int) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("chart_deposit_line2", color.toString()))
                val updated = _chartColorSettings.value.marketDeposit.copy(lineColor2 = color)
                _chartColorSettings.value = _chartColorSettings.value.copy(marketDeposit = updated)
                themeManager.setMarketDepositColors(updated)
                _message.value = "신용잔고 라인 색상이 변경되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setMarketDepositTextColor(color: Int?) {
        viewModelScope.launch {
            try {
                if (color != null) {
                    etfDao.saveSetting(Setting("chart_deposit_text", color.toString()))
                } else {
                    etfDao.deleteSetting("chart_deposit_text")
                }
                val updated = _chartColorSettings.value.marketDeposit.copy(textColor = color)
                _chartColorSettings.value = _chartColorSettings.value.copy(marketDeposit = updated)
                themeManager.setMarketDepositColors(updated)
                _message.value = "증시자금 차트 텍스트 색상이 변경되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setMarketDepositLegendColor(color: Int?) {
        viewModelScope.launch {
            try {
                if (color != null) {
                    etfDao.saveSetting(Setting("chart_deposit_legend", color.toString()))
                } else {
                    etfDao.deleteSetting("chart_deposit_legend")
                }
                val updated = _chartColorSettings.value.marketDeposit.copy(legendColor = color)
                _chartColorSettings.value = _chartColorSettings.value.copy(marketDeposit = updated)
                themeManager.setMarketDepositColors(updated)
                _message.value = "증시자금 차트 범례 색상이 변경되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setFearGreedLineColor1(color: Int) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("chart_feargreed_line1", color.toString()))
                val updated = _chartColorSettings.value.fearGreed.copy(lineColor1 = color)
                _chartColorSettings.value = _chartColorSettings.value.copy(fearGreed = updated)
                themeManager.setFearGreedColors(updated)
                _message.value = "Fear & Greed Oscillator 라인 색상이 변경되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setFearGreedLineColor2(color: Int) {
        viewModelScope.launch {
            try {
                etfDao.saveSetting(Setting("chart_feargreed_line2", color.toString()))
                val updated = _chartColorSettings.value.fearGreed.copy(lineColor2 = color)
                _chartColorSettings.value = _chartColorSettings.value.copy(fearGreed = updated)
                themeManager.setFearGreedColors(updated)
                _message.value = "지수 라인 색상이 변경되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setFearGreedTextColor(color: Int?) {
        viewModelScope.launch {
            try {
                if (color != null) {
                    etfDao.saveSetting(Setting("chart_feargreed_text", color.toString()))
                } else {
                    etfDao.deleteSetting("chart_feargreed_text")
                }
                val updated = _chartColorSettings.value.fearGreed.copy(textColor = color)
                _chartColorSettings.value = _chartColorSettings.value.copy(fearGreed = updated)
                themeManager.setFearGreedColors(updated)
                _message.value = "Fear & Greed 차트 텍스트 색상이 변경되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setFearGreedLegendColor(color: Int?) {
        viewModelScope.launch {
            try {
                if (color != null) {
                    etfDao.saveSetting(Setting("chart_feargreed_legend", color.toString()))
                } else {
                    etfDao.deleteSetting("chart_feargreed_legend")
                }
                val updated = _chartColorSettings.value.fearGreed.copy(legendColor = color)
                _chartColorSettings.value = _chartColorSettings.value.copy(fearGreed = updated)
                themeManager.setFearGreedColors(updated)
                _message.value = "Fear & Greed 차트 범례 색상이 변경되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun resetChartColors() {
        viewModelScope.launch {
            try {
                // 모든 차트 색상 설정 삭제
                etfDao.deleteSetting("chart_marketcap_line1")
                etfDao.deleteSetting("chart_marketcap_line2")
                etfDao.deleteSetting("chart_marketcap_text")
                etfDao.deleteSetting("chart_marketcap_legend")
                etfDao.deleteSetting("chart_macd_line1")
                etfDao.deleteSetting("chart_macd_line2")
                etfDao.deleteSetting("chart_macd_positive")
                etfDao.deleteSetting("chart_macd_negative")
                etfDao.deleteSetting("chart_macd_text")
                etfDao.deleteSetting("chart_macd_legend")
                etfDao.deleteSetting("chart_deposit_line1")
                etfDao.deleteSetting("chart_deposit_line2")
                etfDao.deleteSetting("chart_deposit_text")
                etfDao.deleteSetting("chart_deposit_legend")
                etfDao.deleteSetting("chart_feargreed_line1")
                etfDao.deleteSetting("chart_feargreed_line2")
                etfDao.deleteSetting("chart_feargreed_text")
                etfDao.deleteSetting("chart_feargreed_legend")

                val defaultSettings = ChartColorSettings()
                _chartColorSettings.value = defaultSettings
                themeManager.setChartColorSettings(defaultSettings)
                _message.value = "차트 색상이 기본값으로 초기화되었습니다"
            } catch (e: Exception) {
                _message.value = "초기화 실패: ${e.message}"
            }
        }
    }

    // ==================== AI API 키 관리 ====================

    /**
     * API 키 설정 여부 확인
     */
    private fun checkApiKeyStatus() {
        viewModelScope.launch {
            _selectedProvider.value = apiKeyProvider.getSelectedProvider()
            _isClaudeApiKeyConfigured.value = apiKeyProvider.hasApiKey(AIProvider.CLAUDE)
            _isGeminiApiKeyConfigured.value = apiKeyProvider.hasApiKey(AIProvider.GEMINI)
        }
    }

    /**
     * AI 프로바이더 선택
     */
    fun setSelectedProvider(provider: AIProvider) {
        viewModelScope.launch {
            try {
                apiKeyProvider.setSelectedProvider(provider)
                _selectedProvider.value = provider
                _message.value = "${provider.toDisplayName()}이(가) 선택되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    /**
     * Claude API 키 설정
     */
    fun setClaudeApiKey(apiKey: String) {
        viewModelScope.launch {
            try {
                if (apiKey.isBlank()) {
                    _message.value = "API 키를 입력해주세요"
                    return@launch
                }

                apiKeyProvider.setApiKey(AIProvider.CLAUDE, apiKey)
                _isClaudeApiKeyConfigured.value = true
                _message.value = "Claude API 키가 저장되었습니다"
            } catch (e: Exception) {
                _message.value = "API 키 저장 실패: ${e.message}"
            }
        }
    }

    /**
     * Gemini API 키 설정
     */
    fun setGeminiApiKey(apiKey: String) {
        viewModelScope.launch {
            try {
                if (apiKey.isBlank()) {
                    _message.value = "API 키를 입력해주세요"
                    return@launch
                }

                apiKeyProvider.setApiKey(AIProvider.GEMINI, apiKey)
                _isGeminiApiKeyConfigured.value = true
                _message.value = "Gemini API 키가 저장되었습니다"
            } catch (e: Exception) {
                _message.value = "API 키 저장 실패: ${e.message}"
            }
        }
    }

    /**
     * API 키 설정 (하위 호환성)
     */
    @Deprecated("Use setClaudeApiKey or setGeminiApiKey instead")
    fun setApiKey(apiKey: String) {
        when (_selectedProvider.value) {
            AIProvider.CLAUDE -> setClaudeApiKey(apiKey)
            AIProvider.GEMINI -> setGeminiApiKey(apiKey)
        }
    }

    /**
     * Claude API 키 제거
     */
    fun clearClaudeApiKey() {
        viewModelScope.launch {
            try {
                apiKeyProvider.removeApiKey(AIProvider.CLAUDE)
                _isClaudeApiKeyConfigured.value = false
                if (_selectedProvider.value == AIProvider.CLAUDE) {
                    _apiKeyTestState.value = ApiKeyTestState.Idle
                }
                _message.value = "Claude API 키가 삭제되었습니다"
            } catch (e: Exception) {
                _message.value = "API 키 삭제 실패: ${e.message}"
            }
        }
    }

    /**
     * Gemini API 키 제거
     */
    fun clearGeminiApiKey() {
        viewModelScope.launch {
            try {
                apiKeyProvider.removeApiKey(AIProvider.GEMINI)
                _isGeminiApiKeyConfigured.value = false
                if (_selectedProvider.value == AIProvider.GEMINI) {
                    _apiKeyTestState.value = ApiKeyTestState.Idle
                }
                _message.value = "Gemini API 키가 삭제되었습니다"
            } catch (e: Exception) {
                _message.value = "API 키 삭제 실패: ${e.message}"
            }
        }
    }

    /**
     * API 키 제거 (하위 호환성)
     */
    @Deprecated("Use clearClaudeApiKey or clearGeminiApiKey instead")
    fun clearApiKey() {
        when (_selectedProvider.value) {
            AIProvider.CLAUDE -> clearClaudeApiKey()
            AIProvider.GEMINI -> clearGeminiApiKey()
        }
    }

    /**
     * API 연결 테스트
     */
    fun testApiConnection() {
        viewModelScope.launch {
            try {
                _apiKeyTestState.value = ApiKeyTestState.Testing

                val result = aiAnalysisRepository.testApiConnection()

                _apiKeyTestState.value = if (result.isSuccess) {
                    _message.value = "${_selectedProvider.value.toDisplayName()} API 연결 성공!"
                    ApiKeyTestState.Success
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "연결 실패"
                    ApiKeyTestState.Error(errorMsg)
                }
            } catch (e: Exception) {
                _apiKeyTestState.value = ApiKeyTestState.Error(e.message ?: "알 수 없는 오류")
            }
        }
    }

    /**
     * API 테스트 상태 초기화
     */
    fun clearApiTestState() {
        _apiKeyTestState.value = ApiKeyTestState.Idle
    }
}