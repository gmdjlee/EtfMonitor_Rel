package com.etfmonitor.core.domain.usecase.krx

import com.etfmonitor.core.data.repository.krx.KrxStockRepositoryImpl
import com.krxkt.model.Market
import com.krxkt.model.MarketCap
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * GetKrxMarketDataUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 성공 경로: KOSPI + KOSDAQ 집계
 * - Fail-fast 동작: 첫 번째 마켓 오류 시 즉시 실패
 * - 기본 markets 파라미터 (KOSPI, KOSDAQ)
 * - 단일 마켓 조회
 * - 빈 결과 처리
 * - 두 번째 마켓 오류 시도 Fail-fast
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("GetKrxMarketDataUseCase 테스트")
class GetKrxMarketDataUseCaseTest {

    private lateinit var krxStockRepository: KrxStockRepositoryImpl
    private lateinit var useCase: GetKrxMarketDataUseCase

    @BeforeEach
    fun setUp() {
        krxStockRepository = mockk()
        useCase = GetKrxMarketDataUseCase(krxStockRepository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withDefaultMarkets_returnsBothKospiAndKosdaq")
        fun `invoke_withDefaultMarkets_returnsBothKospiAndKosdaq`() = runTest {
            // Given
            val date = "20260219"
            val kospiData = listOf(createMarketCap("005930", 400_000_000_000_000L))
            val kosdaqData = listOf(createMarketCap("247540", 20_000_000_000_000L))
            coEvery { krxStockRepository.getMarketCap(date, Market.KOSPI) } returns Result.success(kospiData)
            coEvery { krxStockRepository.getMarketCap(date, Market.KOSDAQ) } returns Result.success(kosdaqData)

            // When
            val result = useCase(date)

            // Then
            assertTrue(result.isSuccess)
            val map = result.getOrNull()!!
            assertEquals(2, map.size)
            assertTrue(map.containsKey(Market.KOSPI))
            assertTrue(map.containsKey(Market.KOSDAQ))
            assertEquals(1, map[Market.KOSPI]?.size)
            assertEquals(1, map[Market.KOSDAQ]?.size)
        }

        @Test
        @DisplayName("invoke_withSingleKospiMarket_returnsKospiOnly")
        fun `invoke_withSingleKospiMarket_returnsKospiOnly`() = runTest {
            // Given
            val date = "20260219"
            val kospiData = listOf(createMarketCap("005930", 400_000_000_000_000L))
            coEvery { krxStockRepository.getMarketCap(date, Market.KOSPI) } returns Result.success(kospiData)

            // When
            val result = useCase(date, markets = listOf(Market.KOSPI))

            // Then
            assertTrue(result.isSuccess)
            val map = result.getOrNull()!!
            assertEquals(1, map.size)
            assertTrue(map.containsKey(Market.KOSPI))
            assertFalse(map.containsKey(Market.KOSDAQ))
        }

        @Test
        @DisplayName("invoke_withAllMarket_returnsAllMarketData")
        fun `invoke_withAllMarket_returnsAllMarketData`() = runTest {
            // Given
            val date = "20260219"
            val allData = listOf(
                createMarketCap("005930", 400_000_000_000_000L),
                createMarketCap("247540", 20_000_000_000_000L)
            )
            coEvery { krxStockRepository.getMarketCap(date, Market.ALL) } returns Result.success(allData)

            // When
            val result = useCase(date, markets = listOf(Market.ALL))

            // Then
            assertTrue(result.isSuccess)
            val map = result.getOrNull()!!
            assertEquals(1, map.size)
            assertEquals(2, map[Market.ALL]?.size)
        }

        @Test
        @DisplayName("invoke_withEmptyMarkets_returnsEmptyMap")
        fun `invoke_withEmptyMarkets_returnsEmptyMap`() = runTest {
            // Given: 마켓 목록이 비어있으면 반복 없이 빈 맵 반환
            val date = "20260219"

            // When
            val result = useCase(date, markets = emptyList())

            // Then
            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()?.isEmpty() == true)
        }
    }

    // ================================================================
    // Fail-fast 동작 테스트 (W1 FIX 검증)
    // ================================================================

    @Nested
    @DisplayName("Fail-fast 동작 테스트")
    inner class FailFastTests {

        @Test
        @DisplayName("invoke_whenFirstMarketFails_returnsFailureWithoutCallingSecond")
        fun `invoke_whenFirstMarketFails_returnsFailureWithoutCallingSecond`() = runTest {
            // Given: KOSPI 실패 → 즉시 반환 (KOSDAQ 호출 안 함)
            val date = "20260219"
            val exception = RuntimeException("KOSPI 조회 실패")
            coEvery { krxStockRepository.getMarketCap(date, Market.KOSPI) } returns Result.failure(exception)
            coEvery { krxStockRepository.getMarketCap(date, Market.KOSDAQ) } returns Result.success(emptyList())

            // When
            val result = useCase(date, markets = listOf(Market.KOSPI, Market.KOSDAQ))

            // Then: Fail-fast → KOSDAQ 호출 안 함
            assertTrue(result.isFailure)
            assertEquals(exception.message, result.exceptionOrNull()?.message)
            coVerify(exactly = 0) { krxStockRepository.getMarketCap(date, Market.KOSDAQ) }
        }

        @Test
        @DisplayName("invoke_whenSecondMarketFails_returnsFailure")
        fun `invoke_whenSecondMarketFails_returnsFailure`() = runTest {
            // Given: KOSPI 성공, KOSDAQ 실패
            val date = "20260219"
            val kospiData = listOf(createMarketCap("005930", 400_000_000_000_000L))
            coEvery { krxStockRepository.getMarketCap(date, Market.KOSPI) } returns Result.success(kospiData)
            coEvery { krxStockRepository.getMarketCap(date, Market.KOSDAQ) } returns
                    Result.failure(RuntimeException("KOSDAQ 조회 실패"))

            // When
            val result = useCase(date)

            // Then
            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("invoke_whenAllMarketsFail_returnsFirstFailure")
        fun `invoke_whenAllMarketsFail_returnsFirstFailure`() = runTest {
            // Given: 모든 마켓 실패
            val date = "20260219"
            val firstError = RuntimeException("첫 번째 마켓 실패")
            coEvery { krxStockRepository.getMarketCap(date, Market.KOSPI) } returns Result.failure(firstError)

            // When
            val result = useCase(date, markets = listOf(Market.KOSPI, Market.KOSDAQ))

            // Then: Fail-fast이므로 첫 번째 에러만 반환
            assertTrue(result.isFailure)
            assertEquals(firstError.message, result.exceptionOrNull()?.message)
        }
    }

    // ================================================================
    // 헬퍼 함수
    // ================================================================

    private fun createMarketCap(ticker: String, marketCap: Long) = MarketCap(
        ticker = ticker,
        name = "종목$ticker",
        close = 50_000L,
        changeRate = 0.5,
        marketCap = marketCap,
        sharesOutstanding = 20_000_000L
    )
}
