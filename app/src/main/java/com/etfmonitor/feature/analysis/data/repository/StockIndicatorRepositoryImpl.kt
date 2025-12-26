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
                FullStockIndicatorAnalysis(
                    correlationResult = legacyResult.correlationResult?.toDomain(),
                    aiInterpretation = legacyResult.aiInterpretation?.toDomain(),
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
        return LegacyCorrelationResult(
            ticker = ticker,
            stockName = stockName,
            market = market,
            startDate = startDate,
            endDate = endDate,
            totalDataPoints = totalDataPoints,
            fearGreedCorrelations = fearGreedCorrelations.map { it.toLegacy() },
            oscillatorCorrelations = oscillatorCorrelations.map { it.toLegacy() },
            depositCorrelations = depositCorrelations.map { it.toLegacy() },
            etfCorrelations = etfCorrelations.map { it.toLegacy() },
            topPositiveCorrelations = topPositiveCorrelations.map { it.toLegacy() },
            topNegativeCorrelations = topNegativeCorrelations.map { it.toLegacy() },
            summary = summary
        )
    }

    private fun DetailedIndicatorCorrelation.toLegacy(): LegacyIndicatorCorrelation = LegacyIndicatorCorrelation(
        indicatorType = indicatorType,
        stockMetricType = stockMetricType,
        correlation = correlation,
        significance = significance,
        dataPoints = dataPoints,
        leadLagDays = leadLagDays,
        description = description
    )

    // Legacy -> Domain 변환 헬퍼
    private fun LegacyCorrelationResult.toDomain(): StockIndicatorCorrelation {
        return StockIndicatorCorrelation(
            ticker = ticker,
            stockName = stockName,
            market = market,
            startDate = startDate,
            endDate = endDate,
            totalDataPoints = totalDataPoints,
            fearGreedCorrelations = fearGreedCorrelations.map { it.toDetailedDomain() },
            oscillatorCorrelations = oscillatorCorrelations.map { it.toDetailedDomain() },
            depositCorrelations = depositCorrelations.map { it.toDetailedDomain() },
            etfCorrelations = etfCorrelations.map { it.toDetailedDomain() },
            topPositiveCorrelations = topPositiveCorrelations.map { it.toDetailedDomain() },
            topNegativeCorrelations = topNegativeCorrelations.map { it.toDetailedDomain() },
            summary = summary
        )
    }

    private fun LegacyIndicatorCorrelation.toDetailedDomain(): DetailedIndicatorCorrelation = DetailedIndicatorCorrelation(
        indicatorType = indicatorType,
        stockMetricType = stockMetricType,
        correlation = correlation,
        significance = significance,
        dataPoints = dataPoints,
        leadLagDays = leadLagDays,
        description = description
    )

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
