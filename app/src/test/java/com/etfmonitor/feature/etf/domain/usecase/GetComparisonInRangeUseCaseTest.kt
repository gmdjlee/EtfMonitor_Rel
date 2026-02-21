package com.etfmonitor.feature.etf.domain.usecase

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.etf.domain.model.ComparisonResult
import com.etfmonitor.feature.etf.domain.repository.EtfRepository
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

/**
 * GetComparisonInRangeUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 날짜 범위 내 비교 결과 반환
 * - invoke() 데이터 없을 때 null 반환
 * - etfTicker, startDate, endDate 파라미터 정확히 전달 검증
 * - 예외 전파
 * - 범위 경계 날짜 처리
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("GetComparisonInRangeUseCase 테스트")
class GetComparisonInRangeUseCaseTest {

    private val repository: EtfRepository = mockk()
    private lateinit var useCase: GetComparisonInRangeUseCase

    @BeforeEach
    fun setUp() {
        useCase = GetComparisonInRangeUseCase(repository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withValidParameters_returnsComparisonResult")
        fun `invoke_withValidParameters_returnsComparisonResult`() = runTest {
            // Given
            val etfTicker = "069500"
            val startDate = "2026-01-01"
            val endDate = "2026-02-19"
            val expected = ComparisonResult(
                etfTicker = etfTicker,
                currentDate = endDate,
                previousDate = startDate,
                items = emptyList(),
                collectionStartDate = startDate,
                collectionEndDate = endDate
            )
            coEvery { repository.getComparisonInRange(etfTicker, startDate, endDate) } returns expected

            // When
            val result = useCase(etfTicker, startDate, endDate)

            // Then
            assertNotNull(result)
            assertEquals(etfTicker, result.etfTicker)
            assertEquals(endDate, result.currentDate)
            assertEquals(startDate, result.previousDate)
        }

        @Test
        @DisplayName("invoke_whenNoDataInRange_returnsNull")
        fun `invoke_whenNoDataInRange_returnsNull`() = runTest {
            // Given
            coEvery { repository.getComparisonInRange(any(), any(), any()) } returns null

            // When
            val result = useCase("069500", "2026-01-01", "2026-01-31")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("invoke_delegatesAllParametersExactly_toRepository")
        fun `invoke_delegatesAllParametersExactly_toRepository`() = runTest {
            // Given
            val etfTicker = "114800"
            val startDate = "2025-12-01"
            val endDate = "2026-01-31"
            coEvery { repository.getComparisonInRange(etfTicker, startDate, endDate) } returns null

            // When
            useCase(etfTicker, startDate, endDate)

            // Then
            coVerify(exactly = 1) { repository.getComparisonInRange(etfTicker, startDate, endDate) }
        }

        @Test
        @DisplayName("invoke_withSameDateBoundary_delegatesCorrectly")
        fun `invoke_withSameDateBoundary_delegatesCorrectly`() = runTest {
            // Given
            val date = "2026-02-19"
            coEvery { repository.getComparisonInRange(any(), date, date) } returns null

            // When
            useCase("069500", date, date)

            // Then
            coVerify(exactly = 1) { repository.getComparisonInRange("069500", date, date) }
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
            coEvery { repository.getComparisonInRange(any(), any(), any()) } throws RuntimeException("범위 조회 오류")

            // When & Then
            var caught: Exception? = null
            try {
                useCase("069500", "2026-01-01", "2026-02-19")
            } catch (e: Exception) {
                caught = e
            }
            assertNotNull(caught)
            assertEquals("범위 조회 오류", caught?.message)
        }
    }
}
