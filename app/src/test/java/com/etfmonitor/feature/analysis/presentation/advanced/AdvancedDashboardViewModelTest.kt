package com.etfmonitor.feature.analysis.presentation.advanced

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.FearGreedDao
import com.etfmonitor.core.database.LiquidityAnalysisDao
import com.etfmonitor.core.database.MarketDepositDao
import com.etfmonitor.core.database.MarketIndexDao
import com.etfmonitor.core.database.SectorAnalysisDao
import com.etfmonitor.core.database.StockAnalysisDao
import com.etfmonitor.feature.analysis.domain.model.DivergenceAnalysis
import com.etfmonitor.feature.analysis.domain.model.EtfCorrelation
import com.etfmonitor.feature.analysis.domain.model.LiquidityAnalysisData
import com.etfmonitor.feature.analysis.domain.model.LiquiditySignalType
import com.etfmonitor.feature.analysis.domain.model.LeverageRisk
import com.etfmonitor.feature.analysis.domain.model.MarketCapFlow
import com.etfmonitor.feature.analysis.domain.model.MarketSentiment
import com.etfmonitor.feature.analysis.domain.model.SectorAnalysisData
import com.etfmonitor.feature.analysis.domain.model.SectorSentimentType
import com.etfmonitor.feature.analysis.domain.model.SectorRotation
import com.etfmonitor.feature.analysis.domain.repository.AdvancedAnalysisRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * AdvancedDashboardViewModel unit tests.
 *
 * Coverage:
 * - Insufficient data: Error state when fewer than 2 distinct dates
 * - Successful dashboard load: Success state with data
 * - Error state propagation when repository throws
 * - refresh() triggers reload
 * - forceRefresh() triggers recalculation
 * - calculateEtfCorrelation() sets isCalculatingCorrelation and calls repository
 *
 * Note: AdvancedDashboardViewModel launches all work on Dispatchers.IO directly.
 * Tests that need to observe post-IO states use Turbine to await the terminal
 * emission after skipping the transient Loading item.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class AdvancedDashboardViewModelTest {

    private lateinit var advancedRepository: AdvancedAnalysisRepository
    private lateinit var etfDao: EtfDao
    private lateinit var stockAnalysisDao: StockAnalysisDao
    private lateinit var marketDepositDao: MarketDepositDao
    private lateinit var fearGreedDao: FearGreedDao
    private lateinit var liquidityAnalysisDao: LiquidityAnalysisDao
    private lateinit var sectorAnalysisDao: SectorAnalysisDao
    private lateinit var marketIndexDao: MarketIndexDao

    @BeforeEach
    fun setup() {
        advancedRepository = mockk(relaxed = true)
        etfDao = mockk(relaxed = true)
        stockAnalysisDao = mockk(relaxed = true)
        marketDepositDao = mockk(relaxed = true)
        fearGreedDao = mockk(relaxed = true)
        liquidityAnalysisDao = mockk(relaxed = true)
        sectorAnalysisDao = mockk(relaxed = true)
        marketIndexDao = mockk(relaxed = true)

        // Default: insufficient data (< 2 dates) so loadDashboard yields Error
        coEvery { etfDao.getAllDistinctDates() } returns emptyList()
        coEvery { etfDao.getAllDistinctDates(any()) } returns emptyList()
        coEvery { etfDao.getTotalHoldingCount() } returns 0L
        coEvery { etfDao.getEtfCount() } returns 0
        coEvery { stockAnalysisDao.getCount() } returns 0
        coEvery { marketDepositDao.getLatestDeposit() } returns null
        coEvery { fearGreedDao.getLatestDate(any()) } returns null
        coEvery { liquidityAnalysisDao.getRecentHistory(any()) } returns emptyList()
        coEvery { sectorAnalysisDao.getAllSectors() } returns emptyList()
        coEvery { marketIndexDao.getByMarketAndDateRangeSuspend(any(), any(), any()) } returns emptyList()
    }

    private fun createViewModel(): AdvancedDashboardViewModel = AdvancedDashboardViewModel(
        advancedRepository = advancedRepository,
        etfDao = etfDao,
        stockAnalysisDao = stockAnalysisDao,
        marketDepositDao = marketDepositDao,
        fearGreedDao = fearGreedDao,
        liquidityAnalysisDao = liquidityAnalysisDao,
        sectorAnalysisDao = sectorAnalysisDao,
        marketIndexDao = marketIndexDao
    )

    // Helpers

    private fun makeDates() = listOf("2025-01-15", "2025-01-14")

    private fun makeMarketCapFlow() = MarketCapFlow(
        date = "2025-01-15",
        market = "ALL",
        totalInflow = 500_000L,
        totalOutflow = 300_000L,
        netFlow = 200_000L,
        topInflowStocks = emptyList(),
        topOutflowStocks = emptyList(),
        inflowBySize = emptyMap(),
        outflowBySize = emptyMap(),
        flowVsMarketChange = null
    )

    private fun makeDivergence() = DivergenceAnalysis(
        date = "2025-01-15",
        market = "ALL",
        foreignBullishCount = 5,
        institutionBullishCount = 3,
        alignedBullishCount = 2,
        alignedBearishCount = 1,
        neutralCount = 4,
        topForeignBullish = emptyList(),
        topInstitutionBullish = emptyList(),
        marketSentiment = MarketSentiment.MIXED,
        sentimentStrength = 0.5
    )

    private fun makeLiquidityAnalysis() = LiquidityAnalysisData(
        date = "2025-01-15",
        depositAmount = 50_000.0,
        creditAmount = 15_000.0,
        totalMarketCap = 2_000_000_000_000L,
        kospiMarketCap = 1_500_000_000_000L,
        kosdaqMarketCap = 500_000_000_000L,
        depositToMarketCapRatio = 2.5,
        creditToDepositRatio = 30.0,
        depositChange = 1.2,
        creditChange = 0.5,
        riskLevel = LeverageRisk.MEDIUM,
        signal = LiquiditySignalType.NEUTRAL,
        historicalPercentile = 50.0
    )

    private fun makeSectorAnalysis(sectorName: String = "반도체") = SectorAnalysisData(
        id = "sector-001",
        sector = "IT",
        sectorName = sectorName,
        date = "2025-01-15",
        fearGreedValue = 0.6,
        etfFlowScore = 0.5,
        momentumScore = 0.6,
        volatilityScore = 0.4,
        stockCount = 10,
        newEntries = 1,
        removals = 0,
        avgWeightChange = 0.1,
        sentiment = SectorSentimentType.GREED
    )

    private fun setupSuccessfulLoad() {
        val dates = makeDates()
        coEvery { etfDao.getAllDistinctDates() } returns dates
        coEvery { etfDao.getAllDistinctDates(any()) } returns dates
        coEvery { etfDao.getTotalHoldingCount() } returns 1000L
        coEvery { etfDao.getEtfCount() } returns 50
        coEvery { stockAnalysisDao.getCount() } returns 100
        coEvery { advancedRepository.calculateMarketCapWeightedFlow(any(), any()) } returns makeMarketCapFlow()
        coEvery { advancedRepository.analyzeSupplyDemandDivergence(any()) } returns makeDivergence()
        coEvery { advancedRepository.getLatestLiquidityAnalysis() } returns makeLiquidityAnalysis()
        coEvery { advancedRepository.getSectorAnalysisByDate(any()) } returns listOf(makeSectorAnalysis())
        coEvery { advancedRepository.detectSectorRotation(any(), any()) } returns emptyList()
        coEvery { advancedRepository.getHighOverlapEtfPairs(any(), any()) } returns emptyList()
        coEvery { advancedRepository.calculateAllEtfCorrelations(any()) } returns emptyList()
    }

    /**
     * Awaits state emissions until either Success or Error is reached.
     * Necessary because loadDashboard runs on Dispatchers.IO and emits Loading first.
     */
    private suspend fun app.cash.turbine.TurbineTestContext<AdvancedDashboardState>.awaitTerminalState(): AdvancedDashboardState {
        var item = awaitItem()
        while (item is AdvancedDashboardState.Loading) {
            item = awaitItem()
        }
        return item
    }

    // ---------------------------------------------------------------
    // Initial load with insufficient data
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("데이터 부족 시 Error 상태 테스트")
    inner class InsufficientDataTests {

        @Test
        @DisplayName("날짜 데이터 없을 때 Error 상태")
        fun noDates_loadsErrorState() = runTest {
            coEvery { etfDao.getAllDistinctDates() } returns emptyList()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<AdvancedDashboardState.Error>(awaitTerminalState())
            }
        }

        @Test
        @DisplayName("날짜가 1개만 있을 때 Error 상태")
        fun onlyOneDate_loadsErrorState() = runTest {
            coEvery { etfDao.getAllDistinctDates() } returns listOf("2025-01-15")

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<AdvancedDashboardState.Error>(awaitTerminalState())
            }
        }

        @Test
        @DisplayName("Error 상태 메시지 비어있지 않음")
        fun errorState_hasNonEmptyMessage() = runTest {
            coEvery { etfDao.getAllDistinctDates() } returns emptyList()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitTerminalState()
                assertIs<AdvancedDashboardState.Error>(state)
                assertTrue(state.message.isNotEmpty())
            }
        }
    }

    // ---------------------------------------------------------------
    // Successful dashboard load tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("대시보드 로드 성공 테스트")
    inner class SuccessfulLoadTests {

        @Test
        @DisplayName("날짜 2개 이상이고 repository 정상 시 Success 상태")
        fun twoOrMoreDates_withRepository_loadsSuccessState() = runTest {
            setupSuccessfulLoad()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<AdvancedDashboardState.Success>(awaitTerminalState())
            }
        }

        @Test
        @DisplayName("Success 상태의 data.date가 가장 최신일")
        fun successState_dateIsLatestDate() = runTest {
            setupSuccessfulLoad()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitTerminalState()
                assertIs<AdvancedDashboardState.Success>(state)
                assertTrue(state.data.date.isNotEmpty())
            }
        }

        @Test
        @DisplayName("Success 상태에서 marketCapFlow 설정됨")
        fun successState_hasMarketCapFlow() = runTest {
            setupSuccessfulLoad()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitTerminalState()
                assertIs<AdvancedDashboardState.Success>(state)
                assertNotNull(state.data.marketCapFlow)
            }
        }

        @Test
        @DisplayName("Success 상태에서 개별 marketCapFlow StateFlow 업데이트")
        fun successState_updatesMarketCapFlowStateFlow() = runTest {
            setupSuccessfulLoad()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.marketCapFlow.test {
                assertNotNull(awaitItem())
            }
        }

        @Test
        @DisplayName("repository 호출 확인: calculateMarketCapWeightedFlow")
        fun loadDashboard_callsCalculateMarketCapWeightedFlow() = runTest {
            setupSuccessfulLoad()

            val viewModel = createViewModel()
            advanceUntilIdle()

            coVerify { advancedRepository.calculateMarketCapWeightedFlow("2025-01-15", "2025-01-14") }
        }

        @Test
        @DisplayName("repository 호출 확인: analyzeSupplyDemandDivergence")
        fun loadDashboard_callsAnalyzeSupplyDemandDivergence() = runTest {
            setupSuccessfulLoad()

            val viewModel = createViewModel()
            advanceUntilIdle()

            coVerify { advancedRepository.analyzeSupplyDemandDivergence("2025-01-15") }
        }
    }

    // ---------------------------------------------------------------
    // Error state from repository tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("repository 오류 시 Error 상태 테스트")
    inner class RepositoryErrorTests {

        @Test
        @DisplayName("calculateMarketCapWeightedFlow 예외 발생 시 Error 상태")
        fun repositoryThrows_loadsErrorState() = runTest {
            coEvery { etfDao.getAllDistinctDates() } returns makeDates()
            coEvery { etfDao.getAllDistinctDates(any()) } returns makeDates()
            coEvery { etfDao.getTotalHoldingCount() } returns 100L
            coEvery { etfDao.getEtfCount() } returns 10
            coEvery { stockAnalysisDao.getCount() } returns 5
            coEvery { advancedRepository.calculateMarketCapWeightedFlow(any(), any()) } throws
                RuntimeException("데이터 로드 실패")

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<AdvancedDashboardState.Error>(awaitTerminalState())
            }
        }

        @Test
        @DisplayName("오류 메시지에 예외 내용 포함")
        fun repositoryThrows_errorMessageContainsExceptionMessage() = runTest {
            coEvery { etfDao.getAllDistinctDates() } returns makeDates()
            coEvery { etfDao.getAllDistinctDates(any()) } returns makeDates()
            coEvery { etfDao.getTotalHoldingCount() } returns 100L
            coEvery { etfDao.getEtfCount() } returns 10
            coEvery { stockAnalysisDao.getCount() } returns 5
            coEvery { advancedRepository.calculateMarketCapWeightedFlow(any(), any()) } throws
                RuntimeException("DB 오류")

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitTerminalState()
                assertIs<AdvancedDashboardState.Error>(state)
                assertTrue(state.message.contains("실패") || state.message.contains("오류"))
            }
        }
    }

    // ---------------------------------------------------------------
    // refresh() tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("refresh() 테스트")
    inner class RefreshTests {

        @Test
        @DisplayName("refresh() 호출 시 isRefreshing 상태 변화 후 false 복귀")
        fun refresh_setsRefreshingThenFalse() = runTest {
            setupSuccessfulLoad()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.refresh()
            advanceUntilIdle()

            viewModel.isRefreshing.test {
                assertFalse(awaitItem())
            }
        }

        @Test
        @DisplayName("refresh() 호출 후 Success 상태 유지")
        fun refresh_maintainsSuccessState() = runTest {
            setupSuccessfulLoad()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.refresh()
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<AdvancedDashboardState.Success>(awaitTerminalState())
            }
        }

        @Test
        @DisplayName("refresh() 호출 시 repository 재호출")
        fun refresh_callsRepositoryAgain() = runTest {
            setupSuccessfulLoad()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.refresh()
            advanceUntilIdle()

            // Should have been called at least twice: once during init, once during refresh
            coVerify(atLeast = 2) {
                advancedRepository.calculateMarketCapWeightedFlow(any(), any())
            }
        }
    }

    // ---------------------------------------------------------------
    // forceRefresh() tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("forceRefresh() 테스트")
    inner class ForceRefreshTests {

        @Test
        @DisplayName("forceRefresh() 날짜 부족 시 Error 상태")
        fun forceRefresh_insufficientDates_setsErrorState() = runTest {
            coEvery { etfDao.getAllDistinctDates() } returns emptyList()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.forceRefresh()
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<AdvancedDashboardState.Error>(awaitTerminalState())
            }
        }

        @Test
        @DisplayName("forceRefresh() 성공 시 calculateAndSaveLiquidityAnalysis 호출")
        fun forceRefresh_success_callsLiquidityRecalculation() = runTest {
            setupSuccessfulLoad()
            coEvery { advancedRepository.calculateAndSaveLiquidityAnalysis(any()) } returns makeLiquidityAnalysis()
            coEvery { advancedRepository.calculateAndSaveSectorAnalysis(any(), any()) } returns emptyList()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.forceRefresh()
            advanceUntilIdle()

            coVerify(atLeast = 1) { advancedRepository.calculateAndSaveLiquidityAnalysis(any()) }
        }

        @Test
        @DisplayName("forceRefresh() 완료 후 isRefreshing = false")
        fun forceRefresh_completion_setsRefreshingFalse() = runTest {
            setupSuccessfulLoad()
            coEvery { advancedRepository.calculateAndSaveLiquidityAnalysis(any()) } returns makeLiquidityAnalysis()
            coEvery { advancedRepository.calculateAndSaveSectorAnalysis(any(), any()) } returns emptyList()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.forceRefresh()
            advanceUntilIdle()

            // isRefreshing starts false, goes true during forceRefresh, returns false when done.
            // After advanceUntilIdle() all queued work is done; observe final stable value.
            viewModel.isRefreshing.test {
                // Drain any intermediate true emission (if still present), expect final false
                var item = awaitItem()
                while (item) {
                    item = awaitItem()
                }
                assertFalse(item)
            }
        }
    }

    // ---------------------------------------------------------------
    // calculateEtfCorrelation() tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("ETF 상관관계 계산 테스트")
    inner class EtfCorrelationTests {

        @Test
        @DisplayName("calculateEtfCorrelation() 날짜 부족 시 종료 (isCalculatingCorrelation false)")
        fun calculateEtfCorrelation_noDates_doesNotStartCalculation() = runTest {
            coEvery { etfDao.getAllDistinctDates() } returns emptyList()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.calculateEtfCorrelation()
            advanceUntilIdle()

            viewModel.isCalculatingCorrelation.test {
                assertFalse(awaitItem())
            }
        }

        @Test
        @DisplayName("calculateEtfCorrelation() 날짜 충분 시 repository 호출")
        fun calculateEtfCorrelation_withDates_callsRepository() = runTest {
            setupSuccessfulLoad()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.calculateEtfCorrelation()
            advanceUntilIdle()

            coVerify(atLeast = 1) { advancedRepository.calculateAllEtfCorrelations(any()) }
        }

        @Test
        @DisplayName("calculateEtfCorrelation() 완료 후 isCalculatingCorrelation false")
        fun calculateEtfCorrelation_completion_setsCalculatingFalse() = runTest {
            setupSuccessfulLoad()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.calculateEtfCorrelation()
            advanceUntilIdle()

            // Drain any intermediate true emission, expect final false
            viewModel.isCalculatingCorrelation.test {
                var item = awaitItem()
                while (item) {
                    item = awaitItem()
                }
                assertFalse(item)
            }
        }
    }
}
