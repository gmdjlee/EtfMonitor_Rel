package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.stock.domain.repository.StockStatisticsRepository
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
 * GetStatisticsDatesUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() (최신일, 전일) 쌍 반환
 * - invoke() 데이터 없을 때 null 반환
 * - 날짜 쌍의 순서 (최신일 first, 전일 second) 검증
 * - repository 정확히 1회 호출 검증
 * - 예외 전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("GetStatisticsDatesUseCase 테스트")
class GetStatisticsDatesUseCaseTest {

    private val repository: StockStatisticsRepository = mockk()
    private lateinit var useCase: GetStatisticsDatesUseCase

    @BeforeEach
    fun setUp() {
        useCase = GetStatisticsDatesUseCase(repository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withData_returnsLatestAndPreviousDatePair")
        fun `invoke_withData_returnsLatestAndPreviousDatePair`() = runTest {
            // Given
            val latestDate = "2026-02-19"
            val previousDate = "2026-02-18"
            coEvery { repository.getStatisticsDates() } returns (latestDate to previousDate)

            // When
            val result = useCase()

            // Then
            assertNotNull(result)
            assertEquals(latestDate, result.first)
            assertEquals(previousDate, result.second)
        }

        @Test
        @DisplayName("invoke_withNoData_returnsNull")
        fun `invoke_withNoData_returnsNull`() = runTest {
            // Given
            coEvery { repository.getStatisticsDates() } returns null

            // When
            val result = useCase()

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("invoke_delegatesExactlyOnce_toRepository")
        fun `invoke_delegatesExactlyOnce_toRepository`() = runTest {
            // Given
            coEvery { repository.getStatisticsDates() } returns null

            // When
            useCase()

            // Then
            coVerify(exactly = 1) { repository.getStatisticsDates() }
        }

        @Test
        @DisplayName("invoke_withWeekBoundaryDates_returnsPairCorrectly")
        fun `invoke_withWeekBoundaryDates_returnsPairCorrectly`() = runTest {
            // Given: 주 경계 — 월요일과 금요일
            val latestDate = "2026-02-16"  // 월요일
            val previousDate = "2026-02-13"  // 금요일
            coEvery { repository.getStatisticsDates() } returns (latestDate to previousDate)

            // When
            val result = useCase()

            // Then
            assertNotNull(result)
            assertEquals("2026-02-16", result.first)
            assertEquals("2026-02-13", result.second)
            assertTrue(result.first > result.second)
        }

        @Test
        @DisplayName("invoke_withSameDate_returnsPairWithEqualDates")
        fun `invoke_withSameDate_returnsPairWithEqualDates`() = runTest {
            // Given: 동일 날짜 (단일 데이터 시나리오)
            val date = "2026-02-19"
            coEvery { repository.getStatisticsDates() } returns (date to date)

            // When
            val result = useCase()

            // Then
            assertNotNull(result)
            assertEquals(date, result.first)
            assertEquals(date, result.second)
        }
    }

    // ================================================================
    // 실패 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("실패 경로 테스트")
    inner class FailurePathTests {

        @Test
        @DisplayName("invoke_whenRepositoryThrows_propagatesException")
        fun `invoke_whenRepositoryThrows_propagatesException`() = runTest {
            // Given
            coEvery { repository.getStatisticsDates() } throws RuntimeException("날짜 조회 실패")

            // When & Then
            var caught: Exception? = null
            try {
                useCase()
            } catch (e: Exception) {
                caught = e
            }
            assertNotNull(caught)
            assertEquals("날짜 조회 실패", caught?.message)
        }
    }
}
