package com.etfmonitor.core.analysis

import com.etfmonitor.core.analysis.model.*

/**
 * 추세 시그널 분석 계산기
 *
 * trend_signal 패키지의 기술적 지표 분석:
 * - MA (Moving Average): 추세 방향
 * - CMF (Chaikin Money Flow): 자금 유입/유출
 * - Fear & Greed Index: 시장 심리
 * - 매수/매도 시그널
 *
 * determineSignal은 참조 TrendCalculator.calcTrend와 동일한
 * 2-of-3 투표 로직으로 구현된다. MarketMonitor의 5-state 시그널
 * (STRONG_BUY/BUY/NEUTRAL/SELL/STRONG_SELL)을 유지하면서
 * FG 극단값으로 STRONG 등급을 부여한다.
 */
object TrendSignalCalculator {

    /**
     * 추세 시그널 분석
     *
     * @param data Python에서 계산된 지표 데이터
     * @return 분석 결과
     */
    fun analyze(data: TrendSignalData): TrendSignalAnalysis {
        if (data.dates.isEmpty()) {
            return createEmptyAnalysis()
        }

        val lastIdx = data.dates.size - 1

        // 최신 값들
        val currentPrice = data.close[lastIdx]
        val maPrice = data.ma[lastIdx]
        val cmfValue = data.cmf[lastIdx]
        val fearGreedValue = data.fearGreed[lastIdx]

        // 최근 5개 기간의 시그널 카운트 (주 시그널 + 보조 시그널)
        val recentPeriod = minOf(5, data.dates.size)
        val recentBuyCount = data.buySignal.takeLast(recentPeriod).sum() +
                data.auxBuySignal.takeLast(recentPeriod).sum()
        val recentSellCount = data.sellSignal.takeLast(recentPeriod).sum() +
                data.auxSellSignal.takeLast(recentPeriod).sum()

        // 추세 신호 결정 — 2-of-3 투표 + FG 극단값 STRONG 승격
        val signal = determineSignal(
            currentPrice = currentPrice,
            maPrice = maPrice,
            cmfValue = cmfValue,
            fearGreedValue = fearGreedValue,
            recentBuyCount = recentBuyCount,
            recentSellCount = recentSellCount
        )

        // 추세 설명
        val trendDescription = buildTrendDescription(
            currentPrice = currentPrice,
            maPrice = maPrice,
            cmfValue = cmfValue,
            fearGreedValue = fearGreedValue
        )

        // 투자 권고
        val recommendation = buildRecommendation(signal, fearGreedValue)

        return TrendSignalAnalysis(
            signal = signal,
            currentPrice = currentPrice,
            maPrice = maPrice,
            cmfValue = cmfValue,
            fearGreedValue = fearGreedValue,
            trendDescription = trendDescription,
            recommendation = recommendation,
            recentBuyCount = recentBuyCount,
            recentSellCount = recentSellCount
        )
    }

