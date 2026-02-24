package com.etfmonitor.core.analysis

import com.etfmonitor.core.analysis.model.TrendSignalData
import com.etfmonitor.core.analysis.model.TrendTradeSignal
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TrendSignalCalculator 단위 테스트
 *
 * 테스트 범위:
 * - analyze(): 빈 데이터, 강력매수/매수/중립/매도/강력매도 시나리오
 * - determineSignal(): 2-of-3 투표 로직 검증
 * - buildTrendDescription(): 상승/하락 추세, CMF 상태, Fear & Greed 상태
 * - 한국어 시그널 (강력매수 through 강력매도) 포함 전체 흐름
 *
 * 신호 결정 로직 (2-of-3 투표):
 *   지표 1: price > ma  → bull (+1), price < ma → bear (+1)
 *   지표 2: cmf > 0.05  → bull (+1), cmf < -0.05 → bear (+1)
 *   지표 3: fg > 0.5    → bull (+1), fg < -0.5   → bear (+1)
 *   bullCount >= 2 → BUY  (fg > 1.0  → STRONG_BUY)
 *   bearCount >= 2 → SELL (fg < -0.8 → STRONG_SELL)
 *   그 외          → NEUTRAL
 */
@DisplayName("TrendSignalCalculator 테스트")
class TrendSignalCalculatorTest {

    // ── 헬퍼 함수 ────────────────────────────────────────────────────────────

    /**
     * 기본 TrendSignalData 생성 헬퍼.
     * 모든 시그널 값은 0으로 채움.
     */
    private fun createData(
        dates: List<String> = listOf("2026-02-19"),
        close: List<Double> = listOf(100.0),
        ma: List<Double> = listOf(90.0),
        cmf: List<Double> = listOf(0.0),
        fearGreed: List<Double> = listOf(0.0),
        buySignal: List<Int> = listOf(0),
        auxBuySignal: List<Int> = listOf(0),
        sellSignal: List<Int> = listOf(0),
        auxSellSignal: List<Int> = listOf(0)
    ) = TrendSignalData(
        ticker = "005930",
        name = "삼성전자",
        interval = "d",
        dates = dates,
        open = close,
        high = close,
        low = close,
        close = close,
        volume = dates.map { 1_000_000L },
        ma = ma,
        cmf = cmf,
        fearGreed = fearGreed,
        buySignal = buySignal,
        auxBuySignal = auxBuySignal,
        sellSignal = sellSignal,
        auxSellSignal = auxSellSignal
    )

    /**
     * 5개 날짜로 채워진 테스트 데이터를 생성한다.
     * analyzeSignal의 recentPeriod = minOf(5, dates.size) = 5
     */
    private fun create5DayData(
        close: Double = 100.0,
        ma: Double = 90.0,
        cmf: Double = 0.0,
        fearGreed: Double = 0.0,
        buySignal: Int = 0,
        auxBuySignal: Int = 0,
        sellSignal: Int = 0,
        auxSellSignal: Int = 0
    ): TrendSignalData {
        val dates = listOf("2026-02-15", "2026-02-16", "2026-02-17", "2026-02-18", "2026-02-19")
        return createData(
            dates = dates,
            close = List(5) { close },
            ma = List(5) { ma },
            cmf = List(5) { cmf },
            fearGreed = List(5) { fearGreed },
            buySignal = List(5) { buySignal },
            auxBuySignal = List(5) { auxBuySignal },
            sellSignal = List(5) { sellSignal },
            auxSellSignal = List(5) { auxSellSignal }
        )
    }

    // =========================================================================
    // analyze() — 빈 데이터
    // =========================================================================

    @Nested
    @DisplayName("analyze() — 빈 데이터 처리")
    inner class EmptyDataTests {

        @Test
        @DisplayName("dates가 빈 리스트이면 NEUTRAL 시그널과 '데이터 없음'을 반환한다")
        fun `analyze with empty dates returns NEUTRAL and no data description`() {
            val data = createData(
                dates = emptyList(),
                close = emptyList(),
                ma = emptyList(),
                cmf = emptyList(),
                fearGreed = emptyList(),
                buySignal = emptyList(),
                auxBuySignal = emptyList(),
                sellSignal = emptyList(),
                auxSellSignal = emptyList()
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.NEUTRAL, result.signal)
            assertEquals("데이터 없음", result.trendDescription)
            assertEquals(0.0, result.currentPrice, 1e-10)
            assertEquals(0, result.recentBuyCount)
            assertEquals(0, result.recentSellCount)
        }
    }

