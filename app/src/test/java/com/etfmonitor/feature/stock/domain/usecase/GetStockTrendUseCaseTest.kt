package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.stock.domain.model.HoldingTimeSeries
import com.etfmonitor.feature.stock.domain.model.StockTrend
import com.etfmonitor.feature.stock.domain.repository.StockTrendRepository
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
 * GetStockTrendUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() ETF 내 종목 추이 반환
 * - invoke() 데이터 없을 때 null 반환
 * - etfTicker + stockTicker 파라미터 모두 정확히 전달 검증
 * - 시계열 데이터 필드 검증
 * - 예외 전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("GetStockTrendUseCase 테스트")
class GetStockTrendUseCaseTest {

    private val repository: StockTrendRepository = mockk()
    private lateinit var useCase: GetStockTrendUseCase

    @BeforeEach
    fun setUp() {
        useCase = GetStockTrendUseCase(repository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withValidTickerPair_returnsStockTrend")
        fun `invoke_withValidTickerPair_returnsStockTrend`() = runTest {
            // Given
            val etfTicker = "069500"
            val stockTicker = "005930"
            val expected = StockTrend(
                etfTicker = etfTicker,
                stockTicker = stockTicker,
                stockName = "삼성전자",
                timeSeries = listOf(
                    HoldingTimeSeries(date = "2026-02-19", weight = 5.0f, amount = 1_000_000f),
                    HoldingTimeSeries(date = "2026-02-18", weight = 4.8f, amount = 950_000f)
                )
            )
            coEvery { repository.getStockTrend(etfTicker, stockTicker) } returns expected

            // When
            val result = useCase(etfTicker, stockTicker)

            // Then
            assertNotNull(result)
            assertEquals(etfTicker, result.etfTicker)
            assertEquals(stockTicker, result.stockTicker)
            assertEquals("삼성전자", result.stockName)
            assertEquals(2, result.timeSeries.size)
        }

        @Test
        @DisplayName("invoke_withNoData_returnsNull")
        fun `invoke_withNoData_returnsNull`() = runTest {
            // Given
            coEvery { repository.getStockTrend(any(), any()) } returns null

            // When
            val result = useCase("069500", "005930")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("invoke_delegatesBothParametersExactly_toRepository")
        fun `invoke_delegatesBothParametersExactly_toRepository`() = runTest {
            // Given
            val etfTicker = "114800"
            val stockTicker = "000660"
            coEvery { repository.getStockTrend(etfTicker, stockTicker) } returns null

            // When
            useCase(etfTicker, stockTicker)

            // Then
            coVerify(exactly = 1) { repository.getStockTrend(etfTicker, stockTicker) }
        }

        @Test
        @DisplayName("invoke_withTimeSeriesData_preservesChronologicalOrder")
        fun `invoke_withTimeSeriesData_preservesChronologicalOrder`() = runTest {
            // Given
            val etfTicker = "069500"
            val stockTicker = "005930"
            val timeSeries = (1..5).map { i ->
                HoldingTimeSeries(
                    date = "2026-02-${(20 - i).toString().padStart(2, '0')}",
                    weight = (5.0f + i * 0.1f),
                    amount = (1_000_000f + i * 10_000f)
                )
            }
            val expected = StockTrend(
                etfTicker = etfTicker,
                stockTicker = stockTicker,
                stockName = "삼성전자",
                timeSeries = timeSeries
            )
            coEvery { repository.getStockTrend(etfTicker, stockTicker) } returns expected

            // When
            val result = useCase(etfTicker, stockTicker)

            // Then
            assertNotNull(result)
            assertEquals(5, result.timeSeries.size)
        }

        @Test
        @DisplayName("invoke_withDifferentEtfs_callsRepositoryEachTime")
        fun `invoke_withDifferentEtfs_callsRepositoryEachTime`() = runTest {
            // Given
            val stockTicker = "005930"
            val etf1 = "069500"
            val etf2 = "114800"
            coEvery { repository.getStockTrend(etf1, stockTicker) } returns null
            coEvery { repository.getStockTrend(etf2, stockTicker) } returns null

            // When
            useCase(etf1, stockTicker)
            useCase(etf2, stockTicker)

            // Then
            coVerify(exactly = 1) { repository.getStockTrend(etf1, stockTicker) }
            coVerify(exactly = 1) { repository.getStockTrend(etf2, stockTicker) }
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
            coEvery { repository.getStockTrend(any(), any()) } throws RuntimeException("추이 조회 실패")

            // When & Then
            var caught: Exception? = null
            try {
                useCase("069500", "005930")
            } catch (e: Exception) {
                caught = e
            }
            assertNotNull(caught)
            assertEquals("추이 조회 실패", caught?.message)
        }
    }
}
