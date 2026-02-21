package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.stock.domain.model.Stock
import com.etfmonitor.feature.stock.domain.repository.StockRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SearchStocksUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 검색 결과 Flow 반환
 * - invoke() 빈 검색 결과 처리
 * - 쿼리 파라미터 정확히 전달 검증
 * - Flow 방출 데이터 내용 검증
 * - ticker 코드 검색 vs 이름 검색 구분
 *
 * 주의: SearchStocksUseCase.invoke()는 suspend가 아닌 Flow<List<Stock>> 반환
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("SearchStocksUseCase 테스트")
class SearchStocksUseCaseTest {

    private val repository: StockRepository = mockk()
    private lateinit var useCase: SearchStocksUseCase

    @BeforeEach
    fun setUp() {
        useCase = SearchStocksUseCase(repository)
    }

    private fun createStock(ticker: String, name: String, market: String = "KOSPI") = Stock(
        ticker = ticker,
        name = name,
        market = market,
        isEtfHolding = true,
        lastUpdated = System.currentTimeMillis()
    )

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withNameQuery_returnsMatchingStocksFlow")
        fun `invoke_withNameQuery_returnsMatchingStocksFlow`() = runTest {
            // Given
            val query = "삼성"
            val stocks = listOf(
                createStock("005930", "삼성전자"),
                createStock("005935", "삼성전자우"),
                createStock("028260", "삼성물산")
            )
            every { repository.searchStocks(query) } returns flowOf(stocks)

            // When
            val result = useCase(query).first()

            // Then
            assertEquals(3, result.size)
            assertTrue(result.all { it.name.contains("삼성") })
        }

        @Test
        @DisplayName("invoke_withTickerQuery_returnsMatchingStocksFlow")
        fun `invoke_withTickerQuery_returnsMatchingStocksFlow`() = runTest {
            // Given
            val query = "00593"
            val stocks = listOf(
                createStock("005930", "삼성전자"),
                createStock("005935", "삼성전자우")
            )
            every { repository.searchStocks(query) } returns flowOf(stocks)

            // When
            val result = useCase(query).first()

            // Then
            assertEquals(2, result.size)
        }

        @Test
        @DisplayName("invoke_withEmptyQuery_returnsEmptyFlow")
        fun `invoke_withEmptyQuery_returnsEmptyFlow`() = runTest {
            // Given
            every { repository.searchStocks("") } returns flowOf(emptyList())

            // When
            val result = useCase("").first()

            // Then
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("invoke_withNoMatch_returnsEmptyList")
        fun `invoke_withNoMatch_returnsEmptyList`() = runTest {
            // Given
            every { repository.searchStocks(any()) } returns flowOf(emptyList())

            // When
            val result = useCase("없는종목XYZ").first()

            // Then
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("invoke_delegatesQueryExactly_toRepository")
        fun `invoke_delegatesQueryExactly_toRepository`() = runTest {
            // Given
            val query = "SK하이닉스"
            every { repository.searchStocks(query) } returns flowOf(listOf(
                createStock("000660", "SK하이닉스")
            ))

            // When
            useCase(query).first()

            // Then
            verify(exactly = 1) { repository.searchStocks(query) }
        }

        @Test
        @DisplayName("invoke_withKosdaqStock_returnsKosdaqStock")
        fun `invoke_withKosdaqStock_returnsKosdaqStock`() = runTest {
            // Given
            val query = "에코프로"
            val stocks = listOf(
                createStock("247540", "에코프로비엠", market = "KOSDAQ"),
                createStock("086520", "에코프로", market = "KOSDAQ")
            )
            every { repository.searchStocks(query) } returns flowOf(stocks)

            // When
            val result = useCase(query).first()

            // Then
            assertEquals(2, result.size)
            assertTrue(result.all { it.market == "KOSDAQ" })
        }
    }
}
