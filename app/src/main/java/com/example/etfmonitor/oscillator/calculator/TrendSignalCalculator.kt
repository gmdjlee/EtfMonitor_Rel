package com.etfmonitor.oscillator.calculator

import com.etfmonitor.oscillator.model.*

/**
 * 추세 시그널 분석 계산기
 *
 * trend_signal 패키지의 기술적 지표 분석:
 * - MA (Moving Average): 추세 방향
 * - CMF (Chaikin Money Flow): 자금 유입/유출
 * - Fear & Greed Index: 시장 심리
 * - 매수/매도 시그널
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

        // 최근 5개 기간의 시그널 카운트
        val recentPeriod = minOf(5, data.dates.size)
        val recentBuyCount = data.buySignal.takeLast(recentPeriod).sum()
        val recentSellCount = data.sellSignal.takeLast(recentPeriod).sum()

        // 추세 신호 결정
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
     * 매매 신호 결정
     */
    private fun determineSignal(
        currentPrice: Double,
        maPrice: Double,
        cmfValue: Double,
        fearGreedValue: Double,
        recentBuyCount: Int,
        recentSellCount: Int
    ): TrendTradeSignal {
        var score = 0

        // 1. 가격 위치 (MA 대비) - 30점
        if (currentPrice > maPrice) {
            score += 30
        } else if (currentPrice < maPrice) {
            score -= 30
        }

        // 2. CMF (자금 유입/유출) - 30점
        when {
            cmfValue > 0.1 -> score += 30
            cmfValue > 0 -> score += 15
            cmfValue < -0.1 -> score -= 30
            cmfValue < 0 -> score -= 15
        }

        // 3. Fear & Greed - 20점
        when {
            fearGreedValue > 0.4 -> score += 20   // 탐욕 (매수세 강함)
            fearGreedValue > 0 -> score += 10
            fearGreedValue < -0.4 -> score -= 20  // 공포 (매도세 강함)
            fearGreedValue < 0 -> score -= 10
        }

        // 4. 최근 시그널 빈도 - 20점
        val signalDiff = recentBuyCount - recentSellCount
        when {
            signalDiff >= 2 -> score += 20
            signalDiff >= 1 -> score += 10
            signalDiff <= -2 -> score -= 20
            signalDiff <= -1 -> score -= 10
        }

        // 신호 결정 (-100 ~ +100)
        return when {
            score >= 70 -> TrendTradeSignal.STRONG_BUY
            score >= 30 -> TrendTradeSignal.BUY
            score <= -70 -> TrendTradeSignal.STRONG_SELL
            score <= -30 -> TrendTradeSignal.SELL
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
