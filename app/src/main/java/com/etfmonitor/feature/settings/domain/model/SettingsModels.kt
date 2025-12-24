package com.etfmonitor.feature.settings.domain.model

/**
 * Settings Domain Models
 *
 * Clean Architecture domain models for Settings feature.
 * These models represent the business logic layer and are UI-agnostic.
 */

// ==================== Basic Settings ====================

/**
 * Theme keywords for ETF filtering
 */
data class ThemeSettings(
    val themes: List<String>,
    val exclusions: List<String>
)

/**
 * General application settings
 */
data class GeneralSettings(
    val defaultDays: Int,
    val searchHistoryLimit: Int,
    val fearGreedPeriodDays: Int,
    val marketOscillatorPeriodDays: Int,
    val marketIndexPeriodDays: Int
)

// ==================== Update Schedule Settings ====================

/**
 * Common update settings structure
 */
data class UpdateSchedule(
    val hour: Int,
    val minute: Int
)

/**
 * All update schedules in the app
 */
data class AllUpdateSchedules(
    val etf: UpdateSchedule,
    val stock: UpdateSchedule,
    val marketDeposit: UpdateSchedule,
    val fearGreed: UpdateSchedule,
    val marketOscillator: UpdateSchedule,
    val marketIndex: UpdateSchedule,
    val advancedAnalysis: UpdateSchedule
)

/**
 * Data statistics for a single update type
 */
data class UpdateDataInfo(
    val count: Int,
    val lastUpdateTime: Long?,
    val isUpdating: Boolean = false
)

/**
 * Market-specific data info (KOSPI/KOSDAQ)
 */
data class MarketDataInfo(
    val kospiCount: Int,
    val kosdaqCount: Int,
    val lastUpdateTime: Long?,
    val isUpdating: Boolean = false
)

/**
 * ETF-specific data info
 */
data class EtfDataInfo(
    val etfCount: Int,
    val holdingCount: Int,
    val lastUpdateTime: Long?,
    val isUpdating: Boolean = false
)

// ==================== Theme Settings ====================

/**
 * Application theme configuration
 */
data class AppThemeSettings(
    val isDarkTheme: Boolean?,
    val quickChartAnalysisEnabled: Boolean,
    val fontScales: FontScales,
    val chartColors: ChartColors
)

/**
 * Font scale settings for different text styles
 */
data class FontScales(
    val displayScale: Float = 1.0f,
    val headlineScale: Float = 1.0f,
    val titleScale: Float = 1.0f,
    val bodyScale: Float = 1.0f,
    val labelScale: Float = 1.0f
)

/**
 * Chart color configuration
 */
data class ChartColors(
    val marketCapOscillator: SingleChartColors,
    val macd: MacdChartColors,
    val marketDeposit: SingleChartColors,
    val fearGreed: SingleChartColors
)

/**
 * Colors for a single chart type
 */
data class SingleChartColors(
    val lineColor1: Int,
    val lineColor2: Int,
    val textColor: Int,
    val legendColor: Int
)

/**
 * Extended colors for MACD chart (includes positive/negative)
 */
data class MacdChartColors(
    val lineColor1: Int,
    val lineColor2: Int,
    val positiveColor: Int,
    val negativeColor: Int,
    val textColor: Int,
    val legendColor: Int
)

// ==================== AI Settings ====================

/**
 * AI provider configuration
 */
data class AIConfiguration(
    val selectedProvider: AIProviderType,
    val claudeConfigured: Boolean,
    val geminiConfigured: Boolean,
    val claudeModel: String?,
    val geminiModel: String?
)

/**
 * AI provider type
 */
enum class AIProviderType {
    CLAUDE,
    GEMINI;

    fun toDisplayName(): String = when (this) {
        CLAUDE -> "Claude"
        GEMINI -> "Gemini"
    }
}

/**
 * AI model information
 */
data class AIModelInfo(
    val id: String,
    val name: String,
    val provider: AIProviderType,
    val contextWindow: Int,
    val maxOutputTokens: Int
)

/**
 * API connection test result
 */
sealed class ApiTestResult {
    object Idle : ApiTestResult()
    object Testing : ApiTestResult()
    object Success : ApiTestResult()
    data class Error(val message: String) : ApiTestResult()
}

// ==================== Combined Settings State ====================

/**
 * Complete settings state for UI consumption
 */
data class SettingsState(
    val themeSettings: ThemeSettings,
    val generalSettings: GeneralSettings,
    val appThemeSettings: AppThemeSettings,
    val aiConfiguration: AIConfiguration,
    val message: String? = null
)
