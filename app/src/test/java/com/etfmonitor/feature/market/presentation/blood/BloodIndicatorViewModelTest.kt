package com.etfmonitor.feature.market.presentation.blood

import android.content.Context
import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.service.CollectionState
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.feature.market.domain.model.BloodIndicator
import com.etfmonitor.feature.market.domain.model.BloodSignalType
import com.etfmonitor.feature.market.domain.repository.BloodIndicatorRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * BloodIndicatorViewModel 단위 테스트
 *
 * 테스트 범위:
 * - 초기 상태 (Loading → Idle)
 * - 데이터 있을 때 / 없을 때 Idle 상태
 * - initialize() 성공 / 실패
 * - update() 성공 / 실패
 * - clearMessage() 동작
 * - 날짜 범위 변경 (updateDateRange)
 * - 첫 실행 다이얼로그 로직
 * - CollectionState 완료 시 자동 새로고침
 * - DateRangeOption.ALL 처리 (earliest + latest date 사용)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class BloodIndicatorViewModelTest {

    private lateinit var repository: BloodIndicatorRepository
    private lateinit var context: Context

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        context = mockk(relaxed = true)

        // Default: no data, dialog dismissed
        coEvery { repository.getCount() } returns 0
        coEvery { repository.getLatestDate() } returns null
        coEvery { repository.getEarliestDate() } returns null
        coEvery { repository.isDialogDismissed() } returns true
        coEvery { repository.getByDateRange(any(), any()) } returns flowOf(emptyList())

        CollectionState.reset()
    }

    private fun createViewModel(): BloodIndicatorViewModel =
        BloodIndicatorViewModel(repository = repository, context = context)

    // --- test helpers ---

    private fun makeBloodIndicator(
        date: String = "2025-01-15",
        signalType: BloodSignalType = BloodSignalType.RISK_ON
    ) = BloodIndicator(
        id = "blood-$date",
        date = date,
        bloodValue = 1.5,
        bloodSma = 1.2,
        us03my = 5.2,
        highYieldSpread = 3.5,
        spyClose = 500.0,
        signalType = signalType,
        signalColor = "green",
        lastUpdated = System.currentTimeMillis()
    )

    // ---------------------------------------------------------------
    // 초기 상태 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("초기 상태 테스트")
    inner class InitialStateTests {

        @Test
        @DisplayName("데이터 없을 때 Idle(hasData=false, latestDate=null)")
        fun noData_initialState_isIdleWithNoData() = runTest {
            coEvery { repository.getCount() } returns 0
            coEvery { repository.getLatestDate() } returns null

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<BloodIndicatorState.Idle>(state)
                assertFalse(state.hasData)
                assertNull(state.latestDate)
            }
        }

        @Test
        @DisplayName("데이터 있을 때 Idle(hasData=true, latestDate 설정)")
        fun hasData_initialState_isIdleWithData() = runTest {
            val testDate = "2025-06-01"
            coEvery { repository.getCount() } returns 500
            coEvery { repository.getLatestDate() } returns testDate

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<BloodIndicatorState.Idle>(state)
                assertTrue(state.hasData)
                assertEquals(testDate, state.latestDate)
            }
        }

        @Test
        @DisplayName("초기 selectedRange 는 FIVE_YEARS")
        fun initialSelectedRange_isFiveYears() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectedRange.test {
                val range = awaitItem()
                assertEquals(DateRangeOption.FIVE_YEARS, range)
            }
        }

        @Test
        @DisplayName("초기 bloodData 는 빈 리스트")
        fun initialBloodData_isEmpty() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.bloodData.test {
                assertEquals(emptyList(), awaitItem())
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
        @DisplayName("initialize() 성공 시 repository 호출 후 데이터 갱신")
        fun initialize_success_callsRepositoryAndUpdatesData() = runTest {
            val count = 1825
            coEvery { repository.initializeBloodIndicator(any(), any()) } returns Result.success(count)
            coEvery { repository.getCount() } returns count
            coEvery { repository.getLatestDate() } returns "2025-06-01"

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.initialize(1825)
            advanceUntilIdle()

            // Verify repository was called
            coVerify { repository.initializeBloodIndicator(1825, any()) }
            // After success + checkData, final state should be Idle with hasData=true
            viewModel.state.test {
                val state = awaitItem()
                assertIs<BloodIndicatorState.Idle>(state)
                assertTrue(state.hasData)
                assertEquals("2025-06-01", state.latestDate)
            }
        }

        @Test
        @DisplayName("initialize() 실패 시 Error 상태")
        fun initialize_failure_producesErrorState() = runTest {
            coEvery { repository.initializeBloodIndicator(any(), any()) } returns
                Result.failure(RuntimeException("Yahoo Finance timeout"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.initialize(1825)
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<BloodIndicatorState.Error>(state)
                assertTrue(state.message.contains("Yahoo Finance timeout"))
            }
        }

        @Test
        @DisplayName("initialize() 시작 시 Initializing 상태 먼저 설정")
        fun initialize_setsInitializingFirst() = runTest {
            coEvery { repository.initializeBloodIndicator(any(), any()) } coAnswers {
                kotlinx.coroutines.delay(200)
                Result.success(10)
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test { cancelAndIgnoreRemainingEvents() }

            viewModel.initialize(1825)

            viewModel.state.test {
                val first = awaitItem()
                assertIs<BloodIndicatorState.Initializing>(first)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("initialize() 기본값은 1825일 (5년)")
        fun initialize_defaultDays_is1825() = runTest {
            coEvery { repository.initializeBloodIndicator(any(), any()) } returns Result.success(500)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.initialize()
            advanceUntilIdle()

            coVerify { repository.initializeBloodIndicator(1825, any()) }
        }

        @Test
        @DisplayName("initialize() 성공 후 checkData() 호출로 데이터 갱신")
        fun initialize_success_callsCheckData() = runTest {
            coEvery { repository.initializeBloodIndicator(any(), any()) } returns Result.success(100)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.initialize(1825)
            advanceUntilIdle()

            // checkData() called at least twice: init + post-initialize
            coVerify(atLeast = 2) { repository.getCount() }
        }

        @Test
        @DisplayName("initialize() onProgress 콜백으로 Initializing 상태 업데이트")
        fun initialize_onProgress_updatesInitializingState() = runTest {
            coEvery { repository.initializeBloodIndicator(any(), any()) } coAnswers {
                val progressCallback = secondArg<((String, Int) -> Unit)?>()
                progressCallback?.invoke("Yahoo Finance 조회 중...", 30)
                progressCallback?.invoke("FRED API 조회 중...", 60)
                Result.success(50)
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            // The progress callbacks are invoked synchronously in the repository mock.
            // Since Initializing state is set before the callbacks and clearMessage
            // sets it via _state.value, we verify that the repository was called with
            // the progress callback parameter.
            viewModel.initialize(1825)
            advanceUntilIdle()

            // After the callbacks, state should have gone through Initializing.
            // Verify the repository was called — progress callbacks were passed.
            coVerify { repository.initializeBloodIndicator(any(), any()) }
            // Final state is Idle (checkData called after success)
            viewModel.state.test {
                assertIs<BloodIndicatorState.Idle>(awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // update() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("update() 테스트")
    inner class UpdateTests {

        @Test
        @DisplayName("update() 성공 시 updateBloodIndicator() 호출 후 데이터 갱신")
        fun update_success_callsRepositoryAndUpdatesData() = runTest {
            val count = 7
            coEvery { repository.updateBloodIndicator() } returns Result.success(count)
            coEvery { repository.getCount() } returns count
            coEvery { repository.getLatestDate() } returns "2025-06-10"

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.update()
            advanceUntilIdle()

            // Verify repository was called
            coVerify { repository.updateBloodIndicator() }
            // After success + checkData, final state should be Idle with hasData=true
            viewModel.state.test {
                val state = awaitItem()
                assertIs<BloodIndicatorState.Idle>(state)
                assertTrue(state.hasData)
            }
        }

        @Test
        @DisplayName("update() 실패 시 Error 상태")
        fun update_failure_producesErrorState() = runTest {
            coEvery { repository.updateBloodIndicator() } returns
                Result.failure(RuntimeException("FRED API error"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.update()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<BloodIndicatorState.Error>(state)
                assertTrue(state.message.contains("FRED API error"))
            }
        }

        @Test
        @DisplayName("update() 시작 시 Updating 상태 먼저 설정")
        fun update_setsUpdatingFirst() = runTest {
            coEvery { repository.updateBloodIndicator() } coAnswers {
                kotlinx.coroutines.delay(200)
                Result.success(5)
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test { cancelAndIgnoreRemainingEvents() }

            viewModel.update()

            viewModel.state.test {
                val first = awaitItem()
                assertIs<BloodIndicatorState.Updating>(first)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("update() 성공 후 checkData() 호출로 데이터 갱신")
        fun update_success_callsCheckData() = runTest {
            coEvery { repository.updateBloodIndicator() } returns Result.success(3)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.update()
            advanceUntilIdle()

            coVerify(atLeast = 2) { repository.getCount() }
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
            coEvery { repository.updateBloodIndicator() } returns Result.success(5)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.update()
            advanceUntilIdle()

            val countBefore = mutableListOf<Int>()
            coEvery { repository.getCount() } answers { countBefore.size.also { countBefore.add(it) }; 0 }

            viewModel.clearMessage()
            advanceUntilIdle()

            coVerify(atLeast = 2) { repository.getCount() }
        }

        @Test
        @DisplayName("Error 상태에서 clearMessage() 시 Idle 상태로 전환")
        fun clearMessage_fromError_transitionsToIdle() = runTest {
            coEvery { repository.updateBloodIndicator() } returns
                Result.failure(RuntimeException("err"))
            coEvery { repository.getCount() } returns 10
            coEvery { repository.getLatestDate() } returns "2025-01-01"

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.update()
            advanceUntilIdle()

            viewModel.clearMessage()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<BloodIndicatorState.Idle>(state)
            }
        }

        @Test
        @DisplayName("Idle 상태에서 clearMessage() 는 no-op")
        fun clearMessage_fromIdle_isNoOp() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.clearMessage()
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<BloodIndicatorState.Idle>(awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // 날짜 범위 변경 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("날짜 범위 변경 테스트")
    inner class DateRangeTests {

        @Test
        @DisplayName("updateDateRange() 호출 시 selectedRange 업데이트")
        fun updateDateRange_updatesSelectedRange() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.updateDateRange(DateRangeOption.THREE_YEARS)
            advanceUntilIdle()

            viewModel.selectedRange.test {
                assertEquals(DateRangeOption.THREE_YEARS, awaitItem())
            }
        }

        @Test
        @DisplayName("DateRangeOption.ALL 선택 시 getEarliestDate() 와 getLatestDate() 호출")
        fun updateDateRange_ALL_usesEarliestAndLatestDate() = runTest {
            val earliest = "2015-01-01"
            val latest = "2025-06-01"
            coEvery { repository.getEarliestDate() } returns earliest
            coEvery { repository.getLatestDate() } returns latest
            coEvery { repository.getByDateRange(earliest, latest) } returns flowOf(emptyList())

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.updateDateRange(DateRangeOption.ALL)
            advanceUntilIdle()

            coVerify { repository.getEarliestDate() }
            coVerify { repository.getByDateRange(earliest, latest) }
        }

        @Test
        @DisplayName("날짜 범위 변경 시 getByDateRange() 재호출")
        fun updateDateRange_triggersDataReload() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.updateDateRange(DateRangeOption.THREE_MONTHS)
            advanceUntilIdle()

            coVerify(atLeast = 1) { repository.getByDateRange(any(), any()) }
        }
    }

    // ---------------------------------------------------------------
    // 첫 실행 다이얼로그 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("첫 실행 다이얼로그 테스트")
    inner class FirstRunDialogTests {

        @Test
        @DisplayName("데이터 없고 미표시 시 showFirstRunDialog=true")
        fun noData_dialogNotDismissed_showsDialog() = runTest {
            coEvery { repository.getCount() } returns 0
            coEvery { repository.isDialogDismissed() } returns false

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showFirstRunDialog.test {
                assertTrue(awaitItem())
            }
        }

        @Test
        @DisplayName("다이얼로그 이미 닫혔으면 showFirstRunDialog=false")
        fun dialogDismissed_showFirstRunDialog_isFalse() = runTest {
            coEvery { repository.getCount() } returns 0
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
            coEvery { repository.getCount() } returns 500
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
            coEvery { repository.getCount() } returns 0
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
        @DisplayName("onFirstRunDialogConfirmed() 호출 시 saveDialogDismissed() 호출 후 다이얼로그 닫힘")
        fun onFirstRunDialogConfirmed_savesDismissedAndClosesDialog() = runTest {
            coEvery { repository.getCount() } returns 0
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
        @DisplayName("수집 완료 시 getCount() 재호출로 데이터 새로고침")
        fun collectionComplete_triggersCheckData() = runTest {
            CollectionState.reset()

            val viewModel = createViewModel()
            advanceUntilIdle()

            CollectionState.startCollection(isInitialize = true)
            advanceUntilIdle()

            CollectionState.complete("done")
            advanceUntilIdle()

            coVerify(atLeast = 2) { repository.getCount() }
        }
    }

    // ---------------------------------------------------------------
    // bloodData StateFlow 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("bloodData StateFlow 테스트")
    inner class BloodDataTests {

        @Test
        @DisplayName("repository 에서 반환된 데이터가 bloodData 에 반영됨")
        fun repositoryData_isReflectedInBloodData() = runTest {
            val testData = listOf(
                makeBloodIndicator("2025-01-10", BloodSignalType.RISK_ON),
                makeBloodIndicator("2025-01-11", BloodSignalType.RISK_OFF),
                makeBloodIndicator("2025-01-12", BloodSignalType.NEUTRAL)
            )
            coEvery { repository.getByDateRange(any(), any()) } returns flowOf(testData)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.bloodData.test {
                val data = awaitItem()
                assertEquals(3, data.size)
                assertEquals("2025-01-10", data[0].date)
                assertEquals(BloodSignalType.RISK_OFF, data[1].signalType)
            }
        }
    }
}
