package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.stock.domain.model.StockAnalysisResult
import com.etfmonitor.feature.stock.domain.repository.StockSearchResult
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
 * AnalyzeStockUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 종목 분석 결과 반환
 * - invoke() 데이터 없을 때 null 반환
 * - searchStocks() 검색 결과 반환
 * - 파라미터 정확히 전달 검증
 * - 예외 전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("AnalyzeStockUseCase 테스트")
class AnalyzeStockUseCaseTest {

    private val repository: StockStatisticsRepository = mockk()
    private lateinit var useCase: AnalyzeStockUseCase

    @BeforeEach
    fun setUp() {
        useCase = AnalyzeStockUseCase(repository)
    }

    // ================================================================
    // invoke() 테스트
    // ================================================================

    @Nested
    @DisplayName("invoke() 종목 분석 테스트")
    inner class InvokeTests {

        @Test
        @DisplayName("invoke_withValidTicker_returnsAnalysisResult")
        fun `invoke_withValidTicker_returnsAnalysisResult`() = runTest {
            // Given
            val ticker = "005930"
            val expected = StockAnalysisResult(
                stockTicker = ticker,
                stockName = "삼성전자",
                etfDetails = emptyList(),
                totalAmount = 1_000_000f,
                currentEtfCount = 5,
                previousEtfCount = 4,
                increasedCount = 2,
                decreasedCount = 1,
                newIncludedCount = 1,
                removedCount = 0,
                avgWeight = 2.5f,
                maxWeight = 5.0f
            )
            coEvery { repository.analyzeStock(ticker) } returns expected

            // When
            val result = useCase(ticker)

            // Then
            assertNotNull(result)
            assertEquals(ticker, result.stockTicker)
            assertEquals("삼성전자", result.stockName)
            assertEquals(5, result.currentEtfCount)
            assertEquals(1_000_000f, result.totalAmount)
        }

        @Test
        @DisplayName("invoke_withUnknownTicker_returnsNull")
        fun `invoke_withUnknownTicker_returnsNull`() = runTest {
            // Given
            coEvery { repository.analyzeStock(any()) } returns null

            // When
            val result = useCase("999999")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("invoke_delegatesTickerExactly_toRepository")
        fun `invoke_delegatesTickerExactly_toRepository`() = runTest {
            // Given
            val ticker = "000660"
            coEvery { repository.analyzeStock(ticker) } returns null

            // When
            useCase(ticker)

            // Then
            coVerify(exactly = 1) { repository.analyzeStock(ticker) }
        }
    }

    // ================================================================
    // searchStocks() 테스트
    // ================================================================

    @Nested
    @DisplayName("searchStocks() 테스트")
    inner class SearchStocksTests {

        @Test
        @DisplayName("searchStocks_withQuery_returnsMatchingResults")
        fun `searchStocks_withQuery_returnsMatchingResults`() = runTest {
            // Given
            val query = "삼성"
            val expected = listOf(
                StockSearchResult("005930", "삼성전자"),
                StockSearchResult("005935", "삼성전자우"),
                StockSearchResult("028260", "삼성물산")
            )
            coEvery { repository.searchStocks(query) } returns expected

            // When
            val result = useCase.searchStocks(query)

            // Then
            assertEquals(3, result.size)
            assertEquals("005930", result.first().stockTicker)
            assertEquals("삼성전자", result.first().stockName)
        }

        @Test
        @DisplayName("searchStocks_withNoMatch_returnsEmptyList")
        fun `searchStocks_withNoMatch_returnsEmptyList`() = runTest {
            // Given
            coEvery { repository.searchStocks(any()) } returns emptyList()

            // When
            val result = useCase.searchStocks("없는종목XYZ")

            // Then
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("searchStocks_delegatesQueryExactly_toRepository")
        fun `searchStocks_delegatesQueryExactly_toRepository`() = runTest {
            // Given
            val query = "SK하이닉스"
            coEvery { repository.searchStocks(query) } returns listOf(
                StockSearchResult("000660", "SK하이닉스")
            )

            // When
            useCase.searchStocks(query)

            // Then
            coVerify(exactly = 1) { repository.searchStocks(query) }
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
            coEvery { repository.analyzeStock(any()) } throws RuntimeException("분석 실패")

            // When & Then
            var caught: Exception? = null
            try {
                useCase("005930")
            } catch (e: Exception) {
                caught = e
            }
            assertNotNull(caught)
            assertEquals("분석 실패", caught?.message)
        }

        @Test
        @DisplayName("searchStocks_whenRepositoryThrows_propagatesException")
        fun `searchStocks_whenRepositoryThrows_propagatesException`() = runTest {
            // Given
            coEvery { repository.searchStocks(any()) } throws RuntimeException("검색 실패")

            // When & Then
            var caught: Exception? = null
            try {
                useCase.searchStocks("삼성")
            } catch (e: Exception) {
                caught = e
            }
            assertNotNull(caught)
        }
    }
}
