package com.etfmonitor.feature.analysis.domain.model

/**
 * AI 분석 결과 도메인 모델
 */
data class AIAnalysis(
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
    val keyFactors: List<String>,
    val recommendation: String,
    val alternativeScenarios: String?,
    val processingTimeMs: Long
)

/**
 * 상관관계 분석 결과 도메인 모델
 */
data class CorrelationAnalysis(
    val id: String,
    val market: String,
    val analysisDate: String,
    val periodDays: Int,
    // ETF 관련 상관관계
    val etfNetFlowCorrelation: Double,
    val etfNewStockCorrelation: Double,
    val etfRemovedStockCorrelation: Double,
    val etfIncreasedCorrelation: Double,
    val etfDecreasedCorrelation: Double,
    val cashDepositCorrelation: Double,
    // 시장 자금 관련 상관관계
    val marketDepositCorrelation: Double?,
    val creditBalanceCorrelation: Double?,
    // Fear & Greed 상관관계
    val fearGreedCorrelation: Double?,
    val fearGreedLeadCorrelation: Double?,
    // Oscillator 상관관계
    val oscillatorCorrelation: Double?,
    val oscillatorLeadCorrelation: Double?,
    // 종합 결과
    val compositeScore: Double,
    val signal: String,
    val confidence: Double,
    val upProbability: Double,
    val downProbability: Double,
    val analysisContext: String
)

/**
 * 전체 분석 결과 (상관관계 + AI 해석)
 */
data class FullAnalysis(
    val correlationResult: CorrelationAnalysis,
    val aiResult: AIAnalysis?,
    val errorMessage: String?
)

/**
 * 시총 가중 ETF 흐름 도메인 모델
 */
data class MarketCapFlow(
    val date: String,
    val market: String,
    val totalInflow: Long,
    val totalOutflow: Long,
    val netFlow: Long,
    val topInflowStocks: List<StockFlow>,
    val topOutflowStocks: List<StockFlow>,
    val inflowBySize: Map<MarketCapSize, Long>,
    val outflowBySize: Map<MarketCapSize, Long>,
    val flowVsMarketChange: Double?
)

/**
 * 개별 종목 흐름
 */
data class StockFlow(
    val ticker: String,
    val name: String,
    val market: String,
    val marketCap: Long,
    val weightChange: Double,
    val flowAmount: Long,
    val etfCount: Int,
    val status: String
)

/**
 * 시총 규모 분류
 */
enum class MarketCapSize {
    LARGE,      // 대형주 (10조원 이상)
    MID,        // 중형주 (1조원 ~ 10조원)
    SMALL;      // 소형주 (1조원 미만)

    companion object {
        fun fromMarketCap(marketCap: Long): MarketCapSize {
            return when {
                marketCap >= 100_000_0000_0000L -> LARGE   // 10조원
                marketCap >= 10_000_0000_0000L -> MID      // 1조원
                else -> SMALL
            }
        }
    }
}

/**
 * 유동성 분석 도메인 모델
 */
data class LiquidityAnalysisData(
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
    val riskLevel: LeverageRisk,
    val signal: LiquiditySignalType,
    val historicalPercentile: Double
)

/**
 * 레버리지 위험 수준
 */
enum class LeverageRisk {
    LOW,
    MEDIUM,
    HIGH;

    companion object {
        fun fromCreditDepositRatio(ratio: Double): LeverageRisk {
            return when {
                ratio > 40 -> HIGH
                ratio > 25 -> MEDIUM
                else -> LOW
            }
        }
    }
}

/**
 * 유동성 신호 타입
 */
enum class LiquiditySignalType {
    BULLISH_LIQUIDITY,   // 유동성 증가 (긍정적)
    NEUTRAL,             // 중립
    DELEVERAGING,        // 디레버리징 (단기 하락 가능)
    BEARISH_LEVERAGE;    // 과도한 레버리지 (위험)

    companion object {
        fun calculate(depositChange: Double, creditChange: Double, creditRatio: Double): LiquiditySignalType {
            return when {
                depositChange > 0 && creditRatio < 30 -> BULLISH_LIQUIDITY
                creditChange < 0 -> DELEVERAGING
                creditRatio > 40 -> BEARISH_LEVERAGE
                else -> NEUTRAL
            }
        }
    }
}

/**
 * 섹터 분석 도메인 모델
 */
data class SectorAnalysisData(
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
    val sentiment: SectorSentimentType
)

/**
 * 섹터 심리 타입
 */
enum class SectorSentimentType {
    GREED,
    NEUTRAL,
    FEAR;

    companion object {
        fun fromValue(value: Double): SectorSentimentType {
            return when {
                value >= 0.6 -> GREED
                value <= 0.4 -> FEAR
                else -> NEUTRAL
            }
        }
    }
}

/**
 * 섹터 로테이션 신호
 */
data class SectorRotation(
    val fromSector: String,
    val toSector: String,
    val confidence: Double,
    val flowDifference: Double,
    val description: String
)

