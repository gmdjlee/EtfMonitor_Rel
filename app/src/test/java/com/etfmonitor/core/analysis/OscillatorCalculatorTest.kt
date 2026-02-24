package com.etfmonitor.core.analysis

import com.etfmonitor.core.analysis.model.MarketDepositData
import com.etfmonitor.core.analysis.model.OscillatorResult
import com.etfmonitor.core.analysis.model.StockData
import com.etfmonitor.core.analysis.model.TradeSignal
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OscillatorCalculator 단위 테스트
 *
 * 테스트 범위:
 * - calculate(): 수급비율 계산, EMA12/26, MACD, Signal, Oscillator
 * - EMA: alpha 계산, 첫 값, 후속 값 수렴
 * - analyzeSignal(): 빈 데이터, STRONG_BUY/BUY/NEUTRAL/SELL/STRONG_SELL
 * - analyzeMarketDeposit(): 빈 데이터, 4가지 조합
 */
@DisplayName("OscillatorCalculator 테스트")
class OscillatorCalculatorTest {

    // ── 헬퍼 함수 ────────────────────────────────────────────────────────────

    private fun createStockData(
        dates: List<String>,
        marketCap: List<Long>,
        foreign5d: List<Long>,
        institution5d: List<Long>
    ) = StockData(
        ticker = "005930",
        name = "삼성전자",
        dates = dates,
        marketCap = marketCap,
        foreign5d = foreign5d,
        institution5d = institution5d
    )

    /**
     * N개의 동일한 수급비율을 가진 StockData 생성.
     * supplyRatio = (foreign5d + institution5d) / marketCap
     */
    private fun createUniformData(
        n: Int,
        marketCap: Long = 1_000_000L,
        foreign5d: Long = 10_000L,
        institution5d: Long = 10_000L
    ): StockData {
        val dates = (1..n).map { "2026-01-${it.toString().padStart(2, '0')}" }
        return createStockData(
            dates = dates,
            marketCap = List(n) { marketCap },
            foreign5d = List(n) { foreign5d },
            institution5d = List(n) { institution5d }
        )
    }

    // =========================================================================
    // calculate() — 기본 동작
    // =========================================================================

    @Nested
    @DisplayName("calculate() — 기본 동작 테스트")
    inner class CalculateBasicTests {

        @Test
        @DisplayName("결과 리스트 크기는 입력 dates 크기와 같다")
        fun `calculate result sizes match input dates size`() {
            val data = createUniformData(30)

            val result = OscillatorCalculator.calculate(data)

            assertEquals(30, result.dates.size)
            assertEquals(30, result.oscillator.size)
            assertEquals(30, result.ema.size)
            assertEquals(30, result.macd.size)
            assertEquals(30, result.signal.size)
            assertEquals(30, result.histogram.size)
        }

        @Test
        @DisplayName("oscillator와 histogram은 동일한 값이다")
        fun `calculate oscillator and histogram are identical`() {
            val data = createUniformData(30)

            val result = OscillatorCalculator.calculate(data)

            assertEquals(result.oscillator, result.histogram)
        }

        @Test
        @DisplayName("marketCap이 0인 경우 수급비율이 0.0으로 처리된다")
        fun `calculate with zero marketCap gives zero supply ratio`() {
            val data = createStockData(
                dates = listOf("2026-01-01"),
                marketCap = listOf(0L),
                foreign5d = listOf(1000L),
                institution5d = listOf(1000L)
            )

            val result = OscillatorCalculator.calculate(data)

            // supplyRatio = 0.0 (시총이 0) → EMA = 0.0 → MACD = 0.0 → Signal = 0.0 → Osc = 0.0
            assertEquals(0.0, result.oscillator[0], 1e-10)
        }

        @Test
        @DisplayName("dates가 비어있으면 모든 결과 리스트도 비어있다")
        fun `calculate with empty data returns empty result lists`() {
            val data = createStockData(
                dates = emptyList(),
                marketCap = emptyList(),
                foreign5d = emptyList(),
                institution5d = emptyList()
            )

            val result = OscillatorCalculator.calculate(data)

            assertTrue(result.dates.isEmpty())
            assertTrue(result.oscillator.isEmpty())
            assertTrue(result.ema.isEmpty())
            assertTrue(result.macd.isEmpty())
            assertTrue(result.signal.isEmpty())
        }

        @Test
        @DisplayName("단일 데이터 포인트는 EMA = 첫 수급비율 값이다")
        fun `calculate single data point EMA equals first supply ratio`() {
            // supplyRatio = (10_000 + 10_000) / 1_000_000 = 0.02
            val data = createStockData(
                dates = listOf("2026-01-01"),
                marketCap = listOf(1_000_000L),
                foreign5d = listOf(10_000L),
                institution5d = listOf(10_000L)
            )

            val result = OscillatorCalculator.calculate(data)

            // EMA[0] = supplyRatio[0] = 0.02
            assertEquals(0.02, result.ema[0], 1e-10)
        }

        @Test
        @DisplayName("균일한 수급비율에서 MACD는 0에 수렴한다")
        fun `calculate uniform supply ratio MACD converges to zero`() {
            // 균일한 비율로 EMA12 = EMA26 = ratio → MACD = 0
            val data = createUniformData(50)

            val result = OscillatorCalculator.calculate(data)

            // 충분히 많은 데이터 후에는 MACD ≈ 0
            val lastMACD = result.macd.last()
            assertTrue(
                kotlin.math.abs(lastMACD) < 1e-10,
                "MACD should converge to 0 for uniform data but got: $lastMACD"
            )
        }

        @Test
        @DisplayName("수급비율이 양수이면 초기 MACD는 양수이다")
        fun `calculate positive supply ratio produces positive initial MACD`() {
            // EMA12가 EMA26보다 최근 데이터에 더 빠르게 반응
            // 상승 데이터: 초기에는 EMA12 > EMA26
            val n = 30
            val dates = (1..n).map { "2026-01-${it.toString().padStart(2, '0')}" }

            // 수급비율이 점점 증가하는 데이터
            val marketCap = List(n) { 1_000_000L }
            val combined = List(n) { i -> (i + 1) * 1000L }

            val data = createStockData(
                dates = dates,
                marketCap = marketCap,
                foreign5d = combined,
                institution5d = List(n) { 0L }
            )

            val result = OscillatorCalculator.calculate(data)

            // 상승 추세에서는 EMA12 > EMA26 → MACD > 0
            assertTrue(result.macd.last() > 0, "Expected positive MACD for rising trend")
        }
    }

