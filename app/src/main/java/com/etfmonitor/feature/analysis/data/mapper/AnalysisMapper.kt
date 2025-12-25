package com.etfmonitor.feature.analysis.data.mapper

import com.etfmonitor.core.database.entities.AIAnalysisResult as AIAnalysisEntity
import com.etfmonitor.core.database.entities.AIChatMessage as ChatMessageEntity
import com.etfmonitor.core.database.entities.AIChatSession as ChatSessionEntity
import com.etfmonitor.core.database.entities.CorrelationAnalysisResult as CorrelationEntity
import com.etfmonitor.core.database.entities.LiquidityAnalysis as LiquidityEntity
import com.etfmonitor.core.database.entities.SectorAnalysis as SectorEntity
import com.etfmonitor.core.database.entities.EtfCorrelationCache as EtfCorrelationEntity
import com.etfmonitor.core.database.entities.StockIndicatorAIResult as StockIndicatorEntity
import com.etfmonitor.core.database.entities.TrendDirection as EntityTrendDirection
import com.etfmonitor.core.database.entities.SectorSentiment as EntitySectorSentiment
import com.etfmonitor.core.database.entities.MarketCapWeightedFlow as EntityMarketCapFlow
import com.etfmonitor.core.database.entities.StockFlow as EntityStockFlow
import com.etfmonitor.core.database.entities.MarketCapSize as EntityMarketCapSize
import com.etfmonitor.core.database.entities.MarketDivergenceSummary as EntityDivergence
import com.etfmonitor.core.database.entities.SupplyDemandDivergence as EntitySupplyDemand
import com.etfmonitor.core.database.entities.DivergenceType as EntityDivergenceType
import com.etfmonitor.core.database.entities.MarketSentimentType as EntityMarketSentiment
import com.etfmonitor.core.database.entities.LiquidityTrend as EntityLiquidityTrend
import com.etfmonitor.core.database.entities.PortfolioDiversification as EntityPortfolio
import com.etfmonitor.core.database.entities.EtfCorrelation as EntityEtfCorr
import com.etfmonitor.core.database.entities.DiversificationSuggestion as EntitySuggestion
import com.etfmonitor.core.database.entities.SuggestionType as EntitySuggestionType
import com.etfmonitor.core.database.entities.SectorRotationSignal as EntitySectorRotation
import com.etfmonitor.core.database.entities.CommonStock as EntityCommonStock
import com.etfmonitor.feature.analysis.domain.model.*
import com.etfmonitor.feature.analysis.domain.repository.StockIndicatorAIHistoryItem
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

// ==================== AI Analysis Mapping ====================

fun AIAnalysisEntity.toDomain(): AIAnalysis = AIAnalysis(
    id = id,
    market = market,
    analysisDate = analysisDate,
    correlationResultId = correlationResultId,
    aiProvider = aiProvider,
    aiModel = aiModel,
    signal = signal,
    confidence = confidence,
    upProbability = upProbability,
    downProbability = downProbability,
    riskLevel = riskLevel,
    reasoning = reasoning,
    keyFactors = try {
        json.decodeFromString<List<String>>(keyFactors)
    } catch (e: Exception) {
        emptyList()
    },
    recommendation = recommendation,
    alternativeScenarios = alternativeScenarios,
    processingTimeMs = processingTimeMs
)

// ==================== Correlation Analysis Mapping ====================

fun CorrelationEntity.toDomain(): CorrelationAnalysis = CorrelationAnalysis(
    id = id,
    market = market,
    analysisDate = analysisDate,
    periodDays = periodDays,
    etfNetFlowCorrelation = etfNetFlowCorrelation,
    etfNewStockCorrelation = etfNewStockCorrelation,
    etfRemovedStockCorrelation = etfRemovedStockCorrelation,
    etfIncreasedCorrelation = etfIncreasedCorrelation,
    etfDecreasedCorrelation = etfDecreasedCorrelation,
    cashDepositCorrelation = cashDepositCorrelation,
    marketDepositCorrelation = marketDepositCorrelation,
    creditBalanceCorrelation = creditBalanceCorrelation,
    fearGreedCorrelation = fearGreedCorrelation,
    fearGreedLeadCorrelation = fearGreedLeadCorrelation,
    oscillatorCorrelation = oscillatorCorrelation,
    oscillatorLeadCorrelation = oscillatorLeadCorrelation,
    compositeScore = compositeScore,
    signal = signal,
    confidence = confidence,
    upProbability = upProbability,
    downProbability = downProbability,
    analysisContext = analysisContext
)

// ==================== Chat Mapping ====================

fun ChatSessionEntity.toDomain(): ChatSession = ChatSession(
    id = id,
    title = title,
    market = market,
    analysisDate = analysisDate,
    contextData = contextData,
    messageCount = messageCount,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ChatMessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = id,
    sessionId = sessionId,
    role = when (role) {
        "user" -> MessageRole.USER
        "assistant" -> MessageRole.ASSISTANT
        else -> MessageRole.SYSTEM
    },
    content = content,
    aiProvider = aiProvider,
    aiModel = aiModel,
    tokenCount = tokenCount,
    timestamp = timestamp
)

