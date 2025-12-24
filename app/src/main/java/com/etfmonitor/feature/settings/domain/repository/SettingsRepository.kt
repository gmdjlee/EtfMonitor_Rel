package com.etfmonitor.feature.settings.domain.repository

import com.etfmonitor.feature.settings.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Settings Repository Interface
 *
 * Defines the contract for settings data access.
 * Implementation is provided in the data layer.
 */
interface SettingsRepository {

    // ==================== Theme Keywords ====================

    /**
     * Get current theme keywords
     */
    suspend fun getThemes(): List<String>

    /**
     * Add a new theme keyword
     */
    suspend fun addTheme(theme: String)

    /**
     * Remove a theme keyword
     */
    suspend fun removeTheme(theme: String)

    /**
     * Get current exclusion keywords
     */
    suspend fun getExclusions(): List<String>

    /**
     * Add a new exclusion keyword
     */
    suspend fun addExclusion(keyword: String)

    /**
     * Remove an exclusion keyword
     */
    suspend fun removeExclusion(keyword: String)

    // ==================== General Settings ====================

    /**
     * Get general settings
     */
    suspend fun getGeneralSettings(): GeneralSettings

    /**
     * Set default collection days
     */
    suspend fun setDefaultDays(days: Int)

    /**
     * Set search history limit
     */
    suspend fun setSearchHistoryLimit(limit: Int)

    /**
     * Set Fear & Greed period days
     */
    suspend fun setFearGreedPeriodDays(days: Int)

    /**
     * Set Market Oscillator period days
     */
    suspend fun setMarketOscillatorPeriodDays(days: Int)

    /**
     * Set Market Index period days
     */
    suspend fun setMarketIndexPeriodDays(days: Int)

    // ==================== Update Schedules ====================

    /**
     * Get all update schedules
     */
    suspend fun getUpdateSchedules(): AllUpdateSchedules

    /**
     * Set update schedule for a specific type
     */
    suspend fun setUpdateSchedule(type: UpdateType, hour: Int, minute: Int)

    // ==================== Theme Settings ====================

    /**
     * Get app theme settings
     */
    suspend fun getAppThemeSettings(): AppThemeSettings

    /**
     * Set dark theme preference
     */
    suspend fun setDarkTheme(isDark: Boolean?)

    /**
     * Set quick chart analysis enabled
     */
    suspend fun setQuickChartAnalysisEnabled(enabled: Boolean)

    /**
     * Set font scale for a specific text style
     */
    suspend fun setFontScale(type: FontScaleType, scale: Float)

    /**
     * Set chart color
     */
    suspend fun setChartColor(chartType: ChartType, property: ColorProperty, color: Int?)

    /**
     * Reset all chart colors to default
     */
    suspend fun resetChartColors()

    // ==================== AI Settings ====================

    /**
     * Get AI configuration
     */
    suspend fun getAIConfiguration(): AIConfiguration

    /**
     * Set selected AI provider
     */
    suspend fun setSelectedProvider(provider: AIProviderType)

    /**
     * Set API key for a provider
     */
    suspend fun setApiKey(provider: AIProviderType, apiKey: String)

    /**
     * Remove API key for a provider
     */
    suspend fun removeApiKey(provider: AIProviderType)

    /**
     * Test API connection
     */
    suspend fun testApiConnection(): Result<Unit>

    /**
     * Get available models for a provider
     */
    suspend fun getModels(provider: AIProviderType): Result<List<AIModelInfo>>

    /**
     * Set selected model for a provider
     */
    suspend fun setSelectedModel(provider: AIProviderType, modelId: String)

    // ==================== Data Management ====================

    /**
     * Reset database
     */
    suspend fun resetDatabase()

    /**
     * Trim data to period
     */
    suspend fun trimDataToPeriod(days: Int): Int
}

/**
 * Update type enumeration
 */
enum class UpdateType {
    ETF,
    STOCK,
    MARKET_DEPOSIT,
    FEAR_GREED,
    MARKET_OSCILLATOR,
    MARKET_INDEX,
    ADVANCED_ANALYSIS
}

/**
 * Font scale type enumeration
 */
enum class FontScaleType {
    DISPLAY,
    HEADLINE,
    TITLE,
    BODY,
    LABEL
}

/**
 * Chart type enumeration
 */
enum class ChartType {
    MARKET_CAP,
    MACD,
    DEPOSIT,
    FEAR_GREED
}

/**
 * Color property enumeration
 */
enum class ColorProperty {
    LINE1,
    LINE2,
    TEXT,
    LEGEND,
    POSITIVE,
    NEGATIVE
}