    // =========================================================================
    // calculate() — EMA 알고리즘 검증
    // =========================================================================

    @Nested
    @DisplayName("calculate() — EMA 알고리즘 검증")
    inner class EmaAlgorithmTests {

        @Test
        @DisplayName("EMA12 alpha = 2/(12+1) = 2/13 ≈ 0.1538")
        fun `calculate EMA12 uses correct alpha`() {
            // 단일 데이터: supplyRatio = 0.02
            // 두 번째 데이터: supplyRatio = 0.04
            // EMA12[1] = alpha * 0.04 + (1-alpha) * 0.02
            // alpha = 2/13
            val alpha = 2.0 / 13.0
            val ratio0 = 0.02
            val ratio1 = 0.04
            val expectedEma = alpha * ratio1 + (1 - alpha) * ratio0

            val data = createStockData(
                dates = listOf("2026-01-01", "2026-01-02"),
                marketCap = listOf(1_000_000L, 1_000_000L),
                foreign5d = listOf(10_000L, 20_000L),
                institution5d = listOf(10_000L, 20_000L)
            )

            val result = OscillatorCalculator.calculate(data)

            // ema[1] = EMA12[1]
            assertEquals(expectedEma, result.ema[1], 1e-10)
        }

        @Test
        @DisplayName("EMA의 첫 번째 값은 첫 번째 수급비율과 같다")
        fun `calculate EMA first value equals first supply ratio`() {
            // supplyRatio[0] = (5000 + 5000) / 100_000 = 0.1
            val data = createStockData(
                dates = listOf("2026-01-01", "2026-01-02"),
                marketCap = listOf(100_000L, 100_000L),
                foreign5d = listOf(5_000L, 5_000L),
                institution5d = listOf(5_000L, 5_000L)
            )

            val result = OscillatorCalculator.calculate(data)

            assertEquals(0.1, result.ema[0], 1e-10)
        }
    }

    // =========================================================================
    // analyzeSignal() — 빈 데이터
    // =========================================================================

    @Nested
    @DisplayName("analyzeSignal() — 빈 데이터 처리")
    inner class AnalyzeSignalEmptyTests {

        @Test
        @DisplayName("dates가 빈 OscillatorResult는 NEUTRAL을 반환한다")
        fun `analyzeSignal with empty dates returns NEUTRAL`() {
            val emptyResult = OscillatorResult(
                dates = emptyList(),
                marketCap = emptyList(),
                oscillator = emptyList(),
                ema = emptyList(),
                macd = emptyList(),
                signal = emptyList(),
                histogram = emptyList()
            )

            val analysis = OscillatorCalculator.analyzeSignal(emptyResult)

            assertEquals(TradeSignal.NEUTRAL, analysis.signal)
            assertEquals("데이터 없음", analysis.trend)
        }
    }

