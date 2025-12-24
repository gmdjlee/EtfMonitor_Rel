package com.etfmonitor.core.network.ai

/**
 * AI 시장 분석 프롬프트 템플릿
 * Claude API를 통한 시장 분석용 프롬프트 생성
 */
object MarketAnalysisPrompts {

    /**
     * 종합 시장 분석 프롬프트 생성
     */
    fun createComprehensiveAnalysisPrompt(data: MarketAnalysisData): String {
        return """
당신은 한국 주식 시장 전문 애널리스트입니다. 다음 데이터를 분석하여 ${data.market} 시장의 투자 신호를 제공해주세요.

## 📊 시장 현황
- **시장**: ${data.market}
- **날짜**: ${data.date}
- **현재 지수**: ${formatNumber(data.currentIndex)}
- **등락률**: ${formatPercent(data.indexChange)}%

## 📈 ETF 편입/편출 통계
- **신규 편입**: ${data.newStocks}개 종목 (금액: ${formatAmount(data.newStocksAmount)}원)
- **편입 제외**: ${data.removedStocks}개 종목 (금액: ${formatAmount(data.removedStocksAmount)}원)
- **비중 증가**: ${data.increasedStocks}개 종목 (금액: ${formatAmount(data.increasedStocksAmount)}원)
- **비중 감소**: ${data.decreasedStocks}개 종목 (금액: ${formatAmount(data.decreasedStocksAmount)}원)

## 💰 원화예금 동향
- **현재 예금**: ${formatAmount(data.cashDeposit)}원
- **전일 대비**: ${formatAmount(data.cashDepositChange)}원 (${formatPercent(data.cashDepositChangeRate)}%)

${if (data.depositAmount != null) """
## 💵 증시 자금 동향
- **고객예탁금**: ${formatAmount(data.depositAmount.toLong())}원
- **전일 대비**: ${formatAmount((data.depositChange ?: 0.0).toLong())}원
""" else ""}

${if (data.fearGreedValue != null) """
## 😨📊 Fear & Greed Index
- **지수 값**: ${formatPercent(data.fearGreedValue * 100)}
- **Oscillator**: ${formatNumber(data.fearGreedOscillator ?: 0.0)}
- **해석**: ${interpretFearGreed(data.fearGreedValue)}
""" else ""}

${if (data.marketOscillator != null) """
## 📉📈 시장 과매수/과매도
- **Oscillator**: ${formatNumber(data.marketOscillator)}
- **상태**: ${interpretOscillator(data.marketOscillator)}
""" else ""}

${if (!data.correlationData.isNullOrEmpty()) """
## 🔗 상관관계 분석
${data.correlationData.entries.joinToString("\n") { (key, value) ->
    "- **$key**: ${formatPercent(value * 100)} (${interpretCorrelation(value)})"
}}
""" else ""}

## 📋 분석 요청사항
위 데이터를 종합적으로 분석하여 다음 정보를 JSON 형식으로 제공해주세요:

```json
{
  "signal": "STRONG_BUY|BUY|NEUTRAL|SELL|STRONG_SELL",
  "confidence": 0.0-1.0,
  "upProbability": 0-100,
  "downProbability": 0-100,
  "reasoning": "분석 근거를 상세히 설명",
  "keyFactors": ["주요 영향 요인 1", "주요 영향 요인 2", "주요 영향 요인 3"],
  "recommendation": "투자자를 위한 구체적인 행동 권장사항",
  "riskLevel": "LOW|MEDIUM|HIGH"
}
```

**분석 시 고려사항:**
1. ETF의 신규 편입/제외는 기관의 시장 전망을 반영합니다
2. 원화예금 증가는 관망세, 감소는 매수 의욕을 나타냅니다
3. Fear & Greed 지수가 극단적일 때는 반전 가능성을 고려하세요
4. 과매수(>70) 또는 과매도(<30) 상태를 고려하세요
5. 각 지표의 상관관계를 종합적으로 판단하세요

**주의**: 이 분석은 참고용이며, 투자 결정은 개인의 판단과 책임입니다.
        """.trimIndent()
    }

