package com.etfmonitor.core.data.repository.krx

import com.etfmonitor.MainDispatcherExtension
import com.krxkt.KrxIndex
import com.krxkt.model.IndexOhlcv
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
 * KrxIndexRepositoryImpl 단위 테스트
 *
 * 테스트 범위:
 * - getMarketIndices: KOSPI, KOSDAQ, 다중 시장, 날짜 포맷 변환
 * - getRecentMarketIndices: 기간 계산
 * - 날짜 변환: yyyyMMdd → yyyy-MM-dd (Critical Rule #10)
 * - 변화율 계산
 * - 예외 처리 및 CancellationException 재전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("KrxIndexRepositoryImpl 테스트")
class KrxIndexRepositoryImplTest {

    private lateinit var krxIndex: KrxIndex
    private lateinit var repository: KrxIndexRepositoryImpl

    @BeforeEach
    fun setup() {
        krxIndex = mockk(relaxed = true)
        repository = KrxIndexRepositoryImpl(krxIndex)
    }

    // ============================================================
    // getMarketIndices — KOSPI
    // ============================================================

    @Nested
    @DisplayName("getMarketIndices KOSPI 테스트")
    inner class KospiTests {

        @Test
        @DisplayName("getMarketIndices_withKospiOnly_returnsKospiEntities")
        fun `getMarketIndices_withKospiOnly_returnsKospiEntities`() = runTest {
            // Given
            val ohlcvList = listOf(
                createMockIndexOhlcv("20260101", 2800.0),
                createMockIndexOhlcv("20260102", 2820.0)
            )
            coEvery { krxIndex.getKospi(any(), any()) } returns ohlcvList

            // When
            val result = repository.getMarketIndices(
                startDate = "20260101",
                endDate = "20260102",
                markets = listOf("KOSPI")
            )

            // Then
            assertTrue(result.isSuccess)
            val indices = result.getOrNull()
            assertNotNull(indices)
            assertEquals(2, indices.size)
            assertTrue(indices.all { it.market == "KOSPI" })
        }

        @Test
        @DisplayName("getMarketIndices_convertsKrxDateToIsoFormat")
        fun `getMarketIndices_convertsKrxDateToIsoFormat`() = runTest {
            // Given: krx format date yyyyMMdd
            val ohlcvList = listOf(createMockIndexOhlcv("20260219", 2800.0))
            coEvery { krxIndex.getKospi(any(), any()) } returns ohlcvList

            // When
            val result = repository.getMarketIndices("20260219", "20260219", listOf("KOSPI"))

            // Then: date should be converted to ISO format yyyy-MM-dd
            assertTrue(result.isSuccess)
            val entity = result.getOrNull()?.first()
            assertNotNull(entity)
            assertEquals("2026-02-19", entity.date, "Date must be converted from yyyyMMdd to yyyy-MM-dd")
        }

        @Test
        @DisplayName("getMarketIndices_calculatesCorrectIdFormat")
        fun `getMarketIndices_calculatesCorrectIdFormat`() = runTest {
            // Given
            val ohlcvList = listOf(createMockIndexOhlcv("20260101", 2800.0))
            coEvery { krxIndex.getKospi(any(), any()) } returns ohlcvList

            // When
            val result = repository.getMarketIndices("20260101", "20260101", listOf("KOSPI"))

            // Then
            assertTrue(result.isSuccess)
            val entity = result.getOrNull()?.first()
            assertEquals("KOSPI-2026-01-01", entity?.id)
        }

        @Test
        @DisplayName("getMarketIndices_withPositiveChange_calculatesPositiveChangeRate")
        fun `getMarketIndices_withPositiveChange_calculatesPositiveChangeRate`() = runTest {
            // Given: close = 100, change = 5 => prevClose = 95 => changeRate = 5/95 * 100 ≈ 5.26%
            val ohlcvList = listOf(createMockIndexOhlcv("20260101", close = 100.0, change = 5.0))
            coEvery { krxIndex.getKospi(any(), any()) } returns ohlcvList

            // When
            val result = repository.getMarketIndices("20260101", "20260101", listOf("KOSPI"))

            // Then
            assertTrue(result.isSuccess)
            val entity = result.getOrNull()?.first()
            assertNotNull(entity)
            assertTrue(entity.changeRate > 0, "Change rate should be positive when price rose")
        }
    }

    // ============================================================
    // getMarketIndices — KOSDAQ
    // ============================================================

    @Nested
    @DisplayName("getMarketIndices KOSDAQ 테스트")
    inner class KosdaqTests {

        @Test
        @DisplayName("getMarketIndices_withKosdaqOnly_returnsKosdaqEntities")
        fun `getMarketIndices_withKosdaqOnly_returnsKosdaqEntities`() = runTest {
            // Given
            coEvery { krxIndex.getKosdaq(any(), any()) } returns listOf(
                createMockIndexOhlcv("20260101", 900.0)
            )

            // When
            val result = repository.getMarketIndices("20260101", "20260101", listOf("KOSDAQ"))

            // Then
            assertTrue(result.isSuccess)
            val indices = result.getOrNull()
            assertNotNull(indices)
            assertEquals(1, indices.size)
            assertEquals("KOSDAQ", indices[0].market)
        }
    }

    // ============================================================
    // getMarketIndices — Multiple markets
    // ============================================================

    @Nested
    @DisplayName("복수 시장 테스트")
    inner class MultipleMarketsTests {

        @Test
        @DisplayName("getMarketIndices_withBothMarkets_combinesResults")
        fun `getMarketIndices_withBothMarkets_combinesResults`() = runTest {
            // Given
            coEvery { krxIndex.getKospi(any(), any()) } returns listOf(
                createMockIndexOhlcv("20260101", 2800.0)
            )
            coEvery { krxIndex.getKosdaq(any(), any()) } returns listOf(
                createMockIndexOhlcv("20260101", 900.0)
            )

            // When
            val result = repository.getMarketIndices(
                startDate = "20260101",
                endDate = "20260101",
                markets = listOf("KOSPI", "KOSDAQ")
            )

            // Then: both markets combined
            assertTrue(result.isSuccess)
            assertEquals(2, result.getOrNull()?.size)
        }

        @Test
        @DisplayName("getMarketIndices_withUnknownMarket_returnsEmptyForThatMarket")
        fun `getMarketIndices_withUnknownMarket_returnsEmptyForThatMarket`() = runTest {
            // Given: only KOSPI data
            coEvery { krxIndex.getKospi(any(), any()) } returns listOf(
                createMockIndexOhlcv("20260101", 2800.0)
            )

            // When: include unknown market
            val result = repository.getMarketIndices(
                startDate = "20260101",
                endDate = "20260101",
                markets = listOf("KOSPI", "UNKNOWN")
            )

            // Then: only KOSPI data, unknown market returns nothing
            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrNull()?.size)
        }
    }

    // ============================================================
    // getRecentMarketIndices
    // ============================================================

    @Nested
    @DisplayName("getRecentMarketIndices 테스트")
    inner class RecentMarketIndicesTests {

        @Test
        @DisplayName("getRecentMarketIndices_withValidDays_returnsResult")
        fun `getRecentMarketIndices_withValidDays_returnsResult`() = runTest {
            // Given
            coEvery { krxIndex.getKospi(any(), any()) } returns listOf(
                createMockIndexOhlcv("20260101", 2800.0)
            )
            coEvery { krxIndex.getKosdaq(any(), any()) } returns emptyList()

            // When
            val result = repository.getRecentMarketIndices(days = 30)

            // Then
            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("getRecentMarketIndices_whenKrxThrowsException_returnsFailure")
        fun `getRecentMarketIndices_whenKrxThrowsException_returnsFailure`() = runTest {
            // Given
            coEvery { krxIndex.getKospi(any(), any()) } throws RuntimeException("Connection timeout")
            coEvery { krxIndex.getKosdaq(any(), any()) } throws RuntimeException("Connection timeout")

            // When
            val result = repository.getRecentMarketIndices(days = 30, markets = listOf("KOSPI"))

            // Then
            assertTrue(result.isFailure)
        }
    }

    // ============================================================
    // CancellationException rethrow
    // ============================================================

    @Test
    @DisplayName("getMarketIndices_whenCancelled_rethrowsCancellationException")
    fun `getMarketIndices_whenCancelled_rethrowsCancellationException`() = runTest {
        // Given
        coEvery { krxIndex.getKospi(any(), any()) } throws CancellationException("Cancelled")

        // When & Then
        var exceptionCaught: Throwable? = null
        try {
            repository.getMarketIndices("20260101", "20260101", listOf("KOSPI"))
        } catch (e: CancellationException) {
            exceptionCaught = e
        }
        assertNotNull(exceptionCaught, "CancellationException must be rethrown")
    }

    // ============================================================
    // Helper
    // ============================================================

    private fun createMockIndexOhlcv(date: String, close: Double, change: Double? = null): IndexOhlcv {
        val mock = mockk<IndexOhlcv>()
        every { mock.date } returns date
        every { mock.close } returns close
        every { mock.open } returns close - 10.0
        every { mock.high } returns close + 20.0
        every { mock.low } returns close - 15.0
        every { mock.volume } returns 1_000_000L
        every { mock.change } returns change
        return mock
    }
}
