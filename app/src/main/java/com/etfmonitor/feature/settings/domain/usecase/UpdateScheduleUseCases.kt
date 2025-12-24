package com.etfmonitor.feature.settings.domain.usecase

import com.etfmonitor.feature.settings.domain.model.AllUpdateSchedules
import com.etfmonitor.feature.settings.domain.repository.SettingsRepository
import com.etfmonitor.feature.settings.domain.repository.UpdateType
import javax.inject.Inject

/**
 * Update Schedule UseCases
 */

/**
 * Get all update schedules
 */
class GetUpdateSchedulesUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(): AllUpdateSchedules = repository.getUpdateSchedules()
}

/**
 * Set update schedule for a specific type
 */
class SetUpdateScheduleUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(type: UpdateType, hour: Int, minute: Int) {
        require(hour in 0..23) { "Hour must be between 0 and 23" }
        require(minute in 0..59) { "Minute must be between 0 and 59" }
        repository.setUpdateSchedule(type, hour, minute)
    }
}

/**
 * Set ETF update schedule
 */
class SetEtfUpdateScheduleUseCase @Inject constructor(
    private val setScheduleUseCase: SetUpdateScheduleUseCase
) {
    suspend operator fun invoke(hour: Int, minute: Int) {
        setScheduleUseCase(UpdateType.ETF, hour, minute)
    }
}

/**
 * Set Stock update schedule
 */
class SetStockUpdateScheduleUseCase @Inject constructor(
    private val setScheduleUseCase: SetUpdateScheduleUseCase
) {
    suspend operator fun invoke(hour: Int, minute: Int) {
        setScheduleUseCase(UpdateType.STOCK, hour, minute)
    }
}

/**
 * Set Market Deposit update schedule
 */
class SetMarketDepositUpdateScheduleUseCase @Inject constructor(
    private val setScheduleUseCase: SetUpdateScheduleUseCase
) {
    suspend operator fun invoke(hour: Int, minute: Int) {
        setScheduleUseCase(UpdateType.MARKET_DEPOSIT, hour, minute)
    }
}

/**
 * Set Fear & Greed update schedule
 */
class SetFearGreedUpdateScheduleUseCase @Inject constructor(
    private val setScheduleUseCase: SetUpdateScheduleUseCase
) {
    suspend operator fun invoke(hour: Int, minute: Int) {
        setScheduleUseCase(UpdateType.FEAR_GREED, hour, minute)
    }
}

/**
 * Set Market Oscillator update schedule
 */
class SetMarketOscillatorUpdateScheduleUseCase @Inject constructor(
    private val setScheduleUseCase: SetUpdateScheduleUseCase
) {
    suspend operator fun invoke(hour: Int, minute: Int) {
        setScheduleUseCase(UpdateType.MARKET_OSCILLATOR, hour, minute)
    }
}

/**
 * Set Market Index update schedule
 */
class SetMarketIndexUpdateScheduleUseCase @Inject constructor(
    private val setScheduleUseCase: SetUpdateScheduleUseCase
) {
    suspend operator fun invoke(hour: Int, minute: Int) {
        setScheduleUseCase(UpdateType.MARKET_INDEX, hour, minute)
    }
}

/**
 * Set Advanced Analysis update schedule
 */
class SetAdvancedAnalysisUpdateScheduleUseCase @Inject constructor(
    private val setScheduleUseCase: SetUpdateScheduleUseCase
) {
    suspend operator fun invoke(hour: Int, minute: Int) {
        setScheduleUseCase(UpdateType.ADVANCED_ANALYSIS, hour, minute)
    }
}
