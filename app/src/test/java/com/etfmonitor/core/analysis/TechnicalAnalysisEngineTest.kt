package com.etfmonitor.core.analysis

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TechnicalAnalysisEngine 단위 테스트
 *
 * 테스트 범위:
 * - calculateEMA: 지수이동평균 계산
 * - resampleWeekly: 주봉 OHLCV 집계
 * - resampleMonthly: 월봉 OHLCV 집계
 * - rollingSum: 롤링 합산
 * - calculateCMF: Chaikin Money Flow
 * - calculateElderImpulse: Elder Impulse System
 * - calculateDemarkTD: DeMark TD Sequential
 * - generateSignals: 매수/매도 신호 생성
 */
@DisplayName("TechnicalAnalysisEngine 테스트")
class TechnicalAnalysisEngineTest {

    // ============================================================
    // calculateEMA 테스트
    // ============================================================

    @Nested
    @DisplayName("calculateEMA — 지수이동평균")
    inner class CalculateEmaTests {

        @Test
        @DisplayName("period 1이면 원본 값과 동일")
        fun `calculateEMA_withPeriodOne_returnsOriginalValues`() {
            val values = listOf(10.0, 20.0, 30.0, 40.0, 50.0)

            val result = TechnicalAnalysisEngine.calculateEMA(values, period = 1)

            // alpha = 2/(1+1) = 1.0, so EMA[i] = 1.0*value[i] + 0.0*ema[i-1] = value[i]
            assertEquals(values.size, result.size)
            values.forEachIndexed { i, v ->
                assertEquals(v, result[i], 1e-9, "index $i mismatch")
            }
        }

        @Test
        @DisplayName("EMA는 최근 값 방향으로 수렴한다")
        fun `calculateEMA_withRisingValues_emaTrailsBelowClose`() {
            // 단조 증가 데이터: EMA는 항상 현재 값보다 낮아야 함 (period > 1)
            val values = (1..10).map { it.toDouble() }

            val result = TechnicalAnalysisEngine.calculateEMA(values, period = 3)

            assertEquals(values.size, result.size)
            // EMA[0] = values[0], 이후부터는 values[i] > result[i] 이어야 함
            for (i in 1 until result.size) {
                assertTrue(
                    result[i] < values[i],
                    "EMA[${i}]=${result[i]} should be below close=${values[i]} on rising series"
                )
            }
            // 마지막 EMA는 첫 번째 EMA보다 높아야 함 (상승 추세)
            assertTrue(result.last() > result.first())
        }

        @Test
        @DisplayName("빈 리스트 입력 시 빈 리스트 반환")
        fun `calculateEMA_withEmptyList_returnsEmpty`() {
            val result = TechnicalAnalysisEngine.calculateEMA(emptyList(), period = 5)
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("단일 원소는 그대로 반환")
        fun `calculateEMA_withSingleElement_returnsSameValue`() {
            val result = TechnicalAnalysisEngine.calculateEMA(listOf(42.0), period = 5)
            assertEquals(1, result.size)
            assertEquals(42.0, result[0], 1e-9)
        }

        @Test
        @DisplayName("EMA 공식 수계산 검증 (period=2, 3개 데이터)")
        fun `calculateEMA_knownValues_matchesManualCalculation`() {
            // alpha = 2/(2+1) = 2/3
            // EMA[0] = 10.0
            // EMA[1] = (2/3)*20 + (1/3)*10 = 13.333 + 3.333 = 16.667
            // EMA[2] = (2/3)*30 + (1/3)*16.667 = 20 + 5.556 = 25.556
            val values = listOf(10.0, 20.0, 30.0)
            val alpha = 2.0 / 3.0

            val result = TechnicalAnalysisEngine.calculateEMA(values, period = 2)

            assertEquals(3, result.size)
            assertEquals(10.0, result[0], 1e-9)
            val expectedEma1 = alpha * 20.0 + (1 - alpha) * 10.0
            assertEquals(expectedEma1, result[1], 1e-9)
            val expectedEma2 = alpha * 30.0 + (1 - alpha) * expectedEma1
            assertEquals(expectedEma2, result[2], 1e-9)
        }
    }

    // ============================================================
    // resampleWeekly 테스트
    // ============================================================

    @Nested
    @DisplayName("resampleWeekly — 주봉 집계")
    inner class ResampleWeeklyTests {

        @Test
        @DisplayName("OHLCV 집계: open=첫째, high=최대, low=최소, close=마지막, volume=합산")
        fun `resampleWeekly_withMultipleDaysInSameWeek_aggregatesCorrectly`() {
            // 2025-01-06(월) ~ 2025-01-10(금): 같은 주
            val dates = listOf("20250106", "20250107", "20250108", "20250109", "20250110")
            val open  = listOf(100.0, 102.0, 104.0, 101.0, 103.0)
            val high  = listOf(110.0, 108.0, 115.0, 107.0, 112.0)
            val low   = listOf(98.0,  99.0,  100.0, 96.0,  101.0)
            val close = listOf(105.0, 106.0, 111.0, 103.0, 109.0)
            val volume = listOf(1000L, 1200L, 1500L, 800L, 1100L)

            val result = TechnicalAnalysisEngine.resampleWeekly(dates, open, high, low, close, volume)

            assertEquals(1, result.size, "5 days in 1 week → 1 weekly bar")
            val bar = result[0]
            assertEquals(100.0, bar.open,  1e-9, "open = first day's open")
            assertEquals(115.0, bar.high,  1e-9, "high = max of week")
            assertEquals(96.0,  bar.low,   1e-9, "low = min of week")
            assertEquals(109.0, bar.close, 1e-9, "close = last day's close")
            assertEquals(5600L, bar.volume,       "volume = sum of week")
            assertEquals("20250110", bar.date,    "date = last day of week")
        }

        @Test
        @DisplayName("빈 입력은 빈 결과 반환")
        fun `resampleWeekly_withEmptyInput_returnsEmpty`() {
            val result = TechnicalAnalysisEngine.resampleWeekly(
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()
            )
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("한 주 내 데이터는 단일 항목 반환")
        fun `resampleWeekly_withDataInOneWeek_returnsSingleEntry`() {
            // 2025-01-08(수) 하루만
            val dates  = listOf("20250108")
            val open   = listOf(200.0)
            val high   = listOf(210.0)
            val low    = listOf(195.0)
            val close  = listOf(205.0)
            val volume = listOf(500L)

            val result = TechnicalAnalysisEngine.resampleWeekly(dates, open, high, low, close, volume)

            assertEquals(1, result.size)
            assertEquals(200.0, result[0].open,  1e-9)
            assertEquals(210.0, result[0].high,  1e-9)
            assertEquals(195.0, result[0].low,   1e-9)
            assertEquals(205.0, result[0].close, 1e-9)
            assertEquals(500L,  result[0].volume)
        }

        @Test
        @DisplayName("2주에 걸친 데이터는 2개 항목 반환")
        fun `resampleWeekly_withTwoWeeks_returnsTwoBars`() {
            // Week 1: 20250106(월)~20250110(금)
            // Week 2: 20250113(월)~20250117(금)
            val dates = listOf(
                "20250106", "20250107", "20250108",
                "20250113", "20250114", "20250115"
            )
            val open   = listOf(100.0, 101.0, 102.0, 200.0, 201.0, 202.0)
            val high   = listOf(110.0, 111.0, 112.0, 210.0, 211.0, 212.0)
            val low    = listOf(90.0,  91.0,  92.0,  190.0, 191.0, 192.0)
            val close  = listOf(105.0, 106.0, 107.0, 205.0, 206.0, 207.0)
            val volume = listOf(1000L, 1100L, 1200L, 2000L, 2100L, 2200L)

            val result = TechnicalAnalysisEngine.resampleWeekly(dates, open, high, low, close, volume)

            assertEquals(2, result.size)
            // Week 1 bar
            assertEquals(100.0, result[0].open,  1e-9)
            assertEquals(112.0, result[0].high,  1e-9)
            assertEquals(90.0,  result[0].low,   1e-9)
            assertEquals(107.0, result[0].close, 1e-9)
            assertEquals(3300L, result[0].volume)
            // Week 2 bar
            assertEquals(200.0, result[1].open,  1e-9)
            assertEquals(212.0, result[1].high,  1e-9)
            assertEquals(190.0, result[1].low,   1e-9)
            assertEquals(207.0, result[1].close, 1e-9)
            assertEquals(6300L, result[1].volume)
        }
    }

    // ============================================================
    // resampleMonthly 테스트
    // ============================================================

    @Nested
    @DisplayName("resampleMonthly — 월봉 집계")
    inner class ResampleMonthlyTests {

        @Test
        @DisplayName("같은 연월 데이터는 하나로 집계")
        fun `resampleMonthly_withSameMonth_aggregatesIntoOneBar`() {
            val dates  = listOf("20250101", "20250115", "20250131")
            val open   = listOf(100.0, 102.0, 104.0)
            val high   = listOf(120.0, 115.0, 118.0)
            val low    = listOf(95.0,  98.0,  97.0)
            val close  = listOf(110.0, 111.0, 112.0)
            val volume = listOf(1000L, 1200L, 1100L)

            val result = TechnicalAnalysisEngine.resampleMonthly(dates, open, high, low, close, volume)

            assertEquals(1, result.size)
            val bar = result[0]
            assertEquals(100.0, bar.open,  1e-9, "open = first of month")
            assertEquals(120.0, bar.high,  1e-9, "high = max of month")
            assertEquals(95.0,  bar.low,   1e-9, "low = min of month")
            assertEquals(112.0, bar.close, 1e-9, "close = last of month")
            assertEquals(3300L, bar.volume,       "volume = sum of month")
        }

        @Test
        @DisplayName("빈 입력은 빈 결과 반환")
        fun `resampleMonthly_withEmptyInput_returnsEmpty`() {
            val result = TechnicalAnalysisEngine.resampleMonthly(
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()
            )
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("두 달에 걸친 데이터는 2개 항목, 연-월별로 그룹화")
        fun `resampleMonthly_withTwoMonths_returnsTwoBars`() {
            val dates = listOf("20250115", "20250116", "20250201", "20250215")
            val open   = listOf(100.0, 101.0, 200.0, 201.0)
            val high   = listOf(110.0, 111.0, 210.0, 211.0)
            val low    = listOf(90.0,  91.0,  190.0, 191.0)
            val close  = listOf(105.0, 106.0, 205.0, 206.0)
            val volume = listOf(500L,  600L,  700L,  800L)

            val result = TechnicalAnalysisEngine.resampleMonthly(dates, open, high, low, close, volume)

            assertEquals(2, result.size)
            // January bar (sorted by date, so index 0)
            assertEquals(100.0, result[0].open,  1e-9)
            assertEquals(111.0, result[0].high,  1e-9)
            assertEquals(90.0,  result[0].low,   1e-9)
            assertEquals(106.0, result[0].close, 1e-9)
            assertEquals(1100L, result[0].volume)
            // February bar
            assertEquals(200.0, result[1].open,  1e-9)
            assertEquals(211.0, result[1].high,  1e-9)
            assertEquals(190.0, result[1].low,   1e-9)
            assertEquals(206.0, result[1].close, 1e-9)
            assertEquals(1500L, result[1].volume)
        }

        @Test
        @DisplayName("연도가 다른 같은 월은 별도 항목으로 분리")
        fun `resampleMonthly_withSameMonthDifferentYear_returnsSeparateBars`() {
            val dates  = listOf("20240101", "20250101")
            val open   = listOf(100.0, 200.0)
            val high   = listOf(110.0, 210.0)
            val low    = listOf(90.0,  190.0)
            val close  = listOf(105.0, 205.0)
            val volume = listOf(1000L, 2000L)

            val result = TechnicalAnalysisEngine.resampleMonthly(dates, open, high, low, close, volume)

            assertEquals(2, result.size, "2024-01 and 2025-01 must be separate bars")
        }
    }

    // ============================================================
    // rollingSum 테스트
    // ============================================================

    @Nested
    @DisplayName("rollingSum — 롤링 합산")
    inner class RollingSumTests {

        @Test
        @DisplayName("period=3으로 정확한 롤링 합 계산")
        fun `rollingSum_withPeriodThree_returnsCorrectSums`() {
            // [1, 2, 3, 4, 5], period=3
            // index 0,1 → 0L (워밍업)
            // index 2 → 1+2+3=6
            // index 3 → 2+3+4=9
            // index 4 → 3+4+5=12
            val values = listOf(1L, 2L, 3L, 4L, 5L)

            val result = TechnicalAnalysisEngine.rollingSum(values, period = 3)

            assertEquals(5, result.size)
            assertEquals(0L,  result[0])
            assertEquals(0L,  result[1])
            assertEquals(6L,  result[2])
            assertEquals(9L,  result[3])
            assertEquals(12L, result[4])
        }

        @Test
        @DisplayName("처음 (period-1)개 값은 0L")
        fun `rollingSum_leadingValues_areZero`() {
            val values = listOf(10L, 20L, 30L, 40L, 50L, 60L)

            val result = TechnicalAnalysisEngine.rollingSum(values, period = 4)

            // Indices 0,1,2 must be 0
            assertEquals(0L, result[0])
            assertEquals(0L, result[1])
            assertEquals(0L, result[2])
            // Index 3 = 10+20+30+40 = 100
            assertEquals(100L, result[3])
        }

        @Test
        @DisplayName("빈 입력은 빈 결과 반환")
        fun `rollingSum_withEmptyInput_returnsEmpty`() {
            val result = TechnicalAnalysisEngine.rollingSum(emptyList(), period = 5)
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("데이터 개수가 period보다 적으면 모두 0")
        fun `rollingSum_whenDataSizeLessThanPeriod_returnsAllZeros`() {
            val values = listOf(100L, 200L, 300L)

            val result = TechnicalAnalysisEngine.rollingSum(values, period = 5)

            assertEquals(3, result.size)
            result.forEach { assertEquals(0L, it) }
        }

        @Test
        @DisplayName("period=1이면 원본 값과 동일")
        fun `rollingSum_withPeriodOne_returnsOriginalValues`() {
            val values = listOf(7L, 13L, 42L, 99L)

            val result = TechnicalAnalysisEngine.rollingSum(values, period = 1)

            assertEquals(values, result)
        }
    }

    // ============================================================
    // calculateCMF 테스트
    // ============================================================

    @Nested
    @DisplayName("calculateCMF — Chaikin Money Flow")
    inner class CalculateCmfTests {

        @Test
        @DisplayName("high == low이면 MFM=0이므로 CMF=0")
        fun `calculateCMF_whenHighEqualsLow_returnZero`() {
            val n = 6
            val price  = List(n) { 100.0 }
            val volume = List(n) { 1000L }

            val result = TechnicalAnalysisEngine.calculateCMF(
                high = price, low = price, close = price, volume = volume, period = 4
            )

            assertEquals(n, result.size)
            result.forEachIndexed { i, v ->
                assertEquals(0.0, v, 1e-9, "index $i should be 0.0")
            }
        }

        @Test
        @DisplayName("close가 항상 high이면 MFM=+1, CMF는 양수")
        fun `calculateCMF_whenCloseAlwaysEqualsHigh_returnPositiveCmf`() {
            // MFM = ((H-L) - (H-H)) / (H-L) = 1.0
            // → CMF is positive
            val high   = listOf(110.0, 112.0, 115.0, 114.0, 113.0, 116.0)
            val low    = listOf(100.0, 102.0, 105.0, 104.0, 103.0, 106.0)
            val close  = high   // close = high → MFM = 1.0
            val volume = listOf(1000L, 1200L, 1100L, 1300L, 900L, 1400L)

            val result = TechnicalAnalysisEngine.calculateCMF(high, low, close, volume, period = 4)

            assertEquals(6, result.size)
            // First (period-1)=3 values are 0; from index 3 onward should be positive
            for (i in 3 until result.size) {
                assertTrue(result[i] > 0.0, "CMF[$i]=${result[i]} should be positive")
            }
        }

        @Test
        @DisplayName("데이터 개수가 period보다 적으면 모두 0.0")
        fun `calculateCMF_whenDataSizeLessThanPeriod_returnsAllZeros`() {
            val high   = listOf(110.0, 112.0)
            val low    = listOf(100.0, 102.0)
            val close  = listOf(105.0, 107.0)
            val volume = listOf(1000L, 1100L)

            val result = TechnicalAnalysisEngine.calculateCMF(high, low, close, volume, period = 4)

            assertEquals(2, result.size)
            result.forEach { assertEquals(0.0, it, 1e-9) }
        }

        @Test
        @DisplayName("처음 (period-1)개 인덱스는 0.0 워밍업")
        fun `calculateCMF_leadingIndices_areZero`() {
            val n = 8
            val high   = List(n) { 110.0 + it }
            val low    = List(n) { 100.0 + it }
            val close  = List(n) { 105.0 + it }
            val volume = List(n) { 1000L }
            val period = 4

            val result = TechnicalAnalysisEngine.calculateCMF(high, low, close, volume, period)

            for (i in 0 until period - 1) {
                assertEquals(0.0, result[i], 1e-9, "warmup index $i should be 0.0")
            }
        }

        @Test
        @DisplayName("close가 항상 low이면 MFM=-1, CMF는 음수")
        fun `calculateCMF_whenCloseAlwaysEqualsLow_returnsNegativeCmf`() {
            val high   = listOf(110.0, 112.0, 115.0, 114.0, 113.0, 116.0)
            val low    = listOf(100.0, 102.0, 105.0, 104.0, 103.0, 106.0)
            val close  = low   // close = low → MFM = -1.0
            val volume = listOf(1000L, 1200L, 1100L, 1300L, 900L, 1400L)

            val result = TechnicalAnalysisEngine.calculateCMF(high, low, close, volume, period = 4)

            for (i in 3 until result.size) {
                assertTrue(result[i] < 0.0, "CMF[$i]=${result[i]} should be negative")
            }
        }
    }

    // ============================================================
    // calculateElderImpulse 테스트
    // ============================================================

    @Nested
    @DisplayName("calculateElderImpulse — Elder Impulse System")
    inner class CalculateElderImpulseTests {

        @Test
        @DisplayName("단조 증가 데이터: 후반부 impulse는 1 (bull)")
        fun `calculateElderImpulse_withStronglyRisingPrices_producesBullSignal`() {
            // 가속 상승 데이터: i^2 성장으로 EMA 기울기와 MACD 히스토그램 기울기 모두 양수 보장
            // 선형 데이터는 MACD 히스토그램이 상수로 수렴(기울기=0)하여 bull 신호가 생성되지 않음
            // 가속 성장(이차 함수)은 MACD 히스토그램이 계속 확장되어 histSlope > 0 보장
            val close = (1..80).map { it.toDouble() * it.toDouble() }

            val result = TechnicalAnalysisEngine.calculateElderImpulse(close)

            assertEquals(close.size, result.impulse.size)
            // EMA와 MACD가 안정화되는 후반 30개 구간에서 bull 신호가 있어야 함
            val tailImpulse = result.impulse.takeLast(30)
            assertTrue(
                tailImpulse.any { it == 1 },
                "Strongly accelerating rising series should produce at least one bull impulse in the last 30 bars"
            )
        }

        @Test
        @DisplayName("단조 감소 데이터: 후반부 impulse는 -1 (bear)")
        fun `calculateElderImpulse_withStronglyFallingPrices_producesBearSignal`() {
            // 가속 하락 데이터: 선형 하락은 MACD 히스토그램이 상수로 수렴하여 bear 신호가 생성되지 않음
            // close[i] = 10000 - i^2 형태로, 낙폭이 매 bar 증가(3, 5, 7, ...씩 추가 하락)
            // → EMA 기울기 음수, MACD 히스토그램 기울기 음수 모두 보장
            val close = (1..80).map { i -> 10000.0 - i.toDouble() * i.toDouble() }

            val result = TechnicalAnalysisEngine.calculateElderImpulse(close)

            assertEquals(close.size, result.impulse.size)
            val tailImpulse = result.impulse.takeLast(30)
            assertTrue(
                tailImpulse.any { it == -1 },
                "Strongly accelerating falling series should produce at least one bear impulse in the last 30 bars"
            )
        }

        @Test
        @DisplayName("impulse 값은 -1, 0, 1 중 하나여야 한다")
        fun `calculateElderImpulse_allImpulseValues_areValid`() {
            val close = listOf(
                100.0, 102.0, 98.0, 101.0, 99.0, 103.0, 97.0, 105.0,
                100.0, 102.0, 98.0, 101.0, 99.0, 103.0, 97.0, 105.0,
                100.0, 102.0, 98.0, 101.0, 99.0, 103.0, 97.0, 105.0,
                100.0, 102.0, 98.0, 101.0, 99.0, 103.0
            )

            val result = TechnicalAnalysisEngine.calculateElderImpulse(close)

            result.impulse.forEachIndexed { i, v ->
                assertTrue(v in listOf(-1, 0, 1), "impulse[$i]=$v is not in {-1, 0, 1}")
            }
        }

        @Test
        @DisplayName("빈 리스트 입력 시 모든 결과 리스트가 비어있다")
        fun `calculateElderImpulse_withEmptyInput_returnsEmptyLists`() {
            val result = TechnicalAnalysisEngine.calculateElderImpulse(emptyList())

            assertTrue(result.ema.isEmpty())
            assertTrue(result.macd.isEmpty())
            assertTrue(result.macdSignal.isEmpty())
            assertTrue(result.macdHist.isEmpty())
            assertTrue(result.impulse.isEmpty())
        }

        @Test
        @DisplayName("결과 리스트 크기가 입력과 동일")
        fun `calculateElderImpulse_resultSize_matchesInputSize`() {
            val close = (1..30).map { it.toDouble() }

            val result = TechnicalAnalysisEngine.calculateElderImpulse(close, emaPeriod = 13)

            assertEquals(close.size, result.ema.size)
            assertEquals(close.size, result.macd.size)
            assertEquals(close.size, result.macdSignal.size)
            assertEquals(close.size, result.macdHist.size)
            assertEquals(close.size, result.impulse.size)
        }

        @Test
        @DisplayName("상승 후 하락 패턴에서 impulse가 혼합(0 포함) 신호를 만든다")
        fun `calculateElderImpulse_withMixedTrend_producesNeutralSignals`() {
            // 첫 25개 상승, 다음 25개 하락 → 전환점 부근은 0(neutral)이어야 함
            val rising  = (1..25).map { it.toDouble() }
            val falling = (24 downTo 0).map { it.toDouble() }
            val close   = rising + falling

            val result = TechnicalAnalysisEngine.calculateElderImpulse(close)

            assertTrue(
                result.impulse.contains(0),
                "Mixed trend should produce at least one neutral impulse"
            )
        }
    }

    // ============================================================
    // calculateDemarkTD 테스트
    // ============================================================

    @Nested
    @DisplayName("calculateDemarkTD — DeMark TD Sequential")
    inner class CalculateDemarkTdTests {

        @Test
        @DisplayName("연속 상승 close: tdSell이 순차 증가, tdBuy는 0")
        fun `calculateDemarkTD_withConsecutiveUpCloses_countsSellSetupCorrectly`() {
            // close[i] > close[i-4] 조건을 충족하도록 단조 증가 시리즈
            // 인덱스 0~3: tdSell=0 (워밍업)
            // 인덱스 4: close[4]=50 > close[0]=10 → tdSell=1
            // 인덱스 5: close[5]=60 > close[1]=20 → tdSell=2
            // ...
            val close = listOf(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0)

            val result = TechnicalAnalysisEngine.calculateDemarkTD(close)

            assertEquals(close.size, result.tdSell.size)
            assertEquals(close.size, result.tdBuy.size)

            // 워밍업: 처음 4개는 0
            for (i in 0..3) {
                assertEquals(0, result.tdSell[i], "tdSell[$i] warmup should be 0")
                assertEquals(0, result.tdBuy[i], "tdBuy[$i] warmup should be 0")
            }
            // 인덱스 4부터 tdSell 순차 증가
            assertEquals(1, result.tdSell[4])
            assertEquals(2, result.tdSell[5])
            assertEquals(3, result.tdSell[6])
            // tdBuy는 0 유지
            for (i in 4 until result.tdBuy.size) {
                assertEquals(0, result.tdBuy[i], "tdBuy[$i] should be 0 during sell setup")
            }
        }

        @Test
        @DisplayName("연속 하락 close: tdBuy가 순차 증가, tdSell은 0")
        fun `calculateDemarkTD_withConsecutiveDownCloses_countsBuySetupCorrectly`() {
            // close[i] < close[i-4]: 단조 감소
            val close = listOf(90.0, 80.0, 70.0, 60.0, 50.0, 40.0, 30.0, 20.0, 10.0)

            val result = TechnicalAnalysisEngine.calculateDemarkTD(close)

            for (i in 0..3) {
                assertEquals(0, result.tdSell[i])
                assertEquals(0, result.tdBuy[i])
            }
            assertEquals(1, result.tdBuy[4])
            assertEquals(2, result.tdBuy[5])
            assertEquals(3, result.tdBuy[6])
            for (i in 4 until result.tdSell.size) {
                assertEquals(0, result.tdSell[i], "tdSell[$i] should be 0 during buy setup")
            }
        }

        @Test
        @DisplayName("close[i] == close[i-4]이면 두 카운트 모두 0으로 리셋")
        fun `calculateDemarkTD_whenEqualPrice_resetsBothCounts`() {
            // 모든 가격이 동일: close[i] == close[i-4] → 항상 else 분기
            val close = List(10) { 100.0 }

            val result = TechnicalAnalysisEngine.calculateDemarkTD(close)

            result.tdSell.forEachIndexed { i, v -> assertEquals(0, v, "tdSell[$i] should be 0") }
            result.tdBuy.forEachIndexed  { i, v -> assertEquals(0, v, "tdBuy[$i] should be 0") }
        }

        @Test
        @DisplayName("데이터가 5개 미만이면 모두 0")
        fun `calculateDemarkTD_whenLessThanFiveDataPoints_returnsAllZeros`() {
            val close = listOf(100.0, 105.0, 110.0, 108.0)

            val result = TechnicalAnalysisEngine.calculateDemarkTD(close)

            assertEquals(close.size, result.tdSell.size)
            assertEquals(close.size, result.tdBuy.size)
            result.tdSell.forEachIndexed { i, v -> assertEquals(0, v, "tdSell[$i] should be 0") }
            result.tdBuy.forEachIndexed  { i, v -> assertEquals(0, v, "tdBuy[$i] should be 0") }
        }

        @Test
        @DisplayName("카운트 전환: sell setup 중 하락 발생 시 buy setup으로 전환")
        fun `calculateDemarkTD_whenTrendReversals_switchesBetweenSetups`() {
            // 인덱스 0~4: 상승 → tdSell 1
            // 인덱스 5: 급락 → tdSell 리셋, tdBuy 시작
            val close = listOf(
                10.0, 20.0, 30.0, 40.0,   // 워밍업 (0~3)
                50.0,                       // index 4: close > close[0]=10 → tdSell=1
                5.0                         // index 5: close < close[1]=20 → tdBuy=1, tdSell=0
            )

            val result = TechnicalAnalysisEngine.calculateDemarkTD(close)

            assertEquals(1, result.tdSell[4], "tdSell[4] should be 1")
            assertEquals(0, result.tdBuy[4],  "tdBuy[4] should be 0")
            assertEquals(0, result.tdSell[5], "tdSell[5] should reset to 0")
            assertEquals(1, result.tdBuy[5],  "tdBuy[5] should be 1")
        }
    }

    // ============================================================
    // generateSignals 테스트
    // ============================================================

    @Nested
    @DisplayName("generateSignals — 매수/매도 신호 생성")
    inner class GenerateSignalsTests {

        @Test
        @DisplayName("빈 입력은 모든 결과 리스트가 비어있는 SignalResult 반환")
        fun `generateSignals_withEmptyInput_returnsEmptySignalResult`() {
            val result = TechnicalAnalysisEngine.generateSignals(
                dates = emptyList(),
                high = emptyList(), low = emptyList(), close = emptyList(),
                volume = emptyList()
            )

            assertTrue(result.dates.isEmpty())
            assertTrue(result.ma.isEmpty())
            assertTrue(result.cmf.isEmpty())
            assertTrue(result.fearGreed.isEmpty())
            assertTrue(result.buySignal.isEmpty())
            assertTrue(result.auxBuySignal.isEmpty())
            assertTrue(result.sellSignal.isEmpty())
            assertTrue(result.auxSellSignal.isEmpty())
        }

        @Test
        @DisplayName("결과 크기가 입력 날짜 수와 동일")
        fun `generateSignals_resultSize_matchesInputDatesSize`() {
            val n = 30
            val dates  = (1..n).map { "202501${it.toString().padStart(2, '0')}" }
            val high   = List(n) { 110.0 + it }
            val low    = List(n) { 90.0  + it }
            val close  = List(n) { 100.0 + it }
            val volume = List(n) { 1000L }

            val result = TechnicalAnalysisEngine.generateSignals(dates, high, low, close, volume)

            assertEquals(n, result.dates.size)
            assertEquals(n, result.ma.size)
            assertEquals(n, result.cmf.size)
            assertEquals(n, result.buySignal.size)
            assertEquals(n, result.auxBuySignal.size)
            assertEquals(n, result.sellSignal.size)
            assertEquals(n, result.auxSellSignal.size)
        }

        @Test
        @DisplayName("신호 값은 0 또는 1이어야 한다")
        fun `generateSignals_allSignalValues_areZeroOrOne`() {
            val n = 30
            val dates  = (1..n).map { "202501${it.toString().padStart(2, '0')}" }
            val high   = List(n) { 100.0 + it * 2 }
            val low    = List(n) { 100.0 + it }
            val close  = List(n) { 100.0 + it + 0.5 }
            val volume = List(n) { 1000L }

            val result = TechnicalAnalysisEngine.generateSignals(dates, high, low, close, volume)

            result.buySignal.forEachIndexed    { i, v -> assertTrue(v in 0..1, "buySignal[$i]=$v") }
            result.auxBuySignal.forEachIndexed { i, v -> assertTrue(v in 0..1, "auxBuySignal[$i]=$v") }
            result.sellSignal.forEachIndexed   { i, v -> assertTrue(v in 0..1, "sellSignal[$i]=$v") }
            result.auxSellSignal.forEachIndexed{ i, v -> assertTrue(v in 0..1, "auxSellSignal[$i]=$v") }
        }

        @Test
        @DisplayName("강한 상승장 조건: buySignal 발생 확인")
        fun `generateSignals_withBullishConditions_producesBuySignal`() {
            // 강한 상승 시나리오: high 매일 돌파, close > MA, CMF 양수 유도
            // 충분한 데이터(30일)로 MA 워밍업 완료 후 신호 검증
            val n = 40
            // 단조 상승: high 매일 신고가, close > MA 보장
            val high   = (1..n).map { 100.0 + it * 2.0 }
            val low    = (1..n).map { 100.0 + it * 1.0 }
            // close = high (MFM = 1.0 → CMF 양수)
            val close  = high
            val volume = List(n) { 1000L }
            val dates  = (1..n).map { "202501${it.toString().padStart(2, '0')}" }

            val result = TechnicalAnalysisEngine.generateSignals(
                dates, high, low, close, volume, maPeriod = 20, cmfPeriod = 4
            )

            // MA 워밍업 후 (인덱스 19 이후) 적어도 하나의 buy 신호가 있어야 함
            val buyAfterWarmup = result.buySignal.drop(20)
            assertTrue(
                buyAfterWarmup.any { it == 1 },
                "Strong bullish conditions should produce at least one buy signal after warmup"
            )
        }

        @Test
        @DisplayName("강한 하락장 조건: sellSignal 발생 확인")
        fun `generateSignals_withBearishConditions_producesSellSignal`() {
            // 강한 하락 시나리오: low 매일 신저가, close < MA, CMF 음수 유도
            val n = 40
            val high   = (1..n).map { 200.0 - it * 1.0 }
            val low    = (1..n).map { 200.0 - it * 2.0 }
            // close = low (MFM = -1.0 → CMF 음수)
            val close  = low
            val volume = List(n) { 1000L }
            val dates  = (1..n).map { "202501${it.toString().padStart(2, '0')}" }

            val result = TechnicalAnalysisEngine.generateSignals(
                dates, high, low, close, volume, maPeriod = 20, cmfPeriod = 4
            )

            val sellAfterWarmup = result.sellSignal.drop(20)
            assertTrue(
                sellAfterWarmup.any { it == 1 },
                "Strong bearish conditions should produce at least one sell signal after warmup"
            )
        }

        @Test
        @DisplayName("buy와 sell 신호는 동일 인덱스에서 동시에 1이 될 수 없다")
        fun `generateSignals_buyAndSellSignal_neverBothOneAtSameIndex`() {
            val n = 30
            val dates  = (1..n).map { "202501${it.toString().padStart(2, '0')}" }
            val high   = List(n) { 110.0 }
            val low    = List(n) { 90.0  }
            val close  = List(n) { 100.0 }
            val volume = List(n) { 1000L }

            val result = TechnicalAnalysisEngine.generateSignals(dates, high, low, close, volume)

            for (i in result.dates.indices) {
                val bothBuy  = result.buySignal[i] == 1 && result.sellSignal[i] == 1
                assertFalse(bothBuy, "buySignal and sellSignal cannot both be 1 at index $i")
            }
        }

        @Test
        @DisplayName("MA 워밍업 전 (처음 maPeriod-1개) MA는 0.0")
        fun `generateSignals_beforeMaWarmup_maIsZero`() {
            val n = 30
            val dates  = (1..n).map { "202501${it.toString().padStart(2, '0')}" }
            val high   = List(n) { 110.0 }
            val low    = List(n) { 90.0  }
            val close  = List(n) { 100.0 }
            val volume = List(n) { 1000L }
            val maPeriod = 20

            val result = TechnicalAnalysisEngine.generateSignals(
                dates, high, low, close, volume, maPeriod = maPeriod
            )

            for (i in 0 until maPeriod - 1) {
                assertEquals(0.0, result.ma[i], 1e-9, "MA[$i] before warmup should be 0.0")
            }
        }
    }

    // ============================================================
    // 경계값 및 통합 테스트
    // ============================================================

    @Nested
    @DisplayName("경계값 및 불변성 테스트")
    inner class BoundaryAndInvariantTests {

        @Test
        @DisplayName("모든 함수가 단일 데이터 포인트를 안전하게 처리")
        fun `allFunctions_withSingleDataPoint_handleGracefully`() {
            val d = listOf("20250101")
            val v = listOf(100.0)
            val vol = listOf(1000L)

            // EMA
            val ema = TechnicalAnalysisEngine.calculateEMA(v, period = 5)
            assertEquals(1, ema.size)
            assertEquals(100.0, ema[0], 1e-9)

            // resampleWeekly
            val weekly = TechnicalAnalysisEngine.resampleWeekly(d, v, v, v, v, vol)
            assertEquals(1, weekly.size)

            // resampleMonthly
            val monthly = TechnicalAnalysisEngine.resampleMonthly(d, v, v, v, v, vol)
            assertEquals(1, monthly.size)

            // CMF (size < period → all zeros)
            val cmf = TechnicalAnalysisEngine.calculateCMF(v, v, v, vol, period = 4)
            assertEquals(1, cmf.size)
            assertEquals(0.0, cmf[0], 1e-9)

            // DemarkTD (size < 5 → all zeros)
            val demark = TechnicalAnalysisEngine.calculateDemarkTD(v)
            assertEquals(1, demark.tdSell.size)
            assertEquals(0, demark.tdSell[0])
        }

        @Test
        @DisplayName("rollingSum — period=5, 정확한 슬라이딩 윈도우 계산")
        fun `rollingSum_period5_slidingWindowCorrect`() {
            val values = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L)

            val result = TechnicalAnalysisEngine.rollingSum(values, period = 5)

            // index 0~3: 0
            // index 4: 1+2+3+4+5=15
            // index 5: 2+3+4+5+6=20
            // index 6: 3+4+5+6+7=25
            assertEquals(7, result.size)
            assertEquals(0L,  result[0])
            assertEquals(0L,  result[1])
            assertEquals(0L,  result[2])
            assertEquals(0L,  result[3])
            assertEquals(15L, result[4])
            assertEquals(20L, result[5])
            assertEquals(25L, result[6])
        }

        @Test
        @DisplayName("EMA 크기가 항상 입력 크기와 같다")
        fun `calculateEMA_outputSize_alwaysMatchesInput`() {
            listOf(1, 5, 10, 20).forEach { n ->
                val values = List(n) { it.toDouble() + 1 }
                val result = TechnicalAnalysisEngine.calculateEMA(values, period = 5)
                assertEquals(n, result.size, "EMA size mismatch for n=$n")
            }
        }
    }
}

// 부정 단언 헬퍼 (kotlin.test에 assertFalse 없는 경우 대비)
private fun assertFalse(condition: Boolean, message: String) {
    if (condition) throw AssertionError(message)
}
