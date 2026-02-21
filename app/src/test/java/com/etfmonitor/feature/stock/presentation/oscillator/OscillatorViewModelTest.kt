package com.etfmonitor.feature.stock.presentation.oscillator

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.analysis.model.DemarkTDData
import com.etfmonitor.core.analysis.model.ElderImpulseData
import com.etfmonitor.core.analysis.model.StockData
import com.etfmonitor.core.analysis.model.TrendSignalData
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.SearchHistoryDao
import com.etfmonitor.core.database.entities.SearchHistory
import com.etfmonitor.core.database.entities.SearchHistoryType
import com.etfmonitor.core.domain.usecase.krx.GetDemarkTDDataUseCase
import com.etfmonitor.core.domain.usecase.krx.GetElderImpulseDataUseCase
import com.etfmonitor.core.domain.usecase.krx.GetTrendSignalDataUseCase
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.feature.stock.domain.model.Stock
import com.etfmonitor.feature.stock.domain.repository.StockAnalysisRepository
import com.etfmonitor.feature.stock.domain.repository.StockRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * OscillatorViewModel 단위 테스트
 *
 * 테스트 범위:
 * - 초기 상태 (Idle)
 * - searchAndAnalyze() 성공/실패
 * - analyzeStock() 성공/실패
 * - 종목 미발견 시 Error 상태
 * - 데이터 없을 때 Error 상태
 * - 검색 쿼리 업데이트 및 제안 초기화
 * - currentTicker 설정
 * - 인터벌 변경 (trendSignal, elderImpulse, demarkTD)
 * - 날짜 범위 변경
 * - 검색 히스토리 저장 여부
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class OscillatorViewModelTest {

    private lateinit var getTrendSignalDataUseCase: GetTrendSignalDataUseCase
    private lateinit var getElderImpulseDataUseCase: GetElderImpulseDataUseCase
    private lateinit var getDemarkTDDataUseCase: GetDemarkTDDataUseCase
    private lateinit var stockRepository: StockRepository
    private lateinit var stockAnalysisRepository: StockAnalysisRepository
    private lateinit var searchHistoryDao: SearchHistoryDao
    private lateinit var etfDao: EtfDao

    private val testTicker = "005930"
    private val testStockName = "삼성전자"

    @BeforeEach
    fun setup() {
        getTrendSignalDataUseCase = mockk(relaxed = true)
        getElderImpulseDataUseCase = mockk(relaxed = true)
        getDemarkTDDataUseCase = mockk(relaxed = true)
        stockRepository = mockk(relaxed = true)
        stockAnalysisRepository = mockk(relaxed = true)
        searchHistoryDao = mockk(relaxed = true)
        etfDao = mockk(relaxed = true)

        // Default search history
        every { searchHistoryDao.getRecentSearchesByType(SearchHistoryType.STOCK, any()) } returns
            flowOf(emptyList<SearchHistory>())

        // Default: setting not configured
        coEvery { etfDao.getSetting(any()) } returns null

        // Default: stock found
        every { stockRepository.searchStocks(any()) } returns flowOf(listOf(makeStock()))

        // Default: analysis data available
        coEvery { stockAnalysisRepository.getStockAnalysis(any(), any()) } returns makeStockData()

        // Default: trend signal / elder / demark return null (optional data)
        coEvery { getTrendSignalDataUseCase(any(), any(), any()) } returns null
        coEvery { getElderImpulseDataUseCase(any(), any(), any()) } returns null
        coEvery { getDemarkTDDataUseCase(any(), any(), any()) } returns null
    }

    private fun createViewModel(): OscillatorViewModel = OscillatorViewModel(
        getTrendSignalDataUseCase = getTrendSignalDataUseCase,
        getElderImpulseDataUseCase = getElderImpulseDataUseCase,
        getDemarkTDDataUseCase = getDemarkTDDataUseCase,
        stockRepository = stockRepository,
        stockAnalysisRepository = stockAnalysisRepository,
        searchHistoryDao = searchHistoryDao,
        etfDao = etfDao
    )

    // --- helpers ---

    private fun makeStock(
        ticker: String = testTicker,
        name: String = testStockName
    ) = Stock(ticker = ticker, name = name, market = "KOSPI")

    private fun makeStockData(
        ticker: String = testTicker,
        name: String = testStockName
    ) = StockData(
        ticker = ticker,
        name = name,
        dates = listOf("20250113", "20250114", "20250115"),
        marketCap = listOf(3_000_000L, 3_100_000L, 3_200_000L),
        foreign5d = listOf(100L, 200L, -50L),
        institution5d = listOf(500L, -100L, 300L)
    )

    // ---------------------------------------------------------------
    // 초기 상태 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("초기 상태 테스트")
    inner class InitialStateTests {

        @Test
        @DisplayName("초기 상태는 Idle")
        fun initialState_isIdle() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<OscillatorState.Idle>(awaitItem())
            }
        }

        @Test
        @DisplayName("초기 searchQuery 는 빈 문자열")
        fun initialSearchQuery_isEmpty() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.searchQuery.test {
                assertEquals("", awaitItem())
            }
        }

        @Test
        @DisplayName("초기 currentTicker 는 null")
        fun initialCurrentTicker_isNull() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.currentTicker.test {
                assertNull(awaitItem())
            }
        }

        @Test
        @DisplayName("초기 selectedRange 는 SIX_MONTHS")
        fun initialSelectedRange_isSixMonths() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectedRange.test {
                assertEquals(DateRangeOption.SIX_MONTHS, awaitItem())
            }
        }

        @Test
        @DisplayName("초기 suggestions 는 비어있음")
        fun initialSuggestions_isEmpty() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.suggestions.test {
                assertTrue(awaitItem().isEmpty())
            }
        }
    }

    // ---------------------------------------------------------------
    // analyzeStock() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("analyzeStock() 테스트")
    inner class AnalyzeStockTests {

        @Test
        @DisplayName("analyzeStock() 성공 시 Success 상태")
        fun analyzeStock_success_producesSuccessState() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.analyzeStock(testTicker)
            advanceUntilIdle()

            // analyzeStock uses flowOn(Dispatchers.IO) for the history save path, so the
            // coroutine may still be running after advanceUntilIdle() when other tests in
            // the full suite keep the IO thread pool busy.  Wait for the state to settle.
            val state = viewModel.state.first { it !is OscillatorState.Loading }
            assertIs<OscillatorState.Success>(state)
            assertEquals(testTicker, state.stockData.ticker)
        }

        @Test
        @DisplayName("analyzeStock() 시 Loading 상태 먼저 설정")
        fun analyzeStock_setsLoadingStateFirst() = runTest {
            coEvery { stockAnalysisRepository.getStockAnalysis(any(), any()) } coAnswers {
                kotlinx.coroutines.delay(100)
                makeStockData()
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                cancelAndIgnoreRemainingEvents()
            }

            // Use saveHistory=false to avoid flowOn(Dispatchers.IO) in the history-save
            // path.  Without this, a real IO thread can outlive the test and try to
            // dispatch back to Dispatchers.Main after afterEach() resets it, causing
            // UncaughtExceptionsBeforeTest in the next test.
            viewModel.analyzeStock(testTicker, saveHistory = false)

            viewModel.state.test {
                val first = awaitItem()
                assertIs<OscillatorState.Loading>(first)
                cancelAndIgnoreRemainingEvents()
            }

            // Drain the remaining ViewModel coroutine (past the virtual delay(100))
            // so it completes cleanly before afterEach() resets Dispatchers.Main.
            advanceUntilIdle()
        }

        @Test
        @DisplayName("analyzeStock() 데이터 없을 때 Error 상태")
        fun analyzeStock_nullData_producesErrorState() = runTest {
            coEvery { stockAnalysisRepository.getStockAnalysis(any(), any()) } returns null

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.analyzeStock(testTicker)
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<OscillatorState.Error>(state)
                assertTrue(state.message.isNotEmpty())
            }
        }

        @Test
        @DisplayName("analyzeStock() 예외 발생 시 Error 상태")
        fun analyzeStock_exception_producesErrorState() = runTest {
            coEvery { stockAnalysisRepository.getStockAnalysis(any(), any()) } throws RuntimeException("네트워크 오류")

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.analyzeStock(testTicker)
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<OscillatorState.Error>(state)
                assertTrue(state.message.contains("네트워크 오류"))
            }
        }

        @Test
        @DisplayName("analyzeStock() 호출 시 currentTicker 업데이트")
        fun analyzeStock_updateCurrentTicker() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.analyzeStock(testTicker)
            advanceUntilIdle()

            viewModel.currentTicker.test {
                assertEquals(testTicker, awaitItem())
            }
        }

        @Test
        @DisplayName("analyzeStock() saveHistory=true 시 검색 히스토리 저장")
        fun analyzeStock_saveHistoryTrue_savesSearchHistory() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.analyzeStock(testTicker, saveHistory = true)
            advanceUntilIdle()

            coVerify(atLeast = 1) { searchHistoryDao.insertSearch(any()) }
        }

        @Test
        @DisplayName("analyzeStock() saveHistory=false 시 검색 히스토리 미저장")
        fun analyzeStock_saveHistoryFalse_doesNotSaveHistory() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.analyzeStock(testTicker, saveHistory = false)
            advanceUntilIdle()

            coVerify(exactly = 0) { searchHistoryDao.insertSearch(any()) }
        }
    }

    // ---------------------------------------------------------------
    // searchAndAnalyze() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("searchAndAnalyze() 테스트")
    inner class SearchAndAnalyzeTests {

        @Test
        @DisplayName("searchAndAnalyze() 종목 발견 시 Success 상태")
        fun searchAndAnalyze_stockFound_producesSuccessState() = runTest {
            every { stockRepository.searchStocks(testTicker) } returns flowOf(listOf(makeStock()))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.searchAndAnalyze(testTicker)
            advanceUntilIdle()

            // searchAndAnalyze internally uses flowOn(Dispatchers.IO) for stock lookup,
            // so the coroutine may still be running after advanceUntilIdle().
            // Wait for state to settle past Loading before asserting.
            val finalState = viewModel.state.first { it !is OscillatorState.Loading }
            assertIs<OscillatorState.Success>(finalState)
        }

        @Test
        @DisplayName("searchAndAnalyze() 종목 미발견 시 Error 상태")
        fun searchAndAnalyze_stockNotFound_producesErrorState() = runTest {
            every { stockRepository.searchStocks(any()) } returns flowOf(emptyList<Stock>())

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.searchAndAnalyze("존재하지않는종목")
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<OscillatorState.Error>(state)
                assertTrue(state.message.isNotEmpty())
            }
        }
    }

    // ---------------------------------------------------------------
    // onSearchQueryChanged() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("onSearchQueryChanged() 테스트")
    inner class SearchQueryTests {

        @Test
        @DisplayName("검색어 변경 시 searchQuery StateFlow 업데이트")
        fun onSearchQueryChanged_updatesSearchQuery() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSearchQueryChanged("삼성")
            advanceUntilIdle()

            viewModel.searchQuery.test {
                assertEquals("삼성", awaitItem())
            }
        }

        @Test
        @DisplayName("빈 검색어 입력 시 suggestions 초기화")
        fun blankQuery_clearsSuggestions() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Trigger the "삼성" search and wait until the async IO completes and
            // suggestions are populated.  This ensures no in-flight IO job can
            // overwrite the empty list after the blank-input clear below.
            viewModel.onSearchQueryChanged("삼성")
            advanceUntilIdle()
            viewModel.suggestions.first { it.isNotEmpty() }

            // Blank query must clear suggestions synchronously.
            viewModel.onSearchQueryChanged("")
            advanceUntilIdle()

            // The previous search job is now done (we waited for it), so no
            // in-flight IO remains to overwrite the cleared list.
            assertTrue(viewModel.suggestions.value.isEmpty())
        }

        @Test
        @DisplayName("onClearSuggestions() 호출 시 suggestions 비워짐")
        fun onClearSuggestions_clearsSuggestions() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onClearSuggestions()

            viewModel.suggestions.test {
                assertTrue(awaitItem().isEmpty())
            }
        }
    }

    // ---------------------------------------------------------------
    // 인터벌 변경 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("인터벌 변경 테스트")
    inner class IntervalChangeTests {

        @Test
        @DisplayName("changeTrendSignalInterval() - Success 아닌 상태에선 무시")
        fun changeTrendSignalInterval_notSuccess_ignored() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // State is Idle — interval change should be ignored
            viewModel.changeTrendSignalInterval("d")
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<OscillatorState.Idle>(awaitItem())
            }
        }

        @Test
        @DisplayName("changeTrendSignalInterval() - Success 상태에서 인터벌 변경 및 데이터 업데이트")
        fun changeTrendSignalInterval_inSuccess_fetchesNewData() = runTest {
            val trendData = makeTrendSignalData()
            coEvery { getTrendSignalDataUseCase(testTicker, any(), "d") } returns trendData

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Get into Success state and explicitly wait for it, because analyzeStock
            // internally uses flowOn(Dispatchers.IO) which may not complete before
            // advanceUntilIdle() returns on the test dispatcher.
            viewModel.analyzeStock(testTicker)
            advanceUntilIdle()
            viewModel.state.first { it is OscillatorState.Success }

            viewModel.changeTrendSignalInterval("d")
            advanceUntilIdle()

            coVerify { getTrendSignalDataUseCase(testTicker, any(), "d") }
        }

        @Test
        @DisplayName("changeElderImpulseInterval() - 같은 인터벌 재설정 시 무시")
        fun changeElderImpulseInterval_sameInterval_ignored() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.analyzeStock(testTicker)
            advanceUntilIdle()

            // Initial interval is "w" — changing to same "w" should be a no-op
            viewModel.changeElderImpulseInterval("w")
            advanceUntilIdle()

            // Should NOT call use case again for the same interval
            coVerify(exactly = 1) { getElderImpulseDataUseCase(testTicker, any(), "w") }
        }

        @Test
        @DisplayName("changeDemarkTDInterval() - Success 상태에서 인터벌 변경")
        fun changeDemarkTDInterval_inSuccess_fetchesNewData() = runTest {
            val demarkData = makeDemarkTDData()
            coEvery { getDemarkTDDataUseCase(testTicker, any(), "d") } returns demarkData

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Get into Success state and explicitly wait for it, because analyzeStock
            // internally uses flowOn(Dispatchers.IO) which may not complete before
            // advanceUntilIdle() returns on the test dispatcher.
            viewModel.analyzeStock(testTicker)
            advanceUntilIdle()
            viewModel.state.first { it is OscillatorState.Success }

            viewModel.changeDemarkTDInterval("d")
            advanceUntilIdle()

            coVerify { getDemarkTDDataUseCase(testTicker, any(), "d") }
        }
    }

    // ---------------------------------------------------------------
    // 날짜 범위 변경 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("날짜 범위 변경 테스트")
    inner class DateRangeTests {

        @Test
        @DisplayName("updateDateRange() 성공 상태에서 클라이언트 사이드 필터링")
        fun updateDateRange_inSuccess_appliesClientFilter() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.analyzeStock(testTicker)
            advanceUntilIdle()

            viewModel.updateDateRange(DateRangeOption.THREE_MONTHS)
            advanceUntilIdle()

            viewModel.selectedRange.test {
                assertEquals(DateRangeOption.THREE_MONTHS, awaitItem())
            }
        }

        @Test
        @DisplayName("같은 날짜 범위로 updateDateRange() 시 변경 없음")
        fun updateDateRange_sameOption_noChange() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.analyzeStock(testTicker)
            advanceUntilIdle()

            val stateBefore = viewModel.state.value

            viewModel.updateDateRange(DateRangeOption.SIX_MONTHS) // Same as default
            advanceUntilIdle()

            assertEquals(stateBefore, viewModel.state.value)
        }
    }

    // --- test data factories ---

    private fun makeTrendSignalData() = TrendSignalData(
        ticker = testTicker,
        name = testStockName,
        interval = "w",
        dates = listOf("20250113", "20250114"),
        open = listOf(72000.0, 72500.0),
        high = listOf(73000.0, 73500.0),
        low = listOf(71500.0, 72000.0),
        close = listOf(72500.0, 73000.0),
        volume = listOf(10000L, 12000L),
        ma = listOf(71000.0, 71500.0),
        cmf = listOf(0.1, 0.2),
        fearGreed = listOf(50.0, 55.0),
        buySignal = listOf(0, 1),
        auxBuySignal = listOf(0, 0),
        sellSignal = listOf(0, 0),
        auxSellSignal = listOf(0, 0)
    )

    private fun makeElderImpulseData() = ElderImpulseData(
        ticker = testTicker,
        name = testStockName,
        interval = "w",
        dates = listOf("20250113", "20250114"),
        close = listOf(72500.0, 73000.0),
        marketCap = listOf(3_000_000L, 3_100_000L),
        ema = listOf(71000.0, 71500.0),
        macd = listOf(500.0, 600.0),
        macdSignal = listOf(450.0, 550.0),
        macdHist = listOf(50.0, 50.0),
        impulse = listOf(1, 1)
    )

    private fun makeDemarkTDData() = DemarkTDData(
        ticker = testTicker,
        name = testStockName,
        interval = "d",
        intervalName = "일봉",
        dates = listOf("20250113", "20250114"),
        close = listOf(72500.0, 73000.0),
        marketCap = listOf(3_000_000L, 3_100_000L),
        tdSell = listOf(0, 9),
        tdBuy = listOf(4, 0)
    )
}
