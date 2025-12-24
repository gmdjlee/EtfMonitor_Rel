package com.etfmonitor.feature.settings.data.mapper

import com.etfmonitor.core.network.ai.AIModel
import com.etfmonitor.core.network.ai.AIProvider
import com.etfmonitor.core.ui.theme.ChartColorSettings
import com.etfmonitor.core.ui.theme.FontScaleSettings
import com.etfmonitor.core.ui.theme.SingleChartColorSettings
import com.etfmonitor.feature.settings.domain.model.*

/**
 * Settings Mapper
 *
 * Converts between data layer models and domain models.
 */
object SettingsMapper {

    // ==================== Font Scale Mapping ====================

    fun FontScaleSettings.toDomain(): FontScales = FontScales(
        displayScale = displayScale,
        headlineScale = headlineScale,
        titleScale = titleScale,
        bodyScale = bodyScale,
        labelScale = labelScale
    )

    fun FontScales.toDataModel(): FontScaleSettings = FontScaleSettings(
        displayScale = displayScale,
        headlineScale = headlineScale,
        titleScale = titleScale,
        bodyScale = bodyScale,
        labelScale = labelScale
    )

    // ==================== Chart Colors Mapping ====================

    fun ChartColorSettings.toDomain(): ChartColors = ChartColors(
        marketCapOscillator = marketCapOscillator.toDomain(),
        macd = macd.toMacdDomain(),
        marketDeposit = marketDeposit.toDomain(),
        fearGreed = fearGreed.toDomain()
    )

    private fun SingleChartColorSettings.toDomain(): SingleChartColors = SingleChartColors(
        lineColor1 = lineColor1,
        lineColor2 = lineColor2,
        textColor = textColor,
        legendColor = legendColor
    )

    private fun SingleChartColorSettings.toMacdDomain(): MacdChartColors = MacdChartColors(
        lineColor1 = lineColor1,
        lineColor2 = lineColor2,
        positiveColor = positiveColor,
        negativeColor = negativeColor,
        textColor = textColor,
        legendColor = legendColor
    )

    // ==================== AI Provider Mapping ====================

    fun AIProvider.toDomain(): AIProviderType = when (this) {
        AIProvider.CLAUDE -> AIProviderType.CLAUDE
        AIProvider.GEMINI -> AIProviderType.GEMINI
    }

    fun AIProviderType.toDataModel(): AIProvider = when (this) {
        AIProviderType.CLAUDE -> AIProvider.CLAUDE
        AIProviderType.GEMINI -> AIProvider.GEMINI
    }

    // ==================== AI Model Mapping ====================

    fun AIModel.toDomain(): AIModelInfo = AIModelInfo(
        id = id,
        name = name,
        provider = provider.toDomain(),
        contextWindow = contextWindow,
        maxOutputTokens = maxOutputTokens
    )

    fun List<AIModel>.toDomainModels(): List<AIModelInfo> = map { it.toDomain() }
}
