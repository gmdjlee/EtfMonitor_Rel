package com.etfmonitor.feature.settings.data.repository

import android.content.Context
import com.etfmonitor.core.network.ai.AIProvider
import com.etfmonitor.core.network.ai.ApiKeyProvider
import com.etfmonitor.core.ui.theme.ChartColorSettings
import com.etfmonitor.core.ui.theme.FontScaleSettings
import com.etfmonitor.core.ui.theme.SingleChartColorSettings
import com.etfmonitor.core.ui.theme.ThemeManager
import com.etfmonitor.core.worker.WorkManagerHelper
import com.etfmonitor.database.EtfDao
import com.etfmonitor.database.entities.Setting
import com.etfmonitor.feature.settings.data.mapper.SettingsMapper.toDomain
import com.etfmonitor.feature.settings.data.mapper.SettingsMapper.toDomainModels
import com.etfmonitor.feature.settings.data.mapper.SettingsMapper.toDataModel
import com.etfmonitor.feature.settings.domain.model.*
import com.etfmonitor.feature.settings.domain.repository.*
import com.etfmonitor.repository.AIAnalysisRepository
import com.etfmonitor.repository.DataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings Repository Implementation
 *
 * Wraps existing repositories and DAOs to provide Clean Architecture interface.
 * Maintains all existing functionality and patterns.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataRepository: DataRepository,
    private val aiAnalysisRepository: AIAnalysisRepository,
    private val apiKeyProvider: ApiKeyProvider,
    private val etfDao: EtfDao,
    private val themeManager: ThemeManager,
    @ApplicationContext private val context: Context
) : SettingsRepository {

    companion object {
        // Setting keys
        private const val KEY_SEARCH_HISTORY_LIMIT = "search_history_limit"
        private const val KEY_FEAR_GREED_PERIOD = "fear_greed_period_days"
        private const val KEY_OSCILLATOR_PERIOD = "market_oscillator_period_days"
        private const val KEY_MARKET_INDEX_PERIOD = "market_index_period_days"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_QUICK_CHART_ANALYSIS = "quick_chart_analysis_enabled"

        private fun updateHourKey(type: String) = "${type}_update_hour"
        private fun updateMinuteKey(type: String) = "${type}_update_minute"
        private fun fontScaleKey(type: String) = "font_scale_$type"
        private fun chartColorKey(chart: String, prop: String) = "chart_${chart}_$prop"
    }

    // ==================== Theme Keywords ====================

    override suspend fun getThemes(): List<String> = withContext(Dispatchers.IO) {
        dataRepository.getThemes()
    }

    override suspend fun addTheme(theme: String) = withContext(Dispatchers.IO) {
        dataRepository.addTheme(theme)
    }

    override suspend fun removeTheme(theme: String) = withContext(Dispatchers.IO) {
        dataRepository.removeTheme(theme)
    }

    override suspend fun getExclusions(): List<String> = withContext(Dispatchers.IO) {
        dataRepository.getExclusions()
    }

    override suspend fun addExclusion(keyword: String) = withContext(Dispatchers.IO) {
        dataRepository.addExclusion(keyword)
    }

    override suspend fun removeExclusion(keyword: String) = withContext(Dispatchers.IO) {
        dataRepository.removeExclusion(keyword)
    }

    // ==================== General Settings ====================

    override suspend fun getGeneralSettings(): GeneralSettings = withContext(Dispatchers.IO) {
        GeneralSettings(
            defaultDays = dataRepository.getDefaultDays(),
            searchHistoryLimit = etfDao.getSetting(KEY_SEARCH_HISTORY_LIMIT)?.toIntOrNull() ?: 15,
            fearGreedPeriodDays = etfDao.getSetting(KEY_FEAR_GREED_PERIOD)?.toIntOrNull() ?: 365,
            marketOscillatorPeriodDays = etfDao.getSetting(KEY_OSCILLATOR_PERIOD)?.toIntOrNull() ?: 365,
            marketIndexPeriodDays = etfDao.getSetting(KEY_MARKET_INDEX_PERIOD)?.toIntOrNull() ?: 30
        )
    }

    override suspend fun setDefaultDays(days: Int) = withContext(Dispatchers.IO) {
        dataRepository.setDefaultDays(days)
    }

    override suspend fun setSearchHistoryLimit(limit: Int) = withContext(Dispatchers.IO) {
        etfDao.saveSetting(Setting(KEY_SEARCH_HISTORY_LIMIT, limit.toString()))
    }

    override suspend fun setFearGreedPeriodDays(days: Int) = withContext(Dispatchers.IO) {
        etfDao.saveSetting(Setting(KEY_FEAR_GREED_PERIOD, days.toString()))
    }

    override suspend fun setMarketOscillatorPeriodDays(days: Int) = withContext(Dispatchers.IO) {
        etfDao.saveSetting(Setting(KEY_OSCILLATOR_PERIOD, days.toString()))
    }

    override suspend fun setMarketIndexPeriodDays(days: Int) = withContext(Dispatchers.IO) {
        etfDao.saveSetting(Setting(KEY_MARKET_INDEX_PERIOD, days.toString()))
    }

    // ==================== Update Schedules ====================

    override suspend fun getUpdateSchedules(): AllUpdateSchedules = withContext(Dispatchers.IO) {
        AllUpdateSchedules(
            etf = getSchedule("etf", 0, 30),
            stock = getSchedule("stock", 1, 0),
            marketDeposit = getSchedule("market_deposit", 2, 0),
            fearGreed = getSchedule("fear_greed", 3, 0),
            marketOscillator = getSchedule("market_oscillator", 4, 0),
            marketIndex = getSchedule("market_index", 5, 0),
            advancedAnalysis = getSchedule("advanced_analysis", 18, 30)
        )
    }

    private suspend fun getSchedule(type: String, defaultHour: Int, defaultMinute: Int): UpdateSchedule {
        val hour = etfDao.getSetting(updateHourKey(type))?.toIntOrNull() ?: defaultHour
        val minute = etfDao.getSetting(updateMinuteKey(type))?.toIntOrNull() ?: defaultMinute
        return UpdateSchedule(hour, minute)
    }

    override suspend fun setUpdateSchedule(type: UpdateType, hour: Int, minute: Int) = withContext(Dispatchers.IO) {
        val typeKey = when (type) {
            UpdateType.ETF -> "etf"
            UpdateType.STOCK -> "stock"
            UpdateType.MARKET_DEPOSIT -> "market_deposit"
            UpdateType.FEAR_GREED -> "fear_greed"
            UpdateType.MARKET_OSCILLATOR -> "market_oscillator"
            UpdateType.MARKET_INDEX -> "market_index"
            UpdateType.ADVANCED_ANALYSIS -> "advanced_analysis"
        }

        etfDao.saveSetting(Setting(updateHourKey(typeKey), hour.toString()))
        etfDao.saveSetting(Setting(updateMinuteKey(typeKey), minute.toString()))

        // Schedule worker
        when (type) {
            UpdateType.ETF -> WorkManagerHelper.scheduleEtfUpdate(context, hour, minute)
            UpdateType.STOCK -> WorkManagerHelper.scheduleStockUpdate(context, hour, minute)
            UpdateType.MARKET_DEPOSIT -> WorkManagerHelper.scheduleMarketDepositUpdate(context, hour, minute)
            UpdateType.FEAR_GREED -> WorkManagerHelper.scheduleFearGreedUpdate(context, hour, minute)
            UpdateType.MARKET_OSCILLATOR -> WorkManagerHelper.scheduleMarketOscillatorUpdate(context, hour, minute)
            UpdateType.MARKET_INDEX -> WorkManagerHelper.scheduleMarketIndexUpdate(context, hour, minute)
            UpdateType.ADVANCED_ANALYSIS -> WorkManagerHelper.scheduleAdvancedAnalysis(context, hour, minute)
        }
    }

    // ==================== Theme Settings ====================

    override suspend fun getAppThemeSettings(): AppThemeSettings = withContext(Dispatchers.IO) {
        val isDarkTheme = when (etfDao.getSetting(KEY_DARK_THEME)) {
            "true" -> true
            "false" -> false
            else -> null
        }

        val quickChartAnalysisEnabled = etfDao.getSetting(KEY_QUICK_CHART_ANALYSIS) == "true"

        val fontScales = FontScales(
            displayScale = etfDao.getSetting(fontScaleKey("display"))?.toFloatOrNull() ?: 1.0f,
            headlineScale = etfDao.getSetting(fontScaleKey("headline"))?.toFloatOrNull() ?: 1.0f,
            titleScale = etfDao.getSetting(fontScaleKey("title"))?.toFloatOrNull() ?: 1.0f,
            bodyScale = etfDao.getSetting(fontScaleKey("body"))?.toFloatOrNull() ?: 1.0f,
            labelScale = etfDao.getSetting(fontScaleKey("label"))?.toFloatOrNull() ?: 1.0f
        )

        val chartColors = loadChartColors()

        AppThemeSettings(
            isDarkTheme = isDarkTheme,
            quickChartAnalysisEnabled = quickChartAnalysisEnabled,
            fontScales = fontScales,
            chartColors = chartColors
        )
    }

    private suspend fun loadChartColors(): ChartColors {
        val default = ChartColorSettings()

        suspend fun loadColor(chart: String, prop: String, defaultVal: Int): Int =
            etfDao.getSetting(chartColorKey(chart, prop))?.toIntOrNull() ?: defaultVal

        return ChartColors(
            marketCapOscillator = SingleChartColors(
                lineColor1 = loadColor("marketcap", "line1", default.marketCapOscillator.lineColor1),
                lineColor2 = loadColor("marketcap", "line2", default.marketCapOscillator.lineColor2),
                textColor = loadColor("marketcap", "text", default.marketCapOscillator.textColor),
                legendColor = loadColor("marketcap", "legend", default.marketCapOscillator.legendColor)
            ),
            macd = MacdChartColors(
                lineColor1 = loadColor("macd", "line1", default.macd.lineColor1),
                lineColor2 = loadColor("macd", "line2", default.macd.lineColor2),
                positiveColor = loadColor("macd", "positive", default.macd.positiveColor),
                negativeColor = loadColor("macd", "negative", default.macd.negativeColor),
                textColor = loadColor("macd", "text", default.macd.textColor),
                legendColor = loadColor("macd", "legend", default.macd.legendColor)
            ),
            marketDeposit = SingleChartColors(
                lineColor1 = loadColor("deposit", "line1", default.marketDeposit.lineColor1),
                lineColor2 = loadColor("deposit", "line2", default.marketDeposit.lineColor2),
                textColor = loadColor("deposit", "text", default.marketDeposit.textColor),
                legendColor = loadColor("deposit", "legend", default.marketDeposit.legendColor)
            ),
            fearGreed = SingleChartColors(
                lineColor1 = loadColor("feargreed", "line1", default.fearGreed.lineColor1),
                lineColor2 = loadColor("feargreed", "line2", default.fearGreed.lineColor2),
                textColor = loadColor("feargreed", "text", default.fearGreed.textColor),
                legendColor = loadColor("feargreed", "legend", default.fearGreed.legendColor)
            )
        )
    }

    override suspend fun setDarkTheme(isDark: Boolean?) = withContext(Dispatchers.IO) {
        val value = when (isDark) {
            true -> "true"
            false -> "false"
            null -> "system"
        }
        etfDao.saveSetting(Setting(KEY_DARK_THEME, value))
        themeManager.setDarkTheme(isDark)
    }

    override suspend fun setQuickChartAnalysisEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        etfDao.saveSetting(Setting(KEY_QUICK_CHART_ANALYSIS, enabled.toString()))
    }

    override suspend fun setFontScale(type: FontScaleType, scale: Float) = withContext(Dispatchers.IO) {
        val typeKey = when (type) {
            FontScaleType.DISPLAY -> "display"
            FontScaleType.HEADLINE -> "headline"
            FontScaleType.TITLE -> "title"
            FontScaleType.BODY -> "body"
            FontScaleType.LABEL -> "label"
        }

        etfDao.saveSetting(Setting(fontScaleKey(typeKey), scale.toString()))

        when (type) {
            FontScaleType.DISPLAY -> themeManager.setDisplayScale(scale)
            FontScaleType.HEADLINE -> themeManager.setHeadlineScale(scale)
            FontScaleType.TITLE -> themeManager.setTitleScale(scale)
            FontScaleType.BODY -> themeManager.setBodyScale(scale)
            FontScaleType.LABEL -> themeManager.setLabelScale(scale)
        }
    }

    override suspend fun setChartColor(chartType: ChartType, property: ColorProperty, color: Int?) = withContext(Dispatchers.IO) {
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

        val settingKey = chartColorKey(chartKey, propKey)
        if (color != null) {
            etfDao.saveSetting(Setting(settingKey, color.toString()))
        } else {
            etfDao.deleteSetting(settingKey)
        }

        // Update ThemeManager (reload all chart colors)
        val chartColors = loadChartColors()
        updateThemeManagerChartColors(chartType, chartColors)
    }

    private fun updateThemeManagerChartColors(chartType: ChartType, colors: ChartColors) {
        when (chartType) {
            ChartType.MARKET_CAP -> themeManager.setMarketCapOscillatorColors(
                SingleChartColorSettings(
                    lineColor1 = colors.marketCapOscillator.lineColor1,
                    lineColor2 = colors.marketCapOscillator.lineColor2,
                    textColor = colors.marketCapOscillator.textColor,
                    legendColor = colors.marketCapOscillator.legendColor
                )
            )
            ChartType.MACD -> themeManager.setMacdColors(
                SingleChartColorSettings(
                    lineColor1 = colors.macd.lineColor1,
                    lineColor2 = colors.macd.lineColor2,
                    positiveColor = colors.macd.positiveColor,
                    negativeColor = colors.macd.negativeColor,
                    textColor = colors.macd.textColor,
                    legendColor = colors.macd.legendColor
                )
            )
            ChartType.DEPOSIT -> themeManager.setMarketDepositColors(
                SingleChartColorSettings(
                    lineColor1 = colors.marketDeposit.lineColor1,
                    lineColor2 = colors.marketDeposit.lineColor2,
                    textColor = colors.marketDeposit.textColor,
                    legendColor = colors.marketDeposit.legendColor
                )
            )
            ChartType.FEAR_GREED -> themeManager.setFearGreedColors(
                SingleChartColorSettings(
                    lineColor1 = colors.fearGreed.lineColor1,
                    lineColor2 = colors.fearGreed.lineColor2,
                    textColor = colors.fearGreed.textColor,
                    legendColor = colors.fearGreed.legendColor
                )
            )
        }
    }

    override suspend fun resetChartColors() = withContext(Dispatchers.IO) {
        listOf("marketcap", "macd", "deposit", "feargreed").flatMap { chart ->
            listOf("line1", "line2", "text", "legend", "positive", "negative").map { prop ->
                chartColorKey(chart, prop)
            }
        }.forEach { etfDao.deleteSetting(it) }

        val defaultSettings = ChartColorSettings()
        themeManager.setChartColorSettings(defaultSettings)
    }

    // ==================== AI Settings ====================

    override suspend fun getAIConfiguration(): AIConfiguration = withContext(Dispatchers.IO) {
        AIConfiguration(
            selectedProvider = apiKeyProvider.getSelectedProvider().toDomain(),
            claudeConfigured = apiKeyProvider.hasApiKey(AIProvider.CLAUDE),
            geminiConfigured = apiKeyProvider.hasApiKey(AIProvider.GEMINI),
            claudeModel = apiKeyProvider.getSelectedModel(AIProvider.CLAUDE),
            geminiModel = apiKeyProvider.getSelectedModel(AIProvider.GEMINI)
        )
    }

    override suspend fun setSelectedProvider(provider: AIProviderType) = withContext(Dispatchers.IO) {
        apiKeyProvider.setSelectedProvider(provider.toDataModel())
    }

    override suspend fun setApiKey(provider: AIProviderType, apiKey: String) = withContext(Dispatchers.IO) {
        apiKeyProvider.setApiKey(provider.toDataModel(), apiKey)
    }

    override suspend fun removeApiKey(provider: AIProviderType) = withContext(Dispatchers.IO) {
        apiKeyProvider.removeApiKey(provider.toDataModel())
    }

    override suspend fun testApiConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        aiAnalysisRepository.testApiConnection().map { }
    }

    override suspend fun getModels(provider: AIProviderType): Result<List<AIModelInfo>> = withContext(Dispatchers.IO) {
        aiAnalysisRepository.listModels(provider.toDataModel()).map { models ->
            models.toDomainModels()
        }
    }

    override suspend fun setSelectedModel(provider: AIProviderType, modelId: String) = withContext(Dispatchers.IO) {
        apiKeyProvider.setSelectedModel(provider.toDataModel(), modelId)
    }

    // ==================== Data Management ====================

    override suspend fun resetDatabase() = withContext(Dispatchers.IO) {
        dataRepository.resetDatabase()
    }

    override suspend fun trimDataToPeriod(days: Int): Int = withContext(Dispatchers.IO) {
        dataRepository.trimDataToPeriod(days)
    }
}
