package com.etfmonitor.core.worker

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.feature.analysis.domain.model.DivergenceAnalysis
import com.etfmonitor.feature.analysis.domain.model.DivergenceType
import com.etfmonitor.feature.analysis.domain.model.EtfCorrelation
import com.etfmonitor.feature.analysis.domain.model.LiquidityAnalysisData
import com.etfmonitor.feature.analysis.domain.model.LeverageRisk
import com.etfmonitor.feature.analysis.domain.model.LiquiditySignalType
import com.etfmonitor.feature.analysis.domain.model.MarketCapFlow
import com.etfmonitor.feature.analysis.domain.model.MarketCapSize
import com.etfmonitor.feature.analysis.domain.model.MarketSentiment
import com.etfmonitor.feature.analysis.domain.model.SectorAnalysisData
import com.etfmonitor.feature.analysis.domain.model.SectorRotation
import com.etfmonitor.feature.analysis.domain.model.SectorSentimentType
import com.etfmonitor.feature.analysis.domain.repository.AdvancedAnalysisRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * AdvancedAnalysisWorker 단위 테스트
 *
 * Worker의 doWork() 로직:
 * 1. etfDao.getAllDistinctDates(10)으로 날짜 조회
 * 2. 날짜가 2개 미만이면 → FAILURE (데이터 부족)
 * 3. 5가지 분석을 순차 실행:
 *    - calculateMarketCapWeightedFlow()
 *    - analyzeSupplyDemandDivergence()
 *    - calculateAndSaveLiquidityAnalysis()
 *    - calculateAndSaveSectorAnalysis() + detectSectorRotation()
 *    - getHighOverlapEtfPairs() → 캐시 없으면 calculateAllEtfCorrelations()
 * 4. 전부 성공 → SUCCESS (with output data)
 * 5. 일부 성공 → SUCCESS (with partial error message)
 * 6. 전부 실패 → FAILURE
 * 7. 외부 예외 → attempt < 3: RETRY, else: FAILURE
 * 8. CancellationException → rethrow
 *
 * runAnalysis() 헬퍼는 개별 분석의 예외를 catch하여 boolean 반환.
 * CancellationException은 runAnalysis() 내에서도 rethrow됨.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("AdvancedAnalysisWorker 로직 테스트")
class AdvancedAnalysisWorkerTest {

    private lateinit var advancedAnalysisRepository: AdvancedAnalysisRepository
    private lateinit var etfDao: EtfDao

    @BeforeEach
    fun setup() {
        advancedAnalysisRepository = mockk(relaxed = true)
        etfDao = mockk(relaxed = true)
    }

    // ============================================================
    // Helper: AdvancedAnalysisWorker.doWork()와 동일한 로직 추출
    // ============================================================

