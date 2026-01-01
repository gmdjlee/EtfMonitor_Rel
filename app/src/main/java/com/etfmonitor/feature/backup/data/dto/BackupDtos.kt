package com.etfmonitor.feature.backup.data.dto

import com.etfmonitor.core.database.entities.*
import com.etfmonitor.feature.backup.domain.model.BackupMetadata
import kotlinx.serialization.Serializable

/**
 * 전체 백업 파일 구조
 */
@Serializable
data class BackupData(
    val metadata: BackupMetadata,
    val data: EntityData
)

/**
 * 엔티티 데이터 컨테이너
 */
@Serializable
data class EntityData(
    val etfs: List<EtfDto>? = null,
    val stocks: List<StockDto>? = null,
    val settings: List<SettingDto>? = null,
    val holdings: List<HoldingDto>? = null,
    val marketDeposits: List<MarketDepositDto>? = null,
    val fearGreedIndices: List<FearGreedIndexDto>? = null,
    val marketOscillators: List<MarketOscillatorDataDto>? = null,
    val marketIndices: List<MarketIndexDto>? = null,
    val dailyEtfStatistics: List<DailyEtfStatisticsDto>? = null,
    val bloodIndicators: List<BloodIndicatorDto>? = null,
    val priceCaches: List<PriceCacheDto>? = null,
    val stockAnalysisData: List<StockAnalysisDataDto>? = null,
    val aiAnalysisResults: List<AIAnalysisResultDto>? = null,
    val aiChatSessions: List<AIChatSessionDto>? = null,
    val aiChatMessages: List<AIChatMessageDto>? = null,
    val correlationResults: List<CorrelationAnalysisResultDto>? = null,
    val sectorAnalyses: List<SectorAnalysisDto>? = null,
    val etfCorrelationCaches: List<EtfCorrelationCacheDto>? = null,
    val liquidityAnalyses: List<LiquidityAnalysisDto>? = null,
    val stockIndicatorAIResults: List<StockIndicatorAIResultDto>? = null,
    val enhancedPredictions: List<EnhancedPredictionDto>? = null,
    val searchHistories: List<SearchHistoryDto>? = null
)

// ==================== Master Data DTOs ====================

@Serializable
data class EtfDto(
    val ticker: String,
    val name: String
) {
    fun toEntity() = Etf(ticker = ticker, name = name)
    companion object {
        fun fromEntity(entity: Etf) = EtfDto(ticker = entity.ticker, name = entity.name)
    }
}

@Serializable
data class StockDto(
    val ticker: String,
    val name: String,
    val market: String,
    val sector: String,
    val isEtfHolding: Boolean,
    val lastUpdated: Long
) {
    fun toEntity() = Stock(
        ticker = ticker,
        name = name,
        market = market,
        sector = sector,
        isEtfHolding = isEtfHolding,
        lastUpdated = lastUpdated
    )
    companion object {
        fun fromEntity(entity: Stock) = StockDto(
            ticker = entity.ticker,
            name = entity.name,
            market = entity.market,
            sector = entity.sector,
            isEtfHolding = entity.isEtfHolding,
            lastUpdated = entity.lastUpdated
        )
    }
}

@Serializable
data class SettingDto(
    val key: String,
    val value: String
) {
    fun toEntity() = Setting(key = key, value = value)
    companion object {
        fun fromEntity(entity: Setting) = SettingDto(key = entity.key, value = entity.value)
    }
}

// ==================== Time-Series DTOs ====================

@Serializable
data class HoldingDto(
    val etfTicker: String,
    val stockTicker: String,
    val stockName: String,
    val date: String,
    val weight: Float,      // Converted from weightBps
    val amount: Float,      // Converted from amountMillion
    val snapshotType: String
) {
    fun toEntity() = Holding.create(
        etfTicker = etfTicker,
        stockTicker = stockTicker,
        stockName = stockName,
        date = date,
        weight = weight,
        amount = amount,
        snapshotType = SnapshotType.fromValue(snapshotType)
    )
    companion object {
        fun fromEntity(entity: Holding) = HoldingDto(
            etfTicker = entity.etfTicker,
            stockTicker = entity.stockTicker,
            stockName = entity.stockName,
            date = entity.date,
            weight = entity.weight,
            amount = entity.amount,
            snapshotType = entity.snapshotType
        )
    }
}

