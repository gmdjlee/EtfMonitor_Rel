package com.etfmonitor.feature.analysis.data.repository

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.DailyEtfStatisticsDao
import com.etfmonitor.core.database.FearGreedDao
import com.etfmonitor.core.database.MarketDepositDao
import com.etfmonitor.core.database.MarketIndexDao
import com.etfmonitor.core.database.MarketOscillatorDao
import com.etfmonitor.core.database.entities.DailyEtfStatistics
import com.etfmonitor.core.database.entities.MarketDeposit as MarketDepositEntity
import com.etfmonitor.core.database.entities.MarketIndex as MarketIndexEntity
import com.etfmonitor.core.database.entities.MarketOscillatorData
import com.etfmonitor.core.network.ai.AIApiClient
import com.etfmonitor.core.network.ai.AIApiClientFactory
import com.etfmonitor.core.network.ai.AIModel
import com.etfmonitor.core.network.ai.AIProvider
import com.etfmonitor.core.network.ai.MarketSignal
import com.etfmonitor.core.network.ai.RiskLevel
import com.etfmonitor.core.network.ai.SignalType
import com.etfmonitor.feature.analysis.domain.repository.AnalysisTypeRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * AIAnalysisRepositoryImpl 테스트
 *
 * 테스트 범위:
 * - analyzeMarket — 성공, 시장 지수 누락, ETF 통계 누락, AI API 실패
 * - isApiAvailable / testApiConnection — AIApiClient 위임
 * - getSelectedProvider / getAvailableProviders — factory 위임
 * - listModels — 성공 및 예외 처리
 * - collectAnalysisData — optional 데이터 (FearGreed, Oscillator, Deposit) 없어도 성공
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class AIAnalysisRepositoryImplTest {

    private lateinit var aiApiClientFactory: AIApiClientFactory
    private lateinit var aiApiClient: AIApiClient
    private lateinit var marketIndexDao: MarketIndexDao
    private lateinit var dailyEtfStatisticsDao: DailyEtfStatisticsDao
    private lateinit var fearGreedDao: FearGreedDao
    private lateinit var marketOscillatorDao: MarketOscillatorDao
    private lateinit var marketDepositDao: MarketDepositDao

    private lateinit var repository: AIAnalysisRepositoryImpl

    @BeforeEach
    fun setup() {
        aiApiClientFactory = mockk(relaxed = true)
        aiApiClient = mockk(relaxed = true)
        marketIndexDao = mockk(relaxed = true)
        dailyEtfStatisticsDao = mockk(relaxed = true)
        fearGreedDao = mockk(relaxed = true)
        marketOscillatorDao = mockk(relaxed = true)
        marketDepositDao = mockk(relaxed = true)

        every { aiApiClientFactory.getClient() } returns aiApiClient
        every { aiApiClient.provider } returns AIProvider.CLAUDE

        repository = AIAnalysisRepositoryImpl(
            aiApiClientFactory = aiApiClientFactory,
            marketIndexDao = marketIndexDao,
            dailyEtfStatisticsDao = dailyEtfStatisticsDao,
            fearGreedDao = fearGreedDao,
            marketOscillatorDao = marketOscillatorDao,
            marketDepositDao = marketDepositDao
        )
    }

    // ========== analyzeMarket 테스트 ==========

    @Nested
    @DisplayName("analyzeMarket 테스트")
    inner class AnalyzeMarketTests {

        @Test
        @DisplayName("모든 데이터 있을 때 — AI 호출 후 Result.success 반환")
        fun analyzeMarket_allDataPresent_returnsSuccess() = runTest {
            val market = "KOSPI"
            val date = "2025-01-15"
            val indexEntity = createMarketIndexEntity(market, date)
            val etfStats = createDailyEtfStatistics(date)
            val signal = createTestMarketSignal(market, date)

            coEvery { marketIndexDao.getByMarketAndDate(market, date) } returns indexEntity
            coEvery { dailyEtfStatisticsDao.getByDate(date) } returns etfStats
            coEvery { fearGreedDao.getByMarketAndDate(market, date) } returns null
            coEvery { marketOscillatorDao.getByMarketAndDate(market, date) } returns null
            coEvery { marketDepositDao.getDepositByDate(date) } returns null
            coEvery { aiApiClient.analyzeMarket(any()) } returns Result.success(signal)

            val result = repository.analyzeMarket(market, date, AnalysisTypeRequest.COMPREHENSIVE)

            assertTrue(result.isSuccess)
            val response = result.getOrNull()
            assertNotNull(response)
            assertEquals(market, response.signal.market)
            assertEquals(date, response.signal.date)
            coVerify(exactly = 1) { aiApiClient.analyzeMarket(any()) }
        }

        @Test
        @DisplayName("시장 지수 데이터 없으면 — Result.failure (필수 데이터)")
        fun analyzeMarket_marketIndexMissing_returnsFailure() = runTest {
            coEvery { marketIndexDao.getByMarketAndDate(any(), any()) } returns null
            coEvery { dailyEtfStatisticsDao.getByDate(any()) } returns createDailyEtfStatistics("2025-01-15")

            val result = repository.analyzeMarket("KOSPI", "2025-01-15")

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("ETF 통계 없으면 — Result.failure (필수 데이터)")
        fun analyzeMarket_etfStatsMissing_returnsFailure() = runTest {
            coEvery { marketIndexDao.getByMarketAndDate(any(), any()) } returns createMarketIndexEntity("KOSPI", "2025-01-15")
            coEvery { dailyEtfStatisticsDao.getByDate(any()) } returns null

            val result = repository.analyzeMarket("KOSPI", "2025-01-15")

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("AI API 실패 — Result.failure 전파")
        fun analyzeMarket_aiApiFailure_returnsFailure() = runTest {
            val market = "KOSPI"
            val date = "2025-01-15"
            coEvery { marketIndexDao.getByMarketAndDate(market, date) } returns createMarketIndexEntity(market, date)
            coEvery { dailyEtfStatisticsDao.getByDate(date) } returns createDailyEtfStatistics(date)
            coEvery { fearGreedDao.getByMarketAndDate(any(), any()) } returns null
            coEvery { marketOscillatorDao.getByMarketAndDate(any(), any()) } returns null
            coEvery { marketDepositDao.getDepositByDate(any()) } returns null
            coEvery { aiApiClient.analyzeMarket(any()) } returns Result.failure(Exception("API key invalid"))

            val result = repository.analyzeMarket(market, date)

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("FearGreed/Oscillator/Deposit 없어도 — 분석 성공 (optional 데이터)")
        fun analyzeMarket_optionalDataMissing_stillSucceeds() = runTest {
            val market = "KOSDAQ"
            val date = "2025-01-15"
            val signal = createTestMarketSignal(market, date)

            coEvery { marketIndexDao.getByMarketAndDate(market, date) } returns createMarketIndexEntity(market, date)
            coEvery { dailyEtfStatisticsDao.getByDate(date) } returns createDailyEtfStatistics(date)
            coEvery { fearGreedDao.getByMarketAndDate(any(), any()) } returns null
            coEvery { marketOscillatorDao.getByMarketAndDate(any(), any()) } returns null
            coEvery { marketDepositDao.getDepositByDate(any()) } returns null
            coEvery { aiApiClient.analyzeMarket(any()) } returns Result.success(signal)

            val result = repository.analyzeMarket(market, date)

            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("ETF_ONLY 분석 타입으로 호출 — AI 호출 성공")
        fun analyzeMarket_etfOnlyType_callsAiClient() = runTest {
            val market = "KOSPI"
            val date = "2025-01-15"
            val signal = createTestMarketSignal(market, date)
            coEvery { marketIndexDao.getByMarketAndDate(market, date) } returns createMarketIndexEntity(market, date)
            coEvery { dailyEtfStatisticsDao.getByDate(date) } returns createDailyEtfStatistics(date)
            coEvery { fearGreedDao.getByMarketAndDate(any(), any()) } returns null
            coEvery { marketOscillatorDao.getByMarketAndDate(any(), any()) } returns null
            coEvery { marketDepositDao.getDepositByDate(any()) } returns null
            coEvery { aiApiClient.analyzeMarket(any()) } returns Result.success(signal)

            val result = repository.analyzeMarket(market, date, AnalysisTypeRequest.ETF_ONLY)

            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("응답에 processingTime 포함")
        fun analyzeMarket_success_responseIncludesProcessingTime() = runTest {
            val market = "KOSPI"
            val date = "2025-01-15"
            val signal = createTestMarketSignal(market, date)
            coEvery { marketIndexDao.getByMarketAndDate(market, date) } returns createMarketIndexEntity(market, date)
            coEvery { dailyEtfStatisticsDao.getByDate(date) } returns createDailyEtfStatistics(date)
            coEvery { fearGreedDao.getByMarketAndDate(any(), any()) } returns null
            coEvery { marketOscillatorDao.getByMarketAndDate(any(), any()) } returns null
            coEvery { marketDepositDao.getDepositByDate(any()) } returns null
            coEvery { aiApiClient.analyzeMarket(any()) } returns Result.success(signal)

            val result = repository.analyzeMarket(market, date)

            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()!!.processingTime >= 0)
        }
    }

    // ========== isApiAvailable / testApiConnection 테스트 ==========

    @Nested
    @DisplayName("API 가용성 테스트")
    inner class ApiAvailabilityTests {

        @Test
        @DisplayName("isApiAvailable — aiApiClient.isApiAvailable()에 위임")
        fun isApiAvailable_delegatesToClient() = runTest {
            coEvery { aiApiClient.isApiAvailable() } returns true

            assertTrue(repository.isApiAvailable())
            coVerify(exactly = 1) { aiApiClient.isApiAvailable() }
        }

        @Test
        @DisplayName("isApiAvailable — API 설정 안 된 경우 false")
        fun isApiAvailable_notConfigured_returnsFalse() = runTest {
            coEvery { aiApiClient.isApiAvailable() } returns false

            assertFalse(repository.isApiAvailable())
        }

        @Test
        @DisplayName("testApiConnection — aiApiClient.testApiKey()에 위임")
        fun testApiConnection_delegatesToClient() = runTest {
            coEvery { aiApiClient.testApiKey() } returns Result.success(true)

            val result = repository.testApiConnection()

            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull() == true)
        }
    }

    // ========== getSelectedProvider / getAvailableProviders ==========

    @Nested
    @DisplayName("제공자 정보 테스트")
    inner class ProviderTests {

        @Test
        @DisplayName("getSelectedProvider — 현재 클라이언트 provider 반환")
        fun getSelectedProvider_returnClientProvider() {
            every { aiApiClient.provider } returns AIProvider.CLAUDE

            val provider = repository.getSelectedProvider()

            assertEquals(AIProvider.CLAUDE, provider)
        }

        @Test
        @DisplayName("getAvailableProviders — 팩토리에서 모든 제공자 반환")
        fun getAvailableProviders_returnsAllProviders() {
            every { aiApiClientFactory.getAvailableProviders() } returns listOf(AIProvider.CLAUDE, AIProvider.GEMINI)

            val providers = repository.getAvailableProviders()

            assertEquals(2, providers.size)
            assertTrue(AIProvider.CLAUDE in providers)
            assertTrue(AIProvider.GEMINI in providers)
        }
    }

    // ========== listModels 테스트 ==========

    @Nested
    @DisplayName("listModels 테스트")
    inner class ListModelsTests {

        @Test
        @DisplayName("listModels — Claude 모델 목록 반환")
        fun listModels_claude_returnsModels() = runTest {
            val models = listOf(AIModel("claude-3-5-sonnet", "Claude 3.5 Sonnet", AIProvider.CLAUDE))
            val claudeClient = mockk<AIApiClient>(relaxed = true)
            every { aiApiClientFactory.getClient(AIProvider.CLAUDE) } returns claudeClient
            coEvery { claudeClient.listModels() } returns Result.success(models)

            val result = repository.listModels(AIProvider.CLAUDE)

            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrNull()?.size)
        }

        @Test
        @DisplayName("listModels — 예외 발생 시 Result.failure")
        fun listModels_exceptionThrown_returnsFailure() = runTest {
            val claudeClient = mockk<AIApiClient>(relaxed = true)
            every { aiApiClientFactory.getClient(AIProvider.CLAUDE) } returns claudeClient
            coEvery { claudeClient.listModels() } throws RuntimeException("Network error")

            val result = repository.listModels(AIProvider.CLAUDE)

            assertTrue(result.isFailure)
        }
    }

    // ========== Helpers ==========

    private fun createMarketIndexEntity(
        market: String,
        date: String,
        closePrice: Double = 2800.0
    ): MarketIndexEntity = MarketIndexEntity(
        id = "$market-$date",
        market = market,
        date = date,
        closePrice = closePrice,
        openPrice = closePrice - 10.0,
        highPrice = closePrice + 20.0,
        lowPrice = closePrice - 15.0,
        volume = 1_000_000L,
        changeRate = 0.5,
        lastUpdated = System.currentTimeMillis()
    )

    private fun createDailyEtfStatistics(date: String): DailyEtfStatistics = DailyEtfStatistics(
        date = date,
        newStockCount = 3,
        newStockAmount = 1_500_000_000L,
        removedStockCount = 1,
        removedStockAmount = 500_000_000L,
        increasedStockCount = 10,
        increasedStockAmount = 5_000_000_000L,
        decreasedStockCount = 5,
        decreasedStockAmount = 2_000_000_000L,
        cashDepositAmount = 100_000_000_000L,
        cashDepositChange = 500_000_000L,
        cashDepositChangeRate = 0.5,
        totalEtfCount = 50,
        totalHoldingAmount = 10_000_000_000_000L,
        lastUpdated = System.currentTimeMillis()
    )

    private fun createTestMarketSignal(market: String, date: String): MarketSignal = MarketSignal(
        market = market,
        date = date,
        signal = SignalType.BUY,
        confidence = 0.75,
        upProbability = 65.0,
        downProbability = 35.0,
        reasoning = "기술적 지표와 ETF 자금 흐름 분석 기반",
        keyFactors = listOf("ETF 순유입", "RSI 50 이상"),
        recommendation = "비중 확대 검토",
        riskLevel = RiskLevel.MEDIUM,
        generatedAt = System.currentTimeMillis()
    )
}
