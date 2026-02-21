package com.etfmonitor.feature.analysis.data.repository

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.analysis.StockIndicatorCorrelationRequest
import com.etfmonitor.core.analysis.StockIndicatorCorrelationResult
import com.etfmonitor.core.analysis.FullStockIndicatorCorrelationResult
import com.etfmonitor.core.database.StockIndicatorAIResultDao
import com.etfmonitor.core.database.entities.StockIndicatorAIResult
import com.etfmonitor.feature.analysis.data.internal.TimeSeriesAnalysisHelper
import com.etfmonitor.feature.analysis.domain.model.StockIndicatorRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
 * StockIndicatorRepositoryImpl 단위 테스트
 *
 * 테스트 범위:
 * - searchStock: 성공, 결과 없음
 * - analyzeStockIndicatorCorrelations: 성공, 실패
 * - getStockIndicatorAIHistory: Flow 위임
 * - getAllStockIndicatorAIHistory: 제한 파라미터
 * - deleteStockIndicatorAIHistory: DAO 위임
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("StockIndicatorRepositoryImpl 테스트")
class StockIndicatorRepositoryImplTest {

    private lateinit var timeSeriesHelper: TimeSeriesAnalysisHelper
    private lateinit var stockIndicatorAIResultDao: StockIndicatorAIResultDao
    private lateinit var repository: StockIndicatorRepositoryImpl

    @BeforeEach
    fun setup() {
        timeSeriesHelper = mockk(relaxed = true)
        stockIndicatorAIResultDao = mockk(relaxed = true)
        repository = StockIndicatorRepositoryImpl(timeSeriesHelper, stockIndicatorAIResultDao)
    }

    // ============================================================
    // searchStock
    // ============================================================

    @Nested
    @DisplayName("searchStock 테스트")
    inner class SearchStockTests {

        @Test
        @DisplayName("searchStock_withValidQuery_returnsTickerNamePair")
        fun `searchStock_withValidQuery_returnsTickerNamePair`() = runTest {
            // Given
            coEvery { timeSeriesHelper.searchStock("삼성") } returns Pair("005930", "삼성전자")

            // When
            val result = repository.searchStock("삼성")

            // Then
            assertNotNull(result)
            assertEquals("005930", result.first)
            assertEquals("삼성전자", result.second)
        }

        @Test
        @DisplayName("searchStock_withNoMatch_returnsNull")
        fun `searchStock_withNoMatch_returnsNull`() = runTest {
            // Given
            coEvery { timeSeriesHelper.searchStock(any()) } returns null

            // When
            val result = repository.searchStock("존재하지않는종목")

            // Then
            assertNull(result)
        }
    }

    // ============================================================
    // analyzeStockIndicatorCorrelations
    // ============================================================

