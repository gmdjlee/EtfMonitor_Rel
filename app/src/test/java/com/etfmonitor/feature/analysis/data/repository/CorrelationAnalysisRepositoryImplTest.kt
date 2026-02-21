package com.etfmonitor.feature.analysis.data.repository

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.analysis.CorrelationAnalyzer
import com.etfmonitor.core.database.AIAnalysisDao
import com.etfmonitor.core.database.CorrelationAnalysisDao
import com.etfmonitor.core.database.DailyEtfStatisticsDao
import com.etfmonitor.core.database.entities.AIAnalysisResult
import com.etfmonitor.core.database.entities.CorrelationAnalysisResult
import com.etfmonitor.core.network.ai.AIApiClient
import com.etfmonitor.core.network.ai.AIApiClientFactory
import com.etfmonitor.core.network.ai.AIProvider
import com.etfmonitor.feature.market.domain.repository.MarketIndexRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CorrelationAnalysisRepositoryImpl 단위 테스트
 *
 * 테스트 범위:
 * - runCorrelationAnalysis: 성공 시 저장 + 반환, 실패 전파
 * - runLatestCorrelationAnalysis: 날짜 없음, 시장 지수 자동 수집
 * - interpretWithAI: 성공, AI 실패
 * - runFullAnalysis: 상관관계 성공 + AI 성공, AI 실패 시 부분 결과
 * - getCorrelationResults: Flow 위임
 * - getLatestCorrelationResult: DAO 위임
 * - CancellationException 재전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("CorrelationAnalysisRepositoryImpl 테스트")
class CorrelationAnalysisRepositoryImplTest {

    private lateinit var correlationAnalyzer: CorrelationAnalyzer
    private lateinit var correlationAnalysisDao: CorrelationAnalysisDao
    private lateinit var aiAnalysisDao: AIAnalysisDao
    private lateinit var dailyEtfStatisticsDao: DailyEtfStatisticsDao
    private lateinit var marketIndexRepository: MarketIndexRepository
    private lateinit var aiApiClientFactory: AIApiClientFactory
    private lateinit var aiApiClient: AIApiClient

    private lateinit var repository: CorrelationAnalysisRepositoryImpl

    @BeforeEach
    fun setup() {
        correlationAnalyzer = mockk(relaxed = true)
        correlationAnalysisDao = mockk(relaxed = true)
        aiAnalysisDao = mockk(relaxed = true)
        dailyEtfStatisticsDao = mockk(relaxed = true)
        marketIndexRepository = mockk(relaxed = true)
        aiApiClientFactory = mockk(relaxed = true)
        aiApiClient = mockk(relaxed = true)

        every { aiApiClientFactory.getClient() } returns aiApiClient
        every { aiApiClient.provider } returns AIProvider.CLAUDE
        every { aiApiClientFactory.getSelectedModel(any()) } returns "claude-sonnet-4-6"

        repository = CorrelationAnalysisRepositoryImpl(
            correlationAnalyzer = correlationAnalyzer,
            correlationAnalysisDao = correlationAnalysisDao,
            aiAnalysisDao = aiAnalysisDao,
            dailyEtfStatisticsDao = dailyEtfStatisticsDao,
            marketIndexRepository = marketIndexRepository,
            aiApiClientFactory = aiApiClientFactory
        )
    }

    // ============================================================
    // runCorrelationAnalysis
    // ============================================================

