package com.etfmonitor.ui.screens.settings

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

// ==================== Data Classes ====================

data class StockUpdateSettings(
    val updateHour: Int = 1,
    val updateMinute: Int = 0,
    val lastUpdateTime: Long? = null,
    val stockCount: Int = 0,
    val isUpdating: Boolean = false
)

data class MarketDepositUpdateSettings(
    val updateHour: Int = 2,
    val updateMinute: Int = 0,
    val lastUpdateTime: Long? = null,
    val depositCount: Int = 0,
    val isUpdating: Boolean = false
)

data class FearGreedUpdateSettings(
    val updateHour: Int = 3,
    val updateMinute: Int = 0,
    val lastUpdateTime: Long? = null,
    val kospiCount: Int = 0,
    val kosdaqCount: Int = 0,
    val isUpdating: Boolean = false
)

data class MarketOscillatorUpdateSettings(
    val updateHour: Int = 4,
    val updateMinute: Int = 0,
    val lastUpdateTime: Long? = null,
    val kospiCount: Int = 0,
    val kosdaqCount: Int = 0,
    val isUpdating: Boolean = false
)

data class AdvancedAnalysisSettings(
    val updateHour: Int = 18,
    val updateMinute: Int = 30,
    val lastUpdateTime: Long? = null,
    val isUpdating: Boolean = false
)

sealed class ApiKeyTestState {
    object Idle : ApiKeyTestState()
    object Testing : ApiKeyTestState()
    object Success : ApiKeyTestState()
    data class Error(val message: String) : ApiKeyTestState()
}

// 차트 색상 타입 (코드 중복 제거용)
enum class ChartType { MARKET_CAP, MACD, DEPOSIT, FEAR_GREED }
enum class ColorProperty { LINE1, LINE2, TEXT, LEGEND, POSITIVE, NEGATIVE }