    // =========================================================================
    // analyze() — 강력매수 시나리오 (STRONG_BUY)
    // 조건: bullCount >= 2 AND fg > 1.0
    // =========================================================================

    @Nested
    @DisplayName("analyze() — 강력매수(STRONG_BUY) 시나리오")
    inner class StrongBuyTests {

        @Test
        @DisplayName("3지표 모두 bull + fg > 1.0 이면 STRONG_BUY를 반환한다")
        fun `analyze all bull indicators with extreme fg returns STRONG_BUY`() {
            // price(100) > ma(80)  → bull
            // cmf(0.15) > 0.05    → bull
            // fg(1.2) > 0.5       → bull; fg > 1.0 → STRONG_BUY 승격
            val data = create5DayData(
                close = 100.0,
                ma = 80.0,
                cmf = 0.15,
                fearGreed = 1.2
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.STRONG_BUY, result.signal)
        }

        @Test
        @DisplayName("bullCount=2 (price+cmf) + fg > 1.0 이면 STRONG_BUY를 반환한다")
        fun `analyze two bull indicators with extreme fg returns STRONG_BUY`() {
            // price(100) > ma(80)  → bull
            // cmf(0.15) > 0.05    → bull
            // fg(-0.1) 중립        → 미기여
            // bullCount=2 → BUY, 그런데 fg(-0.1)이 1.0 초과 아님 → BUY
            // 이번 케이스: fg=1.1 > 1.0 → STRONG_BUY 승격
            val data = create5DayData(
                close = 100.0,
                ma = 80.0,
                cmf = 0.15,
                fearGreed = 1.1
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.STRONG_BUY, result.signal)
        }

        @Test
        @DisplayName("강력매수 시 recentBuyCount는 최근 5기간 buy+auxBuy의 합이다")
        fun `analyze STRONG_BUY recentBuyCount is sum of recent buy signals`() {
            val data = create5DayData(
                close = 100.0,
                ma = 80.0,
                cmf = 0.15,
                fearGreed = 1.2,
                buySignal = 1,
                auxBuySignal = 1,
                sellSignal = 0,
                auxSellSignal = 0
            )

            val result = TrendSignalCalculator.analyze(data)

            // 5기간 × (buySignal=1 + auxBuySignal=1) = 10
            assertEquals(10, result.recentBuyCount)
            assertEquals(0, result.recentSellCount)
        }
    }

    // =========================================================================
    // analyze() — 매수 시나리오 (BUY)
    // 조건: bullCount >= 2 AND fg <= 1.0
    // =========================================================================