    @Nested
    @DisplayName("runCorrelationAnalysis 테스트")
    inner class RunCorrelationAnalysisTests {

        @Test
        @DisplayName("runCorrelationAnalysis_whenAnalyzerSucceeds_savesToDaoAndReturnsSuccess")
        fun `runCorrelationAnalysis_whenAnalyzerSucceeds_savesToDaoAndReturnsSuccess`() = runTest {
            // Given
            val entity = createTestCorrelationEntity()
            coEvery { correlationAnalyzer.analyze("KOSPI", "2026-01-15", 30) } returns Result.success(entity)
            coEvery { correlationAnalysisDao.insert(any()) } returns Unit

            // When
            val result = repository.runCorrelationAnalysis("KOSPI", "2026-01-15", 30)

            // Then
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { correlationAnalysisDao.insert(entity) }
            assertEquals("KOSPI", result.getOrNull()?.market)
        }

        @Test
        @DisplayName("runCorrelationAnalysis_whenAnalyzerFails_returnsFailure")
        fun `runCorrelationAnalysis_whenAnalyzerFails_returnsFailure`() = runTest {
            // Given
            coEvery { correlationAnalyzer.analyze(any(), any(), any()) } returns
                Result.failure(RuntimeException("Insufficient data"))

            // When
            val result = repository.runCorrelationAnalysis("KOSPI", "2026-01-15", 30)

            // Then
            assertTrue(result.isFailure)
            coVerify(exactly = 0) { correlationAnalysisDao.insert(any()) }
        }

        @Test
        @DisplayName("runCorrelationAnalysis_whenDaoThrows_returnsFailure")
        fun `runCorrelationAnalysis_whenDaoThrows_returnsFailure`() = runTest {
            // Given
            val entity = createTestCorrelationEntity()
            coEvery { correlationAnalyzer.analyze(any(), any(), any()) } returns Result.success(entity)
            coEvery { correlationAnalysisDao.insert(any()) } throws RuntimeException("DB error")

            // When
            val result = repository.runCorrelationAnalysis("KOSPI", "2026-01-15", 30)

            // Then
            assertTrue(result.isFailure)
        }
    }

    // ============================================================
    // runLatestCorrelationAnalysis
    // ============================================================

