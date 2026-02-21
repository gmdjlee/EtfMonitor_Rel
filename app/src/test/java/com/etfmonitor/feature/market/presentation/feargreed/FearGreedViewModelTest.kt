package com.etfmonitor.feature.market.presentation.feargreed

import android.content.Context
import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.service.CollectionState
import com.etfmonitor.feature.market.domain.model.FearGreedIndex
import com.etfmonitor.feature.market.domain.repository.FearGreedRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
 * FearGreedViewModel 단위 테스트
 *
 * 테스트 범위:
 * - 초기화 시 checkData() 호출로 Loading → Idle 상태 전환
 * - initialize() 성공 시 Success 상태
 * - initialize() 실패 시 Error 상태
 * - update() 성공/실패 상태 전환
 * - clearMessage() 동작
 * - 첫 실행 다이얼로그 로직
 * - NonCancellable 래핑 (initialize 완료 보장)
 * - 시장 변경 시 selectedMarket 상태 갱신
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class FearGreedViewModelTest {

    private lateinit var repository: FearGreedRepository
    private lateinit var context: Context

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        context = mockk(relaxed = true)

        // Default: no data, no dialog dismissed
        coEvery { repository.getCountByMarket(any()) } returns 0
        coEvery { repository.getLatestDate(any()) } returns null
        coEvery { repository.isDialogDismissed() } returns true
        coEvery { repository.getByMarketAndDateRange(any(), any(), any()) } returns flowOf(emptyList())

        CollectionState.reset()
    }

    private fun createViewModel(): FearGreedViewModel =
        FearGreedViewModel(repository = repository, context = context)

    // --- helpers ---

    private fun makeFearGreedIndex(
        market: String = "KOSPI",
        date: String = "2025-01-15"
    ) = FearGreedIndex(
        id = "$market-$date",
        market = market,
        date = date,
        indexValue = 2500.0,
        fearGreedValue = 0.65,
        oscillator = 65.0,
        rsi = 55.0,
        momentum = 0.5,
        putCallRatio = 0.8,
        volatility = 0.2,
        spread = 0.1,
        lastUpdated = System.currentTimeMillis()
    )

    // ---------------------------------------------------------------
    // 초기 상태 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("초기 상태 테스트")
    inner class InitialStateTests {

        @Test
        @DisplayName("데이터 없을 때 Idle(hasData=false) 상태")
        fun noData_initialState_isIdleWithNoData() = runTest {
            coEvery { repository.getCountByMarket("KOSPI") } returns 0
            coEvery { repository.getCountByMarket("KOSDAQ") } returns 0
            coEvery { repository.getLatestDate("KOSPI") } returns null

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<FearGreedState.Idle>(state)
                assertFalse(state.hasData)
                assertEquals(null, state.latestDate)
            }
        }

        @Test
        @DisplayName("KOSPI 데이터 있을 때 Idle(hasData=true) 상태")
        fun kospiData_initialState_isIdleWithData() = runTest {
            val testDate = "2025-06-01"
            coEvery { repository.getCountByMarket("KOSPI") } returns 100
            coEvery { repository.getCountByMarket("KOSDAQ") } returns 0
            coEvery { repository.getLatestDate("KOSPI") } returns testDate

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<FearGreedState.Idle>(state)
                assertTrue(state.hasData)
                assertEquals(testDate, state.latestDate)
            }
        }

        @Test
        @DisplayName("KOSDAQ 데이터만 있을 때도 hasData=true")
        fun kosdaqDataOnly_initialState_hasDataIsTrue() = runTest {
            coEvery { repository.getCountByMarket("KOSPI") } returns 0
            coEvery { repository.getCountByMarket("KOSDAQ") } returns 50
            coEvery { repository.getLatestDate("KOSPI") } returns null

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<FearGreedState.Idle>(state)
                assertTrue(state.hasData)
            }
        }

        @Test
        @DisplayName("초기 selectedMarket 은 KOSPI")
        fun initialSelectedMarket_isKospi() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectedMarket.test {
                assertEquals("KOSPI", awaitItem())
            }
        }

        @Test
        @DisplayName("초기 selectedRange 는 DateRangeOption.DEFAULT(YEAR)")
        fun initialSelectedRange_isDefault() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectedRange.test {
                val range = awaitItem()
                // DateRangeOption.DEFAULT == YEAR (days=365)
                assertEquals(365, range.days)
            }
        }
    }

    // ---------------------------------------------------------------
    // initialize() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("initialize() 테스트")
    inner class InitializeTests {

        @Test
        @DisplayName("initialize() 성공 시 Success 상태 후 Idle로 전환")
        fun initialize_success_producesSuccessStateAndIdle() = runTest {
            coEvery { repository.initializeFearGreed(any(), any()) } returns Result.success(30)
            coEvery { repository.getCountByMarket("KOSPI") } returns 30
            coEvery { repository.getLatestDate("KOSPI") } returns "2025-06-15"

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.initialize(90)
            advanceUntilIdle()

            // After success, checkData() is called which emits Idle
            viewModel.state.test {
                val state = awaitItem()
                // The final steady state should be Idle (success already transitioned through)
                assertTrue(
                    state is FearGreedState.Success || state is FearGreedState.Idle,
                    "Expected Success or Idle, got $state"
                )
            }
        }

        @Test
        @DisplayName("initialize() 성공 시 Initializing 상태 먼저 설정")
        fun initialize_setsInitializingStateFirst() = runTest {
            // Suspend the repository call so we can observe Initializing
            coEvery { repository.initializeFearGreed(any(), any()) } coAnswers {
                kotlinx.coroutines.delay(100)
                Result.success(10)
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                // clear any current state
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.initialize(90)

            viewModel.state.test {
                val first = awaitItem()
                assertIs<FearGreedState.Initializing>(first)
                assertTrue(first.message.isNotEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("initialize() 실패 시 Error 상태 설정")
        fun initialize_failure_producesErrorState() = runTest {
            coEvery { repository.initializeFearGreed(any(), any()) } returns
                Result.failure(RuntimeException("Network error"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.initialize(90)
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<FearGreedState.Error>(state)
                assertTrue(state.message.contains("Network error"))
            }
        }

        @Test
        @DisplayName("initialize() 성공 시 repository.initializeFearGreed 호출 후 checkData 호출")
        fun initialize_success_callsRepositoryAndUpdatesData() = runTest {
            val count = 42
            coEvery { repository.initializeFearGreed(any(), any()) } returns Result.success(count)
            coEvery { repository.getCountByMarket("KOSPI") } returns count
            coEvery { repository.getLatestDate("KOSPI") } returns "2025-06-01"

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.initialize(90)
            advanceUntilIdle()

            // Verify repository was called with expected days
            coVerify { repository.initializeFearGreed(90, any()) }
            // After success, checkData is called — final state should be Idle with hasData=true
            viewModel.state.test {
                val state = awaitItem()
                assertIs<FearGreedState.Idle>(state)
                assertTrue(state.hasData)
            }
        }

        @Test
        @DisplayName("initialize() 기본값은 365일")
        fun initialize_defaultDays_is365() = runTest {
            coEvery { repository.initializeFearGreed(any(), any()) } returns Result.success(100)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.initialize()
            advanceUntilIdle()

            coVerify { repository.initializeFearGreed(365, any()) }
        }
    }

    // ---------------------------------------------------------------
    // update() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("update() 테스트")
    inner class UpdateTests {

        @Test
        @DisplayName("update() 성공 시 updateFearGreed() 호출 후 checkData 갱신")
        fun update_success_callsRepositoryAndUpdatesData() = runTest {
            val count = 7
            coEvery { repository.updateFearGreed() } returns Result.success(count)
            coEvery { repository.getCountByMarket("KOSPI") } returns count
            coEvery { repository.getLatestDate("KOSPI") } returns "2025-06-10"

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.update()
            advanceUntilIdle()

            // Verify repository was called
            coVerify { repository.updateFearGreed() }
            // After success, checkData is called — final state should be Idle with hasData=true
            viewModel.state.test {
                val state = awaitItem()
                assertIs<FearGreedState.Idle>(state)
                assertTrue(state.hasData)
            }
        }

        @Test
        @DisplayName("update() 실패 시 Error 상태")
        fun update_failure_producesErrorState() = runTest {
            coEvery { repository.updateFearGreed() } returns
                Result.failure(RuntimeException("Timeout"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.update()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<FearGreedState.Error>(state)
                assertTrue(state.message.contains("Timeout"))
            }
        }

        @Test
        @DisplayName("update() 시작 시 Updating 상태 먼저 설정")
        fun update_setsUpdatingStateFirst() = runTest {
            coEvery { repository.updateFearGreed() } coAnswers {
                kotlinx.coroutines.delay(100)
                Result.success(5)
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.update()

            viewModel.state.test {
                val first = awaitItem()
                assertIs<FearGreedState.Updating>(first)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // ---------------------------------------------------------------
    // clearMessage() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("clearMessage() 테스트")
    inner class ClearMessageTests {

        @Test
        @DisplayName("Success 상태에서 clearMessage() 시 checkData() 호출")
        fun clearMessage_fromSuccess_callsCheckData() = runTest {
            coEvery { repository.updateFearGreed() } returns Result.success(3)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.update()
            advanceUntilIdle()

            // Now in Success state — clear it
            viewModel.clearMessage()
            advanceUntilIdle()

            // Should have called getCountByMarket again (from checkData inside clearMessage)
            // At minimum 2 calls: init + clearMessage
            coVerify(atLeast = 2) { repository.getCountByMarket(any()) }
        }

        @Test
        @DisplayName("Error 상태에서 clearMessage() 시 Idle 상태로 전환")
        fun clearMessage_fromError_transitionsToIdle() = runTest {
            coEvery { repository.updateFearGreed() } returns
                Result.failure(RuntimeException("err"))
            coEvery { repository.getCountByMarket(any()) } returns 5
            coEvery { repository.getLatestDate("KOSPI") } returns "2025-01-01"

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.update()
            advanceUntilIdle()

            viewModel.clearMessage()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<FearGreedState.Idle>(state)
            }
        }

        @Test
        @DisplayName("Idle 상태에서 clearMessage() 는 아무것도 하지 않음")
        fun clearMessage_fromIdle_noChange() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                awaitItem() // current Idle state

                viewModel.clearMessage()
                advanceUntilIdle()

                // No additional state change should occur — clearMessage() is a no-op when Idle
                expectNoEvents()
            }
        }
    }

    // ---------------------------------------------------------------
    // 시장 변경 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("onSelectedMarketChanged() 테스트")
    inner class MarketChangeTests {

        @Test
        @DisplayName("시장 변경 시 selectedMarket 업데이트")
        fun onSelectedMarketChanged_updatesSelectedMarket() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSelectedMarketChanged("KOSDAQ")
            advanceUntilIdle()

            viewModel.selectedMarket.test {
                assertEquals("KOSDAQ", awaitItem())
            }
        }

        @Test
        @DisplayName("시장 변경 시 해당 시장의 최신 날짜로 상태 갱신")
        fun onSelectedMarketChanged_updatesLatestDate() = runTest {
            val kosdaqDate = "2025-05-10"
            coEvery { repository.getCountByMarket("KOSPI") } returns 10
            coEvery { repository.getCountByMarket("KOSDAQ") } returns 20
            coEvery { repository.getLatestDate("KOSDAQ") } returns kosdaqDate

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSelectedMarketChanged("KOSDAQ")
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<FearGreedState.Idle>(state)
                assertEquals(kosdaqDate, state.latestDate)
            }
        }
    }

    // ---------------------------------------------------------------
    // 첫 실행 다이얼로그 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("첫 실행 다이얼로그 테스트")
    inner class FirstRunDialogTests {

        @Test
        @DisplayName("데이터 없고 다이얼로그 미표시 시 showFirstRunDialog=true")
        fun noData_dialogNotDismissed_showsFirstRunDialog() = runTest {
            coEvery { repository.getCountByMarket(any()) } returns 0
            coEvery { repository.isDialogDismissed() } returns false

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showFirstRunDialog.test {
                assertTrue(awaitItem())
            }
        }

        @Test
        @DisplayName("다이얼로그 이미 닫혔으면 showFirstRunDialog=false")
        fun dialogAlreadyDismissed_showFirstRunDialog_isFalse() = runTest {
            coEvery { repository.getCountByMarket(any()) } returns 0
            coEvery { repository.isDialogDismissed() } returns true

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showFirstRunDialog.test {
                assertFalse(awaitItem())
            }
        }

        @Test
        @DisplayName("데이터 있으면 showFirstRunDialog=false")
        fun hasData_showFirstRunDialog_isFalse() = runTest {
            coEvery { repository.getCountByMarket("KOSPI") } returns 100
            coEvery { repository.isDialogDismissed() } returns false

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showFirstRunDialog.test {
                assertFalse(awaitItem())
            }
        }

        @Test
        @DisplayName("onFirstRunDialogShown() 호출 시 다이얼로그 닫힘")
        fun onFirstRunDialogShown_closesDialog() = runTest {
            coEvery { repository.getCountByMarket(any()) } returns 0
            coEvery { repository.isDialogDismissed() } returns false

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onFirstRunDialogShown()
            advanceUntilIdle()

            viewModel.showFirstRunDialog.test {
                assertFalse(awaitItem())
            }
        }

        @Test
        @DisplayName("onFirstRunDialogConfirmed() 호출 시 다이얼로그 닫고 dismissed 상태 저장")
        fun onFirstRunDialogConfirmed_closesDialogAndSavesDismissed() = runTest {
            coEvery { repository.getCountByMarket(any()) } returns 0
            coEvery { repository.isDialogDismissed() } returns false

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onFirstRunDialogConfirmed()
            advanceUntilIdle()

            coVerify { repository.saveDialogDismissed() }
            viewModel.showFirstRunDialog.test {
                assertFalse(awaitItem())
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
        @DisplayName("수집 완료(isCollecting=false) 시 checkData() 자동 호출")
        fun collectionComplete_triggersCheckData() = runTest {
            CollectionState.reset()

            val viewModel = createViewModel()
            advanceUntilIdle()

            val callsBefore = mutableListOf<Int>()
            // Count how many times getCountByMarket is called

            // Simulate collection completing
            CollectionState.startCollection(isInitialize = true)
            advanceUntilIdle()

            CollectionState.complete("done")
            advanceUntilIdle()

            // After completion, checkData() should have been called again (at least 2 total)
            coVerify(atLeast = 2) { repository.getCountByMarket(any()) }
        }
    }
}