@Serializable
data class MarketDepositDto(
    val date: String,
    val depositAmount: Double,
    val depositChange: Double,
    val creditAmount: Double,
    val creditChange: Double,
    val lastUpdated: Long
) {
    fun toEntity() = MarketDeposit(
        date = date,
        depositAmount = depositAmount,
        depositChange = depositChange,
        creditAmount = creditAmount,
        creditChange = creditChange,
        lastUpdated = lastUpdated
    )
    companion object {
        fun fromEntity(entity: MarketDeposit) = MarketDepositDto(
            date = entity.date,
            depositAmount = entity.depositAmount,
            depositChange = entity.depositChange,
            creditAmount = entity.creditAmount,
            creditChange = entity.creditChange,
            lastUpdated = entity.lastUpdated
        )
    }
}

@Serializable
data class FearGreedIndexDto(
    val id: String,
    val market: String,
    val date: String,
    val indexValue: Double,
    val fearGreedValue: Double,
    val oscillator: Double,
    val rsi: Double,
    val momentum: Double,
    val putCallRatio: Double,
    val volatility: Double,
    val spread: Double,
    val lastUpdated: Long
) {
    fun toEntity() = FearGreedIndex(
        id = id, market = market, date = date, indexValue = indexValue,
        fearGreedValue = fearGreedValue, oscillator = oscillator, rsi = rsi,
        momentum = momentum, putCallRatio = putCallRatio, volatility = volatility,
        spread = spread, lastUpdated = lastUpdated
    )
    companion object {
        fun fromEntity(entity: FearGreedIndex) = FearGreedIndexDto(
            id = entity.id, market = entity.market, date = entity.date,
            indexValue = entity.indexValue, fearGreedValue = entity.fearGreedValue,
            oscillator = entity.oscillator, rsi = entity.rsi, momentum = entity.momentum,
            putCallRatio = entity.putCallRatio, volatility = entity.volatility,
            spread = entity.spread, lastUpdated = entity.lastUpdated
        )
    }
}

@Serializable
data class MarketOscillatorDataDto(
    val id: String,
    val market: String,
    val date: String,
    val indexValue: Double,
    val oscillator: Double,
    val lastUpdated: Long
) {
    fun toEntity() = MarketOscillatorData(
        id = id, market = market, date = date,
        indexValue = indexValue, oscillator = oscillator, lastUpdated = lastUpdated
    )
    companion object {
        fun fromEntity(entity: MarketOscillatorData) = MarketOscillatorDataDto(
            id = entity.id, market = entity.market, date = entity.date,
            indexValue = entity.indexValue, oscillator = entity.oscillator,
            lastUpdated = entity.lastUpdated
        )
    }
}

@Serializable
data class MarketIndexDto(
    val id: String,
    val market: String,
    val date: String,
    val closePrice: Double,
    val openPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val volume: Long,
    val changeRate: Double,
    val lastUpdated: Long
) {
    fun toEntity() = MarketIndex(
        id = id, market = market, date = date,
        closePrice = closePrice, openPrice = openPrice, highPrice = highPrice,
        lowPrice = lowPrice, volume = volume, changeRate = changeRate,
        lastUpdated = lastUpdated
    )
    companion object {
        fun fromEntity(entity: MarketIndex) = MarketIndexDto(
            id = entity.id, market = entity.market, date = entity.date,
            closePrice = entity.closePrice, openPrice = entity.openPrice,
            highPrice = entity.highPrice, lowPrice = entity.lowPrice,
            volume = entity.volume, changeRate = entity.changeRate,
            lastUpdated = entity.lastUpdated
        )
    }
}