    /**
     * 매매 신호 결정 — 2-of-3 투표 로직
     *
     * 참조: TrendCalculator.calcTrend (lines 385-422)
     *
     * 1단계: 3개 지표로 bullish/bearish/neutral 판별
     *   - MA 위치: price > ma → bull, price < ma → bear
     *   - CMF:     cmf > 0.05 → bull, cmf < -0.05 → bear
     *   - FG:      fg > 0.5   → bull, fg < -0.5   → bear
     *
     * 2단계: 2개 이상 동의 시 방향 확정
     *   bullCount >= 2 → "bullish"
     *   bearCount >= 2 → "bearish"
     *   그 외         → "neutral"
     *
     * 3단계: FG 극단값으로 STRONG 등급 승격
     *   bullish  + fg > 1.0  → STRONG_BUY
     *   bullish              → BUY
     *   bearish  + fg < -0.8 → STRONG_SELL
     *   bearish              → SELL
     *   neutral              → NEUTRAL
     *
     * 5-state 시그널은 UI 역호환을 위해 유지된다.
     */
    private fun determineSignal(
        currentPrice: Double,
        maPrice: Double,
        cmfValue: Double,
        fearGreedValue: Double,
        recentBuyCount: Int,
        recentSellCount: Int
    ): TrendTradeSignal {
        var bullCount = 0
        var bearCount = 0

        // 1. 가격 위치 (MA 대비)
        when {
            currentPrice > maPrice -> bullCount++
            currentPrice < maPrice -> bearCount++
        }

        // 2. CMF (자금 흐름)
        when {
            cmfValue > 0.05 -> bullCount++
            cmfValue < -0.05 -> bearCount++
        }

        // 3. Fear & Greed Index
        when {
            fearGreedValue > 0.5 -> bullCount++
            fearGreedValue < -0.5 -> bearCount++
        }

        // 2-of-3 투표로 기본 방향 결정 후 FG 극단값으로 STRONG 승격
        return when {
            bullCount >= 2 -> {
                if (fearGreedValue > 1.0) TrendTradeSignal.STRONG_BUY
                else TrendTradeSignal.BUY
            }
            bearCount >= 2 -> {
                if (fearGreedValue < -0.8) TrendTradeSignal.STRONG_SELL
                else TrendTradeSignal.SELL
            }
            else -> TrendTradeSignal.NEUTRAL
        }
    }

    /**
     * 추세 설명 생성
     */
    private fun buildTrendDescription(
        currentPrice: Double,
        maPrice: Double,
        cmfValue: Double,
        fearGreedValue: Double
    ): String {
        val parts = mutableListOf<String>()

        // 가격 추세
        val priceTrend = if (currentPrice > maPrice) "상승 추세" else "하락 추세"
        val priceGap = ((currentPrice - maPrice) / maPrice * 100)
        parts.add("$priceTrend (MA 대비 ${String.format("%.1f", priceGap)}%)")

        // 자금 흐름
        val cmfTrend = when {
            cmfValue > 0.1 -> "강한 자금 유입"
            cmfValue > 0 -> "자금 유입"
            cmfValue < -0.1 -> "강한 자금 유출"
            cmfValue < 0 -> "자금 유출"
            else -> "중립"
        }
        parts.add(cmfTrend)

        // Fear & Greed 상태
        val fearGreedState = FearGreedState.fromValue(fearGreedValue)
        parts.add(fearGreedState.displayName)

        return parts.joinToString(" | ")
    }

    /**
     * 투자 권고 생성
     */
    private fun buildRecommendation(
        signal: TrendTradeSignal,
        fearGreedValue: Double
    ): String {
        val fearGreedState = FearGreedState.fromValue(fearGreedValue)

        return when (signal) {
            TrendTradeSignal.STRONG_BUY -> {
                if (fearGreedState == FearGreedState.EXTREME_GREED) {
                    "강한 매수 시그널이나, 과열 주의"
                } else {
                    "적극 매수 검토"
                }
            }
            TrendTradeSignal.BUY -> {
                "매수 관심, 추가 확인 권장"
            }
            TrendTradeSignal.NEUTRAL -> {
                "관망, 명확한 추세 대기"
            }
            TrendTradeSignal.SELL -> {
                "매도 검토, 손절/익절 점검"
            }
            TrendTradeSignal.STRONG_SELL -> {
                if (fearGreedState == FearGreedState.EXTREME_FEAR) {
                    "강한 매도 시그널, 반등 기회 모니터링"
                } else {
                    "적극 매도 검토"
                }
            }
        }
    }

    /**
     * 빈 분석 결과 생성
     */
    private fun createEmptyAnalysis(): TrendSignalAnalysis {
        return TrendSignalAnalysis(
            signal = TrendTradeSignal.NEUTRAL,
            currentPrice = 0.0,
            maPrice = 0.0,
            cmfValue = 0.0,
            fearGreedValue = 0.0,
            trendDescription = "데이터 없음",
            recommendation = "데이터를 확인해주세요",
            recentBuyCount = 0,
            recentSellCount = 0
        )
    }
}
