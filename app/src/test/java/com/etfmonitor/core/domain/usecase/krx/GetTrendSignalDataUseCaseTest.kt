package com.etfmonitor.core.domain.usecase.krx

import com.etfmonitor.core.analysis.model.TrendSignalData
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
 * GetTrendSignalDataUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 성공 경로: TrendSignalData 반환
 * - 기본 파라미터(days=365, interval="w") 검증
 * - null 반환 처리
 * - buySignal / sellSignal 데이터 검증
 * - cmf / ma 필드 존재 검증
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("GetTrendSignalDataUseCase 테스트")
class GetTrendSignalDataUseCaseTest {

    private lateinit var stockDataRepository: StockDataRepository
    private lateinit var useCase: GetTrendSignalDataUseCase

    @BeforeEach
    fun setUp() {
        stockDataRepository = mockk()
        useCase = GetTrendSignalDataUseCase(stockDataRepository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withValidTicker_returnsTrendSignalData")
        fun `invoke_withValidTicker_returnsTrendSignalData`() = runTest {
            // Given
            val ticker = "005930"
            val data = createTrendSignalData(ticker, 52)
            coEvery { stockDataRepository.getTrendSignalData(ticker, 365, "w") } returns data

            // When
            val result = useCase(ticker)

            // Then
            assertNotNull(result)
            assertEquals(ticker, result.ticker)
            assertEquals(52, result.dates.size)
        }

        @Test
        @DisplayName("invoke_withDefaultParams_usesWeeklyIntervalAnd365Days")
        fun `invoke_withDefaultParams_usesWeeklyIntervalAnd365Days`() = runTest {
            // Given
            val ticker = "000660"
            coEvery { stockDataRepository.getTrendSignalData(ticker, 365, "w") } returns null

            // When
            useCase(ticker)

            // Then: 기본값 days=365, interval="w" 사용
            coVerify(exactly = 1) { stockDataRepository.getTrendSignalData(ticker, 365, "w") }
        }

        @Test
        @DisplayName("invoke_withDailyInterval_delegatesDailyToRepository")
        fun `invoke_withDailyInterval_delegatesDailyToRepository`() = runTest {
            // Given
            val ticker = "035420"
            val data = createTrendSignalData(ticker, 30)
            coEvery { stockDataRepository.getTrendSignalData(ticker, 180, "d") } returns data

            // When
            val result = useCase(ticker, days = 180, interval = "d")

            // Then
            assertNotNull(result)
            coVerify(exactly = 1) { stockDataRepository.getTrendSignalData(ticker, 180, "d") }
        }

        @Test
        @DisplayName("invoke_withValidData_buyAndSellSignalsAreZeroOrOne")
        fun `invoke_withValidData_buyAndSellSignalsAreZeroOrOne`() = runTest {
            // Given
            val ticker = "005930"
            val data = createTrendSignalData(ticker, 20)
            coEvery { stockDataRepository.getTrendSignalData(ticker, 365, "w") } returns data

            // When
            val result = useCase(ticker)!!

            // Then
            result.buySignal.forEachIndexed { i, v ->
                assertTrue(v in 0..1, "buySignal[$i]=$v must be 0 or 1")
            }
            result.sellSignal.forEachIndexed { i, v ->
                assertTrue(v in 0..1, "sellSignal[$i]=$v must be 0 or 1")
            }
        }

        @Test
        @DisplayName("invoke_withValidData_allListsSameSize")
        fun `invoke_withValidData_allListsSameSize`() = runTest {
            // Given
            val ticker = "005930"
            val count = 52
            val data = createTrendSignalData(ticker, count)
            coEvery { stockDataRepository.getTrendSignalData(ticker, 365, "w") } returns data

            // When
            val result = useCase(ticker)!!

            // Then: 모든 리스트 크기가 동일해야 함
            assertEquals(count, result.dates.size)
            assertEquals(count, result.close.size)
            assertEquals(count, result.ma.size)
            assertEquals(count, result.cmf.size)
            assertEquals(count, result.buySignal.size)
            assertEquals(count, result.sellSignal.size)
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
            coEvery { stockDataRepository.getTrendSignalData(any(), any(), any()) } returns null

            // When
            val result = useCase("005930")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("invoke_withInvalidTicker_returnsNull")
        fun `invoke_withInvalidTicker_returnsNull`() = runTest {
            // Given
            coEvery { stockDataRepository.getTrendSignalData("INVALID", 365, "w") } returns null

            // When
            val result = useCase("INVALID")

            // Then
            assertNull(result)
        }
    }

    // ================================================================
    // 에지 케이스 테스트
    // ================================================================

    @Nested
    @DisplayName("에지 케이스 테스트")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("invoke_withEmptyTicker_delegatesAndReturnsNull")
        fun `invoke_withEmptyTicker_delegatesAndReturnsNull`() = runTest {
            // Given
            coEvery { stockDataRepository.getTrendSignalData("", 365, "w") } returns null

            // When
            val result = useCase("")

            // Then
            assertNull(result)
            coVerify(exactly = 1) { stockDataRepository.getTrendSignalData("", 365, "w") }
        }

        @Test
        @DisplayName("invoke_withZeroDays_delegatesAndReturnsNull")
        fun `invoke_withZeroDays_delegatesAndReturnsNull`() = runTest {
            // Given
            coEvery { stockDataRepository.getTrendSignalData("005930", 0, "w") } returns null

            // When
            val result = useCase("005930", days = 0)

            // Then
            assertNull(result)
        }
    }

    // ================================================================
    // 헬퍼 함수
    // ================================================================

    private fun createTrendSignalData(ticker: String, count: Int): TrendSignalData {
        return TrendSignalData(
            ticker = ticker,
            name = "테스트종목$ticker",
            interval = "w",
            dates = (1..count).map { "2026-${(it / 5 + 1).toString().padStart(2, '0')}-01" },
            open = List(count) { 70000.0 },
            high = List(count) { 71000.0 },
            low = List(count) { 69000.0 },
            close = List(count) { 70500.0 },
            volume = List(count) { 500_000L },
            ma = List(count) { 70000.0 },
            cmf = List(count) { 0.1 },
            fearGreed = List(count) { 0.0 },
            buySignal = List(count) { 0 },
            auxBuySignal = List(count) { 0 },
            sellSignal = List(count) { 0 },
            auxSellSignal = List(count) { 0 }
        )
    }
}
