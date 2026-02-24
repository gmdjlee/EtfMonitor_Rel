package com.etfmonitor.feature.analysis.data.repository

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.DailyEtfStatisticsDao
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.MarketIndexDao
import com.etfmonitor.core.database.entities.DailyEtfStatistics
import com.etfmonitor.core.database.entities.MarketIndex
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * StatisticsAnalysisRepositoryImpl 단위 테스트
 *
 * 테스트 범위:
 * - calculateAndStoreDailyStatistics: 성공 (2개 날짜 있음), 날짜 부족 → null, Exception → null
 * - getStatisticsByDate: DAO 위임
 * - getLatestDate: DAO 위임
 * - getAllDates: DAO 위임
 * - calculateCorrelation: 성공 (Pearson 계산), 데이터 부족 → null, 쌍 데이터 부족 → null, Exception → null
 * - CancellationException 재전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("StatisticsAnalysisRepositoryImpl 테스트")
class StatisticsAnalysisRepositoryImplTest {

    private lateinit var etfDao: EtfDao
    private lateinit var marketIndexDao: MarketIndexDao
    private lateinit var dailyEtfStatisticsDao: DailyEtfStatisticsDao
    private lateinit var repository: StatisticsAnalysisRepositoryImpl

    @BeforeEach
    fun setup() {
        etfDao = mockk(relaxed = true)
        marketIndexDao = mockk(relaxed = true)
        dailyEtfStatisticsDao = mockk(relaxed = true)

        repository = StatisticsAnalysisRepositoryImpl(
            etfDao = etfDao,
            marketIndexDao = marketIndexDao,
            dailyEtfStatisticsDao = dailyEtfStatisticsDao
        )
    }

    // ============================================================
    // calculateAndStoreDailyStatistics
    // ============================================================

