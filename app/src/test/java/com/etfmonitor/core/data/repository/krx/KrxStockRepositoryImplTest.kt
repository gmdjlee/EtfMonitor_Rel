package com.etfmonitor.core.data.repository.krx

import com.etfmonitor.MainDispatcherExtension
import com.krxkt.KrxStock
import com.krxkt.model.Market
import com.krxkt.model.MarketCap
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
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
 * KrxStockRepositoryImpl 단위 테스트
 *
 * 테스트 범위:
 * - getStockList: 성공, 예외 처리
 * - getMarketCap: 성공, 빈 결과, 예외 처리
 * - CancellationException 재전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("KrxStockRepositoryImpl 테스트")
class KrxStockRepositoryImplTest {

    private lateinit var krxStock: KrxStock
    private lateinit var repository: KrxStockRepositoryImpl

    @BeforeEach
    fun setup() {
        krxStock = mockk(relaxed = true)
        repository = KrxStockRepositoryImpl(krxStock)
    }

    // ============================================================
    // getStockList
    // ============================================================

    @Nested
    @DisplayName("getStockList 테스트")
    inner class GetStockListTests {

        @Test
        @DisplayName("getStockList_withValidDate_returnsTickerList")
        fun `getStockList_withValidDate_returnsTickerList`() = runTest {
            // Given
            val mockTickers = listOf(
                createMockTickerInfo("005930"),
                createMockTickerInfo("000660"),
                createMockTickerInfo("035420")
            )
            coEvery { krxStock.getTickerList(any(), any()) } returns mockTickers

            // When
            val result = repository.getStockList(date = "20260101", market = Market.ALL)

            // Then
            assertTrue(result.isSuccess)
            val tickers = result.getOrNull()
            assertNotNull(tickers)
            assertEquals(3, tickers.size)
            assertTrue(tickers.contains("005930"))
            assertTrue(tickers.contains("000660"))
        }

        @Test
        @DisplayName("getStockList_withEmptyResult_returnsEmptyList")
        fun `getStockList_withEmptyResult_returnsEmptyList`() = runTest {
            // Given
            coEvery { krxStock.getTickerList(any(), any()) } returns emptyList()

            // When
            val result = repository.getStockList()

            // Then
            assertTrue(result.isSuccess)
            assertEquals(0, result.getOrNull()?.size)
        }

        @Test
        @DisplayName("getStockList_whenKrxThrowsException_returnsFailure")
        fun `getStockList_whenKrxThrowsException_returnsFailure`() = runTest {
            // Given
            coEvery { krxStock.getTickerList(any(), any()) } throws RuntimeException("Network error")

            // When
            val result = repository.getStockList()

            // Then
            assertTrue(result.isFailure)
            assertNotNull(result.exceptionOrNull())
        }

        @Test
        @DisplayName("getStockList_withKospiMarket_passesCorrectMarket")
        fun `getStockList_withKospiMarket_passesCorrectMarket`() = runTest {
            // Given
            coEvery { krxStock.getTickerList(any(), Market.KOSPI) } returns listOf(
                createMockTickerInfo("005930")
            )

            // When
            val result = repository.getStockList(market = Market.KOSPI)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrNull()?.size)
        }
    }

    // ============================================================
    // getMarketCap
    // ============================================================

    @Nested
    @DisplayName("getMarketCap 테스트")
    inner class GetMarketCapTests {

        @Test
        @DisplayName("getMarketCap_withValidDate_returnsMarketCapList")
        fun `getMarketCap_withValidDate_returnsMarketCapList`() = runTest {
            // Given
            val mockCaps = listOf(
                createMockMarketCap("005930", 400_000_000_000_000L),
                createMockMarketCap("000660", 100_000_000_000_000L)
            )
            coEvery { krxStock.getMarketCap(any(), any()) } returns mockCaps

            // When
            val result = repository.getMarketCap()

            // Then
            assertTrue(result.isSuccess)
            val caps = result.getOrNull()
            assertNotNull(caps)
            assertEquals(2, caps.size)
            assertEquals("005930", caps[0].ticker)
        }

        @Test
        @DisplayName("getMarketCap_withEmptyResult_returnsEmptyList")
        fun `getMarketCap_withEmptyResult_returnsEmptyList`() = runTest {
            // Given
            coEvery { krxStock.getMarketCap(any(), any()) } returns emptyList()

            // When
            val result = repository.getMarketCap()

            // Then
            assertTrue(result.isSuccess)
            assertEquals(0, result.getOrNull()?.size)
        }

        @Test
        @DisplayName("getMarketCap_whenKrxThrowsException_returnsFailure")
        fun `getMarketCap_whenKrxThrowsException_returnsFailure`() = runTest {
            // Given
            coEvery { krxStock.getMarketCap(any(), any()) } throws RuntimeException("API timeout")

            // When
            val result = repository.getMarketCap()

            // Then
            assertTrue(result.isFailure)
        }
    }

    // ============================================================
    // CancellationException rethrow
    // ============================================================

    @Test
    @DisplayName("getStockList_whenCancelled_rethrowsCancellationException")
    fun `getStockList_whenCancelled_rethrowsCancellationException`() = runTest {
        // Given
        coEvery { krxStock.getTickerList(any(), any()) } throws CancellationException("Cancelled")

        // When & Then
        var exceptionCaught: Throwable? = null
        try {
            repository.getStockList()
        } catch (e: CancellationException) {
            exceptionCaught = e
        }
        assertNotNull(exceptionCaught, "CancellationException must be rethrown")
    }

    // ============================================================
    // Helpers
    // ============================================================

    private fun createMockTickerInfo(ticker: String): com.krxkt.model.TickerInfo {
        val mock = mockk<com.krxkt.model.TickerInfo>()
        io.mockk.every { mock.ticker } returns ticker
        return mock
    }

    private fun createMockMarketCap(ticker: String, marketCap: Long): MarketCap {
        val mock = mockk<MarketCap>()
        io.mockk.every { mock.ticker } returns ticker
        io.mockk.every { mock.marketCap } returns marketCap
        io.mockk.every { mock.close } returns 80000L
        io.mockk.every { mock.sharesOutstanding } returns (marketCap / 80000)
        return mock
    }
}