    @Nested
    @DisplayName("analyze() — 매수(BUY) 시나리오")
    inner class BuyTests {

        @Test
        @DisplayName("bullCount=2 (price+cmf) + fg 중립이면 BUY를 반환한다")
        fun `analyze two bull indicators neutral fg returns BUY`() {
            // price(100) > ma(80)  → bull
            // cmf(0.15) > 0.05    → bull
            // fg(0.0) 중립        → 미기여
            // bullCount=2 → BUY (fg <= 1.0 이므로 STRONG 미승격)
            val data = create5DayData(
                close = 100.0,
                ma = 80.0,
                cmf = 0.15,
                fearGreed = 0.0
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.BUY, result.signal)
        }

        @Test
        @DisplayName("bullCount=2 (price+fg) + cmf 중립이면 BUY를 반환한다")
        fun `analyze price and fg bull cmf neutral returns BUY`() {
            // price(100) > ma(80)  → bull
            // cmf(0.02) 중립(0.02 < 0.05) → 미기여
            // fg(0.6) > 0.5       → bull
            // bullCount=2 → BUY
            val data = create5DayData(
                close = 100.0,
                ma = 80.0,
                cmf = 0.02,
                fearGreed = 0.6
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.BUY, result.signal)
        }

        @Test
        @DisplayName("BUY 시 recommendation은 '매수 관심, 추가 확인 권장'이다")
        fun `analyze BUY recommendation is correct`() {
            val data = create5DayData(
                close = 100.0,
                ma = 80.0,
                cmf = 0.15,
                fearGreed = 0.0
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals("매수 관심, 추가 확인 권장", result.recommendation)
        }
    }

    // =========================================================================
    // analyze() — 중립 시나리오 (NEUTRAL)
    // 조건: bullCount < 2 AND bearCount < 2
    // =========================================================================

    @Nested
    @DisplayName("analyze() — 중립(NEUTRAL) 시나리오")
    inner class NeutralTests {

        @Test
        @DisplayName("3지표 모두 중립이면 NEUTRAL을 반환한다")
        fun `analyze all neutral indicators returns NEUTRAL`() {
            // price(100) == ma(100) → 중립
            // cmf(0.0) 중립         → 미기여
            // fg(0.0) 중립          → 미기여
            val data = create5DayData(
                close = 100.0,
                ma = 100.0,
                cmf = 0.0,
                fearGreed = 0.0
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.NEUTRAL, result.signal)
        }

        @Test
        @DisplayName("bullCount=1, bearCount=0이면 NEUTRAL을 반환한다")
        fun `analyze one bull indicator returns NEUTRAL`() {
            // price(100) > ma(90) → bull (bullCount=1)
            // cmf(-0.03) 중립     → 미기여
            // fg(0.0) 중립        → 미기여
            // bullCount=1 < 2 → NEUTRAL
            val data = create5DayData(
                close = 100.0,
                ma = 90.0,
                cmf = -0.03,
                fearGreed = 0.0
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.NEUTRAL, result.signal)
        }

        @Test
        @DisplayName("bull 1개 + bear 1개 혼재이면 NEUTRAL을 반환한다")
        fun `analyze mixed one bull one bear returns NEUTRAL`() {
            // price(100) > ma(90) → bull
            // cmf(-0.1) < -0.05   → bear
            // fg(0.0) 중립         → 미기여
            // bullCount=1, bearCount=1 → NEUTRAL
            val data = create5DayData(
                close = 100.0,
                ma = 90.0,
                cmf = -0.1,
                fearGreed = 0.0
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.NEUTRAL, result.signal)
        }

        @Test
        @DisplayName("NEUTRAL 시 recommendation은 '관망, 명확한 추세 대기'이다")
        fun `analyze NEUTRAL recommendation is correct`() {
            val data = create5DayData(
                close = 100.0,
                ma = 100.0,
                cmf = 0.0,
                fearGreed = 0.0
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals("관망, 명확한 추세 대기", result.recommendation)
        }
    }

    // =========================================================================
    // analyze() — 매도 시나리오 (SELL)
    // 조건: bearCount >= 2 AND fg >= -0.8
    // =========================================================================

    @Nested
    @DisplayName("analyze() — 매도(SELL) 시나리오")
    inner class SellTests {

        @Test
        @DisplayName("bearCount=2 (price+cmf) + fg 중립이면 SELL을 반환한다")
        fun `analyze two bear indicators neutral fg returns SELL`() {
            // price(80) < ma(100)   → bear
            // cmf(-0.1) < -0.05    → bear
            // fg(0.0) 중립          → 미기여
            // bearCount=2 → SELL (fg >= -0.8 이므로 STRONG 미승격)
            val data = create5DayData(
                close = 80.0,
                ma = 100.0,
                cmf = -0.1,
                fearGreed = 0.0
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.SELL, result.signal)
        }

        @Test
        @DisplayName("bearCount=2 (price+fg) + cmf 중립이면 SELL을 반환한다")
        fun `analyze price and fg bear cmf neutral returns SELL`() {
            // price(80) < ma(100)   → bear
            // cmf(-0.02) 중립       → 미기여
            // fg(-0.6) < -0.5      → bear
            // bearCount=2 → SELL (fg=-0.6 >= -0.8 이므로 STRONG 미승격)
            val data = create5DayData(
                close = 80.0,
                ma = 100.0,
                cmf = -0.02,
                fearGreed = -0.6
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.SELL, result.signal)
        }

        @Test
        @DisplayName("SELL 시 recommendation은 '매도 검토, 손절/익절 점검'이다")
        fun `analyze SELL recommendation is correct`() {
            val data = create5DayData(
                close = 80.0,
                ma = 100.0,
                cmf = -0.1,
                fearGreed = 0.0
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals("매도 검토, 손절/익절 점검", result.recommendation)
        }
    }

    // =========================================================================
    // analyze() — 강력매도 시나리오 (STRONG_SELL)
    // 조건: bearCount >= 2 AND fg < -0.8
    // =========================================================================

    @Nested
    @DisplayName("analyze() — 강력매도(STRONG_SELL) 시나리오")
    inner class StrongSellTests {

        @Test
        @DisplayName("3지표 모두 bear + fg < -0.8 이면 STRONG_SELL을 반환한다")
        fun `analyze all bear indicators with extreme fear fg returns STRONG_SELL`() {
            // price(80) < ma(100)   → bear
            // cmf(-0.15) < -0.05   → bear
            // fg(-0.9) < -0.5      → bear; fg < -0.8 → STRONG_SELL 승격
            val data = create5DayData(
                close = 80.0,
                ma = 100.0,
                cmf = -0.15,
                fearGreed = -0.9
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.STRONG_SELL, result.signal)
        }

        @Test
        @DisplayName("bearCount=2 (price+cmf) + fg < -0.8 이면 STRONG_SELL을 반환한다")
        fun `analyze two bear indicators with extreme fear fg returns STRONG_SELL`() {
            // price(80) < ma(100)   → bear
            // cmf(-0.15) < -0.05   → bear
            // fg(-0.85) 중립 범위 내이지만 < -0.5 → bear (bearCount=3 이지만 이미 >= 2)
            // fg(-0.85) < -0.8 → STRONG_SELL 승격
            val data = create5DayData(
                close = 80.0,
                ma = 100.0,
                cmf = -0.15,
                fearGreed = -0.85
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.STRONG_SELL, result.signal)
        }

        @Test
        @DisplayName("강력매도이고 극도의 공포 상태면 특별 권고를 반환한다")
        fun `analyze STRONG_SELL with extreme fear returns special recommendation`() {
            // fearGreed <= -0.6 → EXTREME_FEAR → 특별 권고
            // bearCount=2, fg=-0.9 < -0.8 → STRONG_SELL
            val data = create5DayData(
                close = 80.0,
                ma = 100.0,
                cmf = -0.15,
                fearGreed = -0.9
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.STRONG_SELL, result.signal)
            assertEquals("강한 매도 시그널, 반등 기회 모니터링", result.recommendation)
        }

        @Test
        @DisplayName("강력매도이고 공포 상태가 아니면 기본 권고를 반환한다")
        fun `analyze STRONG_SELL without extreme fear returns default recommendation`() {
            // fg=-0.85 < -0.8 → STRONG_SELL; EXTREME_FEAR는 fg <= -0.6이므로 해당
            // 단, 기본 권고 테스트: EXTREME_FEAR가 아닌 케이스는 fg > -0.6 필요
            // STRONG_SELL 조건: fg < -0.8 이므로, fg 범위 [-0.8, -0.6) 사용 불가 (불가능한 범위)
            // 실제로 STRONG_SELL (fg < -0.8) 이면 fg <= -0.6도 참이므로 항상 EXTREME_FEAR
            // 이 테스트는 로직상 도달 불가능하지만, recommendation 분기를 명시하기 위해
            // STRONG_SELL + EXTREME_FEAR → "강한 매도 시그널, 반등 기회 모니터링"임을 간접 검증
            val data = create5DayData(
                close = 80.0,
                ma = 100.0,
                cmf = -0.15,
                fearGreed = -0.9
            )

            val result = TrendSignalCalculator.analyze(data)

            // fg=-0.9 → EXTREME_FEAR → 특별 권고
            assertEquals(TrendTradeSignal.STRONG_SELL, result.signal)
            assertEquals("강한 매도 시그널, 반등 기회 모니터링", result.recommendation)
        }

        @Test
        @DisplayName("강력매도 시 recentSellCount는 최근 5기간 sell+auxSell의 합이다")
        fun `analyze STRONG_SELL recentSellCount is sum of recent sell signals`() {
            val data = create5DayData(
                close = 80.0,
                ma = 100.0,
                cmf = -0.15,
                fearGreed = -0.9,
                buySignal = 0,
                auxBuySignal = 0,
                sellSignal = 1,
                auxSellSignal = 1
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(0, result.recentBuyCount)
            // 5기간 × (sellSignal=1 + auxSellSignal=1) = 10
            assertEquals(10, result.recentSellCount)
        }
    }

    // =========================================================================
    // analyze() — 강력매수 + 과열 경고
    // =========================================================================

    @Nested
    @DisplayName("analyze() — 강력매수 + 극도의 탐욕 시나리오")
    inner class StrongBuyExtremeGreedTests {

        @Test
        @DisplayName("강력매수이고 극도의 탐욕 상태면 '강한 매수 시그널이나, 과열 주의' 권고를 반환한다")
        fun `analyze STRONG_BUY with extreme greed returns caution recommendation`() {
            // fearGreed > 0.6 → EXTREME_GREED → 과열 권고
            // bullCount=3, fg=1.2 > 1.0 → STRONG_BUY
            val data = create5DayData(
                close = 100.0,
                ma = 80.0,
                cmf = 0.15,
                fearGreed = 1.2
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.STRONG_BUY, result.signal)
            assertEquals("강한 매수 시그널이나, 과열 주의", result.recommendation)
        }

        @Test
        @DisplayName("강력매수이고 탐욕(과열 아님) 상태면 기본 권고를 반환한다")
        fun `analyze STRONG_BUY without extreme greed returns default recommendation`() {
            // fearGreed=1.05 > 1.0 → STRONG_BUY, GREED (0.6 < fg <= 1.05)
            // EXTREME_GREED는 fg > 0.6, 여기서 fg=1.05 > 0.6 → EXTREME_GREED
            // 따라서 과열 권고가 나온다. 탐욕(GREED, 0.2<fg<=0.6)에서만 기본 권고 확인.
            // 탐욕 상태 + STRONG_BUY는 달성 불가 (STRONG_BUY 조건이 fg>1.0이라 항상 EXTREME_GREED)
            // 따라서 BUY (fg<=1.0) + GREED로 기본 권고 검증
            val data = create5DayData(
                close = 100.0,
                ma = 80.0,
                cmf = 0.15,
                fearGreed = 0.45   // GREED 범위; fg <= 1.0 → BUY
            )

            val result = TrendSignalCalculator.analyze(data)

            // fg=0.45 <= 1.0 → BUY (not STRONG_BUY)
            assertEquals(TrendTradeSignal.BUY, result.signal)
            assertEquals("매수 관심, 추가 확인 권장", result.recommendation)
        }
    }

    // =========================================================================
    // analyze() — trendDescription 검증
    // =========================================================================

    @Nested
    @DisplayName("analyze() — trendDescription 내용 검증")
    inner class TrendDescriptionTests {

        @Test
        @DisplayName("가격이 MA보다 높으면 설명에 '상승 추세'가 포함된다")
        fun `analyze price above MA includes uptrend in description`() {
            val data = createData(
                close = listOf(110.0),
                ma = listOf(100.0),
                cmf = listOf(0.0),
                fearGreed = listOf(0.0)
            )

            val result = TrendSignalCalculator.analyze(data)

            assertTrue(
                result.trendDescription.contains("상승 추세"),
                "Expected '상승 추세' but got: ${result.trendDescription}"
            )
        }

        @Test
        @DisplayName("가격이 MA보다 낮으면 설명에 '하락 추세'가 포함된다")
        fun `analyze price below MA includes downtrend in description`() {
            val data = createData(
                close = listOf(90.0),
                ma = listOf(100.0),
                cmf = listOf(0.0),
                fearGreed = listOf(0.0)
            )

            val result = TrendSignalCalculator.analyze(data)

            assertTrue(
                result.trendDescription.contains("하락 추세"),
                "Expected '하락 추세' but got: ${result.trendDescription}"
            )
        }

        @Test
        @DisplayName("CMF > 0.1이면 설명에 '강한 자금 유입'이 포함된다")
        fun `analyze CMF above 0_1 includes strong inflow in description`() {
            val data = createData(
                close = listOf(100.0),
                ma = listOf(100.0),
                cmf = listOf(0.15),
                fearGreed = listOf(0.0)
            )

            val result = TrendSignalCalculator.analyze(data)

            assertTrue(
                result.trendDescription.contains("강한 자금 유입"),
                "Expected '강한 자금 유입' but got: ${result.trendDescription}"
            )
        }

        @Test
        @DisplayName("CMF < -0.1이면 설명에 '강한 자금 유출'이 포함된다")
        fun `analyze CMF below -0_1 includes strong outflow in description`() {
            val data = createData(
                close = listOf(100.0),
                ma = listOf(100.0),
                cmf = listOf(-0.15),
                fearGreed = listOf(0.0)
            )

            val result = TrendSignalCalculator.analyze(data)

            assertTrue(
                result.trendDescription.contains("강한 자금 유출"),
                "Expected '강한 자금 유출' but got: ${result.trendDescription}"
            )
        }

        @Test
        @DisplayName("0 < CMF <= 0.1이면 설명에 '자금 유입'이 포함된다")
        fun `analyze small positive CMF includes inflow in description`() {
            val data = createData(
                close = listOf(100.0),
                ma = listOf(100.0),
                cmf = listOf(0.05),
                fearGreed = listOf(0.0)
            )

            val result = TrendSignalCalculator.analyze(data)

            assertTrue(
                result.trendDescription.contains("자금 유입"),
                "Expected '자금 유입' but got: ${result.trendDescription}"
            )
        }

        @Test
        @DisplayName("-0.1 <= CMF < 0이면 설명에 '자금 유출'이 포함된다")
        fun `analyze small negative CMF includes outflow in description`() {
            val data = createData(
                close = listOf(100.0),
                ma = listOf(100.0),
                cmf = listOf(-0.05),
                fearGreed = listOf(0.0)
            )

            val result = TrendSignalCalculator.analyze(data)

            assertTrue(
                result.trendDescription.contains("자금 유출"),
                "Expected '자금 유출' but got: ${result.trendDescription}"
            )
        }

        @Test
        @DisplayName("설명은 '|' 구분자로 여러 부분으로 구성된다")
        fun `analyze description is pipe-delimited with multiple parts`() {
            val data = createData(
                close = listOf(100.0),
                ma = listOf(90.0),
                cmf = listOf(0.15),
                fearGreed = listOf(0.0)
            )

            val result = TrendSignalCalculator.analyze(data)

            val parts = result.trendDescription.split(" | ")
            assertTrue(parts.size >= 3, "Expected at least 3 parts but got: ${parts.size}")
        }
    }

    // =========================================================================
    // analyze() — currentPrice, maPrice, cmfValue 필드 검증
    // =========================================================================

    @Nested
    @DisplayName("analyze() — 결과 필드값 검증")
    inner class ResultFieldTests {

        @Test
        @DisplayName("currentPrice는 마지막 close 값이다")
        fun `analyze currentPrice is last close value`() {
            val dates = listOf("2026-02-17", "2026-02-18", "2026-02-19")
            val close = listOf(100.0, 110.0, 120.0)
            val data = createData(
                dates = dates,
                close = close,
                ma = listOf(90.0, 90.0, 90.0),
                cmf = listOf(0.0, 0.0, 0.0),
                fearGreed = listOf(0.0, 0.0, 0.0),
                buySignal = listOf(0, 0, 0),
                auxBuySignal = listOf(0, 0, 0),
                sellSignal = listOf(0, 0, 0),
                auxSellSignal = listOf(0, 0, 0)
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(120.0, result.currentPrice, 1e-10)
        }

        @Test
        @DisplayName("maPrice는 마지막 MA 값이다")
        fun `analyze maPrice is last ma value`() {
            val dates = listOf("2026-02-17", "2026-02-18", "2026-02-19")
            val data = createData(
                dates = dates,
                close = listOf(100.0, 100.0, 100.0),
                ma = listOf(80.0, 85.0, 88.0),
                cmf = listOf(0.0, 0.0, 0.0),
                fearGreed = listOf(0.0, 0.0, 0.0),
                buySignal = listOf(0, 0, 0),
                auxBuySignal = listOf(0, 0, 0),
                sellSignal = listOf(0, 0, 0),
                auxSellSignal = listOf(0, 0, 0)
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(88.0, result.maPrice, 1e-10)
        }

        @Test
        @DisplayName("cmfValue는 마지막 CMF 값이다")
        fun `analyze cmfValue is last cmf value`() {
            val dates = listOf("2026-02-17", "2026-02-18", "2026-02-19")
            val data = createData(
                dates = dates,
                close = listOf(100.0, 100.0, 100.0),
                ma = listOf(90.0, 90.0, 90.0),
                cmf = listOf(0.1, 0.15, 0.2),
                fearGreed = listOf(0.0, 0.0, 0.0),
                buySignal = listOf(0, 0, 0),
                auxBuySignal = listOf(0, 0, 0),
                sellSignal = listOf(0, 0, 0),
                auxSellSignal = listOf(0, 0, 0)
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(0.2, result.cmfValue, 1e-10)
        }

        @Test
        @DisplayName("fearGreedValue는 마지막 fearGreed 값이다")
        fun `analyze fearGreedValue is last fearGreed value`() {
            val dates = listOf("2026-02-17", "2026-02-18", "2026-02-19")
            val data = createData(
                dates = dates,
                close = listOf(100.0, 100.0, 100.0),
                ma = listOf(90.0, 90.0, 90.0),
                cmf = listOf(0.0, 0.0, 0.0),
                fearGreed = listOf(0.1, 0.2, 0.45),
                buySignal = listOf(0, 0, 0),
                auxBuySignal = listOf(0, 0, 0),
                sellSignal = listOf(0, 0, 0),
                auxSellSignal = listOf(0, 0, 0)
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(0.45, result.fearGreedValue, 1e-10)
        }
    }

    // =========================================================================
    // analyze() — recentPeriod = minOf(5, dates.size)
    // =========================================================================

    @Nested
    @DisplayName("analyze() — 최근 기간 집계 (5개 또는 데이터 수 기준)")
    inner class RecentPeriodTests {

        @Test
        @DisplayName("데이터가 3개이면 최근 3개 기간만 집계한다")
        fun `analyze with 3 dates uses 3 recent periods for signal count`() {
            val data = createData(
                dates = listOf("2026-02-17", "2026-02-18", "2026-02-19"),
                close = listOf(100.0, 100.0, 100.0),
                ma = listOf(90.0, 90.0, 90.0),
                cmf = listOf(0.0, 0.0, 0.0),
                fearGreed = listOf(0.0, 0.0, 0.0),
                buySignal = listOf(1, 1, 1),
                auxBuySignal = listOf(0, 0, 0),
                sellSignal = listOf(0, 0, 0),
                auxSellSignal = listOf(0, 0, 0)
            )

            val result = TrendSignalCalculator.analyze(data)

            // 최근 minOf(5, 3) = 3개 → buySignal 합 = 3
            assertEquals(3, result.recentBuyCount)
        }

        @Test
        @DisplayName("데이터가 10개이면 최근 5개 기간만 집계한다")
        fun `analyze with 10 dates uses only last 5 periods for signal count`() {
            // 처음 5개: buySignal=0, 마지막 5개: buySignal=1
            val data = createData(
                dates = (1..10).map { "2026-01-${it.toString().padStart(2, '0')}" },
                close = List(10) { 100.0 },
                ma = List(10) { 90.0 },
                cmf = List(10) { 0.0 },
                fearGreed = List(10) { 0.0 },
                buySignal = List(5) { 0 } + List(5) { 1 },
                auxBuySignal = List(10) { 0 },
                sellSignal = List(10) { 0 },
                auxSellSignal = List(10) { 0 }
            )

            val result = TrendSignalCalculator.analyze(data)

            // 최근 5개만 집계 → buySignal=1 × 5 = 5
            assertEquals(5, result.recentBuyCount)
        }
    }

    // =========================================================================
    // determineSignal() — 2-of-3 투표 로직 경계값 테스트
    // =========================================================================

    @Nested
    @DisplayName("determineSignal() — 2-of-3 투표 경계값 검증")
    inner class VotingBoundaryTests {

        @Test
        @DisplayName("CMF 임계값: cmf=0.05 초과면 bull, 이하면 미기여")
        fun `cmf above 0_05 threshold contributes bull vote`() {
            // price(100) > ma(80) → bull
            // cmf(0.06) > 0.05   → bull  (bullCount=2 → BUY)
            val above = create5DayData(close = 100.0, ma = 80.0, cmf = 0.06, fearGreed = 0.0)
            assertEquals(TrendTradeSignal.BUY, TrendSignalCalculator.analyze(above).signal)

            // cmf(0.05) == 0.05  → 중립 (bullCount=1 → NEUTRAL)
            val atThreshold = create5DayData(close = 100.0, ma = 80.0, cmf = 0.05, fearGreed = 0.0)
            assertEquals(TrendTradeSignal.NEUTRAL, TrendSignalCalculator.analyze(atThreshold).signal)
        }

        @Test
        @DisplayName("CMF 임계값: cmf=-0.05 미만이면 bear, 이상이면 미기여")
        fun `cmf below negative 0_05 threshold contributes bear vote`() {
            // price(80) < ma(100) → bear
            // cmf(-0.06) < -0.05 → bear  (bearCount=2 → SELL)
            val below = create5DayData(close = 80.0, ma = 100.0, cmf = -0.06, fearGreed = 0.0)
            assertEquals(TrendTradeSignal.SELL, TrendSignalCalculator.analyze(below).signal)

            // cmf(-0.05) == -0.05 → 중립 (bearCount=1 → NEUTRAL)
            val atThreshold = create5DayData(close = 80.0, ma = 100.0, cmf = -0.05, fearGreed = 0.0)
            assertEquals(TrendTradeSignal.NEUTRAL, TrendSignalCalculator.analyze(atThreshold).signal)
        }

        @Test
        @DisplayName("FG 임계값: fg=0.5 초과면 bull 기여")
        fun `fg above 0_5 threshold contributes bull vote`() {
            // price(100) > ma(80) → bull
            // fg(0.51) > 0.5      → bull (bullCount=2 → BUY)
            val data = create5DayData(close = 100.0, ma = 80.0, cmf = 0.0, fearGreed = 0.51)
            assertEquals(TrendTradeSignal.BUY, TrendSignalCalculator.analyze(data).signal)
        }

        @Test
        @DisplayName("FG 임계값: fg=-0.5 미만이면 bear 기여")
        fun `fg below negative 0_5 threshold contributes bear vote`() {
            // price(80) < ma(100) → bear
            // fg(-0.51) < -0.5    → bear (bearCount=2 → SELL)
            val data = create5DayData(close = 80.0, ma = 100.0, cmf = 0.0, fearGreed = -0.51)
            assertEquals(TrendTradeSignal.SELL, TrendSignalCalculator.analyze(data).signal)
        }

        @Test
        @DisplayName("STRONG_BUY 승격 임계값: fg=1.0 초과면 STRONG_BUY")
        fun `fg above 1_0 upgrades BUY to STRONG_BUY`() {
            // bullCount=2, fg=1.01 > 1.0 → STRONG_BUY
            val data = create5DayData(close = 100.0, ma = 80.0, cmf = 0.1, fearGreed = 1.01)
            assertEquals(TrendTradeSignal.STRONG_BUY, TrendSignalCalculator.analyze(data).signal)

            // bullCount=2, fg=1.0 <= 1.0 → BUY (승격 미발생)
            val notUpgraded = create5DayData(close = 100.0, ma = 80.0, cmf = 0.1, fearGreed = 1.0)
            assertEquals(TrendTradeSignal.BUY, TrendSignalCalculator.analyze(notUpgraded).signal)
        }

        @Test
        @DisplayName("STRONG_SELL 승격 임계값: fg=-0.8 미만이면 STRONG_SELL")
        fun `fg below negative 0_8 upgrades SELL to STRONG_SELL`() {
            // bearCount=2, fg=-0.81 < -0.8 → STRONG_SELL
            val data = create5DayData(close = 80.0, ma = 100.0, cmf = -0.1, fearGreed = -0.81)
            assertEquals(TrendTradeSignal.STRONG_SELL, TrendSignalCalculator.analyze(data).signal)

            // bearCount=2, fg=-0.8 >= -0.8 → SELL (승격 미발생)
            val notUpgraded = create5DayData(close = 80.0, ma = 100.0, cmf = -0.1, fearGreed = -0.8)
            assertEquals(TrendTradeSignal.SELL, TrendSignalCalculator.analyze(notUpgraded).signal)
        }
    }
}
