package com.etfmonitor.feature.settings.domain.usecase

import com.etfmonitor.feature.settings.domain.model.AppThemeSettings
import com.etfmonitor.feature.settings.domain.repository.ChartType
import com.etfmonitor.feature.settings.domain.repository.ColorProperty
import com.etfmonitor.feature.settings.domain.repository.FontScaleType
import com.etfmonitor.feature.settings.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Theme Settings UseCases
 */

/**
 * Get app theme settings
 */
class GetAppThemeSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(): AppThemeSettings = repository.getAppThemeSettings()
}

/**
 * Set dark theme preference
 */
class SetDarkThemeUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(isDark: Boolean?) = repository.setDarkTheme(isDark)
}

/**
 * Set quick chart analysis enabled
 */
class SetQuickChartAnalysisEnabledUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(enabled: Boolean) = repository.setQuickChartAnalysisEnabled(enabled)
}

/**
 * Set font scale
 */
class SetFontScaleUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(type: FontScaleType, scale: Float) {
        require(scale in 0.5f..2.0f) { "Scale must be between 0.5 and 2.0" }
        repository.setFontScale(type, scale)
    }
}

/**
 * Set chart color
 */
class SetChartColorUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(chartType: ChartType, property: ColorProperty, color: Int?) {
        repository.setChartColor(chartType, property, color)
    }
}

/**
 * Reset all chart colors to default
 */
class ResetChartColorsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke() = repository.resetChartColors()
}

/**
 * Set display font scale
 */
class SetDisplayScaleUseCase @Inject constructor(
    private val setFontScaleUseCase: SetFontScaleUseCase
) {
    suspend operator fun invoke(scale: Float) {
        setFontScaleUseCase(FontScaleType.DISPLAY, scale)
    }
}

/**
 * Set headline font scale
 */
class SetHeadlineScaleUseCase @Inject constructor(
    private val setFontScaleUseCase: SetFontScaleUseCase
) {
    suspend operator fun invoke(scale: Float) {
        setFontScaleUseCase(FontScaleType.HEADLINE, scale)
    }
}

/**
 * Set title font scale
 */
class SetTitleScaleUseCase @Inject constructor(
    private val setFontScaleUseCase: SetFontScaleUseCase
) {
    suspend operator fun invoke(scale: Float) {
        setFontScaleUseCase(FontScaleType.TITLE, scale)
    }
}

/**
 * Set body font scale
 */
class SetBodyScaleUseCase @Inject constructor(
    private val setFontScaleUseCase: SetFontScaleUseCase
) {
    suspend operator fun invoke(scale: Float) {
        setFontScaleUseCase(FontScaleType.BODY, scale)
    }
}

/**
 * Set label font scale
 */
class SetLabelScaleUseCase @Inject constructor(
    private val setFontScaleUseCase: SetFontScaleUseCase
) {
    suspend operator fun invoke(scale: Float) {
        setFontScaleUseCase(FontScaleType.LABEL, scale)
    }
}
