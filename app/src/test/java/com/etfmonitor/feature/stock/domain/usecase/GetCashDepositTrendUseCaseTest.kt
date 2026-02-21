package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.stock.domain.model.CashDepositTrend
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
import kotlin.test.assertTrue

/**
 * GetCashDepositTrendUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 원화예금 추이 목록 반환
 * - invoke() 빈 목록 처리
 * - 데이터 내용 (date, totalAmount, etfCount) 검증
 * - repository 정확히 1회 호출 검증
 * - 예외 전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("GetCashDepositTrendUseCase 테스트")
class GetCashDepositTrendUseCaseTest {

    private val repository: StockStatisticsRepository = mockk()
    private lateinit var useCase: GetCashDepositTrendUseCase

    @BeforeEach
    fun setUp() {
        useCase = GetCashDepositTrendUseCase(repository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withData_returnsTrendList")
        fun `invoke_withData_returnsTrendList`() = runTest {
            // Given
            val expected = listOf(
                CashDepositTrend(date = "2026-02-19", totalAmount = 1_500_000f, etfCount = 10),
                CashDepositTrend(date = "2026-02-18", totalAmount = 1_480_000f, etfCount = 10),
                CashDepositTrend(date = "2026-02-17", totalAmount = 1_460_000f, etfCount = 9)
            )
            coEvery { repository.getCashDepositTrend() } returns expected

            // When
            val result = useCase()

            // Then
            assertEquals(3, result.size)
            assertEquals("2026-02-19", result.first().date)
            assertEquals(1_500_000f, result.first().totalAmount)
            assertEquals(10, result.first().etfCount)
        }

        @Test
        @DisplayName("invoke_withEmptyData_returnsEmptyList")
        fun `invoke_withEmptyData_returnsEmptyList`() = runTest {
            // Given
            coEvery { repository.getCashDepositTrend() } returns emptyList()

            // When
            val result = useCase()

            // Then
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("invoke_delegatesExactlyOnce_toRepository")
        fun `invoke_delegatesExactlyOnce_toRepository`() = runTest {
            // Given
            coEvery { repository.getCashDepositTrend() } returns emptyList()

            // When
            useCase()

            // Then
            coVerify(exactly = 1) { repository.getCashDepositTrend() }
        }

        @Test
        @DisplayName("invoke_withLargeDataset_returnsAllItems")
        fun `invoke_withLargeDataset_returnsAllItems`() = runTest {
            // Given
            val largeList = (1..100).map { i ->
                CashDepositTrend(
                    date = "2026-01-${i.toString().padStart(2, '0')}",
                    totalAmount = (1_000_000 + i * 1000).toFloat(),
                    etfCount = 10
                )
            }
            coEvery { repository.getCashDepositTrend() } returns largeList

            // When
            val result = useCase()

            // Then
            assertEquals(100, result.size)
        }

        @Test
        @DisplayName("invoke_returnsDataWithCorrectFields")
        fun `invoke_returnsDataWithCorrectFields`() = runTest {
            // Given
            val trend = CashDepositTrend(
                date = "2026-02-19",
                totalAmount = 2_000_000f,
                etfCount = 15
            )
            coEvery { repository.getCashDepositTrend() } returns listOf(trend)

            // When
            val result = useCase()

            // Then
            val item = result.first()
            assertEquals("2026-02-19", item.date)
            assertEquals(2_000_000f, item.totalAmount)
            assertEquals(15, item.etfCount)
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
            coEvery { repository.getCashDepositTrend() } throws RuntimeException("예금 추이 조회 실패")

            // When & Then
            var caught: Exception? = null
            try {
                useCase()
            } catch (e: Exception) {
                caught = e
            }
            assertNotNull(caught)
            assertEquals("예금 추이 조회 실패", caught?.message)
        }
    }
}
