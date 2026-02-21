package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.stock.domain.model.StockAmountRanking
import com.etfmonitor.feature.stock.domain.repository.StockStatisticsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * GetStockRankingUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 금액순위 목록 반환
 * - invoke() 빈 목록 처리
 * - 순위 데이터 필드 검증 (totalAmount, etfCount 등)
 * - repository 정확히 1회 호출 검증
 * - 예외 전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("GetStockRankingUseCase 테스트")
class GetStockRankingUseCaseTest {

    private val repository: StockStatisticsRepository = mockk()
    private lateinit var useCase: GetStockRankingUseCase

    @BeforeEach
    fun setUp() {
        useCase = GetStockRankingUseCase(repository)
    }

    private fun createRanking(
        ticker: String,
        name: String,
        totalAmount: Float,
        etfCount: Int = 5
    ) = StockAmountRanking(
        stockTicker = ticker,
        stockName = name,
        totalAmount = totalAmount,
        etfCount = etfCount,
        newEtfCount = 0,
        increasedEtfCount = 1,
        decreasedEtfCount = 0,
        removedEtfCount = 0
    )

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withRankingData_returnsRankedList")
        fun `invoke_withRankingData_returnsRankedList`() = runTest {
            // Given
            val expected = listOf(
                createRanking("005930", "삼성전자", 10_000_000f, etfCount = 15),
                createRanking("000660", "SK하이닉스", 5_000_000f, etfCount = 8),
                createRanking("035420", "NAVER", 2_000_000f, etfCount = 5)
            )
            coEvery { repository.getStockAmountRanking() } returns expected

            // When
            val result = useCase()

            // Then
            assertEquals(3, result.size)
            assertEquals("005930", result.first().stockTicker)
            assertEquals("삼성전자", result.first().stockName)
            assertEquals(10_000_000f, result.first().totalAmount)
            assertEquals(15, result.first().etfCount)
        }

        @Test
        @DisplayName("invoke_withEmptyData_returnsEmptyList")
        fun `invoke_withEmptyData_returnsEmptyList`() = runTest {
            // Given
            coEvery { repository.getStockAmountRanking() } returns emptyList()

            // When
            val result = useCase()

            // Then
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("invoke_delegatesExactlyOnce_toRepository")
        fun `invoke_delegatesExactlyOnce_toRepository`() = runTest {
            // Given
            coEvery { repository.getStockAmountRanking() } returns emptyList()

            // When
            useCase()

            // Then
            coVerify(exactly = 1) { repository.getStockAmountRanking() }
        }

        @Test
        @DisplayName("invoke_withMaxLimit500_returnsAllItems")
        fun `invoke_withMaxLimit500_returnsAllItems`() = runTest {
            // Given: LIMIT 500 검증 — 기존 DAO limit 맞춤
            val largeList = (1..500).map { i ->
                createRanking(
                    ticker = i.toString().padStart(6, '0'),
                    name = "종목$i",
                    totalAmount = (500 - i).toFloat() * 100_000f
                )
            }
            coEvery { repository.getStockAmountRanking() } returns largeList

            // When
            val result = useCase()

            // Then
            assertEquals(500, result.size)
        }

        @Test
        @DisplayName("invoke_withChangeCountFields_returnsCorrectCounts")
        fun `invoke_withChangeCountFields_returnsCorrectCounts`() = runTest {
            // Given
            val ranking = StockAmountRanking(
                stockTicker = "005930",
                stockName = "삼성전자",
                totalAmount = 1_000_000f,
                etfCount = 10,
                newEtfCount = 2,
                increasedEtfCount = 3,
                decreasedEtfCount = 1,
                removedEtfCount = 1
            )
            coEvery { repository.getStockAmountRanking() } returns listOf(ranking)

            // When
            val result = useCase()

            // Then
            val item = result.first()
            assertEquals(2, item.newEtfCount)
            assertEquals(3, item.increasedEtfCount)
            assertEquals(1, item.decreasedEtfCount)
            assertEquals(1, item.removedEtfCount)
        }
    }

    // ================================================================
    // 실패 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("실패 경로 테스트")
    inner class FailurePathTests {

        @Test
        @DisplayName("invoke_whenRepositoryThrows_propagatesException")
        fun `invoke_whenRepositoryThrows_propagatesException`() = runTest {
            // Given
            coEvery { repository.getStockAmountRanking() } throws RuntimeException("순위 조회 실패")

            // When & Then
            var caught: Exception? = null
            try {
                useCase()
            } catch (e: Exception) {
                caught = e
            }
            assertNotNull(caught)
            assertEquals("순위 조회 실패", caught?.message)
        }
    }
}
