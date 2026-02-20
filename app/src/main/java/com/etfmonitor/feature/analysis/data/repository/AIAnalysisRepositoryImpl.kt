package com.etfmonitor.feature.analysis.data.repository

import com.etfmonitor.core.network.ai.*
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.database.DailyEtfStatisticsDao
import com.etfmonitor.core.database.FearGreedDao
import com.etfmonitor.core.database.MarketDepositDao
import com.etfmonitor.core.database.MarketIndexDao
import com.etfmonitor.core.database.MarketOscillatorDao
import com.etfmonitor.feature.analysis.domain.repository.AIAnalysisRepository
import com.etfmonitor.feature.analysis.domain.repository.AIAnalysisResponse
import com.etfmonitor.feature.analysis.domain.repository.AnalysisTypeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 분석 Repository 구현체
 * AI API (Claude, Gemini 등)를 활용한 시장 분석 및 신호 생성
 */
@Singleton
class AIAnalysisRepositoryImpl @Inject constructor(
    private val aiApiClientFactory: AIApiClientFactory,
    private val marketIndexDao: MarketIndexDao,
    private val dailyEtfStatisticsDao: DailyEtfStatisticsDao,
    private val fearGreedDao: FearGreedDao,
    private val marketOscillatorDao: MarketOscillatorDao,
    private val marketDepositDao: MarketDepositDao
) : AIAnalysisRepository {

    companion object {
        private val logger = AppLogger.getLogger("AIAnalysisRepoImpl")
    }

    private fun getClient(): AIApiClient = aiApiClientFactory.getClient()

    override suspend fun analyzeMarket(
        market: String,
        date: String,
        analysisType: AnalysisTypeRequest
    ): Result<AIAnalysisResponse> = withContext(Dispatchers.IO) {
        try {
            logger.d("Starting market analysis for $market on $date")

            val startTime = System.currentTimeMillis()

            // 1. 데이터 수집
            val analysisData = collectAnalysisData(market, date)
            if (analysisData == null) {
                logger.e("Failed to collect analysis data for $market on $date")
                return@withContext Result.failure(
                    Exception("$date 날짜의 데이터를 찾을 수 없습니다.\n\n시장 지수 또는 ETF 통계 데이터가 누락되었습니다.\n홈 화면에서 데이터를 수집해주세요.")
                )
            }

            // 2. 프롬프트 생성
            val legacyType = when (analysisType) {
                AnalysisTypeRequest.COMPREHENSIVE -> AnalysisType.COMPREHENSIVE
                AnalysisTypeRequest.ETF_ONLY -> AnalysisType.ETF_ONLY
                AnalysisTypeRequest.TECHNICAL_ONLY -> AnalysisType.TECHNICAL_ONLY
                AnalysisTypeRequest.SENTIMENT_ONLY -> AnalysisType.SENTIMENT_ONLY
            }
            val prompt = when (legacyType) {
                AnalysisType.COMPREHENSIVE -> MarketAnalysisPrompts.createComprehensiveAnalysisPrompt(analysisData)
                AnalysisType.ETF_ONLY -> MarketAnalysisPrompts.createEtfFocusedAnalysisPrompt(analysisData)
                AnalysisType.TECHNICAL_ONLY, AnalysisType.SENTIMENT_ONLY ->
                    MarketAnalysisPrompts.createQuickSignalPrompt(analysisData)
            }

            // 3. AI 분석 수행
            val signalResult = getClient().analyzeMarket(prompt)
            if (signalResult.isFailure) {
                return@withContext Result.failure(signalResult.exceptionOrNull()
                    ?: Exception("AI 분석 실패"))
            }

            val signal = signalResult.getOrThrow().copy(
                market = market,
                date = date
            )

            val processingTime = System.currentTimeMillis() - startTime

            logger.d("Market analysis completed: signal=${signal.signal}, confidence=${signal.confidence}")

            Result.success(
                AIAnalysisResponse(
                    signal = signal,
                    alternativeScenarios = emptyList(),
                    historicalAccuracy = null,
                    processingTime = processingTime
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Market analysis error", e)
            Result.failure(e)
        }
    }

    override suspend fun isApiAvailable(): Boolean {
        return getClient().isApiAvailable()
    }

    override suspend fun testApiConnection(): Result<Boolean> {
        return getClient().testApiKey()
    }

    override fun getSelectedProvider(): AIProvider {
        return aiApiClientFactory.getClient().provider
    }

    override fun getAvailableProviders(): List<AIProvider> {
        return aiApiClientFactory.getAvailableProviders()
    }

    override suspend fun listModels(provider: AIProvider): Result<List<AIModel>> = withContext(Dispatchers.IO) {
        try {
            val client = aiApiClientFactory.getClient(provider)
            client.listModels()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Failed to list models for $provider", e)
            Result.failure(e)
        }
    }

    // ==================== Private Helpers ====================

    private suspend fun collectAnalysisData(
        market: String,
        date: String
    ): MarketAnalysisData? = withContext(Dispatchers.IO) {
        try {
            logger.d("Collecting analysis data for market=$market, date=$date")

            // 시장 지수
            val marketIndex = marketIndexDao.getByMarketAndDate(market, date)
            if (marketIndex == null) {
                logger.e("Market index not found for $market on $date")
                return@withContext null
            }
            logger.d("Market index found: closePrice=${marketIndex.closePrice}, changeRate=${marketIndex.changeRate}")

            // ETF 통계
            val etfStats = dailyEtfStatisticsDao.getByDate(date)
            if (etfStats == null) {
                logger.e("ETF statistics not found for $date")
                return@withContext null
            }
            logger.d("ETF stats found: newStocks=${etfStats.newStockCount}, removed=${etfStats.removedStockCount}")

            // Fear & Greed (optional)
            val fearGreed = try {
                fearGreedDao.getByMarketAndDate(market, date)
            } catch (e: Exception) {
                null
            }

            // 시장 Oscillator (optional)
            val oscillator = try {
                marketOscillatorDao.getByMarketAndDate(market, date)
            } catch (e: Exception) {
                null
            }

            // 고객예탁금 (optional)
            val marketDeposit = try {
                marketDepositDao.getDepositByDate(date)
            } catch (e: Exception) {
                null
            }

            MarketAnalysisData(
                market = market,
                date = date,
                currentIndex = marketIndex.closePrice,
                indexChange = marketIndex.changeRate,
                newStocks = etfStats.newStockCount,
                newStocksAmount = etfStats.newStockAmount,
                removedStocks = etfStats.removedStockCount,
                removedStocksAmount = etfStats.removedStockAmount,
                increasedStocks = etfStats.increasedStockCount,
                increasedStocksAmount = etfStats.increasedStockAmount,
                decreasedStocks = etfStats.decreasedStockCount,
                decreasedStocksAmount = etfStats.decreasedStockAmount,
                cashDeposit = etfStats.cashDepositAmount,
                cashDepositChange = etfStats.cashDepositChange,
                cashDepositChangeRate = etfStats.cashDepositChangeRate,
                depositAmount = marketDeposit?.depositAmount,
                depositChange = marketDeposit?.depositChange,
                fearGreedValue = fearGreed?.fearGreedValue,
                fearGreedOscillator = fearGreed?.oscillator,
                marketOscillator = oscillator?.oscillator
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Error collecting analysis data", e)
            null
        }
    }
}
