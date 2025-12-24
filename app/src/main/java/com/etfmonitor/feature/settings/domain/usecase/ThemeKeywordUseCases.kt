package com.etfmonitor.feature.settings.domain.usecase

import com.etfmonitor.feature.settings.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Theme Keyword Management UseCases
 */

/**
 * Get current theme keywords
 */
class GetThemesUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(): List<String> = repository.getThemes()
}

/**
 * Add a new theme keyword
 */
class AddThemeUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(theme: String): Result<Unit> {
        return if (theme.isBlank()) {
            Result.failure(IllegalArgumentException("키워드를 입력하세요"))
        } else {
            repository.addTheme(theme)
            Result.success(Unit)
        }
    }
}

/**
 * Remove a theme keyword
 */
class RemoveThemeUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(theme: String) = repository.removeTheme(theme)
}

/**
 * Get current exclusion keywords
 */
class GetExclusionsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(): List<String> = repository.getExclusions()
}

/**
 * Add a new exclusion keyword
 */
class AddExclusionUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(keyword: String): Result<Unit> {
        return if (keyword.isBlank()) {
            Result.failure(IllegalArgumentException("키워드를 입력하세요"))
        } else {
            repository.addExclusion(keyword)
            Result.success(Unit)
        }
    }
}

/**
 * Remove an exclusion keyword
 */
class RemoveExclusionUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(keyword: String) = repository.removeExclusion(keyword)
}
