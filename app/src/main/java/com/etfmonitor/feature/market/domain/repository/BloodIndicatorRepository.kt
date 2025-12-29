package com.etfmonitor.feature.market.domain.repository

import com.etfmonitor.feature.market.domain.model.BloodIndicator
import kotlinx.coroutines.flow.Flow

/**
 * Blood Indicator Repository Interface
 * US Treasury 기반 시장 건강도 지표 데이터 관리
 */
interface BloodIndicatorRepository {
    /**
     * Get all Blood Indicator data
     */
    fun getAll(): Flow<List<BloodIndicator>>

    /**
     * Get recent N days of data
     */
    fun getRecent(limit: Int = 365): Flow<List<BloodIndicator>>

    /**
     * Get data by date range
     */
    fun getByDateRange(startDate: String, endDate: String): Flow<List<BloodIndicator>>

    /**
     * Get data by specific date
     */
    suspend fun getByDate(date: String): BloodIndicator?

    /**
     * Get data count
     */
    suspend fun getCount(): Int

    /**
     * Get latest date in database
     */
    suspend fun getLatestDate(): String?

    /**
     * Get last update time
     */
    suspend fun getLastUpdateTime(): Long?

    /**
     * Initialize Blood Indicator data
     *
     * @param days Number of days to collect
     * @param onProgress Progress callback (message, percent)
     * @return Number of records saved
     */
    suspend fun initializeBloodIndicator(
        days: Int = 365,
        onProgress: ((String, Int) -> Unit)? = null
    ): Result<Int>

    /**
     * Update Blood Indicator data (recent data only)
     */
    suspend fun updateBloodIndicator(): Result<Int>

    /**
     * Check if first-run dialog was dismissed
     */
    suspend fun isDialogDismissed(): Boolean

    /**
     * Save dialog dismissed state
     */
    suspend fun saveDialogDismissed()
}
