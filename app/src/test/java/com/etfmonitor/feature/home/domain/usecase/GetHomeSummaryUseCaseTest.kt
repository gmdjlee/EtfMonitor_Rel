package com.etfmonitor.feature.home.domain.usecase

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.home.domain.model.HomeSummary
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
import kotlin.test.assertNull

/**
 * GetHomeSummaryUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() HomeSummary 데이터 반환
 * - invoke() 데이터 없을 때 null 반환
 * - HomeSummary 필드 값 검증 (Fear&Greed, Oscillator 등)
 * - repository 정확히 1회 호출 검증
 * - 예외 전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("GetHomeSummaryUseCase 테스트")
class GetHomeSummaryUseCaseTest {

    private val repository: HomeRepository = mockk()
    private lateinit var useCase: GetHomeSummaryUseCase

    @BeforeEach
    fun setUp() {
        useCase = GetHomeSummaryUseCase(repository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withFullSummary_returnsAllFields")
        fun `invoke_withFullSummary_returnsAllFields`() = runTest {
            // Given
            val expected = HomeSummary(
                depositChange = 1500.0,
                creditChange = -200.0,
                kospiFearGreed = 65.5,
                kosdaqFearGreed = 45.0,
                kospiOscillator = 0.75,
                kospiStatus = "과매수",
                kosdaqOscillator = 0.25,
                kosdaqStatus = "중립"
            )
            coEvery { repository.getHomeSummary() } returns expected

            // When
            val result = useCase()

            // Then
            assertNotNull(result)
            assertEquals(1500.0, result.depositChange)
            assertEquals(-200.0, result.creditChange)
            assertEquals(65.5, result.kospiFearGreed)
            assertEquals(45.0, result.kosdaqFearGreed)
            assertEquals(0.75, result.kospiOscillator)
            assertEquals("과매수", result.kospiStatus)
        }

        @Test
        @DisplayName("invoke_withNullSummary_returnsNull")
        fun `invoke_withNullSummary_returnsNull`() = runTest {
            // Given
            coEvery { repository.getHomeSummary() } returns null

            // When
            val result = useCase()

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("invoke_withNullFields_returnsHomeSummaryWithNullFields")
        fun `invoke_withNullFields_returnsHomeSummaryWithNullFields`() = runTest {
            // Given
            val expected = HomeSummary(
                depositChange = null,
                creditChange = null,
                kospiFearGreed = null,
                kosdaqFearGreed = null,
                kospiOscillator = null,
                kospiStatus = null,
                kosdaqOscillator = null,
                kosdaqStatus = null
            )
            coEvery { repository.getHomeSummary() } returns expected

            // When
            val result = useCase()

            // Then
            assertNotNull(result)
            assertNull(result.depositChange)
            assertNull(result.kospiFearGreed)
            assertNull(result.kospiStatus)
        }

        @Test
        @DisplayName("invoke_delegatesExactlyOnce_toRepository")
        fun `invoke_delegatesExactlyOnce_toRepository`() = runTest {
            // Given
            coEvery { repository.getHomeSummary() } returns null

            // When
            useCase()

            // Then
            coVerify(exactly = 1) { repository.getHomeSummary() }
        }

        @Test
        @DisplayName("invoke_withFearGreedBoundaryValues_returnsCorrectly")
        fun `invoke_withFearGreedBoundaryValues_returnsCorrectly`() = runTest {
            // Given: Fear & Greed 범위는 0~100
            val expected = HomeSummary(
                depositChange = 0.0,
                creditChange = 0.0,
                kospiFearGreed = 0.0,   // 최솟값
                kosdaqFearGreed = 100.0,  // 최댓값
                kospiOscillator = -1.0,
                kospiStatus = "과매도",
                kosdaqOscillator = 1.0,
                kosdaqStatus = "과매수"
            )
            coEvery { repository.getHomeSummary() } returns expected

            // When
            val result = useCase()

            // Then
            assertNotNull(result)
            assertEquals(0.0, result.kospiFearGreed)
            assertEquals(100.0, result.kosdaqFearGreed)
            assertEquals("과매도", result.kospiStatus)
            assertEquals("과매수", result.kosdaqStatus)
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
            coEvery { repository.getHomeSummary() } throws RuntimeException("요약 조회 실패")

            // When & Then
            var caught: Exception? = null
            try {
                useCase()
            } catch (e: Exception) {
                caught = e
            }
            assertNotNull(caught)
            assertEquals("요약 조회 실패", caught?.message)
        }
    }
}