    // =========================================================================
    // analyzeSignal() — 시그널 판별
    // =========================================================================

    @Nested
    @DisplayName("analyzeSignal() — 매매 시그널 판별")
    inner class AnalyzeSignalTests {

        /**
         * 충분한 데이터(30개 이상)를 생성하여 analyzeSignal이 최소 5개의
         * 최근 데이터를 사용할 수 있게 하는 헬퍼.
         */
        private fun buildOscillatorResult(
            n: Int = 30,
            oscillatorValues: List<Double>,
            macdValues: List<Double>,
            signalValues: List<Double>
        ): OscillatorResult {
            require(oscillatorValues.size == n)
            require(macdValues.size == n)
            require(signalValues.size == n)

            return OscillatorResult(
                dates = (1..n).map { "2026-01-${it.toString().padStart(2, '0')}" },
                marketCap = List(n) { 1_000_000L },
                oscillator = oscillatorValues,
                ema = List(n) { 0.0 },
                macd = macdValues,
                signal = signalValues,
                histogram = oscillatorValues
            )
        }

        @Test
        @DisplayName("latestOsc > 0.005 이고 MACD > Signal이면 STRONG_BUY를 반환한다")
        fun `analyzeSignal high latestOsc and MACD above signal returns STRONG_BUY`() {
            val n = 30
            // score: latestOsc > 0.005 → +40, MACD > Signal (no cross) → +15,
            //        histogram 상승 추세 → +30 → total = 85 → STRONG_BUY
            val oscillator = List(n - 3) { 0.006 } + listOf(0.004, 0.005, 0.007)
            val macd = List(n) { 0.01 }
            val signal = List(n) { 0.005 }

            val result = buildOscillatorResult(n, oscillator, macd, signal)
            val analysis = OscillatorCalculator.analyzeSignal(result)

            assertEquals(TradeSignal.STRONG_BUY, analysis.signal)
        }

        @Test
        @DisplayName("latestOsc < -0.005 이고 MACD < Signal이면 STRONG_SELL을 반환한다")
        fun `analyzeSignal low latestOsc and MACD below signal returns STRONG_SELL`() {
            val n = 30
            // score: latestOsc < -0.005 → -40, MACD < Signal (no cross) → -15,
            //        histogram 하락 추세 → -30 → total = -85 → STRONG_SELL
            val oscillator = List(n - 3) { -0.006 } + listOf(-0.007, -0.008, -0.009)
            val macd = List(n) { -0.01 }
            val signal = List(n) { -0.005 }

            val result = buildOscillatorResult(n, oscillator, macd, signal)
            val analysis = OscillatorCalculator.analyzeSignal(result)

            assertEquals(TradeSignal.STRONG_SELL, analysis.signal)
        }

        @Test
        @DisplayName("MACD가 Signal을 상향 돌파(골든크로스)하면 +30점이 추가된다")
        fun `analyzeSignal MACD golden cross adds 30 points`() {
            val n = 30
            // 마지막 두 항목에서 골든크로스:
            // macd[n-2] <= signal[n-2] 이었다가 macd[n-1] > signal[n-1]
            val macd = List(n - 1) { -0.005 } + listOf(0.005)       // 마지막 양수
            val signal = List(n) { 0.0 }                              // signal = 0
            val oscillator = List(n) { 0.001 }                        // 약한 양수 (latestOsc ~0.001 → 0점)

            val result = buildOscillatorResult(n, oscillator, macd, signal)
            val analysis = OscillatorCalculator.analyzeSignal(result)

            // score = 0 (latestOsc ~0.001) + 30 (골든크로스) + 0 (히스토그램 미미) = 30+ → BUY 이상
            assertTrue(
                analysis.score >= 20.0,
                "Expected score ≥ 20 for golden cross but got: ${analysis.score}"
            )
        }

        @Test
        @DisplayName("score가 -100~+100 범위로 클램핑된다")
        fun `analyzeSignal score is clamped to -100 to 100`() {
            val n = 30
            val oscillator = List(n - 3) { 0.01 } + listOf(0.008, 0.009, 0.011)
            val macd = List(n) { 0.02 }
            val signal = List(n) { 0.01 }

            val result = buildOscillatorResult(n, oscillator, macd, signal)
            val analysis = OscillatorCalculator.analyzeSignal(result)

            assertTrue(analysis.score in -100.0..100.0)
        }

        @Test
        @DisplayName("latestOsc > 0이면 foreignTrend와 institutionTrend가 '순매수'다")
        fun `analyzeSignal positive latestOsc gives net buy trend labels`() {
            val n = 30
            val oscillator = List(n) { 0.001 }
            val macd = List(n) { 0.005 }
            val signal = List(n) { 0.003 }

            val result = buildOscillatorResult(n, oscillator, macd, signal)
            val analysis = OscillatorCalculator.analyzeSignal(result)

            assertEquals("순매수", analysis.foreignTrend)
            assertEquals("순매수", analysis.institutionTrend)
        }

        @Test
        @DisplayName("latestOsc < 0이면 foreignTrend와 institutionTrend가 '순매도'다")
        fun `analyzeSignal negative latestOsc gives net sell trend labels`() {
            val n = 30
            val oscillator = List(n) { -0.001 }
            val macd = List(n) { -0.005 }
            val signal = List(n) { -0.003 }

            val result = buildOscillatorResult(n, oscillator, macd, signal)
            val analysis = OscillatorCalculator.analyzeSignal(result)

            assertEquals("순매도", analysis.foreignTrend)
            assertEquals("순매도", analysis.institutionTrend)
        }

        @Test
        @DisplayName("STRONG_BUY recommendation은 '적극 매수 검토'다")
        fun `analyzeSignal STRONG_BUY recommendation is correct`() {
            val n = 30
            val oscillator = List(n - 3) { 0.006 } + listOf(0.004, 0.005, 0.007)
            val macd = List(n) { 0.01 }
            val signal = List(n) { 0.005 }

            val result = buildOscillatorResult(n, oscillator, macd, signal)
            val analysis = OscillatorCalculator.analyzeSignal(result)

            assertEquals("적극 매수 검토", analysis.recommendation)
        }

        @Test
        @DisplayName("STRONG_SELL recommendation은 '적극 매도 검토'다")
        fun `analyzeSignal STRONG_SELL recommendation is correct`() {
            val n = 30
            val oscillator = List(n - 3) { -0.006 } + listOf(-0.007, -0.008, -0.009)
            val macd = List(n) { -0.01 }
            val signal = List(n) { -0.005 }

            val result = buildOscillatorResult(n, oscillator, macd, signal)
            val analysis = OscillatorCalculator.analyzeSignal(result)

            assertEquals("적극 매도 검토", analysis.recommendation)
        }

        @Test
        @DisplayName("latestOsc > 0.003 이면 trend가 '강한 매수세'다")
        fun `analyzeSignal latestOsc above 0_003 trend is strong buy`() {
            val n = 30
            val oscillator = List(n) { 0.005 }
            val macd = List(n) { 0.01 }
            val signal = List(n) { 0.005 }

            val result = buildOscillatorResult(n, oscillator, macd, signal)
            val analysis = OscillatorCalculator.analyzeSignal(result)

            assertEquals("강한 매수세", analysis.trend)
        }

        @Test
        @DisplayName("latestOsc < -0.003 이면 trend가 '강한 매도세'다")
        fun `analyzeSignal latestOsc below -0_003 trend is strong sell`() {
            val n = 30
            val oscillator = List(n) { -0.005 }
            val macd = List(n) { -0.01 }
            val signal = List(n) { -0.005 }

            val result = buildOscillatorResult(n, oscillator, macd, signal)
            val analysis = OscillatorCalculator.analyzeSignal(result)

            assertEquals("강한 매도세", analysis.trend)
        }
    }

