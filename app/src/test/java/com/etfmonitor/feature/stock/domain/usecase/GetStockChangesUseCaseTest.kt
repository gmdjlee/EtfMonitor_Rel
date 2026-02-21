package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.stock.domain.model.StockChangeInfo
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
 * GetStockChangesUseCase 단위 테스트
 *
 * 테스트 범위:
 * - getNewStocks() 신규 편입 종목 반환
 * - getRemovedStocks() 제외된 종목 반환
 * - getIncreasedStocks() 비중 증가 종목 반환
 * - getDecreasedStocks() 비중 감소 종목 반환
 * - 빈 목록 처리
 * - 예외 전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("GetStockChangesUseCase 테스트")
class GetStockChangesUseCaseTest {

    private val repository: StockStatisticsRepository = mockk()
    private lateinit var useCase: GetStockChangesUseCase

    @BeforeEach
    fun setUp() {
        useCase = GetStockChangesUseCase(repository)
    }

    private fun createStockChangeInfo(
        ticker: String = "005930",
        name: String = "삼성전자",
        currentWeight: Float = 5.0f,
        previousWeight: Float = 0f,
        change: Float = 5.0f
    ) = StockChangeInfo(
        stockTicker = ticker,
        stockName = name,
        etfTicker = "069500",
        etfName = "KODEX 200",
        currentWeight = currentWeight,
        currentAmount = 1_000_000f,
        previousWeight = previousWeight,
        change = change
    )

    // ================================================================
    // getNewStocks() 테스트
    // ================================================================

    @Nested
    @DisplayName("getNewStocks() 테스트")
    inner class GetNewStocksTests {

        @Test
        @DisplayName("getNewStocks_withNewlyAddedStocks_returnsNewStockList")
        fun `getNewStocks_withNewlyAddedStocks_returnsNewStockList`() = runTest {
            // Given
            val expected = listOf(
                createStockChangeInfo("005930", "삼성전자", currentWeight = 5.0f, change = 5.0f),
                createStockChangeInfo("000660", "SK하이닉스", currentWeight = 3.0f, change = 3.0f)
            )
            coEvery { repository.getAllNewStocks() } returns expected

            // When
            val result = useCase.getNewStocks()

            // Then
            assertEquals(2, result.size)
            assertEquals("005930", result.first().stockTicker)
            coVerify(exactly = 1) { repository.getAllNewStocks() }
        }

        @Test
        @DisplayName("getNewStocks_withNoNewStocks_returnsEmptyList")
        fun `getNewStocks_withNoNewStocks_returnsEmptyList`() = runTest {
            // Given
            coEvery { repository.getAllNewStocks() } returns emptyList()

            // When
            val result = useCase.getNewStocks()

            // Then
            assertTrue(result.isEmpty())
        }
    }

    // ================================================================
    // getRemovedStocks() 테스트
    // ================================================================

    @Nested
    @DisplayName("getRemovedStocks() 테스트")
    inner class GetRemovedStocksTests {

        @Test
        @DisplayName("getRemovedStocks_withRemovedStocks_returnsRemovedList")
        fun `getRemovedStocks_withRemovedStocks_returnsRemovedList`() = runTest {
            // Given
            val expected = listOf(
                createStockChangeInfo("028260", "삼성물산", currentWeight = 0f, previousWeight = 2.0f, change = -2.0f)
            )
            coEvery { repository.getAllRemovedStocks() } returns expected

            // When
            val result = useCase.getRemovedStocks()

            // Then
            assertEquals(1, result.size)
            assertEquals("028260", result.first().stockTicker)
            coVerify(exactly = 1) { repository.getAllRemovedStocks() }
        }

        @Test
        @DisplayName("getRemovedStocks_withNoRemovedStocks_returnsEmptyList")
        fun `getRemovedStocks_withNoRemovedStocks_returnsEmptyList`() = runTest {
            // Given
            coEvery { repository.getAllRemovedStocks() } returns emptyList()

            // When
            val result = useCase.getRemovedStocks()

            // Then
            assertTrue(result.isEmpty())
        }
    }

