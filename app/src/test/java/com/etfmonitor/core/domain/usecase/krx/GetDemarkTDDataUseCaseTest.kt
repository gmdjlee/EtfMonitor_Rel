package com.etfmonitor.core.domain.usecase.krx

import com.etfmonitor.core.analysis.model.DemarkTDData
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
 * GetDemarkTDDataUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 성공 경로: DemarkTDData 반환
 * - 기본 파라미터(days=365, interval="w") 검증
 * - null 반환 처리
 * - tdSell / tdBuy 카운트 값 검증
 * - 9+ 피로 신호 상태 검증 (TDSetupState)
 * - intervalName 필드 검증 (일봉/주봉/월봉)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("GetDemarkTDDataUseCase 테스트")
class GetDemarkTDDataUseCaseTest {

    private lateinit var stockDataRepository: StockDataRepository
    private lateinit var useCase: GetDemarkTDDataUseCase

    @BeforeEach
    fun setUp() {
        stockDataRepository = mockk()
        useCase = GetDemarkTDDataUseCase(stockDataRepository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withValidTicker_returnsDemarkTDData")
        fun `invoke_withValidTicker_returnsDemarkTDData`() = runTest {
            // Given
            val ticker = "005930"
            val data = createDemarkTDData(ticker, 52, "w", "주봉")
            coEvery { stockDataRepository.getDemarkTDData(ticker, 365, "w") } returns data

            // When
            val result = useCase(ticker)

            // Then
            assertNotNull(result)
            assertEquals(ticker, result.ticker)
            assertEquals("w", result.interval)
            assertEquals("주봉", result.intervalName)
        }

        @Test
        @DisplayName("invoke_withDefaultParams_usesWeeklyIntervalAnd365Days")
        fun `invoke_withDefaultParams_usesWeeklyIntervalAnd365Days`() = runTest {
            // Given
            val ticker = "000660"
            coEvery { stockDataRepository.getDemarkTDData(ticker, 365, "w") } returns null

            // When
            useCase(ticker)

            // Then: 기본값 days=365, interval="w" 사용
            coVerify(exactly = 1) { stockDataRepository.getDemarkTDData(ticker, 365, "w") }
        }

        @Test
        @DisplayName("invoke_withMonthlyInterval_delegatesMonthlyToRepository")
        fun `invoke_withMonthlyInterval_delegatesMonthlyToRepository`() = runTest {
            // Given
            val ticker = "035420"
            val data = createDemarkTDData(ticker, 24, "m", "월봉")
            coEvery { stockDataRepository.getDemarkTDData(ticker, 730, "m") } returns data

            // When
            val result = useCase(ticker, days = 730, interval = "m")

            // Then
            assertNotNull(result)
            assertEquals("m", result.interval)
            assertEquals("월봉", result.intervalName)
            coVerify(exactly = 1) { stockDataRepository.getDemarkTDData(ticker, 730, "m") }
        }

        @Test
        @DisplayName("invoke_withValidData_allListsSameSize")
        fun `invoke_withValidData_allListsSameSize`() = runTest {
            // Given
            val ticker = "005930"
            val count = 52
            val data = createDemarkTDData(ticker, count, "w", "주봉")
            coEvery { stockDataRepository.getDemarkTDData(ticker, 365, "w") } returns data

            // When
            val result = useCase(ticker)!!

            // Then: 모든 리스트 크기가 동일해야 함
            assertEquals(count, result.dates.size)
            assertEquals(count, result.close.size)
            assertEquals(count, result.tdSell.size)
            assertEquals(count, result.tdBuy.size)
        }

        @Test
        @DisplayName("invoke_withSellFatigueSignal_tdSellReaches9")
        fun `invoke_withSellFatigueSignal_tdSellReaches9`() = runTest {
            // Given: tdSell이 9에 도달하는 시나리오 (매도 피로)
            val ticker = "005930"
            val tdSell = listOf(0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 9, 0)
            val tdBuy = List(15) { 0 }
            val data = createDemarkTDData(ticker, 15, "w", "주봉", tdSell = tdSell, tdBuy = tdBuy)
            coEvery { stockDataRepository.getDemarkTDData(ticker, 365, "w") } returns data

            // When
            val result = useCase(ticker)!!

            // Then: tdSell이 9에 도달한 항목 존재
            assertTrue(result.tdSell.any { it >= 9 })
        }

        @Test
        @DisplayName("invoke_withBuyFatigueSignal_tdBuyReaches9")
        fun `invoke_withBuyFatigueSignal_tdBuyReaches9`() = runTest {
            // Given: tdBuy가 9에 도달하는 시나리오 (매수 피로)
            val ticker = "005930"
            val tdBuy = listOf(0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 9, 0)
            val tdSell = List(15) { 0 }
            val data = createDemarkTDData(ticker, 15, "w", "주봉", tdSell = tdSell, tdBuy = tdBuy)
            coEvery { stockDataRepository.getDemarkTDData(ticker, 365, "w") } returns data

            // When
            val result = useCase(ticker)!!

            // Then
            assertTrue(result.tdBuy.any { it >= 9 })
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
            coEvery { stockDataRepository.getDemarkTDData(any(), any(), any()) } returns null

            // When
            val result = useCase("005930")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("invoke_withUnknownTicker_returnsNull")
        fun `invoke_withUnknownTicker_returnsNull`() = runTest {
            // Given
            coEvery { stockDataRepository.getDemarkTDData("UNKNOWN", 365, "w") } returns null

            // When
            val result = useCase("UNKNOWN")

            // Then
            assertNull(result)
        }
    }

    // ================================================================
    // 헬퍼 함수
    // ================================================================

    private fun createDemarkTDData(
        ticker: String,
        count: Int,
        interval: String,
        intervalName: String,
        tdSell: List<Int>? = null,
        tdBuy: List<Int>? = null
    ): DemarkTDData {
        return DemarkTDData(
            ticker = ticker,
            name = "테스트종목$ticker",
            interval = interval,
            intervalName = intervalName,
            dates = (1..count).map { "2026-01-${(it % 28 + 1).toString().padStart(2, '0')}" },
            close = List(count) { 70000.0 + it * 100 },
            marketCap = List(count) { 400_000_000_000_000L },
            tdSell = tdSell ?: List(count) { 0 },
            tdBuy = tdBuy ?: List(count) { 0 }
        )
    }
}
