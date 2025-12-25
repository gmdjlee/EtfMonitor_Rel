package com.etfmonitor.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 시장 유동성 분석 결과
 * 예탁금/시총 비율 및 레버리지 수준 추적
 */
@Entity(tableName = "liquidity_analysis")
data class LiquidityAnalysis(
    @PrimaryKey
    val date: String,
    val depositAmount: Double,              // 고객 예탁금 (억원)
    val creditAmount: Double,               // 신용 잔고 (억원)
    val totalMarketCap: Long,               // 시장 전체 시총 (억원)
    val kospiMarketCap: Long,               // KOSPI 시총 (억원)
    val kosdaqMarketCap: Long,              // KOSDAQ 시총 (억원)
    val depositToMarketCapRatio: Double,    // 예탁금/시총 비율 (%)
    val creditToDepositRatio: Double,       // 신용/예탁금 비율 (%)
    val depositChange: Double,              // 예탁금 변화 (억원)
    val creditChange: Double,               // 신용 변화 (억원)
    val riskLevel: String,                  // LOW, MEDIUM, HIGH, EXTREME
    val signal: String,                     // BULLISH_LIQUIDITY, BEARISH_LEVERAGE, NEUTRAL, DELEVERAGING
    val historicalPercentile: Double,       // 예탁금/시총 비율의 과거 대비 백분위 (0~100)
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * 레버리지 위험 수준
 */
enum class LeverageRiskLevel(val displayName: String, val threshold: Double) {
    LOW("낮음", 30.0),
    MEDIUM("보통", 45.0),
    HIGH("높음", 60.0),
    EXTREME("매우 높음", Double.MAX_VALUE);

    companion object {
        fun fromCreditDepositRatio(ratio: Double): LeverageRiskLevel = when {
            ratio < LOW.threshold -> LOW
            ratio < MEDIUM.threshold -> MEDIUM
            ratio < HIGH.threshold -> HIGH
            else -> EXTREME
        }
    }
}

/**
 * 유동성 신호
 */
enum class LiquiditySignal(val displayName: String, val description: String) {
    BULLISH_LIQUIDITY("상승 여력", "예탁금 증가 + 낮은 신용 = 유동성 풍부"),
    BEARISH_LEVERAGE("하락 위험", "신용 증가 + 예탁금 감소 = 레버리지 과열"),
    DELEVERAGING("디레버리징", "신용 감소 = 조정 진행 중"),
    NEUTRAL("중립", "특이 신호 없음");

    companion object {
        fun calculate(
            depositChange: Double,
            creditChange: Double,
            creditToDepositRatio: Double
        ): LiquiditySignal = when {
            depositChange > 0 && creditToDepositRatio < 35.0 -> BULLISH_LIQUIDITY
            creditChange > 0 && depositChange < 0 -> BEARISH_LEVERAGE
            creditChange < 0 && creditChange < depositChange -> DELEVERAGING
            else -> NEUTRAL
        }
    }
}

/**
 * 유동성 추이 분석
 */
data class LiquidityTrend(
    val history: List<LiquidityAnalysis>,
    val avgDepositRatio: Double,           // 기간 평균 예탁금/시총 비율
    val avgCreditRatio: Double,            // 기간 평균 신용/예탁금 비율
    val currentVsAvgDeposit: Double,       // 현재/평균 비율
    val depositTrend: TrendDirection,      // 예탁금 추세
    val creditTrend: TrendDirection,       // 신용 추세
    val trendStrength: Double              // 추세 강도 (0.0 ~ 1.0)
)

/**
 * 추세 방향
 */
enum class TrendDirection(val displayName: String) {
    STRONG_UP("강한 상승"),
    UP("상승"),
    STABLE("안정"),
    DOWN("하락"),
    STRONG_DOWN("강한 하락");

    companion object {
        fun fromChangeRate(changeRate: Double): TrendDirection = when {
            changeRate > 5.0 -> STRONG_UP
            changeRate > 1.0 -> UP
            changeRate > -1.0 -> STABLE
            changeRate > -5.0 -> DOWN
            else -> STRONG_DOWN
        }
    }
}

/**
 * 시총 가중 ETF 흐름 분석 결과
 */
data class MarketCapWeightedFlow(
    val date: String,
    val market: String,                     // KOSPI, KOSDAQ, ALL
    val totalInflow: Long,                  // 시총 가중 유입 (억원)
    val totalOutflow: Long,                 // 시총 가중 유출 (억원)
    val netFlow: Long,                      // 순 흐름 (억원)
    val topInflowStocks: List<StockFlow>,   // 상위 유입 종목
    val topOutflowStocks: List<StockFlow>,  // 상위 유출 종목
    val inflowBySize: Map<MarketCapSize, Long>,  // 시총 규모별 유입
    val outflowBySize: Map<MarketCapSize, Long>, // 시총 규모별 유출
    val flowVsMarketChange: Double?         // 흐름 대비 시장 변화 (상관성)
)

/**
 * 개별 종목 흐름
 */
data class StockFlow(
    val ticker: String,
    val name: String,
    val market: String,
    val marketCap: Long,                // 시가총액 (억원)
    val weightChange: Double,           // 비중 변화 (%)
    val flowAmount: Long,               // 시총 가중 흐름 (억원)
    val etfCount: Int,                  // 보유 ETF 수
    val status: String                  // NEW, INCREASED, DECREASED, REMOVED
)

/**
 * 시총 규모 분류
 */
enum class MarketCapSize(val displayName: String, val minCap: Long) {
    LARGE("대형주", 10_000_000_000_000L),    // 10조 이상
    MID("중형주", 1_000_000_000_000L),       // 1조~10조
    SMALL("소형주", 0L);                      // 1조 미만

    companion object {
        fun fromMarketCap(marketCap: Long): MarketCapSize = when {
            marketCap >= LARGE.minCap -> LARGE
            marketCap >= MID.minCap -> MID
            else -> SMALL
        }
    }
}

/**
 * 외국인/기관 수급 Divergence 결과
 */
data class SupplyDemandDivergence(
    val ticker: String,
    val name: String,
    val market: String,
    val date: String,
    val foreign5d: Long,                    // 외국인 5일 누적 (백만원)
    val institution5d: Long,                // 기관 5일 누적 (백만원)
    val marketCap: Long,                    // 시가총액 (억원)
    val divergenceScore: Double,            // -1.0 ~ 1.0
    val divergenceType: DivergenceType,
    val etfWeightChange: Double?,           // ETF 비중 변화 (있는 경우)
    val etfStatus: String?                  // ETF 편입 상태 (있는 경우)
)

/**
 * Divergence 유형
 */
enum class DivergenceType(val displayName: String, val description: String) {
    FOREIGN_BULLISH("외국인 강세", "외국인 매수, 기관 매도"),
    INSTITUTION_BULLISH("기관 강세", "기관 매수, 외국인 매도"),
    ALIGNED_BULLISH("동반 매수", "외국인/기관 동반 매수"),
    ALIGNED_BEARISH("동반 매도", "외국인/기관 동반 매도"),
    NEUTRAL("중립", "뚜렷한 방향 없음");

    companion object {
        fun classify(
            foreign5d: Long,
            institution5d: Long,
            threshold: Long = 1_000_000_000L  // 10억원
        ): DivergenceType = when {
            foreign5d > threshold && institution5d < -threshold -> FOREIGN_BULLISH
            institution5d > threshold && foreign5d < -threshold -> INSTITUTION_BULLISH
            foreign5d > threshold && institution5d > threshold -> ALIGNED_BULLISH
            foreign5d < -threshold && institution5d < -threshold -> ALIGNED_BEARISH
            else -> NEUTRAL
        }
    }
}

/**
 * 시장 전체 Divergence 요약
 */
data class MarketDivergenceSummary(
    val date: String,
    val market: String,
    val foreignBullishCount: Int,
    val institutionBullishCount: Int,
    val alignedBullishCount: Int,
    val alignedBearishCount: Int,
    val neutralCount: Int,
    val topForeignBullish: List<SupplyDemandDivergence>,
    val topInstitutionBullish: List<SupplyDemandDivergence>,
    val marketSentiment: MarketSentimentType,
    val sentimentStrength: Double  // 0.0 ~ 1.0
)

/**
 * 시장 심리 유형
 */
enum class MarketSentimentType(val displayName: String) {
    STRONG_FOREIGN_LED("외국인 주도 상승"),
    STRONG_INSTITUTION_LED("기관 주도 상승"),
    CONSENSUS_BULLISH("컨센서스 상승"),
    CONSENSUS_BEARISH("컨센서스 하락"),
    MIXED("혼조");

    companion object {
        fun calculate(
            foreignBullish: Int,
            institutionBullish: Int,
            alignedBullish: Int,
            alignedBearish: Int,
            total: Int
        ): MarketSentimentType {
            if (total == 0) return MIXED

            val foreignRatio = foreignBullish.toDouble() / total
            val institutionRatio = institutionBullish.toDouble() / total
            val bullishRatio = alignedBullish.toDouble() / total
            val bearishRatio = alignedBearish.toDouble() / total

            return when {
                foreignRatio > 0.3 && foreignRatio > institutionRatio * 1.5 -> STRONG_FOREIGN_LED
                institutionRatio > 0.3 && institutionRatio > foreignRatio * 1.5 -> STRONG_INSTITUTION_LED
                bullishRatio > 0.4 -> CONSENSUS_BULLISH
                bearishRatio > 0.4 -> CONSENSUS_BEARISH
                else -> MIXED
            }
        }
    }
}
