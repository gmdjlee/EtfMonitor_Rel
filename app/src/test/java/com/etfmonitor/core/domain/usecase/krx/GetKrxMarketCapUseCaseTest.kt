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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * GetKrxMarketCapUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 성공 경로: 특정 날짜 + 시장 조합
 * - invoke() 실패 경로: 예외 전파
 * - 기본 파라미터(Market.ALL) 사용
 * - 빈 결과 처리
 * - 다수 시가총액 항목 반환
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("GetKrxMarketCapUseCase 테스트")
class GetKrxMarketCapUseCaseTest {

    private lateinit var krxStockRepository: KrxStockRepositoryImpl
    private lateinit var useCase: GetKrxMarketCapUseCase

    @BeforeEach
    fun setUp() {
        krxStockRepository = mockk()
        useCase = GetKrxMarketCapUseCase(krxStockRepository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withValidDateAndMarket_returnsMarketCapList")
        fun `invoke_withValidDateAndMarket_returnsMarketCapList`() = runTest {
            // Given
            val date = "20260219"
            val market = Market.KOSPI
            val expected = listOf(
                createMarketCap("005930", "삼성전자", 400_000_000_000_000L),
                createMarketCap("000660", "SK하이닉스", 90_000_000_000_000L)
            )
            coEvery { krxStockRepository.getMarketCap(date, market) } returns Result.success(expected)

            // When
            val result = useCase(date, market)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(2, result.getOrNull()?.size)
            assertEquals("005930", result.getOrNull()?.first()?.ticker)
        }

        @Test
        @DisplayName("invoke_withDefaultMarket_usesMarketAll")
        fun `invoke_withDefaultMarket_usesMarketAll`() = runTest {
            // Given
            val date = "20260219"
            coEvery { krxStockRepository.getMarketCap(date, Market.ALL) } returns Result.success(emptyList())

            // When
            useCase(date)  // market 기본값 Market.ALL

            // Then
            coVerify(exactly = 1) { krxStockRepository.getMarketCap(date, Market.ALL) }
        }

        @Test
        @DisplayName("invoke_withKosdaqMarket_routesToKosdaqData")
        fun `invoke_withKosdaqMarket_routesToKosdaqData`() = runTest {
            // Given
            val date = "20260219"
            val kosdaqData = listOf(createMarketCap("247540", "에코프로비엠", 20_000_000_000_000L))
            coEvery { krxStockRepository.getMarketCap(date, Market.KOSDAQ) } returns Result.success(kosdaqData)

            // When
            val result = useCase(date, Market.KOSDAQ)

            // Then
            assertTrue(result.isSuccess)
            assertEquals("247540", result.getOrNull()?.first()?.ticker)
        }

        @Test
        @DisplayName("invoke_withEmptyResponse_returnsSuccessWithEmptyList")
        fun `invoke_withEmptyResponse_returnsSuccessWithEmptyList`() = runTest {
            // Given
            val date = "20260219"
            coEvery { krxStockRepository.getMarketCap(date, Market.KOSPI) } returns Result.success(emptyList())

            // When
            val result = useCase(date, Market.KOSPI)

            // Then
            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()?.isEmpty() == true)
        }

        @Test
        @DisplayName("invoke_withLargeMarketCapList_returnsAllItems")
        fun `invoke_withLargeMarketCapList_returnsAllItems`() = runTest {
            // Given
            val date = "20260219"
            val largeList = (1..200).map { i ->
                createMarketCap("${i.toString().padStart(6, '0')}", "종목$i", (200L - i) * 1_000_000_000_000L)
            }
            coEvery { krxStockRepository.getMarketCap(date, Market.KOSPI) } returns Result.success(largeList)

            // When
            val result = useCase(date, Market.KOSPI)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(200, result.getOrNull()?.size)
        }
    }

    // ================================================================
    // 실패 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("실패 경로 테스트")
    inner class FailurePathTests {

        @Test
        @DisplayName("invoke_whenRepositoryFails_returnsFailureResult")
        fun `invoke_whenRepositoryFails_returnsFailureResult`() = runTest {
            // Given
            val date = "20260219"
            val exception = RuntimeException("KRX 네트워크 오류")
            coEvery { krxStockRepository.getMarketCap(date, Market.KOSPI) } returns Result.failure(exception)

            // When
            val result = useCase(date, Market.KOSPI)

            // Then
            assertTrue(result.isFailure)
            assertEquals(exception.message, result.exceptionOrNull()?.message)
        }

        @Test
        @DisplayName("invoke_whenRepositoryThrows_propagatesException")
        fun `invoke_whenRepositoryThrows_propagatesException`() = runTest {
            // Given
            val date = "20260219"
            coEvery { krxStockRepository.getMarketCap(date, Market.ALL) } throws RuntimeException("연결 실패")

            // When & Then
            var caught: Exception? = null
            try {
                useCase(date)
            } catch (e: Exception) {
                caught = e
            }
            assertNotNull(caught)
        }
    }

    // ================================================================
    // 위임(delegation) 검증 테스트
    // ================================================================

    @Nested
    @DisplayName("위임 검증 테스트")
    inner class DelegationTests {

        @Test
        @DisplayName("invoke_delegatesExactly_toRepository")
        fun `invoke_delegatesExactly_toRepository`() = runTest {
            // Given
            val date = "20260101"
            val market = Market.KOSPI
            coEvery { krxStockRepository.getMarketCap(date, market) } returns Result.success(emptyList())

            // When
            useCase(date, market)

            // Then
            coVerify(exactly = 1) { krxStockRepository.getMarketCap(date, market) }
        }
    }

    // ================================================================
    // 헬퍼 함수
    // ================================================================

    private fun createMarketCap(ticker: String, name: String, marketCap: Long) = MarketCap(
        ticker = ticker,
        name = name,
        close = 50_000L,
        changeRate = 0.5,
        marketCap = marketCap,
        sharesOutstanding = 20_000_000L
    )
}
