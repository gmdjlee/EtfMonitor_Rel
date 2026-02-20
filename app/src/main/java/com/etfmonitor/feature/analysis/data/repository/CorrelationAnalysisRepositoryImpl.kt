package com.etfmonitor.feature.analysis.data.repository

import com.etfmonitor.core.analysis.AnalysisContext
import com.etfmonitor.core.analysis.CorrelationAnalyzer
import com.etfmonitor.core.network.ai.SignalType
import com.etfmonitor.core.database.AIAnalysisDao
import com.etfmonitor.core.database.CorrelationAnalysisDao
import com.etfmonitor.core.database.DailyEtfStatisticsDao
import com.etfmonitor.core.database.entities.AIAnalysisResult as AIAnalysisEntity
import com.etfmonitor.core.database.entities.CorrelationAnalysisResult as CorrelationEntity
import com.etfmonitor.core.network.ai.AIApiClientFactory
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.feature.analysis.data.mapper.toDomain
import com.etfmonitor.feature.analysis.domain.model.AIAnalysis
import com.etfmonitor.feature.analysis.domain.model.CorrelationAnalysis
import com.etfmonitor.feature.analysis.domain.model.FullAnalysis
import com.etfmonitor.feature.analysis.domain.repository.CorrelationAnalysisRepository
import com.etfmonitor.feature.market.domain.repository.MarketIndexRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 상관관계 분석 Repository 구현체
 * 로컬 상관관계 계산 + AI 해석 통합
 */
