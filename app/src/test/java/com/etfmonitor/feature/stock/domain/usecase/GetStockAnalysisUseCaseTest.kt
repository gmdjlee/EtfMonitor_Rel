package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.core.analysis.model.StockData
import com.etfmonitor.feature.stock.domain.repository.StockAnalysisRepository
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
 * GetStockAnalysisUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 성공 경로: StockData 반환
 * - 기본 파라미터(days=180) 검증
 * - null 반환 처리
 * - StockData 필드 검증 (ticker, name, dates, marketCap, foreign5d, institution5d)
 * - 24시간 캐싱 정책 위임 검증 (Repository에 위임)
 * - 다양한 days 파라미터 검증
 *
 * 캐싱 정책 (Repository에서 관리):
 * - 데이터 만료: 24시간
 * - 최신 날짜가 오늘이 아니면 업데이트
 * - 데이터가 요청 일수의 80% 미만이면 업데이트
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("GetStockAnalysisUseCase 테스트")
class GetStockAnalysisUseCaseTest {

    private lateinit var repository: StockAnalysisRepository
    private lateinit var useCase: GetStockAnalysisUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = GetStockAnalysisUseCase(repository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withValidTicker_returnsStockData")
        fun `invoke_withValidTicker_returnsStockData`() = runTest {
            // Given
            val ticker = "005930"
            val stockData = createStockData(ticker, "삼성전자", 180)
            coEvery { repository.getStockAnalysis(ticker, 180) } returns stockData

            // When
            val result = useCase(ticker)

            // Then
            assertNotNull(result)
            assertEquals(ticker, result.ticker)
            assertEquals("삼성전자", result.name)
        }

        @Test
        @DisplayName("invoke_withDefaultDays_uses180Days")
        fun `invoke_withDefaultDays_uses180Days`() = runTest {
            // Given
            val ticker = "000660"
            coEvery { repository.getStockAnalysis(ticker, 180) } returns null

            // When
            useCase(ticker)

            // Then: 기본값 days=180 사용
            coVerify(exactly = 1) { repository.getStockAnalysis(ticker, 180) }
        }

        @Test
        @DisplayName("invoke_withCustomDays_delegatesCustomDaysToRepository")
        fun `invoke_withCustomDays_delegatesCustomDaysToRepository`() = runTest {
            // Given
            val ticker = "035420"
            val stockData = createStockData(ticker, "NAVER", 365)
            coEvery { repository.getStockAnalysis(ticker, 365) } returns stockData

            // When
            val result = useCase(ticker, days = 365)

            // Then
            assertNotNull(result)
            coVerify(exactly = 1) { repository.getStockAnalysis(ticker, 365) }
        }

        @Test
        @DisplayName("invoke_withValidData_allListsSameSize")
        fun `invoke_withValidData_allListsSameSize`() = runTest {
            // Given
            val ticker = "005930"
            val count = 180
            val stockData = createStockData(ticker, "삼성전자", count)
            coEvery { repository.getStockAnalysis(ticker, count) } returns stockData

            // When
            val result = useCase(ticker, days = count)!!

            // Then: 모든 리스트 크기가 동일해야 함
            assertEquals(count, result.dates.size)
            assertEquals(count, result.marketCap.size)
            assertEquals(count, result.foreign5d.size)
            assertEquals(count, result.institution5d.size)
        }

        @Test
        @DisplayName("invoke_withValidData_foreignAndInstitutionDataPresent")
        fun `invoke_withValidData_foreignAndInstitutionDataPresent`() = runTest {
            // Given: 외국인/기관 5일 누적 데이터가 있는 경우
            val ticker = "005930"
            val foreign5d = listOf(100_000L, -50_000L, 200_000L, -80_000L, 150_000L)
            val institution5d = listOf(-30_000L, 70_000L, -40_000L, 90_000L, -20_000L)
            val stockData = StockData(
                ticker = ticker,
                name = "삼성전자",
                dates = listOf("2026-02-17", "2026-02-18", "2026-02-19", "2026-02-20", "2026-02-21"),
                marketCap = List(5) { 400_000_000_000_000L },
                foreign5d = foreign5d,
                institution5d = institution5d
            )
            coEvery { repository.getStockAnalysis(ticker, 180) } returns stockData

            // When
            val result = useCase(ticker)!!

            // Then
            assertEquals(5, result.foreign5d.size)
            assertEquals(5, result.institution5d.size)
            // 외국인 매수/매도 혼재
            assertTrue(result.foreign5d.any { it > 0 })
            assertTrue(result.foreign5d.any { it < 0 })
        }

        @Test
        @DisplayName("invoke_withSmallDays_returns90DaysData")
        fun `invoke_withSmallDays_returns90DaysData`() = runTest {
            // Given
            val ticker = "000660"
            val stockData = createStockData(ticker, "SK하이닉스", 90)
            coEvery { repository.getStockAnalysis(ticker, 90) } returns stockData

            // When
            val result = useCase(ticker, days = 90)!!

            // Then
            assertEquals(90, result.dates.size)
            coVerify(exactly = 1) { repository.getStockAnalysis(ticker, 90) }
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
            coEvery { repository.getStockAnalysis(any(), any()) } returns null

            // When
            val result = useCase("005930")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("invoke_withUnknownTicker_returnsNull")
        fun `invoke_withUnknownTicker_returnsNull`() = runTest {
            // Given
            coEvery { repository.getStockAnalysis("UNKNOWN", 180) } returns null

            // When
            val result = useCase("UNKNOWN")

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("invoke_withInvalidTicker_returnsNullAndDelegatesToRepository")
        fun `invoke_withInvalidTicker_returnsNullAndDelegatesToRepository`() = runTest {
            // Given: UseCase는 단순 위임 — 유효성 검사를 Repository에서 처리
            coEvery { repository.getStockAnalysis("", 180) } returns null

            // When
            val result = useCase("")

            // Then
            assertNull(result)
            coVerify(exactly = 1) { repository.getStockAnalysis("", 180) }
        }
    }

    // ================================================================
    // 헬퍼 함수
    // ================================================================

    private fun createStockData(ticker: String, name: String, count: Int): StockData {
        return StockData(
            ticker = ticker,
            name = name,
            dates = (1..count).map { "2026-01-${(it % 28 + 1).toString().padStart(2, '0')}" },
            marketCap = List(count) { 400_000_000_000_000L },
            foreign5d = List(count) { (it - count / 2) * 10_000L },
            institution5d = List(count) { (count / 2 - it) * 5_000L }
        )
    }
}
