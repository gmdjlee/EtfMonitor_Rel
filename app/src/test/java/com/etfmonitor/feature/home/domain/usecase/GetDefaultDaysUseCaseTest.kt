package com.etfmonitor.feature.home.domain.usecase

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.home.domain.repository.HomeRepository
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

/**
 * GetDefaultDaysUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 기본 수집 일수 반환
 * - 다양한 일수 값 검증 (25일, 60일, 730일 등)
 * - repository 정확히 1회 호출 검증
 * - 예외 전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("GetDefaultDaysUseCase 테스트")
class GetDefaultDaysUseCaseTest {

    private val repository: HomeRepository = mockk()
    private lateinit var useCase: GetDefaultDaysUseCase

    @BeforeEach
    fun setUp() {
        useCase = GetDefaultDaysUseCase(repository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withDefault25Days_returns25")
        fun `invoke_withDefault25Days_returns25`() = runTest {
            // Given
            coEvery { repository.getDefaultDays() } returns 25

            // When
            val result = useCase()

            // Then
            assertEquals(25, result)
        }

        @Test
        @DisplayName("invoke_withCustomDays_returnsCorrectValue")
        fun `invoke_withCustomDays_returnsCorrectValue`() = runTest {
            // Given
            coEvery { repository.getDefaultDays() } returns 60

            // When
            val result = useCase()

            // Then
            assertEquals(60, result)
        }

        @Test
        @DisplayName("invoke_withMaxDays_returns365")
        fun `invoke_withMaxDays_returns365`() = runTest {
            // Given
            coEvery { repository.getDefaultDays() } returns 365

            // When
            val result = useCase()

            // Then
            assertEquals(365, result)
        }

        @Test
        @DisplayName("invoke_delegatesExactlyOnce_toRepository")
        fun `invoke_delegatesExactlyOnce_toRepository`() = runTest {
            // Given
            coEvery { repository.getDefaultDays() } returns 25

            // When
            useCase()

            // Then
            coVerify(exactly = 1) { repository.getDefaultDays() }
        }

        @Test
        @DisplayName("invoke_calledMultipleTimes_callsRepositoryEachTime")
        fun `invoke_calledMultipleTimes_callsRepositoryEachTime`() = runTest {
            // Given
            coEvery { repository.getDefaultDays() } returns 25

            // When
            useCase()
            useCase()
            useCase()

            // Then
            coVerify(exactly = 3) { repository.getDefaultDays() }
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
            coEvery { repository.getDefaultDays() } throws RuntimeException("설정 조회 실패")

            // When & Then
            var caught: Exception? = null
            try {
                useCase()
            } catch (e: Exception) {
                caught = e
            }
            assertNotNull(caught)
            assertEquals("설정 조회 실패", caught?.message)
        }
    }
}
