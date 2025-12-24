package com.etfmonitor.feature.analysis.data.mapper

import com.etfmonitor.database.entities.AIAnalysisResult as AIAnalysisEntity
import com.etfmonitor.database.entities.AIChatMessage as ChatMessageEntity
import com.etfmonitor.database.entities.AIChatSession as ChatSessionEntity
import com.etfmonitor.database.entities.CorrelationAnalysisResult as CorrelationEntity
import com.etfmonitor.database.entities.LiquidityAnalysis as LiquidityEntity
import com.etfmonitor.database.entities.SectorAnalysis as SectorEntity
import com.etfmonitor.database.entities.EtfCorrelationCache as EtfCorrelationEntity
import com.etfmonitor.database.entities.StockIndicatorAIResult as StockIndicatorEntity
import com.etfmonitor.feature.analysis.domain.model.*
import com.etfmonitor.feature.analysis.domain.repository.StockIndicatorAIHistoryItem
import com.etfmonitor.repository.MarketCapWeightedFlow as RepoMarketCapFlow
import com.etfmonitor.repository.StockFlow as RepoStockFlow
import com.etfmonitor.repository.MarketDivergenceSummary as RepoDivergence
import com.etfmonitor.repository.SupplyDemandDivergence as RepoSupplyDemand
import com.etfmonitor.repository.SectorRotationSignal as RepoSectorRotation
import com.etfmonitor.repository.LiquidityTrend as RepoLiquidityTrend
import com.etfmonitor.repository.PortfolioDiversification as RepoPortfolio
import com.etfmonitor.repository.EtfCorrelation as RepoEtfCorrelation
import com.etfmonitor.repository.DiversificationSuggestion as RepoSuggestion
import com.etfmonitor.repository.MarketCapSize as RepoMarketCapSize
import com.etfmonitor.repository.DivergenceType as RepoDivergenceType
import com.etfmonitor.repository.MarketSentimentType as RepoMarketSentiment
import com.etfmonitor.repository.TrendDirection as RepoTrendDirection
import com.etfmonitor.repository.SuggestionType as RepoSuggestionType
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
    sentiment = try {
        SectorSentimentType.valueOf(sentiment)
    } catch (e: Exception) {
        SectorSentimentType.NEUTRAL
    }
)

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

// ==================== Repository Model Mapping ====================

fun RepoMarketCapFlow.toDomain(): MarketCapFlow = MarketCapFlow(
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

fun RepoStockFlow.toDomain(): StockFlow = StockFlow(
    ticker = ticker,
    name = name,
    market = market,
    marketCap = marketCap,
    weightChange = weightChange,
    flowAmount = flowAmount,
    etfCount = etfCount,
    status = status
)

fun RepoMarketCapSize.toDomain(): MarketCapSize = when (this) {
    RepoMarketCapSize.LARGE -> MarketCapSize.LARGE
    RepoMarketCapSize.MID -> MarketCapSize.MID
    RepoMarketCapSize.SMALL -> MarketCapSize.SMALL
}

fun RepoDivergence.toDomain(): DivergenceAnalysis = DivergenceAnalysis(
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

fun RepoSupplyDemand.toDomain(): SupplyDemandItem = SupplyDemandItem(
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

fun RepoDivergenceType.toDomain(): DivergenceType = when (this) {
    RepoDivergenceType.FOREIGN_BULLISH -> DivergenceType.FOREIGN_BULLISH
    RepoDivergenceType.INSTITUTION_BULLISH -> DivergenceType.INSTITUTION_BULLISH
    RepoDivergenceType.ALIGNED_BULLISH -> DivergenceType.ALIGNED_BULLISH
    RepoDivergenceType.ALIGNED_BEARISH -> DivergenceType.ALIGNED_BEARISH
    RepoDivergenceType.NEUTRAL -> DivergenceType.NEUTRAL
}

fun RepoMarketSentiment.toDomain(): MarketSentiment = when (this) {
    RepoMarketSentiment.CONSENSUS_BULLISH -> MarketSentiment.CONSENSUS_BULLISH
    RepoMarketSentiment.STRONG_FOREIGN_LED -> MarketSentiment.STRONG_FOREIGN_LED
    RepoMarketSentiment.STRONG_INSTITUTION_LED -> MarketSentiment.STRONG_INSTITUTION_LED
    RepoMarketSentiment.MIXED -> MarketSentiment.MIXED
    RepoMarketSentiment.CONSENSUS_BEARISH -> MarketSentiment.CONSENSUS_BEARISH
}

fun RepoSectorRotation.toDomain(): SectorRotation = SectorRotation(
    fromSector = fromSector,
    toSector = toSector,
    confidence = confidence,
    flowDifference = flowDifference,
    description = description
)

fun RepoLiquidityTrend.toDomain(): LiquidityTrendData = LiquidityTrendData(
    history = history.map { it.toDomain() },
    avgDepositRatio = avgDepositRatio,
    avgCreditRatio = avgCreditRatio,
    currentVsAvgDeposit = currentVsAvgDeposit,
    depositTrend = depositTrend.toDomain(),
    creditTrend = creditTrend.toDomain(),
    trendStrength = trendStrength
)

fun RepoTrendDirection.toDomain(): TrendDirection = when (this) {
    RepoTrendDirection.UP -> TrendDirection.UP
    RepoTrendDirection.DOWN -> TrendDirection.DOWN
    RepoTrendDirection.FLAT -> TrendDirection.FLAT
}

fun RepoPortfolio.toDomain(): PortfolioDiversificationResult = PortfolioDiversificationResult(
    selectedEtfs = selectedEtfs,
    overallDiversificationScore = overallDiversificationScore,
    pairwiseCorrelations = pairwiseCorrelations.map { it.toDomain() },
    avgCorrelation = avgCorrelation,
    suggestions = suggestions.map { it.toDomain() }
)

fun RepoEtfCorrelation.toDomain(): EtfPairCorrelation = EtfPairCorrelation(
    etf1Ticker = etf1Ticker,
    etf1Name = etf1Name,
    etf2Ticker = etf2Ticker,
    etf2Name = etf2Name,
    overlapRatio = overlapRatio,
    weightCorrelation = weightCorrelation,
    commonStockCount = commonStockCount,
    topCommonStocks = topCommonStocks.map {
        CommonStock(
            ticker = it.ticker,
            name = it.name,
            etf1Weight = it.etf1Weight,
            etf2Weight = it.etf2Weight,
            avgWeight = it.avgWeight
        )
    }
)

fun RepoSuggestion.toDomain(): DiversificationAdvice = DiversificationAdvice(
    type = type.toDomain(),
    message = message,
    affectedEtfs = affectedEtfs,
    impact = impact
)

fun RepoSuggestionType.toDomain(): AdviceType = when (this) {
    RepoSuggestionType.HIGH_OVERLAP_WARNING -> AdviceType.HIGH_OVERLAP_WARNING
    RepoSuggestionType.LOW_DIVERSIFICATION -> AdviceType.LOW_DIVERSIFICATION
    RepoSuggestionType.SECTOR_CONCENTRATION -> AdviceType.SECTOR_CONCENTRATION
    RepoSuggestionType.SUGGESTION -> AdviceType.SUGGESTION
}

// ==================== Stock Indicator Mapping ====================

fun StockIndicatorEntity.toHistoryItem(): StockIndicatorAIHistoryItem = StockIndicatorAIHistoryItem(
    id = id,
    ticker = ticker,
    stockName = stockName,
    market = market,
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
    reasoning = reasoning,
    createdAt = createdAt
)