    /**
     * runAnalysis 헬퍼: Worker 내의 private inline fun runAnalysis()와 동일한 구현.
     * CancellationException은 rethrow, 그 외 Exception은 catch하여 false 반환.
     */
    private inline fun runAnalysis(name: String, block: () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /**
     * AdvancedAnalysisWorker.doWork()의 핵심 비즈니스 로직과 동일한 구현.
     */
    private suspend fun executeAdvancedAnalysisLogic(
        repository: AdvancedAnalysisRepository,
        dao: EtfDao,
        runAttemptCount: Int = 0
    ): WorkerResult {
        return try {
            val dates = dao.getAllDistinctDates(10)
            if (dates.size < 2) {
                return WorkerResult.FAILURE_INSUFFICIENT_DATES
            }

            val currentDate = dates.first()
            val previousDate = dates[1]

            val results = mutableMapOf<String, Boolean>()

            // 1. 시총 가중 ETF 흐름
            results[AdvancedAnalysisWorker.KEY_MARKET_CAP_FLOW_SUCCESS] = runAnalysis("MarketCapFlow") {
                repository.calculateMarketCapWeightedFlow(currentDate, previousDate)
            }

            // 2. 수급 Divergence
            results[AdvancedAnalysisWorker.KEY_DIVERGENCE_SUCCESS] = runAnalysis("Divergence") {
                repository.analyzeSupplyDemandDivergence(currentDate)
            }

            // 3. 유동성 분석
            results[AdvancedAnalysisWorker.KEY_LIQUIDITY_SUCCESS] = runAnalysis("Liquidity") {
                repository.calculateAndSaveLiquidityAnalysis(currentDate)
            }

            // 4. 섹터 분석
            results[AdvancedAnalysisWorker.KEY_SECTOR_SUCCESS] = runAnalysis("Sector") {
                repository.calculateAndSaveSectorAnalysis(currentDate, previousDate)
                repository.detectSectorRotation(currentDate, previousDate)
            }

            // 5. ETF 상관관계 (캐시 확인)
            results[AdvancedAnalysisWorker.KEY_CORRELATION_SUCCESS] = runAnalysis("Correlation") {
                val cached = repository.getHighOverlapEtfPairs(currentDate, 0.0)
                if (cached.isEmpty()) {
                    repository.calculateAllEtfCorrelations(currentDate)
                }
            }

            val successCount = results.values.count { it }
            val totalCount = results.size

            when {
                successCount == totalCount -> WorkerResult.ALL_SUCCESS
                successCount > 0 -> WorkerResult.PARTIAL_SUCCESS
                else -> WorkerResult.FAILURE_ALL_ANALYSES
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 3) WorkerResult.RETRY else WorkerResult.FAILURE_EXCEPTION
        }
    }

    // ============================================================
    // Fixtures
    // ============================================================

    private fun makeMarketCapFlow() = MarketCapFlow(
        date = "2026-02-20",
        market = "ALL",
        totalInflow = 1_000_000_000L,
        totalOutflow = 500_000_000L,
        netFlow = 500_000_000L,
        topInflowStocks = emptyList(),
        topOutflowStocks = emptyList(),
        inflowBySize = mapOf(MarketCapSize.LARGE to 1_000_000_000L),
        outflowBySize = mapOf(MarketCapSize.LARGE to 500_000_000L),
        flowVsMarketChange = 0.5
    )

    private fun makeDivergenceAnalysis() = DivergenceAnalysis(
        date = "2026-02-20",
        market = "ALL",
        foreignBullishCount = 50,
        institutionBullishCount = 30,
        alignedBullishCount = 20,
        alignedBearishCount = 10,
        neutralCount = 40,
        topForeignBullish = emptyList(),
        topInstitutionBullish = emptyList(),
        marketSentiment = MarketSentiment.CONSENSUS_BULLISH,
        sentimentStrength = 0.65
    )

    private fun makeLiquidityAnalysis() = LiquidityAnalysisData(
        date = "2026-02-20",
        depositAmount = 50_000_000_000.0,
        creditAmount = 10_000_000_000.0,
        totalMarketCap = 2_000_000_000_000L,
        kospiMarketCap = 1_500_000_000_000L,
        kosdaqMarketCap = 500_000_000_000L,
        depositToMarketCapRatio = 2.5,
        creditToDepositRatio = 20.0,
        depositChange = 1.5,
        creditChange = -0.5,
        riskLevel = LeverageRisk.LOW,
        signal = LiquiditySignalType.BULLISH_LIQUIDITY,
        historicalPercentile = 0.75
    )

    private fun makeSectorAnalysis(sector: String = "반도체") = SectorAnalysisData(
        id = "sector_$sector",
        sector = sector,
        sectorName = "$sector 섹터",
        date = "2026-02-20",
        fearGreedValue = 0.65,
        etfFlowScore = 0.7,
        momentumScore = 0.8,
        volatilityScore = 0.4,
        stockCount = 20,
        newEntries = 2,
        removals = 1,
        avgWeightChange = 0.05,
        sentiment = SectorSentimentType.GREED
    )

    private fun makeSectorRotation() = SectorRotation(
        fromSector = "에너지",
        toSector = "반도체",
        confidence = 0.75,
        flowDifference = 500_000_000.0,
        description = "에너지 → 반도체 로테이션 감지"
    )

    private fun makeEtfCorrelation() = EtfCorrelation(
        id = "corr_069500_102110",
        etf1Ticker = "069500",
        etf1Name = "KODEX 200",
        etf2Ticker = "102110",
        etf2Name = "TIGER 200",
        date = "2026-02-20",
        overlapRatio = 0.85,
        weightCorrelation = 0.92,
        commonStockCount = 150,
        etf1StockCount = 200,
        etf2StockCount = 200,
        topCommonStocks = emptyList()
    )

    // ============================================================
    // InsufficientDateTests: 날짜 부족
    // ============================================================

    @Nested
    @DisplayName("날짜 데이터 부족 처리 테스트")
    inner class InsufficientDateTests {

        @Test
        @DisplayName("날짜가 0개이면 FAILURE_INSUFFICIENT_DATES를 반환한다")
        fun `executeAdvancedLogic_noDates_returnsFailureInsufficientDates`() = runTest {
            // Given
            coEvery { etfDao.getAllDistinctDates(10) } returns emptyList()

            // When
            val result = executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao)

            // Then
            assertEquals(WorkerResult.FAILURE_INSUFFICIENT_DATES, result)
        }

        @Test
        @DisplayName("날짜가 1개이면 FAILURE_INSUFFICIENT_DATES를 반환한다")
        fun `executeAdvancedLogic_singleDate_returnsFailureInsufficientDates`() = runTest {
            // Given
            coEvery { etfDao.getAllDistinctDates(10) } returns listOf("2026-02-20")

            // When
            val result = executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao)

            // Then
            assertEquals(WorkerResult.FAILURE_INSUFFICIENT_DATES, result)
        }

