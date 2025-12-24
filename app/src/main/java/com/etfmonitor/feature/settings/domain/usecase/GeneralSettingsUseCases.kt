package com.etfmonitor.feature.settings.domain.usecase

import com.etfmonitor.feature.settings.domain.model.GeneralSettings
import com.etfmonitor.feature.settings.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * General Settings UseCases
 */

/**
 * Get general settings
 */
class GetGeneralSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(): GeneralSettings = repository.getGeneralSettings()
}

/**
 * Set default collection days
 */
class SetDefaultDaysUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(days: Int) = repository.setDefaultDays(days)
}

/**
 * Set search history limit
 */
class SetSearchHistoryLimitUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(limit: Int) = repository.setSearchHistoryLimit(limit)
}

/**
 * Set Fear & Greed period days
 */
class SetFearGreedPeriodUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(days: Int) = repository.setFearGreedPeriodDays(days)
}

/**
 * Set Market Oscillator period days
 */
class SetMarketOscillatorPeriodUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(days: Int) = repository.setMarketOscillatorPeriodDays(days)
}

/**
 * Set Market Index period days
 */
class SetMarketIndexPeriodUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(days: Int) = repository.setMarketIndexPeriodDays(days)
}

/**
 * Reset database
 */
class ResetDatabaseUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke() = repository.resetDatabase()
}

/**
 * Trim data to period
 */
class TrimDataToPeriodUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(days: Int): Int = repository.trimDataToPeriod(days)
}
