package com.etfmonitor.feature.analysis.data.repository

// Database DAOs
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.StockDao
import com.etfmonitor.core.database.StockAnalysisDao
import com.etfmonitor.core.database.MarketDepositDao
import com.etfmonitor.core.database.FearGreedDao
import com.etfmonitor.core.database.MarketIndexDao
import com.etfmonitor.core.database.SectorAnalysisDao
import com.etfmonitor.core.database.EtfCorrelationDao
import com.etfmonitor.core.database.LiquidityAnalysisDao

// Database entities - only what's needed (no conflicts)
import com.etfmonitor.core.database.entities.Etf as EntityEtf

// Domain models - use these for return types
import com.etfmonitor.feature.analysis.domain.model.MarketCapFlow
import com.etfmonitor.feature.analysis.domain.model.StockFlow
import com.etfmonitor.feature.analysis.domain.model.MarketCapSize
import com.etfmonitor.feature.analysis.domain.model.DivergenceAnalysis
import com.etfmonitor.feature.analysis.domain.model.DivergenceType
import com.etfmonitor.feature.analysis.domain.model.SupplyDemandItem
import com.etfmonitor.feature.analysis.domain.model.MarketSentiment
import com.etfmonitor.feature.analysis.domain.model.LiquidityAnalysisData
import com.etfmonitor.feature.analysis.domain.model.LiquidityTrendData
import com.etfmonitor.feature.analysis.domain.model.LeverageRisk
import com.etfmonitor.feature.analysis.domain.model.LiquiditySignalType
import com.etfmonitor.feature.analysis.domain.model.TrendDirection
import com.etfmonitor.feature.analysis.domain.model.SectorAnalysisData
import com.etfmonitor.feature.analysis.domain.model.SectorSentimentType
import com.etfmonitor.feature.analysis.domain.model.SectorRotation
import com.etfmonitor.feature.analysis.domain.model.EtfCorrelation
import com.etfmonitor.feature.analysis.domain.model.CommonStock
import com.etfmonitor.feature.analysis.domain.model.EtfPairCorrelation
import com.etfmonitor.feature.analysis.domain.model.PortfolioDiversificationResult
import com.etfmonitor.feature.analysis.domain.model.DiversificationAdvice
import com.etfmonitor.feature.analysis.domain.model.AdviceType
import com.etfmonitor.feature.analysis.domain.model.DataAvailability
import com.etfmonitor.feature.analysis.domain.model.DataSourceStatus
import com.etfmonitor.feature.analysis.domain.model.AdvancedDashboard
import com.etfmonitor.feature.analysis.domain.model.OverallSignal
import com.etfmonitor.feature.analysis.domain.model.SignalDirection
import com.etfmonitor.feature.analysis.domain.model.MarketCapFlowHistory
import com.etfmonitor.feature.analysis.domain.model.PredictionAccuracy

import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.feature.analysis.data.mapper.toDomain
import com.etfmonitor.feature.analysis.domain.repository.AdvancedAnalysisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 고급 분석 Repository 구현체
 *
 * 5가지 핵심 분석 기능 제공:
 * 1. 시총 가중 ETF 흐름 분석
 * 2. 외국인/기관 수급 Divergence 분석
 * 3. 예탁금/시총 비율 분석 (유동성)
 * 4. 섹터별 Fear & Greed 분석
 * 5. ETF 간 상관관계 분석
 */
