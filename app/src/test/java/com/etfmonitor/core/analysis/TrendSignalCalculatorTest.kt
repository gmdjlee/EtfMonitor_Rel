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
 * - determineSignal(): 점수 경계값 (±70, ±30) 검증
 * - buildTrendDescription(): 상승/하락 추세, CMF 상태, Fear & Greed 상태
 * - 한국어 시그널 (강력매수 through 강력매도) 포함 전체 흐름
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
    // =========================================================================

    @Nested
    @DisplayName("analyze() — 강력매수(STRONG_BUY) 시나리오")
    inner class StrongBuyTests {

        @Test
        @DisplayName("모든 조건이 최대 매수면 STRONG_BUY(강력매수)를 반환한다")
        fun `analyze all max buy conditions returns STRONG_BUY`() {
            // score 계산:
            // 1. 가격(100) > MA(80) → +30
            // 2. CMF(0.15) > 0.1 → +30
            // 3. fearGreed(0.5) > 0.4 → +20
            // 4. signalDiff = (buySignal*5 + auxBuy*5) - 0 = 10 - 0 ≥ 2 → +20
            // total = 100 ≥ 70 → STRONG_BUY
            val data = create5DayData(
                close = 100.0,
                ma = 80.0,
                cmf = 0.15,
                fearGreed = 0.5,
                buySignal = 1,
                auxBuySignal = 1,
                sellSignal = 0,
                auxSellSignal = 0
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
                fearGreed = 0.5,
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
    // =========================================================================

    @Nested
    @DisplayName("analyze() — 매수(BUY) 시나리오")
    inner class BuyTests {

        @Test
        @DisplayName("score 30~69 범위이면 BUY(매수)를 반환한다")
        fun `analyze score 30 to 69 returns BUY`() {
            // score:
            // 1. 가격(100) > MA(80) → +30
            // 2. CMF(0.05) > 0 but ≤ 0.1 → +15
            // 3. fearGreed(0.0) = 0 (neither >0 nor <0 → 0)
            // 4. signalDiff=0 → 0
            // total = 45 → BUY
            val data = create5DayData(
                close = 100.0,
                ma = 80.0,
                cmf = 0.05,
                fearGreed = 0.0,
                buySignal = 0,
                sellSignal = 0
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
                cmf = 0.05,
                fearGreed = 0.0
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals("매수 관심, 추가 확인 권장", result.recommendation)
        }
    }

    // =========================================================================
    // analyze() — 중립 시나리오 (NEUTRAL)
    // =========================================================================

    @Nested
    @DisplayName("analyze() — 중립(NEUTRAL) 시나리오")
    inner class NeutralTests {

        @Test
        @DisplayName("score -29 ~ +29 범위이면 NEUTRAL(중립)을 반환한다")
        fun `analyze score -29 to 29 returns NEUTRAL`() {
            // score:
            // 1. 가격(100) > MA(95) → +30
            // 2. CMF(0.0) = 0 (neither >0 nor <0 → 0)
            // 3. fearGreed(0.0) = 0
            // 4. signalDiff=0 → 0
            // total = 30 → BUY
            // To get NEUTRAL, use price below MA:
            // 1. 가격(80) < MA(100) → -30
            // 2. CMF(0.0) → 0
            // 3. fearGreed(0.0) → 0
            // 4. signalDiff=0 → 0
            // total = -30 → SELL (not NEUTRAL)
            // Need score in -29..+29:
            // 1. 가격 == MA (score 0) + CMF=0 + fg=0 + diff=0 = 0 → NEUTRAL
            val data = create5DayData(
                close = 100.0,
                ma = 100.0,  // equal → score=0
                cmf = 0.0,
                fearGreed = 0.0,
                buySignal = 0,
                sellSignal = 0
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
    // =========================================================================

    @Nested
    @DisplayName("analyze() — 매도(SELL) 시나리오")
    inner class SellTests {

        @Test
        @DisplayName("score -30 ~ -69 범위이면 SELL(매도)를 반환한다")
        fun `analyze score -30 to -69 returns SELL`() {
            // score:
            // 1. 가격(80) < MA(100) → -30
            // 2. CMF(-0.05) < 0 but ≥ -0.1 → -15
            // 3. fearGreed(0.0) → 0
            // 4. signalDiff=0 → 0
            // total = -45 → SELL
            val data = create5DayData(
                close = 80.0,
                ma = 100.0,
                cmf = -0.05,
                fearGreed = 0.0,
                buySignal = 0,
                sellSignal = 0
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
                cmf = -0.05,
                fearGreed = 0.0
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals("매도 검토, 손절/익절 점검", result.recommendation)
        }
    }

    // =========================================================================
    // analyze() — 강력매도 시나리오 (STRONG_SELL)
    // =========================================================================

    @Nested
    @DisplayName("analyze() — 강력매도(STRONG_SELL) 시나리오")
    inner class StrongSellTests {

        @Test
        @DisplayName("모든 조건이 최대 매도면 STRONG_SELL(강력매도)을 반환한다")
        fun `analyze all max sell conditions returns STRONG_SELL`() {
            // score:
            // 1. 가격(80) < MA(100) → -30
            // 2. CMF(-0.15) < -0.1 → -30
            // 3. fearGreed(-0.5) < -0.4 → -20
            // 4. signalDiff = 0 - (5+5) = -10 ≤ -2 → -20
            // total = -100 ≤ -70 → STRONG_SELL
            val data = create5DayData(
                close = 80.0,
                ma = 100.0,
                cmf = -0.15,
                fearGreed = -0.5,
                buySignal = 0,
                auxBuySignal = 0,
                sellSignal = 1,
                auxSellSignal = 1
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.STRONG_SELL, result.signal)
        }

        @Test
        @DisplayName("강력매도이고 극도의 공포 상태면 특별 권고를 반환한다")
        fun `analyze STRONG_SELL with extreme fear returns special recommendation`() {
            // fearGreed <= -0.6 → EXTREME_FEAR
            val data = create5DayData(
                close = 80.0,
                ma = 100.0,
                cmf = -0.15,
                fearGreed = -0.7,
                buySignal = 0,
                auxBuySignal = 0,
                sellSignal = 1,
                auxSellSignal = 1
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.STRONG_SELL, result.signal)
            assertEquals("강한 매도 시그널, 반등 기회 모니터링", result.recommendation)
        }

        @Test
        @DisplayName("강력매도이고 공포 상태가 아니면 기본 권고를 반환한다")
        fun `analyze STRONG_SELL without extreme fear returns default recommendation`() {
            // fearGreed = -0.2 → FEAR (not EXTREME_FEAR)
            // score: -30 + -30 + -10 + -20 = -90 → STRONG_SELL
            val data = create5DayData(
                close = 80.0,
                ma = 100.0,
                cmf = -0.15,
                fearGreed = -0.2,
                buySignal = 0,
                auxBuySignal = 0,
                sellSignal = 1,
                auxSellSignal = 1
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.STRONG_SELL, result.signal)
            assertEquals("적극 매도 검토", result.recommendation)
        }

        @Test
        @DisplayName("강력매도 시 recentSellCount는 최근 5기간 sell+auxSell의 합이다")
        fun `analyze STRONG_SELL recentSellCount is sum of recent sell signals`() {
            val data = create5DayData(
                close = 80.0,
                ma = 100.0,
                cmf = -0.15,
                fearGreed = -0.5,
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
            // fearGreed > 0.6 → EXTREME_GREED
            // score: +30 + +30 + +20 + +20 = 100 → STRONG_BUY
            val data = create5DayData(
                close = 100.0,
                ma = 80.0,
                cmf = 0.15,
                fearGreed = 0.7,
                buySignal = 1,
                auxBuySignal = 1,
                sellSignal = 0,
                auxSellSignal = 0
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.STRONG_BUY, result.signal)
            assertEquals("강한 매수 시그널이나, 과열 주의", result.recommendation)
        }

        @Test
        @DisplayName("강력매수이고 탐욕(과열 아님) 상태면 기본 권고를 반환한다")
        fun `analyze STRONG_BUY without extreme greed returns default recommendation`() {
            // fearGreed = 0.45 → GREED (not EXTREME_GREED)
            val data = create5DayData(
                close = 100.0,
                ma = 80.0,
                cmf = 0.15,
                fearGreed = 0.45,
                buySignal = 1,
                auxBuySignal = 1,
                sellSignal = 0,
                auxSellSignal = 0
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.STRONG_BUY, result.signal)
            assertEquals("적극 매수 검토", result.recommendation)
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
        @DisplayName("0 < CMF ≤ 0.1이면 설명에 '자금 유입'이 포함된다")
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
        @DisplayName("-0.1 ≤ CMF < 0이면 설명에 '자금 유출'이 포함된다")
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
    // determineSignal() — 점수 경계값 테스트 (via analyze())
    // =========================================================================

    @Nested
    @DisplayName("determineSignal() — 점수 경계값 검증")
    inner class ScoreBoundaryTests {

        @Test
        @DisplayName("score = 70 → STRONG_BUY (정확한 경계)")
        fun `score exactly 70 returns STRONG_BUY`() {
            // +30 (price>ma) + +30 (cmf>0.1) + +10 (fg>0 && ≤0.4) + 0 = 70 → STRONG_BUY
            val data = createData(
                close = listOf(100.0),
                ma = listOf(90.0),
                cmf = listOf(0.15),
                fearGreed = listOf(0.1),   // >0 && ≤0.4 → +10
                buySignal = listOf(0),
                auxBuySignal = listOf(0),
                sellSignal = listOf(0),
                auxSellSignal = listOf(0)
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.STRONG_BUY, result.signal)
        }

        @Test
        @DisplayName("score = -70 → STRONG_SELL (정확한 경계)")
        fun `score exactly -70 returns STRONG_SELL`() {
            // -30 + -30 + -10 + 0 = -70 → STRONG_SELL
            val data = createData(
                close = listOf(80.0),
                ma = listOf(100.0),
                cmf = listOf(-0.15),
                fearGreed = listOf(-0.1),  // <0 && ≥-0.4 → -10
                buySignal = listOf(0),
                auxBuySignal = listOf(0),
                sellSignal = listOf(0),
                auxSellSignal = listOf(0)
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.STRONG_SELL, result.signal)
        }

        @Test
        @DisplayName("score = 30 → BUY (정확한 경계)")
        fun `score exactly 30 returns BUY`() {
            // +30 (price>ma) + 0 + 0 + 0 = 30 → BUY
            val data = createData(
                close = listOf(100.0),
                ma = listOf(90.0),
                cmf = listOf(0.0),
                fearGreed = listOf(0.0),
                buySignal = listOf(0),
                auxBuySignal = listOf(0),
                sellSignal = listOf(0),
                auxSellSignal = listOf(0)
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.BUY, result.signal)
        }

        @Test
        @DisplayName("score = -30 → SELL (정확한 경계)")
        fun `score exactly -30 returns SELL`() {
            // -30 (price<ma) + 0 + 0 + 0 = -30 → SELL
            val data = createData(
                close = listOf(80.0),
                ma = listOf(100.0),
                cmf = listOf(0.0),
                fearGreed = listOf(0.0),
                buySignal = listOf(0),
                auxBuySignal = listOf(0),
                sellSignal = listOf(0),
                auxSellSignal = listOf(0)
            )

            val result = TrendSignalCalculator.analyze(data)

            assertEquals(TrendTradeSignal.SELL, result.signal)
        }
    }
}
