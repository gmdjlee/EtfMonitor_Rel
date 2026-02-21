package com.etfmonitor.core.domain.usecase.krx

import com.etfmonitor.core.analysis.model.ElderImpulseData
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
 * GetElderImpulseDataUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 성공 경로: ElderImpulseData 반환
 * - 기본 파라미터(days=365, interval="w") 검증
 * - null 반환 처리
 * - impulse 값 범위 검증 (-1, 0, 1)
 * - ema / macd / macdSignal / macdHist 필드 검증
 * - marketCap 필드 포함 여부
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("GetElderImpulseDataUseCase 테스트")
class GetElderImpulseDataUseCaseTest {

    private lateinit var stockDataRepository: StockDataRepository
    private lateinit var useCase: GetElderImpulseDataUseCase

    @BeforeEach
    fun setUp() {
        stockDataRepository = mockk()
        useCase = GetElderImpulseDataUseCase(stockDataRepository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withValidTicker_returnsElderImpulseData")
        fun `invoke_withValidTicker_returnsElderImpulseData`() = runTest {
            // Given
            val ticker = "005930"
            val data = createElderImpulseData(ticker, 52)
            coEvery { stockDataRepository.getElderImpulseData(ticker, 365, "w") } returns data

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
            coEvery { stockDataRepository.getElderImpulseData(ticker, 365, "w") } returns null

            // When
            useCase(ticker)

            // Then
            coVerify(exactly = 1) { stockDataRepository.getElderImpulseData(ticker, 365, "w") }
        }

        @Test
        @DisplayName("invoke_withDailyInterval_delegatesDailyToRepository")
        fun `invoke_withDailyInterval_delegatesDailyToRepository`() = runTest {
            // Given
            val ticker = "035420"
            val data = createElderImpulseData(ticker, 180)
            coEvery { stockDataRepository.getElderImpulseData(ticker, 180, "d") } returns data

            // When
            val result = useCase(ticker, days = 180, interval = "d")

            // Then
            assertNotNull(result)
            coVerify(exactly = 1) { stockDataRepository.getElderImpulseData(ticker, 180, "d") }
        }

        @Test
        @DisplayName("invoke_withValidData_impulseValuesAreInValidRange")
        fun `invoke_withValidData_impulseValuesAreInValidRange`() = runTest {
            // Given: impulse 값은 -1, 0, 1 중 하나여야 함
            val ticker = "005930"
            val data = createElderImpulseData(ticker, 20, impulses = listOf(1, -1, 0, 1, -1, 0, 1, 0, -1, 1,
                1, -1, 0, 1, -1, 0, 1, 0, -1, 1))
            coEvery { stockDataRepository.getElderImpulseData(ticker, 365, "w") } returns data

            // When
            val result = useCase(ticker)!!

            // Then
            result.impulse.forEachIndexed { i, v ->
                assertTrue(v in listOf(-1, 0, 1), "impulse[$i]=$v must be -1, 0, or 1")
            }
        }

        @Test
        @DisplayName("invoke_withValidData_allListsSameSize")
        fun `invoke_withValidData_allListsSameSize`() = runTest {
            // Given
            val ticker = "005930"
            val count = 52
            val data = createElderImpulseData(ticker, count)
            coEvery { stockDataRepository.getElderImpulseData(ticker, 365, "w") } returns data

            // When
            val result = useCase(ticker)!!

            // Then: 모든 리스트 크기가 동일해야 함
            assertEquals(count, result.dates.size)
            assertEquals(count, result.close.size)
            assertEquals(count, result.ema.size)
            assertEquals(count, result.macd.size)
            assertEquals(count, result.macdSignal.size)
            assertEquals(count, result.macdHist.size)
            assertEquals(count, result.impulse.size)
        }

        @Test
        @DisplayName("invoke_withBullImpulse_returnsCorrectSignal")
        fun `invoke_withBullImpulse_returnsCorrectSignal`() = runTest {
            // Given: 모든 impulse = 1 (bull)
            val ticker = "005930"
            val data = createElderImpulseData(ticker, 5, impulses = List(5) { 1 })
            coEvery { stockDataRepository.getElderImpulseData(ticker, 365, "w") } returns data

            // When
            val result = useCase(ticker)!!

            // Then
            assertTrue(result.impulse.all { it == 1 })
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
            coEvery { stockDataRepository.getElderImpulseData(any(), any(), any()) } returns null

            // When
            val result = useCase("005930")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("invoke_withUnknownTicker_returnsNull")
        fun `invoke_withUnknownTicker_returnsNull`() = runTest {
            // Given
            coEvery { stockDataRepository.getElderImpulseData("UNKNOWN", 365, "w") } returns null

            // When
            val result = useCase("UNKNOWN")

            // Then
            assertNull(result)
        }
    }

    // ================================================================
    // 헬퍼 함수
    // ================================================================

    private fun createElderImpulseData(
        ticker: String,
        count: Int,
        impulses: List<Int>? = null
    ): ElderImpulseData {
        val validImpulses = impulses ?: List(count) { 0 }
        return ElderImpulseData(
            ticker = ticker,
            name = "테스트종목$ticker",
            interval = "w",
            dates = (1..count).map { "2026-01-${(it % 28 + 1).toString().padStart(2, '0')}" },
            close = List(count) { 70000.0 + it * 100 },
            marketCap = List(count) { 400_000_000_000_000L },
            ema = List(count) { 70000.0 + it * 50 },
            macd = List(count) { 0.5 * it },
            macdSignal = List(count) { 0.3 * it },
            macdHist = List(count) { 0.2 * it },
            impulse = validImpulses
        )
    }
}
