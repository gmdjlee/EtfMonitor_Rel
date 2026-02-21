package com.etfmonitor.feature.etf.domain.usecase

import com.etfmonitor.MainDispatcherExtension
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
import kotlin.test.assertTrue

/**
 * GetAvailableDatesUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 기본 limit(100) 사용 검증
 * - invoke() 커스텀 limit 전달 검증
 * - 빈 결과 처리
 * - 날짜 목록 순서 (내림차순) 유지
 * - 예외 전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("GetAvailableDatesUseCase 테스트")
class GetAvailableDatesUseCaseTest {

    private val repository: EtfRepository = mockk()
    private lateinit var useCase: GetAvailableDatesUseCase

    @BeforeEach
    fun setUp() {
        useCase = GetAvailableDatesUseCase(repository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withDefaultLimit_usesLimit100")
        fun `invoke_withDefaultLimit_usesLimit100`() = runTest {
            // Given
            coEvery { repository.getAvailableDates(100) } returns listOf("2026-02-19", "2026-02-18")

            // When
            val result = useCase()  // limit 기본값 100

            // Then
            coVerify(exactly = 1) { repository.getAvailableDates(100) }
            assertEquals(2, result.size)
        }

        @Test
        @DisplayName("invoke_withCustomLimit_passesLimitToRepository")
        fun `invoke_withCustomLimit_passesLimitToRepository`() = runTest {
            // Given
            val customLimit = 30
            val dates = (1..30).map { "2026-01-${it.toString().padStart(2, '0')}" }.reversed()
            coEvery { repository.getAvailableDates(customLimit) } returns dates

            // When
            val result = useCase(customLimit)

            // Then
            coVerify(exactly = 1) { repository.getAvailableDates(customLimit) }
            assertEquals(30, result.size)
        }

        @Test
        @DisplayName("invoke_withEmptyDates_returnsEmptyList")
        fun `invoke_withEmptyDates_returnsEmptyList`() = runTest {
            // Given
            coEvery { repository.getAvailableDates(any()) } returns emptyList()

            // When
            val result = useCase()

            // Then
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("invoke_withDates_preservesDescendingOrder")
        fun `invoke_withDates_preservesDescendingOrder`() = runTest {
            // Given
            val dates = listOf("2026-02-19", "2026-02-18", "2026-02-17", "2026-02-14")
            coEvery { repository.getAvailableDates(any()) } returns dates

            // When
            val result = useCase()

            // Then
            assertEquals(dates, result)
            assertTrue(result.first() > result.last())
        }

        @Test
        @DisplayName("invoke_withLimit1_returnsSingleDate")
        fun `invoke_withLimit1_returnsSingleDate`() = runTest {
            // Given
            coEvery { repository.getAvailableDates(1) } returns listOf("2026-02-19")

            // When
            val result = useCase(1)

            // Then
            assertEquals(1, result.size)
            assertEquals("2026-02-19", result.first())
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
            coEvery { repository.getAvailableDates(any()) } throws RuntimeException("DB 조회 실패")

            // When & Then
            var caught: Exception? = null
            try {
                useCase()
            } catch (e: Exception) {
                caught = e
            }
            assertNotNull(caught)
            assertEquals("DB 조회 실패", caught?.message)
        }
    }
}
