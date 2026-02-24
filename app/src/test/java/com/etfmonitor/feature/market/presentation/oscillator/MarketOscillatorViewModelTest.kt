package com.etfmonitor.feature.market.presentation.oscillator

import android.content.Context
import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.service.CollectionState
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.core.ui.theme.FontScaleSettings
import com.etfmonitor.core.ui.theme.ThemeManager
import com.etfmonitor.feature.market.domain.model.MarketOscillator
import com.etfmonitor.feature.market.domain.repository.MarketOscillatorRepository
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
 * MarketOscillatorViewModel 단위 테스트
 *
 * 테스트 범위:
 * - 초기 상태 (Loading → Idle)
 * - initialize() KOSPI + KOSDAQ 순차 수집
 * - 15초 KRX rate-limit 쿨다운 (NonCancellable 래핑)
 * - update() 성공 / 실패 상태
 * - clearMessage() 동작
 * - 시장 변경 시 selectedMarket 갱신
 * - 임계값 변경 (overbought/oversold)
 * - 첫 실행 다이얼로그 로직
 * - CollectionState 완료 시 자동 새로고침
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class MarketOscillatorViewModelTest {

    private lateinit var repository: MarketOscillatorRepository
    private lateinit var themeManager: ThemeManager
    private lateinit var context: Context

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        themeManager = ThemeManager()
        context = mockk(relaxed = true)

        // Default: no data, dialog dismissed
        coEvery { repository.getDataCount(any()) } returns 0
        coEvery { repository.getLatestData(any()) } returns null
        coEvery { repository.isDialogDismissed() } returns true
        coEvery { repository.getDataByDateRange(any(), any(), any()) } returns flowOf(emptyList())

        CollectionState.reset()
    }

    private fun createViewModel(): MarketOscillatorViewModel =
        MarketOscillatorViewModel(
            repository = repository,
            themeManager = themeManager,
            context = context
        )

    // --- test helpers ---

    private fun makeOscillator(
        market: String = "KOSPI",
        date: String = "2025-01-15",
        oscillator: Double = 75.0
    ) = MarketOscillator(
        id = "$market-$date",
        market = market,
        date = date,
        indexValue = 2500.0,
        oscillator = oscillator,
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
            coEvery { repository.getDataCount("KOSPI") } returns 0
            coEvery { repository.getDataCount("KOSDAQ") } returns 0
            coEvery { repository.getLatestData("KOSPI") } returns null

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<MarketOscillatorState.Idle>(state)
                assertFalse(state.hasData)
                assertEquals(null, state.latestDate)
            }
        }

        @Test
        @DisplayName("KOSPI 데이터 있을 때 Idle(hasData=true) 상태")
        fun kospiHasData_initialState_isIdleWithData() = runTest {
            val testDate = "2025-06-01"
            coEvery { repository.getDataCount("KOSPI") } returns 200
            coEvery { repository.getDataCount("KOSDAQ") } returns 0
            coEvery { repository.getLatestData("KOSPI") } returns makeOscillator(date = testDate)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<MarketOscillatorState.Idle>(state)
                assertTrue(state.hasData)
                assertEquals(testDate, state.latestDate)
            }
        }

        @Test
        @DisplayName("KOSDAQ 데이터만 있을 때도 hasData=true")
        fun kosdaqDataOnly_hasDataIsTrue() = runTest {
            coEvery { repository.getDataCount("KOSPI") } returns 0
            coEvery { repository.getDataCount("KOSDAQ") } returns 50

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<MarketOscillatorState.Idle>(state)
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
        @DisplayName("초기 overboughtThreshold 는 80.0")
        fun initialOverboughtThreshold_is80() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.overboughtThreshold.test {
                assertEquals(80.0, awaitItem())
            }
        }

        @Test
        @DisplayName("초기 oversoldThreshold 는 -80.0")
        fun initialOversoldThreshold_isMinus80() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.oversoldThreshold.test {
                assertEquals(-80.0, awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // initialize() 테스트 (KOSPI + KOSDAQ 순차 수집)
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("initialize() 테스트")
    inner class InitializeTests {

        @Test
        @DisplayName("KOSPI + KOSDAQ 모두 성공 시 두 시장 모두 initializeMarketData 호출")
        fun initialize_bothSucceed_callsBothMarkets() = runTest {
            val kospiCount = 250
            val kosdaqCount = 200
            coEvery { repository.initializeMarketData("KOSPI", any(), any()) } returns
                Result.success(kospiCount)
            coEvery { repository.initializeMarketData("KOSDAQ", any(), any()) } returns
                Result.success(kosdaqCount)
            coEvery { repository.getDataCount("KOSPI") } returns kospiCount
            coEvery { repository.getDataCount("KOSDAQ") } returns kosdaqCount

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.initialize(365)
            advanceUntilIdle()

            // Both markets should have been initialized
            coVerify { repository.initializeMarketData("KOSPI", 365, any()) }
            coVerify { repository.initializeMarketData("KOSDAQ", 365, any()) }
            // After success + checkData, final state should be Idle with hasData=true
            viewModel.state.test {
                val state = awaitItem()
                assertIs<MarketOscillatorState.Idle>(state)
                assertTrue(state.hasData)
            }
        }

        @Test
        @DisplayName("KOSPI 실패 시 Error 상태")
        fun initialize_kosp_fails_producesErrorState() = runTest {
            coEvery { repository.initializeMarketData("KOSPI", any(), any()) } returns
                Result.failure(RuntimeException("KOSPI network error"))
            coEvery { repository.initializeMarketData("KOSDAQ", any(), any()) } returns
                Result.success(100)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.initialize(365)
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<MarketOscillatorState.Error>(state)
                assertTrue(state.message.contains("KOSPI network error"))
            }
        }

        @Test
        @DisplayName("KOSDAQ 실패 시 Error 상태")
        fun initialize_kosdaq_fails_producesErrorState() = runTest {
            coEvery { repository.initializeMarketData("KOSPI", any(), any()) } returns
                Result.success(250)
            coEvery { repository.initializeMarketData("KOSDAQ", any(), any()) } returns
                Result.failure(RuntimeException("KOSDAQ timeout"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.initialize(365)
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<MarketOscillatorState.Error>(state)
                assertTrue(state.message.contains("KOSDAQ timeout"))
            }
        }

        @Test
        @DisplayName("initialize() 시작 시 Initializing 상태 먼저 설정")
        fun initialize_setsInitializingFirst() = runTest {
            coEvery { repository.initializeMarketData(any(), any(), any()) } coAnswers {
                kotlinx.coroutines.delay(200)
                Result.success(10)
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test { cancelAndIgnoreRemainingEvents() }

            viewModel.initialize(365)

            viewModel.state.test {
                val first = awaitItem()
                assertIs<MarketOscillatorState.Initializing>(first)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("KOSPI 수집 완료 후 KOSDAQ 수집 호출 (순차 처리)")
        fun initialize_callsKospiThenKosdaq_inOrder() = runTest {
            val callOrder = mutableListOf<String>()
            coEvery { repository.initializeMarketData("KOSPI", any(), any()) } coAnswers {
                callOrder.add("KOSPI")
                Result.success(100)
            }
            coEvery { repository.initializeMarketData("KOSDAQ", any(), any()) } coAnswers {
                callOrder.add("KOSDAQ")
                Result.success(100)
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.initialize(365)
            advanceUntilIdle()

            assertEquals(listOf("KOSPI", "KOSDAQ"), callOrder)
        }

        @Test
        @DisplayName("initialize() 기본값은 365일")
        fun initialize_defaultDays_is365() = runTest {
            coEvery { repository.initializeMarketData(any(), any(), any()) } returns Result.success(50)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.initialize()
            advanceUntilIdle()

            coVerify { repository.initializeMarketData("KOSPI", 365, any()) }
            coVerify { repository.initializeMarketData("KOSDAQ", 365, any()) }
        }
    }

    // ---------------------------------------------------------------
    // update() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("update() 테스트")
    inner class UpdateTests {

        @Test
        @DisplayName("update() KOSPI + KOSDAQ 모두 성공 시 두 시장 모두 updateMarketData 호출")
        fun update_bothSucceed_callsBothMarkets() = runTest {
            coEvery { repository.updateMarketData("KOSPI") } returns Result.success(5)
            coEvery { repository.updateMarketData("KOSDAQ") } returns Result.success(3)
            coEvery { repository.getDataCount("KOSPI") } returns 200
            coEvery { repository.getDataCount("KOSDAQ") } returns 150

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.update()
            advanceUntilIdle()

            // Both markets should have been updated
            coVerify { repository.updateMarketData("KOSPI") }
            coVerify { repository.updateMarketData("KOSDAQ") }
            // After success + checkData, final state should be Idle with hasData=true
            viewModel.state.test {
                val state = awaitItem()
                assertIs<MarketOscillatorState.Idle>(state)
                assertTrue(state.hasData)
            }
        }

        @Test
        @DisplayName("update() KOSPI 실패 시 Error 상태")
        fun update_kosp_fails_producesErrorState() = runTest {
            coEvery { repository.updateMarketData("KOSPI") } returns
                Result.failure(RuntimeException("Update failed"))
            coEvery { repository.updateMarketData("KOSDAQ") } returns Result.success(2)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.update()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<MarketOscillatorState.Error>(state)
                assertTrue(state.message.contains("Update failed"))
            }
        }

        @Test
        @DisplayName("update() 시작 시 Updating 상태 먼저 설정")
        fun update_setsUpdatingFirst() = runTest {
            coEvery { repository.updateMarketData(any()) } coAnswers {
                kotlinx.coroutines.delay(200)
                Result.success(1)
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test { cancelAndIgnoreRemainingEvents() }

            viewModel.update()

            viewModel.state.test {
                val first = awaitItem()
                assertIs<MarketOscillatorState.Updating>(first)
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
        @DisplayName("Error 상태에서 clearMessage() 시 Idle 상태로 전환")
        fun clearMessage_fromError_transitionsToIdle() = runTest {
            coEvery { repository.updateMarketData(any()) } returns
                Result.failure(RuntimeException("err"))
            coEvery { repository.getDataCount("KOSPI") } returns 10
            coEvery { repository.getLatestData("KOSPI") } returns makeOscillator(date = "2025-01-01")

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.update()
            advanceUntilIdle()

            viewModel.clearMessage()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<MarketOscillatorState.Idle>(state)
            }
        }

        @Test
        @DisplayName("Idle 상태에서 clearMessage() 는 no-op")
        fun clearMessage_fromIdle_isNoOp() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Should not throw or change state
            viewModel.clearMessage()
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<MarketOscillatorState.Idle>(awaitItem())
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
        @DisplayName("KOSDAQ 선택 시 selectedMarket = KOSDAQ")
        fun selectKosdaq_updatesSelectedMarket() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSelectedMarketChanged("KOSDAQ")
            advanceUntilIdle()

            viewModel.selectedMarket.test {
                assertEquals("KOSDAQ", awaitItem())
            }
        }

        @Test
        @DisplayName("시장 변경 시 해당 시장 최신 데이터로 latestDate 갱신")
        fun selectKosdaq_updatesLatestDateFromKosdaq() = runTest {
            val kosdaqDate = "2025-06-15"
            coEvery { repository.getDataCount("KOSPI") } returns 10
            coEvery { repository.getDataCount("KOSDAQ") } returns 20
            coEvery { repository.getLatestData("KOSDAQ") } returns makeOscillator(
                market = "KOSDAQ",
                date = kosdaqDate
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSelectedMarketChanged("KOSDAQ")
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<MarketOscillatorState.Idle>(state)
                assertEquals(kosdaqDate, state.latestDate)
            }
        }
    }

    // ---------------------------------------------------------------
    // 임계값 변경 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("임계값 변경 테스트")
    inner class ThresholdTests {

        @Test
        @DisplayName("onOverboughtThresholdChanged() 는 overboughtThreshold 업데이트")
        fun overboughtThresholdChanged_updatesStateFlow() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onOverboughtThresholdChanged(90.0)
            advanceUntilIdle()

            viewModel.overboughtThreshold.test {
                assertEquals(90.0, awaitItem())
            }
        }

        @Test
        @DisplayName("onOversoldThresholdChanged() 는 oversoldThreshold 업데이트")
        fun oversoldThresholdChanged_updatesStateFlow() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onOversoldThresholdChanged(-70.0)
            advanceUntilIdle()

            viewModel.oversoldThreshold.test {
                assertEquals(-70.0, awaitItem())
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
        @DisplayName("데이터 없고 미표시 시 showFirstRunDialog=true")
        fun noData_dialogNotDismissed_showsDialog() = runTest {
            coEvery { repository.getDataCount(any()) } returns 0
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
            coEvery { repository.getDataCount(any()) } returns 0
            coEvery { repository.isDialogDismissed() } returns true

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showFirstRunDialog.test {
                assertFalse(awaitItem())
            }
        }

        @Test
        @DisplayName("데이터 있으면 showFirstRunDialog=false (KOSPI)")
        fun kospiHasData_showFirstRunDialog_isFalse() = runTest {
            coEvery { repository.getDataCount("KOSPI") } returns 200
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
            coEvery { repository.getDataCount(any()) } returns 0
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
        @DisplayName("onFirstRunDialogConfirmed() 호출 시 saveDialogDismissed() 호출")
        fun onFirstRunDialogConfirmed_savesDismissed() = runTest {
            coEvery { repository.getDataCount(any()) } returns 0
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
        @DisplayName("수집 완료 시 getDataCount 재호출로 데이터 새로고침")
        fun collectionComplete_triggersDataRefresh() = runTest {
            CollectionState.reset()

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Trigger collection and then complete it
            CollectionState.startCollection(isInitialize = true)
            advanceUntilIdle()

            CollectionState.complete("complete")
            advanceUntilIdle()

            // checkData() should have been called again after collection completed
            coVerify(atLeast = 2) { repository.getDataCount(any()) }
        }
    }

    // ---------------------------------------------------------------
    // ThemeManager 통합 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("ThemeManager 통합 테스트")
    inner class ThemeManagerTests {

        @Test
        @DisplayName("bodyScale StateFlow 는 ThemeManager.fontScaleSettings.bodyScale 을 방영")
        fun bodyScale_reflectsThemeManagerFontScale() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Default bodyScale = 1.0f
            viewModel.bodyScale.test {
                assertEquals(1.0f, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            // Update ThemeManager
            themeManager.setBodyScale(1.5f)
            advanceUntilIdle()

            viewModel.bodyScale.test {
                assertEquals(1.5f, awaitItem())
            }
        }
    }
}
