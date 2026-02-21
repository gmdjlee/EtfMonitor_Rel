package com.etfmonitor.core.domain.usecase.krx

import com.etfmonitor.core.analysis.model.StockOhlcvData
import com.etfmonitor.core.domain.repository.StockDataRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * GetStockOhlcvUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 성공 경로: 일별/주별/월별 OHLCV 반환
 * - invoke() 실패 경로: null 반환
 * - 기본 파라미터(days=180, interval="d") 검증
 * - getChangeRates() 계산 검증
 * - takeLast() 추출 검증
 * - 빈 데이터 처리
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("GetStockOhlcvUseCase 테스트")
class GetStockOhlcvUseCaseTest {

    private lateinit var stockDataRepository: StockDataRepository
    private lateinit var useCase: GetStockOhlcvUseCase

    @BeforeEach
    fun setUp() {
        stockDataRepository = mockk()
        useCase = GetStockOhlcvUseCase(stockDataRepository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withValidTicker_returnsStockOhlcvData")
        fun `invoke_withValidTicker_returnsStockOhlcvData`() = runTest {
            // Given
            val ticker = "005930"
            val ohlcvData = createStockOhlcvData(ticker, 10)
            coEvery { stockDataRepository.getStockOhlcv(ticker, 180, "d") } returns ohlcvData

            // When
            val result = useCase(ticker)

            // Then
            assertNotNull(result)
            assertEquals(ticker, result.ticker)
            assertEquals(10, result.dates.size)
        }

        @Test
        @DisplayName("invoke_withWeeklyInterval_delegatesWeeklyToRepository")
        fun `invoke_withWeeklyInterval_delegatesWeeklyToRepository`() = runTest {
            // Given
            val ticker = "005930"
            val weeklyData = createStockOhlcvData(ticker, 52)
            coEvery { stockDataRepository.getStockOhlcv(ticker, 365, "w") } returns weeklyData

            // When
            val result = useCase(ticker, days = 365, interval = "w")

            // Then
            assertNotNull(result)
            assertEquals(52, result.dates.size)
            coVerify(exactly = 1) { stockDataRepository.getStockOhlcv(ticker, 365, "w") }
        }

        @Test
        @DisplayName("invoke_withMonthlyInterval_delegatesMonthlyToRepository")
        fun `invoke_withMonthlyInterval_delegatesMonthlyToRepository`() = runTest {
            // Given
            val ticker = "000660"
            val monthlyData = createStockOhlcvData(ticker, 24)
            coEvery { stockDataRepository.getStockOhlcv(ticker, 730, "m") } returns monthlyData

            // When
            val result = useCase(ticker, days = 730, interval = "m")

            // Then
            assertNotNull(result)
            coVerify(exactly = 1) { stockDataRepository.getStockOhlcv(ticker, 730, "m") }
        }

        @Test
        @DisplayName("invoke_withDefaultParams_usesDays180AndDailyInterval")
        fun `invoke_withDefaultParams_usesDays180AndDailyInterval`() = runTest {
            // Given
            val ticker = "035420"
            coEvery { stockDataRepository.getStockOhlcv(ticker, 180, "d") } returns null

            // When
            useCase(ticker)

            // Then: 기본값 days=180, interval="d" 사용
            coVerify(exactly = 1) { stockDataRepository.getStockOhlcv(ticker, 180, "d") }
        }
    }

    // ================================================================
    // null 반환 처리 테스트
    // ================================================================

    @Nested
    @DisplayName("null 반환 처리 테스트")
    inner class NullReturnTests {

        @Test
        @DisplayName("invoke_whenRepositoryReturnsNull_returnsNull")
        fun `invoke_whenRepositoryReturnsNull_returnsNull`() = runTest {
            // Given
            coEvery { stockDataRepository.getStockOhlcv(any(), any(), any()) } returns null

            // When
            val result = useCase("999999")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("invoke_withUnknownTicker_returnsNull")
        fun `invoke_withUnknownTicker_returnsNull`() = runTest {
            // Given
            coEvery { stockDataRepository.getStockOhlcv("INVALID", any(), any()) } returns null

            // When
            val result = useCase("INVALID")

            // Then
            assertNull(result)
        }
    }

    // ================================================================
    // StockOhlcvData 기능 테스트
    // ================================================================

    @Nested
    @DisplayName("StockOhlcvData 기능 테스트")
    inner class StockOhlcvDataFunctionTests {

        @Test
        @DisplayName("getChangeRates_withMultipleCloses_calculatesCorrectRates")
        fun `getChangeRates_withMultipleCloses_calculatesCorrectRates`() = runTest {
            // Given: close 가격이 100 → 110 → 99 → 110
            val data = StockOhlcvData(
                ticker = "005930",
                name = "삼성전자",
                dates = listOf("2026-02-17", "2026-02-18", "2026-02-19", "2026-02-20"),
                open = listOf(99.0, 105.0, 108.0, 97.0),
                high = listOf(110.0, 112.0, 115.0, 112.0),
                low = listOf(98.0, 103.0, 98.0, 95.0),
                close = listOf(100.0, 110.0, 99.0, 110.0),
                volume = listOf(1000L, 1200L, 900L, 1100L)
            )
            coEvery { stockDataRepository.getStockOhlcv("005930", 180, "d") } returns data

            // When
            val result = useCase("005930")!!
            val changeRates = result.getChangeRates()

            // Then
            assertEquals(4, changeRates.size)
            assertEquals(0.0, changeRates[0], 1e-9)          // 첫 번째는 0.0
            assertEquals(10.0, changeRates[1], 1e-9)         // (110-100)/100 * 100 = 10%
            assertEquals(-10.0, changeRates[2], 1e-6)        // (99-110)/110 * 100 ≈ -10%
        }

        @Test
        @DisplayName("takeLast_withNDays_extractsLastNRows")
        fun `takeLast_withNDays_extractsLastNRows`() = runTest {
            // Given
            val data = createStockOhlcvData("005930", 30)
            coEvery { stockDataRepository.getStockOhlcv("005930", 180, "d") } returns data

            // When
            val result = useCase("005930")!!
            val last10 = result.takeLast(10)

            // Then
            assertEquals(10, last10.dates.size)
            assertEquals(10, last10.close.size)
            assertEquals(result.dates.takeLast(10), last10.dates)
        }

        @Test
        @DisplayName("getChangeRates_withSingleElement_returnsEmptyList")
        fun `getChangeRates_withSingleElement_returnsEmptyList`() = runTest {
            // Given: 단일 날짜 데이터
            val data = StockOhlcvData(
                ticker = "005930",
                name = "삼성전자",
                dates = listOf("2026-02-19"),
                open = listOf(70000.0),
                high = listOf(71000.0),
                low = listOf(69000.0),
                close = listOf(70500.0),
                volume = listOf(500_000L)
            )
            coEvery { stockDataRepository.getStockOhlcv("005930", 180, "d") } returns data

            // When
            val result = useCase("005930")!!
            val changeRates = result.getChangeRates()

            // Then: close < 2이므로 빈 리스트 반환
            assertTrue(changeRates.isEmpty())
        }
    }

    // ================================================================
    // 헬퍼 함수
    // ================================================================

    private fun createStockOhlcvData(ticker: String, count: Int): StockOhlcvData {
        val dates = (1..count).map { "2026-01-${it.toString().padStart(2, '0')}" }
        val prices = (1..count).map { 70000.0 + it * 100 }
        return StockOhlcvData(
            ticker = ticker,
            name = "테스트종목$ticker",
            dates = dates,
            open = prices.map { it - 200 },
            high = prices.map { it + 300 },
            low = prices.map { it - 300 },
            close = prices,
            volume = List(count) { 500_000L }
        )
    }
}