@Singleton
class CorrelationAnalysisRepositoryImpl @Inject constructor(
    private val correlationAnalyzer: CorrelationAnalyzer,
    private val correlationAnalysisDao: CorrelationAnalysisDao,
    private val aiAnalysisDao: AIAnalysisDao,
    private val dailyEtfStatisticsDao: DailyEtfStatisticsDao,
    private val marketIndexRepository: MarketIndexRepository,
    private val aiApiClientFactory: AIApiClientFactory
) : CorrelationAnalysisRepository {

    companion object {
        private val logger = AppLogger.getLogger("CorrelationRepoImpl")
        private const val DEFAULT_PERIOD_DAYS = 30
    }

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override suspend fun runCorrelationAnalysis(
        market: String,
        endDate: String,
        periodDays: Int
    ): Result<CorrelationAnalysis> = withContext(Dispatchers.IO) {
        try {
            logger.d("Running correlation analysis for $market on $endDate")

            // 상관관계 분석 실행
            val result = correlationAnalyzer.analyze(market, endDate, periodDays)

            if (result.isSuccess) {
                val analysisResult = result.getOrThrow()
                // 결과 저장
                correlationAnalysisDao.insert(analysisResult)
                logger.d("Correlation analysis saved: ${analysisResult.id}")
                Result.success(analysisResult.toDomain())
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Analysis failed"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Correlation analysis failed", e)
            Result.failure(e)
        }
    }

    override suspend fun runLatestCorrelationAnalysis(
        market: String,
        periodDays: Int
    ): Result<CorrelationAnalysis> = withContext(Dispatchers.IO) {
        try {
            // 최신 날짜 조회
            val latestDate = dailyEtfStatisticsDao.getLatestDate()
                ?: return@withContext Result.failure(Exception("데이터가 없습니다. ETF 데이터를 먼저 수집해주세요."))

            // 시장 지수 데이터 확인 및 자동 수집
            val hasMarketIndexData = marketIndexRepository.hasData(market)
            if (!hasMarketIndexData) {
                logger.d("No market index data found for $market. Fetching data...")
                val fetchResult = marketIndexRepository.initializeMarketIndex(periodDays + 30)
                if (fetchResult.isFailure) {
                    logger.w("Failed to fetch market index data: ${fetchResult.exceptionOrNull()?.message}")
                } else {
                    logger.d("Successfully fetched market index data: ${fetchResult.getOrNull()} records")
                }
            }

            runCorrelationAnalysis(market, latestDate, periodDays)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Latest correlation analysis failed", e)
            Result.failure(e)
        }
    }

    override suspend fun interpretWithAI(
        correlationResult: CorrelationAnalysis
    ): Result<AIAnalysis> = withContext(Dispatchers.IO) {
        try {
            logger.d("Interpreting correlation with AI for ${correlationResult.id}")

            val startTime = System.currentTimeMillis()
            val client = aiApiClientFactory.getClient()

            // 분석 컨텍스트 파싱
            val context = try {
                json.decodeFromString<AnalysisContext>(correlationResult.analysisContext)
            } catch (e: Exception) {
                logger.w("Failed to parse analysis context", e)
                null
            }

            // 프롬프트 생성
            val prompt = createInterpretationPrompt(correlationResult, context)

            // AI 분석 요청
            val signalResult = client.analyzeMarket(prompt, temperature = 0.5)

            if (signalResult.isFailure) {
                return@withContext Result.failure(
                    signalResult.exceptionOrNull() ?: Exception("AI 분석 실패")
                )
            }

            val signal = signalResult.getOrThrow()
            val processingTime = System.currentTimeMillis() - startTime

            // AI 분석 결과 생성
            val aiResult = AIAnalysisEntity(
                id = UUID.randomUUID().toString(),
                market = correlationResult.market,
                analysisDate = correlationResult.analysisDate,
                correlationResultId = correlationResult.id,
                aiProvider = client.provider.name,
                aiModel = aiApiClientFactory.getSelectedModel(client.provider),
                signal = signal.signal.name,
                confidence = signal.confidence,
                upProbability = signal.upProbability,
                downProbability = signal.downProbability,
                riskLevel = signal.riskLevel.name,
                reasoning = signal.reasoning,
                keyFactors = json.encodeToString(signal.keyFactors),
                recommendation = signal.recommendation,
                alternativeScenarios = null,
                promptUsed = prompt,
                rawResponse = signal.reasoning,
                processingTimeMs = processingTime
            )

            // 결과 저장
            aiAnalysisDao.insert(aiResult)
            logger.d("AI analysis saved: ${aiResult.id}")

            Result.success(aiResult.toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("AI interpretation failed", e)
            Result.failure(e)
        }
    }

    override suspend fun runFullAnalysis(
        market: String,
        endDate: String?,
        periodDays: Int
    ): Result<FullAnalysis> = withContext(Dispatchers.IO) {
        try {
            // 1. 상관관계 분석
            val correlationResult = if (endDate != null) {
                runCorrelationAnalysis(market, endDate, periodDays)
            } else {
                runLatestCorrelationAnalysis(market, periodDays)
            }

            if (correlationResult.isFailure) {
                return@withContext Result.failure(
                    correlationResult.exceptionOrNull() ?: Exception("상관관계 분석 실패")
                )
            }

            val correlation = correlationResult.getOrThrow()

            // 2. AI 해석
            val aiResult = interpretWithAI(correlation)

            if (aiResult.isFailure) {
                // AI 실패해도 상관관계 결과는 반환
                logger.w("AI interpretation failed, returning correlation only")
                return@withContext Result.success(
                    FullAnalysis(
                        correlationResult = correlation,
                        aiResult = null,
                        errorMessage = "AI 분석 실패: ${aiResult.exceptionOrNull()?.message}"
                    )
                )
            }

            Result.success(
                FullAnalysis(
                    correlationResult = correlation,
                    aiResult = aiResult.getOrThrow(),
                    errorMessage = null
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Full analysis failed", e)
            Result.failure(e)
        }
    }

    override fun getCorrelationResults(market: String): Flow<List<CorrelationAnalysis>> {
        return correlationAnalysisDao.getAllByMarket(market)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getLatestCorrelationResult(market: String): CorrelationAnalysis? =
        withContext(Dispatchers.IO) {
            correlationAnalysisDao.getLatestByMarket(market)?.toDomain()
        }

    override suspend fun getLatestAIResult(market: String): AIAnalysis? =
        withContext(Dispatchers.IO) {
            aiAnalysisDao.getLatestByMarket(market)?.toDomain()
        }

    // ==================== Private Helpers ====================

    private fun createInterpretationPrompt(
        result: CorrelationAnalysis,
        context: AnalysisContext?
    ): String {
        return buildString {
            appendLine("당신은 한국 주식 시장 전문 애널리스트입니다.")
            appendLine("다음 상관관계 분석 결과를 해석하여 투자 신호를 제공해주세요.")
            appendLine()
            appendLine("## 분석 개요")
            appendLine("- 시장: ${result.market}")
            appendLine("- 분석 날짜: ${result.analysisDate}")
            appendLine("- 분석 기간: ${result.periodDays}일")
            appendLine()

            // 현재 시장 상황
            if (context != null) {
                appendLine("## 현재 시장 상황")
                appendLine("- 현재 지수: ${String.format("%.2f", context.currentIndex)}")
                appendLine("- 등락률: ${String.format("%+.2f", context.indexChangeRate)}%")
                appendLine()

                context.etfSummary?.let { etf ->
                    appendLine("## ETF 편입/편출 현황")
                    appendLine("- 신규 편입: ${etf.newStocks}개 종목")
                    appendLine("- 편입 제외: ${etf.removedStocks}개 종목")
                    appendLine("- 순 편입: ${etf.newStocks - etf.removedStocks}개")
                    appendLine("- 비중 증가: ${etf.increasedStocks}개, 감소: ${etf.decreasedStocks}개")
                    appendLine("- 원화예금 변화율: ${String.format("%+.2f", etf.cashDepositChange)}%")
                    appendLine()
                }

                context.fearGreedValue?.let {
                    appendLine("## Fear & Greed Index")
                    appendLine("- 현재 값: ${String.format("%.2f", it)} (${interpretFearGreed(it)})")
                    appendLine()
                }

                context.oscillatorValue?.let {
                    appendLine("## 시장 Oscillator")
                    appendLine("- 현재 값: ${String.format("%.1f", it)} (${interpretOscillator(it)})")
                    appendLine()
                }
            }

            appendLine("## 상관관계 분석 결과")
            appendLine("(지난 ${result.periodDays}일간 각 지표와 시장 지수 등락률의 상관계수)")
            appendLine()
            appendLine("### ETF 통계 상관관계")
            appendLine("- ETF 순 편입(신규-제외) vs 지수: ${formatCorrelation(result.etfNetFlowCorrelation)}")
            appendLine("- ETF 신규 편입 vs 지수: ${formatCorrelation(result.etfNewStockCorrelation)}")
            appendLine("- ETF 편입 제외 vs 지수: ${formatCorrelation(result.etfRemovedStockCorrelation)}")
            appendLine("- 비중 증가 종목 vs 지수: ${formatCorrelation(result.etfIncreasedCorrelation)}")
            appendLine("- 비중 감소 종목 vs 지수: ${formatCorrelation(result.etfDecreasedCorrelation)}")
            appendLine("- 원화예금 변화 vs 지수: ${formatCorrelation(result.cashDepositCorrelation)}")
            appendLine()

            result.marketDepositCorrelation?.let {
                appendLine("### 자금 동향 상관관계")
                appendLine("- 고객예탁금 변화 vs 지수: ${formatCorrelation(it)}")
            }
            result.creditBalanceCorrelation?.let {
                appendLine("- 신용잔고 변화 vs 지수: ${formatCorrelation(it)}")
            }
            appendLine()

            result.fearGreedCorrelation?.let {
                appendLine("### Fear & Greed 상관관계")
                appendLine("- Fear & Greed vs 지수: ${formatCorrelation(it)}")
            }
            result.fearGreedLeadCorrelation?.let {
                appendLine("- Fear & Greed (2일 선행) vs 지수: ${formatCorrelation(it)}")
            }
            appendLine()

            result.oscillatorCorrelation?.let {
                appendLine("### Oscillator 상관관계")
                appendLine("- Oscillator vs 지수: ${formatCorrelation(it)}")
            }
            result.oscillatorLeadCorrelation?.let {
                appendLine("- Oscillator (2일 선행) vs 지수: ${formatCorrelation(it)}")
            }
            appendLine()

            appendLine("## 로컬 분석 결과 (참고)")
            appendLine("- 종합 점수: ${String.format("%.3f", result.compositeScore)}")
            appendLine("- 예비 신호: ${SignalType.valueOf(result.signal).toKorean()}")
            appendLine("- 상승 확률: ${String.format("%.1f", result.upProbability)}%")
            appendLine("- 하락 확률: ${String.format("%.1f", result.downProbability)}%")
            appendLine()

            appendLine("## 분석 요청")
            appendLine("위 상관관계 데이터와 현재 지표 값들을 종합적으로 분석하여,")
            appendLine("다음 JSON 형식으로 투자 신호를 제공해주세요:")
            appendLine()
            appendLine("```json")
            appendLine("{")
            appendLine("  \"signal\": \"STRONG_BUY|BUY|NEUTRAL|SELL|STRONG_SELL\",")
            appendLine("  \"confidence\": 0.0-1.0,")
            appendLine("  \"upProbability\": 0-100,")
            appendLine("  \"downProbability\": 0-100,")
            appendLine("  \"reasoning\": \"분석 근거 상세 설명 (상관관계 해석 포함)\",")
            appendLine("  \"keyFactors\": [\"핵심 요인 1\", \"핵심 요인 2\", \"핵심 요인 3\"],")
            appendLine("  \"recommendation\": \"구체적인 투자 행동 권장사항\",")
            appendLine("  \"riskLevel\": \"LOW|MEDIUM|HIGH\"")
            appendLine("}")
            appendLine("```")
        }
    }

    private fun formatCorrelation(value: Double): String {
        val strength = when {
            kotlin.math.abs(value) >= 0.7 -> "강함"
            kotlin.math.abs(value) >= 0.4 -> "중간"
            kotlin.math.abs(value) >= 0.2 -> "약함"
            else -> "무시"
        }
        val direction = if (value >= 0) "양(+)" else "음(-)"
        return "${String.format("%+.3f", value)} ($direction, $strength)"
    }

    private fun interpretFearGreed(value: Double): String = when {
        value >= 0.8 -> "극단적 탐욕"
        value >= 0.6 -> "탐욕"
        value >= 0.4 -> "중립"
        value >= 0.2 -> "공포"
        else -> "극단적 공포"
    }

    private fun interpretOscillator(value: Double): String = when {
        value > 70 -> "과매수"
        value > 30 -> "중립"
        else -> "과매도"
    }
}
