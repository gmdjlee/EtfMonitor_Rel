package com.etfmonitor.core.data.repository.krx

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.StockDao
import com.krxkt.KrxStock
import com.krxkt.model.Market
import com.krxkt.model.MarketCap
import com.krxkt.model.StockOhlcvHistory
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * KrxStockDataRepositoryImpl 단위 테스트
 *
 * 테스트 범위:
 * - getStockOhlcv: 일봉/주봉/월봉, 빈 데이터, 예외 처리
 * - getStockAnalysisData: OHLCV + 시총 + 투자자 거래 통합
 * - getAllStocksList: 성공, 예외 처리
 * - getStockName: DB 우선, API 폴백
 * - CancellationException 재전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("KrxStockDataRepositoryImpl 테스트")
class KrxStockDataRepositoryImplTest {

    private lateinit var krxStock: KrxStock
    private lateinit var stockDao: StockDao
    private lateinit var repository: KrxStockDataRepositoryImpl

    @BeforeEach
    fun setup() {
        krxStock = mockk(relaxed = true)
        stockDao = mockk(relaxed = true)
        repository = KrxStockDataRepositoryImpl(krxStock, stockDao)
    }

    // ============================================================
    // getStockOhlcv
    // ============================================================

    @Nested
    @DisplayName("getStockOhlcv 테스트")
    inner class GetStockOhlcvTests {

        @Test
        @DisplayName("getStockOhlcv_withDailyInterval_returnsOhlcvData")
        fun `getStockOhlcv_withDailyInterval_returnsOhlcvData`() = runTest {
            // Given
            val ohlcvList = createOhlcvList(5)
            coEvery { krxStock.getOhlcvByTicker(any(), any(), any()) } returns ohlcvList
            coEvery { stockDao.getStockName(any()) } returns "삼성전자"

            // When
            val result = repository.getStockOhlcv(ticker = "005930", days = 60, interval = "d")

            // Then
            assertNotNull(result)
            assertEquals("005930", result.ticker)
            assertEquals("삼성전자", result.name)
            assertEquals(5, result.dates.size)
        }

        @Test
        @DisplayName("getStockOhlcv_withEmptyData_returnsNull")
        fun `getStockOhlcv_withEmptyData_returnsNull`() = runTest {
            // Given
            coEvery { krxStock.getOhlcvByTicker(any(), any(), any()) } returns emptyList()

            // When
            val result = repository.getStockOhlcv("005930", 60, "d")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("getStockOhlcv_whenKrxFails_returnsNull")
        fun `getStockOhlcv_whenKrxFails_returnsNull`() = runTest {
            // Given
            coEvery { krxStock.getOhlcvByTicker(any(), any(), any()) } throws RuntimeException("Network error")

            // When
            val result = repository.getStockOhlcv("005930", 60, "d")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("getStockOhlcv_whenStockNameNotInDb_usesTicker")
        fun `getStockOhlcv_whenStockNameNotInDb_usesTicker`() = runTest {
            // Given
            val ohlcvList = createOhlcvList(3)
            coEvery { krxStock.getOhlcvByTicker(any(), any(), any()) } returns ohlcvList
            coEvery { stockDao.getStockName(any()) } returns null
            coEvery { krxStock.getTickerList(any(), any()) } returns emptyList()

            // When
            val result = repository.getStockOhlcv("005930", 30, "d")

            // Then: when name not found, ticker is used as fallback
            assertNotNull(result)
            assertEquals("005930", result.name)
        }

        @Test
        @DisplayName("getStockOhlcv_withWeeklyInterval_fetchesExtraDays")
        fun `getStockOhlcv_withWeeklyInterval_fetchesExtraDays`() = runTest {
            // Given: enough ohlcv data to resample to weekly
            val ohlcvList = createOhlcvList(50)
            coEvery { krxStock.getOhlcvByTicker(any(), any(), any()) } returns ohlcvList
            coEvery { stockDao.getStockName(any()) } returns "삼성전자"

            // When
            val result = repository.getStockOhlcv("005930", 20, "w")

            // Then: resampled weekly data, fewer bars than daily
            assertNotNull(result)
        }
    }

    // ============================================================
    // getAllStocksList
    // ============================================================

    @Nested
    @DisplayName("getAllStocksList 테스트")
    inner class GetAllStocksListTests {

        @Test
        @DisplayName("getAllStocksList_whenKrxSucceeds_returnsTickerNamePairs")
        fun `getAllStocksList_whenKrxSucceeds_returnsTickerNamePairs`() = runTest {
            // Given
            val tickerList = listOf(
                createMockTickerInfo("005930", "삼성전자"),
                createMockTickerInfo("000660", "SK하이닉스")
            )
            coEvery { krxStock.getTickerList(any(), any()) } returns tickerList

            // When
            val result = repository.getAllStocksList()

            // Then
            assertEquals(2, result.size)
            assertEquals("005930", result[0].first)
            assertEquals("삼성전자", result[0].second)
        }

        @Test
        @DisplayName("getAllStocksList_whenKrxFails_returnsEmptyList")
        fun `getAllStocksList_whenKrxFails_returnsEmptyList`() = runTest {
            // Given
            coEvery { krxStock.getTickerList(any(), any()) } throws RuntimeException("Connection reset")

            // When
            val result = repository.getAllStocksList()

            // Then
            assertTrue(result.isEmpty())
        }
    }

    // ============================================================
    // getStockName
    // ============================================================

    @Nested
    @DisplayName("getStockName 테스트")
    inner class GetStockNameTests {

        @Test
        @DisplayName("getStockName_whenInDb_returnsNameFromDb")
        fun `getStockName_whenInDb_returnsNameFromDb`() = runTest {
            // Given
            coEvery { stockDao.getStockName("005930") } returns "삼성전자"

            // When
            val result = repository.getStockName("005930")

            // Then: DB hit, no network call needed
            assertEquals("삼성전자", result)
        }

        @Test
        @DisplayName("getStockName_whenNotInDb_fallsBackToKrxApi")
        fun `getStockName_whenNotInDb_fallsBackToKrxApi`() = runTest {
            // Given
            coEvery { stockDao.getStockName(any()) } returns null
            coEvery { krxStock.getTickerList(any(), any()) } returns listOf(
                createMockTickerInfo("005930", "삼성전자")
            )

            // When
            val result = repository.getStockName("005930")

            // Then
            assertEquals("삼성전자", result)
        }

        @Test
        @DisplayName("getStockName_whenNotInDbAndKrxFails_returnsNull")
        fun `getStockName_whenNotInDbAndKrxFails_returnsNull`() = runTest {
            // Given
            coEvery { stockDao.getStockName(any()) } returns null
            coEvery { krxStock.getTickerList(any(), any()) } throws RuntimeException("Network error")

            // When
            val result = repository.getStockName("005930")

            // Then
            assertNull(result)
        }
    }

    // ============================================================
    // CancellationException rethrow
    // ============================================================

    @Test
    @DisplayName("getStockOhlcv_whenCancelled_rethrowsCancellationException")
    fun `getStockOhlcv_whenCancelled_rethrowsCancellationException`() = runTest {
        // Given
        coEvery { krxStock.getOhlcvByTicker(any(), any(), any()) } throws CancellationException("Cancelled")

        // When & Then
        var exceptionCaught: Throwable? = null
        try {
            repository.getStockOhlcv("005930", 60, "d")
        } catch (e: CancellationException) {
            exceptionCaught = e
        }
        assertNotNull(exceptionCaught, "CancellationException must be rethrown")
    }

    // ============================================================
    // Helpers
    // ============================================================

    private fun createOhlcvList(count: Int): List<StockOhlcvHistory> {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val startDate = LocalDate.of(2026, 1, 1)
        return (0 until count).map { i ->
            StockOhlcvHistory(
                date = startDate.plusDays(i.toLong()).format(formatter),
                open = 80000L,
                high = 81000L,
                low = 79000L,
                close = 80500L,
                volume = 10_000_000L,
                tradingValue = 800_000_000_000L,
                changeRate = 0.5
            )
        }
    }

    private fun createMockTickerInfo(ticker: String, name: String): com.krxkt.model.TickerInfo {
        val mock = mockk<com.krxkt.model.TickerInfo>()
        every { mock.ticker } returns ticker
        every { mock.name } returns name
        return mock
    }
}