    /**
     * ETF 통계 중심 분석 프롬프트
     */
    fun createEtfFocusedAnalysisPrompt(data: MarketAnalysisData): String {
        return """
한국 ETF 시장 분석가로서, ETF 편입/편출 데이터를 중심으로 ${data.market} 시장을 분석해주세요.

## ETF 포지션 변화
- 신규 편입: ${data.newStocks}개 (${formatAmount(data.newStocksAmount)}원)
- 편입 제외: ${data.removedStocks}개 (${formatAmount(data.removedStocksAmount)}원)
- 비중 증가: ${data.increasedStocks}개 (${formatAmount(data.increasedStocksAmount)}원)
- 비중 감소: ${data.decreasedStocks}개 (${formatAmount(data.decreasedStocksAmount)}원)
- 원화예금 변화: ${formatPercent(data.cashDepositChangeRate)}%

## 분석 포인트
1. 순 편입(신규-제외)이 양수면 기관의 낙관적 전망
2. 비중 증가 종목 수가 많으면 공격적 매수
3. 원화예금 증가는 방어적 포지션, 감소는 공격적 포지션

위 데이터만으로 ${data.market} 지수의 향후 방향성과 투자 신호를 JSON 형식으로 제공해주세요.
        """.trimIndent()
    }

    /**
     * 빠른 신호 생성 프롬프트 (간소화 버전)
     */
    fun createQuickSignalPrompt(data: MarketAnalysisData): String {
        val netInflow = (data.newStocks - data.removedStocks)
        val netAmount = (data.newStocksAmount - data.removedStocksAmount)
        val netIncreased = (data.increasedStocks - data.decreasedStocks)

        return """
${data.market} 시장 빠른 분석:

순 편입: ${netInflow}개 (${formatAmount(netAmount)}원)
순 비중 증가: ${netIncreased}개
원화예금 변화: ${formatPercent(data.cashDepositChangeRate)}%
${if (data.fearGreedValue != null) "Fear&Greed: ${formatPercent(data.fearGreedValue * 100)}" else ""}

30단어 이내로 매수/매도/중립 신호와 핵심 이유를 JSON으로 답변해주세요:
{"signal": "...", "reasoning": "..."}
        """.trimIndent()
    }

    /**
     * 백테스팅 결과 포함 프롬프트
     */
    fun createBacktestEnhancedPrompt(data: MarketAnalysisData, backtestResult: BacktestResult): String {
        return """
${createComprehensiveAnalysisPrompt(data)}

## 🔍 과거 신호 정확도 참고
- **분석 기간**: ${backtestResult.period}
- **총 신호**: ${backtestResult.totalSignals}회
- **정확도**: ${formatPercent(backtestResult.accuracy)}%
- **평균 수익률**: ${formatPercent(backtestResult.averageReturn)}%
- **승률**: ${formatPercent(backtestResult.winRate)}%

위 과거 정확도를 감안하여 현재 신호의 신뢰도를 조정해주세요.
        """.trimIndent()
    }

    // ========== Helper Functions ==========

    private fun formatNumber(value: Double): String {
        return String.format("%.2f", value)
    }

    private fun formatPercent(value: Double): String {
        return String.format("%+.2f", value)
    }

    private fun formatAmount(value: Long): String {
        return when {
            value >= 1_000_000_000_000 -> String.format("%.1f조", value / 1_000_000_000_000.0)
            value >= 100_000_000 -> String.format("%.0f억", value / 100_000_000.0)
            value >= 10_000 -> String.format("%.0f만", value / 10_000.0)
            else -> String.format("%,d", value)
        }
    }

    private fun interpretFearGreed(value: Double): String {
        return when {
            value >= 0.8 -> "극단적 탐욕 (반전 주의)"
            value >= 0.6 -> "탐욕 (과열 가능성)"
            value >= 0.4 -> "중립"
            value >= 0.2 -> "공포 (저점 근접)"
            else -> "극단적 공포 (반등 가능성)"
        }
    }

    private fun interpretOscillator(value: Double): String {
        return when {
            value > 70 -> "과매수 (조정 가능성)"
            value > 50 -> "강세 (상승 추세)"
            value > 30 -> "약세 (하락 추세)"
            else -> "과매도 (반등 가능성)"
        }
    }

    private fun interpretCorrelation(value: Double): String {
        return when {
            value >= 0.7 -> "강한 양의 상관관계"
            value >= 0.4 -> "중간 양의 상관관계"
            value >= 0.1 -> "약한 양의 상관관계"
            value > -0.1 -> "상관관계 없음"
            value > -0.4 -> "약한 음의 상관관계"
            value > -0.7 -> "중간 음의 상관관계"
            else -> "강한 음의 상관관계"
        }
    }
}
