package com.etfmonitor.feature.stock.data.repository

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.entities.CashDepositTrend
import com.etfmonitor.core.database.entities.StockAggregatedTimePoint
import com.etfmonitor.core.database.entities.StockAmountRanking
import com.etfmonitor.core.database.entities.StockChangeInfo
import com.etfmonitor.feature.stock.data.datasource.StockStatisticsLocalDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
 * StockStatisticsRepositoryImpl 단위 테스트
 *
 * 테스트 범위:
 * - getStatisticsDates: 2개 날짜 → Pair 반환, 1개 이하 → null, normalizeDateFormat 최초 1회 호출
 * - getAvailableDates: LocalDataSource 위임
 * - getStatisticsDatesInRange: 범위 내 날짜 처리 (2개 이상, 정확히 1개, 0개)
 * - getStockAmountRanking: getStatisticsDates → null 시 empty
 * - getAllNewStocks / getAllRemovedStocks / getAllIncreasedStocks / getAllDecreasedStocks: 위임
 * - searchStocks: LocalDataSource 위임
 * - getCashDepositTrend: LocalDataSource 위임
 * - getStockAggregatedTrend: 시계열 있음 → StockAggregatedTrend, 없음 → null
 * - ensureDateFormatNormalized 단일 실행 보장
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("StockStatisticsRepositoryImpl 테스트")
class StockStatisticsRepositoryImplTest {

    private lateinit var localDataSource: StockStatisticsLocalDataSource
    private lateinit var etfDao: EtfDao
    private lateinit var repository: StockStatisticsRepositoryImpl

    @BeforeEach
    fun setup() {
        localDataSource = mockk(relaxed = true)
        etfDao = mockk(relaxed = true)
        repository = StockStatisticsRepositoryImpl(localDataSource, etfDao)
    }

    // ============================================================
    // getStatisticsDates
    // ============================================================