@Singleton
class AdvancedAnalysisRepositoryImpl @Inject constructor(
    private val etfDao: EtfDao,
    private val stockDao: StockDao,
    private val stockAnalysisDao: StockAnalysisDao,
    private val marketDepositDao: MarketDepositDao,
    private val fearGreedDao: FearGreedDao,
    private val marketIndexDao: MarketIndexDao,
    private val sectorAnalysisDao: SectorAnalysisDao,
    private val etfCorrelationDao: EtfCorrelationDao,
    private val liquidityAnalysisDao: LiquidityAnalysisDao
) : AdvancedAnalysisRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    companion object {
        private val logger = AppLogger.getLogger("AdvancedAnalysisImpl")
        private const val LARGE_CAP_THRESHOLD = 100_000L
        private const val MID_CAP_THRESHOLD = 10_000L
        private const val DIVERGENCE_THRESHOLD = 1_000L
        private const val MIN_DATA_POINTS = 5
    }

    // ==================== 1. 시총 가중 ETF 흐름 분석 ====================

    override suspend fun calculateMarketCapWeightedFlow(
        currentDate: String,
        previousDate: String,
        market: String
    ): MarketCapFlow = withContext(Dispatchers.IO) {
        try {
            val stockChanges = etfDao.getStockAmountRanking(currentDate, previousDate)
                .filter { market == "ALL" || getStockMarket(it.stockTicker) == market }

            if (stockChanges.isEmpty()) {
                return@withContext createEmptyMarketCapFlow(currentDate, market)
            }

            val inflowStocks = mutableListOf<StockFlow>()
            val outflowStocks = mutableListOf<StockFlow>()
            val inflowBySize = mutableMapOf<MarketCapSize, Long>()
            val outflowBySize = mutableMapOf<MarketCapSize, Long>()

            MarketCapSize.entries.forEach {
                inflowBySize[it] = 0L
                outflowBySize[it] = 0L
            }

            for (stock in stockChanges) {
                val amountInBillion = (stock.totalAmount / 100_000_000).toLong()
                val weightChange = calculateWeightChange(stock)

                val flowAmount = when {
                    stock.newEtfCount > 0 -> amountInBillion
                    stock.removedEtfCount > 0 -> -amountInBillion
                    stock.increasedEtfCount > stock.decreasedEtfCount -> (amountInBillion * 0.1).toLong()
                    stock.decreasedEtfCount > stock.increasedEtfCount -> -(amountInBillion * 0.1).toLong()
                    else -> 0L
                }

                val stockFlow = StockFlow(
                    ticker = stock.stockTicker,
                    name = stock.stockName,
                    market = getStockMarket(stock.stockTicker),
                    marketCap = amountInBillion,
                    weightChange = weightChange,
                    flowAmount = flowAmount,
                    etfCount = stock.etfCount,
                    status = determineStatus(stock)
                )

                val size = MarketCapSize.fromMarketCap(stock.totalAmount.toLong())

                if (flowAmount > 0) {
                    inflowStocks.add(stockFlow)
                    inflowBySize[size] = inflowBySize.getValue(size) + flowAmount
                } else if (flowAmount < 0) {
                    outflowStocks.add(stockFlow)
                    outflowBySize[size] = outflowBySize.getValue(size) + abs(flowAmount)
                }
            }

            val totalInflow = inflowBySize.values.sum()
            val totalOutflow = outflowBySize.values.sum()

            MarketCapFlow(
                date = currentDate,
                market = market,
                totalInflow = totalInflow,
                totalOutflow = totalOutflow,
                netFlow = totalInflow - totalOutflow,
                topInflowStocks = inflowStocks.sortedByDescending { it.flowAmount }.take(10),
                topOutflowStocks = outflowStocks.sortedByDescending { abs(it.flowAmount) }.take(10),
                inflowBySize = inflowBySize.toMap(),
                outflowBySize = outflowBySize.toMap(),
                flowVsMarketChange = null
            )
        } catch (e: Exception) {
            logger.e("Error calculating market cap weighted flow", e)
            createEmptyMarketCapFlow(currentDate, market)
        }
    }

    override fun observeMarketCapWeightedFlowHistory(
        days: Int,
        market: String
    ): Flow<List<MarketCapFlow>> = flow {
        val dates = etfDao.getAllDistinctDates(days + 1)
        val results = mutableListOf<MarketCapFlow>()

        for (i in 0 until minOf(days, dates.size - 1)) {
            val flow = calculateMarketCapWeightedFlow(dates[i], dates[i + 1], market)
            results.add(flow)
        }

        emit(results)
    }.flowOn(Dispatchers.IO)

    // ==================== 2. 외국인/기관 수급 Divergence 분석 ====================

    override suspend fun analyzeSupplyDemandDivergence(
        date: String,
        market: String
    ): DivergenceAnalysis = withContext(Dispatchers.IO) {
        try {
            val allData = stockAnalysisDao.getAllAnalysisData()
            if (allData.isEmpty()) {
                return@withContext createEmptyDivergenceAnalysis(date, market)
            }

            val analysisDataList = allData.filter { market == "ALL" || getStockMarket(it.ticker) == market }
            val divergenceList = mutableListOf<SupplyDemandItem>()

            for (data in analysisDataList) {
                val dateIndex = data.dates.indexOf(date)
                if (dateIndex < 0) continue

                val foreign5d = if (dateIndex < data.foreign5d.size) data.foreign5d[dateIndex] else 0L
                val institution5d = if (dateIndex < data.institution5d.size) data.institution5d[dateIndex] else 0L
                val marketCap = if (dateIndex < data.marketCap.size) data.marketCap[dateIndex] else 0L

                if (marketCap == 0L) continue

                val stockName = stockDao.getStock(data.ticker)?.name ?: data.ticker
                val divergenceScore = calculateDivergenceScore(foreign5d, institution5d, marketCap)
                val divergenceType = classifyDivergenceType(foreign5d, institution5d)

                divergenceList.add(
                    SupplyDemandItem(
                        ticker = data.ticker,
                        name = stockName,
                        market = getStockMarket(data.ticker),
                        date = date,
                        foreign5d = foreign5d / 1_000_000,
                        institution5d = institution5d / 1_000_000,
                        marketCap = marketCap / 100_000_000,
                        divergenceScore = divergenceScore,
                        divergenceType = divergenceType,
                        etfWeightChange = null,
                        etfStatus = null
                    )
                )
            }

            val foreignBullish = divergenceList.filter { it.divergenceType == DivergenceType.FOREIGN_BULLISH }
            val institutionBullish = divergenceList.filter { it.divergenceType == DivergenceType.INSTITUTION_BULLISH }
            val alignedBullish = divergenceList.filter { it.divergenceType == DivergenceType.ALIGNED_BULLISH }
            val alignedBearish = divergenceList.filter { it.divergenceType == DivergenceType.ALIGNED_BEARISH }
            val neutral = divergenceList.filter { it.divergenceType == DivergenceType.NEUTRAL }

            val total = divergenceList.size
            val sentiment = calculateMarketSentiment(
                foreignBullish.size,
                institutionBullish.size,
                alignedBullish.size,
                alignedBearish.size,
                total
            )

            DivergenceAnalysis(
                date = date,
                market = market,
                foreignBullishCount = foreignBullish.size,
                institutionBullishCount = institutionBullish.size,
                alignedBullishCount = alignedBullish.size,
                alignedBearishCount = alignedBearish.size,
                neutralCount = neutral.size,
                topForeignBullish = foreignBullish.sortedByDescending { it.divergenceScore }.take(10),
                topInstitutionBullish = institutionBullish.sortedBy { it.divergenceScore }.take(10),
                marketSentiment = sentiment,
                sentimentStrength = calculateSentimentStrength(foreignBullish.size, institutionBullish.size, alignedBullish.size, alignedBearish.size, total)
            )
        } catch (e: Exception) {
            logger.e("Error analyzing supply demand divergence", e)
            createEmptyDivergenceAnalysis(date, market)
        }
    }

    // ==================== 3. 예탁금/시총 비율 분석 (유동성) ====================

    override suspend fun calculateAndSaveLiquidityAnalysis(date: String): LiquidityAnalysisData? =
        withContext(Dispatchers.IO) {
            try {
                var deposit = marketDepositDao.getDepositByDate(date)
                var effectiveDate = date
                if (deposit == null) {
                    deposit = marketDepositDao.getLatestDeposit()
                    if (deposit != null) effectiveDate = deposit.date
                }
                if (deposit == null) return@withContext null

                val (kospiCap, kosdaqCap) = calculateTotalMarketCap(date)
                var totalCap = kospiCap + kosdaqCap
                if (totalCap == 0L) totalCap = 2500_0000_0000_0000L

                val depositRatio = (deposit.depositAmount / (totalCap / 100_000_000.0)) * 100
                val creditRatio = (deposit.creditAmount / deposit.depositAmount) * 100
                val percentile = liquidityAnalysisDao.getDepositRatioPercentile(depositRatio) ?: 50.0

                val riskLevel = LeverageRisk.fromCreditDepositRatio(creditRatio)
                val signal = LiquiditySignalType.calculate(deposit.depositChange, deposit.creditChange, creditRatio)

                val analysis = LiquidityAnalysis(
                    date = effectiveDate,
                    depositAmount = deposit.depositAmount,
                    creditAmount = deposit.creditAmount,
                    totalMarketCap = totalCap / 100_000_000,
                    kospiMarketCap = kospiCap / 100_000_000,
                    kosdaqMarketCap = kosdaqCap / 100_000_000,
                    depositToMarketCapRatio = depositRatio,
                    creditToDepositRatio = creditRatio,
                    depositChange = deposit.depositChange,
                    creditChange = deposit.creditChange,
                    riskLevel = riskLevel.name,
                    signal = signal.name,
                    historicalPercentile = percentile
                )

                liquidityAnalysisDao.insert(analysis)
                analysis.toDomain()
            } catch (e: Exception) {
                logger.e("Error calculating liquidity analysis", e)
                null
            }
        }

    override suspend fun getLatestLiquidityAnalysis(): LiquidityAnalysisData? =
        withContext(Dispatchers.IO) {
            liquidityAnalysisDao.getLatest()?.toDomain()
        }

    override fun observeLiquidityHistory(days: Int): Flow<List<LiquidityAnalysisData>> {
        return liquidityAnalysisDao.observeRecentHistory(days)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun analyzeLiquidityTrend(days: Int): LiquidityTrendData? =
        withContext(Dispatchers.IO) {
            try {
                val history = liquidityAnalysisDao.getRecentHistory(days)
                if (history.size < 2) return@withContext null

                val avgDepositRatio = history.map { it.depositToMarketCapRatio }.average()
                val avgCreditRatio = history.map { it.creditToDepositRatio }.average()

                val latest = history.first()
                val depositChanges = history.zipWithNext { a, b -> a.depositAmount - b.depositAmount }
                val creditChanges = history.zipWithNext { a, b -> a.creditAmount - b.creditAmount }

                val avgDepositChange = if (depositChanges.isNotEmpty()) depositChanges.average() else 0.0
                val avgCreditChange = if (creditChanges.isNotEmpty()) creditChanges.average() else 0.0

                LiquidityTrendData(
                    history = history.map { it.toDomain() },
                    avgDepositRatio = avgDepositRatio,
                    avgCreditRatio = avgCreditRatio,
                    currentVsAvgDeposit = latest.depositToMarketCapRatio / avgDepositRatio,
                    depositTrend = TrendDirection.fromChangeRate(avgDepositChange / latest.depositAmount * 100),
                    creditTrend = TrendDirection.fromChangeRate(avgCreditChange / latest.creditAmount * 100),
                    trendStrength = calculateTrendStrength(depositChanges)
                )
            } catch (e: Exception) {
                logger.e("Error analyzing liquidity trend", e)
                null
            }
        }

    // ==================== 4. 섹터별 Fear & Greed 분석 ====================

    override suspend fun calculateAndSaveSectorAnalysis(
        currentDate: String,
        previousDate: String
    ): List<SectorAnalysisData> = withContext(Dispatchers.IO) {
        try {
            val stockChanges = etfDao.getStockAmountRanking(currentDate, previousDate)
            val sectorMap = mutableMapOf<String, MutableList<StockAmountRanking>>()

            for (stock in stockChanges) {
                val sector = inferSectorFromStock(stock.stockTicker, stock.stockName)
                sectorMap.getOrPut(sector) { mutableListOf() }.add(stock)
            }

            val results = mutableListOf<SectorAnalysis>()

            for ((sector, stocks) in sectorMap) {
                if (stocks.isEmpty()) continue

                val newEntries = stocks.count { it.newEtfCount > 0 }
                val removals = stocks.count { it.removedEtfCount > 0 }
                val avgWeightChange = stocks.map { calculateWeightChange(it) }.average()

                val etfFlowScore = ((newEntries - removals).toDouble() / stocks.size).coerceIn(-1.0, 1.0)
                val momentumScore = (avgWeightChange / 10.0).coerceIn(-1.0, 1.0)
                val volatilityScore = calculateSectorVolatility(stocks)

                val fearGreedValue = (
                    0.4 * ((etfFlowScore + 1) / 2) +
                    0.35 * ((momentumScore + 1) / 2) +
                    0.25 * volatilityScore
                ).coerceIn(0.0, 1.0)

                val sentiment = when {
                    fearGreedValue >= 0.7 -> "GREEDY"
                    fearGreedValue >= 0.4 -> "NEUTRAL"
                    else -> "FEARFUL"
                }

                val analysis = SectorAnalysis(
                    id = "${sector}-${currentDate}",
                    sector = sector,
                    sectorName = getSectorDisplayName(sector),
                    date = currentDate,
                    fearGreedValue = fearGreedValue,
                    etfFlowScore = etfFlowScore,
                    momentumScore = momentumScore,
                    volatilityScore = volatilityScore,
                    stockCount = stocks.size,
                    newEntries = newEntries,
                    removals = removals,
                    avgWeightChange = avgWeightChange,
                    sentiment = sentiment
                )

                results.add(analysis)
            }

            sectorAnalysisDao.insertAll(results)
            results.sortedByDescending { it.fearGreedValue }.map { it.toDomain() }
        } catch (e: Exception) {
            logger.e("Error calculating sector analysis", e)
            emptyList()
        }
    }

    override suspend fun getSectorAnalysisByDate(date: String): List<SectorAnalysisData> =
        withContext(Dispatchers.IO) {
            sectorAnalysisDao.getByDate(date).map { it.toDomain() }
        }

    override fun observeSectorAnalysis(date: String): Flow<List<SectorAnalysisData>> {
        return sectorAnalysisDao.observeByDate(date)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun detectSectorRotation(
        currentDate: String,
        previousDate: String
    ): List<SectorRotation> = withContext(Dispatchers.IO) {
        try {
            val currentAnalysis = sectorAnalysisDao.getByDate(currentDate).associateBy { it.sector }
            val previousAnalysis = sectorAnalysisDao.getByDate(previousDate).associateBy { it.sector }

            val signals = mutableListOf<SectorRotation>()

            val flowChanges = currentAnalysis.mapValues { (sector, current) ->
                val prev = previousAnalysis[sector]
                if (prev != null) current.etfFlowScore - prev.etfFlowScore else 0.0
            }

            val increasingSectors = flowChanges.filter { it.value > 0.2 }.keys
            val decreasingSectors = flowChanges.filter { it.value < -0.2 }.keys

            for (from in decreasingSectors) {
                for (to in increasingSectors) {
                    val flowDiff = (flowChanges[to] ?: 0.0) - (flowChanges[from] ?: 0.0)
                    if (flowDiff > 0.3) {
                        signals.add(
                            SectorRotation(
                                fromSector = from,
                                toSector = to,
                                confidence = minOf(flowDiff / 2.0, 1.0),
                                flowDifference = flowDiff,
                                description = "${getSectorDisplayName(from)} → ${getSectorDisplayName(to)} 자금 이동 감지"
                            )
                        )
                    }
                }
            }

            signals.sortedByDescending { it.confidence }
        } catch (e: Exception) {
            logger.e("Error detecting sector rotation", e)
            emptyList()
        }
    }

    // ==================== 5. ETF 간 상관관계 분석 ====================

    override suspend fun calculateAndSaveEtfCorrelation(
        etf1Ticker: String,
        etf2Ticker: String,
        date: String
    ): EtfCorrelation? = withContext(Dispatchers.IO) {
        try {
            val etf1 = etfDao.getEtf(etf1Ticker) ?: return@withContext null
            val etf2 = etfDao.getEtf(etf2Ticker) ?: return@withContext null

            val holdings1 = etfDao.getHoldings(etf1Ticker, date)
            val holdings2 = etfDao.getHoldings(etf2Ticker, date)

            if (holdings1.isEmpty() || holdings2.isEmpty()) return@withContext null

            val stocks1 = holdings1.map { it.stockTicker }.toSet()
            val stocks2 = holdings2.map { it.stockTicker }.toSet()

            val intersection = stocks1.intersect(stocks2)
            val union = stocks1.union(stocks2)
            val overlapRatio = if (union.isNotEmpty()) intersection.size.toDouble() / union.size else 0.0

            val commonStocks = intersection.mapNotNull { ticker ->
                val h1 = holdings1.find { it.stockTicker == ticker }
                val h2 = holdings2.find { it.stockTicker == ticker }
                if (h1 != null && h2 != null) {
                    EntityCommonStock(
                        ticker = ticker,
                        name = h1.stockName,
                        etf1Weight = h1.weightBps / 100.0,
                        etf2Weight = h2.weightBps / 100.0,
                        avgWeight = (h1.weightBps + h2.weightBps) / 200.0
                    )
                } else null
            }.sortedByDescending { it.avgWeight }.take(10)

            val weightCorrelation = calculateWeightCorrelation(holdings1, holdings2, intersection)

            val correlation = EtfCorrelationCache(
                id = "${etf1Ticker}-${etf2Ticker}-$date",
                etf1Ticker = etf1Ticker,
                etf1Name = etf1.name,
                etf2Ticker = etf2Ticker,
                etf2Name = etf2.name,
                date = date,
                overlapRatio = overlapRatio,
                weightCorrelation = weightCorrelation,
                commonStockCount = intersection.size,
                etf1StockCount = stocks1.size,
                etf2StockCount = stocks2.size,
                topCommonStocks = json.encodeToString(commonStocks)
            )

            etfCorrelationDao.insert(correlation)
            correlation.toDomain()
        } catch (e: Exception) {
            logger.e("Error calculating ETF correlation", e)
            null
        }
    }

    override suspend fun calculateAllEtfCorrelations(date: String): List<EtfCorrelation> =
        withContext(Dispatchers.IO) {
            try {
                val etfs = etfDao.getAllEtfsSuspend()
                val results = mutableListOf<EtfCorrelation>()

                for (i in etfs.indices) {
                    for (j in i + 1 until etfs.size) {
                        val correlation = calculateAndSaveEtfCorrelation(
                            etfs[i].ticker,
                            etfs[j].ticker,
                            date
                        )
                        correlation?.let { results.add(it) }
                    }
                }

                results
            } catch (e: Exception) {
                logger.e("Error calculating all ETF correlations", e)
                emptyList()
            }
        }

    override suspend fun getHighOverlapEtfPairs(
        date: String,
        threshold: Double
    ): List<EtfCorrelation> = withContext(Dispatchers.IO) {
        etfCorrelationDao.getHighOverlapPairs(date, threshold).map { it.toDomain() }
    }

    override suspend fun analyzePortfolioDiversification(
        etfTickers: List<String>,
        date: String
    ): PortfolioDiversificationResult = withContext(Dispatchers.IO) {
        try {
            val correlations = mutableListOf<EtfPairCorrelation>()

            for (i in etfTickers.indices) {
                for (j in i + 1 until etfTickers.size) {
                    val correlation = calculateAndSaveEtfCorrelation(
                        etfTickers[i],
                        etfTickers[j],
                        date
                    )
                    correlation?.let {
                        correlations.add(
                            EtfPairCorrelation(
                                etf1Ticker = it.etf1Ticker,
                                etf1Name = it.etf1Name,
                                etf2Ticker = it.etf2Ticker,
                                etf2Name = it.etf2Name,
                                overlapRatio = it.overlapRatio,
                                weightCorrelation = it.weightCorrelation,
                                commonStockCount = it.commonStockCount,
                                topCommonStocks = it.topCommonStocks
                            )
                        )
                    }
                }
            }

            val avgCorrelation = if (correlations.isNotEmpty())
                correlations.map { it.overlapRatio }.average() else 0.0

            val score = ((1 - avgCorrelation) * 100).coerceIn(0.0, 100.0)

            val suggestions = mutableListOf<DiversificationAdvice>()
            correlations.filter { it.overlapRatio > 0.3 }.forEach { pair ->
                suggestions.add(
                    DiversificationAdvice(
                        type = AdviceType.HIGH_OVERLAP_WARNING,
                        message = "${pair.etf1Name}와 ${pair.etf2Name}은 ${String.format("%.1f", pair.overlapRatio * 100)}% 중복됩니다",
                        affectedEtfs = listOf(pair.etf1Ticker, pair.etf2Ticker),
                        impact = pair.overlapRatio
                    )
                )
            }

            PortfolioDiversificationResult(
                selectedEtfs = etfTickers,
                overallDiversificationScore = score,
                pairwiseCorrelations = correlations,
                avgCorrelation = avgCorrelation,
                suggestions = suggestions
            )
        } catch (e: Exception) {
            logger.e("Error analyzing portfolio diversification", e)
            PortfolioDiversificationResult(
                selectedEtfs = etfTickers,
                overallDiversificationScore = 0.0,
                pairwiseCorrelations = emptyList(),
                avgCorrelation = 0.0,
                suggestions = emptyList()
            )
        }
    }

    // ==================== Private Helpers ====================

    private fun getStockMarket(ticker: String): String {
        return if (ticker.startsWith("0") || ticker.startsWith("1") ||
                   ticker.startsWith("2") || ticker.startsWith("3")) {
            "KOSPI"
        } else {
            "KOSDAQ"
        }
    }

    private fun calculateWeightChange(stock: StockAmountRanking): Double {
        return when {
            stock.newEtfCount > 0 -> 100.0
            stock.removedEtfCount > 0 -> -100.0
            else -> ((stock.increasedEtfCount - stock.decreasedEtfCount).toDouble() /
                    maxOf(stock.etfCount, 1)) * 50.0
        }
    }

    private fun determineStatus(stock: StockAmountRanking): String {
        return when {
            stock.newEtfCount > 0 -> "NEW"
            stock.removedEtfCount > 0 -> "REMOVED"
            stock.increasedEtfCount > stock.decreasedEtfCount -> "INCREASED"
            stock.decreasedEtfCount > stock.increasedEtfCount -> "DECREASED"
            else -> "UNCHANGED"
        }
    }

    private fun createEmptyMarketCapFlow(date: String, market: String): MarketCapFlow {
        return MarketCapFlow(
            date = date,
            market = market,
            totalInflow = 0,
            totalOutflow = 0,
            netFlow = 0,
            topInflowStocks = emptyList(),
            topOutflowStocks = emptyList(),
            inflowBySize = emptyMap(),
            outflowBySize = emptyMap(),
            flowVsMarketChange = null
        )
    }

    private fun createEmptyDivergenceAnalysis(date: String, market: String): DivergenceAnalysis {
        return DivergenceAnalysis(
            date = date,
            market = market,
            foreignBullishCount = 0,
            institutionBullishCount = 0,
            alignedBullishCount = 0,
            alignedBearishCount = 0,
            neutralCount = 0,
            topForeignBullish = emptyList(),
            topInstitutionBullish = emptyList(),
            marketSentiment = MarketSentiment.MIXED,
            sentimentStrength = 0.0
        )
    }

    private fun calculateDivergenceScore(foreign5d: Long, institution5d: Long, marketCap: Long): Double {
        if (marketCap == 0L) return 0.0
        return ((foreign5d - institution5d).toDouble() / marketCap) * 100
    }

    private fun classifyDivergenceType(foreign5d: Long, institution5d: Long): DivergenceType {
        val threshold = DIVERGENCE_THRESHOLD * 1_000_000
        return when {
            foreign5d > threshold && institution5d < -threshold -> DivergenceType.FOREIGN_BULLISH
            institution5d > threshold && foreign5d < -threshold -> DivergenceType.INSTITUTION_BULLISH
            foreign5d > threshold && institution5d > threshold -> DivergenceType.ALIGNED_BULLISH
            foreign5d < -threshold && institution5d < -threshold -> DivergenceType.ALIGNED_BEARISH
            else -> DivergenceType.NEUTRAL
        }
    }

    private fun calculateMarketSentiment(
        foreignBullish: Int,
        institutionBullish: Int,
        alignedBullish: Int,
        alignedBearish: Int,
        total: Int
    ): MarketSentiment {
        if (total == 0) return MarketSentiment.MIXED

        val bullishRatio = (foreignBullish + institutionBullish + alignedBullish).toDouble() / total
        val bearishRatio = alignedBearish.toDouble() / total

        return when {
            alignedBullish > total * 0.3 -> MarketSentiment.CONSENSUS_BULLISH
            alignedBearish > total * 0.3 -> MarketSentiment.CONSENSUS_BEARISH
            foreignBullish > institutionBullish * 2 -> MarketSentiment.STRONG_FOREIGN_LED
            institutionBullish > foreignBullish * 2 -> MarketSentiment.STRONG_INSTITUTION_LED
            else -> MarketSentiment.MIXED
        }
    }

    private fun calculateSentimentStrength(
        foreignBullish: Int,
        institutionBullish: Int,
        alignedBullish: Int,
        alignedBearish: Int,
        total: Int
    ): Double {
        if (total == 0) return 0.0
        val dominant = maxOf(foreignBullish, institutionBullish, alignedBullish, alignedBearish)
        return dominant.toDouble() / total
    }

    private suspend fun calculateTotalMarketCap(date: String): Pair<Long, Long> {
        val kospiIndex = marketIndexDao.getByMarketAndDate("KOSPI", date)
        val kosdaqIndex = marketIndexDao.getByMarketAndDate("KOSDAQ", date)
        return Pair(
            kospiIndex?.volume?.toLong() ?: 0L,
            kosdaqIndex?.volume?.toLong() ?: 0L
        )
    }

    private fun calculateSectorVolatility(stocks: List<StockAmountRanking>): Double {
        if (stocks.size < 2) return 0.5
        val changes = stocks.map { calculateWeightChange(it) }
        val mean = changes.average()
        val variance = changes.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        return (stdDev / 100.0).coerceIn(0.0, 1.0)
    }

    private fun inferSectorFromStock(ticker: String, name: String): String {
        return when {
            name.contains("삼성전자") || name.contains("SK하이닉스") -> "IT"
            name.contains("현대차") || name.contains("기아") -> "AUTOMOTIVE"
            name.contains("셀트리온") || name.contains("삼바") -> "HEALTHCARE"
            name.contains("NAVER") || name.contains("카카오") -> "PLATFORM"
            name.contains("은행") || name.contains("금융") -> "FINANCE"
            name.contains("화학") || name.contains("케미칼") -> "CHEMICAL"
            name.contains("조선") -> "SHIPBUILDING"
            name.contains("건설") -> "CONSTRUCTION"
            name.contains("에너지") || name.contains("전력") -> "ENERGY"
            else -> "ETC"
        }
    }

    private fun getSectorDisplayName(sector: String): String {
        return when (sector) {
            "IT" -> "IT/반도체"
            "AUTOMOTIVE" -> "자동차"
            "HEALTHCARE" -> "헬스케어"
            "PLATFORM" -> "플랫폼"
            "FINANCE" -> "금융"
            "CHEMICAL" -> "화학"
            "SHIPBUILDING" -> "조선"
            "CONSTRUCTION" -> "건설"
            "ENERGY" -> "에너지"
            else -> "기타"
        }
    }

    private fun calculateTrendStrength(changes: List<Double>): Double {
        if (changes.isEmpty()) return 0.0
        val positive = changes.count { it > 0 }
        val negative = changes.count { it < 0 }
        return abs(positive - negative).toDouble() / changes.size
    }

    private fun calculateWeightCorrelation(
        holdings1: List<Holding>,
        holdings2: List<Holding>,
        commonStocks: Set<String>
    ): Double {
        if (commonStocks.size < MIN_DATA_POINTS) return 0.0

        val weights1 = commonStocks.mapNotNull { ticker ->
            holdings1.find { it.stockTicker == ticker }?.weightBps?.toDouble()
        }
        val weights2 = commonStocks.mapNotNull { ticker ->
            holdings2.find { it.stockTicker == ticker }?.weightBps?.toDouble()
        }

        if (weights1.size != weights2.size || weights1.isEmpty()) return 0.0

        val mean1 = weights1.average()
        val mean2 = weights2.average()

        var numerator = 0.0
        var sumSq1 = 0.0
        var sumSq2 = 0.0

        for (i in weights1.indices) {
            val d1 = weights1[i] - mean1
            val d2 = weights2[i] - mean2
            numerator += d1 * d2
            sumSq1 += d1 * d1
            sumSq2 += d2 * d2
        }

        val denominator = sqrt(sumSq1 * sumSq2)
        return if (denominator > 0) numerator / denominator else 0.0
    }
}