    @Nested
    @DisplayName("calculateAndStoreDailyStatistics 테스트")
    inner class CalculateAndStoreDailyStatisticsTests {

        @Test
        @DisplayName("calculateAndStoreDailyStatistics_withTwoDates_computesAndSavesStatistics")
        fun `calculateAndStoreDailyStatistics_withTwoDates_computesAndSavesStatistics`() = runTest {
            // Given
            coEvery { etfDao.getLatestTwoDates() } returns listOf("2026-01-15", "2026-01-14")
            coEvery { etfDao.getAllNewStocks(any(), any(), any()) } returns emptyList()
            coEvery { etfDao.getAllRemovedStocks(any(), any(), any()) } returns emptyList()
            coEvery { etfDao.getAllIncreasedStocks(any(), any(), any()) } returns emptyList()
            coEvery { etfDao.getAllDecreasedStocks(any(), any(), any()) } returns emptyList()
            coEvery { etfDao.getCashDepositTrend(any()) } returns emptyList()
            coEvery { etfDao.getEtfCount() } returns 50
            coEvery { etfDao.getStockAmountRanking(any(), any(), any()) } returns emptyList()
            coEvery { dailyEtfStatisticsDao.insert(any()) } returns Unit

            // When
            val result = repository.calculateAndStoreDailyStatistics("2026-01-15")

            // Then
            assertNotNull(result)
            assertEquals("2026-01-15", result.date)
            coVerify(exactly = 1) { dailyEtfStatisticsDao.insert(any()) }
        }

        @Test
        @DisplayName("calculateAndStoreDailyStatistics_withInsufficientDates_returnsNull")
        fun `calculateAndStoreDailyStatistics_withInsufficientDates_returnsNull`() = runTest {
            // Given: only 1 date available — need 2
            coEvery { etfDao.getLatestTwoDates() } returns listOf("2026-01-15")

            // When
            val result = repository.calculateAndStoreDailyStatistics("2026-01-15")

            // Then: returns null, no DAO insert
            assertNull(result)
            coVerify(exactly = 0) { dailyEtfStatisticsDao.insert(any()) }
        }

        @Test
        @DisplayName("calculateAndStoreDailyStatistics_withNoDates_returnsNull")
        fun `calculateAndStoreDailyStatistics_withNoDates_returnsNull`() = runTest {
            // Given
            coEvery { etfDao.getLatestTwoDates() } returns emptyList()

            // When
            val result = repository.calculateAndStoreDailyStatistics("2026-01-15")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("calculateAndStoreDailyStatistics_whenDaoThrows_returnsNull")
        fun `calculateAndStoreDailyStatistics_whenDaoThrows_returnsNull`() = runTest {
            // Given
            coEvery { etfDao.getLatestTwoDates() } throws RuntimeException("DB connection error")

            // When
            val result = repository.calculateAndStoreDailyStatistics("2026-01-15")

            // Then: exception is swallowed, null returned
            assertNull(result)
        }

        @Test
        @DisplayName("calculateAndStoreDailyStatistics_usesCurrentDateFromDates_notParameter")
        fun `calculateAndStoreDailyStatistics_usesCurrentDateFromDates_notParameter`() = runTest {
            // Given: latest dates differ from parameter — repository uses dates[0]
            coEvery { etfDao.getLatestTwoDates() } returns listOf("2026-01-22", "2026-01-15")
            coEvery { etfDao.getAllNewStocks(any(), any(), any()) } returns emptyList()
            coEvery { etfDao.getAllRemovedStocks(any(), any(), any()) } returns emptyList()
            coEvery { etfDao.getAllIncreasedStocks(any(), any(), any()) } returns emptyList()
            coEvery { etfDao.getAllDecreasedStocks(any(), any(), any()) } returns emptyList()
            coEvery { etfDao.getCashDepositTrend(any()) } returns emptyList()
            coEvery { etfDao.getEtfCount() } returns 50
            coEvery { etfDao.getStockAmountRanking(any(), any(), any()) } returns emptyList()
            coEvery { dailyEtfStatisticsDao.insert(any()) } returns Unit

            // When
            val result = repository.calculateAndStoreDailyStatistics("2026-01-01")

            // Then: date in result comes from etfDao.getLatestTwoDates()[0]
            assertNotNull(result)
            assertEquals("2026-01-22", result.date)
        }
    }

    // ============================================================
    // getStatisticsByDate
    // ============================================================

    @Nested
    @DisplayName("getStatisticsByDate 테스트")
    inner class GetStatisticsByDateTests {

        @Test
        @DisplayName("getStatisticsByDate_withValidDate_returnsStatistics")
        fun `getStatisticsByDate_withValidDate_returnsStatistics`() = runTest {
            // Given
            val statistics = createTestDailyEtfStatistics("2026-01-15")
            coEvery { dailyEtfStatisticsDao.getByDate("2026-01-15") } returns statistics

            // When
            val result = repository.getStatisticsByDate("2026-01-15")

            // Then
            assertNotNull(result)
            assertEquals("2026-01-15", result.date)
        }

        @Test
        @DisplayName("getStatisticsByDate_withNoData_returnsNull")
        fun `getStatisticsByDate_withNoData_returnsNull`() = runTest {
            // Given
            coEvery { dailyEtfStatisticsDao.getByDate(any()) } returns null

            // When
            val result = repository.getStatisticsByDate("2026-01-01")

            // Then
            assertNull(result)
        }
    }

    // ============================================================
    // getLatestDate
    // ============================================================

    @Test
    @DisplayName("getLatestDate_delegatesToDao")
    fun `getLatestDate_delegatesToDao`() = runTest {
        // Given
        coEvery { dailyEtfStatisticsDao.getLatestDate() } returns "2026-01-15"

        // When
        val result = repository.getLatestDate()

        // Then
        assertEquals("2026-01-15", result)
    }

    @Test
    @DisplayName("getLatestDate_withNoData_returnsNull")
    fun `getLatestDate_withNoData_returnsNull`() = runTest {
        // Given
        coEvery { dailyEtfStatisticsDao.getLatestDate() } returns null

        // When
        val result = repository.getLatestDate()

        // Then
        assertNull(result)
    }

    // ============================================================
    // getAllDates
    // ============================================================

    @Test
    @DisplayName("getAllDates_delegatesToDao")
    fun `getAllDates_delegatesToDao`() = runTest {
        // Given
        val dates = listOf("2026-01-15", "2026-01-14", "2026-01-13")
        coEvery { dailyEtfStatisticsDao.getAllDates() } returns dates

        // When
        val result = repository.getAllDates()

        // Then
        assertEquals(3, result.size)
        assertEquals(dates, result)
    }

    // ============================================================
    // calculateCorrelation
    // ============================================================

    @Nested
    @DisplayName("calculateCorrelation 테스트")
    inner class CalculateCorrelationTests {

        @Test
        @DisplayName("calculateCorrelation_withEnoughData_returnsPearsonCorrelationData")
        fun `calculateCorrelation_withEnoughData_returnsPearsonCorrelationData`() = runTest {
            // Given: 15 paired data points (requirement is >= 10)
            val stats = (1..15).map { i ->
                createTestDailyEtfStatistics(
                    date = "2026-01-${i.toString().padStart(2, '0')}",
                    newStockCount = i
                )
            }
            val indices = (1..15).map { i ->
                createTestMarketIndex(
                    date = "2026-01-${i.toString().padStart(2, '0')}",
                    changeRate = i * 0.1
                )
            }
            coEvery { dailyEtfStatisticsDao.getByDateRangeSuspend(any(), any()) } returns stats
            coEvery { marketIndexDao.getByMarketAndDateRangeSuspend(any(), any(), any()) } returns indices

            // When
            val result = repository.calculateCorrelation("KOSPI", "2026-01-01", "2026-01-15")

            // Then
            assertNotNull(result)
            assertEquals("KOSPI", result.market)
            assertEquals(15, result.dataPoints)
            assertTrue(result.correlations.containsKey("newStock"))
            assertTrue(result.correlations.containsKey("removedStock"))
            assertTrue(result.correlations.containsKey("increasedStock"))
            assertTrue(result.correlations.containsKey("decreasedStock"))
            assertTrue(result.correlations.containsKey("cashDeposit"))
        }

        @Test
        @DisplayName("calculateCorrelation_withEmptyStatistics_returnsNull")
        fun `calculateCorrelation_withEmptyStatistics_returnsNull`() = runTest {
            // Given
            coEvery { dailyEtfStatisticsDao.getByDateRangeSuspend(any(), any()) } returns emptyList()
            coEvery { marketIndexDao.getByMarketAndDateRangeSuspend(any(), any(), any()) } returns listOf(
                createTestMarketIndex("2026-01-15", changeRate = 0.5)
            )

            // When
            val result = repository.calculateCorrelation("KOSPI", "2026-01-01", "2026-01-15")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("calculateCorrelation_withEmptyIndices_returnsNull")
        fun `calculateCorrelation_withEmptyIndices_returnsNull`() = runTest {
            // Given
            coEvery { dailyEtfStatisticsDao.getByDateRangeSuspend(any(), any()) } returns listOf(
                createTestDailyEtfStatistics("2026-01-15")
            )
            coEvery { marketIndexDao.getByMarketAndDateRangeSuspend(any(), any(), any()) } returns emptyList()

            // When
            val result = repository.calculateCorrelation("KOSPI", "2026-01-01", "2026-01-15")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("calculateCorrelation_withFewerThan10PairedPoints_returnsNull")
        fun `calculateCorrelation_withFewerThan10PairedPoints_returnsNull`() = runTest {
            // Given: 9 stats but only 3 matching index dates (fewer than 10 pairs)
            val stats = (1..9).map { i ->
                createTestDailyEtfStatistics("2026-01-${i.toString().padStart(2, '0')}")
            }
            // Only 3 indices with matching dates to stats
            val indices = listOf(
                createTestMarketIndex("2026-01-01", changeRate = 0.1),
                createTestMarketIndex("2026-01-02", changeRate = 0.2),
                createTestMarketIndex("2026-01-03", changeRate = 0.3)
            )
            coEvery { dailyEtfStatisticsDao.getByDateRangeSuspend(any(), any()) } returns stats
            coEvery { marketIndexDao.getByMarketAndDateRangeSuspend(any(), any(), any()) } returns indices

            // When
            val result = repository.calculateCorrelation("KOSPI", "2026-01-01", "2026-01-09")

            // Then: < 10 pairs, returns null
            assertNull(result)
        }

        @Test
        @DisplayName("calculateCorrelation_whenDaoThrows_returnsNull")
        fun `calculateCorrelation_whenDaoThrows_returnsNull`() = runTest {
            // Given
            coEvery { dailyEtfStatisticsDao.getByDateRangeSuspend(any(), any()) } throws RuntimeException("DB error")

            // When
            val result = repository.calculateCorrelation("KOSPI", "2026-01-01", "2026-01-15")

            // Then: exception swallowed, null returned
            assertNull(result)
        }

        @Test
        @DisplayName("calculateCorrelation_perfectPositiveCorrelation_returns1")
        fun `calculateCorrelation_perfectPositiveCorrelation_returns1`() = runTest {
            // Given: newStockCount and changeRate are perfectly correlated (1:1 linear)
            val stats = (1..15).map { i ->
                createTestDailyEtfStatistics(
                    date = "2026-01-${i.toString().padStart(2, '0')}",
                    newStockCount = i
                )
            }
            val indices = (1..15).map { i ->
                createTestMarketIndex(
                    date = "2026-01-${i.toString().padStart(2, '0')}",
                    changeRate = i.toDouble()  // Same linear trend
                )
            }
            coEvery { dailyEtfStatisticsDao.getByDateRangeSuspend(any(), any()) } returns stats
            coEvery { marketIndexDao.getByMarketAndDateRangeSuspend(any(), any(), any()) } returns indices

            // When
            val result = repository.calculateCorrelation("KOSPI", "2026-01-01", "2026-01-15")

            // Then: Pearson correlation should be ~1.0 for perfect positive linear relationship
            assertNotNull(result)
            val newStockCorrelation = result.correlations["newStock"] ?: 0.0
            assertTrue(newStockCorrelation > 0.99, "Expected correlation ~1.0 but was $newStockCorrelation")
        }
    }

    // ============================================================
    // CancellationException rethrow
    // ============================================================

    @Test
    @DisplayName("calculateAndStoreDailyStatistics_whenCancelled_rethrowsCancellationException")
    fun `calculateAndStoreDailyStatistics_whenCancelled_rethrowsCancellationException`() = runTest {
        // Given
        coEvery { etfDao.getLatestTwoDates() } throws CancellationException("Cancelled")

        // When & Then
        var exceptionCaught: Throwable? = null
        try {
            repository.calculateAndStoreDailyStatistics("2026-01-15")
        } catch (e: CancellationException) {
            exceptionCaught = e
        }
        assertNotNull(exceptionCaught, "CancellationException must be rethrown")
    }

    @Test
    @DisplayName("calculateCorrelation_whenCancelled_rethrowsCancellationException")
    fun `calculateCorrelation_whenCancelled_rethrowsCancellationException`() = runTest {
        // Given
        coEvery { dailyEtfStatisticsDao.getByDateRangeSuspend(any(), any()) } throws CancellationException("Cancelled")

        // When & Then
        var exceptionCaught: Throwable? = null
        try {
            repository.calculateCorrelation("KOSPI", "2026-01-01", "2026-01-15")
        } catch (e: CancellationException) {
            exceptionCaught = e
        }
        assertNotNull(exceptionCaught, "CancellationException must be rethrown")
    }

    // ============================================================
    // Helpers
    // ============================================================

    private fun createTestDailyEtfStatistics(
        date: String,
        newStockCount: Int = 5,
        removedStockCount: Int = 3,
        increasedStockCount: Int = 12,
        decreasedStockCount: Int = 8
    ): DailyEtfStatistics = DailyEtfStatistics(
        date = date,
        newStockCount = newStockCount,
        newStockAmount = newStockCount * 1_000_000L,
        removedStockCount = removedStockCount,
        removedStockAmount = removedStockCount * 500_000L,
        increasedStockCount = increasedStockCount,
        increasedStockAmount = increasedStockCount * 800_000L,
        decreasedStockCount = decreasedStockCount,
        decreasedStockAmount = decreasedStockCount * 600_000L,
        cashDepositAmount = 50_000_000_000L,
        cashDepositChange = 1_000_000_000L,
        cashDepositChangeRate = 2.0,
        totalEtfCount = 50,
        totalHoldingAmount = 200_000_000_000L
    )

    private fun createTestMarketIndex(
        date: String,
        changeRate: Double = 0.5
    ): MarketIndex = MarketIndex(
        id = "KOSPI-$date",
        market = "KOSPI",
        date = date,
        closePrice = 2_700.0,
        openPrice = 2_680.0,
        highPrice = 2_720.0,
        lowPrice = 2_660.0,
        volume = 500_000_000L,
        changeRate = changeRate
    )
}