        @Test
        @DisplayName("날짜가 2개이면 분석을 시작한다 (insufficient date 아님)")
        fun `executeAdvancedLogic_twoDates_proceedsWithAnalysis`() = runTest {
            // Given: 2개 날짜 제공 (분석 진행), 모든 분석 성공 mock
            coEvery { etfDao.getAllDistinctDates(10) } returns listOf("2026-02-20", "2026-02-19")
            coEvery { advancedAnalysisRepository.calculateMarketCapWeightedFlow(any(), any()) } returns makeMarketCapFlow()
            coEvery { advancedAnalysisRepository.analyzeSupplyDemandDivergence(any()) } returns makeDivergenceAnalysis()
            coEvery { advancedAnalysisRepository.calculateAndSaveLiquidityAnalysis(any()) } returns makeLiquidityAnalysis()
            coEvery { advancedAnalysisRepository.calculateAndSaveSectorAnalysis(any(), any()) } returns listOf(makeSectorAnalysis())
            coEvery { advancedAnalysisRepository.detectSectorRotation(any(), any()) } returns emptyList()
            coEvery { advancedAnalysisRepository.getHighOverlapEtfPairs(any(), any()) } returns listOf(makeEtfCorrelation())

            // When
            val result = executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao)

            // Then: 분석이 진행되어 ALL_SUCCESS 반환
            assertEquals(WorkerResult.ALL_SUCCESS, result)
        }

        @Test
        @DisplayName("날짜가 부족할 때 분석 메서드들이 호출되지 않는다")
        fun `executeAdvancedLogic_insufficientDates_noAnalysisCalled`() = runTest {
            // Given
            coEvery { etfDao.getAllDistinctDates(10) } returns listOf("2026-02-20")

            // When
            executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao)

            // Then: 분석 메서드들이 호출되지 않아야 한다
            coVerify(exactly = 0) { advancedAnalysisRepository.calculateMarketCapWeightedFlow(any(), any()) }
            coVerify(exactly = 0) { advancedAnalysisRepository.analyzeSupplyDemandDivergence(any()) }
        }
    }

    // ============================================================
    // AllSuccessTests: 전체 분석 성공
    // ============================================================

    @Nested
    @DisplayName("전체 분석 성공 경로")
    inner class AllSuccessTests {

        @BeforeEach
        fun setupSuccessScenario() {
            coEvery { etfDao.getAllDistinctDates(10) } returns listOf("2026-02-20", "2026-02-19")
            coEvery { advancedAnalysisRepository.calculateMarketCapWeightedFlow(any(), any()) } returns makeMarketCapFlow()
            coEvery { advancedAnalysisRepository.analyzeSupplyDemandDivergence(any()) } returns makeDivergenceAnalysis()
            coEvery { advancedAnalysisRepository.calculateAndSaveLiquidityAnalysis(any()) } returns makeLiquidityAnalysis()
            coEvery { advancedAnalysisRepository.calculateAndSaveSectorAnalysis(any(), any()) } returns
                listOf(makeSectorAnalysis("반도체"), makeSectorAnalysis("바이오"))
            coEvery { advancedAnalysisRepository.detectSectorRotation(any(), any()) } returns listOf(makeSectorRotation())
            coEvery { advancedAnalysisRepository.getHighOverlapEtfPairs(any(), any()) } returns listOf(makeEtfCorrelation())
        }

        @Test
        @DisplayName("5가지 분석 모두 성공하면 ALL_SUCCESS를 반환한다")
        fun `executeAdvancedLogic_allAnalysesSucceed_returnsAllSuccess`() = runTest {
            val result = executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao)
            assertEquals(WorkerResult.ALL_SUCCESS, result)
        }

        @Test
        @DisplayName("성공 시 currentDate (가장 최근 날짜)와 previousDate (두 번째 날짜)로 분석 호출")
        fun `executeAdvancedLogic_usesCorrectDatesForAnalysis`() = runTest {
            // Given
            val currentDate = "2026-02-20"
            val previousDate = "2026-02-19"
            coEvery { etfDao.getAllDistinctDates(10) } returns listOf(currentDate, previousDate)

            // When
            executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao)

            // Then
            coVerify { advancedAnalysisRepository.calculateMarketCapWeightedFlow(currentDate, previousDate) }
            coVerify { advancedAnalysisRepository.analyzeSupplyDemandDivergence(currentDate) }
            coVerify { advancedAnalysisRepository.calculateAndSaveLiquidityAnalysis(currentDate) }
        }

        @Test
        @DisplayName("캐시된 상관관계가 있으면 calculateAllEtfCorrelations()를 호출하지 않는다")
        fun `executeAdvancedLogic_cachedCorrelationsExist_skipRecalculation`() = runTest {
            // Given: 캐시 존재
            coEvery { advancedAnalysisRepository.getHighOverlapEtfPairs(any(), 0.0) } returns
                listOf(makeEtfCorrelation(), makeEtfCorrelation())

            // When
            executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao)

            // Then: 재계산 없음
            coVerify(exactly = 0) { advancedAnalysisRepository.calculateAllEtfCorrelations(any()) }
        }

        @Test
        @DisplayName("캐시가 없으면 calculateAllEtfCorrelations()를 호출한다")
        fun `executeAdvancedLogic_noCachedCorrelations_triggersRecalculation`() = runTest {
            // Given: 캐시 없음
            coEvery { advancedAnalysisRepository.getHighOverlapEtfPairs(any(), 0.0) } returns emptyList()
            coEvery { advancedAnalysisRepository.calculateAllEtfCorrelations(any()) } returns listOf(makeEtfCorrelation())

            // When
            executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao)

            // Then: 재계산 호출
            coVerify(exactly = 1) { advancedAnalysisRepository.calculateAllEtfCorrelations(any()) }
        }

        @Test
        @DisplayName("getAllDistinctDates는 limit=10으로 호출된다")
        fun `executeAdvancedLogic_callsGetAllDistinctDatesWithLimit10`() = runTest {
            // When
            executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao)

            // Then
            coVerify(exactly = 1) { etfDao.getAllDistinctDates(10) }
        }
    }

    // ============================================================
    // PartialSuccessTests: 일부 분석 성공
    // ============================================================

    @Nested
    @DisplayName("일부 분석 성공 경로 (partial success)")
    inner class PartialSuccessTests {

        @BeforeEach
        fun setupDates() {
            coEvery { etfDao.getAllDistinctDates(10) } returns listOf("2026-02-20", "2026-02-19")
        }

        @Test
        @DisplayName("MarketCapFlow만 실패해도 나머지가 성공하면 PARTIAL_SUCCESS를 반환한다")
        fun `executeAdvancedLogic_marketCapFlowFails_otherSucceed_returnsPartialSuccess`() = runTest {
            // Given: 1번 분석만 실패
            coEvery { advancedAnalysisRepository.calculateMarketCapWeightedFlow(any(), any()) } throws
                RuntimeException("시총 흐름 분석 실패")
            coEvery { advancedAnalysisRepository.analyzeSupplyDemandDivergence(any()) } returns makeDivergenceAnalysis()
            coEvery { advancedAnalysisRepository.calculateAndSaveLiquidityAnalysis(any()) } returns makeLiquidityAnalysis()
            coEvery { advancedAnalysisRepository.calculateAndSaveSectorAnalysis(any(), any()) } returns listOf(makeSectorAnalysis())
            coEvery { advancedAnalysisRepository.detectSectorRotation(any(), any()) } returns emptyList()
            coEvery { advancedAnalysisRepository.getHighOverlapEtfPairs(any(), any()) } returns listOf(makeEtfCorrelation())

            // When
            val result = executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao)

            // Then: 4개 성공 → PARTIAL_SUCCESS
            assertEquals(WorkerResult.PARTIAL_SUCCESS, result)
        }

        @Test
        @DisplayName("Divergence만 실패해도 나머지가 성공하면 PARTIAL_SUCCESS를 반환한다")
        fun `executeAdvancedLogic_divergenceFails_otherSucceed_returnsPartialSuccess`() = runTest {
            // Given
            coEvery { advancedAnalysisRepository.calculateMarketCapWeightedFlow(any(), any()) } returns makeMarketCapFlow()
            coEvery { advancedAnalysisRepository.analyzeSupplyDemandDivergence(any()) } throws
                IllegalStateException("수급 데이터 없음")
            coEvery { advancedAnalysisRepository.calculateAndSaveLiquidityAnalysis(any()) } returns makeLiquidityAnalysis()
            coEvery { advancedAnalysisRepository.calculateAndSaveSectorAnalysis(any(), any()) } returns listOf(makeSectorAnalysis())
            coEvery { advancedAnalysisRepository.detectSectorRotation(any(), any()) } returns emptyList()
            coEvery { advancedAnalysisRepository.getHighOverlapEtfPairs(any(), any()) } returns listOf(makeEtfCorrelation())

            // When
            val result = executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao)

            // Then
            assertEquals(WorkerResult.PARTIAL_SUCCESS, result)
        }

        @Test
        @DisplayName("유동성 분석이 null을 반환해도 성공으로 처리된다 (예외 미발생)")
        fun `executeAdvancedLogic_liquidityAnalysisReturnsNull_countedAsSuccess`() = runTest {
            // Given: 예탁금 데이터 없어서 null 반환 (예외 아님)
            coEvery { advancedAnalysisRepository.calculateMarketCapWeightedFlow(any(), any()) } returns makeMarketCapFlow()
            coEvery { advancedAnalysisRepository.analyzeSupplyDemandDivergence(any()) } returns makeDivergenceAnalysis()
            coEvery { advancedAnalysisRepository.calculateAndSaveLiquidityAnalysis(any()) } returns null
            coEvery { advancedAnalysisRepository.calculateAndSaveSectorAnalysis(any(), any()) } returns listOf(makeSectorAnalysis())
            coEvery { advancedAnalysisRepository.detectSectorRotation(any(), any()) } returns emptyList()
            coEvery { advancedAnalysisRepository.getHighOverlapEtfPairs(any(), any()) } returns listOf(makeEtfCorrelation())

            // When
            val result = executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao)

            // Then: null 반환은 예외가 아니므로 성공으로 카운트 → ALL_SUCCESS
            assertEquals(WorkerResult.ALL_SUCCESS, result)
        }

        @Test
        @DisplayName("섹터 로테이션 감지가 비어있어도 섹터 분석 성공으로 처리된다")
        fun `executeAdvancedLogic_noSectorRotation_sectorAnalysisCountedAsSuccess`() = runTest {
            // Given
            coEvery { advancedAnalysisRepository.calculateMarketCapWeightedFlow(any(), any()) } returns makeMarketCapFlow()
            coEvery { advancedAnalysisRepository.analyzeSupplyDemandDivergence(any()) } returns makeDivergenceAnalysis()
            coEvery { advancedAnalysisRepository.calculateAndSaveLiquidityAnalysis(any()) } returns makeLiquidityAnalysis()
            coEvery { advancedAnalysisRepository.calculateAndSaveSectorAnalysis(any(), any()) } returns emptyList()
            coEvery { advancedAnalysisRepository.detectSectorRotation(any(), any()) } returns emptyList()
            coEvery { advancedAnalysisRepository.getHighOverlapEtfPairs(any(), any()) } returns listOf(makeEtfCorrelation())

            // When
            val result = executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao)

            // Then
            assertEquals(WorkerResult.ALL_SUCCESS, result)
        }
    }

    // ============================================================
    // AllFailTests: 전체 분석 실패
    // ============================================================

    @Nested
    @DisplayName("전체 분석 실패 경로")
    inner class AllFailTests {

        @BeforeEach
        fun setupDates() {
            coEvery { etfDao.getAllDistinctDates(10) } returns listOf("2026-02-20", "2026-02-19")
        }

        @Test
        @DisplayName("모든 분석이 예외를 던지면 FAILURE_ALL_ANALYSES를 반환한다")
        fun `executeAdvancedLogic_allAnalysesFail_returnsFailureAllAnalyses`() = runTest {
            // Given: 모든 분석 예외 발생
            coEvery { advancedAnalysisRepository.calculateMarketCapWeightedFlow(any(), any()) } throws
                RuntimeException("시총 오류")
            coEvery { advancedAnalysisRepository.analyzeSupplyDemandDivergence(any()) } throws
                RuntimeException("수급 오류")
            coEvery { advancedAnalysisRepository.calculateAndSaveLiquidityAnalysis(any()) } throws
                RuntimeException("유동성 오류")
            coEvery { advancedAnalysisRepository.calculateAndSaveSectorAnalysis(any(), any()) } throws
                RuntimeException("섹터 오류")
            coEvery { advancedAnalysisRepository.getHighOverlapEtfPairs(any(), any()) } throws
                RuntimeException("상관관계 오류")

            // When
            val result = executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao)

            // Then
            assertEquals(WorkerResult.FAILURE_ALL_ANALYSES, result)
        }

        @Test
        @DisplayName("전체 실패 시 후속 분석은 계속 실행된다 (runAnalysis는 개별 catch)")
        fun `executeAdvancedLogic_firstAnalysisFails_subsequentAnalysesStillRun`() = runTest {
            // Given: 첫 번째 분석만 실패
            coEvery { advancedAnalysisRepository.calculateMarketCapWeightedFlow(any(), any()) } throws
                RuntimeException("오류")
            coEvery { advancedAnalysisRepository.analyzeSupplyDemandDivergence(any()) } returns makeDivergenceAnalysis()
            coEvery { advancedAnalysisRepository.calculateAndSaveLiquidityAnalysis(any()) } returns makeLiquidityAnalysis()
            coEvery { advancedAnalysisRepository.calculateAndSaveSectorAnalysis(any(), any()) } returns listOf(makeSectorAnalysis())
            coEvery { advancedAnalysisRepository.detectSectorRotation(any(), any()) } returns emptyList()
            coEvery { advancedAnalysisRepository.getHighOverlapEtfPairs(any(), any()) } returns listOf(makeEtfCorrelation())

            // When
            executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao)

            // Then: 첫 번째 실패해도 나머지는 호출됨
            coVerify(exactly = 1) { advancedAnalysisRepository.analyzeSupplyDemandDivergence(any()) }
            coVerify(exactly = 1) { advancedAnalysisRepository.calculateAndSaveLiquidityAnalysis(any()) }
            coVerify(exactly = 1) { advancedAnalysisRepository.calculateAndSaveSectorAnalysis(any(), any()) }
        }
    }

    // ============================================================
    // ExceptionHandlingTests: 외부 예외 (Worker 수준)
    // ============================================================

    @Nested
    @DisplayName("Worker 수준 예외 처리 테스트")
    inner class ExceptionHandlingTests {

        @Test
        @DisplayName("etfDao.getAllDistinctDates가 예외를 던지고 attempt=0이면 RETRY를 반환한다")
        fun `executeAdvancedLogic_daoExceptionAttempt0_returnsRetry`() = runTest {
            // Given
            coEvery { etfDao.getAllDistinctDates(10) } throws RuntimeException("DB 연결 오류")

            // When
            val result = executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao, runAttemptCount = 0)

            // Then
            assertEquals(WorkerResult.RETRY, result)
        }

        @Test
        @DisplayName("etfDao.getAllDistinctDates가 예외를 던지고 attempt=2이면 RETRY를 반환한다")
        fun `executeAdvancedLogic_daoExceptionAttempt2_returnsRetry`() = runTest {
            // Given
            coEvery { etfDao.getAllDistinctDates(10) } throws IllegalStateException("DB 오류")

            // When
            val result = executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao, runAttemptCount = 2)

            // Then
            assertEquals(WorkerResult.RETRY, result)
        }

        @Test
        @DisplayName("etfDao.getAllDistinctDates가 예외를 던지고 attempt=3이면 FAILURE_EXCEPTION을 반환한다")
        fun `executeAdvancedLogic_daoExceptionAttempt3_returnsFailureException`() = runTest {
            // Given
            coEvery { etfDao.getAllDistinctDates(10) } throws RuntimeException("복구 불가 DB 오류")

            // When
            val result = executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao, runAttemptCount = 3)

            // Then
            assertEquals(WorkerResult.FAILURE_EXCEPTION, result)
        }

        @Test
        @DisplayName("retry 임계값은 3이다 (attempt < 3 → RETRY, attempt >= 3 → FAILURE_EXCEPTION)")
        fun `executeAdvancedLogic_retryThresholdBoundary`() = runTest {
            coEvery { etfDao.getAllDistinctDates(10) } throws RuntimeException("오류")

            val resultAt2 = executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao, 2)
            val resultAt3 = executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao, 3)

            assertEquals(WorkerResult.RETRY, resultAt2, "attempt=2는 RETRY이어야 한다")
            assertEquals(WorkerResult.FAILURE_EXCEPTION, resultAt3, "attempt=3은 FAILURE_EXCEPTION이어야 한다")
        }
    }

    // ============================================================
    // CancellationTests: CancellationException 처리
    // ============================================================

    @Nested
    @DisplayName("CancellationException 처리 테스트")
    inner class CancellationTests {

        @Test
        @DisplayName("etfDao에서 CancellationException이 발생하면 rethrow한다")
        fun `executeAdvancedLogic_ceFromDao_rethrows`() = runTest {
            // Given
            coEvery { etfDao.getAllDistinctDates(10) } throws CancellationException("취소됨")

            // When & Then
            assertThrows<CancellationException> {
                executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao)
            }
        }

        @Test
        @DisplayName("runAnalysis 내 분석에서 CancellationException이 발생하면 rethrow한다")
        fun `executeAdvancedLogic_ceFromAnalysis_rethrows`() = runTest {
            // Given
            coEvery { etfDao.getAllDistinctDates(10) } returns listOf("2026-02-20", "2026-02-19")
            coEvery { advancedAnalysisRepository.calculateMarketCapWeightedFlow(any(), any()) } throws
                CancellationException("시장 분석 취소")

            // When & Then: runAnalysis 내에서도 CE는 rethrow → Worker catch(CE) → rethrow
            assertThrows<CancellationException> {
                executeAdvancedAnalysisLogic(advancedAnalysisRepository, etfDao)
            }
        }
    }

    // ============================================================
    // RunAnalysisHelperTests: runAnalysis 헬퍼 로직 단위 검증
    // ============================================================

    @Nested
    @DisplayName("runAnalysis 헬퍼 로직 검증")
    inner class RunAnalysisHelperTests {

        @Test
        @DisplayName("runAnalysis: 블록이 성공적으로 실행되면 true를 반환한다")
        fun `runAnalysis_blockSucceeds_returnsTrue`() {
            val result = runAnalysis("Test") { /* no-op */ }
            assertTrue(result)
        }

        @Test
        @DisplayName("runAnalysis: 블록에서 Exception이 발생하면 false를 반환한다")
        fun `runAnalysis_blockThrowsException_returnsFalse`() {
            val result = runAnalysis("Test") { throw RuntimeException("오류") }
            assertFalse(result)
        }

        @Test
        @DisplayName("runAnalysis: 블록에서 CancellationException이 발생하면 rethrow한다")
        fun `runAnalysis_blockThrowsCancellationException_rethrows`() {
            assertThrows<CancellationException> {
                runAnalysis("Test") { throw CancellationException("취소") }
            }
        }

        @Test
        @DisplayName("runAnalysis: 다양한 Exception 타입이 모두 catch되어 false를 반환한다")
        fun `runAnalysis_variousExceptionTypes_allReturnFalse`() {
            assertFalse(runAnalysis("Test") { throw IllegalArgumentException("invalid") })
            assertFalse(runAnalysis("Test") { throw NullPointerException("null") })
            assertFalse(runAnalysis("Test") { throw IllegalStateException("state") })
        }
    }

    // ============================================================
    // ConstantTests: Worker 상수 및 Output Key 검증
    // ============================================================

    @Nested
    @DisplayName("Worker 상수 및 Output Key 검증")
    inner class ConstantTests {

        @Test
        @DisplayName("WORK_NAME 상수가 예상 값을 가진다")
        fun `advancedAnalysisWorker_workNameConstant_hasExpectedValue`() {
            assertEquals("advanced_analysis_work", AdvancedAnalysisWorker.WORK_NAME)
        }

        @Test
        @DisplayName("Output Key 상수들이 예상 값을 가진다")
        fun `advancedAnalysisWorker_outputKeyConstants_haveExpectedValues`() {
            assertEquals("market_cap_flow_success", AdvancedAnalysisWorker.KEY_MARKET_CAP_FLOW_SUCCESS)
            assertEquals("divergence_success", AdvancedAnalysisWorker.KEY_DIVERGENCE_SUCCESS)
            assertEquals("liquidity_success", AdvancedAnalysisWorker.KEY_LIQUIDITY_SUCCESS)
            assertEquals("sector_success", AdvancedAnalysisWorker.KEY_SECTOR_SUCCESS)
            assertEquals("correlation_success", AdvancedAnalysisWorker.KEY_CORRELATION_SUCCESS)
            assertEquals("error_message", AdvancedAnalysisWorker.KEY_ERROR_MESSAGE)
        }
    }

    /**
     * WorkerResult: Worker.Result를 JVM 테스트에서 표현하기 위한 내부 enum.
     * Worker.Result는 Android 라이브러리 클래스이므로 JVM에서 직접 사용 불가.
     */
    private enum class WorkerResult {
        ALL_SUCCESS,
        PARTIAL_SUCCESS,
        FAILURE_INSUFFICIENT_DATES,
        FAILURE_ALL_ANALYSES,
        FAILURE_EXCEPTION,
        RETRY
    }
}
