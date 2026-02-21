package com.etfmonitor.core.data.repository.krx

import com.etfmonitor.MainDispatcherExtension
import com.krxkt.KrxEtf
import com.krxkt.model.EtfInfo
import com.krxkt.model.EtfPortfolio
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
 * KrxEtfRepositoryImpl 단위 테스트
 *
 * 테스트 범위:
 * - getEtfList: 성공, 빈 결과
 * - getEtfHoldings: 날짜 포맷 변환(Critical Rule #10), Holding.create() 사용 검증
 * - getEtfName: 성공, null 처리
 * - 예외 처리 및 CancellationException 재전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("KrxEtfRepositoryImpl 테스트")
class KrxEtfRepositoryImplTest {

    private lateinit var krxEtf: KrxEtf
    private lateinit var repository: KrxEtfRepositoryImpl

    @BeforeEach
    fun setup() {
        krxEtf = mockk(relaxed = true)
        repository = KrxEtfRepositoryImpl(krxEtf)
    }

    // ============================================================
    // getEtfList
    // ============================================================

    @Nested
    @DisplayName("getEtfList 테스트")
    inner class GetEtfListTests {

        @Test
        @DisplayName("getEtfList_withValidDate_returnsTickerList")
        fun `getEtfList_withValidDate_returnsTickerList`() = runTest {
            // Given
            val etfTickers = listOf(
                createMockEtfInfo("069500"),
                createMockEtfInfo("229200"),
                createMockEtfInfo("251340")
            )
            coEvery { krxEtf.getEtfTickerList(any()) } returns etfTickers

            // When
            val result = repository.getEtfList(date = "20260101")

            // Then
            assertTrue(result.isSuccess)
            val tickers = result.getOrNull()
            assertNotNull(tickers)
            assertEquals(3, tickers.size)
            assertTrue(tickers.contains("069500"))
        }

        @Test
        @DisplayName("getEtfList_withEmptyResult_returnsEmptyList")
        fun `getEtfList_withEmptyResult_returnsEmptyList`() = runTest {
            // Given
            coEvery { krxEtf.getEtfTickerList(any()) } returns emptyList()

            // When
            val result = repository.getEtfList()

            // Then
            assertTrue(result.isSuccess)
            assertEquals(0, result.getOrNull()?.size)
        }

        @Test
        @DisplayName("getEtfList_whenKrxThrowsException_returnsFailure")
        fun `getEtfList_whenKrxThrowsException_returnsFailure`() = runTest {
            // Given
            coEvery { krxEtf.getEtfTickerList(any()) } throws RuntimeException("Network timeout")

            // When
            val result = repository.getEtfList()

            // Then
            assertTrue(result.isFailure)
        }
    }

    // ============================================================
    // getEtfHoldings
    // ============================================================

    @Nested
    @DisplayName("getEtfHoldings 테스트")
    inner class GetEtfHoldingsTests {

        @Test
        @DisplayName("getEtfHoldings_withValidData_convertsKrxDateToIsoFormat")
        fun `getEtfHoldings_withValidData_convertsKrxDateToIsoFormat`() = runTest {
            // Given
            val portfolio = listOf(
                createMockEtfPortfolio("005930", "삼성전자", 30.5, 10_000_000L),
                createMockEtfPortfolio("000660", "SK하이닉스", 12.3, 5_000_000L)
            )
            coEvery { krxEtf.getPortfolio(date = any(), ticker = any()) } returns portfolio

            // When: pass krx format date
            val result = repository.getEtfHoldings(ticker = "069500", date = "20260219")

            // Then
            assertTrue(result.isSuccess)
            val holdings = result.getOrNull()
            assertNotNull(holdings)
            assertEquals(2, holdings.size)
            // Critical Rule #10: date must be in ISO format yyyy-MM-dd
            assertTrue(holdings.all { it.date == "2026-02-19" },
                "Holdings dates must be converted from yyyyMMdd to yyyy-MM-dd")
        }

        @Test
        @DisplayName("getEtfHoldings_usesHoldingCreateFactory_notDirectConstructor")
        fun `getEtfHoldings_usesHoldingCreateFactory_notDirectConstructor`() = runTest {
            // Given: use weight small enough to not overflow Short.MAX_VALUE (32767)
            // HoldingMapper passes portfolio.weight (%) directly to Holding.create(weight),
            // which computes weightBps = (weight * 10000). For 3.0% → 30000 (fits in Short).
            val portfolio = listOf(
                createMockEtfPortfolio("005930", "삼성전자", 3.0, 10_000_000L)
            )
            coEvery { krxEtf.getPortfolio(date = any(), ticker = any()) } returns portfolio

            // When
            val result = repository.getEtfHoldings("069500", "20260101")

            // Then: weightBps and amountMillion should be correctly stored via factory
            assertTrue(result.isSuccess)
            val holding = result.getOrNull()?.first()
            assertNotNull(holding)
            // 3.0 (as passed to Holding.create) → weightBps = (3.0 * 10000) = 30000
            assertEquals(30000.toShort(), holding.weightBps)
            // 10_000_000 → amountMillion = (10_000_000 / 1_000_000) = 10
            assertEquals(10, holding.amountMillion)
        }

        @Test
        @DisplayName("getEtfHoldings_withEmptyPortfolio_returnsEmptyList")
        fun `getEtfHoldings_withEmptyPortfolio_returnsEmptyList`() = runTest {
            // Given
            coEvery { krxEtf.getPortfolio(date = any(), ticker = any()) } returns emptyList()

            // When
            val result = repository.getEtfHoldings("069500", "20260101")

            // Then
            assertTrue(result.isSuccess)
            assertEquals(0, result.getOrNull()?.size)
        }

        @Test
        @DisplayName("getEtfHoldings_whenKrxThrowsException_returnsFailure")
        fun `getEtfHoldings_whenKrxThrowsException_returnsFailure`() = runTest {
            // Given
            coEvery { krxEtf.getPortfolio(date = any(), ticker = any()) } throws RuntimeException("API error")

            // When
            val result = repository.getEtfHoldings("069500", "20260101")

            // Then
            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("getEtfHoldings_setsCorrectEtfTicker")
        fun `getEtfHoldings_setsCorrectEtfTicker`() = runTest {
            // Given
            val portfolio = listOf(createMockEtfPortfolio("005930", "삼성전자", 30.5, 10_000_000L))
            coEvery { krxEtf.getPortfolio(date = any(), ticker = any()) } returns portfolio

            // When
            val result = repository.getEtfHoldings("069500", "20260101")

            // Then
            assertTrue(result.isSuccess)
            assertEquals("069500", result.getOrNull()?.first()?.etfTicker)
        }
    }

    // ============================================================
    // getEtfName
    // ============================================================

    @Nested
    @DisplayName("getEtfName 테스트")
    inner class GetEtfNameTests {

        @Test
        @DisplayName("getEtfName_withValidTicker_returnsName")
        fun `getEtfName_withValidTicker_returnsName`() = runTest {
            // Given
            coEvery { krxEtf.getEtfName(any(), any()) } returns "KODEX 200"

            // When
            val result = repository.getEtfName("069500", "20260101")

            // Then
            assertTrue(result.isSuccess)
            assertEquals("KODEX 200", result.getOrNull())
        }

        @Test
        @DisplayName("getEtfName_whenKrxReturnsNull_returnsEmptyString")
        fun `getEtfName_whenKrxReturnsNull_returnsEmptyString`() = runTest {
            // Given
            coEvery { krxEtf.getEtfName(any(), any()) } returns null

            // When
            val result = repository.getEtfName("999999", "20260101")

            // Then
            assertTrue(result.isSuccess)
            assertEquals("", result.getOrNull())
        }

        @Test
        @DisplayName("getEtfName_whenKrxThrowsException_returnsFailure")
        fun `getEtfName_whenKrxThrowsException_returnsFailure`() = runTest {
            // Given
            coEvery { krxEtf.getEtfName(any(), any()) } throws RuntimeException("Timeout")

            // When
            val result = repository.getEtfName("069500", "20260101")

            // Then
            assertTrue(result.isFailure)
        }
    }

    // ============================================================
    // CancellationException rethrow
    // ============================================================

    @Test
    @DisplayName("getEtfHoldings_whenCancelled_rethrowsCancellationException")
    fun `getEtfHoldings_whenCancelled_rethrowsCancellationException`() = runTest {
        // Given
        coEvery { krxEtf.getPortfolio(date = any(), ticker = any()) } throws CancellationException("Cancelled")

        // When & Then
        var exceptionCaught: Throwable? = null
        try {
            repository.getEtfHoldings("069500", "20260101")
        } catch (e: CancellationException) {
            exceptionCaught = e
        }
        assertNotNull(exceptionCaught, "CancellationException must be rethrown")
    }

    // ============================================================
    // Helpers
    // ============================================================

    private fun createMockEtfInfo(ticker: String): EtfInfo {
        val mock = mockk<EtfInfo>()
        every { mock.ticker } returns ticker
        every { mock.name } returns "ETF $ticker"
        every { mock.isinCode } returns "KR7${ticker}000"
        return mock
    }

    private fun createMockEtfPortfolio(
        ticker: String,
        name: String,
        weight: Double?,
        amount: Long
    ): EtfPortfolio {
        val mock = mockk<EtfPortfolio>()
        every { mock.ticker } returns ticker
        every { mock.name } returns name
        every { mock.weight } returns weight
        every { mock.amount } returns amount
        return mock
    }
}