/**
 * SettingsViewModel - Optimized for code reduction and maintainability
 *
 * 최적화:
 * - 반복 코드 패턴을 제네릭 헬퍼 함수로 통합
 * - 차트 색상 설정을 단일 함수로 처리
 * - 설정 저장/로드 로직 간소화
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

    companion object {
        private const val TAG = "SettingsViewModel"

        // 설정 키 상수
        private object Keys {
            const val THEMES = "themes"
            const val EXCLUSIONS = "exclusions"
            const val DEFAULT_DAYS = "default_days"
            const val SEARCH_HISTORY_LIMIT = "search_history_limit"
            const val FEAR_GREED_PERIOD = "fear_greed_period_days"
            const val OSCILLATOR_PERIOD = "market_oscillator_period_days"
            const val DARK_THEME = "dark_theme"

            fun updateHour(type: String) = "${type}_update_hour"
            fun updateMinute(type: String) = "${type}_update_minute"
            fun fontScale(type: String) = "font_scale_$type"
            fun chartColor(chart: String, prop: String) = "chart_${chart}_$prop"
        }
    }

    // ==================== State Flows ====================

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

    private val _advancedAnalysisSettings = MutableStateFlow(AdvancedAnalysisSettings())
    val advancedAnalysisSettings: StateFlow<AdvancedAnalysisSettings> = _advancedAnalysisSettings.asStateFlow()

    private val _searchHistoryLimit = MutableStateFlow(15)
    val searchHistoryLimit: StateFlow<Int> = _searchHistoryLimit.asStateFlow()

    private val _fearGreedPeriodDays = MutableStateFlow(365)
    val fearGreedPeriodDays: StateFlow<Int> = _fearGreedPeriodDays.asStateFlow()

    private val _marketOscillatorPeriodDays = MutableStateFlow(365)
    val marketOscillatorPeriodDays: StateFlow<Int> = _marketOscillatorPeriodDays.asStateFlow()

    private val _isDarkTheme = MutableStateFlow<Boolean?>(null)
    val isDarkTheme: StateFlow<Boolean?> = _isDarkTheme.asStateFlow()

    private val _fontScaleSettings = MutableStateFlow(FontScaleSettings())
    val fontScaleSettings: StateFlow<FontScaleSettings> = _fontScaleSettings.asStateFlow()

    private val _chartColorSettings = MutableStateFlow(ChartColorSettings())
    val chartColorSettings: StateFlow<ChartColorSettings> = _chartColorSettings.asStateFlow()

    // AI 관련 상태
    private val _selectedProvider = MutableStateFlow(AIProvider.CLAUDE)
    val selectedProvider: StateFlow<AIProvider> = _selectedProvider.asStateFlow()

    private val _isClaudeApiKeyConfigured = MutableStateFlow(false)
    val isClaudeApiKeyConfigured: StateFlow<Boolean> = _isClaudeApiKeyConfigured.asStateFlow()

    private val _isGeminiApiKeyConfigured = MutableStateFlow(false)
    val isGeminiApiKeyConfigured: StateFlow<Boolean> = _isGeminiApiKeyConfigured.asStateFlow()

    private val _apiKeyTestState = MutableStateFlow<ApiKeyTestState>(ApiKeyTestState.Idle)
    val apiKeyTestState: StateFlow<ApiKeyTestState> = _apiKeyTestState.asStateFlow()

    private val _claudeModels = MutableStateFlow<List<com.etfmonitor.ai.AIModel>>(emptyList())
    val claudeModels: StateFlow<List<com.etfmonitor.ai.AIModel>> = _claudeModels.asStateFlow()

    private val _geminiModels = MutableStateFlow<List<com.etfmonitor.ai.AIModel>>(emptyList())
    val geminiModels: StateFlow<List<com.etfmonitor.ai.AIModel>> = _geminiModels.asStateFlow()

    private val _selectedClaudeModel = MutableStateFlow<String?>(null)
    val selectedClaudeModel: StateFlow<String?> = _selectedClaudeModel.asStateFlow()

    private val _selectedGeminiModel = MutableStateFlow<String?>(null)
    val selectedGeminiModel: StateFlow<String?> = _selectedGeminiModel.asStateFlow()

    private val _isLoadingClaudeModels = MutableStateFlow(false)
    val isLoadingClaudeModels: StateFlow<Boolean> = _isLoadingClaudeModels.asStateFlow()

    private val _isLoadingGeminiModels = MutableStateFlow(false)
    val isLoadingGeminiModels: StateFlow<Boolean> = _isLoadingGeminiModels.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        loadAllSettings()
    }

    // ==================== Helper Functions ====================

    /** 공통 설정 저장 패턴 - 코드 중복 제거 */
    private inline fun saveSetting(
        successMessage: String,
        crossinline action: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                action()
                _message.value = successMessage
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    // ==================== Settings Load ====================

    private fun loadAllSettings() {
        viewModelScope.launch {
            loadBasicSettings()
            loadUpdateScheduleSettings()
            loadThemeSettings()
            loadChartColorSettings()
            loadDataInfo()
            checkApiKeyStatus()
        }
    }

    private suspend fun loadBasicSettings() {
        _themes.value = repository.getThemes()
        _exclusions.value = repository.getExclusions()
        _defaultDays.value = repository.getDefaultDays()
        _searchHistoryLimit.value = etfDao.getSetting(Keys.SEARCH_HISTORY_LIMIT)?.toIntOrNull() ?: 15
        _fearGreedPeriodDays.value = etfDao.getSetting(Keys.FEAR_GREED_PERIOD)?.toIntOrNull() ?: 365
        _marketOscillatorPeriodDays.value = etfDao.getSetting(Keys.OSCILLATOR_PERIOD)?.toIntOrNull() ?: 365
    }

    private suspend fun loadUpdateScheduleSettings() {
        // Stock update
        val stockHour = etfDao.getSetting(Keys.updateHour("stock"))?.toIntOrNull() ?: 1
        val stockMinute = etfDao.getSetting(Keys.updateMinute("stock"))?.toIntOrNull() ?: 0
        _stockUpdateSettings.value = _stockUpdateSettings.value.copy(
            updateHour = stockHour, updateMinute = stockMinute
        )
        WorkManagerHelper.scheduleStockUpdate(context, stockHour, stockMinute)

        // Market deposit update
        val depositHour = etfDao.getSetting(Keys.updateHour("market_deposit"))?.toIntOrNull() ?: 2
        val depositMinute = etfDao.getSetting(Keys.updateMinute("market_deposit"))?.toIntOrNull() ?: 0
        _marketDepositUpdateSettings.value = _marketDepositUpdateSettings.value.copy(
            updateHour = depositHour, updateMinute = depositMinute
        )
        WorkManagerHelper.scheduleMarketDepositUpdate(context, depositHour, depositMinute)

        // Fear & Greed update
        val fgHour = etfDao.getSetting(Keys.updateHour("fear_greed"))?.toIntOrNull() ?: 3
        val fgMinute = etfDao.getSetting(Keys.updateMinute("fear_greed"))?.toIntOrNull() ?: 0
        _fearGreedUpdateSettings.value = _fearGreedUpdateSettings.value.copy(
            updateHour = fgHour, updateMinute = fgMinute
        )
        WorkManagerHelper.scheduleFearGreedUpdate(context, fgHour, fgMinute)

        // Market oscillator update
        val oscHour = etfDao.getSetting(Keys.updateHour("market_oscillator"))?.toIntOrNull() ?: 4
        val oscMinute = etfDao.getSetting(Keys.updateMinute("market_oscillator"))?.toIntOrNull() ?: 0
        _marketOscillatorUpdateSettings.value = _marketOscillatorUpdateSettings.value.copy(
            updateHour = oscHour, updateMinute = oscMinute
        )
        WorkManagerHelper.scheduleMarketOscillatorUpdate(context, oscHour, oscMinute)

        // Advanced analysis update (default 18:30)
        val advHour = etfDao.getSetting(Keys.updateHour("advanced_analysis"))?.toIntOrNull() ?: 18
        val advMinute = etfDao.getSetting(Keys.updateMinute("advanced_analysis"))?.toIntOrNull() ?: 30
        _advancedAnalysisSettings.value = _advancedAnalysisSettings.value.copy(
            updateHour = advHour, updateMinute = advMinute
        )
        WorkManagerHelper.scheduleAdvancedAnalysis(context, advHour, advMinute)
    }

    private suspend fun loadThemeSettings() {
        _isDarkTheme.value = when (etfDao.getSetting(Keys.DARK_THEME)) {
            "true" -> true
            "false" -> false
            else -> null
        }

        _fontScaleSettings.value = FontScaleSettings(
            displayScale = etfDao.getSetting(Keys.fontScale("display"))?.toFloatOrNull() ?: 1.0f,
            headlineScale = etfDao.getSetting(Keys.fontScale("headline"))?.toFloatOrNull() ?: 1.0f,
            titleScale = etfDao.getSetting(Keys.fontScale("title"))?.toFloatOrNull() ?: 1.0f,
            bodyScale = etfDao.getSetting(Keys.fontScale("body"))?.toFloatOrNull() ?: 1.0f,
            labelScale = etfDao.getSetting(Keys.fontScale("label"))?.toFloatOrNull() ?: 1.0f
        )
    }

    private suspend fun loadChartColorSettings() {
        val default = ChartColorSettings()

        // 색상 로드를 위한 suspend 헬퍼 함수
        suspend fun loadColor(chart: String, prop: String, defaultVal: Int): Int =
            etfDao.getSetting(Keys.chartColor(chart, prop))?.toIntOrNull() ?: defaultVal

        suspend fun loadOptionalColor(chart: String, prop: String): Int? =
            etfDao.getSetting(Keys.chartColor(chart, prop))?.toIntOrNull()

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

        _chartColorSettings.value = settings
        themeManager.setChartColorSettings(settings)
    }

    private fun loadDataInfo() {
        viewModelScope.launch {
            try {
                // Stock info
                _stockUpdateSettings.value = _stockUpdateSettings.value.copy(
                    stockCount = stockRepository.getStockCount(),
                    lastUpdateTime = stockRepository.getLastUpdateTime()
                )

                // Market deposit info
                _marketDepositUpdateSettings.value = _marketDepositUpdateSettings.value.copy(
                    depositCount = marketDepositRepository.getDepositCount(),
                    lastUpdateTime = marketDepositRepository.getLastUpdateTime()
                )

                // Fear & Greed info
                val fgKospiUpdate = fearGreedRepository.getLastUpdateTime("KOSPI")
                val fgKosdaqUpdate = fearGreedRepository.getLastUpdateTime("KOSDAQ")
                _fearGreedUpdateSettings.value = _fearGreedUpdateSettings.value.copy(
                    kospiCount = fearGreedRepository.getCountByMarket("KOSPI"),
                    kosdaqCount = fearGreedRepository.getCountByMarket("KOSDAQ"),
                    lastUpdateTime = maxOf(fgKospiUpdate ?: 0L, fgKosdaqUpdate ?: 0L).takeIf { it > 0L }
                )

                // Market oscillator info
                val oscKospiUpdate = marketOscillatorRepository.getLatestData("KOSPI")?.lastUpdated
                val oscKosdaqUpdate = marketOscillatorRepository.getLatestData("KOSDAQ")?.lastUpdated
                _marketOscillatorUpdateSettings.value = _marketOscillatorUpdateSettings.value.copy(
                    kospiCount = marketOscillatorRepository.getDataCount("KOSPI"),
                    kosdaqCount = marketOscillatorRepository.getDataCount("KOSDAQ"),
                    lastUpdateTime = maxOf(oscKospiUpdate ?: 0L, oscKosdaqUpdate ?: 0L).takeIf { it > 0L }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading data info", e)
            }
        }
    }

    // ==================== Basic Settings ====================

    fun setDefaultDays(days: Int) = saveSetting("기본 수집 기간이 ${days}일로 설정되었습니다") {
        repository.setDefaultDays(days)
        _defaultDays.value = days
    }

    fun addTheme(theme: String) {
        if (theme.isBlank()) { _message.value = "키워드를 입력하세요"; return }
        saveSetting("테마 추가됨: $theme") {
            repository.addTheme(theme)
            _themes.value = repository.getThemes()
        }
    }

    fun removeTheme(theme: String) = saveSetting("테마 제거됨: $theme") {
        repository.removeTheme(theme)
        _themes.value = repository.getThemes()
    }

    fun addExclusion(keyword: String) {
        if (keyword.isBlank()) { _message.value = "키워드를 입력하세요"; return }
        saveSetting("제외 키워드 추가됨: $keyword") {
            repository.addExclusion(keyword)
            _exclusions.value = repository.getExclusions()
        }
    }

    fun removeExclusion(keyword: String) = saveSetting("제외 키워드 제거됨: $keyword") {
        repository.removeExclusion(keyword)
        _exclusions.value = repository.getExclusions()
    }

    fun resetDatabase() = saveSetting("데이터베이스가 초기화되었습니다") { repository.resetDatabase() }

    fun setSearchHistoryLimit(limit: Int) = saveSetting("검색 히스토리가 최대 ${limit}개로 설정되었습니다") {
        etfDao.saveSetting(Setting(Keys.SEARCH_HISTORY_LIMIT, limit.toString()))
        _searchHistoryLimit.value = limit
    }

    fun setFearGreedPeriodDays(days: Int) = saveSetting(formatPeriodMessage("Fear & Greed Index", days)) {
        etfDao.saveSetting(Setting(Keys.FEAR_GREED_PERIOD, days.toString()))
        _fearGreedPeriodDays.value = days
    }

    fun setMarketOscillatorPeriodDays(days: Int) = saveSetting(formatPeriodMessage("과매수/과매도", days)) {
        etfDao.saveSetting(Setting(Keys.OSCILLATOR_PERIOD, days.toString()))
        _marketOscillatorPeriodDays.value = days
    }

    private fun formatPeriodMessage(name: String, days: Int): String {
        val period = when (days) {
            180 -> "6개월"; 365 -> "12개월"; 540 -> "18개월"; 730 -> "24개월"; else -> "${days}일"
        }
        return "$name 데이터 수집 기간이 ${period}로 설정되었습니다"
    }

    // ==================== Update Time Settings ====================

    fun setUpdateTime(hour: Int, minute: Int) = setSchedule("stock", hour, minute, "업데이트") {
        _stockUpdateSettings.value = _stockUpdateSettings.value.copy(updateHour = hour, updateMinute = minute)
        WorkManagerHelper.scheduleStockUpdate(context, hour, minute)
    }

    fun setMarketDepositUpdateTime(hour: Int, minute: Int) = setSchedule("market_deposit", hour, minute, "증시 자금 업데이트") {
        _marketDepositUpdateSettings.value = _marketDepositUpdateSettings.value.copy(updateHour = hour, updateMinute = minute)
        WorkManagerHelper.scheduleMarketDepositUpdate(context, hour, minute)
    }

    fun setFearGreedUpdateTime(hour: Int, minute: Int) = setSchedule("fear_greed", hour, minute, "Fear & Greed Index 업데이트") {
        _fearGreedUpdateSettings.value = _fearGreedUpdateSettings.value.copy(updateHour = hour, updateMinute = minute)
        WorkManagerHelper.scheduleFearGreedUpdate(context, hour, minute)
    }

    fun setMarketOscillatorUpdateTime(hour: Int, minute: Int) = setSchedule("market_oscillator", hour, minute, "과매수/과매도 업데이트") {
        _marketOscillatorUpdateSettings.value = _marketOscillatorUpdateSettings.value.copy(updateHour = hour, updateMinute = minute)
        WorkManagerHelper.scheduleMarketOscillatorUpdate(context, hour, minute)
    }

    fun setAdvancedAnalysisUpdateTime(hour: Int, minute: Int) = setSchedule("advanced_analysis", hour, minute, "고급 분석 업데이트") {
        _advancedAnalysisSettings.value = _advancedAnalysisSettings.value.copy(updateHour = hour, updateMinute = minute)
        WorkManagerHelper.scheduleAdvancedAnalysis(context, hour, minute)
    }

    private inline fun setSchedule(type: String, hour: Int, minute: Int, name: String, crossinline onSchedule: () -> Unit) {
        saveSetting("$name 시간이 ${hour}:${String.format("%02d", minute)}로 설정되었습니다") {
            etfDao.saveSetting(Setting(Keys.updateHour(type), hour.toString()))
            etfDao.saveSetting(Setting(Keys.updateMinute(type), minute.toString()))
            onSchedule()
        }
    }

    // ==================== Manual Updates ====================

    fun updateStocksNow() {
        viewModelScope.launch {
            _stockUpdateSettings.value = _stockUpdateSettings.value.copy(isUpdating = true)
            _message.value = "종목 데이터 업데이트 중..."
            try {
                val result = withTimeoutOrNull(90_000L) { stockRepository.updateStocks() }
                when {
                    result == null -> _message.value = "업데이트 시간 초과 (90초)"
                    result.isSuccess -> {
                        loadDataInfo()
                        _message.value = "업데이트 완료: ${result.getOrNull() ?: 0}개 종목"
                    }
                    else -> _message.value = "업데이트 실패: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _message.value = "오류 발생: ${e.message}"
            } finally {
                _stockUpdateSettings.value = _stockUpdateSettings.value.copy(isUpdating = false)
            }
        }
    }

    fun updateMarketDepositsNow() {
        viewModelScope.launch {
            _marketDepositUpdateSettings.value = _marketDepositUpdateSettings.value.copy(isUpdating = true)
            _message.value = "증시 자금 데이터 업데이트 중..."
            try {
                val result = withTimeoutOrNull(90_000L) { marketDepositRepository.updateDeposits(numPages = 10) }
                when {
                    result == null -> _message.value = "업데이트 시간 초과 (90초)"
                    result.isSuccess -> {
                        loadDataInfo()
                        _message.value = "업데이트 완료: ${result.getOrNull() ?: 0}개 데이터"
                    }
                    else -> _message.value = "업데이트 실패: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _message.value = "오류 발생: ${e.message}"
            } finally {
                _marketDepositUpdateSettings.value = _marketDepositUpdateSettings.value.copy(isUpdating = false)
            }
        }
    }

    fun updateFearGreedNow() {
        viewModelScope.launch {
            _fearGreedUpdateSettings.value = _fearGreedUpdateSettings.value.copy(isUpdating = true)
            _message.value = "Fear & Greed Index 업데이트 중..."
            try {
                val result = withTimeoutOrNull(90_000L) { fearGreedRepository.updateFearGreed() }
                when {
                    result == null -> _message.value = "업데이트 시간 초과 (90초)"
                    result.isSuccess -> {
                        loadDataInfo()
                        _message.value = "업데이트 완료: ${result.getOrNull() ?: 0}개 데이터"
                    }
                    else -> _message.value = "업데이트 실패: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _message.value = "오류 발생: ${e.message}"
            } finally {
                _fearGreedUpdateSettings.value = _fearGreedUpdateSettings.value.copy(isUpdating = false)
            }
        }
    }

    fun updateMarketOscillatorsNow() {
        viewModelScope.launch {
            _marketOscillatorUpdateSettings.value = _marketOscillatorUpdateSettings.value.copy(isUpdating = true)
            _message.value = "과매수/과매도 데이터 업데이트 중..."
            try {
                // 시장 오실레이터는 전체 종목 데이터 수집으로 오래 걸림 (5분 타임아웃)
                val results = withTimeoutOrNull(300_000L) {
                    val kospiResult = marketOscillatorRepository.updateMarketData("KOSPI")
                    val kosdaqResult = marketOscillatorRepository.updateMarketData("KOSDAQ")
                    Pair(kospiResult, kosdaqResult)
                }
                when {
                    results == null -> _message.value = "업데이트 시간 초과 (5분)"
                    results.first.isSuccess && results.second.isSuccess -> {
                        loadDataInfo()
                        _message.value = "업데이트 완료: KOSPI ${results.first.getOrNull() ?: 0}개, KOSDAQ ${results.second.getOrNull() ?: 0}개"
                    }
                    else -> {
                        val errors = listOfNotNull(
                            results.first.exceptionOrNull()?.let { "KOSPI: ${it.message}" },
                            results.second.exceptionOrNull()?.let { "KOSDAQ: ${it.message}" }
                        )
                        _message.value = "업데이트 실패: ${errors.joinToString(", ")}"
                    }
                }
            } catch (e: Exception) {
                _message.value = "오류 발생: ${e.message}"
            } finally {
                _marketOscillatorUpdateSettings.value = _marketOscillatorUpdateSettings.value.copy(isUpdating = false)
            }
        }
    }

    /**
     * 고급 분석 수동 실행
     * - 시총 가중 ETF 흐름 분석
     * - 외국인/기관 수급 Divergence 분석
     * - 유동성 분석 (예탁금/시총 비율)
     * - 섹터별 Fear & Greed 분석
     * - ETF 간 상관관계 분석
     */
    fun runAdvancedAnalysisNow() {
        viewModelScope.launch {
            _advancedAnalysisSettings.value = _advancedAnalysisSettings.value.copy(isUpdating = true)
            _message.value = "고급 분석 실행 중..."
            try {
                // WorkManager를 통해 백그라운드에서 실행
                WorkManagerHelper.runAdvancedAnalysisNow(context)
                _message.value = "고급 분석이 백그라운드에서 시작되었습니다"
            } catch (e: Exception) {
                _message.value = "오류 발생: ${e.message}"
            } finally {
                // 백그라운드 작업이므로 바로 isUpdating을 false로
                _advancedAnalysisSettings.value = _advancedAnalysisSettings.value.copy(isUpdating = false)
            }
        }
    }

    // ==================== Data Collection ====================

    fun initializeData(days: Int) = saveSetting("데이터 초기화를 시작합니다") {
        com.etfmonitor.service.DataCollectionService.startInitialize(context, days)
    }

    fun updateData() = saveSetting("데이터 업데이트를 시작합니다") {
        com.etfmonitor.service.DataCollectionService.startUpdate(context)
    }

    // ==================== Theme Settings ====================

    fun setDarkTheme(isDark: Boolean?) = saveSetting(when (isDark) {
        true -> "테마가 다크 모드로 변경되었습니다"
        false -> "테마가 라이트 모드로 변경되었습니다"
        null -> "테마가 시스템 설정으로 변경되었습니다"
    }) {
        val value = when (isDark) { true -> "true"; false -> "false"; null -> "system" }
        etfDao.saveSetting(Setting(Keys.DARK_THEME, value))
        _isDarkTheme.value = isDark
        themeManager.setDarkTheme(isDark)
    }

    fun setDisplayScale(scale: Float) = setFontScale("display", scale, "Display") { _fontScaleSettings.value.copy(displayScale = scale) }
    fun setHeadlineScale(scale: Float) = setFontScale("headline", scale, "Headline") { _fontScaleSettings.value.copy(headlineScale = scale) }
    fun setTitleScale(scale: Float) = setFontScale("title", scale, "Title") { _fontScaleSettings.value.copy(titleScale = scale) }
    fun setBodyScale(scale: Float) = setFontScale("body", scale, "Body") { _fontScaleSettings.value.copy(bodyScale = scale) }
    fun setLabelScale(scale: Float) = setFontScale("label", scale, "Label") { _fontScaleSettings.value.copy(labelScale = scale) }

    private inline fun setFontScale(type: String, scale: Float, displayName: String, crossinline updateState: () -> FontScaleSettings) {
        saveSetting("$displayName 폰트 크기가 ${(scale * 100).toInt()}%로 설정되었습니다") {
            etfDao.saveSetting(Setting(Keys.fontScale(type), scale.toString()))
            _fontScaleSettings.value = updateState()
            when (type) {
                "display" -> themeManager.setDisplayScale(scale)
                "headline" -> themeManager.setHeadlineScale(scale)
                "title" -> themeManager.setTitleScale(scale)
                "body" -> themeManager.setBodyScale(scale)
                "label" -> themeManager.setLabelScale(scale)
            }
        }
    }

    // ==================== Chart Color Settings (Unified) ====================

    /** 통합 차트 색상 설정 함수 - 모든 차트 색상 변경을 단일 진입점으로 처리 */
    fun setChartColor(chartType: ChartType, property: ColorProperty, color: Int?, message: String) {
        viewModelScope.launch {
            try {
                val chartKey = when (chartType) {
                    ChartType.MARKET_CAP -> "marketcap"
                    ChartType.MACD -> "macd"
                    ChartType.DEPOSIT -> "deposit"
                    ChartType.FEAR_GREED -> "feargreed"
                }
                val propKey = when (property) {
                    ColorProperty.LINE1 -> "line1"
                    ColorProperty.LINE2 -> "line2"
                    ColorProperty.TEXT -> "text"
                    ColorProperty.LEGEND -> "legend"
                    ColorProperty.POSITIVE -> "positive"
                    ColorProperty.NEGATIVE -> "negative"
                }

                val settingKey = Keys.chartColor(chartKey, propKey)
                if (color != null) {
                    etfDao.saveSetting(Setting(settingKey, color.toString()))
                } else {
                    etfDao.deleteSetting(settingKey)
                }

                updateChartColorState(chartType, property, color)
                _message.value = message
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    private fun updateChartColorState(chartType: ChartType, property: ColorProperty, color: Int?) {
        val current = _chartColorSettings.value
        val updated = when (chartType) {
            ChartType.MARKET_CAP -> {
                val colors = current.marketCapOscillator.updateProperty(property, color)
                current.copy(marketCapOscillator = colors).also { themeManager.setMarketCapOscillatorColors(colors) }
            }
            ChartType.MACD -> {
                val colors = current.macd.updateProperty(property, color)
                current.copy(macd = colors).also { themeManager.setMacdColors(colors) }
            }
            ChartType.DEPOSIT -> {
                val colors = current.marketDeposit.updateProperty(property, color)
                current.copy(marketDeposit = colors).also { themeManager.setMarketDepositColors(colors) }
            }
            ChartType.FEAR_GREED -> {
                val colors = current.fearGreed.updateProperty(property, color)
                current.copy(fearGreed = colors).also { themeManager.setFearGreedColors(colors) }
            }
        }
        _chartColorSettings.value = updated
    }

    private fun SingleChartColorSettings.updateProperty(property: ColorProperty, color: Int?): SingleChartColorSettings = when (property) {
        ColorProperty.LINE1 -> copy(lineColor1 = color ?: lineColor1)
        ColorProperty.LINE2 -> copy(lineColor2 = color ?: lineColor2)
        ColorProperty.TEXT -> copy(textColor = color ?: textColor)
        ColorProperty.LEGEND -> copy(legendColor = color ?: legendColor)
        ColorProperty.POSITIVE -> copy(positiveColor = color ?: positiveColor)
        ColorProperty.NEGATIVE -> copy(negativeColor = color ?: negativeColor)
    }

    // 하위 호환성을 위한 개별 메서드들
    fun setMarketCapOscillatorLineColor1(color: Int) = setChartColor(ChartType.MARKET_CAP, ColorProperty.LINE1, color, "시가총액 라인 색상이 변경되었습니다")
    fun setMarketCapOscillatorLineColor2(color: Int) = setChartColor(ChartType.MARKET_CAP, ColorProperty.LINE2, color, "오실레이터 라인 색상이 변경되었습니다")
    fun setMarketCapOscillatorTextColor(color: Int?) = setChartColor(ChartType.MARKET_CAP, ColorProperty.TEXT, color, "시가총액 차트 텍스트 색상이 변경되었습니다")
    fun setMarketCapOscillatorLegendColor(color: Int?) = setChartColor(ChartType.MARKET_CAP, ColorProperty.LEGEND, color, "시가총액 차트 범례 색상이 변경되었습니다")

    fun setMacdLineColor1(color: Int) = setChartColor(ChartType.MACD, ColorProperty.LINE1, color, "MACD 라인 색상이 변경되었습니다")
    fun setMacdLineColor2(color: Int) = setChartColor(ChartType.MACD, ColorProperty.LINE2, color, "Signal 라인 색상이 변경되었습니다")
    fun setMacdPositiveColor(color: Int) = setChartColor(ChartType.MACD, ColorProperty.POSITIVE, color, "MACD 양수 색상이 변경되었습니다")
    fun setMacdNegativeColor(color: Int) = setChartColor(ChartType.MACD, ColorProperty.NEGATIVE, color, "MACD 음수 색상이 변경되었습니다")
    fun setMacdTextColor(color: Int?) = setChartColor(ChartType.MACD, ColorProperty.TEXT, color, "MACD 텍스트 색상이 변경되었습니다")
    fun setMacdLegendColor(color: Int?) = setChartColor(ChartType.MACD, ColorProperty.LEGEND, color, "MACD 범례 색상이 변경되었습니다")

    fun setMarketDepositLineColor1(color: Int) = setChartColor(ChartType.DEPOSIT, ColorProperty.LINE1, color, "고객예탁금 라인 색상이 변경되었습니다")
    fun setMarketDepositLineColor2(color: Int) = setChartColor(ChartType.DEPOSIT, ColorProperty.LINE2, color, "신용잔고 라인 색상이 변경되었습니다")
    fun setMarketDepositTextColor(color: Int?) = setChartColor(ChartType.DEPOSIT, ColorProperty.TEXT, color, "증시자금 차트 텍스트 색상이 변경되었습니다")
    fun setMarketDepositLegendColor(color: Int?) = setChartColor(ChartType.DEPOSIT, ColorProperty.LEGEND, color, "증시자금 차트 범례 색상이 변경되었습니다")

    fun setFearGreedLineColor1(color: Int) = setChartColor(ChartType.FEAR_GREED, ColorProperty.LINE1, color, "Fear & Greed Oscillator 라인 색상이 변경되었습니다")
    fun setFearGreedLineColor2(color: Int) = setChartColor(ChartType.FEAR_GREED, ColorProperty.LINE2, color, "지수 라인 색상이 변경되었습니다")
    fun setFearGreedTextColor(color: Int?) = setChartColor(ChartType.FEAR_GREED, ColorProperty.TEXT, color, "Fear & Greed 차트 텍스트 색상이 변경되었습니다")
    fun setFearGreedLegendColor(color: Int?) = setChartColor(ChartType.FEAR_GREED, ColorProperty.LEGEND, color, "Fear & Greed 차트 범례 색상이 변경되었습니다")

    fun resetChartColors() {
        viewModelScope.launch {
            try {
                // 모든 차트 색상 설정 키 삭제
                listOf("marketcap", "macd", "deposit", "feargreed").flatMap { chart ->
                    listOf("line1", "line2", "text", "legend", "positive", "negative").map { prop ->
                        Keys.chartColor(chart, prop)
                    }
                }.forEach { etfDao.deleteSetting(it) }

                val defaultSettings = ChartColorSettings()
                _chartColorSettings.value = defaultSettings
                themeManager.setChartColorSettings(defaultSettings)
                _message.value = "차트 색상이 기본값으로 초기화되었습니다"
            } catch (e: Exception) {
                _message.value = "초기화 실패: ${e.message}"
            }
        }
    }

    // ==================== AI API Key Management ====================

    private fun checkApiKeyStatus() {
        viewModelScope.launch {
            _selectedProvider.value = apiKeyProvider.getSelectedProvider()
            _isClaudeApiKeyConfigured.value = apiKeyProvider.hasApiKey(AIProvider.CLAUDE)
            _isGeminiApiKeyConfigured.value = apiKeyProvider.hasApiKey(AIProvider.GEMINI)
        }
    }

    fun setSelectedProvider(provider: AIProvider) = saveSetting("${provider.toDisplayName()}이(가) 선택되었습니다") {
        apiKeyProvider.setSelectedProvider(provider)
        _selectedProvider.value = provider
    }

    fun setClaudeApiKey(apiKey: String) {
        if (apiKey.isBlank()) { _message.value = "API 키를 입력해주세요"; return }
        saveSetting("Claude API 키가 저장되었습니다") {
            apiKeyProvider.setApiKey(AIProvider.CLAUDE, apiKey)
            _isClaudeApiKeyConfigured.value = true
        }
    }

    fun setGeminiApiKey(apiKey: String) {
        if (apiKey.isBlank()) { _message.value = "API 키를 입력해주세요"; return }
        saveSetting("Gemini API 키가 저장되었습니다") {
            apiKeyProvider.setApiKey(AIProvider.GEMINI, apiKey)
            _isGeminiApiKeyConfigured.value = true
        }
    }

    fun clearClaudeApiKey() = saveSetting("Claude API 키가 삭제되었습니다") {
        apiKeyProvider.removeApiKey(AIProvider.CLAUDE)
        _isClaudeApiKeyConfigured.value = false
        if (_selectedProvider.value == AIProvider.CLAUDE) {
            _apiKeyTestState.value = ApiKeyTestState.Idle
        }
    }

    fun clearGeminiApiKey() = saveSetting("Gemini API 키가 삭제되었습니다") {
        apiKeyProvider.removeApiKey(AIProvider.GEMINI)
        _isGeminiApiKeyConfigured.value = false
        if (_selectedProvider.value == AIProvider.GEMINI) {
            _apiKeyTestState.value = ApiKeyTestState.Idle
        }
    }

    fun testApiConnection() {
        viewModelScope.launch {
            try {
                _apiKeyTestState.value = ApiKeyTestState.Testing
                val result = aiAnalysisRepository.testApiConnection()
                _apiKeyTestState.value = if (result.isSuccess) {
                    _message.value = "${_selectedProvider.value.toDisplayName()} API 연결 성공!"
                    ApiKeyTestState.Success
                } else {
                    ApiKeyTestState.Error(result.exceptionOrNull()?.message ?: "연결 실패")
                }
            } catch (e: Exception) {
                _apiKeyTestState.value = ApiKeyTestState.Error(e.message ?: "알 수 없는 오류")
            }
        }
    }

    fun clearApiTestState() { _apiKeyTestState.value = ApiKeyTestState.Idle }

    fun loadClaudeModels() {
        viewModelScope.launch {
            try {
                _isLoadingClaudeModels.value = true
                val result = aiAnalysisRepository.listModels(AIProvider.CLAUDE)
                if (result.isSuccess) {
                    _claudeModels.value = result.getOrNull() ?: emptyList()
                    _selectedClaudeModel.value = apiKeyProvider.getSelectedModel(AIProvider.CLAUDE)
                } else {
                    _message.value = "Claude 모델 목록 조회 실패: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _message.value = "Claude 모델 목록 조회 실패: ${e.message}"
            } finally {
                _isLoadingClaudeModels.value = false
            }
        }
    }

    fun loadGeminiModels() {
        viewModelScope.launch {
            try {
                _isLoadingGeminiModels.value = true
                val result = aiAnalysisRepository.listModels(AIProvider.GEMINI)
                if (result.isSuccess) {
                    _geminiModels.value = result.getOrNull() ?: emptyList()
                    _selectedGeminiModel.value = apiKeyProvider.getSelectedModel(AIProvider.GEMINI)
                } else {
                    _message.value = "Gemini 모델 목록 조회 실패: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _message.value = "Gemini 모델 목록 조회 실패: ${e.message}"
            } finally {
                _isLoadingGeminiModels.value = false
            }
        }
    }

    fun setClaudeModel(modelId: String) = saveSetting("Claude 모델이 선택되었습니다") {
        apiKeyProvider.setSelectedModel(AIProvider.CLAUDE, modelId)
        _selectedClaudeModel.value = modelId
    }

    fun setGeminiModel(modelId: String) = saveSetting("Gemini 모델이 선택되었습니다: $modelId") {
        Log.d(TAG, "Setting Gemini model: $modelId")
        apiKeyProvider.setSelectedModel(AIProvider.GEMINI, modelId)
        _selectedGeminiModel.value = modelId
    }

    fun clearMessage() { _message.value = null }
}
