package com.etfmonitor.feature.analysis.data.repository

import com.etfmonitor.core.database.StockIndicatorAIResultDao
import com.etfmonitor.feature.analysis.data.mapper.toHistoryItem
import com.etfmonitor.feature.analysis.domain.model.*
import com.etfmonitor.feature.analysis.domain.repository.StockIndicatorAIHistoryItem
import com.etfmonitor.feature.analysis.domain.repository.StockIndicatorRepository
import com.etfmonitor.feature.analysis.data.internal.TimeSeriesAnalysisHelper
import com.etfmonitor.core.analysis.StockIndicatorCorrelationRequest as LegacyRequest
import com.etfmonitor.core.analysis.StockIndicatorCorrelationResult as LegacyCorrelationResult
import com.etfmonitor.core.analysis.FullStockIndicatorCorrelationResult as LegacyFullResult
import com.etfmonitor.core.analysis.AIStockIndicatorInterpretation as LegacyInterpretation
import com.etfmonitor.core.analysis.IndicatorStockCorrelation as LegacyIndicatorCorrelation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 종목-지표 상관관계 Repository 구현체
 */
@Singleton
class StockIndicatorRepositoryImpl @Inject constructor(
    private val timeSeriesHelper: TimeSeriesAnalysisHelper,
    private val stockIndicatorAIResultDao: StockIndicatorAIResultDao
) : StockIndicatorRepository {

    override suspend fun searchStock(query: String): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            timeSeriesHelper.searchStock(query)
        }

    override suspend fun analyzeStockIndicatorCorrelations(
        request: StockIndicatorRequest
    ): Result<StockIndicatorCorrelation> = withContext(Dispatchers.IO) {
        val legacyRequest = LegacyRequest(
            ticker = request.ticker,
            name = request.name,
            market = request.market,
            periodDays = request.periodDays
        )
        timeSeriesHelper.analyzeStockIndicatorCorrelations(legacyRequest)
            .map { it.toDomain() }
    }

    override suspend fun runFullStockIndicatorCorrelationAnalysis(
        ticker: String,
        name: String,
        market: String,
        periodDays: Int
    ): Result<FullStockIndicatorAnalysis> = withContext(Dispatchers.IO) {
        timeSeriesHelper.runFullStockIndicatorCorrelationAnalysis(ticker, name, market, periodDays)
            .map { legacyResult ->
                // Pass through core types directly for UI compatibility
                FullStockIndicatorAnalysis(
                    correlationResult = legacyResult.correlationResult,
                    aiInterpretation = legacyResult.aiInterpretation,
                    errorMessage = legacyResult.errorMessage
                )
            }
    }

    override suspend fun interpretStockIndicatorCorrelationsWithAI(
        correlationResult: StockIndicatorCorrelation
    ): Result<StockIndicatorInterpretation> = withContext(Dispatchers.IO) {
        val legacyResult = correlationResult.toLegacy()
        timeSeriesHelper.interpretStockIndicatorCorrelationsWithAI(legacyResult)
            .map { it.toDomain() }
    }

    override fun getStockIndicatorAIHistory(ticker: String): Flow<List<StockIndicatorAIHistoryItem>> {
        return stockIndicatorAIResultDao.getAllByTicker(ticker)
            .map { list -> list.map { it.toHistoryItem() } }
            .flowOn(Dispatchers.IO)
    }

    override fun getAllStockIndicatorAIHistory(limit: Int): Flow<List<StockIndicatorAIHistoryItem>> {
        return stockIndicatorAIResultDao.getRecent(limit)
            .map { list -> list.map { it.toHistoryItem() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun deleteStockIndicatorAIHistory(id: String) = withContext(Dispatchers.IO) {
        stockIndicatorAIResultDao.deleteById(id)
    }

    // Domain -> Legacy 변환 헬퍼
    private fun StockIndicatorCorrelation.toLegacy(): LegacyCorrelationResult {
        // 카테고리별로 상관관계 분류
        val fearGreedCorrelations = mutableListOf<LegacyIndicatorCorrelation>()
        val oscillatorCorrelations = mutableListOf<LegacyIndicatorCorrelation>()
        val depositCorrelations = mutableListOf<LegacyIndicatorCorrelation>()
        val etfCorrelations = mutableListOf<LegacyIndicatorCorrelation>()

        indicatorCorrelations.forEach { corr ->
            val legacyCorr = LegacyIndicatorCorrelation(
                indicatorType = corr.indicatorName,
                stockMetricType = "CLOSE_PRICE",
                correlation = corr.correlationValue,
                significance = 0.05,
                dataPoints = period,
                leadLagDays = 0,
                description = corr.description
            )
            when {
                corr.indicatorName.contains("FEAR_GREED", ignoreCase = true) ||
                corr.indicatorName.contains("RSI", ignoreCase = true) ||
                corr.indicatorName.contains("MOMENTUM", ignoreCase = true) ->
                    fearGreedCorrelations.add(legacyCorr)
                corr.indicatorName.contains("OSCILLATOR", ignoreCase = true) ->
                    oscillatorCorrelations.add(legacyCorr)
                corr.indicatorName.contains("DEPOSIT", ignoreCase = true) ||
                corr.indicatorName.contains("CREDIT", ignoreCase = true) ->
                    depositCorrelations.add(legacyCorr)
                else -> etfCorrelations.add(legacyCorr)
            }
        }

        val allCorrelations = indicatorCorrelations.map {
            LegacyIndicatorCorrelation(
                indicatorType = it.indicatorName,
                stockMetricType = "CLOSE_PRICE",
                correlation = it.correlationValue,
                significance = 0.05,
                dataPoints = period,
                leadLagDays = 0,
                description = it.description
            )
        }
        val topPositive = allCorrelations.filter { it.correlation > 0 }.sortedByDescending { it.correlation }.take(5)
        val topNegative = allCorrelations.filter { it.correlation < 0 }.sortedBy { it.correlation }.take(5)

        return LegacyCorrelationResult(
            ticker = ticker,
            stockName = name,
            market = market,
            startDate = "",
            endDate = "",
            totalDataPoints = period,
            fearGreedCorrelations = fearGreedCorrelations,
            oscillatorCorrelations = oscillatorCorrelations,
            depositCorrelations = depositCorrelations,
            etfCorrelations = etfCorrelations,
            topPositiveCorrelations = topPositive,
            topNegativeCorrelations = topNegative,
            summary = "Signal: $signal, Confidence: $confidence, Score: $compositeScore"
        )
    }

    // Legacy -> Domain 변환 헬퍼
    private fun LegacyCorrelationResult.toDomain(): StockIndicatorCorrelation {
        val allCorrelations = mutableListOf<IndicatorCorrelation>()

        fearGreedCorrelations.forEach { allCorrelations.add(it.toDomain()) }
        oscillatorCorrelations.forEach { allCorrelations.add(it.toDomain()) }
        depositCorrelations.forEach { allCorrelations.add(it.toDomain()) }
        etfCorrelations.forEach { allCorrelations.add(it.toDomain()) }

        // compositeScore, signal, confidence를 summary에서 추출하거나 계산
        val avgCorrelation = allCorrelations.map { kotlin.math.abs(it.correlationValue) }.average()
        val score = avgCorrelation.takeIf { !it.isNaN() } ?: 0.0
        val signal = when {
            topPositiveCorrelations.size > topNegativeCorrelations.size * 2 -> "BUY"
            topNegativeCorrelations.size > topPositiveCorrelations.size * 2 -> "SELL"
            else -> "NEUTRAL"
        }

        return StockIndicatorCorrelation(
            ticker = ticker,
            name = stockName,
            market = market,
            period = totalDataPoints,
            indicatorCorrelations = allCorrelations,
            compositeScore = score,
            signal = signal,
            confidence = score * 100
        )
    }

    private fun LegacyIndicatorCorrelation.toDomain(): IndicatorCorrelation = IndicatorCorrelation(
        indicatorName = indicatorType,
        correlationValue = correlation,
        strength = CorrelationStrength.fromValue(correlation),
        description = description
    )

    private fun LegacyInterpretation.toDomain(): StockIndicatorInterpretation = StockIndicatorInterpretation(
        ticker = ticker,
        name = name,
        period = period.toIntOrNull() ?: 30,
        signal = signal,
        confidence = confidence,
        upProbability = upProbability,
        downProbability = downProbability,
        riskLevel = riskLevel,
        keyCorrelations = keyCorrelations,
        marketSentimentImpact = marketSentimentImpact,
        fundFlowImpact = fundFlowImpact,
        etfFlowImpact = etfFlowImpact,
        recommendation = recommendation,
        reasoning = reasoning
    )
}
