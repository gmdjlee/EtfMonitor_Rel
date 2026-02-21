package com.etfmonitor.core.data.repository.krx

import com.etfmonitor.MainDispatcherExtension
import com.krxkt.KrxStock
import com.krxkt.model.Market
import com.krxkt.model.MarketCap
import io.mockk.coEvery
import io.mockk.every
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
 * KrxMarketRepositoryImpl 단위 테스트
 *
 * 테스트 범위:
 * - getIndexComponents: KOSPI-200 index, KOSDAQ-150 index, unknown index
 * - topN 파라미터 준수
 * - 시장 지수별 Market 매핑
 * - 예외 처리 및 CancellationException 재전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("KrxMarketRepositoryImpl 테스트")
class KrxMarketRepositoryImplTest {

    private lateinit var krxStock: KrxStock
    private lateinit var repository: KrxMarketRepositoryImpl

    @BeforeEach
    fun setup() {
        krxStock = mockk(relaxed = true)
        repository = KrxMarketRepositoryImpl(krxStock)
    }

    // ============================================================
    // getIndexComponents — KOSPI-200
    // ============================================================

    @Nested
    @DisplayName("KOSPI-200 index components 테스트")
    inner class Kospi200Tests {

        @Test
        @DisplayName("getIndexComponents_withKospi200Index_usesKospiMarket")
        fun `getIndexComponents_withKospi200Index_usesKospiMarket`() = runTest {
            // Given
            val caps = (1..300).map { createMockMarketCap("05${it.toString().padStart(4, '0')}", 1_000_000_000L * (300 - it)) }
            coEvery { krxStock.getMarketCap(any(), Market.KOSPI) } returns caps

            // When
            val result = repository.getIndexComponents(
                indexTicker = KrxMarketRepositoryImpl.KOSPI_200_INDEX,
                topN = 200
            )

            // Then
            assertTrue(result.isSuccess)
            val tickers = result.getOrNull()
            assertNotNull(tickers)
            assertEquals(200, tickers.size)
        }

        @Test
        @DisplayName("getIndexComponents_withTopN50_returnsOnly50Tickers")
        fun `getIndexComponents_withTopN50_returnsOnly50Tickers`() = runTest {
            // Given
            val caps = (1..100).map { createMockMarketCap("${it.toString().padStart(6, '0')}", 100_000_000L * (100 - it)) }
            coEvery { krxStock.getMarketCap(any(), any()) } returns caps

            // When
            val result = repository.getIndexComponents(
                indexTicker = KrxMarketRepositoryImpl.KOSPI_200_INDEX,
                topN = 50
            )

            // Then
            assertTrue(result.isSuccess)
            assertEquals(50, result.getOrNull()?.size)
        }

        @Test
        @DisplayName("getIndexComponents_withKospi200_sortsByMarketCapDescending")
        fun `getIndexComponents_withKospi200_sortsByMarketCapDescending`() = runTest {
            // Given: caps in ascending order
            val caps = listOf(
                createMockMarketCap("000001", 1_000_000L),
                createMockMarketCap("000002", 3_000_000L),
                createMockMarketCap("000003", 2_000_000L)
            )
            coEvery { krxStock.getMarketCap(any(), any()) } returns caps

            // When
            val result = repository.getIndexComponents(
                indexTicker = KrxMarketRepositoryImpl.KOSPI_200_INDEX,
                topN = 3
            )

            // Then: first ticker should be highest market cap
            assertTrue(result.isSuccess)
            val tickers = result.getOrNull()
            assertNotNull(tickers)
            assertEquals("000002", tickers[0])
        }
    }

    // ============================================================
    // getIndexComponents — KOSDAQ-150
    // ============================================================

    @Nested
    @DisplayName("KOSDAQ-150 index components 테스트")
    inner class Kosdaq150Tests {

        @Test
        @DisplayName("getIndexComponents_withKosdaq150Index_usesKosdaqMarket")
        fun `getIndexComponents_withKosdaq150Index_usesKosdaqMarket`() = runTest {
            // Given
            val caps = (1..200).map { createMockMarketCap("${it.toString().padStart(6, '0')}", 500_000_000L * (200 - it)) }
            coEvery { krxStock.getMarketCap(any(), Market.KOSDAQ) } returns caps

            // When
            val result = repository.getIndexComponents(
                indexTicker = KrxMarketRepositoryImpl.KOSDAQ_150_INDEX,
                topN = 150
            )

            // Then
            assertTrue(result.isSuccess)
            assertEquals(150, result.getOrNull()?.size)
        }
    }

    // ============================================================
    // getIndexComponents — Unknown index
    // ============================================================

    @Nested
    @DisplayName("알 수 없는 index 테스트")
    inner class UnknownIndexTests {

        @Test
        @DisplayName("getIndexComponents_withUnknownIndex_usesAllMarket")
        fun `getIndexComponents_withUnknownIndex_usesAllMarket`() = runTest {
            // Given
            coEvery { krxStock.getMarketCap(any(), Market.ALL) } returns listOf(
                createMockMarketCap("000001", 1_000_000_000L)
            )

            // When
            val result = repository.getIndexComponents(
                indexTicker = "9999",
                topN = 10
            )

            // Then
            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("getIndexComponents_withEmptyMarketCap_returnsEmptyList")
        fun `getIndexComponents_withEmptyMarketCap_returnsEmptyList`() = runTest {
            // Given
            coEvery { krxStock.getMarketCap(any(), any()) } returns emptyList()

            // When
            val result = repository.getIndexComponents(
                indexTicker = KrxMarketRepositoryImpl.KOSPI_200_INDEX,
                topN = 200
            )

            // Then
            assertTrue(result.isSuccess)
            assertEquals(0, result.getOrNull()?.size)
        }

        @Test
        @DisplayName("getIndexComponents_whenKrxThrowsException_returnsFailure")
        fun `getIndexComponents_whenKrxThrowsException_returnsFailure`() = runTest {
            // Given
            coEvery { krxStock.getMarketCap(any(), any()) } throws RuntimeException("Server error")

            // When
            val result = repository.getIndexComponents(
                indexTicker = KrxMarketRepositoryImpl.KOSPI_200_INDEX
            )

            // Then
            assertTrue(result.isFailure)
        }
    }

    // ============================================================
    // CancellationException rethrow
    // ============================================================

    @Test
    @DisplayName("getIndexComponents_whenCancelled_rethrowsCancellationException")
    fun `getIndexComponents_whenCancelled_rethrowsCancellationException`() = runTest {
        // Given
        coEvery { krxStock.getMarketCap(any(), any()) } throws CancellationException("Cancelled")

        // When & Then
        var exceptionCaught: Throwable? = null
        try {
            repository.getIndexComponents(KrxMarketRepositoryImpl.KOSPI_200_INDEX)
        } catch (e: CancellationException) {
            exceptionCaught = e
        }
        assertNotNull(exceptionCaught, "CancellationException must be rethrown")
    }

    // ============================================================
    // Helper
    // ============================================================

    private fun createMockMarketCap(ticker: String, marketCap: Long): MarketCap {
        val mock = mockk<MarketCap>()
        every { mock.ticker } returns ticker
        every { mock.marketCap } returns marketCap
        every { mock.close } returns 10000L
        every { mock.sharesOutstanding } returns (marketCap / 10000)
        return mock
    }
}