@Serializable
data class DailyEtfStatisticsDto(
    val date: String,
    val newStockCount: Int,
    val newStockAmount: Long,
    val removedStockCount: Int,
    val removedStockAmount: Long,
    val increasedStockCount: Int,
    val increasedStockAmount: Long,
    val decreasedStockCount: Int,
    val decreasedStockAmount: Long,
    val cashDepositAmount: Long,
    val cashDepositChange: Long,
    val cashDepositChangeRate: Double,
    val totalEtfCount: Int,
    val totalHoldingAmount: Long,
    val lastUpdated: Long
) {
    fun toEntity() = DailyEtfStatistics(
        date = date, newStockCount = newStockCount, newStockAmount = newStockAmount,
        removedStockCount = removedStockCount, removedStockAmount = removedStockAmount,
        increasedStockCount = increasedStockCount, increasedStockAmount = increasedStockAmount,
        decreasedStockCount = decreasedStockCount, decreasedStockAmount = decreasedStockAmount,
        cashDepositAmount = cashDepositAmount, cashDepositChange = cashDepositChange,
        cashDepositChangeRate = cashDepositChangeRate, totalEtfCount = totalEtfCount,
        totalHoldingAmount = totalHoldingAmount, lastUpdated = lastUpdated
    )
    companion object {
        fun fromEntity(entity: DailyEtfStatistics) = DailyEtfStatisticsDto(
            date = entity.date, newStockCount = entity.newStockCount,
            newStockAmount = entity.newStockAmount, removedStockCount = entity.removedStockCount,
            removedStockAmount = entity.removedStockAmount, increasedStockCount = entity.increasedStockCount,
            increasedStockAmount = entity.increasedStockAmount, decreasedStockCount = entity.decreasedStockCount,
            decreasedStockAmount = entity.decreasedStockAmount, cashDepositAmount = entity.cashDepositAmount,
            cashDepositChange = entity.cashDepositChange, cashDepositChangeRate = entity.cashDepositChangeRate,
            totalEtfCount = entity.totalEtfCount, totalHoldingAmount = entity.totalHoldingAmount,
            lastUpdated = entity.lastUpdated
        )
    }
}

@Serializable
data class BloodIndicatorDto(
    val id: String,
    val date: String,
    val bloodValue: Double,
    val bloodSma: Double,
    val us03my: Double,
    val highYieldSpread: Double,
    val spyClose: Double?,
    val signalType: String,
    val signalColor: String,
    val lastUpdated: Long
) {
    fun toEntity() = BloodIndicator(
        id = id, date = date, bloodValue = bloodValue, bloodSma = bloodSma,
        us03my = us03my, highYieldSpread = highYieldSpread, spyClose = spyClose,
        signalType = signalType, signalColor = signalColor, lastUpdated = lastUpdated
    )
    companion object {
        fun fromEntity(entity: BloodIndicator) = BloodIndicatorDto(
            id = entity.id, date = entity.date, bloodValue = entity.bloodValue,
            bloodSma = entity.bloodSma, us03my = entity.us03my,
            highYieldSpread = entity.highYieldSpread, spyClose = entity.spyClose,
            signalType = entity.signalType, signalColor = entity.signalColor,
            lastUpdated = entity.lastUpdated
        )
    }
}

@Serializable
data class PriceCacheDto(
    val ticker: String,
    val date: String,
    val closePrice: Double,
    val priceChange5d: Double?,
    val priceChange10d: Double?,
    val updatedAt: Long
) {
    fun toEntity() = PriceCache(
        ticker = ticker, date = date, closePrice = closePrice,
        priceChange5d = priceChange5d, priceChange10d = priceChange10d,
        updatedAt = updatedAt
    )
    companion object {
        fun fromEntity(entity: PriceCache) = PriceCacheDto(
            ticker = entity.ticker, date = entity.date, closePrice = entity.closePrice,
            priceChange5d = entity.priceChange5d, priceChange10d = entity.priceChange10d,
            updatedAt = entity.updatedAt
        )
    }
}

@Serializable
data class StockAnalysisDataDto(
    val ticker: String,
    val dates: List<String>,
    val marketCap: List<Long>,
    val foreign5d: List<Long>,
    val institution5d: List<Long>,
    val lastUpdated: Long,
    val dataStartDate: String,
    val dataEndDate: String
) {
    fun toEntity() = StockAnalysisData(
        ticker = ticker, dates = dates, marketCap = marketCap,
        foreign5d = foreign5d, institution5d = institution5d,
        lastUpdated = lastUpdated, dataStartDate = dataStartDate, dataEndDate = dataEndDate
    )
    companion object {
        fun fromEntity(entity: StockAnalysisData) = StockAnalysisDataDto(
            ticker = entity.ticker, dates = entity.dates, marketCap = entity.marketCap,
            foreign5d = entity.foreign5d, institution5d = entity.institution5d,
            lastUpdated = entity.lastUpdated, dataStartDate = entity.dataStartDate,
            dataEndDate = entity.dataEndDate
        )
    }
}

