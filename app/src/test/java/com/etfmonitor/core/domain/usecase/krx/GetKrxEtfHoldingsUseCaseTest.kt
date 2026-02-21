package com.etfmonitor.core.domain.usecase.krx

import com.etfmonitor.core.data.repository.krx.KrxEtfRepositoryImpl
import com.etfmonitor.core.database.entities.Holding
import com.etfmonitor.core.database.entities.SnapshotType
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
 * GetKrxEtfHoldingsUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 성공 경로: 보유 종목 목록 반환
 * - 빈 보유 목록 처리
 * - 실패 Result 전파
 * - ticker + date 파라미터 위임 검증
 * - Holding.create() 팩토리 생성 값 검증
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("GetKrxEtfHoldingsUseCase 테스트")
class GetKrxEtfHoldingsUseCaseTest {

    private lateinit var krxEtfRepository: KrxEtfRepositoryImpl
    private lateinit var useCase: GetKrxEtfHoldingsUseCase

    @BeforeEach
    fun setUp() {
        krxEtfRepository = mockk()
        useCase = GetKrxEtfHoldingsUseCase(krxEtfRepository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withValidTickerAndDate_returnsHoldingList")
        fun `invoke_withValidTickerAndDate_returnsHoldingList`() = runTest {
            // Given
            val etfTicker = "069500"
            val date = "20260219"
            val holdings = listOf(
                Holding.create("069500", "005930", "삼성전자", "2026-02-19", 0.25f, 100_000_000_000f),
                Holding.create("069500", "000660", "SK하이닉스", "2026-02-19", 0.10f, 40_000_000_000f)
            )
            coEvery { krxEtfRepository.getEtfHoldings(etfTicker, date) } returns Result.success(holdings)

            // When
            val result = useCase(etfTicker, date)

            // Then
            assertTrue(result.isSuccess)
            val list = result.getOrNull()!!
            assertEquals(2, list.size)
            assertEquals("005930", list[0].stockTicker)
            assertEquals("삼성전자", list[0].stockName)
        }

        @Test
        @DisplayName("invoke_withHoldingCreateFactory_compressesWeightAndAmount")
        fun `invoke_withHoldingCreateFactory_compressesWeightAndAmount`() = runTest {
            // Given: Holding.create() 팩토리가 올바른 bps/million 압축을 수행해야 함
            val holding = Holding.create(
                etfTicker = "069500",
                stockTicker = "005930",
                stockName = "삼성전자",
                date = "2026-02-19",
                weight = 0.25f,        // 25% → 2500 bps
                amount = 1_000_000_000f // 10억원 → 1000 million
            )
            coEvery { krxEtfRepository.getEtfHoldings("069500", "20260219") } returns Result.success(listOf(holding))

            // When
            val result = useCase("069500", "20260219")

            // Then
            assertTrue(result.isSuccess)
            val fetched = result.getOrNull()!!.first()
            assertEquals(2500.toShort(), fetched.weightBps)
            assertEquals(1000, fetched.amountMillion)
        }

        @Test
        @DisplayName("invoke_withEmptyHoldings_returnsSuccessWithEmptyList")
        fun `invoke_withEmptyHoldings_returnsSuccessWithEmptyList`() = runTest {
            // Given: ETF 보유 종목 없음
            val etfTicker = "999999"
            val date = "20260219"
            coEvery { krxEtfRepository.getEtfHoldings(etfTicker, date) } returns Result.success(emptyList())

            // When
            val result = useCase(etfTicker, date)

            // Then
            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()?.isEmpty() == true)
        }

        @Test
        @DisplayName("invoke_withLargeHoldingList_returnsAllHoldings")
        fun `invoke_withLargeHoldingList_returnsAllHoldings`() = runTest {
            // Given: 대규모 ETF (100개 보유 종목)
            val etfTicker = "069500"
            val date = "20260219"
            val holdings = (1..100).map { i ->
                Holding.create(
                    etfTicker = etfTicker,
                    stockTicker = i.toString().padStart(6, '0'),
                    stockName = "종목$i",
                    date = "2026-02-19",
                    weight = 0.01f,
                    amount = 1_000_000_000f,
                    snapshotType = SnapshotType.DAILY
                )
            }
            coEvery { krxEtfRepository.getEtfHoldings(etfTicker, date) } returns Result.success(holdings)

            // When
            val result = useCase(etfTicker, date)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(100, result.getOrNull()?.size)
        }
    }

    // ================================================================
    // 파라미터 위임 검증 테스트
    // ================================================================

    @Nested
    @DisplayName("파라미터 위임 검증 테스트")
    inner class DelegationTests {

        @Test
        @DisplayName("invoke_delegatesTickerAndDate_toRepository")
        fun `invoke_delegatesTickerAndDate_toRepository`() = runTest {
            // Given
            val etfTicker = "102110"
            val date = "20260101"
            coEvery { krxEtfRepository.getEtfHoldings(etfTicker, date) } returns Result.success(emptyList())

            // When
            useCase(etfTicker, date)

            // Then
            coVerify(exactly = 1) { krxEtfRepository.getEtfHoldings(etfTicker, date) }
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
            val exception = RuntimeException("보유 종목 조회 실패")
            coEvery { krxEtfRepository.getEtfHoldings(any(), any()) } returns Result.failure(exception)

            // When
            val result = useCase("069500", "20260219")

            // Then
            assertTrue(result.isFailure)
            assertEquals(exception.message, result.exceptionOrNull()?.message)
        }

        @Test
        @DisplayName("invoke_whenNetworkError_propagatesError")
        fun `invoke_whenNetworkError_propagatesError`() = runTest {
            // Given
            coEvery { krxEtfRepository.getEtfHoldings(any(), any()) } returns
                    Result.failure(RuntimeException("KRX 네트워크 오류"))

            // When
            val result = useCase("069500", "20260219")

            // Then
            assertTrue(result.isFailure)
        }
    }
}