    // =========================================================================
    // analyzeMarketDeposit() — 증시 자금 동향 분석
    // =========================================================================

    @Nested
    @DisplayName("analyzeMarketDeposit() — 증시 자금 동향 분석")
    inner class AnalyzeMarketDepositTests {

        private fun createDepositData(
            n: Int = 5,
            depositStart: Double,
            depositEnd: Double,
            creditStart: Double,
            creditEnd: Double
        ): MarketDepositData {
            val dates = (1..n).map { "2026-01-${it.toString().padStart(2, '0')}" }
            val deposit = listOf(depositStart) + List(n - 2) { (depositStart + depositEnd) / 2 } + listOf(depositEnd)
            val credit = listOf(creditStart) + List(n - 2) { (creditStart + creditEnd) / 2 } + listOf(creditEnd)

            return MarketDepositData(
                dates = dates,
                depositAmounts = deposit,
                depositChanges = List(n) { 0.0 },
                creditAmounts = credit,
                creditChanges = List(n) { 0.0 }
            )
        }

        @Test
        @DisplayName("빈 데이터는 '데이터 없음'을 반환한다")
        fun `analyzeMarketDeposit empty data returns no data message`() {
            val data = MarketDepositData(
                dates = emptyList(),
                depositAmounts = emptyList(),
                depositChanges = emptyList(),
                creditAmounts = emptyList(),
                creditChanges = emptyList()
            )

            val result = OscillatorCalculator.analyzeMarketDeposit(data)

            assertEquals("데이터 없음", result)
        }

        @Test
        @DisplayName("예탁금 증가 + 신용 증가 → '자금 유입 & 신용 증가 - 시장 긍정적'")
        fun `analyzeMarketDeposit deposit up and credit up returns positive market`() {
            val data = createDepositData(
                depositStart = 100.0, depositEnd = 200.0,  // 증가
                creditStart = 50.0, creditEnd = 80.0        // 증가
            )

            val result = OscillatorCalculator.analyzeMarketDeposit(data)

            assertEquals("자금 유입 & 신용 증가 - 시장 긍정적", result)
        }

        @Test
        @DisplayName("예탁금 증가 + 신용 감소 → '자금 유입 & 신용 감소 - 안정적'")
        fun `analyzeMarketDeposit deposit up and credit down returns stable`() {
            val data = createDepositData(
                depositStart = 100.0, depositEnd = 200.0,  // 증가
                creditStart = 80.0, creditEnd = 50.0        // 감소
            )

            val result = OscillatorCalculator.analyzeMarketDeposit(data)

            assertEquals("자금 유입 & 신용 감소 - 안정적", result)
        }

        @Test
        @DisplayName("예탁금 감소 + 신용 증가 → '자금 유출 & 신용 증가 - 주의'")
        fun `analyzeMarketDeposit deposit down and credit up returns caution`() {
            val data = createDepositData(
                depositStart = 200.0, depositEnd = 100.0,  // 감소
                creditStart = 50.0, creditEnd = 80.0        // 증가
            )

            val result = OscillatorCalculator.analyzeMarketDeposit(data)

            assertEquals("자금 유출 & 신용 증가 - 주의", result)
        }

        @Test
        @DisplayName("예탁금 감소 + 신용 감소 → '자금 유출 & 신용 감소 - 시장 부정적'")
        fun `analyzeMarketDeposit deposit down and credit down returns negative market`() {
            val data = createDepositData(
                depositStart = 200.0, depositEnd = 100.0,  // 감소
                creditStart = 80.0, creditEnd = 50.0        // 감소
            )

            val result = OscillatorCalculator.analyzeMarketDeposit(data)

            assertEquals("자금 유출 & 신용 감소 - 시장 부정적", result)
        }

        @Test
        @DisplayName("예탁금 동일 + 신용 동일 → '보합'")
        fun `analyzeMarketDeposit no change returns neutral`() {
            val data = createDepositData(
                depositStart = 100.0, depositEnd = 100.0,  // 변화 없음 (trend = 0)
                creditStart = 50.0, creditEnd = 50.0        // 변화 없음
            )

            val result = OscillatorCalculator.analyzeMarketDeposit(data)

            assertEquals("보합", result)
        }
    }