// ==================== Analysis Result DTOs ====================

@Serializable
data class AIAnalysisResultDto(
    val id: String,
    val market: String,
    val analysisDate: String,
    val correlationResultId: String?,
    val aiProvider: String,
    val aiModel: String,
    val signal: String,
    val confidence: Double,
    val upProbability: Double,
    val downProbability: Double,
    val riskLevel: String,
    val reasoning: String,
    val keyFactors: String,
    val recommendation: String,
    val alternativeScenarios: String?,
    val promptUsed: String,
    val rawResponse: String,
    val processingTimeMs: Long,
    val createdAt: Long
) {
    fun toEntity() = AIAnalysisResult(
        id = id, market = market, analysisDate = analysisDate,
        correlationResultId = correlationResultId, aiProvider = aiProvider,
        aiModel = aiModel, signal = signal, confidence = confidence,
        upProbability = upProbability, downProbability = downProbability,
        riskLevel = riskLevel, reasoning = reasoning, keyFactors = keyFactors,
        recommendation = recommendation, alternativeScenarios = alternativeScenarios,
        promptUsed = promptUsed, rawResponse = rawResponse,
        processingTimeMs = processingTimeMs, createdAt = createdAt
    )
    companion object {
        fun fromEntity(entity: AIAnalysisResult) = AIAnalysisResultDto(
            id = entity.id, market = entity.market, analysisDate = entity.analysisDate,
            correlationResultId = entity.correlationResultId, aiProvider = entity.aiProvider,
            aiModel = entity.aiModel, signal = entity.signal, confidence = entity.confidence,
            upProbability = entity.upProbability, downProbability = entity.downProbability,
            riskLevel = entity.riskLevel, reasoning = entity.reasoning,
            keyFactors = entity.keyFactors, recommendation = entity.recommendation,
            alternativeScenarios = entity.alternativeScenarios, promptUsed = entity.promptUsed,
            rawResponse = entity.rawResponse, processingTimeMs = entity.processingTimeMs,
            createdAt = entity.createdAt
        )
    }
}