    @Nested
    @DisplayName("analyzeStockIndicatorCorrelations 테스트")
    inner class AnalyzeCorrelationsTests {

        @Test
        @DisplayName("analyzeStockIndicatorCorrelations_whenHelperSucceeds_returnsDomainModel")
        fun `analyzeStockIndicatorCorrelations_whenHelperSucceeds_returnsDomainModel`() = runTest {
            // Given
            val request = StockIndicatorRequest("005930", "삼성전자", "KOSPI", 90)
            val legacyResult = createTestCorrelationResult()
            coEvery { timeSeriesHelper.analyzeStockIndicatorCorrelations(any()) } returns Result.success(legacyResult)

            // When
            val result = repository.analyzeStockIndicatorCorrelations(request)

            // Then
            assertTrue(result.isSuccess)
            val domain = result.getOrNull()
            assertNotNull(domain)
            assertEquals("005930", domain.ticker)
            assertEquals("삼성전자", domain.stockName)
        }

        @Test
        @DisplayName("analyzeStockIndicatorCorrelations_whenHelperFails_returnsFailure")
        fun `analyzeStockIndicatorCorrelations_whenHelperFails_returnsFailure`() = runTest {
            // Given
            val request = StockIndicatorRequest("005930", "삼성전자", "KOSPI", 90)
            coEvery { timeSeriesHelper.analyzeStockIndicatorCorrelations(any()) } returns
                Result.failure(RuntimeException("Analysis failed"))

            // When
            val result = repository.analyzeStockIndicatorCorrelations(request)

            // Then
            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("analyzeStockIndicatorCorrelations_mapsRequestToLegacyFormat")
        fun `analyzeStockIndicatorCorrelations_mapsRequestToLegacyFormat`() = runTest {
            // Given
            val request = StockIndicatorRequest("000660", "SK하이닉스", "KOSPI", 60)
            val legacyResult = createTestCorrelationResult("000660", "SK하이닉스")
            coEvery { timeSeriesHelper.analyzeStockIndicatorCorrelations(any()) } returns Result.success(legacyResult)

            // When
            repository.analyzeStockIndicatorCorrelations(request)

            // Then: verify legacy request was passed correctly
            coVerify {
                timeSeriesHelper.analyzeStockIndicatorCorrelations(
                    match { it.ticker == "000660" && it.name == "SK하이닉스" && it.periodDays == 60 }
                )
            }
        }
    }

    // ============================================================
    // getStockIndicatorAIHistory
    // ============================================================

    @Nested
    @DisplayName("getStockIndicatorAIHistory 테스트")
    inner class GetHistoryTests {

        @Test
        @DisplayName("getStockIndicatorAIHistory_withValidTicker_returnsFlow")
        fun `getStockIndicatorAIHistory_withValidTicker_returnsFlow`() = runTest {
            // Given
            val entities = listOf(createTestAIResultEntity("005930"))
            every { stockIndicatorAIResultDao.getAllByTicker("005930") } returns flowOf(entities)

            // When & Then
            repository.getStockIndicatorAIHistory("005930").test {
                val items = awaitItem()
                assertEquals(1, items.size)
                assertEquals("005930", items[0].ticker)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("getStockIndicatorAIHistory_withNoHistory_returnsEmptyFlow")
        fun `getStockIndicatorAIHistory_withNoHistory_returnsEmptyFlow`() = runTest {
            // Given
            every { stockIndicatorAIResultDao.getAllByTicker(any()) } returns flowOf(emptyList())

            // When & Then
            repository.getStockIndicatorAIHistory("999999").test {
                val items = awaitItem()
                assertTrue(items.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // ============================================================
    // getAllStockIndicatorAIHistory
    // ============================================================

    @Nested
    @DisplayName("getAllStockIndicatorAIHistory 테스트")
    inner class GetAllHistoryTests {

        @Test
        @DisplayName("getAllStockIndicatorAIHistory_withLimit_passesLimitToDao")
        fun `getAllStockIndicatorAIHistory_withLimit_passesLimitToDao`() = runTest {
            // Given
            val limit = 50
            val entities = (1..limit).map { createTestAIResultEntity("00593${it % 10}") }
            every { stockIndicatorAIResultDao.getRecent(limit) } returns flowOf(entities)

            // When & Then
            repository.getAllStockIndicatorAIHistory(limit).test {
                val items = awaitItem()
                assertEquals(limit, items.size)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // ============================================================
    // deleteStockIndicatorAIHistory
    // ============================================================

    @Test
    @DisplayName("deleteStockIndicatorAIHistory_withId_callsDaoDelete")
    fun `deleteStockIndicatorAIHistory_withId_callsDaoDelete`() = runTest {
        // Given
        val id = "test-uuid-123"
        coEvery { stockIndicatorAIResultDao.deleteById(id) } returns Unit

        // When
        repository.deleteStockIndicatorAIHistory(id)

        // Then
        coVerify(exactly = 1) { stockIndicatorAIResultDao.deleteById(id) }
    }

    // ============================================================
    // Helpers
    // ============================================================

    private fun createTestCorrelationResult(
        ticker: String = "005930",
        stockName: String = "삼성전자"
    ): StockIndicatorCorrelationResult = StockIndicatorCorrelationResult(
        ticker = ticker,
        stockName = stockName,
        market = "KOSPI",
        startDate = "2025-10-01",
        endDate = "2026-01-01",
        totalDataPoints = 60,
        fearGreedCorrelations = emptyList(),
        oscillatorCorrelations = emptyList(),
        depositCorrelations = emptyList(),
        etfCorrelations = emptyList(),
        topPositiveCorrelations = emptyList(),
        topNegativeCorrelations = emptyList(),
        summary = "분석 완료"
    )

    private fun createTestAIResultEntity(ticker: String): StockIndicatorAIResult = StockIndicatorAIResult(
        id = "test-id-$ticker",
        ticker = ticker,
        stockName = "테스트종목",
        market = "KOSPI",
        analysisDate = "2026-01-15",
        period = "2025-10-15 ~ 2026-01-15",
        periodDays = 90,
        aiProvider = "CLAUDE",
        aiModel = "claude-sonnet-4-6",
        signal = "BUY",
        confidence = 0.75,
        upProbability = 65.0,
        downProbability = 35.0,
        riskLevel = "MEDIUM",
        keyCorrelations = "[]",
        marketSentimentImpact = "긍정적",
        fundFlowImpact = "중립",
        etfFlowImpact = "긍정적",
        reasoning = "기술적 분석 결과",
        recommendation = "비중 확대"
    )
}