    // =========================================================================
    // calculate() — 통합 검증 (EMA/MACD/Signal/Oscillator 관계)
    // =========================================================================

    @Nested
    @DisplayName("calculate() — EMA/MACD/Signal/Oscillator 관계 검증")
    inner class OscillatorRelationshipTests {

        @Test
        @DisplayName("oscillator = macd - signal 관계가 성립한다")
        fun `calculate oscillator equals macd minus signal`() {
            val data = createUniformData(30)

            val result = OscillatorCalculator.calculate(data)

            result.macd.zip(result.signal).zip(result.oscillator).forEach { (pair, osc) ->
                val (m, s) = pair
                assertEquals(m - s, osc, 1e-10, "oscillator should equal macd - signal")
            }
        }

        @Test
        @DisplayName("수급비율이 0이면 EMA12, EMA26, MACD, Signal, Oscillator 모두 0이다")
        fun `calculate all zero supply ratio gives all zero outputs`() {
            val data = createStockData(
                dates = (1..30).map { "2026-01-${it.toString().padStart(2, '0')}" },
                marketCap = List(30) { 1_000_000L },
                foreign5d = List(30) { 0L },
                institution5d = List(30) { 0L }
            )

            val result = OscillatorCalculator.calculate(data)

            result.ema.forEach { assertEquals(0.0, it, 1e-10) }
            result.macd.forEach { assertEquals(0.0, it, 1e-10) }
            result.signal.forEach { assertEquals(0.0, it, 1e-10) }
            result.oscillator.forEach { assertEquals(0.0, it, 1e-10) }
        }
    }
}
