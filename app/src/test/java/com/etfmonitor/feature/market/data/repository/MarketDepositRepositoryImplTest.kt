package com.etfmonitor.feature.market.data.repository

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.MarketDepositDao
import com.etfmonitor.core.network.scraper.NaverFinanceScraper
import com.etfmonitor.feature.market.domain.model.MarketDepositData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import com.etfmonitor.core.database.entities.MarketDeposit as MarketDepositEntity
import com.etfmonitor.feature.market.domain.model.MarketDeposit as MarketDepositDomain

/**
 * MarketDepositRepositoryImpl 단위 테스트
 *
 * 테스트 범위:
 * - Flow 데이터 조회 (getAllDeposits, getRecentDeposits, getByDateRange)
 * - 단건 조회 (getDepositByDate, getDepositCount, getLastUpdateTime)
 * - initializeDeposits: 스크래퍼 성공/실패 경로
 * - getOrUpdateMarketData:
 *   - 신선한 캐시 (12시간 미만 + 오늘 날짜) → 스크래퍼 호출 안 함
 *   - 만료 캐시 (12시간 초과) → 스크래퍼 호출
 *   - 최신 날짜 != 오늘 → 스크래퍼 호출
 *   - 캐시 없음 → 스크래퍼 호출
 *   - 스크래퍼 실패 시 캐시 폴백
 *   - 캐시도 없고 스크래퍼도 실패 시 null 반환
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("MarketDepositRepositoryImpl 테스트")
class MarketDepositRepositoryImplTest {

    private lateinit var marketDepositDao: MarketDepositDao
    private lateinit var naverFinanceScraper: NaverFinanceScraper

    private lateinit var repository: MarketDepositRepositoryImpl

    /** 오늘 날짜 (yyyy-MM-dd) */
    private val today: String
        get() = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    /** 어제 날짜 (yyyy-MM-dd) */
    private val yesterday: String
        get() = java.time.LocalDate.now().minusDays(1)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    @BeforeEach
    fun setup() {
        marketDepositDao = mockk(relaxed = true)
        naverFinanceScraper = mockk(relaxed = true)

        repository = MarketDepositRepositoryImpl(
            marketDepositDao = marketDepositDao,
            naverFinanceScraper = naverFinanceScraper
        )
    }

    // ========== Flow 데이터 조회 ==========

    @Nested
    @DisplayName("Flow 데이터 조회 테스트")
    inner class FlowQueryTests {

        @Test
        @DisplayName("getAllDeposits()는 DAO Flow를 Domain List로 변환하여 반환한다")
        fun `getAllDeposits_delegatesToDao_returnsDomainList`() = runTest {
            // Given
            val entities = listOf(
                createTestDepositEntity("2025-01-15", lastUpdated = System.currentTimeMillis()),
                createTestDepositEntity("2025-01-14", lastUpdated = System.currentTimeMillis())
            )
            every { marketDepositDao.getAllDeposits() } returns flowOf(entities)

            // When & Then
            repository.getAllDeposits().test {
                val result = awaitItem()
                assertEquals(2, result.size)
                assertEquals("2025-01-15", result[0].date)
                assertEquals("2025-01-14", result[1].date)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getAllDeposits()는 빈 결과를 빈 리스트로 전달한다")
        fun `getAllDeposits_withEmptyData_returnsEmptyList`() = runTest {
            // Given
            every { marketDepositDao.getAllDeposits() } returns flowOf(emptyList())

            // When & Then
            repository.getAllDeposits().test {
                val result = awaitItem()
                assertTrue(result.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getRecentDeposits()는 limit 파라미터를 DAO에 전달하고 결과를 반환한다")
        fun `getRecentDeposits_withLimit_passesLimitToDao`() = runTest {
            // Given
            val limit = 30
            val entities = (1..limit).map { day ->
                createTestDepositEntity("2025-01-${day.toString().padStart(2, '0')}")
            }
            every { marketDepositDao.getRecentDeposits(limit) } returns flowOf(entities)

            // When & Then
            repository.getRecentDeposits(limit).test {
                val result = awaitItem()
                assertEquals(limit, result.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getByDateRange()는 날짜 범위를 DAO에 전달하고 결과를 반환한다")
        fun `getByDateRange_withDateRange_passesRangeToDao`() = runTest {
            // Given
            val start = "2025-01-01"
            val end = "2025-01-15"
            val entities = listOf(
                createTestDepositEntity("2025-01-15"),
                createTestDepositEntity("2025-01-10"),
                createTestDepositEntity("2025-01-01")
            )
            every { marketDepositDao.getByDateRange(start, end) } returns flowOf(entities)

            // When & Then
            repository.getByDateRange(start, end).test {
                val result = awaitItem()
                assertEquals(3, result.size)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // ========== 단건 조회 ==========

    @Nested
    @DisplayName("단건 조회 테스트")
    inner class SingleQueryTests {

        @Test
        @DisplayName("getDepositByDate()는 특정 날짜의 데이터를 Domain 모델로 반환한다")
        fun `getDepositByDate_withExistingDate_returnsDomainModel`() = runTest {
            // Given
            val date = "2025-01-15"
            val entity = createTestDepositEntity(date)
            coEvery { marketDepositDao.getDepositByDate(date) } returns entity

            // When
            val result = repository.getDepositByDate(date)

            // Then
            assertNotNull(result)
            assertEquals(date, result.date)
            assertEquals(entity.depositAmount, result.depositAmount)
        }

        @Test
        @DisplayName("getDepositByDate()는 데이터가 없을 때 null을 반환한다")
        fun `getDepositByDate_withNoData_returnsNull`() = runTest {
            // Given
            coEvery { marketDepositDao.getDepositByDate(any()) } returns null

            // When
            val result = repository.getDepositByDate("2025-01-01")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("getDepositCount()는 DAO에서 카운트를 반환한다")
        fun `getDepositCount_delegatesToDao_returnsCount`() = runTest {
            // Given
            coEvery { marketDepositDao.getCount() } returns 120

            // When
            val result = repository.getDepositCount()

            // Then
            assertEquals(120, result)
        }

        @Test
        @DisplayName("getLastUpdateTime()는 DAO에서 마지막 업데이트 시간을 반환한다")
        fun `getLastUpdateTime_delegatesToDao_returnsTimestamp`() = runTest {
            // Given
            val expectedTime = 1705300800000L
            coEvery { marketDepositDao.getLastUpdateTime() } returns expectedTime

            // When
            val result = repository.getLastUpdateTime()

            // Then
            assertEquals(expectedTime, result)
        }

        @Test
        @DisplayName("getLastUpdateTime()는 데이터가 없을 때 null을 반환한다")
        fun `getLastUpdateTime_withNoData_returnsNull`() = runTest {
            // Given
            coEvery { marketDepositDao.getLastUpdateTime() } returns null

            // When
            val result = repository.getLastUpdateTime()

            // Then
            assertNull(result)
        }
    }

    // ========== initializeDeposits ==========

    @Nested
    @DisplayName("initializeDeposits 테스트")
    inner class InitializeDepositsTests {

        @Test
        @DisplayName("스크래퍼가 데이터를 반환하면 DB에 저장하고 성공 Result를 반환한다")
        fun `initializeDeposits_scraperSucceeds_savesToDbAndReturnsSuccess`() = runTest {
            // Given
            val scraperData = createTestMarketDepositData(
                dates = listOf("2025-01-15", "2025-01-14", "2025-01-13")
            )
            coEvery { naverFinanceScraper.scrapeDepositData(any()) } returns scraperData

            // When
            val result = repository.initializeDeposits(numPages = 3)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(3, result.getOrNull())
            coVerify(exactly = 1) { marketDepositDao.deleteAll() }
            coVerify(exactly = 1) { marketDepositDao.insertAll(any()) }
        }

        @Test
        @DisplayName("스크래퍼가 null을 반환하면 실패 Result를 반환한다")
        fun `initializeDeposits_scraperReturnsNull_returnsFailure`() = runTest {
            // Given
            coEvery { naverFinanceScraper.scrapeDepositData(any()) } returns null

            // When
            val result = repository.initializeDeposits(numPages = 3)

            // Then
            assertTrue(result.isFailure)
            coVerify(exactly = 0) { marketDepositDao.deleteAll() }
        }

        @Test
        @DisplayName("스크래퍼가 예외를 던지면 실패 Result를 반환한다")
        fun `initializeDeposits_scraperThrowsException_returnsFailure`() = runTest {
            // Given
            coEvery { naverFinanceScraper.scrapeDepositData(any()) } throws RuntimeException("Connection timeout")

            // When
            val result = repository.initializeDeposits(numPages = 3)

            // Then
            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("스크래퍼가 빈 데이터를 반환하면 실패 Result를 반환한다")
        fun `initializeDeposits_scraperReturnsEmptyData_returnsFailure`() = runTest {
            // Given
            val emptyData = createTestMarketDepositData(dates = emptyList())
            coEvery { naverFinanceScraper.scrapeDepositData(any()) } returns emptyData

            // When
            val result = repository.initializeDeposits(numPages = 1)

            // Then
            assertTrue(result.isFailure)
            coVerify(exactly = 0) { marketDepositDao.deleteAll() }
        }
    }

    // ========== getOrUpdateMarketData 캐시 전략 ==========

    @Nested
    @DisplayName("getOrUpdateMarketData 캐시 TTL 테스트")
    inner class CacheTtlTests {

        @Test
        @DisplayName("신선한 캐시 (12시간 미만 + 오늘 날짜) → 스크래퍼 호출 안 함")
        fun `getOrUpdateMarketData_freshCacheWithToday_doesNotCallScraper`() = runTest {
            // Given: 1시간 전 업데이트된 오늘 날짜 데이터
            val freshDeposits = listOf(
                createTestDepositEntity(
                    date = today,
                    lastUpdated = System.currentTimeMillis() - (1 * 60 * 60 * 1000L) // 1시간 전
                )
            )
            every { marketDepositDao.getRecentDeposits(any()) } returns flowOf(freshDeposits)

            // When
            repository.getOrUpdateMarketData(limit = 30)

            // Then: 스크래퍼를 호출하지 않아야 한다
            coVerify(exactly = 0) { naverFinanceScraper.getLatestData() }
            coVerify(exactly = 0) { naverFinanceScraper.scrapeDepositData(any()) }
        }

        @Test
        @DisplayName("캐시가 12시간 초과 → 스크래퍼 호출하여 업데이트")
        fun `getOrUpdateMarketData_cacheOlderThan12h_callsScraper`() = runTest {
            // Given: 13시간 전 업데이트된 오늘 날짜 데이터
            val staleDeposits = listOf(
                createTestDepositEntity(
                    date = today,
                    lastUpdated = System.currentTimeMillis() - (13 * 60 * 60 * 1000L) // 13시간 전
                )
            )
            every { marketDepositDao.getRecentDeposits(any()) } returns flowOf(staleDeposits)
            coEvery { naverFinanceScraper.getLatestData() } returns null

            // When
            repository.getOrUpdateMarketData(limit = 30)

            // Then: 스크래퍼가 호출되어야 한다
            coVerify(atLeast = 1) { naverFinanceScraper.getLatestData() }
        }

        @Test
        @DisplayName("최신 날짜 != 오늘 → 스크래퍼 호출하여 업데이트")
        fun `getOrUpdateMarketData_latestDateIsNotToday_callsScraper`() = runTest {
            // Given: 최근 업데이트이지만 어제 날짜
            val staleDeposits = listOf(
                createTestDepositEntity(
                    date = yesterday,
                    lastUpdated = System.currentTimeMillis() - (1 * 60 * 60 * 1000L) // 1시간 전 (만료 미도달)
                )
            )
            every { marketDepositDao.getRecentDeposits(any()) } returns flowOf(staleDeposits)
            coEvery { naverFinanceScraper.getLatestData() } returns null

            // When
            repository.getOrUpdateMarketData(limit = 30)

            // Then: 날짜 불일치로 스크래퍼 호출
            coVerify(atLeast = 1) { naverFinanceScraper.getLatestData() }
        }

        @Test
        @DisplayName("캐시가 없으면 스크래퍼를 호출한다")
        fun `getOrUpdateMarketData_noCachedData_callsScraper`() = runTest {
            // Given: 빈 캐시
            every { marketDepositDao.getRecentDeposits(any()) } returns flowOf(emptyList())
            coEvery { naverFinanceScraper.getLatestData() } returns null

            // When
            repository.getOrUpdateMarketData(limit = 30)

            // Then
            coVerify(atLeast = 1) { naverFinanceScraper.getLatestData() }
        }

        @Test
        @DisplayName("정확히 12시간 전 → 만료 경계값으로 스크래퍼 호출")
        fun `getOrUpdateMarketData_cacheExactly12hOld_callsScraper`() = runTest {
            // Given: 정확히 12시간 전 (>= 조건이므로 만료)
            val staleDeposits = listOf(
                createTestDepositEntity(
                    date = today,
                    lastUpdated = System.currentTimeMillis() - (12 * 60 * 60 * 1000L) // 정확히 12시간 전
                )
            )
            every { marketDepositDao.getRecentDeposits(any()) } returns flowOf(staleDeposits)
            coEvery { naverFinanceScraper.getLatestData() } returns null

            // When
            repository.getOrUpdateMarketData(limit = 30)

            // Then: 12시간 만료 경계값 → 스크래퍼 호출
            coVerify(atLeast = 1) { naverFinanceScraper.getLatestData() }
        }
    }

    // ========== getOrUpdateMarketData 폴백 동작 ==========

    @Nested
    @DisplayName("getOrUpdateMarketData 폴백 동작 테스트")
    inner class FallbackTests {

        @Test
        @DisplayName("신선한 캐시 존재 시 캐시 데이터를 MarketDepositData로 변환하여 반환한다")
        fun `getOrUpdateMarketData_freshCache_returnsCachedDataAsMarketDepositData`() = runTest {
            // Given: 신선한 캐시 (오늘, 1시간 전 업데이트)
            val freshDeposits = listOf(
                createTestDepositEntity(
                    date = today,
                    depositAmount = 500_000.0,
                    depositChange = 5_000.0,
                    creditAmount = 20_000.0,
                    creditChange = -500.0,
                    lastUpdated = System.currentTimeMillis() - (1 * 60 * 60 * 1000L)
                )
            )
            every { marketDepositDao.getRecentDeposits(any()) } returns flowOf(freshDeposits)

            // When
            val result = repository.getOrUpdateMarketData(limit = 30)

            // Then
            assertNotNull(result)
            assertTrue(result.dates.contains(today))
            assertEquals(500_000.0, result.depositAmounts[result.dates.indexOf(today)])
        }

        @Test
        @DisplayName("만료 캐시 + 스크래퍼 성공 → 새 데이터를 저장하고 업데이트된 결과를 반환한다")
        fun `getOrUpdateMarketData_staleCache_scraperSucceeds_savesAndReturnsNewData`() = runTest {
            // Given: 만료된 캐시
            val staleDeposits = listOf(
                createTestDepositEntity(
                    date = yesterday,
                    lastUpdated = System.currentTimeMillis() - (13 * 60 * 60 * 1000L)
                )
            )
            // 스크래퍼 응답
            val scraperData = createTestMarketDepositData(dates = listOf(today, yesterday))
            // 업데이트 후 반환할 최신 캐시
            val updatedDeposits = listOf(
                createTestDepositEntity(today, depositAmount = 510_000.0, lastUpdated = System.currentTimeMillis()),
                createTestDepositEntity(yesterday, depositAmount = 500_000.0, lastUpdated = System.currentTimeMillis())
            )

            every { marketDepositDao.getRecentDeposits(any()) } returnsMany listOf(
                flowOf(staleDeposits),   // 첫 번째 호출: 만료된 캐시
                flowOf(updatedDeposits)  // 두 번째 호출: 업데이트 후
            )
            coEvery { naverFinanceScraper.getLatestData() } returns scraperData

            // When
            val result = repository.getOrUpdateMarketData(limit = 30)

            // Then: 새 데이터가 DB에 저장되어야 한다
            coVerify(atLeast = 1) { marketDepositDao.insertAll(any()) }
            assertNotNull(result)
        }

        @Test
        @DisplayName("만료 캐시 + 스크래퍼 실패 → 기존 캐시 데이터를 폴백으로 반환한다")
        fun `getOrUpdateMarketData_staleCache_scraperFails_returnsStaleCachedData`() = runTest {
            // Given: 만료된 캐시 + 스크래퍼 실패
            val staleDeposits = listOf(
                createTestDepositEntity(
                    date = yesterday,
                    depositAmount = 495_000.0,
                    lastUpdated = System.currentTimeMillis() - (13 * 60 * 60 * 1000L)
                )
            )
            every { marketDepositDao.getRecentDeposits(any()) } returns flowOf(staleDeposits)
            coEvery { naverFinanceScraper.getLatestData() } returns null

            // When
            val result = repository.getOrUpdateMarketData(limit = 30)

            // Then: 스크래퍼 실패 시 만료 캐시를 폴백으로 반환
            assertNotNull(result, "Stale cache should be returned as fallback when scraper fails")
            assertEquals(1, result.dates.size)
        }

        @Test
        @DisplayName("캐시도 없고 스크래퍼도 실패하면 null을 반환한다")
        fun `getOrUpdateMarketData_noCacheAndScraperFails_returnsNull`() = runTest {
            // Given: 빈 캐시 + 스크래퍼 실패
            every { marketDepositDao.getRecentDeposits(any()) } returns flowOf(emptyList())
            coEvery { naverFinanceScraper.getLatestData() } returns null

            // When
            val result = repository.getOrUpdateMarketData(limit = 30)

            // Then
            assertNull(result, "null should be returned when both cache and scraper fail")
        }

        @Test
        @DisplayName("스크래퍼 예외 발생 시 캐시가 있으면 캐시를 반환한다")
        fun `getOrUpdateMarketData_scraperThrowsException_returnsCachedData`() = runTest {
            // Given: 만료 캐시 + 스크래퍼 예외
            val staleDeposits = listOf(
                createTestDepositEntity(
                    date = yesterday,
                    depositAmount = 495_000.0,
                    lastUpdated = System.currentTimeMillis() - (13 * 60 * 60 * 1000L)
                )
            )
            every { marketDepositDao.getRecentDeposits(any()) } returns flowOf(staleDeposits)
            coEvery { naverFinanceScraper.getLatestData() } throws RuntimeException("Network error")

            // When
            val result = repository.getOrUpdateMarketData(limit = 30)

            // Then: 예외 발생 시 캐시 폴백
            assertNotNull(result, "Cached data should be returned as fallback on scraper exception")
        }

        @Test
        @DisplayName("결과 MarketDepositData는 날짜 오름차순으로 정렬된다")
        fun `getOrUpdateMarketData_freshCache_returnsDataSortedByDateAscending`() = runTest {
            // Given: 날짜가 내림차순인 캐시 (DAO가 DESC로 반환)
            val freshDeposits = listOf(
                createTestDepositEntity("2025-01-15", lastUpdated = System.currentTimeMillis() - 1000L),
                createTestDepositEntity("2025-01-14", lastUpdated = System.currentTimeMillis() - 1000L),
                createTestDepositEntity("2025-01-13", lastUpdated = System.currentTimeMillis() - 1000L)
            )
            // 오늘 날짜를 포함하도록 last deposit은 오늘로 설정
            val todayDeposits = listOf(
                createTestDepositEntity(today, lastUpdated = System.currentTimeMillis() - 1000L),
                createTestDepositEntity(yesterday, lastUpdated = System.currentTimeMillis() - 1000L)
            )
            every { marketDepositDao.getRecentDeposits(any()) } returns flowOf(todayDeposits)

            // When
            val result = repository.getOrUpdateMarketData(limit = 2)

            // Then: convertToMarketDepositData는 날짜 오름차순으로 정렬한다
            if (result != null && result.dates.size > 1) {
                for (i in 0 until result.dates.size - 1) {
                    assertTrue(
                        result.dates[i] <= result.dates[i + 1],
                        "Dates should be sorted ascending: ${result.dates}"
                    )
                }
            }
        }
    }

    // ========== Helper Functions ==========

    private fun createTestDepositEntity(
        date: String,
        depositAmount: Double = 500_000.0,
        depositChange: Double = 5_000.0,
        creditAmount: Double = 20_000.0,
        creditChange: Double = -500.0,
        lastUpdated: Long = System.currentTimeMillis()
    ): MarketDepositEntity = MarketDepositEntity(
        date = date,
        depositAmount = depositAmount,
        depositChange = depositChange,
        creditAmount = creditAmount,
        creditChange = creditChange,
        lastUpdated = lastUpdated
    )

    private fun createTestMarketDepositData(
        dates: List<String>,
        depositAmounts: List<Double>? = null,
        depositChanges: List<Double>? = null,
        creditAmounts: List<Double>? = null,
        creditChanges: List<Double>? = null
    ): MarketDepositData {
        val size = dates.size
        return MarketDepositData(
            dates = dates,
            depositAmounts = depositAmounts ?: List(size) { 500_000.0 },
            depositChanges = depositChanges ?: List(size) { 5_000.0 },
            creditAmounts = creditAmounts ?: List(size) { 20_000.0 },
            creditChanges = creditChanges ?: List(size) { -500.0 }
        )
    }
}
