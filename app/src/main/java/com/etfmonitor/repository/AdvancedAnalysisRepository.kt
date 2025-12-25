package com.etfmonitor.repository

import com.etfmonitor.core.database.*
import com.etfmonitor.core.database.entities.*
import com.etfmonitor.core.common.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 고급 분석 Repository
 *
 * 5가지 핵심 분석 기능 제공:
 * 1. 시총 가중 ETF 흐름 분석
 * 2. 외국인/기관 수급 Divergence 분석
 * 3. 예탁금/시총 비율 분석 (유동성)
 * 4. 섹터별 Fear & Greed 분석
 * 5. ETF 간 상관관계 분석
 */
@Singleton
class AdvancedAnalysisRepository @Inject constructor(
    private val etfDao: EtfDao,
    private val stockDao: StockDao,
    private val stockAnalysisDao: StockAnalysisDao,
    private val marketDepositDao: MarketDepositDao,
    private val fearGreedDao: FearGreedDao,
    private val marketIndexDao: MarketIndexDao,
    private val sectorAnalysisDao: SectorAnalysisDao,
    private val etfCorrelationDao: EtfCorrelationDao,
    private val liquidityAnalysisDao: LiquidityAnalysisDao
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    companion object {
        private val logger = AppLogger.getLogger("AdvancedAnalysis")

        // 시총 규모 기준 (억원)
        private const val LARGE_CAP_THRESHOLD = 100_000L  // 10조원
        private const val MID_CAP_THRESHOLD = 10_000L    // 1조원

        // Divergence 판단 기준 (백만원)
        private const val DIVERGENCE_THRESHOLD = 1_000L  // 10억원

        // 상관관계 계산 최소 데이터 수
        private const val MIN_DATA_POINTS = 5
    }

    // ==================== 1. 시총 가중 ETF 흐름 분석 ====================

    /**
     * 시총 가중 ETF 흐름 계산
     */
    suspend fun calculateMarketCapWeightedFlow(
        currentDate: String,
        previousDate: String,
        market: String = "ALL"
    ): MarketCapWeightedFlow = withContext(Dispatchers.IO) {
        try {
            // 1. 비중 변화 종목 조회
            val stockChanges = etfDao.getStockAmountRanking(currentDate, previousDate)
                .filter { market == "ALL" || getStockMarket(it.stockTicker) == market }

            logger.d( "Stock changes count: ${stockChanges.size} for market: $market")

            if (stockChanges.isEmpty()) {
                logger.w( "No stock changes found for dates: $currentDate vs $previousDate")
                return@withContext createEmptyMarketCapWeightedFlow(currentDate, market)
            }

            // 2. 시총 가중 흐름 계산 (totalAmount를 직접 사용 - stock_analysis_data 의존 제거)
            val inflowStocks = mutableListOf<StockFlow>()
            val outflowStocks = mutableListOf<StockFlow>()
            val inflowBySize = mutableMapOf<MarketCapSize, Long>()
            val outflowBySize = mutableMapOf<MarketCapSize, Long>()

            MarketCapSize.entries.forEach {
                inflowBySize[it] = 0L
                outflowBySize[it] = 0L
            }

            for (stock in stockChanges) {
                // totalAmount (원)을 억원으로 변환하여 사용
                val amountInBillion = (stock.totalAmount / 100_000_000).toLong()
                val weightChange = calculateWeightChange(stock)

                // 흐름 금액 = totalAmount 기반 추정 (비중 변화에 따른 방향 결정)
                val flowAmount = when {
                    stock.newEtfCount > 0 -> amountInBillion  // 신규 편입 = 전체 금액 유입
                    stock.removedEtfCount > 0 -> -amountInBillion  // 제외 = 전체 금액 유출
                    stock.increasedEtfCount > stock.decreasedEtfCount -> (amountInBillion * 0.1).toLong()  // 증가 우위
                    stock.decreasedEtfCount > stock.increasedEtfCount -> -(amountInBillion * 0.1).toLong()  // 감소 우위
                    else -> 0L
                }

                val stockFlow = StockFlow(
                    ticker = stock.stockTicker,
                    name = stock.stockName,
                    market = getStockMarket(stock.stockTicker),
                    marketCap = amountInBillion,  // ETF 보유 금액 (억원)
                    weightChange = weightChange,
                    flowAmount = flowAmount,
                    etfCount = stock.etfCount,
                    status = determineStatus(stock)
                )

                // 시총 규모는 ETF 보유 금액 기준으로 분류
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

            logger.d( "Flow result: inflow=$totalInflow, outflow=$totalOutflow, net=${totalInflow - totalOutflow}")

            MarketCapWeightedFlow(
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
            logger.e( "Error calculating market cap weighted flow", e)
            createEmptyMarketCapWeightedFlow(currentDate, market)
        }
    }

    /**
     * 시총 가중 흐름 이력 조회 (Flow)
     */
    fun observeMarketCapWeightedFlowHistory(
        days: Int = 30,
        market: String = "ALL"
    ): Flow<List<MarketCapWeightedFlow>> = flow {
        val dates = etfDao.getAllDistinctDates(days + 1)
        val results = mutableListOf<MarketCapWeightedFlow>()

        for (i in 0 until minOf(days, dates.size - 1)) {
            val flow = calculateMarketCapWeightedFlow(dates[i], dates[i + 1], market)
            results.add(flow)
        }

        emit(results)
    }.flowOn(Dispatchers.IO)

    // ==================== 2. 외국인/기관 수급 Divergence 분석 ====================

    /**
     * 수급 Divergence 분석
     */
    suspend fun analyzeSupplyDemandDivergence(
        date: String,
        market: String = "ALL"
    ): MarketDivergenceSummary = withContext(Dispatchers.IO) {
        try {
            // 분석 데이터 조회
            val allData = stockAnalysisDao.getAllAnalysisData()
            logger.d( "Stock analysis data count: ${allData.size}")

            if (allData.isEmpty()) {
                logger.w( "No stock analysis data found. Please run stock analysis first.")
                return@withContext createEmptyDivergenceSummary(date, market)
            }

            val analysisDataList = allData.filter { market == "ALL" || getStockMarket(it.ticker) == market }

            val divergenceList = mutableListOf<SupplyDemandDivergence>()

            for (data in analysisDataList) {
                val dateIndex = data.dates.indexOf(date)
                if (dateIndex < 0) continue

                val foreign5d = if (dateIndex < data.foreign5d.size) data.foreign5d[dateIndex] else 0L
                val institution5d = if (dateIndex < data.institution5d.size) data.institution5d[dateIndex] else 0L
                val marketCap = if (dateIndex < data.marketCap.size) data.marketCap[dateIndex] else 0L

                if (marketCap == 0L) continue

                val stockName = stockDao.getStock(data.ticker)?.name ?: data.ticker
                val divergenceScore = calculateDivergenceScore(foreign5d, institution5d, marketCap)
                val divergenceType = DivergenceType.classify(foreign5d, institution5d, DIVERGENCE_THRESHOLD * 1_000_000)

                divergenceList.add(
                    SupplyDemandDivergence(
                        ticker = data.ticker,
                        name = stockName,
                        market = getStockMarket(data.ticker),
                        date = date,
                        foreign5d = foreign5d / 1_000_000,  // 백만원
                        institution5d = institution5d / 1_000_000,
                        marketCap = marketCap / 100_000_000,  // 억원
                        divergenceScore = divergenceScore,
                        divergenceType = divergenceType,
                        etfWeightChange = null,
                        etfStatus = null
                    )
                )
            }

            logger.d( "Divergence analysis: found ${divergenceList.size} stocks with data")

            // 집계
            val foreignBullish = divergenceList.filter { it.divergenceType == DivergenceType.FOREIGN_BULLISH }
            val institutionBullish = divergenceList.filter { it.divergenceType == DivergenceType.INSTITUTION_BULLISH }
            val alignedBullish = divergenceList.filter { it.divergenceType == DivergenceType.ALIGNED_BULLISH }
            val alignedBearish = divergenceList.filter { it.divergenceType == DivergenceType.ALIGNED_BEARISH }
            val neutral = divergenceList.filter { it.divergenceType == DivergenceType.NEUTRAL }

            val total = divergenceList.size
            logger.d( "Divergence breakdown: foreign=${ foreignBullish.size}, inst=${institutionBullish.size}, bullish=${alignedBullish.size}, bearish=${alignedBearish.size}, neutral=${neutral.size}")

            val sentiment = MarketSentimentType.calculate(
                foreignBullish.size,
                institutionBullish.size,
                alignedBullish.size,
                alignedBearish.size,
                total
            )

            MarketDivergenceSummary(
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
            logger.e( "Error analyzing supply demand divergence", e)
            createEmptyDivergenceSummary(date, market)
        }
    }

    // ==================== 3. 예탁금/시총 비율 분석 (유동성) ====================

    /**
     * 유동성 분석 계산 및 저장
     */
    suspend fun calculateAndSaveLiquidityAnalysis(date: String): LiquidityAnalysis? = withContext(Dispatchers.IO) {
        try {
            // 1. 예탁금 데이터 조회 (정확한 날짜 우선, 없으면 최신 데이터 사용)
            var deposit = marketDepositDao.getDepositByDate(date)
            var effectiveDate = date
            if (deposit == null) {
                logger.d("No deposit data for exact date: $date, trying latest")
                deposit = marketDepositDao.getLatestDeposit()
                if (deposit != null) {
                    effectiveDate = deposit.date
                    logger.d("Using latest deposit data from: $effectiveDate")
                }
            }
            if (deposit == null) {
                logger.w("No deposit data found")
                return@withContext null
            }
            logger.d("Deposit data: amount=${deposit.depositAmount}, credit=${deposit.creditAmount}")

            // 2. 시가총액 계산
            val (kospiCap, kosdaqCap) = calculateTotalMarketCap(date)
            var totalCap = kospiCap + kosdaqCap
            logger.d( "Market cap: kospi=$kospiCap, kosdaq=$kosdaqCap, total=$totalCap")

            // 시총 데이터가 없는 경우 기본값 사용 (2024년 기준 KOSPI+KOSDAQ 시총 약 2,500조원)
            if (totalCap == 0L) {
                logger.w( "No market cap data available. Using estimated total market cap.")
                totalCap = 2500_0000_0000_0000L  // 2500조원 기본값
            }

            // 3. 비율 계산
            val depositRatio = (deposit.depositAmount / (totalCap / 100_000_000.0)) * 100
            val creditRatio = (deposit.creditAmount / deposit.depositAmount) * 100

            // 4. 백분위 계산 (기존 데이터 기준)
            val percentile = liquidityAnalysisDao.getDepositRatioPercentile(depositRatio) ?: 50.0

            // 5. 신호 결정
            val riskLevel = LeverageRiskLevel.fromCreditDepositRatio(creditRatio)
            val signal = LiquiditySignal.calculate(deposit.depositChange, deposit.creditChange, creditRatio)

            val analysis = LiquidityAnalysis(
                date = effectiveDate,
                depositAmount = deposit.depositAmount,
                creditAmount = deposit.creditAmount,
                totalMarketCap = totalCap / 100_000_000,  // 억원
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

            // 저장
            liquidityAnalysisDao.insert(analysis)
            analysis
        } catch (e: Exception) {
            logger.e( "Error calculating liquidity analysis", e)
            null
        }
    }

    /**
     * 최신 유동성 분석 조회
     */
    suspend fun getLatestLiquidityAnalysis(): LiquidityAnalysis? = withContext(Dispatchers.IO) {
        liquidityAnalysisDao.getLatest()
    }

    /**
     * 유동성 분석 이력 (Flow)
     */
    fun observeLiquidityHistory(days: Int = 30): Flow<List<LiquidityAnalysis>> {
        return liquidityAnalysisDao.observeRecentHistory(days)
    }

    /**
     * 유동성 추이 분석
     */
    suspend fun analyzeLiquidityTrend(days: Int = 30): LiquidityTrend? = withContext(Dispatchers.IO) {
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

            LiquidityTrend(
                history = history,
                avgDepositRatio = avgDepositRatio,
                avgCreditRatio = avgCreditRatio,
                currentVsAvgDeposit = latest.depositToMarketCapRatio / avgDepositRatio,
                depositTrend = TrendDirection.fromChangeRate(avgDepositChange / latest.depositAmount * 100),
                creditTrend = TrendDirection.fromChangeRate(avgCreditChange / latest.creditAmount * 100),
                trendStrength = calculateTrendStrength(depositChanges)
            )
        } catch (e: Exception) {
            logger.e( "Error analyzing liquidity trend", e)
            null
        }
    }

    // ==================== 4. 섹터별 Fear & Greed 분석 ====================

    /**
     * 섹터별 Fear & Greed 분석 계산 및 저장
     */
    suspend fun calculateAndSaveSectorAnalysis(
        currentDate: String,
        previousDate: String
    ): List<SectorAnalysis> = withContext(Dispatchers.IO) {
        try {
            // 1. ETF별 섹터 매핑된 종목 변화 조회
            val stockChanges = etfDao.getStockAmountRanking(currentDate, previousDate)

            // 2. 섹터별 집계
            val sectorMap = mutableMapOf<String, MutableList<StockAmountRanking>>()

            for (stock in stockChanges) {
                val sector = inferSectorFromStock(stock.stockTicker, stock.stockName)
                sectorMap.getOrPut(sector) { mutableListOf() }.add(stock)
            }

            // 3. 시장 전체 Fear & Greed 조회 (기준값)
            val latestFearGreedDate = fearGreedDao.getLatestDate("KOSPI")
            val marketFearGreed = if (latestFearGreedDate != null) {
                fearGreedDao.getByMarketAndDate("KOSPI", latestFearGreedDate)?.fearGreedValue ?: 0.5
            } else 0.5

            // 4. 섹터별 분석 계산
            val results = mutableListOf<SectorAnalysis>()

            for ((sector, stocks) in sectorMap) {
                if (stocks.isEmpty()) continue

                val newEntries = stocks.count { it.newEtfCount > 0 }
                val removals = stocks.count { it.removedEtfCount > 0 }
                val avgWeightChange = stocks.map {
                    calculateWeightChange(it)
                }.average()

                // ETF 흐름 점수: (-1 ~ 1)
                val etfFlowScore = ((newEntries - removals).toDouble() / stocks.size).coerceIn(-1.0, 1.0)

                // 모멘텀 점수: 비중 변화 기반 (정규화)
                val momentumScore = (avgWeightChange / 10.0).coerceIn(-1.0, 1.0)

                // 변동성 점수: 비중 변화의 표준편차 기반 계산 (0 ~ 1)
                val volatilityScore = calculateSectorVolatility(stocks)

                // Fear & Greed 계산 (가중 평균)
                val fearGreedValue = (
                    0.4 * ((etfFlowScore + 1) / 2) +
                    0.35 * ((momentumScore + 1) / 2) +
                    0.25 * volatilityScore
                ).coerceIn(0.0, 1.0)

                val sentiment = SectorSentiment.fromValue(fearGreedValue)

                val analysis = SectorAnalysis(
                    id = SectorAnalysis.createId(sector, currentDate),
                    sector = sector,
                    sectorName = SectorMapping.getSectorDisplayName(sector),
                    date = currentDate,
                    fearGreedValue = fearGreedValue,
                    etfFlowScore = etfFlowScore,
                    momentumScore = momentumScore,
                    volatilityScore = volatilityScore,
                    stockCount = stocks.size,
                    newEntries = newEntries,
                    removals = removals,
                    avgWeightChange = avgWeightChange,
                    sentiment = sentiment.name
                )

                results.add(analysis)
            }

            // 저장
            sectorAnalysisDao.insertAll(results)
            results.sortedByDescending { it.fearGreedValue }
        } catch (e: Exception) {
            logger.e( "Error calculating sector analysis", e)
            emptyList()
        }
    }

    /**
     * 특정 날짜의 섹터 분석 조회
     */
    suspend fun getSectorAnalysisByDate(date: String): List<SectorAnalysis> = withContext(Dispatchers.IO) {
        sectorAnalysisDao.getByDate(date)
    }

    /**
     * 섹터 분석 (Flow)
     */
    fun observeSectorAnalysis(date: String): Flow<List<SectorAnalysis>> {
        return sectorAnalysisDao.observeByDate(date)
    }

    /**
     * 섹터 로테이션 신호 감지
     */
    suspend fun detectSectorRotation(
        currentDate: String,
        previousDate: String
    ): List<SectorRotationSignal> = withContext(Dispatchers.IO) {
        try {
            val currentAnalysis = sectorAnalysisDao.getByDate(currentDate).associateBy { it.sector }
            val previousAnalysis = sectorAnalysisDao.getByDate(previousDate).associateBy { it.sector }

            val signals = mutableListOf<SectorRotationSignal>()

            // 흐름 점수 변화가 큰 섹터 쌍 찾기
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
                            SectorRotationSignal(
                                fromSector = from,
                                toSector = to,
                                confidence = minOf(flowDiff / 2.0, 1.0),
                                flowDifference = flowDiff,
                                description = "${SectorMapping.getSectorDisplayName(from)} → ${SectorMapping.getSectorDisplayName(to)} 자금 이동 감지"
                            )
                        )
                    }
                }
            }

            signals.sortedByDescending { it.confidence }
        } catch (e: Exception) {
            logger.e( "Error detecting sector rotation", e)
            emptyList()
        }
    }

    // ==================== 5. ETF 간 상관관계 분석 ====================

    /**
     * ETF 쌍의 상관관계 계산 및 저장
     */
    suspend fun calculateAndSaveEtfCorrelation(
        etf1Ticker: String,
        etf2Ticker: String,
        date: String
    ): EtfCorrelationCache? = withContext(Dispatchers.IO) {
        try {
            // 1. ETF 정보 조회
            val etf1 = etfDao.getEtf(etf1Ticker) ?: return@withContext null
            val etf2 = etfDao.getEtf(etf2Ticker) ?: return@withContext null

            // 2. 보유 종목 조회
            val holdings1 = etfDao.getHoldings(etf1Ticker, date)
            val holdings2 = etfDao.getHoldings(etf2Ticker, date)

            if (holdings1.isEmpty() || holdings2.isEmpty()) {
                // 로그는 너무 많아지므로 생략
                return@withContext null
            }

            // 3. 종목 중복률 계산
            val stocks1 = holdings1.map { it.stockTicker }.toSet()
            val stocks2 = holdings2.map { it.stockTicker }.toSet()

            val intersection = stocks1.intersect(stocks2)
            val union = stocks1.union(stocks2)
            val overlapRatio = if (union.isNotEmpty()) intersection.size.toDouble() / union.size else 0.0

            // 4. 공통 종목 정보
            val commonStocks = intersection.mapNotNull { ticker ->
                val h1 = holdings1.find { it.stockTicker == ticker }
                val h2 = holdings2.find { it.stockTicker == ticker }
                if (h1 != null && h2 != null) {
                    CommonStock(
                        ticker = ticker,
                        name = h1.stockName,
                        etf1Weight = h1.weightBps / 100.0,
                        etf2Weight = h2.weightBps / 100.0,
                        avgWeight = (h1.weightBps + h2.weightBps) / 200.0
                    )
                } else null
            }.sortedByDescending { it.avgWeight }.take(10)

            // 5. 비중 변화 상관계수 계산 (단순화: 현재 비중 기준)
            val weightCorrelation = calculateWeightCorrelation(holdings1, holdings2, intersection)

            val correlation = EtfCorrelationCache(
                id = EtfCorrelationCache.createId(etf1Ticker, etf2Ticker, date),
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

            // 저장
            etfCorrelationDao.insert(correlation)
            correlation
        } catch (e: Exception) {
            logger.e( "Error calculating ETF correlation", e)
            null
        }
    }

    /**
     * 모든 ETF 쌍의 상관관계 계산
     */
    suspend fun calculateAllEtfCorrelations(date: String): List<EtfCorrelationCache> = withContext(Dispatchers.IO) {
        try {
            val etfs = etfDao.getAllEtfs().first()
            logger.d("Starting ETF correlation calculation for ${etfs.size} ETFs on $date")
            val results = mutableListOf<EtfCorrelationCache>()
            var skippedCount = 0

            for (i in etfs.indices) {
                for (j in i + 1 until etfs.size) {
                    val correlation = calculateAndSaveEtfCorrelation(etfs[i].ticker, etfs[j].ticker, date)
                    if (correlation != null) {
                        results.add(correlation)
                    } else {
                        skippedCount++
                    }
                }
            }

            logger.d("ETF correlation completed: ${results.size} calculated, $skippedCount skipped")
            results
        } catch (e: Exception) {
            logger.e( "Error calculating all ETF correlations", e)
            emptyList()
        }
    }

    /**
     * ETF 쌍 중복률 조회
     * threshold를 낮춰서 모든 상관관계 결과를 표시
     */
    suspend fun getHighOverlapEtfPairs(
        date: String,
        threshold: Double = 0.1
    ): List<EtfCorrelationCache> = withContext(Dispatchers.IO) {
        val results = etfCorrelationDao.getHighOverlapPairs(date, threshold)
        logger.d("ETF overlap pairs for $date with threshold $threshold: ${results.size} found")
        results
    }

    /**
     * 포트폴리오 분산 분석
     */
    suspend fun analyzePortfolioDiversification(
        etfTickers: List<String>,
        date: String
    ): PortfolioDiversification = withContext(Dispatchers.IO) {
        try {
            if (etfTickers.size < 2) {
                return@withContext PortfolioDiversification(
                    selectedEtfs = etfTickers,
                    overallDiversificationScore = 1.0,
                    pairwiseCorrelations = emptyList(),
                    avgCorrelation = 0.0,
                    suggestions = emptyList()
                )
            }

            // 상관관계 조회 또는 계산
            val correlations = mutableListOf<EtfCorrelation>()
            for (i in etfTickers.indices) {
                for (j in i + 1 until etfTickers.size) {
                    val cached = etfCorrelationDao.getByEtfPair(etfTickers[i], etfTickers[j], date)
                        ?: calculateAndSaveEtfCorrelation(etfTickers[i], etfTickers[j], date)

                    if (cached != null) {
                        correlations.add(
                            EtfCorrelation(
                                etf1Ticker = cached.etf1Ticker,
                                etf1Name = cached.etf1Name,
                                etf2Ticker = cached.etf2Ticker,
                                etf2Name = cached.etf2Name,
                                overlapRatio = cached.overlapRatio,
                                weightCorrelation = cached.weightCorrelation,
                                commonStockCount = cached.commonStockCount,
                                topCommonStocks = emptyList()  // 간략화
                            )
                        )
                    }
                }
            }

            val avgCorrelation = if (correlations.isNotEmpty()) {
                correlations.map { it.overlapRatio }.average()
            } else 0.0

            val diversificationScore = 1.0 - avgCorrelation

            // 제안 생성
            val suggestions = mutableListOf<DiversificationSuggestion>()

            // 높은 중복률 경고
            val highOverlap = correlations.filter { it.overlapRatio > 0.7 }
            for (pair in highOverlap) {
                suggestions.add(
                    DiversificationSuggestion(
                        type = SuggestionType.HIGH_OVERLAP_WARNING,
                        message = "${pair.etf1Name}와 ${pair.etf2Name}의 종목 중복률이 ${(pair.overlapRatio * 100).toInt()}%로 높습니다.",
                        affectedEtfs = listOf(pair.etf1Ticker, pair.etf2Ticker),
                        impact = null
                    )
                )
            }

            PortfolioDiversification(
                selectedEtfs = etfTickers,
                overallDiversificationScore = diversificationScore,
                pairwiseCorrelations = correlations,
                avgCorrelation = avgCorrelation,
                suggestions = suggestions
            )
        } catch (e: Exception) {
            logger.e( "Error analyzing portfolio diversification", e)
            PortfolioDiversification(
                selectedEtfs = etfTickers,
                overallDiversificationScore = 0.5,
                pairwiseCorrelations = emptyList(),
                avgCorrelation = 0.5,
                suggestions = emptyList()
            )
        }
    }

    // ==================== Helper Methods ====================

    private suspend fun getMarketCapMap(date: String): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        val allData = stockAnalysisDao.getAllAnalysisData()

        for (data in allData) {
            val dateIndex = data.dates.indexOf(date)
            if (dateIndex >= 0 && dateIndex < data.marketCap.size) {
                result[data.ticker] = data.marketCap[dateIndex]
            }
        }

        return result
    }

    private fun getStockMarket(ticker: String): String {
        // 종목코드 규칙에 따른 시장 판단
        return when {
            ticker.startsWith("0") || ticker.startsWith("1") ||
            ticker.startsWith("2") || ticker.startsWith("3") -> "KOSPI"
            else -> "KOSDAQ"
        }
    }

    private fun calculateWeightChange(stock: StockAmountRanking): Double {
        // 신규/제외의 경우 비중 변화 추정
        return when {
            stock.newEtfCount > 0 -> stock.maxWeight / 100.0
            stock.removedEtfCount > 0 -> -stock.maxWeight / 100.0
            stock.increasedEtfCount > 0 -> stock.maxWeight / 200.0  // 추정
            stock.decreasedEtfCount > 0 -> -stock.maxWeight / 200.0
            else -> 0.0
        }
    }

    private fun determineStatus(stock: StockAmountRanking): String {
        return when {
            stock.newEtfCount > 0 -> "NEW"
            stock.removedEtfCount > 0 -> "REMOVED"
            stock.increasedEtfCount > 0 -> "INCREASED"
            stock.decreasedEtfCount > 0 -> "DECREASED"
            else -> "UNCHANGED"
        }
    }

    private fun calculateDivergenceScore(foreign5d: Long, institution5d: Long, marketCap: Long): Double {
        if (marketCap == 0L) return 0.0

        val normalizedForeign = foreign5d.toDouble() / (marketCap / 100)
        val normalizedInstitution = institution5d.toDouble() / (marketCap / 100)

        return (normalizedForeign - normalizedInstitution).coerceIn(-1.0, 1.0)
    }

    private fun calculateSentimentStrength(
        foreignBullish: Int,
        institutionBullish: Int,
        alignedBullish: Int,
        alignedBearish: Int,
        total: Int
    ): Double {
        if (total == 0) return 0.0

        val maxCount = maxOf(foreignBullish, institutionBullish, alignedBullish, alignedBearish)
        return maxCount.toDouble() / total
    }

    /**
     * 시장 전체 시가총액 계산
     *
     * 우선순위:
     * 1. 요청 날짜와 정확히 일치하는 데이터 사용
     * 2. 일치하는 날짜가 없으면 가장 최근 날짜 데이터 사용
     * 3. 데이터가 없으면 (0, 0) 반환 (호출측에서 기본값 처리)
     */
    private suspend fun calculateTotalMarketCap(date: String): Pair<Long, Long> {
        val allData = stockAnalysisDao.getAllAnalysisData()
        logger.d( "calculateTotalMarketCap: ${allData.size} stocks in stock_analysis_data")

        if (allData.isEmpty()) {
            logger.w( "No stock analysis data available for market cap calculation")
            return 0L to 0L
        }

        var kospiCap = 0L
        var kosdaqCap = 0L
        var matchedCount = 0
        var usedFallback = false

        for (data in allData) {
            // 1. 정확한 날짜 매칭 시도
            var dateIndex = data.dates.indexOf(date)

            // 2. 정확한 매칭이 없으면 가장 최근 날짜 사용
            if (dateIndex < 0 || dateIndex >= data.marketCap.size) {
                // 최근 날짜 순으로 정렬된 데이터에서 첫 번째 유효한 인덱스 사용
                dateIndex = 0  // 가장 최근 데이터
                usedFallback = true
            }

            if (dateIndex < data.marketCap.size) {
                val cap = data.marketCap[dateIndex]
                if (cap > 0) {
                    matchedCount++
                    if (getStockMarket(data.ticker) == "KOSPI") {
                        kospiCap += cap
                    } else {
                        kosdaqCap += cap
                    }
                }
            }
        }

        if (usedFallback && matchedCount > 0) {
            logger.d( "calculateTotalMarketCap: Using latest available data (not exact date match)")
        }
        logger.d( "calculateTotalMarketCap: $matchedCount stocks, kospi=$kospiCap, kosdaq=$kosdaqCap")
        return kospiCap to kosdaqCap
    }

    private fun calculateTrendStrength(changes: List<Double>): Double {
        if (changes.isEmpty()) return 0.0

        val sameDirection = changes.zipWithNext().count { (a, b) ->
            (a > 0 && b > 0) || (a < 0 && b < 0)
        }

        return if (changes.size > 1) {
            sameDirection.toDouble() / (changes.size - 1)
        } else 0.0
    }

    /**
     * 섹터 변동성 점수 계산
     *
     * 비중 변화의 표준편차를 기반으로 변동성을 계산하고 0~1 범위로 정규화합니다.
     * - 높은 표준편차 = 높은 변동성 = 불안정 (낮은 점수)
     * - 낮은 표준편차 = 낮은 변동성 = 안정 (높은 점수)
     *
     * @param stocks 섹터에 포함된 종목 리스트
     * @return 변동성 점수 (0.0 = 높은 변동성, 1.0 = 낮은 변동성)
     */
    private fun calculateSectorVolatility(stocks: List<StockAmountRanking>): Double {
        if (stocks.size < 2) return 0.5  // 데이터 부족 시 중립값

        // 각 종목의 비중 변화율 계산
        val weightChanges = stocks.map { calculateWeightChange(it) }

        // 표준편차 계산
        val mean = weightChanges.average()
        val variance = weightChanges.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)

        // 정규화: 표준편차 10%를 최대 변동성으로 가정
        // stdDev가 0이면 변동성이 없음 (안정적) → 1.0 반환
        // stdDev가 10 이상이면 높은 변동성 → 0.0에 가까운 값 반환
        val normalizedVolatility = (stdDev / 10.0).coerceIn(0.0, 1.0)

        // 변동성 점수 반전 (낮은 변동성 = 높은 점수, 시장 안정 = Fear & Greed에서 긍정적)
        return 1.0 - normalizedVolatility
    }

    private fun inferSectorFromStock(ticker: String, name: String): String {
        // SectorMapping의 개선된 섹터 분류 사용
        // 1. 종목 티커 기반 직접 매핑 (100+ 대형주)
        // 2. 종목명 키워드 패턴 매칭 (16개 섹터)
        return SectorMapping.inferSectorFromStock(ticker, name)
    }

    private fun calculateWeightCorrelation(
        holdings1: List<Holding>,
        holdings2: List<Holding>,
        commonStocks: Set<String>
    ): Double {
        if (commonStocks.size < MIN_DATA_POINTS) return 0.0

        val weights1 = mutableListOf<Double>()
        val weights2 = mutableListOf<Double>()

        for (ticker in commonStocks) {
            val w1 = holdings1.find { it.stockTicker == ticker }?.weightBps?.toDouble() ?: continue
            val w2 = holdings2.find { it.stockTicker == ticker }?.weightBps?.toDouble() ?: continue
            weights1.add(w1)
            weights2.add(w2)
        }

        return pearsonCorrelation(weights1, weights2)
    }

    private fun pearsonCorrelation(x: List<Double>, y: List<Double>): Double {
        if (x.size != y.size || x.size < 2) return 0.0

        val meanX = x.average()
        val meanY = y.average()

        var numerator = 0.0
        var denomX = 0.0
        var denomY = 0.0

        for (i in x.indices) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY
            numerator += dx * dy
            denomX += dx * dx
            denomY += dy * dy
        }

        val denominator = sqrt(denomX * denomY)
        return if (denominator > 0) numerator / denominator else 0.0
    }

    private fun createEmptyMarketCapWeightedFlow(date: String, market: String) = MarketCapWeightedFlow(
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

    private fun createEmptyDivergenceSummary(date: String, market: String) = MarketDivergenceSummary(
        date = date,
        market = market,
        foreignBullishCount = 0,
        institutionBullishCount = 0,
        alignedBullishCount = 0,
        alignedBearishCount = 0,
        neutralCount = 0,
        topForeignBullish = emptyList(),
        topInstitutionBullish = emptyList(),
        marketSentiment = MarketSentimentType.MIXED,
        sentimentStrength = 0.0
    )
}
