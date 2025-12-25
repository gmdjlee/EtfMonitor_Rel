package com.etfmonitor.core.common.util

import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.SnapshotTypeCount
import com.etfmonitor.core.database.entities.Holding
import com.etfmonitor.core.database.entities.SnapshotType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 데이터 아카이빙 관리자
 *
 * 데이터 보관 정책:
 * - 최근 1년: 일별 전체 데이터 (DAILY)
 * - 1~3년: 주별 스냅샷 (WEEKLY) - 매주 금요일 데이터만 유지
 * - 3~5년: 월별 스냅샷 (MONTHLY) - 매월 마지막 거래일 데이터만 유지
 * - 5년 이상: 삭제
 */
@Singleton
class DataArchiver @Inject constructor(
    private val dao: EtfDao
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    companion object {
        private val logger = AppLogger.getLogger("DataArchiver")

        // 보관 기간 정의
        private const val DAILY_RETENTION_YEARS = 1
        private const val WEEKLY_RETENTION_YEARS = 3
        private const val MONTHLY_RETENTION_YEARS = 5
    }

    /**
     * 전체 아카이빙 프로세스 실행
     * 1. 5년 이상 데이터 삭제
     * 2. 3~5년 데이터를 월별 스냅샷으로 압축
     * 3. 1~3년 데이터를 주별 스냅샷으로 압축
     */
    suspend fun archiveData(): ArchiveResult = withContext(Dispatchers.IO) {
        try {
            logger.d("Starting data archiving process...")

            val today = LocalDate.now()
            val result = ArchiveResult()

            // 1. 5년 이상 오래된 데이터 삭제
            val fiveYearsAgo = today.minusYears(MONTHLY_RETENTION_YEARS.toLong())
            val deletedCount = deleteOldData(fiveYearsAgo)
            result.deletedRecords = deletedCount
            logger.d("Deleted $deletedCount records older than ${fiveYearsAgo.format(dateFormatter)}")

            // 2. 3~5년 데이터를 월별 스냅샷으로 압축
            val threeYearsAgo = today.minusYears(WEEKLY_RETENTION_YEARS.toLong())
            val monthlyCompressed = compressToMonthlySnapshots(threeYearsAgo, fiveYearsAgo)
            result.monthlyCompressed = monthlyCompressed
            logger.d("Compressed $monthlyCompressed records to monthly snapshots (3-5 years)")

            // 3. 1~3년 데이터를 주별 스냅샷으로 압축
            val oneYearAgo = today.minusYears(DAILY_RETENTION_YEARS.toLong())
            val weeklyCompressed = compressToWeeklySnapshots(oneYearAgo, threeYearsAgo)
            result.weeklyCompressed = weeklyCompressed
            logger.d("Compressed $weeklyCompressed records to weekly snapshots (1-3 years)")

            // 4. 통계 수집
            result.totalRecords = dao.getTotalHoldingCount()
            result.snapshotCounts = dao.getSnapshotTypeCounts()

            logger.d("Archiving completed successfully: $result")
            result.success = true
            result

        } catch (e: Exception) {
            logger.e("Error during archiving", e)
            ArchiveResult(success = false, error = e.message)
        }
    }

    /**
     * 5년 이상 오래된 데이터 삭제
     */
    private suspend fun deleteOldData(beforeDate: LocalDate): Int {
        val beforeDateStr = beforeDate.format(dateFormatter)
        val countBefore = dao.getTotalHoldingCount()
        dao.deleteHoldingsBeforeDate(beforeDateStr)
        val countAfter = dao.getTotalHoldingCount()
        return (countBefore - countAfter).toInt()
    }

    /**
     * 3~5년 데이터를 월별 스냅샷으로 압축
     * 각 월의 마지막 거래일 데이터만 유지
     */
    private suspend fun compressToMonthlySnapshots(
        startDate: LocalDate,
        endDate: LocalDate
    ): Int {
        var compressedCount = 0
        var currentDate = startDate

        while (currentDate.isBefore(endDate)) {
            val monthEnd = currentDate.with(TemporalAdjusters.lastDayOfMonth())
            val monthStart = currentDate.with(TemporalAdjusters.firstDayOfMonth())

            // 해당 월의 모든 데이터 조회
            val holdings = dao.getHoldingsByDateRange(
                monthStart.format(dateFormatter),
                monthEnd.format(dateFormatter)
            )

            if (holdings.isNotEmpty()) {
                // 가장 최근 날짜 찾기 (실제 마지막 거래일)
                val lastTradingDate = holdings.maxOf { it.date }

                // 마지막 날짜가 아닌 모든 데이터 삭제
                val toDelete = holdings.filter { it.date != lastTradingDate }
                if (toDelete.isNotEmpty()) {
                    // 날짜별로 삭제
                    toDelete.map { it.date }.distinct().forEach { date ->
                        dao.deleteHoldingsByDateRange(date, date)
                    }
                    compressedCount += toDelete.size
                }

                // 남은 데이터의 snapshotType을 MONTHLY로 업데이트
                val monthlyHoldings = holdings
                    .filter { it.date == lastTradingDate }
                    .map { it.copy(snapshotType = SnapshotType.MONTHLY.value) }
                dao.insertHoldings(monthlyHoldings)
            }

            currentDate = currentDate.plusMonths(1)
        }

        return compressedCount
    }

    /**
     * 1~3년 데이터를 주별 스냅샷으로 압축
     * 각 주의 금요일 데이터만 유지 (금요일이 없으면 가장 최근 거래일)
     */
    private suspend fun compressToWeeklySnapshots(
        startDate: LocalDate,
        endDate: LocalDate
    ): Int {
        var compressedCount = 0
        var currentDate = startDate

        while (currentDate.isBefore(endDate)) {
            // 해당 주의 월요일과 일요일 계산
            val weekStart = currentDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weekEnd = currentDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

            // 해당 주의 모든 데이터 조회
            val holdings = dao.getHoldingsByDateRange(
                weekStart.format(dateFormatter),
                weekEnd.format(dateFormatter)
            )

            if (holdings.isNotEmpty()) {
                // 금요일 데이터 찾기 (없으면 가장 최근 거래일)
                val friday = weekStart.plusDays(4).format(dateFormatter)
                val targetDate = holdings
                    .map { it.date }
                    .distinct()
                    .sortedDescending()
                    .firstOrNull { it <= friday }
                    ?: holdings.maxOf { it.date }

                // 선택된 날짜가 아닌 모든 데이터 삭제
                val toDelete = holdings.filter { it.date != targetDate }
                if (toDelete.isNotEmpty()) {
                    toDelete.map { it.date }.distinct().forEach { date ->
                        dao.deleteHoldingsByDateRange(date, date)
                    }
                    compressedCount += toDelete.size
                }

                // 남은 데이터의 snapshotType을 WEEKLY로 업데이트
                val weeklyHoldings = holdings
                    .filter { it.date == targetDate }
                    .map { it.copy(snapshotType = SnapshotType.WEEKLY.value) }
                dao.insertHoldings(weeklyHoldings)
            }

            currentDate = currentDate.plusWeeks(1)
        }

        return compressedCount
    }

    /**
     * 아카이빙 통계 조회
     */
    suspend fun getArchiveStats(): ArchiveStats = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val oneYearAgo = today.minusYears(1)
        val threeYearsAgo = today.minusYears(3)
        val fiveYearsAgo = today.minusYears(5)

        ArchiveStats(
            totalRecords = dao.getTotalHoldingCount(),
            dailyRecords = dao.getHoldingCountByDateRange(
                oneYearAgo.format(dateFormatter),
                today.format(dateFormatter)
            ),
            weeklyRecords = dao.getHoldingCountByDateRange(
                threeYearsAgo.format(dateFormatter),
                oneYearAgo.format(dateFormatter)
            ),
            monthlyRecords = dao.getHoldingCountByDateRange(
                fiveYearsAgo.format(dateFormatter),
                threeYearsAgo.format(dateFormatter)
            ),
            snapshotCounts = dao.getSnapshotTypeCounts()
        )
    }
}

/**
 * 아카이빙 결과
 */
data class ArchiveResult(
    var success: Boolean = false,
    var deletedRecords: Int = 0,
    var weeklyCompressed: Int = 0,
    var monthlyCompressed: Int = 0,
    var totalRecords: Long = 0,
    var snapshotCounts: List<SnapshotTypeCount> = emptyList(),
    var error: String? = null
)

/**
 * 아카이빙 통계
 */
data class ArchiveStats(
    val totalRecords: Long,
    val dailyRecords: Long,
    val weeklyRecords: Long,
    val monthlyRecords: Long,
    val snapshotCounts: List<SnapshotTypeCount>
)
