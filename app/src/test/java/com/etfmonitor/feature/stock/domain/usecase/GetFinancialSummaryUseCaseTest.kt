package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.stock.domain.model.financial.BalanceSheet
import com.etfmonitor.feature.stock.domain.model.financial.FinancialData
import com.etfmonitor.feature.stock.domain.model.financial.FinancialPeriod
import com.etfmonitor.feature.stock.domain.model.financial.FinancialSummary
import com.etfmonitor.feature.stock.domain.model.financial.GrowthRatios
import com.etfmonitor.feature.stock.domain.model.financial.IncomeStatement
import com.etfmonitor.feature.stock.domain.model.financial.ProfitabilityRatios
import com.etfmonitor.feature.stock.domain.model.financial.StabilityRatios
import com.etfmonitor.feature.stock.domain.repository.FinancialRepository
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
 * GetFinancialSummaryUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() FinancialData → FinancialSummary 변환 후 Result.success 반환
 * - invoke() useCache 파라미터 전달 검증
 * - invoke() 실패 시 Result.failure 반환
 * - refresh() 캐시 우회 조회 검증
 * - ticker/name 파라미터 정확히 전달 검증
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("GetFinancialSummaryUseCase 테스트")
class GetFinancialSummaryUseCaseTest {

    private val repository: FinancialRepository = mockk()
    private lateinit var useCase: GetFinancialSummaryUseCase

    @BeforeEach
    fun setUp() {
        useCase = GetFinancialSummaryUseCase(repository)
    }

    /**
     * 테스트용 FinancialData 생성 헬퍼
     * Q1 단독값만 가진 간단한 데이터 (convertYtdToQuarterly 처리 포함)
     */
    private fun createTestFinancialData(
        ticker: String = "005930",
        name: String = "삼성전자"
    ): FinancialData {
        val period1 = FinancialPeriod("202503", 2025, 1)
        val period2 = FinancialPeriod("202506", 2025, 2)

        val balanceSheets = mapOf(
            "202503" to BalanceSheet(
                period = period1,
                currentAssets = 200_000L, fixedAssets = 300_000L, totalAssets = 500_000L,
                currentLiabilities = 100_000L, fixedLiabilities = 150_000L,
                totalLiabilities = 250_000L,
                capital = 10_000L, capitalSurplus = 50_000L, retainedEarnings = 190_000L,
                totalEquity = 250_000L
            ),
            "202506" to BalanceSheet(
                period = period2,
                currentAssets = 210_000L, fixedAssets = 310_000L, totalAssets = 520_000L,
                currentLiabilities = 105_000L, fixedLiabilities = 155_000L,
                totalLiabilities = 260_000L,
                capital = 10_000L, capitalSurplus = 50_000L, retainedEarnings = 200_000L,
                totalEquity = 260_000L
            )
        )

        val incomeStatements = mapOf(
            "202503" to IncomeStatement(
                period = period1,
                revenue = 80_000L, costOfSales = 50_000L, grossProfit = 30_000L,
                operatingProfit = 15_000L, ordinaryProfit = 14_000L, netIncome = 10_000L
            ),
            "202506" to IncomeStatement(
                period = period2,
                // Q2 YTD: revenue=160_000 → quarterly = 160_000 - 80_000 = 80_000
                revenue = 160_000L, costOfSales = 100_000L, grossProfit = 60_000L,
                operatingProfit = 30_000L, ordinaryProfit = 28_000L, netIncome = 20_000L
            )
        )

        val profitabilityRatios = mapOf(
            "202503" to ProfitabilityRatios(period1, 18.75, 12.5, 4.0, 2.0),
            "202506" to ProfitabilityRatios(period2, 18.75, 12.5, 4.2, 2.1)
        )

        val stabilityRatios = mapOf(
            "202503" to StabilityRatios(period1, 100.0, 200.0, 150.0, 30.0, 5.0),
            "202506" to StabilityRatios(period2, 100.0, 200.0, 150.0, 28.0, 5.5)
        )

        val growthRatios = mapOf(
            "202503" to GrowthRatios(period1, 5.0, 3.0, 2.0, 1.5, 1.0),
            "202506" to GrowthRatios(period2, 5.5, 3.5, 2.5, 2.0, 1.5)
        )

        return FinancialData(
            ticker = ticker,
            name = name,
            periods = listOf("202503", "202506"),
            balanceSheets = balanceSheets,
            incomeStatements = incomeStatements,
            profitabilityRatios = profitabilityRatios,
            stabilityRatios = stabilityRatios,
            growthRatios = growthRatios
        )
    }