/**
 * ETF 상관관계 캐시 도메인 모델
 */
data class EtfCorrelation(
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
    val topCommonStocks: List<CommonStock>
)

/**
 * 공통 보유 종목
 */
data class CommonStock(
    val ticker: String,
    val name: String,
    val etf1Weight: Double,
    val etf2Weight: Double,
    val avgWeight: Double
)

/**
 * 수급 Divergence 분석 결과
 */
data class DivergenceAnalysis(
    val date: String,
    val market: String,
    val foreignBullishCount: Int,
    val institutionBullishCount: Int,
    val alignedBullishCount: Int,
    val alignedBearishCount: Int,
    val neutralCount: Int,
    val topForeignBullish: List<SupplyDemandItem>,
    val topInstitutionBullish: List<SupplyDemandItem>,
    val marketSentiment: MarketSentiment,
    val sentimentStrength: Double
)

/**
 * 수급 아이템
 */
data class SupplyDemandItem(
    val ticker: String,
    val name: String,
    val market: String,
    val date: String,
    val foreign5d: Long,
    val institution5d: Long,
    val marketCap: Long,
    val divergenceScore: Double,
    val divergenceType: DivergenceType,
    val etfWeightChange: Double?,
    val etfStatus: String?
)

/**
 * Divergence 타입
 */
enum class DivergenceType {
    FOREIGN_BULLISH,
    INSTITUTION_BULLISH,
    ALIGNED_BULLISH,
    ALIGNED_BEARISH,
    NEUTRAL;

    companion object {
        fun classify(foreign5d: Long, institution5d: Long, threshold: Long): DivergenceType {
            return when {
                foreign5d > threshold && institution5d > threshold -> ALIGNED_BULLISH
                foreign5d < -threshold && institution5d < -threshold -> ALIGNED_BEARISH
                foreign5d > threshold -> FOREIGN_BULLISH
                institution5d > threshold -> INSTITUTION_BULLISH
                else -> NEUTRAL
            }
        }
    }
}

/**
 * 시장 심리
 */
enum class MarketSentiment {
    CONSENSUS_BULLISH,
    STRONG_FOREIGN_LED,
    STRONG_INSTITUTION_LED,
    MIXED,
    CONSENSUS_BEARISH;

    companion object {
        fun calculate(
            foreignBullish: Int,
            institutionBullish: Int,
            alignedBullish: Int,
            alignedBearish: Int,
            total: Int
        ): MarketSentiment {
            if (total == 0) return MIXED
            val bullishRatio = (foreignBullish + institutionBullish + alignedBullish).toDouble() / total
            val bearishRatio = alignedBearish.toDouble() / total

            return when {
                alignedBullish > alignedBearish && bullishRatio > 0.6 -> CONSENSUS_BULLISH
                foreignBullish > institutionBullish * 2 && bullishRatio > 0.5 -> STRONG_FOREIGN_LED
                institutionBullish > foreignBullish * 2 && bullishRatio > 0.5 -> STRONG_INSTITUTION_LED
                bearishRatio > 0.6 -> CONSENSUS_BEARISH
                else -> MIXED
            }
        }
    }
}

/**
 * 유동성 추이
 */
data class LiquidityTrendData(
    val history: List<LiquidityAnalysisData>,
    val avgDepositRatio: Double,
    val avgCreditRatio: Double,
    val currentVsAvgDeposit: Double,
    val depositTrend: TrendDirection,
    val creditTrend: TrendDirection,
    val trendStrength: Double
)

/**
 * 추세 방향
 */
enum class TrendDirection {
    UP,
    DOWN,
    FLAT;

    companion object {
        fun fromChangeRate(changeRate: Double): TrendDirection {
            return when {
                changeRate > 1.0 -> UP
                changeRate < -1.0 -> DOWN
                else -> FLAT
            }
        }
    }
}

/**
 * 포트폴리오 분산 분석 결과
 */
data class PortfolioDiversificationResult(
    val selectedEtfs: List<String>,
    val overallDiversificationScore: Double,
    val pairwiseCorrelations: List<EtfPairCorrelation>,
    val avgCorrelation: Double,
    val suggestions: List<DiversificationAdvice>
)

/**
 * ETF 쌍 상관관계
 */
data class EtfPairCorrelation(
    val etf1Ticker: String,
    val etf1Name: String,
    val etf2Ticker: String,
    val etf2Name: String,
    val overlapRatio: Double,
    val weightCorrelation: Double,
    val commonStockCount: Int,
    val topCommonStocks: List<CommonStock>
)

/**
 * 분산 투자 조언
 */
data class DiversificationAdvice(
    val type: AdviceType,
    val message: String,
    val affectedEtfs: List<String>,
    val impact: Double?
)

/**
 * 조언 타입
 */
enum class AdviceType {
    HIGH_OVERLAP_WARNING,
    LOW_DIVERSIFICATION,
    SECTOR_CONCENTRATION,
    SUGGESTION
}