    @Nested
    @DisplayName("getStatisticsDates 테스트")
    inner class GetStatisticsDatesTests {

        @Test
        @DisplayName("getStatisticsDates_withTwoDates_returnsPair")
        fun `getStatisticsDates_withTwoDates_returnsPair`() = runTest {
            // Given
            coEvery { localDataSource.getLatestTwoDates() } returns listOf("2026-01-15", "2026-01-14")

            // When
            val result = repository.getStatisticsDates()

            // Then
            assertNotNull(result)
            assertEquals("2026-01-15", result.first)
            assertEquals("2026-01-14", result.second)
        }

        @Test
        @DisplayName("getStatisticsDates_withOnlyOneDate_returnsNull")
        fun `getStatisticsDates_withOnlyOneDate_returnsNull`() = runTest {
            // Given: only 1 date — need 2 for current/previous comparison
            coEvery { localDataSource.getLatestTwoDates() } returns listOf("2026-01-15")

            // When
            val result = repository.getStatisticsDates()

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("getStatisticsDates_withNoDates_returnsNull")
        fun `getStatisticsDates_withNoDates_returnsNull`() = runTest {
            // Given
            coEvery { localDataSource.getLatestTwoDates() } returns emptyList()

            // When
            val result = repository.getStatisticsDates()

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("getStatisticsDates_callsNormalizeDateFormatOnFirstCall")
        fun `getStatisticsDates_callsNormalizeDateFormatOnFirstCall`() = runTest {
            // Given
            coEvery { localDataSource.getLatestTwoDates() } returns listOf("2026-01-15", "2026-01-14")

            // When: first call
            repository.getStatisticsDates()

            // Then: normalizeDateFormat was called exactly once (Critical Rule #10)
            coVerify(exactly = 1) { etfDao.normalizeDateFormat() }
        }

        @Test
        @DisplayName("getStatisticsDates_normalizeDateFormatCalledOnlyOnce_afterMultipleCalls")
        fun `getStatisticsDates_normalizeDateFormatCalledOnlyOnce_afterMultipleCalls`() = runTest {
            // Given
            coEvery { localDataSource.getLatestTwoDates() } returns listOf("2026-01-15", "2026-01-14")

            // When: multiple calls to getStatisticsDates
            repository.getStatisticsDates()
            repository.getStatisticsDates()
            repository.getStatisticsDates()

            // Then: normalizeDateFormat called only once (volatile flag prevents repeat)
            coVerify(exactly = 1) { etfDao.normalizeDateFormat() }
        }
    }

    // ============================================================
    // getAvailableDates
    // ============================================================

    @Test
    @DisplayName("getAvailableDates_delegatesToLocalDataSource")
    fun `getAvailableDates_delegatesToLocalDataSource`() = runTest {
        // Given
        val dates = listOf("2026-01-15", "2026-01-14", "2026-01-13")
        coEvery { localDataSource.getAllDistinctDates(100) } returns dates

        // When
        val result = repository.getAvailableDates(100)

        // Then
        assertEquals(3, result.size)
        assertEquals(dates, result)
    }

    // ============================================================
    // getStatisticsDatesInRange
    // ============================================================

    @Nested
    @DisplayName("getStatisticsDatesInRange 테스트")
    inner class GetStatisticsDatesInRangeTests {

        @Test
        @DisplayName("getStatisticsDatesInRange_withMultipleDatesInRange_returnsFirstAndLast")
        fun `getStatisticsDatesInRange_withMultipleDatesInRange_returnsFirstAndLast`() = runTest {
            // Given: dates returned in descending order
            coEvery { localDataSource.getAllDistinctDates(500) } returns listOf(
                "2026-01-15", "2026-01-14", "2026-01-13", "2026-01-12", "2026-01-11"
            )

            // When: range covers middle 3 dates
            val result = repository.getStatisticsDatesInRange("2026-01-12", "2026-01-14")

            // Then: first (latest in range) and last (oldest in range)
            assertNotNull(result)
            assertEquals("2026-01-14", result.first)
            assertEquals("2026-01-12", result.second)
        }

        @Test
        @DisplayName("getStatisticsDatesInRange_withExactlyOneDate_returnsSameDateForBoth")
        fun `getStatisticsDatesInRange_withExactlyOneDate_returnsSameDateForBoth`() = runTest {
            // Given: only 1 date in the range
            coEvery { localDataSource.getAllDistinctDates(500) } returns listOf("2026-01-15")

            // When
            val result = repository.getStatisticsDatesInRange("2026-01-10", "2026-01-20")

            // Then: same date used for both current and previous
            assertNotNull(result)
            assertEquals("2026-01-15", result.first)
            assertEquals("2026-01-15", result.second)
        }

        @Test
        @DisplayName("getStatisticsDatesInRange_withNoDatesInRange_returnsNull")
        fun `getStatisticsDatesInRange_withNoDatesInRange_returnsNull`() = runTest {
            // Given: no dates in specified range
            coEvery { localDataSource.getAllDistinctDates(500) } returns listOf(
                "2025-12-31", "2025-12-30"
            )

            // When: range is in 2026, but all dates are 2025
            val result = repository.getStatisticsDatesInRange("2026-01-01", "2026-01-31")

            // Then
            assertNull(result)
        }
    }

    // ============================================================
    // getStockAmountRanking
    // ============================================================

    @Nested
    @DisplayName("getStockAmountRanking 테스트")
    inner class GetStockAmountRankingTests {

        @Test
        @DisplayName("getStockAmountRanking_withValidDates_returnsRankings")
        fun `getStockAmountRanking_withValidDates_returnsRankings`() = runTest {
            // Given
            coEvery { localDataSource.getLatestTwoDates() } returns listOf("2026-01-15", "2026-01-14")
            val rankings = listOf(
                createTestStockAmountRanking("005930", "삼성전자", 5_000_000_000f),
                createTestStockAmountRanking("000660", "SK하이닉스", 3_000_000_000f)
            )
            coEvery { localDataSource.getStockAmountRanking("2026-01-15", "2026-01-14", any()) } returns rankings

            // When
            val result = repository.getStockAmountRanking()

            // Then
            assertEquals(2, result.size)
            assertEquals("005930", result[0].stockTicker)
        }

        @Test
        @DisplayName("getStockAmountRanking_whenNoDates_returnsEmpty")
        fun `getStockAmountRanking_whenNoDates_returnsEmpty`() = runTest {
            // Given: no date data available
            coEvery { localDataSource.getLatestTwoDates() } returns emptyList()

            // When
            val result = repository.getStockAmountRanking()

            // Then: returns empty (not null)
            assertTrue(result.isEmpty())
        }
    }

    // ============================================================
    // Stock Change methods
    // ============================================================

    @Test
    @DisplayName("getAllNewStocks_withValidDates_delegatesToLocalDataSource")
    fun `getAllNewStocks_withValidDates_delegatesToLocalDataSource`() = runTest {
        // Given
        coEvery { localDataSource.getLatestTwoDates() } returns listOf("2026-01-15", "2026-01-14")
        val newStocks = listOf(createTestStockChangeInfo("005930", "삼성전자"))
        coEvery { localDataSource.getAllNewStocks("2026-01-15", "2026-01-14", any()) } returns newStocks

        // When
        val result = repository.getAllNewStocks()

        // Then
        assertEquals(1, result.size)
        assertEquals("005930", result[0].stockTicker)
    }

    @Test
    @DisplayName("getAllRemovedStocks_withValidDates_delegatesToLocalDataSource")
    fun `getAllRemovedStocks_withValidDates_delegatesToLocalDataSource`() = runTest {
        // Given
        coEvery { localDataSource.getLatestTwoDates() } returns listOf("2026-01-15", "2026-01-14")
        val removedStocks = listOf(createTestStockChangeInfo("035420", "NAVER"))
        coEvery { localDataSource.getAllRemovedStocks("2026-01-15", "2026-01-14", any()) } returns removedStocks

        // When
        val result = repository.getAllRemovedStocks()

        // Then
        assertEquals(1, result.size)
        assertEquals("035420", result[0].stockTicker)
    }

    @Test
    @DisplayName("getAllIncreasedStocks_whenNoDates_returnsEmpty")
    fun `getAllIncreasedStocks_whenNoDates_returnsEmpty`() = runTest {
        // Given
        coEvery { localDataSource.getLatestTwoDates() } returns listOf("2026-01-15") // only 1 date

        // When
        val result = repository.getAllIncreasedStocks()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("getAllDecreasedStocks_withValidDates_delegatesToLocalDataSource")
    fun `getAllDecreasedStocks_withValidDates_delegatesToLocalDataSource`() = runTest {
        // Given
        coEvery { localDataSource.getLatestTwoDates() } returns listOf("2026-01-15", "2026-01-14")
        coEvery { localDataSource.getAllDecreasedStocks("2026-01-15", "2026-01-14", any()) } returns emptyList()

        // When
        val result = repository.getAllDecreasedStocks()

        // Then
        assertTrue(result.isEmpty())
    }

    // ============================================================
    // searchStocks
    // ============================================================

    @Test
    @DisplayName("searchStocks_withQuery_delegatesToLocalDataSource")
    fun `searchStocks_withQuery_delegatesToLocalDataSource`() = runTest {
        // Given
        val searchResults = listOf(
            com.etfmonitor.core.database.StockSearchResult(stockTicker = "005930", stockName = "삼성전자")
        )
        coEvery { localDataSource.searchStocks("삼성", any()) } returns searchResults

        // When
        val result = repository.searchStocks("삼성")

        // Then
        assertEquals(1, result.size)
        assertEquals("005930", result[0].stockTicker)
    }

    @Test
    @DisplayName("searchStocks_withNoMatch_returnsEmpty")
    fun `searchStocks_withNoMatch_returnsEmpty`() = runTest {
        // Given
        coEvery { localDataSource.searchStocks(any(), any()) } returns emptyList()

        // When
        val result = repository.searchStocks("존재하지않는종목")

        // Then
        assertTrue(result.isEmpty())
    }

    // ============================================================
    // getCashDepositTrend
    // ============================================================

    @Test
    @DisplayName("getCashDepositTrend_delegatesToLocalDataSource")
    fun `getCashDepositTrend_delegatesToLocalDataSource`() = runTest {
        // Given
        val trend = listOf(
            CashDepositTrend(date = "2026-01-15", totalAmount = 50_000_000_000f, etfCount = 30),
            CashDepositTrend(date = "2026-01-14", totalAmount = 49_000_000_000f, etfCount = 30)
        )
        coEvery { localDataSource.getCashDepositTrend(any()) } returns trend

        // When
        val result = repository.getCashDepositTrend()

        // Then
        assertEquals(2, result.size)
    }

    // ============================================================
    // getStockAggregatedTrend
    // ============================================================

    @Nested
    @DisplayName("getStockAggregatedTrend 테스트")
    inner class GetStockAggregatedTrendTests {

        @Test
        @DisplayName("getStockAggregatedTrend_withTimeSeries_returnsAggregatedTrend")
        fun `getStockAggregatedTrend_withTimeSeries_returnsAggregatedTrend`() = runTest {
            // Given
            val timeSeries = listOf(
                StockAggregatedTimePoint(
                    date = "2026-01-15",
                    totalAmount = 5_000_000_000f,
                    etfCount = 20,
                    maxWeight = 30.5f,
                    avgWeight = 15.2f
                ),
                StockAggregatedTimePoint(
                    date = "2026-01-22",
                    totalAmount = 5_200_000_000f,
                    etfCount = 22,
                    maxWeight = 31.0f,
                    avgWeight = 15.8f
                )
            )
            coEvery { localDataSource.getStockAggregatedTrend("005930", any()) } returns timeSeries
            coEvery { localDataSource.getStockName("005930") } returns "삼성전자"

            // When
            val result = repository.getStockAggregatedTrend("005930")

            // Then
            assertNotNull(result)
            assertEquals("005930", result.stockTicker)
            assertEquals("삼성전자", result.stockName)
            assertEquals(2, result.timeSeries.size)
            assertEquals("2026-01-15", result.timeSeries[0].date)
        }

        @Test
        @DisplayName("getStockAggregatedTrend_withEmptyTimeSeries_returnsNull")
        fun `getStockAggregatedTrend_withEmptyTimeSeries_returnsNull`() = runTest {
            // Given
            coEvery { localDataSource.getStockAggregatedTrend(any(), any()) } returns emptyList()

            // When
            val result = repository.getStockAggregatedTrend("005930")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("getStockAggregatedTrend_whenStockNameNotFound_usesTickerAsFallback")
        fun `getStockAggregatedTrend_whenStockNameNotFound_usesTickerAsFallback`() = runTest {
            // Given
            val timeSeries = listOf(
                StockAggregatedTimePoint(
                    date = "2026-01-15",
                    totalAmount = 5_000_000_000f,
                    etfCount = 20,
                    maxWeight = 30.5f,
                    avgWeight = 15.2f
                )
            )
            coEvery { localDataSource.getStockAggregatedTrend("999999", any()) } returns timeSeries
            coEvery { localDataSource.getStockName("999999") } returns null  // not found

            // When
            val result = repository.getStockAggregatedTrend("999999")

            // Then: ticker used as fallback stockName
            assertNotNull(result)
            assertEquals("999999", result.stockName)
        }
    }

    // ============================================================
    // getStockAmountRankingInRange / other In-Range variants
    // ============================================================

    @Test
    @DisplayName("getStockAmountRankingInRange_passesDatesToDatasource")
    fun `getStockAmountRankingInRange_passesDatesToDatasource`() = runTest {
        // Given
        val rankings = listOf(createTestStockAmountRanking("005930", "삼성전자", 1_000_000f))
        coEvery { localDataSource.getStockAmountRanking("2026-01-22", "2026-01-15", any()) } returns rankings

        // When
        val result = repository.getStockAmountRankingInRange("2026-01-22", "2026-01-15")

        // Then
        assertEquals(1, result.size)
    }

    @Test
    @DisplayName("getAllNewStocksInRange_passesDatesToDatasource")
    fun `getAllNewStocksInRange_passesDatesToDatasource`() = runTest {
        // Given
        val newStocks = listOf(createTestStockChangeInfo("000660", "SK하이닉스"))
        coEvery { localDataSource.getAllNewStocks("2026-01-22", "2026-01-15", any()) } returns newStocks

        // When
        val result = repository.getAllNewStocksInRange("2026-01-22", "2026-01-15")

        // Then
        assertEquals(1, result.size)
        assertEquals("000660", result[0].stockTicker)
    }

    // ============================================================
    // Helpers
    // ============================================================

    private fun createTestStockAmountRanking(
        ticker: String,
        name: String,
        totalAmount: Float
    ): StockAmountRanking = StockAmountRanking(
        stockTicker = ticker,
        stockName = name,
        totalAmount = totalAmount,
        etfCount = 5,
        maxWeight = 30.5f,
        etfList = "069500,229200"
    )

    private fun createTestStockChangeInfo(
        ticker: String,
        name: String
    ): StockChangeInfo = StockChangeInfo(
        stockTicker = ticker,
        stockName = name,
        etfTicker = "069500",
        etfName = "KODEX 200",
        previousWeight = 0f,
        currentWeight = 5.5f,
        change = 5.5f,
        currentAmount = 500_000_000f
    )
}