    // ================================================================
    // invoke() 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("invoke() 성공 경로 테스트")
    inner class InvokeSuccessTests {

        @Test
        @DisplayName("invoke_withValidTickerAndName_returnsSuccessWithSummary")
        fun `invoke_withValidTickerAndName_returnsSuccessWithSummary`() = runTest {
            // Given
            val ticker = "005930"
            val name = "삼성전자"
            val financialData = createTestFinancialData(ticker, name)
            coEvery { repository.getFinancialData(ticker, name, true) } returns Result.success(financialData)

            // When
            val result = useCase(ticker, name)

            // Then
            assertTrue(result.isSuccess)
            val summary = result.getOrNull()
            assertNotNull(summary)
            assertEquals(ticker, summary.ticker)
            assertEquals(name, summary.name)
        }

        @Test
        @DisplayName("invoke_withUseCacheTrue_passesUseCacheTrueToRepository")
        fun `invoke_withUseCacheTrue_passesUseCacheTrueToRepository`() = runTest {
            // Given
            val ticker = "005930"
            val name = "삼성전자"
            val financialData = createTestFinancialData(ticker, name)
            coEvery { repository.getFinancialData(ticker, name, true) } returns Result.success(financialData)

            // When
            useCase(ticker, name, useCache = true)

            // Then
            coVerify(exactly = 1) { repository.getFinancialData(ticker, name, true) }
        }

        @Test
        @DisplayName("invoke_withUseCacheFalse_passesUseCacheFalseToRepository")
        fun `invoke_withUseCacheFalse_passesUseCacheFalseToRepository`() = runTest {
            // Given
            val ticker = "005930"
            val name = "삼성전자"
            val financialData = createTestFinancialData(ticker, name)
            coEvery { repository.getFinancialData(ticker, name, false) } returns Result.success(financialData)

            // When
            useCase(ticker, name, useCache = false)

            // Then
            coVerify(exactly = 1) { repository.getFinancialData(ticker, name, false) }
        }

        @Test
        @DisplayName("invoke_summaryContainsPeriods_fromFinancialData")
        fun `invoke_summaryContainsPeriods_fromFinancialData`() = runTest {
            // Given
            val ticker = "005930"
            val name = "삼성전자"
            val financialData = createTestFinancialData(ticker, name)
            coEvery { repository.getFinancialData(ticker, name, true) } returns Result.success(financialData)

            // When
            val result = useCase(ticker, name)

            // Then
            val summary = result.getOrNull()!!
            assertEquals(2, summary.periods.size)
            assertTrue(summary.periods.contains("202503"))
            assertTrue(summary.periods.contains("202506"))
        }

        @Test
        @DisplayName("invoke_withDefaultUseCache_usesTrue")
        fun `invoke_withDefaultUseCache_usesTrue`() = runTest {
            // Given: 기본값 useCache = true
            val ticker = "000660"
            val name = "SK하이닉스"
            val financialData = createTestFinancialData(ticker, name)
            coEvery { repository.getFinancialData(ticker, name, true) } returns Result.success(financialData)

            // When
            useCase(ticker, name)  // useCache 기본값 사용

            // Then
            coVerify(exactly = 1) { repository.getFinancialData(ticker, name, true) }
        }
    }

    // ================================================================
    // invoke() 실패 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("invoke() 실패 경로 테스트")
    inner class InvokeFailureTests {

        @Test
        @DisplayName("invoke_whenRepositoryFails_returnsFailureResult")
        fun `invoke_whenRepositoryFails_returnsFailureResult`() = runTest {
            // Given
            val exception = RuntimeException("KIS API 오류")
            coEvery { repository.getFinancialData(any(), any(), any()) } returns Result.failure(exception)

            // When
            val result = useCase("005930", "삼성전자")

            // Then
            assertTrue(result.isFailure)
            assertEquals(exception.message, result.exceptionOrNull()?.message)
        }

        @Test
        @DisplayName("invoke_whenNoApiKey_returnsFailureResult")
        fun `invoke_whenNoApiKey_returnsFailureResult`() = runTest {
            // Given
            val authException = RuntimeException("KIS API 키 미설정")
            coEvery { repository.getFinancialData(any(), any(), any()) } returns Result.failure(authException)

            // When
            val result = useCase("005930", "삼성전자")

            // Then
            assertTrue(result.isFailure)
            assertNotNull(result.exceptionOrNull())
        }
    }

    // ================================================================
    // refresh() 테스트
    // ================================================================

    @Nested
    @DisplayName("refresh() 캐시 우회 테스트")
    inner class RefreshTests {

        @Test
        @DisplayName("refresh_withValidTicker_callsRefreshFinancialData")
        fun `refresh_withValidTicker_callsRefreshFinancialData`() = runTest {
            // Given
            val ticker = "005930"
            val name = "삼성전자"
            val financialData = createTestFinancialData(ticker, name)
            coEvery { repository.refreshFinancialData(ticker, name) } returns Result.success(financialData)

            // When
            val result = useCase.refresh(ticker, name)

            // Then
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { repository.refreshFinancialData(ticker, name) }
        }

        @Test
        @DisplayName("refresh_whenRepositoryFails_returnsFailureResult")
        fun `refresh_whenRepositoryFails_returnsFailureResult`() = runTest {
            // Given
            coEvery { repository.refreshFinancialData(any(), any()) } returns Result.failure(RuntimeException("갱신 실패"))

            // When
            val result = useCase.refresh("005930", "삼성전자")

            // Then
            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("refresh_doesNotCallGetFinancialData_onlyRefresh")
        fun `refresh_doesNotCallGetFinancialData_onlyRefresh`() = runTest {
            // Given
            val ticker = "005930"
            val name = "삼성전자"
            val financialData = createTestFinancialData(ticker, name)
            coEvery { repository.refreshFinancialData(ticker, name) } returns Result.success(financialData)

            // When
            useCase.refresh(ticker, name)

            // Then — refresh 시 getFinancialData는 호출되지 않아야 함
            coVerify(exactly = 0) { repository.getFinancialData(any(), any(), any()) }
            coVerify(exactly = 1) { repository.refreshFinancialData(ticker, name) }
        }
    }
}