@Serializable
data class AIChatSessionDto(
    val id: String,
    val title: String,
    val market: String?,
    val analysisDate: String?,
    val contextData: String?,
    val messageCount: Int,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toEntity() = AIChatSession(
        id = id, title = title, market = market, analysisDate = analysisDate,
        contextData = contextData, messageCount = messageCount,
        createdAt = createdAt, updatedAt = updatedAt
    )
    companion object {
        fun fromEntity(entity: AIChatSession) = AIChatSessionDto(
            id = entity.id, title = entity.title, market = entity.market,
            analysisDate = entity.analysisDate, contextData = entity.contextData,
            messageCount = entity.messageCount, createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}

@Serializable
data class AIChatMessageDto(
    val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val analysisResultId: String?,
    val aiProvider: String?,
    val aiModel: String?,
    val tokenCount: Int?,
    val timestamp: Long
) {
    fun toEntity() = AIChatMessage(
        id = id, sessionId = sessionId, role = role, content = content,
        analysisResultId = analysisResultId, aiProvider = aiProvider,
        aiModel = aiModel, tokenCount = tokenCount, timestamp = timestamp
    )
    companion object {
        fun fromEntity(entity: AIChatMessage) = AIChatMessageDto(
            id = entity.id, sessionId = entity.sessionId, role = entity.role,
            content = entity.content, analysisResultId = entity.analysisResultId,
            aiProvider = entity.aiProvider, aiModel = entity.aiModel,
            tokenCount = entity.tokenCount, timestamp = entity.timestamp
        )
    }
}

@Serializable
data class CorrelationAnalysisResultDto(
    val id: String,
    val market: String,
    val analysisDate: String,
    val periodDays: Int,
    val etfNewStockCorrelation: Double,
    val etfRemovedStockCorrelation: Double,
    val etfIncreasedCorrelation: Double,
    val etfDecreasedCorrelation: Double,
    val etfNetFlowCorrelation: Double,
    val cashDepositCorrelation: Double,
    val marketDepositCorrelation: Double?,
    val creditBalanceCorrelation: Double?,
    val fearGreedCorrelation: Double?,
    val fearGreedLeadCorrelation: Double?,
    val oscillatorCorrelation: Double?,
    val oscillatorLeadCorrelation: Double?,
    val compositeScore: Double,
    val signal: String,
    val confidence: Double,
    val upProbability: Double,
    val downProbability: Double,
    val analysisContext: String,
    val createdAt: Long
) {
    fun toEntity() = CorrelationAnalysisResult(
        id = id, market = market, analysisDate = analysisDate, periodDays = periodDays,
        etfNewStockCorrelation = etfNewStockCorrelation,
        etfRemovedStockCorrelation = etfRemovedStockCorrelation,
        etfIncreasedCorrelation = etfIncreasedCorrelation,
        etfDecreasedCorrelation = etfDecreasedCorrelation,
        etfNetFlowCorrelation = etfNetFlowCorrelation,
        cashDepositCorrelation = cashDepositCorrelation,
        marketDepositCorrelation = marketDepositCorrelation,
        creditBalanceCorrelation = creditBalanceCorrelation,
        fearGreedCorrelation = fearGreedCorrelation,
        fearGreedLeadCorrelation = fearGreedLeadCorrelation,
        oscillatorCorrelation = oscillatorCorrelation,
        oscillatorLeadCorrelation = oscillatorLeadCorrelation,
        compositeScore = compositeScore, signal = signal, confidence = confidence,
        upProbability = upProbability, downProbability = downProbability,
        analysisContext = analysisContext, createdAt = createdAt
    )
    companion object {
        fun fromEntity(entity: CorrelationAnalysisResult) = CorrelationAnalysisResultDto(
            id = entity.id, market = entity.market, analysisDate = entity.analysisDate,
            periodDays = entity.periodDays,
            etfNewStockCorrelation = entity.etfNewStockCorrelation,
            etfRemovedStockCorrelation = entity.etfRemovedStockCorrelation,
            etfIncreasedCorrelation = entity.etfIncreasedCorrelation,
            etfDecreasedCorrelation = entity.etfDecreasedCorrelation,
            etfNetFlowCorrelation = entity.etfNetFlowCorrelation,
            cashDepositCorrelation = entity.cashDepositCorrelation,
            marketDepositCorrelation = entity.marketDepositCorrelation,
            creditBalanceCorrelation = entity.creditBalanceCorrelation,
            fearGreedCorrelation = entity.fearGreedCorrelation,
            fearGreedLeadCorrelation = entity.fearGreedLeadCorrelation,
            oscillatorCorrelation = entity.oscillatorCorrelation,
            oscillatorLeadCorrelation = entity.oscillatorLeadCorrelation,
            compositeScore = entity.compositeScore, signal = entity.signal,
            confidence = entity.confidence, upProbability = entity.upProbability,
            downProbability = entity.downProbability,
            analysisContext = entity.analysisContext, createdAt = entity.createdAt
        )
    }
}

@Serializable
data class SectorAnalysisDto(
    val id: String,
    val sector: String,
    val sectorName: String,
    val date: String,
    val fearGreedValue: Double,
    val etfFlowScore: Double,
    val momentumScore: Double,
    val volatilityScore: Double,
    val stockCount: Int,
    val newEntries: Int,
    val removals: Int,
    val avgWeightChange: Double,
    val sentiment: String,
    val lastUpdated: Long
) {
    fun toEntity() = SectorAnalysis(
        id = id, sector = sector, sectorName = sectorName, date = date,
        fearGreedValue = fearGreedValue, etfFlowScore = etfFlowScore,
        momentumScore = momentumScore, volatilityScore = volatilityScore,
        stockCount = stockCount, newEntries = newEntries, removals = removals,
        avgWeightChange = avgWeightChange, sentiment = sentiment, lastUpdated = lastUpdated
    )
    companion object {
        fun fromEntity(entity: SectorAnalysis) = SectorAnalysisDto(
            id = entity.id, sector = entity.sector, sectorName = entity.sectorName,
            date = entity.date, fearGreedValue = entity.fearGreedValue,
            etfFlowScore = entity.etfFlowScore, momentumScore = entity.momentumScore,
            volatilityScore = entity.volatilityScore, stockCount = entity.stockCount,
            newEntries = entity.newEntries, removals = entity.removals,
            avgWeightChange = entity.avgWeightChange, sentiment = entity.sentiment,
            lastUpdated = entity.lastUpdated
        )
    }
}

@Serializable
data class EtfCorrelationCacheDto(
    val id: String,
    val etf1Ticker: String,
    val etf1Name: String,
    val etf2Ticker: String,
    val etf2Name: String,
    val date: String,
    val overlapRatio: Double,
    val weightCorrelation: Double,
    val commonStockCount: Int,
    val etf1StockCount: Int,
    val etf2StockCount: Int,
    val topCommonStocks: String,
    val lastUpdated: Long
) {
    fun toEntity() = EtfCorrelationCache(
        id = id, etf1Ticker = etf1Ticker, etf1Name = etf1Name,
        etf2Ticker = etf2Ticker, etf2Name = etf2Name, date = date,
        overlapRatio = overlapRatio, weightCorrelation = weightCorrelation,
        commonStockCount = commonStockCount, etf1StockCount = etf1StockCount,
        etf2StockCount = etf2StockCount, topCommonStocks = topCommonStocks,
        lastUpdated = lastUpdated
    )
    companion object {
        fun fromEntity(entity: EtfCorrelationCache) = EtfCorrelationCacheDto(
            id = entity.id, etf1Ticker = entity.etf1Ticker, etf1Name = entity.etf1Name,
            etf2Ticker = entity.etf2Ticker, etf2Name = entity.etf2Name, date = entity.date,
            overlapRatio = entity.overlapRatio, weightCorrelation = entity.weightCorrelation,
            commonStockCount = entity.commonStockCount, etf1StockCount = entity.etf1StockCount,
            etf2StockCount = entity.etf2StockCount, topCommonStocks = entity.topCommonStocks,
            lastUpdated = entity.lastUpdated
        )
    }
}

@Serializable
data class LiquidityAnalysisDto(
    val date: String,
    val depositAmount: Double,
    val creditAmount: Double,
    val totalMarketCap: Long,
    val kospiMarketCap: Long,
    val kosdaqMarketCap: Long,
    val depositToMarketCapRatio: Double,
    val creditToDepositRatio: Double,
    val depositChange: Double,
    val creditChange: Double,
    val riskLevel: String,
    val signal: String,
    val historicalPercentile: Double,
    val lastUpdated: Long
) {
    fun toEntity() = LiquidityAnalysis(
        date = date, depositAmount = depositAmount, creditAmount = creditAmount,
        totalMarketCap = totalMarketCap, kospiMarketCap = kospiMarketCap,
        kosdaqMarketCap = kosdaqMarketCap, depositToMarketCapRatio = depositToMarketCapRatio,
        creditToDepositRatio = creditToDepositRatio, depositChange = depositChange,
        creditChange = creditChange, riskLevel = riskLevel, signal = signal,
        historicalPercentile = historicalPercentile, lastUpdated = lastUpdated
    )
    companion object {
        fun fromEntity(entity: LiquidityAnalysis) = LiquidityAnalysisDto(
            date = entity.date, depositAmount = entity.depositAmount,
            creditAmount = entity.creditAmount, totalMarketCap = entity.totalMarketCap,
            kospiMarketCap = entity.kospiMarketCap, kosdaqMarketCap = entity.kosdaqMarketCap,
            depositToMarketCapRatio = entity.depositToMarketCapRatio,
            creditToDepositRatio = entity.creditToDepositRatio,
            depositChange = entity.depositChange, creditChange = entity.creditChange,
            riskLevel = entity.riskLevel, signal = entity.signal,
            historicalPercentile = entity.historicalPercentile,
            lastUpdated = entity.lastUpdated
        )
    }
}

@Serializable
data class StockIndicatorAIResultDto(
    val id: String,
    val ticker: String,
    val stockName: String,
    val market: String,
    val analysisDate: String,
    val period: String,
    val periodDays: Int,
    val aiProvider: String,
    val aiModel: String,
    val signal: String,
    val confidence: Double,
    val upProbability: Double,
    val downProbability: Double,
    val riskLevel: String,
    val keyCorrelations: String,
    val marketSentimentImpact: String,
    val fundFlowImpact: String,
    val etfFlowImpact: String,
    val reasoning: String,
    val recommendation: String,
    val createdAt: Long
) {
    fun toEntity() = StockIndicatorAIResult(
        id = id, ticker = ticker, stockName = stockName, market = market,
        analysisDate = analysisDate, period = period, periodDays = periodDays,
        aiProvider = aiProvider, aiModel = aiModel, signal = signal,
        confidence = confidence, upProbability = upProbability,
        downProbability = downProbability, riskLevel = riskLevel,
        keyCorrelations = keyCorrelations, marketSentimentImpact = marketSentimentImpact,
        fundFlowImpact = fundFlowImpact, etfFlowImpact = etfFlowImpact,
        reasoning = reasoning, recommendation = recommendation, createdAt = createdAt
    )
    companion object {
        fun fromEntity(entity: StockIndicatorAIResult) = StockIndicatorAIResultDto(
            id = entity.id, ticker = entity.ticker, stockName = entity.stockName,
            market = entity.market, analysisDate = entity.analysisDate,
            period = entity.period, periodDays = entity.periodDays,
            aiProvider = entity.aiProvider, aiModel = entity.aiModel,
            signal = entity.signal, confidence = entity.confidence,
            upProbability = entity.upProbability, downProbability = entity.downProbability,
            riskLevel = entity.riskLevel, keyCorrelations = entity.keyCorrelations,
            marketSentimentImpact = entity.marketSentimentImpact,
            fundFlowImpact = entity.fundFlowImpact, etfFlowImpact = entity.etfFlowImpact,
            reasoning = entity.reasoning, recommendation = entity.recommendation,
            createdAt = entity.createdAt
        )
    }
}

@Serializable
data class EnhancedPredictionDto(
    val id: String,
    val ticker: String,
    val name: String,
    val predictionDate: String,
    val confidence: Double,
    val status: String,
    val keyFactors: String,
    val riskScore: Double,
    val featureValues: String,
    val modelType: String,
    val daysAfter: Int,
    val priceThreshold: Double,
    val actualPriceChange: Double?,
    val wasCorrect: Boolean?,
    val createdAt: Long
) {
    fun toEntity() = EnhancedPrediction(
        id = id, ticker = ticker, name = name, predictionDate = predictionDate,
        confidence = confidence, status = status, keyFactors = keyFactors,
        riskScore = riskScore, featureValues = featureValues, modelType = modelType,
        daysAfter = daysAfter, priceThreshold = priceThreshold,
        actualPriceChange = actualPriceChange, wasCorrect = wasCorrect, createdAt = createdAt
    )
    companion object {
        fun fromEntity(entity: EnhancedPrediction) = EnhancedPredictionDto(
            id = entity.id, ticker = entity.ticker, name = entity.name,
            predictionDate = entity.predictionDate, confidence = entity.confidence,
            status = entity.status, keyFactors = entity.keyFactors,
            riskScore = entity.riskScore, featureValues = entity.featureValues,
            modelType = entity.modelType, daysAfter = entity.daysAfter,
            priceThreshold = entity.priceThreshold,
            actualPriceChange = entity.actualPriceChange,
            wasCorrect = entity.wasCorrect, createdAt = entity.createdAt
        )
    }
}

// ==================== User Data DTOs ====================

@Serializable
data class SearchHistoryDto(
    val id: Int,
    val ticker: String,
    val name: String,
    val market: String,
    val historyType: String,
    val searchedAt: Long
) {
    fun toEntity() = SearchHistory(
        id = id, ticker = ticker, name = name, market = market,
        historyType = historyType, searchedAt = searchedAt
    )
    companion object {
        fun fromEntity(entity: SearchHistory) = SearchHistoryDto(
            id = entity.id, ticker = entity.ticker, name = entity.name,
            market = entity.market, historyType = entity.historyType,
            searchedAt = entity.searchedAt
        )
    }
}