// ==================== Liquidity Analysis Mapping ====================

fun LiquidityEntity.toDomain(): LiquidityAnalysisData = LiquidityAnalysisData(
    date = date,
    depositAmount = depositAmount,
    creditAmount = creditAmount,
    totalMarketCap = totalMarketCap,
    kospiMarketCap = kospiMarketCap,
    kosdaqMarketCap = kosdaqMarketCap,
    depositToMarketCapRatio = depositToMarketCapRatio,
    creditToDepositRatio = creditToDepositRatio,
    depositChange = depositChange,
    creditChange = creditChange,
    riskLevel = try {
        LeverageRisk.valueOf(riskLevel)
    } catch (e: Exception) {
        LeverageRisk.MEDIUM
    },
    signal = try {
        LiquiditySignalType.valueOf(signal)
    } catch (e: Exception) {
        LiquiditySignalType.NEUTRAL
    },
    historicalPercentile = historicalPercentile
)

// ==================== Sector Analysis Mapping ====================

fun SectorEntity.toDomain(): SectorAnalysisData = SectorAnalysisData(
    id = id,
    sector = sector,
    sectorName = sectorName,
    date = date,
    fearGreedValue = fearGreedValue,
    etfFlowScore = etfFlowScore,
    momentumScore = momentumScore,
    volatilityScore = volatilityScore,
    stockCount = stockCount,
    newEntries = newEntries,
    removals = removals,
    avgWeightChange = avgWeightChange,
    sentiment = mapSectorSentiment(sentiment)
)

private fun mapSectorSentiment(sentiment: String): SectorSentimentType {
    return try {
        // Try to map entity SectorSentiment enum values to domain SectorSentimentType
        when (sentiment.uppercase()) {
            "VERY_GREEDY", "GREEDY", "GREED" -> SectorSentimentType.GREED
            "VERY_FEARFUL", "FEARFUL", "FEAR" -> SectorSentimentType.FEAR
            else -> SectorSentimentType.NEUTRAL
        }
    } catch (e: Exception) {
        SectorSentimentType.NEUTRAL
    }
}

// ==================== ETF Correlation Mapping ====================

fun EtfCorrelationEntity.toDomain(): EtfCorrelation = EtfCorrelation(
    id = id,
    etf1Ticker = etf1Ticker,
    etf1Name = etf1Name,
    etf2Ticker = etf2Ticker,
    etf2Name = etf2Name,
    date = date,
    overlapRatio = overlapRatio,
    weightCorrelation = weightCorrelation,
    commonStockCount = commonStockCount,
    etf1StockCount = etf1StockCount,
    etf2StockCount = etf2StockCount,
    topCommonStocks = try {
        json.decodeFromString<List<CommonStock>>(topCommonStocks)
    } catch (e: Exception) {
        emptyList()
    }
)

// ==================== TrendDirection Mapping ====================

fun EntityTrendDirection.toDomain(): TrendDirection = when (this) {
    EntityTrendDirection.STRONG_UP, EntityTrendDirection.UP -> TrendDirection.UP
    EntityTrendDirection.STRONG_DOWN, EntityTrendDirection.DOWN -> TrendDirection.DOWN
    EntityTrendDirection.STABLE -> TrendDirection.FLAT
}

// ==================== Stock Indicator Mapping ====================

fun StockIndicatorEntity.toHistoryItem(): StockIndicatorAIHistoryItem = StockIndicatorAIHistoryItem(
    id = id,
    ticker = ticker,
    stockName = stockName,
    market = market,
    analysisDate = analysisDate,
    period = periodDays,
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
    reasoning = reasoning,
    createdAt = createdAt
)

// ==================== Legacy Repository Type Mappings ====================
// These map types from com.etfmonitor.database.entities to domain models

fun EntityMarketCapFlow.toDomain(): MarketCapFlow = MarketCapFlow(
    date = date,
    market = market,
    totalInflow = totalInflow,
    totalOutflow = totalOutflow,
    netFlow = netFlow,
    topInflowStocks = topInflowStocks.map { it.toDomain() },
    topOutflowStocks = topOutflowStocks.map { it.toDomain() },
    inflowBySize = inflowBySize.mapKeys { it.key.toDomain() },
    outflowBySize = outflowBySize.mapKeys { it.key.toDomain() },
    flowVsMarketChange = flowVsMarketChange
)

fun EntityStockFlow.toDomain(): StockFlow = StockFlow(
    ticker = ticker,
    name = name,
    market = market,
    marketCap = marketCap,
    weightChange = weightChange,
    flowAmount = flowAmount,
    etfCount = etfCount,
    status = status
)

