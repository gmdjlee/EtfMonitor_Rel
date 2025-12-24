package com.etfmonitor.feature.analysis.data.repository

import com.etfmonitor.database.StockIndicatorAIResultDao
import com.etfmonitor.feature.analysis.data.mapper.toHistoryItem
import com.etfmonitor.feature.analysis.domain.model.*
import com.etfmonitor.feature.analysis.domain.repository.StockIndicatorAIHistoryItem
import com.etfmonitor.feature.analysis.domain.repository.StockIndicatorRepository
import com.etfmonitor.repository.TimeSeriesAnalysisRepository as LegacyTimeSeriesRepo
import com.etfmonitor.analysis.StockIndicatorCorrelationRequest as LegacyRequest
import com.etfmonitor.analysis.StockIndicatorCorrelationResult as LegacyCorrelation
import com.etfmonitor.analysis.FullStockIndicatorCorrelationResult as LegacyFullResult
import com.etfmonitor.analysis.AIStockIndicatorInterpretation as LegacyInterpretation
import com.etfmonitor.analysis.IndicatorStockCorrelation as LegacyIndicatorCorrelation
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
    private val legacyRepository: LegacyTimeSeriesRepo,
    private val stockIndicatorAIResultDao: StockIndicatorAIResultDao
) : StockIndicatorRepository {

    override suspend fun searchStock(query: String): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            legacyRepository.searchStock(query)
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
        legacyRepository.analyzeStockIndicatorCorrelations(legacyRequest)
            .map { it.toDomain() }
    }

    override suspend fun runFullStockIndicatorCorrelationAnalysis(
        ticker: String,
        name: String,
        market: String,
        periodDays: Int
    ): Result<FullStockIndicatorAnalysis> = withContext(Dispatchers.IO) {
        legacyRepository.runFullStockIndicatorCorrelationAnalysis(ticker, name, market, periodDays)
            .map { it.toDomain() }
    }

    override suspend fun interpretStockIndicatorCorrelationsWithAI(
        correlationResult: StockIndicatorCorrelation
    ): Result<StockIndicatorInterpretation> = withContext(Dispatchers.IO) {
        val legacyResult = correlationResult.toLegacy()
        legacyRepository.interpretStockIndicatorCorrelationsWithAI(legacyResult)
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
    private fun StockIndicatorCorrelation.toLegacy(): LegacyCorrelation = LegacyCorrelation(
        ticker = ticker,
        name = name,
        market = market,
        period = period,
        correlations = indicatorCorrelations.map { it.toLegacy() },
        compositeScore = compositeScore,
        signal = signal,
        confidence = confidence
    )

    private fun IndicatorCorrelation.toLegacy(): LegacyIndicatorCorrelation = LegacyIndicatorCorrelation(
        indicatorName = indicatorName,
        correlation = correlationValue,
        pValue = 0.0,  // 기본값
        lagDays = 0,   // 기본값
        strength = strength.name,
        direction = if (correlationValue >= 0) "POSITIVE" else "NEGATIVE"
    )

    // Legacy -> Domain 변환 헬퍼
    private fun LegacyCorrelation.toDomain(): StockIndicatorCorrelation = StockIndicatorCorrelation(
        ticker = ticker,
        name = name,
        market = market,
        period = period,
        indicatorCorrelations = correlations.map { it.toDomain() },
        compositeScore = compositeScore,
        signal = signal,
        confidence = confidence
    )

    private fun LegacyIndicatorCorrelation.toDomain(): IndicatorCorrelation = IndicatorCorrelation(
        indicatorName = indicatorName,
        correlationValue = correlation,
        strength = CorrelationStrength.fromValue(correlation),
        description = "$indicatorName: ${String.format("%.3f", correlation)} ($strength)"
    )

    private fun LegacyFullResult.toDomain(): FullStockIndicatorAnalysis = FullStockIndicatorAnalysis(
        correlationResult = correlationResult?.toDomain(),
        aiInterpretation = aiInterpretation?.toDomain(),
        errorMessage = errorMessage
    )

    private fun LegacyInterpretation.toDomain(): StockIndicatorInterpretation = StockIndicatorInterpretation(
        ticker = ticker,
        name = name,
        period = period,
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