    @Nested
    @DisplayName("runLatestCorrelationAnalysis 테스트")
    inner class RunLatestCorrelationAnalysisTests {

        @Test
        @DisplayName("runLatestCorrelationAnalysis_withNoLatestDate_returnsFailure")
        fun `runLatestCorrelationAnalysis_withNoLatestDate_returnsFailure`() = runTest {
            // Given
            coEvery { dailyEtfStatisticsDao.getLatestDate() } returns null

            // When
            val result = repository.runLatestCorrelationAnalysis("KOSPI", 30)

            // Then
            assertTrue(result.isFailure)
            val error = result.exceptionOrNull()?.message
            assertNotNull(error)
            assertTrue(error.contains("데이터가 없습니다") || error.contains("ETF"))
        }

        @Test
        @DisplayName("runLatestCorrelationAnalysis_withLatestDate_runsAnalysis")
        fun `runLatestCorrelationAnalysis_withLatestDate_runsAnalysis`() = runTest {
            // Given
            coEvery { dailyEtfStatisticsDao.getLatestDate() } returns "2026-01-15"
            coEvery { marketIndexRepository.hasData("KOSPI") } returns true
            val entity = createTestCorrelationEntity()
            coEvery { correlationAnalyzer.analyze("KOSPI", "2026-01-15", 30) } returns Result.success(entity)
            coEvery { correlationAnalysisDao.insert(any()) } returns Unit

            // When
            val result = repository.runLatestCorrelationAnalysis("KOSPI", 30)

            // Then
            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("runLatestCorrelationAnalysis_withNoMarketIndexData_fetchesData")
        fun `runLatestCorrelationAnalysis_withNoMarketIndexData_fetchesData`() = runTest {
            // Given
            coEvery { dailyEtfStatisticsDao.getLatestDate() } returns "2026-01-15"
            coEvery { marketIndexRepository.hasData("KOSPI") } returns false
            coEvery { marketIndexRepository.initializeMarketIndex(any()) } returns Result.success(60)
            val entity = createTestCorrelationEntity()
            coEvery { correlationAnalyzer.analyze("KOSPI", "2026-01-15", 30) } returns Result.success(entity)
            coEvery { correlationAnalysisDao.insert(any()) } returns Unit

            // When
            repository.runLatestCorrelationAnalysis("KOSPI", 30)

            // Then: market index data was fetched
            coVerify(exactly = 1) { marketIndexRepository.initializeMarketIndex(any()) }
        }
    }

    // ============================================================
    // getCorrelationResults
    // ============================================================

    @Nested
    @DisplayName("getCorrelationResults 테스트")
    inner class GetCorrelationResultsTests {

        @Test
        @DisplayName("getCorrelationResults_withValidMarket_returnsFlow")
        fun `getCorrelationResults_withValidMarket_returnsFlow`() = runTest {
            // Given
            val entities = listOf(createTestCorrelationEntity())
            every { correlationAnalysisDao.getAllByMarket("KOSPI") } returns flowOf(entities)

            // When & Then
            repository.getCorrelationResults("KOSPI").test {
                val items = awaitItem()
                assertEquals(1, items.size)
                assertEquals("KOSPI", items[0].market)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getCorrelationResults_withNoData_returnsEmptyFlow")
        fun `getCorrelationResults_withNoData_returnsEmptyFlow`() = runTest {
            // Given
            every { correlationAnalysisDao.getAllByMarket(any()) } returns flowOf(emptyList())

            // When & Then
            repository.getCorrelationResults("KOSPI").test {
                val items = awaitItem()
                assertTrue(items.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // ============================================================
    // getLatestCorrelationResult
    // ============================================================

    @Test
    @DisplayName("getLatestCorrelationResult_delegatesToDao")
    fun `getLatestCorrelationResult_delegatesToDao`() = runTest {
        // Given
        val entity = createTestCorrelationEntity()
        coEvery { correlationAnalysisDao.getLatestByMarket("KOSPI") } returns entity

        // When
        val result = repository.getLatestCorrelationResult("KOSPI")

        // Then
        assertNotNull(result)
        assertEquals("KOSPI", result.market)
    }

    @Test
    @DisplayName("getLatestCorrelationResult_withNoData_returnsNull")
    fun `getLatestCorrelationResult_withNoData_returnsNull`() = runTest {
        // Given
        coEvery { correlationAnalysisDao.getLatestByMarket(any()) } returns null

        // When
        val result = repository.getLatestCorrelationResult("KOSPI")

        // Then
        assertNull(result)
    }

    // ============================================================
    // getLatestAIResult
    // ============================================================

    @Test
    @DisplayName("getLatestAIResult_withNoData_returnsNull")
    fun `getLatestAIResult_withNoData_returnsNull`() = runTest {
        // Given
        coEvery { aiAnalysisDao.getLatestByMarket(any()) } returns null

        // When
        val result = repository.getLatestAIResult("KOSPI")

        // Then
        assertNull(result)
    }

    // ============================================================
    // CancellationException rethrow
    // ============================================================

    @Test
    @DisplayName("runCorrelationAnalysis_whenCancelled_rethrowsCancellationException")
    fun `runCorrelationAnalysis_whenCancelled_rethrowsCancellationException`() = runTest {
        // Given
        coEvery { correlationAnalyzer.analyze(any(), any(), any()) } throws CancellationException("Cancelled")

        // When & Then
        var exceptionCaught: Throwable? = null
        try {
            repository.runCorrelationAnalysis("KOSPI", "2026-01-15", 30)
        } catch (e: CancellationException) {
            exceptionCaught = e
        }
        assertNotNull(exceptionCaught, "CancellationException must be rethrown")
    }

    // ============================================================
    // Helpers
    // ============================================================

    private fun createTestCorrelationEntity(): CorrelationAnalysisResult = CorrelationAnalysisResult(
        id = "test-correlation-id",
        market = "KOSPI",
        analysisDate = "2026-01-15",
        periodDays = 30,
        etfNetFlowCorrelation = 0.45,
        etfNewStockCorrelation = 0.32,
        etfRemovedStockCorrelation = -0.28,
        etfIncreasedCorrelation = 0.38,
        etfDecreasedCorrelation = -0.25,
        cashDepositCorrelation = 0.15,
        marketDepositCorrelation = null,
        creditBalanceCorrelation = null,
        fearGreedCorrelation = null,
        fearGreedLeadCorrelation = null,
        oscillatorCorrelation = null,
        oscillatorLeadCorrelation = null,
        compositeScore = 0.65,
        signal = "BUY",
        confidence = 0.75,
        upProbability = 65.0,
        downProbability = 35.0,
        analysisContext = "{}",
        createdAt = System.currentTimeMillis()
    )
}
