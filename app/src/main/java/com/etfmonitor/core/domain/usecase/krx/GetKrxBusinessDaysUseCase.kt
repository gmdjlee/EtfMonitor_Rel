package com.etfmonitor.core.domain.usecase.krx

import com.etfmonitor.core.data.krx.adapter.DateAdapter
import com.krxkt.KrxIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

/**
 * UseCase for retrieving business days via kotlin_krx.
 *
 * PHASE A MIGRATION: Replaces PyKrxClient.getBusinessDays() in EtfRepositoryImpl.
 * Wraps KrxIndex.getBusinessDays() (maps from pykrx get_previous_business_days).
 *
 * This UseCase enables complete removal of PyKrxClient dependency, achieving
 * 100% pykrx migration (91.7% → 100%).
 *
 * @param days Number of days back from today to retrieve business days
 * @return Result<List<String>> List of business days in "yyyy-MM-dd" format (sorted, ascending)
 */
class GetKrxBusinessDaysUseCase @Inject constructor(
    private val krxIndex: KrxIndex
) {
    /**
     * Retrieves business days for the specified number of days back from today.
     *
     * Implementation:
     * 1. Calculate date range: end = today, start = today - days
     * 2. Convert LocalDate to KRX format ("yyyyMMdd") via DateAdapter
     * 3. Call krxIndex.getBusinessDays(startDate, endDate)
     * 4. Convert result back to "yyyy-MM-dd" format for compatibility
     *
     * @param days Number of days back from today (e.g., 730 for ~2 years)
     * @return Result wrapping List<String> of business days in "yyyy-MM-dd" format
     */
    suspend operator fun invoke(days: Int): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val end = LocalDate.now()
            val start = end.minusDays(days.toLong())

            // Call kotlin_krx with "yyyyMMdd" format
            val businessDays = krxIndex.getBusinessDays(
                DateAdapter.toKrxFormat(start),
                DateAdapter.toKrxFormat(end)
            )

            // Convert back to "yyyy-MM-dd" format for compatibility with existing code
            val formattedDays = businessDays.map { krxDate ->
                val year = krxDate.substring(0, 4)
                val month = krxDate.substring(4, 6)
                val day = krxDate.substring(6, 8)
                "$year-$month-$day"
            }

            Result.success(formattedDays)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
