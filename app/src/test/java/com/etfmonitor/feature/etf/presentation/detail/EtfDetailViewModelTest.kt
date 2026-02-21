package com.etfmonitor.feature.etf.presentation.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.service.CollectionState
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.feature.etf.domain.model.ComparisonResult
import com.etfmonitor.feature.etf.domain.model.Etf
import com.etfmonitor.feature.etf.domain.model.HoldingWithComparison
import com.etfmonitor.feature.etf.domain.model.HoldingStatus
import com.etfmonitor.feature.etf.domain.usecase.GetAvailableDatesUseCase
import com.etfmonitor.feature.etf.domain.usecase.GetComparisonInRangeUseCase
import com.etfmonitor.feature.etf.domain.usecase.GetEtfComparisonUseCase
import com.etfmonitor.feature.etf.domain.usecase.GetEtfDetailUseCase
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * EtfDetailViewModel 단위 테스트
 *
 * 테스트 범위:
 * - 초기 상태 (Loading → Success/Error)
 * - SavedStateHandle ticker 파라미터 처리
 * - ETF 상세 정보 로딩 (etfName)
 * - 비교 데이터 로딩 (ComparisonResult)
 * - 날짜 범위 변경 시 재로딩
 * - 데이터 없을 때 Error 상태
 * - 예외 발생 시 Error 상태
 * - CollectionState 완료 시 자동 새로고침
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class EtfDetailViewModelTest {

    private lateinit var getEtfDetailUseCase: GetEtfDetailUseCase
    private lateinit var getEtfComparisonUseCase: GetEtfComparisonUseCase
    private lateinit var getComparisonInRangeUseCase: GetComparisonInRangeUseCase
    private lateinit var getAvailableDatesUseCase: GetAvailableDatesUseCase

    private val testTicker = "KODEX200"
    private val testEtfName = "KODEX 200"

    @BeforeEach
    fun setup() {
        getEtfDetailUseCase = mockk(relaxed = true)
        getEtfComparisonUseCase = mockk(relaxed = true)
        getComparisonInRangeUseCase = mockk(relaxed = true)
        getAvailableDatesUseCase = mockk(relaxed = true)

        // Default: ETF found, comparison returned
        coEvery { getEtfDetailUseCase(testTicker) } returns makeEtf(testTicker, testEtfName)
        coEvery { getComparisonInRangeUseCase(any(), any(), any()) } returns makeComparisonResult()
        coEvery { getAvailableDatesUseCase() } returns listOf("2025-01-15", "2025-01-14", "2025-01-13")

        CollectionState.reset()
    }

    private fun createViewModel(ticker: String = testTicker): EtfDetailViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("ticker" to ticker))
        return EtfDetailViewModel(
            getEtfDetailUseCase = getEtfDetailUseCase,
            getEtfComparisonUseCase = getEtfComparisonUseCase,
            getComparisonInRangeUseCase = getComparisonInRangeUseCase,
            getAvailableDatesUseCase = getAvailableDatesUseCase,
            savedStateHandle = savedStateHandle
        )
    }

    // --- helpers ---

    private fun makeEtf(ticker: String, name: String) = Etf(ticker, name)

    private fun makeComparisonResult(
        etfTicker: String = testTicker
    ) = ComparisonResult(
        etfTicker = etfTicker,
        currentDate = "2025-01-15",
        previousDate = "2025-01-14",
        items = listOf(
            HoldingWithComparison(
                stockTicker = "005930",
                stockName = "삼성전자",
                currentWeight = 25.5f,
                previousWeight = 24.8f,
                change = 0.7f,
                currentAmount = 1_500_000f,
                status = HoldingStatus.INCREASE
            )
        ),
        collectionStartDate = "2024-01-01",
        collectionEndDate = "2025-01-15"
    )

    // ---------------------------------------------------------------
    // 초기 상태 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("초기 상태 테스트")
    inner class InitialStateTests {

        @Test
        @DisplayName("데이터 있을 때 초기화 후 Success 상태")
        fun hasData_initialState_isSuccess() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<EtfDetailState.Success>(state)
            }
        }

        @Test
        @DisplayName("ETF 이름이 etfName StateFlow에 설정됨")
        fun etfFound_etfNameIsSet() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.etfName.test {
                assertEquals(testEtfName, awaitItem())
            }
        }

        @Test
        @DisplayName("ETF 없을 때 ticker를 etfName으로 사용")
        fun etfNotFound_usesTickerAsName() = runTest {
            coEvery { getEtfDetailUseCase(testTicker) } returns null

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.etfName.test {
                assertEquals(testTicker, awaitItem())
            }
        }

        @Test
        @DisplayName("초기 selectedRange 는 MONTH")
        fun initialSelectedRange_isMonth() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectedRange.test {
                assertEquals(DateRangeOption.MONTH, awaitItem())
            }
        }

        @Test
        @DisplayName("availableDates 로드됨")
        fun availableDates_areLoaded() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.availableDates.test {
                val dates = awaitItem()
                assertTrue(dates.isNotEmpty())
                assertEquals(3, dates.size)
            }
        }
    }

    // ---------------------------------------------------------------
    // SavedStateHandle 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("SavedStateHandle ticker 파라미터 테스트")
    inner class SavedStateHandleTests {

        @Test
        @DisplayName("다른 ticker로 초기화 시 해당 ticker로 비교 데이터 조회")
        fun differentTicker_queriesCorrectTicker() = runTest {
            val otherTicker = "TIGER200"
            coEvery { getEtfDetailUseCase(otherTicker) } returns makeEtf(otherTicker, "TIGER 200")
            coEvery { getComparisonInRangeUseCase(eq(otherTicker), any(), any()) } returns makeComparisonResult(otherTicker)

            val savedStateHandle = SavedStateHandle(mapOf("ticker" to otherTicker))
            val viewModel = EtfDetailViewModel(
                getEtfDetailUseCase = getEtfDetailUseCase,
                getEtfComparisonUseCase = getEtfComparisonUseCase,
                getComparisonInRangeUseCase = getComparisonInRangeUseCase,
                getAvailableDatesUseCase = getAvailableDatesUseCase,
                savedStateHandle = savedStateHandle
            )
            advanceUntilIdle()

            coVerify { getEtfDetailUseCase(otherTicker) }
            coVerify { getComparisonInRangeUseCase(otherTicker, any(), any()) }
        }
    }

    // ---------------------------------------------------------------
    // 비교 데이터 로딩 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("비교 데이터 로딩 테스트")
    inner class ComparisonLoadingTests {

        @Test
        @DisplayName("비교 데이터 있을 때 Success 상태에 올바른 데이터")
        fun hasComparison_successStateHasCorrectData() = runTest {
            val comparison = makeComparisonResult()
            coEvery { getComparisonInRangeUseCase(any(), any(), any()) } returns comparison

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<EtfDetailState.Success>(state)
                assertEquals(testTicker, state.comparison.etfTicker)
                assertEquals(1, state.comparison.items.size)
            }
        }

        @Test
        @DisplayName("비교 데이터 없을 때 Error 상태")
        fun noComparison_producesErrorState() = runTest {
            coEvery { getComparisonInRangeUseCase(any(), any(), any()) } returns null

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<EtfDetailState.Error>(state)
                assertTrue(state.message.isNotEmpty())
            }
        }

        @Test
        @DisplayName("비교 데이터 로딩 예외 발생 시 Error 상태")
        fun exception_producesErrorState() = runTest {
            coEvery { getComparisonInRangeUseCase(any(), any(), any()) } throws RuntimeException("DB 오류")

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<EtfDetailState.Error>(state)
                assertTrue(state.message.contains("DB 오류"))
            }
        }
    }

    // ---------------------------------------------------------------
    // 날짜 범위 변경 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("날짜 범위 변경 테스트")
    inner class DateRangeUpdateTests {

        @Test
        @DisplayName("updateDateRange() 호출 시 selectedRange 업데이트")
        fun updateDateRange_updatesSelectedRange() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.updateDateRange(DateRangeOption.SIX_MONTHS)
            advanceUntilIdle()

            viewModel.selectedRange.test {
                assertEquals(DateRangeOption.SIX_MONTHS, awaitItem())
            }
        }

        @Test
        @DisplayName("updateDateRange() 호출 시 비교 데이터 재조회")
        fun updateDateRange_retriggersComparison() = runTest {
            var callCount = 0
            coEvery { getComparisonInRangeUseCase(any(), any(), any()) } answers {
                callCount++
                makeComparisonResult()
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            val callsBefore = callCount

            viewModel.updateDateRange(DateRangeOption.SIX_MONTHS)
            advanceUntilIdle()

            assertTrue(callCount > callsBefore, "Expected comparison to reload on date range change")
        }

        @Test
        @DisplayName("같은 날짜 범위 재설정 시 재로딩 없음")
        fun updateDateRange_sameOption_noReload() = runTest {
            var callCount = 0
            coEvery { getComparisonInRangeUseCase(any(), any(), any()) } answers {
                callCount++
                makeComparisonResult()
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            val callsBefore = callCount

            // Update with the same range (MONTH is the default)
            viewModel.updateDateRange(DateRangeOption.MONTH)
            advanceUntilIdle()

            assertEquals(callsBefore, callCount, "Expected no extra reload for same date range")
        }

        @Test
        @DisplayName("날짜 범위 변경 후 Success 상태 유지")
        fun updateDateRange_maintainsSuccessState() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.updateDateRange(DateRangeOption.YEAR)
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<EtfDetailState.Success>(awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // CollectionState 감지 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("CollectionState 감지 테스트")
    inner class CollectionStateTests {

        @Test
        @DisplayName("수집 완료 시 availableDates 자동 재로딩")
        fun collectionComplete_reloadsAvailableDates() = runTest {
            CollectionState.reset()

            val viewModel = createViewModel()
            advanceUntilIdle()

            CollectionState.startCollection(isInitialize = true)
            advanceUntilIdle()

            CollectionState.complete("done")
            advanceUntilIdle()

            // GetAvailableDatesUseCase should be called at least twice: init + after collection
            coVerify(atLeast = 2) { getAvailableDatesUseCase() }
        }

        @Test
        @DisplayName("수집 완료 후 Success 상태 유지")
        fun collectionComplete_stateRemainsSuccess() = runTest {
            CollectionState.reset()

            val viewModel = createViewModel()
            advanceUntilIdle()

            CollectionState.startCollection(isInitialize = false)
            advanceUntilIdle()

            CollectionState.complete("done")
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<EtfDetailState.Success>(awaitItem())
            }
        }
    }
}
