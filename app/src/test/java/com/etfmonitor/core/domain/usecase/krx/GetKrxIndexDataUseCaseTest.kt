package com.etfmonitor.core.domain.usecase.krx

import com.etfmonitor.core.data.repository.krx.KrxIndexRepositoryImpl
import com.etfmonitor.core.database.entities.MarketIndex
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
import kotlin.test.assertTrue

/**
 * GetKrxIndexDataUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 성공 경로: KOSPI + KOSDAQ 기간 조회
 * - 기본 markets 파라미터 ("KOSPI", "KOSDAQ")
 * - getRecentDays() 성공 경로
 * - 빈 결과 처리
 * - 실패 Result 전파
 * - 단일 시장 조회
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("GetKrxIndexDataUseCase 테스트")
class GetKrxIndexDataUseCaseTest {

    private lateinit var repository: KrxIndexRepositoryImpl
    private lateinit var useCase: GetKrxIndexDataUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = GetKrxIndexDataUseCase(repository)
    }

    // ================================================================
    // invoke() 기간 조회 테스트
    // ================================================================

    @Nested
    @DisplayName("invoke() 기간 조회 테스트")
    inner class InvokeByDateRangeTests {

        @Test
        @DisplayName("invoke_withDateRange_returnsMarketIndexList")
        fun `invoke_withDateRange_returnsMarketIndexList`() = runTest {
            // Given
            val startDate = "20260101"
            val endDate = "20260219"
            val markets = listOf("KOSPI", "KOSDAQ")
            val indices = listOf(
                createMarketIndex("KOSPI", "2026-01-01", 2800.0),
                createMarketIndex("KOSDAQ", "2026-01-01", 850.0)
            )
            coEvery { repository.getMarketIndices(startDate, endDate, markets) } returns Result.success(indices)

            // When
            val result = useCase(startDate, endDate, markets)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(2, result.getOrNull()?.size)
            assertTrue(result.getOrNull()?.any { it.market == "KOSPI" } == true)
            assertTrue(result.getOrNull()?.any { it.market == "KOSDAQ" } == true)
        }

        @Test
        @DisplayName("invoke_withDefaultMarkets_usesKospiAndKosdaq")
        fun `invoke_withDefaultMarkets_usesKospiAndKosdaq`() = runTest {
            // Given
            val startDate = "20260101"
            val endDate = "20260219"
            coEvery { repository.getMarketIndices(startDate, endDate, listOf("KOSPI", "KOSDAQ")) } returns Result.success(emptyList())

            // When
            useCase(startDate, endDate)  // 기본 markets = ["KOSPI", "KOSDAQ"]

            // Then
            coVerify(exactly = 1) { repository.getMarketIndices(startDate, endDate, listOf("KOSPI", "KOSDAQ")) }
        }

        @Test
        @DisplayName("invoke_withSingleMarket_returnsOnlyThatMarket")
        fun `invoke_withSingleMarket_returnsOnlyThatMarket`() = runTest {
            // Given
            val startDate = "20260101"
            val endDate = "20260219"
            val markets = listOf("KOSPI")
            val indices = listOf(createMarketIndex("KOSPI", "2026-01-01", 2800.0))
            coEvery { repository.getMarketIndices(startDate, endDate, markets) } returns Result.success(indices)

            // When
            val result = useCase(startDate, endDate, markets)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrNull()?.size)
            assertEquals("KOSPI", result.getOrNull()?.first()?.market)
        }

        @Test
        @DisplayName("invoke_withEmptyResult_returnsSuccessWithEmptyList")
        fun `invoke_withEmptyResult_returnsSuccessWithEmptyList`() = runTest {
            // Given
            coEvery { repository.getMarketIndices(any(), any(), any()) } returns Result.success(emptyList())

            // When
            val result = useCase("20260101", "20260105")

            // Then
            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()?.isEmpty() == true)
        }

        @Test
        @DisplayName("invoke_whenRepositoryFails_returnsFailure")
        fun `invoke_whenRepositoryFails_returnsFailure`() = runTest {
            // Given
            val exception = RuntimeException("KRX 지수 조회 실패")
            coEvery { repository.getMarketIndices(any(), any(), any()) } returns Result.failure(exception)

            // When
            val result = useCase("20260101", "20260219")

            // Then
            assertTrue(result.isFailure)
            assertEquals(exception.message, result.exceptionOrNull()?.message)
        }
    }

    // ================================================================
    // getRecentDays() 최근 N일 조회 테스트
    // ================================================================

    @Nested
    @DisplayName("getRecentDays() 최근 N일 조회 테스트")
    inner class GetRecentDaysTests {

        @Test
        @DisplayName("getRecentDays_withPositiveDays_delegatesToRepository")
        fun `getRecentDays_withPositiveDays_delegatesToRepository`() = runTest {
            // Given
            val days = 30
            val indices = (1..30).map { i ->
                createMarketIndex("KOSPI", "2026-01-${i.toString().padStart(2, '0')}", 2800.0 + i)
            }
            coEvery { repository.getRecentMarketIndices(days, any()) } returns Result.success(indices)

            // When
            val result = useCase.getRecentDays(days)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(30, result.getOrNull()?.size)
        }

        @Test
        @DisplayName("getRecentDays_withDefaultMarkets_usesKospiAndKosdaq")
        fun `getRecentDays_withDefaultMarkets_usesKospiAndKosdaq`() = runTest {
            // Given
            coEvery { repository.getRecentMarketIndices(30, listOf("KOSPI", "KOSDAQ")) } returns Result.success(emptyList())

            // When
            useCase.getRecentDays(30)

            // Then
            coVerify(exactly = 1) { repository.getRecentMarketIndices(30, listOf("KOSPI", "KOSDAQ")) }
        }

        @Test
        @DisplayName("getRecentDays_whenFails_returnsFailure")
        fun `getRecentDays_whenFails_returnsFailure`() = runTest {
            // Given
            coEvery { repository.getRecentMarketIndices(any(), any()) } returns
                    Result.failure(RuntimeException("조회 실패"))

            // When
            val result = useCase.getRecentDays(30)

            // Then
            assertTrue(result.isFailure)
        }
    }

    // ================================================================
    // MarketIndex 데이터 검증 테스트
    // ================================================================

    @Nested
    @DisplayName("MarketIndex 데이터 검증 테스트")
    inner class MarketIndexDataTests {

        @Test
        @DisplayName("invoke_withValidData_marketIndexHasCorrectFields")
        fun `invoke_withValidData_marketIndexHasCorrectFields`() = runTest {
            // Given
            val indices = listOf(
                MarketIndex(
                    id = "KOSPI-2026-02-19",
                    market = "KOSPI",
                    date = "2026-02-19",
                    closePrice = 2850.75,
                    openPrice = 2820.0,
                    highPrice = 2870.0,
                    lowPrice = 2810.0,
                    volume = 500_000_000L,
                    changeRate = 1.05
                )
            )
            coEvery { repository.getMarketIndices(any(), any(), any()) } returns Result.success(indices)

            // When
            val result = useCase("20260219", "20260219")

            // Then
            assertTrue(result.isSuccess)
            val index = result.getOrNull()!!.first()
            assertEquals("KOSPI", index.market)
            assertEquals("2026-02-19", index.date)
            assertEquals(2850.75, index.closePrice)
        }
    }

    // ================================================================
    // 헬퍼 함수
    // ================================================================

    private fun createMarketIndex(
        market: String,
        date: String,
        closePrice: Double
    ) = MarketIndex(
        id = "$market-$date",
        market = market,
        date = date,
        closePrice = closePrice,
        openPrice = closePrice - 20.0,
        highPrice = closePrice + 30.0,
        lowPrice = closePrice - 30.0,
        volume = 1_000_000_000L,
        changeRate = 0.5
    )
}
