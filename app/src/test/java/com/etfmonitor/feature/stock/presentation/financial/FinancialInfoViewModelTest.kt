package com.etfmonitor.feature.stock.presentation.financial

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.stock.domain.model.financial.FinancialState
import com.etfmonitor.feature.stock.domain.model.financial.FinancialSummary
import com.etfmonitor.feature.stock.domain.model.financial.FinancialTab
import com.etfmonitor.feature.stock.domain.usecase.GetFinancialSummaryUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * FinancialInfoViewModel 단위 테스트
 *
 * 테스트 범위:
 * - 초기 상태 (NoStock)
 * - loadForStock() 성공/실패
 * - KIS API 키 미설정 시 NoApiKey 상태
 * - 네트워크 오류 처리
 * - 빈 periods 시 Error 상태
 * - 동일 ticker 중복 로드 방지 (캐시)
 * - refresh() 동작
 * - retry() 동작
 * - clearStock() 동작
 * - selectTab() 동작
 * - isRefreshing StateFlow
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class FinancialInfoViewModelTest {

    private lateinit var getFinancialSummaryUseCase: GetFinancialSummaryUseCase

    private val testTicker = "005930"
    private val testName = "삼성전자"

    @BeforeEach
    fun setup() {
        getFinancialSummaryUseCase = mockk(relaxed = true)

        // Default: return valid financial summary
        coEvery { getFinancialSummaryUseCase(any(), any(), any()) } returns Result.success(makeSummary())
        coEvery { getFinancialSummaryUseCase.refresh(any(), any()) } returns Result.success(makeSummary())
    }

    private fun createViewModel(): FinancialInfoViewModel =
        FinancialInfoViewModel(getFinancialSummaryUseCase = getFinancialSummaryUseCase)

    // --- helpers ---

    private fun makeSummary(
        ticker: String = testTicker,
        name: String = testName,
        periods: List<String> = listOf("202503", "202506", "202509", "202512")
    ) = FinancialSummary(
        ticker = ticker,
        name = name,
        periods = periods,
        displayPeriods = periods.map { it.substring(2) },
        revenues = listOf(60_000L, 62_000L, 65_000L, 68_000L),
        operatingProfits = listOf(8_000L, 9_000L, 10_000L, 11_000L),
        netIncomes = listOf(6_000L, 7_000L, 8_000L, 9_000L),
        revenueGrowthRates = listOf(3.0, 3.5, 4.0, 4.5),
        operatingProfitGrowthRates = listOf(5.0, 5.5, 6.0, 6.5),
        netIncomeGrowthRates = listOf(4.0, 4.5, 5.0, 5.5),
        equityGrowthRates = listOf(2.0, 2.5, 3.0, 3.5),
        totalAssetsGrowthRates = listOf(1.5, 2.0, 2.5, 3.0),
        debtRatios = listOf(30.0, 28.0, 26.0, 25.0),
        currentRatios = listOf(200.0, 210.0, 220.0, 230.0),
        borrowingDependencies = listOf(10.0, 9.5, 9.0, 8.5)
    )

    // ---------------------------------------------------------------
    // 초기 상태 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("초기 상태 테스트")
    inner class InitialStateTests {

        @Test
        @DisplayName("초기 상태는 NoStock")
        fun initialState_isNoStock() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<FinancialState.NoStock>(awaitItem())
            }
        }

        @Test
        @DisplayName("초기 selectedTab 은 PROFITABILITY")
        fun initialSelectedTab_isProfitability() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectedTab.test {
                assertEquals(FinancialTab.PROFITABILITY, awaitItem())
            }
        }

        @Test
        @DisplayName("초기 isRefreshing 은 false")
        fun initialIsRefreshing_isFalse() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.isRefreshing.test {
                assertFalse(awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // loadForStock() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("loadForStock() 테스트")
    inner class LoadForStockTests {

        @Test
        @DisplayName("로드 성공 시 Success 상태")
        fun load_success_producesSuccessState() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.loadForStock(testTicker, testName)
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<FinancialState.Success>(state)
                assertEquals(testTicker, state.summary.ticker)
                assertEquals(testName, state.summary.name)
                assertEquals(4, state.summary.periods.size)
            }
        }

        @Test
        @DisplayName("로드 중 Loading 상태 설정")
        fun load_setsLoadingState() = runTest {
            coEvery { getFinancialSummaryUseCase(any(), any(), any()) } coAnswers {
                kotlinx.coroutines.delay(100)
                Result.success(makeSummary())
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.loadForStock(testTicker, testName)

            viewModel.state.test {
                assertIs<FinancialState.Loading>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("빈 periods 반환 시 Error 상태")
        fun emptyPeriods_producesErrorState() = runTest {
            coEvery { getFinancialSummaryUseCase(any(), any(), any()) } returns
                Result.success(makeSummary(periods = emptyList()))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.loadForStock(testTicker, testName)
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<FinancialState.Error>(awaitItem())
            }
        }

        @Test
        @DisplayName("API key 오류 시 NoApiKey 상태")
        fun apiKeyError_producesNoApiKeyState() = runTest {
            coEvery { getFinancialSummaryUseCase(any(), any(), any()) } returns
                Result.failure(Exception("API key not configured"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.loadForStock(testTicker, testName)
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<FinancialState.NoApiKey>(awaitItem())
            }
        }

        @Test
        @DisplayName("네트워크 오류 시 네트워크 관련 Error 상태")
        fun networkError_producesNetworkErrorState() = runTest {
            coEvery { getFinancialSummaryUseCase(any(), any(), any()) } returns
                Result.failure(Exception("network connection failed"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.loadForStock(testTicker, testName)
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<FinancialState.Error>(state)
                assertTrue(state.message.contains("네트워크"))
            }
        }

        @Test
        @DisplayName("일반 오류 시 Error 상태에 메시지 포함")
        fun genericError_producesErrorStateWithMessage() = runTest {
            val errorMsg = "서버 내부 오류"
            coEvery { getFinancialSummaryUseCase(any(), any(), any()) } returns
                Result.failure(Exception(errorMsg))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.loadForStock(testTicker, testName)
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<FinancialState.Error>(state)
                assertTrue(state.message.contains(errorMsg))
            }
        }

        @Test
        @DisplayName("같은 ticker로 이미 Success 상태면 재로드하지 않음")
        fun sameTicker_alreadySuccess_doesNotReload() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // First load
            viewModel.loadForStock(testTicker, testName)
            advanceUntilIdle()

            // Second load with the same ticker (should be no-op per loadForStock logic)
            viewModel.loadForStock(testTicker, testName)
            advanceUntilIdle()

            // useCase should only be called once for the same ticker when already in Success
            coVerify(exactly = 1) { getFinancialSummaryUseCase(testTicker, testName, true) }
        }

        @Test
        @DisplayName("다른 ticker로 loadForStock() 시 재로드")
        fun differentTicker_alwaysReloads() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.loadForStock(testTicker, testName)
            advanceUntilIdle()

            val otherTicker = "000660"
            val otherName = "SK하이닉스"
            coEvery { getFinancialSummaryUseCase(otherTicker, otherName, any()) } returns
                Result.success(makeSummary(otherTicker, otherName))

            viewModel.loadForStock(otherTicker, otherName)
            advanceUntilIdle()

            coVerify { getFinancialSummaryUseCase(otherTicker, otherName, true) }
        }
    }

    // ---------------------------------------------------------------
    // refresh() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("refresh() 테스트")
    inner class RefreshTests {

        @Test
        @DisplayName("refresh() 호출 시 getFinancialSummaryUseCase.refresh 호출")
        fun refresh_callsUseCaseRefresh() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // First load to set the ticker
            viewModel.loadForStock(testTicker, testName)
            advanceUntilIdle()

            viewModel.refresh()
            advanceUntilIdle()

            coVerify { getFinancialSummaryUseCase.refresh(testTicker, testName) }
        }

        @Test
        @DisplayName("refresh() 성공 시 isRefreshing false로 리셋")
        fun refresh_success_resetsIsRefreshing() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.loadForStock(testTicker, testName)
            advanceUntilIdle()

            viewModel.refresh()
            advanceUntilIdle()

            viewModel.isRefreshing.test {
                assertFalse(awaitItem())
            }
        }

        @Test
        @DisplayName("ticker 없을 때 refresh() 는 아무것도 하지 않음")
        fun refresh_noTicker_doesNothing() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.refresh()
            advanceUntilIdle()

            coVerify(exactly = 0) { getFinancialSummaryUseCase.refresh(any(), any()) }
        }
    }

    // ---------------------------------------------------------------
    // retry() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("retry() 테스트")
    inner class RetryTests {

        @Test
        @DisplayName("retry() 호출 시 캐시 없이 재로드")
        fun retry_reloadsWithoutCache() = runTest {
            // Fail first attempt
            coEvery { getFinancialSummaryUseCase(any(), any(), any()) } returns
                Result.failure(Exception("API key not configured"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.loadForStock(testTicker, testName)
            advanceUntilIdle()

            // Fix the error
            coEvery { getFinancialSummaryUseCase.refresh(any(), any()) } returns Result.success(makeSummary())

            viewModel.retry()
            advanceUntilIdle()

            coVerify { getFinancialSummaryUseCase.refresh(testTicker, testName) }
        }

        @Test
        @DisplayName("ticker 없을 때 retry() 는 아무것도 하지 않음")
        fun retry_noTicker_doesNothing() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.retry()
            advanceUntilIdle()

            coVerify(exactly = 0) { getFinancialSummaryUseCase.refresh(any(), any()) }
        }
    }

    // ---------------------------------------------------------------
    // clearStock() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("clearStock() 테스트")
    inner class ClearStockTests {

        @Test
        @DisplayName("clearStock() 호출 시 NoStock 상태로 리셋")
        fun clearStock_resetsToNoStockState() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.loadForStock(testTicker, testName)
            advanceUntilIdle()

            viewModel.clearStock()

            viewModel.state.test {
                assertIs<FinancialState.NoStock>(awaitItem())
            }
        }

        @Test
        @DisplayName("clearStock() 후 reload 시 새로 데이터 로드")
        fun clearStock_thenLoad_fetchesData() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.loadForStock(testTicker, testName)
            advanceUntilIdle()

            viewModel.clearStock()

            // Re-load same ticker after clear — should load again because currentTicker is null
            viewModel.loadForStock(testTicker, testName)
            advanceUntilIdle()

            coVerify(exactly = 2) { getFinancialSummaryUseCase(testTicker, testName, true) }
        }
    }

    // ---------------------------------------------------------------
    // selectTab() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("selectTab() 테스트")
    inner class SelectTabTests {

        @Test
        @DisplayName("selectTab(STABILITY) 호출 시 selectedTab 업데이트")
        fun selectTab_stability_updatesSelectedTab() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectTab(FinancialTab.STABILITY)

            viewModel.selectedTab.test {
                assertEquals(FinancialTab.STABILITY, awaitItem())
            }
        }

        @Test
        @DisplayName("selectTab() 데이터 로드에 영향 없음")
        fun selectTab_doesNotTriggerDataLoad() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectTab(FinancialTab.STABILITY)
            advanceUntilIdle()

            coVerify(exactly = 0) { getFinancialSummaryUseCase(any(), any(), any()) }
        }
    }
}