fun EntityMarketCapSize.toDomain(): MarketCapSize = when (this) {
    EntityMarketCapSize.LARGE -> MarketCapSize.LARGE
    EntityMarketCapSize.MID -> MarketCapSize.MID
    EntityMarketCapSize.SMALL -> MarketCapSize.SMALL
}

fun EntityDivergence.toDomain(): DivergenceAnalysis = DivergenceAnalysis(
    date = date,
    market = market,
    foreignBullishCount = foreignBullishCount,
    institutionBullishCount = institutionBullishCount,
    alignedBullishCount = alignedBullishCount,
    alignedBearishCount = alignedBearishCount,
    neutralCount = neutralCount,
    topForeignBullish = topForeignBullish.map { it.toDomain() },
    topInstitutionBullish = topInstitutionBullish.map { it.toDomain() },
    marketSentiment = marketSentiment.toDomain(),
    sentimentStrength = sentimentStrength
)

fun EntitySupplyDemand.toDomain(): SupplyDemandItem = SupplyDemandItem(
    ticker = ticker,
    name = name,
    market = market,
    date = date,
    foreign5d = foreign5d,
    institution5d = institution5d,
    marketCap = marketCap,
    divergenceScore = divergenceScore,
    divergenceType = divergenceType.toDomain(),
    etfWeightChange = etfWeightChange,
    etfStatus = etfStatus
)

fun EntityDivergenceType.toDomain(): DivergenceType = when (this) {
    EntityDivergenceType.FOREIGN_BULLISH -> DivergenceType.FOREIGN_BULLISH
    EntityDivergenceType.INSTITUTION_BULLISH -> DivergenceType.INSTITUTION_BULLISH
    EntityDivergenceType.ALIGNED_BULLISH -> DivergenceType.ALIGNED_BULLISH
    EntityDivergenceType.ALIGNED_BEARISH -> DivergenceType.ALIGNED_BEARISH
    EntityDivergenceType.NEUTRAL -> DivergenceType.NEUTRAL
}

fun EntityMarketSentiment.toDomain(): MarketSentiment = when (this) {
    EntityMarketSentiment.CONSENSUS_BULLISH -> MarketSentiment.CONSENSUS_BULLISH
    EntityMarketSentiment.STRONG_FOREIGN_LED -> MarketSentiment.STRONG_FOREIGN_LED
    EntityMarketSentiment.STRONG_INSTITUTION_LED -> MarketSentiment.STRONG_INSTITUTION_LED
    EntityMarketSentiment.CONSENSUS_BEARISH -> MarketSentiment.CONSENSUS_BEARISH
    EntityMarketSentiment.MIXED -> MarketSentiment.MIXED
}

fun EntityLiquidityTrend.toDomain(): LiquidityTrendData = LiquidityTrendData(
    history = history.map { it.toDomain() },
    avgDepositRatio = avgDepositRatio,
    avgCreditRatio = avgCreditRatio,
    currentVsAvgDeposit = currentVsAvgDeposit,
    depositTrend = depositTrend.toDomain(),
    creditTrend = creditTrend.toDomain(),
    trendStrength = trendStrength
)

fun EntitySectorRotation.toDomain(): SectorRotation = SectorRotation(
    fromSector = fromSector,
    toSector = toSector,
    confidence = confidence,
    flowDifference = flowDifference,
    description = description
)

fun EntityPortfolio.toDomain(): PortfolioDiversificationResult = PortfolioDiversificationResult(
    selectedEtfs = selectedEtfs,
    overallDiversificationScore = overallDiversificationScore,
    pairwiseCorrelations = pairwiseCorrelations.map { it.toDomain() },
    avgCorrelation = avgCorrelation,
    suggestions = suggestions.map { it.toDomain() }
)

fun EntityEtfCorr.toDomain(): EtfPairCorrelation = EtfPairCorrelation(
    etf1Ticker = etf1Ticker,
    etf1Name = etf1Name,
    etf2Ticker = etf2Ticker,
    etf2Name = etf2Name,
    overlapRatio = overlapRatio,
    weightCorrelation = weightCorrelation,
    commonStockCount = commonStockCount,
    topCommonStocks = topCommonStocks.map { stock ->
        CommonStock(
            ticker = stock.ticker,
            name = stock.name,
            etf1Weight = stock.etf1Weight,
            etf2Weight = stock.etf2Weight,
            avgWeight = stock.avgWeight
        )
    }
)

fun EntitySuggestion.toDomain(): DiversificationAdvice = DiversificationAdvice(
    type = type.toDomain(),
    message = message,
    affectedEtfs = affectedEtfs,
    impact = impact
)

fun EntitySuggestionType.toDomain(): AdviceType = when (this) {
    EntitySuggestionType.HIGH_OVERLAP_WARNING -> AdviceType.HIGH_OVERLAP_WARNING
    EntitySuggestionType.ADD_FOR_DIVERSIFICATION -> AdviceType.SUGGESTION
    EntitySuggestionType.REMOVE_REDUNDANT -> AdviceType.LOW_DIVERSIFICATION
}
