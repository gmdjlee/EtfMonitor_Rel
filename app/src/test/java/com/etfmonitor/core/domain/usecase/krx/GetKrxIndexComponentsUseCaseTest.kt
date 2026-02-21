package com.etfmonitor.core.domain.usecase.krx

import com.etfmonitor.core.data.repository.krx.KrxMarketRepositoryImpl
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
 * GetKrxIndexComponentsUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 성공 경로: 지수 구성 종목 목록 반환
 * - topN 기본값 200 파라미터 동작
 * - 커스텀 topN 값 동작
 * - 빈 결과 처리
 * - 실패 Result 전파
 * - indexTicker 위임 검증 (KOSPI_200_INDEX = "1028", KOSDAQ_150_INDEX = "2203")
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("GetKrxIndexComponentsUseCase 테스트")
class GetKrxIndexComponentsUseCaseTest {

    private lateinit var krxMarketRepository: KrxMarketRepositoryImpl
    private lateinit var useCase: GetKrxIndexComponentsUseCase

    @BeforeEach
    fun setUp() {
        krxMarketRepository = mockk()
        useCase = GetKrxIndexComponentsUseCase(krxMarketRepository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withKospi200Index_returnsTopComponents")
        fun `invoke_withKospi200Index_returnsTopComponents`() = runTest {
            // Given
            val indexTicker = "1028"  // KOSPI_200_INDEX
            val date = "20260219"
            val tickers = (1..200).map { it.toString().padStart(6, '0') }
            coEvery { krxMarketRepository.getIndexComponents(indexTicker, date, 200) } returns Result.success(tickers)

            // When
            val result = useCase(indexTicker, date)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(200, result.getOrNull()?.size)
        }

        @Test
        @DisplayName("invoke_withKosdaq150Index_returnsComponents")
        fun `invoke_withKosdaq150Index_returnsComponents`() = runTest {
            // Given
            val indexTicker = "2203"  // KOSDAQ_150_INDEX
            val date = "20260219"
            val tickers = (1..150).map { it.toString().padStart(6, '0') }
            coEvery { krxMarketRepository.getIndexComponents(indexTicker, date, 200) } returns Result.success(tickers)

            // When
            val result = useCase(indexTicker, date)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(150, result.getOrNull()?.size)
        }

        @Test
        @DisplayName("invoke_withDefaultTopN_passesTopN200")
        fun `invoke_withDefaultTopN_passesTopN200`() = runTest {
            // Given
            val indexTicker = "1028"
            val date = "20260219"
            coEvery { krxMarketRepository.getIndexComponents(indexTicker, date, 200) } returns Result.success(emptyList())

            // When
            useCase(indexTicker, date)  // topN 기본값 200

            // Then
            coVerify(exactly = 1) { krxMarketRepository.getIndexComponents(indexTicker, date, 200) }
        }

        @Test
        @DisplayName("invoke_withCustomTopN_passesCustomTopN")
        fun `invoke_withCustomTopN_passesCustomTopN`() = runTest {
            // Given
            val indexTicker = "1028"
            val date = "20260219"
            val topN = 100
            coEvery { krxMarketRepository.getIndexComponents(indexTicker, date, topN) } returns Result.success(emptyList())

            // When
            useCase(indexTicker, date, topN)

            // Then
            coVerify(exactly = 1) { krxMarketRepository.getIndexComponents(indexTicker, date, topN) }
        }

        @Test
        @DisplayName("invoke_withEmptyComponents_returnsEmptyList")
        fun `invoke_withEmptyComponents_returnsEmptyList`() = runTest {
            // Given
            coEvery { krxMarketRepository.getIndexComponents(any(), any(), any()) } returns Result.success(emptyList())

            // When
            val result = useCase("1028", "20260219")

            // Then
            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()?.isEmpty() == true)
        }

        @Test
        @DisplayName("invoke_withTop10_returnsOnlyTop10Tickers")
        fun `invoke_withTop10_returnsOnlyTop10Tickers`() = runTest {
            // Given: topN=10으로 시가총액 상위 10개만
            val indexTicker = "1028"
            val date = "20260219"
            val top10 = listOf("005930", "000660", "035420", "207940", "005380",
                "000270", "035720", "051910", "068270", "005490")
            coEvery { krxMarketRepository.getIndexComponents(indexTicker, date, 10) } returns Result.success(top10)

            // When
            val result = useCase(indexTicker, date, topN = 10)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(10, result.getOrNull()?.size)
            assertEquals("005930", result.getOrNull()?.first())
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
            // Given: 180s 타임아웃을 초과하는 큰 데이터 조회 실패
            val exception = RuntimeException("KRX WAF 403 오류")
            coEvery { krxMarketRepository.getIndexComponents(any(), any(), any()) } returns Result.failure(exception)

            // When
            val result = useCase("1028", "20260219", 200)

            // Then
            assertTrue(result.isFailure)
            assertEquals(exception.message, result.exceptionOrNull()?.message)
        }

        @Test
        @DisplayName("invoke_whenNetworkTimeout_returnsFailure")
        fun `invoke_whenNetworkTimeout_returnsFailure`() = runTest {
            // Given
            coEvery { krxMarketRepository.getIndexComponents(any(), any(), any()) } returns
                    Result.failure(RuntimeException("타임아웃"))

            // When
            val result = useCase("2203", "20260219")

            // Then
            assertTrue(result.isFailure)
        }
    }
}
