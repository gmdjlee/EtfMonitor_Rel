package com.etfmonitor.repository

import com.etfmonitor.ai.*
import com.etfmonitor.utils.AppLogger
import com.etfmonitor.database.DailyEtfStatisticsDao
import com.etfmonitor.database.FearGreedDao
import com.etfmonitor.database.MarketDepositDao
import com.etfmonitor.database.MarketIndexDao
import com.etfmonitor.database.MarketOscillatorDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 분석 Repository
 * AI API (Claude, Gemini 등)를 활용한 시장 분석 및 신호 생성
 */
@Singleton
class AIAnalysisRepository @Inject constructor(
    private val aiApiClientFactory: AIApiClientFactory,
    private val marketIndexDao: MarketIndexDao,
    private val dailyEtfStatisticsDao: DailyEtfStatisticsDao,
    private val fearGreedDao: FearGreedDao,
    private val marketOscillatorDao: MarketOscillatorDao,
    private val marketDepositDao: MarketDepositDao
) {
    companion object {
        private val logger = AppLogger.getLogger("AIAnalysisRepo")
    }

    /**
     * 현재 선택된 AI 클라이언트
     */
    private fun getClient(): AIApiClient = aiApiClientFactory.getClient()

    /**
     * 종합 시장 분석 수행
     */
    suspend fun analyzeMarket(
        market: String,
        date: String,
        analysisType: AnalysisType = AnalysisType.COMPREHENSIVE
    ): Result<AIAnalysisResponse> = withContext(Dispatchers.IO) {
        try {
            logger.d( "Starting market analysis for $market on $date")

            val startTime = System.currentTimeMillis()

            // 1. 데이터 수집
            val analysisData = collectAnalysisData(market, date)
            if (analysisData == null) {
                logger.e( "Failed to collect analysis data for $market on $date")
                return@withContext Result.failure(
                    Exception("$date 날짜의 데이터를 찾을 수 없습니다.\n\n시장 지수 또는 ETF 통계 데이터가 누락되었습니다.\n홈 화면에서 데이터를 수집해주세요.")
                )
            }

            // 2. 프롬프트 생성
            val prompt = when (analysisType) {
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

            logger.d( "Market analysis completed: signal=${signal.signal}, confidence=${signal.confidence}")

            Result.success(
                AIAnalysisResponse(
                    signal = signal,
                    alternativeScenarios = emptyList(), // 향후 구현
                    historicalAccuracy = null, // 백테스트 결과 추가 가능
                    processingTime = processingTime
                )
            )
        } catch (e: Exception) {
            logger.e( "Market analysis error", e)
            Result.failure(e)
        }
    }

    /**
     * 최신 데이터로 시장 분석
     */
    suspend fun analyzeLatestMarket(market: String): Result<AIAnalysisResponse> = withContext(Dispatchers.IO) {
        try {
            logger.d( "Analyzing latest market data for: $market")

            // 최신 날짜 조회
            val latestDate = dailyEtfStatisticsDao.getLatestDate()

            if (latestDate == null) {
                logger.e( "No ETF statistics data found in database")
                return@withContext Result.failure(
                    Exception("데이터가 없습니다.\n\n홈 화면에서 'ETF 데이터 수집'을 먼저 실행해주세요.")
                )
            }

            logger.d( "Latest date found: $latestDate")
            analyzeMarket(market, latestDate)
        } catch (e: Exception) {
            logger.e( "Latest market analysis error", e)
            Result.failure(e)
        }
    }

    /**
     * 분석 데이터 수집
     */
    private suspend fun collectAnalysisData(
        market: String,
        date: String
    ): MarketAnalysisData? = withContext(Dispatchers.IO) {
        try {
            logger.d( "Collecting analysis data for market=$market, date=$date")

            // 시장 지수
            val marketIndex = marketIndexDao.getByMarketAndDate(market, date)
            if (marketIndex == null) {
                logger.e( "Market index not found for $market on $date")
                return@withContext null
            }
            logger.d( "Market index found: closePrice=${marketIndex.closePrice}, changeRate=${marketIndex.changeRate}")

            // ETF 통계
            val etfStats = dailyEtfStatisticsDao.getByDate(date)
            if (etfStats == null) {
                logger.e( "ETF statistics not found for $date")
                return@withContext null
            }
            logger.d( "ETF stats found: newStocks=${etfStats.newStockCount}, removed=${etfStats.removedStockCount}")

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

                // ETF 통계
                newStocks = etfStats.newStockCount,
                newStocksAmount = etfStats.newStockAmount,
                removedStocks = etfStats.removedStockCount,
                removedStocksAmount = etfStats.removedStockAmount,
                increasedStocks = etfStats.increasedStockCount,
                increasedStocksAmount = etfStats.increasedStockAmount,
                decreasedStocks = etfStats.decreasedStockCount,
                decreasedStocksAmount = etfStats.decreasedStockAmount,

                // 원화예금
                cashDeposit = etfStats.cashDepositAmount,
                cashDepositChange = etfStats.cashDepositChange,
                cashDepositChangeRate = etfStats.cashDepositChangeRate,

                // 증시 자금 동향
                depositAmount = marketDeposit?.depositAmount,
                depositChange = marketDeposit?.depositChange,

                // Fear & Greed
                fearGreedValue = fearGreed?.fearGreedValue,
                fearGreedOscillator = fearGreed?.oscillator,

                // 과매수/과매도
                marketOscillator = oscillator?.oscillator
            )
        } catch (e: Exception) {
            logger.e( "Error collecting analysis data", e)
            null
        }
    }

    /**
     * API 사용 가능 여부 확인
     */
    suspend fun isApiAvailable(): Boolean {
        return getClient().isApiAvailable()
    }

    /**
     * API 키 테스트
     */
    suspend fun testApiConnection(): Result<Boolean> {
        return getClient().testApiKey()
    }

    /**
     * 현재 선택된 AI 제공자
     */
    fun getSelectedProvider(): AIProvider {
        return aiApiClientFactory.getClient().provider
    }

    /**
     * 사용 가능한 모든 AI 제공자 목록
     */
    fun getAvailableProviders(): List<AIProvider> {
        return aiApiClientFactory.getAvailableProviders()
    }

    /**
     * 특정 AI 제공자의 사용 가능한 모델 목록 조회
     */
    suspend fun listModels(provider: AIProvider): Result<List<AIModel>> = withContext(Dispatchers.IO) {
        try {
            val client = aiApiClientFactory.getClient(provider)
            client.listModels()
        } catch (e: Exception) {
            logger.e( "Failed to list models for $provider", e)
            Result.failure(e)
        }
    }

    /**
     * 빠른 신호 생성 (간소화 분석)
     */
    suspend fun generateQuickSignal(
        market: String,
        date: String
    ): Result<MarketSignal> = withContext(Dispatchers.IO) {
        try {
            val analysisData = collectAnalysisData(market, date)
                ?: return@withContext Result.failure(Exception("데이터 없음"))

            val prompt = MarketAnalysisPrompts.createQuickSignalPrompt(analysisData)
            getClient().analyzeMarket(prompt, temperature = 0.3) // 낮은 temperature로 일관성 향상
        } catch (e: Exception) {
            logger.e( "Quick signal generation error", e)
            Result.failure(e)
        }
    }

    /**
     * 여러 날짜의 신호 배치 생성 (백테스팅용)
     */
    suspend fun generateBatchSignals(
        market: String,
        startDate: String,
        endDate: String
    ): Result<List<SignalRecord>> = withContext(Dispatchers.IO) {
        try {
            val dates = dailyEtfStatisticsDao.getAllDates()
                .filter { it >= startDate && it <= endDate }
                .sorted()

            if (dates.isEmpty()) {
                return@withContext Result.failure(Exception("지정된 기간에 데이터가 없습니다"))
            }

            logger.d( "Generating batch signals for ${dates.size} dates")

            val records = mutableListOf<SignalRecord>()

            for (date in dates) {
                try {
                    val result = generateQuickSignal(market, date)
                    if (result.isSuccess) {
                        val signal = result.getOrThrow()
                        val marketIndex = marketIndexDao.getByMarketAndDate(market, date)

                        records.add(
                            SignalRecord(
                                date = date,
                                signal = signal.signal,
                                confidence = signal.confidence,
                                indexAtSignal = marketIndex?.closePrice ?: 0.0
                            )
                        )
                    }

                    // Rate limiting: 1 request per second to avoid API throttling
                    kotlinx.coroutines.delay(1000)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to generate signal for $date", e)
                    continue
                }
            }

            logger.d( "Batch signal generation completed: ${records.size} signals")
            Result.success(records)
        } catch (e: Exception) {
            logger.e( "Batch signal generation error", e)
            Result.failure(e)
        }
    }
}