    // ================================================================
    // getIncreasedStocks() 테스트
    // ================================================================

    @Nested
    @DisplayName("getIncreasedStocks() 테스트")
    inner class GetIncreasedStocksTests {

        @Test
        @DisplayName("getIncreasedStocks_withIncreasedWeightStocks_returnsIncreasedList")
        fun `getIncreasedStocks_withIncreasedWeightStocks_returnsIncreasedList`() = runTest {
            // Given
            val expected = listOf(
                createStockChangeInfo("005930", "삼성전자", currentWeight = 5.5f, previousWeight = 5.0f, change = 0.5f)
            )
            coEvery { repository.getAllIncreasedStocks() } returns expected

            // When
            val result = useCase.getIncreasedStocks()

            // Then
            assertEquals(1, result.size)
            assertEquals(0.5f, result.first().change)
            coVerify(exactly = 1) { repository.getAllIncreasedStocks() }
        }

        @Test
        @DisplayName("getIncreasedStocks_withNoChange_returnsEmptyList")
        fun `getIncreasedStocks_withNoChange_returnsEmptyList`() = runTest {
            // Given
            coEvery { repository.getAllIncreasedStocks() } returns emptyList()

            // When
            val result = useCase.getIncreasedStocks()

            // Then
            assertTrue(result.isEmpty())
        }
    }

    // ================================================================
    // getDecreasedStocks() 테스트
    // ================================================================

    @Nested
    @DisplayName("getDecreasedStocks() 테스트")
    inner class GetDecreasedStocksTests {

        @Test
        @DisplayName("getDecreasedStocks_withDecreasedWeightStocks_returnsDecreasedList")
        fun `getDecreasedStocks_withDecreasedWeightStocks_returnsDecreasedList`() = runTest {
            // Given
            val expected = listOf(
                createStockChangeInfo("000660", "SK하이닉스", currentWeight = 3.0f, previousWeight = 3.5f, change = -0.5f),
                createStockChangeInfo("005490", "POSCO홀딩스", currentWeight = 1.0f, previousWeight = 1.5f, change = -0.5f)
            )
            coEvery { repository.getAllDecreasedStocks() } returns expected

            // When
            val result = useCase.getDecreasedStocks()

            // Then
            assertEquals(2, result.size)
            assertTrue(result.all { it.change < 0 })
            coVerify(exactly = 1) { repository.getAllDecreasedStocks() }
        }

        @Test
        @DisplayName("getDecreasedStocks_withNoChange_returnsEmptyList")
        fun `getDecreasedStocks_withNoChange_returnsEmptyList`() = runTest {
            // Given
            coEvery { repository.getAllDecreasedStocks() } returns emptyList()

            // When
            val result = useCase.getDecreasedStocks()

            // Then
            assertTrue(result.isEmpty())
        }
    }

    // ================================================================
    // 실패 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("실패 경로 테스트")
    inner class FailurePathTests {

        @Test
        @DisplayName("getNewStocks_whenRepositoryThrows_propagatesException")
        fun `getNewStocks_whenRepositoryThrows_propagatesException`() = runTest {
            // Given
            coEvery { repository.getAllNewStocks() } throws RuntimeException("신규 종목 조회 실패")

            // When & Then
            var caught: Exception? = null
            try {
                useCase.getNewStocks()
            } catch (e: Exception) {
                caught = e
            }
            assertNotNull(caught)
        }

        @Test
        @DisplayName("getRemovedStocks_whenRepositoryThrows_propagatesException")
        fun `getRemovedStocks_whenRepositoryThrows_propagatesException`() = runTest {
            // Given
            coEvery { repository.getAllRemovedStocks() } throws RuntimeException("제외 종목 조회 실패")

            // When & Then
            var caught: Exception? = null
            try {
                useCase.getRemovedStocks()
            } catch (e: Exception) {
                caught = e
            }
            assertNotNull(caught)
        }
    }
}
