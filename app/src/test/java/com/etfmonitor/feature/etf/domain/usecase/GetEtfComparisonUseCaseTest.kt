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
 * GetEtfComparisonUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 비교 결과 존재 시 반환
 * - invoke() 데이터 없을 때 null 반환
 * - etfTicker 파라미터 정확히 전달 검증
 * - 예외 전파
 * - 다수 종목 비교 결과 반환
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("GetEtfComparisonUseCase 테스트")
class GetEtfComparisonUseCaseTest {

    private val repository: EtfRepository = mockk()
    private lateinit var useCase: GetEtfComparisonUseCase

    @BeforeEach
    fun setUp() {
        useCase = GetEtfComparisonUseCase(repository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withValidTicker_returnsComparisonResult")
        fun `invoke_withValidTicker_returnsComparisonResult`() = runTest {
            // Given
            val etfTicker = "069500"
            val expected = ComparisonResult(
                etfTicker = etfTicker,
                currentDate = "2026-02-19",
                previousDate = "2026-02-18",
                items = emptyList()
            )
            coEvery { repository.getComparison(etfTicker) } returns expected

            // When
            val result = useCase(etfTicker)

            // Then
            assertNotNull(result)
            assertEquals(etfTicker, result.etfTicker)
            assertEquals("2026-02-19", result.currentDate)
            assertEquals("2026-02-18", result.previousDate)
        }

        @Test
        @DisplayName("invoke_withNoData_returnsNull")
        fun `invoke_withNoData_returnsNull`() = runTest {
            // Given
            coEvery { repository.getComparison(any()) } returns null

            // When
            val result = useCase("069500")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("invoke_delegatesTickerExactly_toRepository")
        fun `invoke_delegatesTickerExactly_toRepository`() = runTest {
            // Given
            val etfTicker = "114800"
            coEvery { repository.getComparison(etfTicker) } returns null

            // When
            useCase(etfTicker)

            // Then
            coVerify(exactly = 1) { repository.getComparison(etfTicker) }
        }

        @Test
        @DisplayName("invoke_withDifferentTickers_callsRepositoryEachTime")
        fun `invoke_withDifferentTickers_callsRepositoryEachTime`() = runTest {
            // Given
            val ticker1 = "069500"
            val ticker2 = "114800"
            coEvery { repository.getComparison(ticker1) } returns null
            coEvery { repository.getComparison(ticker2) } returns null

            // When
            useCase(ticker1)
            useCase(ticker2)

            // Then
            coVerify(exactly = 1) { repository.getComparison(ticker1) }
            coVerify(exactly = 1) { repository.getComparison(ticker2) }
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
            coEvery { repository.getComparison(any()) } throws RuntimeException("네트워크 오류")

            // When & Then
            var caught: Exception? = null
            try {
                useCase("069500")
            } catch (e: Exception) {
                caught = e
            }
            assertNotNull(caught)
            assertEquals("네트워크 오류", caught?.message)
        }
    }
}
