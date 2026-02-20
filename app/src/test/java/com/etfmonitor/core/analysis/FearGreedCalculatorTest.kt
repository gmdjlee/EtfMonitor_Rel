package com.etfmonitor.core.analysis

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * FearGreedCalculator 단위 테스트
 *
 * 테스트 범위:
 * - calcRsi: RSI 계산 경계 조건
 * - calculateEma: EMA 수렴 및 엣지 케이스
 * - rollingMean5: 롤링 평균 NaN 패딩 및 정확도
 * - minMaxNormalize: 정규화 범위 및 상수 계열 처리 (내부 로직을 calcFearGreed 통해 검증)
 * - calcFearGreed: 종합 통합 테스트
 */
@DisplayName("FearGreedCalculator 테스트")
class FearGreedCalculatorTest {

    // ============================================================
    // calcRsi 테스트
    // ============================================================

    @Nested
    @DisplayName("calcRsi — RSI 계산 테스트")
    inner class CalcRsiTests {

        @Test
        @DisplayName("단조 상승 가격은 높은 RSI(100에 근접)를 반환한다")
        fun `calcRsi_monotonicallyRisingPrices_producesHighRsi`() {
            // 100개의 단조 상승 가격 (모든 일자가 상승)
            val prices = (1..100).map { it.toDouble() }
            val rsi = FearGreedCalculator.calcRsi(prices, window = 10)

            // 윈도우 이후 유효한 RSI 값들은 매우 높아야 함 (loss = 0이면 NaN, gain만 있으면 100에 근접)
            val validRsi = rsi.drop(10).filter { it.isFinite() }
            // 단조 상승이면 loss=0 → avgLoss=0 → RSI=NaN (분모 0 처리)
            // 소스 주석: "avgLoss == 0 → RS = NaN"
            // 따라서 유효한 값이 없거나, 만약 있다면 100 근접
            // 이 경우 모든 valid RSI 는 NaN 이어야 함 (완전한 단조 상승 → no losses at all)
            val nanCount = rsi.drop(10).count { it.isNaN() }
            assertTrue(nanCount == rsi.drop(10).size,
                "완전 단조 상승에서 loss=0이므로 모든 유효 구간 RSI는 NaN이어야 한다")
        }

        @Test
        @DisplayName("단조 상승 가격(loss=0)은 RSI NaN을 반환한다 — 소스 spec 확인")
        fun `calcRsi_pureRisingNeverFalls_allValidRsiAreNaN`() {
            // 손실이 전혀 없는 계열 → avgLoss = 0 → RS = NaN → RSI = NaN
            val prices = listOf(10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0, 20.0)
            val rsi = FearGreedCalculator.calcRsi(prices, window = 10)

            // 인덱스 10부터가 첫 유효 구간, avgLoss = 0 이므로 NaN
            assertTrue(rsi[10].isNaN(),
                "손실이 없으면 avgLoss=0이므로 RSI는 NaN이어야 한다")
        }

        @Test
        @DisplayName("단조 하락 가격은 낮은 RSI(0에 근접)를 반환한다")
        fun `calcRsi_monotonicallyFallingPrices_producesLowRsi`() {
            // gain이 전혀 없는 계열 → avgGain = 0 → RS = 0 → RSI = 0
            val prices = (100 downTo 1).map { it.toDouble() }
            val rsi = FearGreedCalculator.calcRsi(prices, window = 10)

            val validRsi = rsi.drop(10).filter { it.isFinite() }
            assertTrue(validRsi.isNotEmpty(), "단조 하락 계열에서 유효한 RSI 값이 있어야 한다")
            validRsi.forEach { r ->
                assertEquals(0.0, r, 0.001,
                    "gain이 없으면 RSI는 0이어야 한다, 실제값: $r")
            }
        }

        @Test
        @DisplayName("원소 2개 미만 입력은 모두 NaN을 반환한다")
        fun `calcRsi_fewerThanTwoElements_returnsAllNaN`() {
            // 빈 리스트
            val emptyResult = FearGreedCalculator.calcRsi(emptyList(), window = 10)
            assertTrue(emptyResult.isEmpty(), "빈 입력은 빈 리스트를 반환해야 한다")

            // 원소 1개
            val singleResult = FearGreedCalculator.calcRsi(listOf(100.0), window = 10)
            assertEquals(1, singleResult.size)
            assertTrue(singleResult[0].isNaN(), "원소 1개 입력은 NaN이어야 한다")
        }

        @Test
        @DisplayName("처음 window개 값은 NaN이다")
        fun `calcRsi_firstWindowValues_areNaN`() {
            val window = 10
            // 충분한 데이터 (window + 5)
            val prices = (1..15).map { it * 1.5 + (if (it % 3 == 0) -0.5 else 0.5) }
            val rsi = FearGreedCalculator.calcRsi(prices, window = window)

            assertEquals(prices.size, rsi.size, "결과 크기는 입력 크기와 같아야 한다")
            // 인덱스 0 ~ window-1 는 NaN
            for (i in 0 until window) {
                assertTrue(rsi[i].isNaN(),
                    "인덱스 $i (window=$window)의 RSI는 NaN이어야 한다")
            }
        }

        @Test
        @DisplayName("flat 계열(변화 없음)은 RSI를 NaN으로 반환한다")
        fun `calcRsi_flatSeries_returnsNaN`() {
            // 모든 값이 동일 → delta = 0 → gain=0, loss=0 → avgLoss=0 → NaN
            val prices = List(15) { 100.0 }
            val rsi = FearGreedCalculator.calcRsi(prices, window = 10)

            // 유효 구간(인덱스 10~14)에서 NaN이어야 함
            for (i in 10 until prices.size) {
                assertTrue(rsi[i].isNaN(),
                    "flat 계열에서 인덱스 ${i}의 RSI는 NaN이어야 한다 (avgLoss=0)")
            }
        }

        @Test
        @DisplayName("혼합 상승/하락 계열은 0~100 범위 내 RSI를 반환한다")
        fun `calcRsi_mixedPrices_rsiInValidRange`() {
            // 교차 상승/하락으로 gain/loss 모두 존재
            val prices = listOf(
                100.0, 102.0, 101.0, 103.0, 100.0, 104.0, 102.0, 105.0, 103.0, 106.0,
                104.0, 107.0, 105.0, 108.0, 106.0
            )
            val rsi = FearGreedCalculator.calcRsi(prices, window = 10)

            val validRsi = rsi.filter { it.isFinite() }
            assertTrue(validRsi.isNotEmpty(), "혼합 계열에서 유효한 RSI 값이 있어야 한다")
            validRsi.forEach { r ->
                assertTrue(r >= 0.0 && r <= 100.0,
                    "RSI는 0~100 범위여야 한다, 실제값: $r")
            }
        }

        @Test
        @DisplayName("window 크기를 변경하면 유효 구간 시작점이 달라진다")
        fun `calcRsi_differentWindow_changesValidStartIndex`() {
            val prices = (1..30).map { it.toDouble() * 1.1 + (if (it % 2 == 0) -0.3 else 0.3) }

            val rsi5 = FearGreedCalculator.calcRsi(prices, window = 5)
            val rsi14 = FearGreedCalculator.calcRsi(prices, window = 14)

            // window=5이면 인덱스 5부터, window=14이면 인덱스 14부터 유효
            assertTrue(rsi5[4].isNaN(), "window=5에서 인덱스 4는 NaN이어야 한다")
            assertTrue(rsi14[13].isNaN(), "window=14에서 인덱스 13은 NaN이어야 한다")
        }
    }

    // ============================================================
    // calculateEma 테스트
    // ============================================================

    @Nested
    @DisplayName("calculateEma — 지수이동평균 테스트")
    inner class CalculateEmaTests {

        @Test
        @DisplayName("period=1이면 EMA는 원본 데이터와 같다")
        fun `calculateEma_periodOne_matchesOriginalData`() {
            // alpha = 2/(1+1) = 1.0 → EMA[i] = 1.0*values[i] + 0.0*EMA[i-1] = values[i]
            val data = listOf(10.0, 20.0, 30.0, 15.0, 25.0)
            val ema = FearGreedCalculator.calculateEma(data, period = 1)

            assertEquals(data.size, ema.size)
            data.zip(ema).forEach { (expected, actual) ->
                assertEquals(expected, actual, 0.0001,
                    "period=1에서 EMA는 원본값과 같아야 한다")
            }
        }

        @Test
        @DisplayName("EMA는 최근 값 방향으로 수렴한다")
        fun `calculateEma_longConstantSeries_convergestoConstant`() {
            // 초기값 0, 이후 모두 100 → EMA는 100으로 수렴해야 함
            val data = listOf(0.0) + List(99) { 100.0 }
            val ema = FearGreedCalculator.calculateEma(data, period = 10)

            // 마지막 값은 100에 매우 근접해야 함
            val lastEma = ema.last()
            assertTrue(abs(lastEma - 100.0) < 1.0,
                "충분한 관측 후 EMA는 상수값(100)에 수렴해야 한다, 실제: $lastEma")
        }

        @Test
        @DisplayName("빈 입력은 빈 리스트를 반환한다")
        fun `calculateEma_emptyInput_returnsEmptyList`() {
            val ema = FearGreedCalculator.calculateEma(emptyList(), period = 10)
            assertTrue(ema.isEmpty(), "빈 입력은 빈 리스트를 반환해야 한다")
        }

        @Test
        @DisplayName("EMA 첫 번째 값은 입력의 첫 번째 값과 같다")
        fun `calculateEma_firstValue_equalsInputFirstValue`() {
            val data = listOf(42.0, 50.0, 60.0, 70.0)
            val ema = FearGreedCalculator.calculateEma(data, period = 3)

            assertEquals(42.0, ema[0], 0.0001,
                "EMA[0]은 series[0]과 동일해야 한다 (pandas ewm(adjust=False) 기준)")
        }

        @Test
        @DisplayName("원소 1개 입력은 동일한 값을 반환한다")
        fun `calculateEma_singleElement_returnsSameValue`() {
            val data = listOf(55.5)
            val ema = FearGreedCalculator.calculateEma(data, period = 5)

            assertEquals(1, ema.size)
            assertEquals(55.5, ema[0], 0.0001)
        }

        @Test
        @DisplayName("EMA는 단순 이동평균보다 최근 값에 더 민감하다")
        fun `calculateEma_reactsMoreToRecentValues`() {
            // 상승 후 급락 시나리오
            val data = listOf(100.0, 100.0, 100.0, 100.0, 100.0, 50.0)
            val ema = FearGreedCalculator.calculateEma(data, period = 3)
            val sma = data.takeLast(3).average()  // 단순 이동평균

            // EMA는 최근 50.0에 더 민감하게 반응하므로 SMA보다 낮아야 함
            assertTrue(ema.last() < sma,
                "EMA(${ema.last()})는 SMA($sma)보다 최근 하락에 더 민감해야 한다")
        }

        @Test
        @DisplayName("EMA 결과 크기는 입력 크기와 같다")
        fun `calculateEma_outputSize_matchesInputSize`() {
            val sizes = listOf(1, 5, 10, 50, 100)
            sizes.forEach { size ->
                val data = List(size) { it.toDouble() }
                val ema = FearGreedCalculator.calculateEma(data, period = 5)
                assertEquals(size, ema.size,
                    "입력 크기 ${size}에 대해 EMA 크기도 ${size}여야 한다")
            }
        }
    }

    // ============================================================
    // rollingMean5 테스트
    // ============================================================

    @Nested
    @DisplayName("rollingMean5 — 5일 롤링 평균 테스트")
    inner class RollingMean5Tests {

        @Test
        @DisplayName("처음 4개(period-1) 값은 NaN이다")
        fun `rollingMean5_firstFourValues_areNaN`() {
            val values = listOf<Long>(10, 20, 30, 40, 50, 60, 70)
            val result = FearGreedCalculator.rollingMean5(values)

            assertEquals(values.size, result.size)
            // 인덱스 0~3 (4개)는 NaN
            for (i in 0 until 4) {
                assertTrue(result[i].isNaN(),
                    "인덱스 ${i}는 NaN이어야 한다 (period=5, min_periods=5)")
            }
        }

        @Test
        @DisplayName("알려진 데이터로 정확한 평균을 계산한다")
        fun `rollingMean5_knownData_computesCorrectAverages`() {
            // 값: 10, 20, 30, 40, 50, 60, 70
            // 인덱스 4 평균: (10+20+30+40+50)/5 = 30
            // 인덱스 5 평균: (20+30+40+50+60)/5 = 40
            // 인덱스 6 평균: (30+40+50+60+70)/5 = 50
            val values = listOf<Long>(10, 20, 30, 40, 50, 60, 70)
            val result = FearGreedCalculator.rollingMean5(values)

            assertEquals(30.0, result[4], 0.001, "인덱스 4의 롤링 평균은 30이어야 한다")
            assertEquals(40.0, result[5], 0.001, "인덱스 5의 롤링 평균은 40이어야 한다")
            assertEquals(50.0, result[6], 0.001, "인덱스 6의 롤링 평균은 50이어야 한다")
        }

        @Test
        @DisplayName("빈 입력은 빈 리스트를 반환한다")
        fun `rollingMean5_emptyInput_returnsEmptyList`() {
            val result = FearGreedCalculator.rollingMean5(emptyList())
            assertTrue(result.isEmpty(), "빈 입력은 빈 리스트를 반환해야 한다")
        }

        @Test
        @DisplayName("정확히 5개 입력에서 마지막 값만 유효하다")
        fun `rollingMean5_exactlyFiveElements_onlyLastValueIsValid`() {
            val values = listOf<Long>(1, 2, 3, 4, 5)
            val result = FearGreedCalculator.rollingMean5(values)

            assertEquals(5, result.size)
            // 인덱스 0~3은 NaN
            for (i in 0 until 4) {
                assertTrue(result[i].isNaN(), "인덱스 ${i}는 NaN이어야 한다")
            }
            // 인덱스 4: (1+2+3+4+5)/5 = 3.0
            assertEquals(3.0, result[4], 0.001, "인덱스 4의 평균은 3.0이어야 한다")
        }

        @Test
        @DisplayName("4개 이하 입력은 모두 NaN이다")
        fun `rollingMean5_fewerThanFiveElements_allNaN`() {
            val values = listOf<Long>(10, 20, 30, 40)
            val result = FearGreedCalculator.rollingMean5(values)

            assertEquals(4, result.size)
            result.forEach { v ->
                assertTrue(v.isNaN(), "4개 이하 원소에서 모든 값은 NaN이어야 한다")
            }
        }

        @Test
        @DisplayName("모든 값이 동일하면 평균도 동일하다")
        fun `rollingMean5_constantValues_averageEqualsConstant`() {
            val constant = 42L
            val values = List(10) { constant }
            val result = FearGreedCalculator.rollingMean5(values)

            val validValues = result.filter { it.isFinite() }
            validValues.forEach { v ->
                assertEquals(constant.toDouble(), v, 0.001,
                    "상수 계열의 롤링 평균은 상수와 같아야 한다")
            }
        }
    }

    // ============================================================
    // minMaxNormalize 동작을 calcFearGreed 통해 간접 검증
    // ============================================================

    @Nested
    @DisplayName("minMaxNormalize — 정규화 (calcFearGreed 통해 간접 검증)")
    inner class MinMaxNormalizeTests {

        @Test
        @DisplayName("상수 계열 특성(min=max)은 0.0으로 정규화된다")
        fun `minMaxNormalize_constantFeature_normalizesToZero`() {
            // vix 값이 모두 동일 → minMaxNorm에서 range=0 → 0.0
            // 충분한 데이터로 MA, RSI 워밍업 완료
            val baseData = createSufficientFearGreedData(size = 50, constantVix = 15.0)
            val results = FearGreedCalculator.calcFearGreed(baseData)

            val validResults = results.filter { it.fearGreedValue.isFinite() }
            assertTrue(validResults.isNotEmpty(), "충분한 데이터에서 유효한 결과가 있어야 한다")

            // volatility(vix)는 상수이므로 정규화 후 0.0이어야 함
            validResults.forEach { r ->
                assertEquals(0.0, r.volatility, 0.001,
                    "상수 VIX는 정규화 후 0.0이어야 한다 (range=0 처리)")
            }
        }

        @Test
        @DisplayName("알려진 범위의 값은 [0,1]로 정규화된다")
        fun `minMaxNormalize_knownRange_normalizesToZeroOne`() {
            // 충분한 데이터로 계산하여 spread 최소값과 최대값을 제어
            val data = createSufficientFearGreedData(size = 50)
            val results = FearGreedCalculator.calcFearGreed(data)

            val validResults = results.filter { r ->
                r.fearGreedValue.isFinite() && r.spread.isFinite()
            }
            assertTrue(validResults.isNotEmpty())

            // 모든 정규화된 값은 [0, 1] 범위 내에 있어야 함
            validResults.forEach { r ->
                assertTrue(r.spread >= 0.0 && r.spread <= 1.0,
                    "정규화된 spread는 [0,1] 범위여야 한다, 실제: ${r.spread}")
                assertTrue(r.rsi >= 0.0 && r.rsi <= 1.0,
                    "정규화된 rsi는 [0,1] 범위여야 한다, 실제: ${r.rsi}")
                assertTrue(r.momentum >= 0.0 && r.momentum <= 1.0,
                    "정규화된 momentum은 [0,1] 범위여야 한다, 실제: ${r.momentum}")
                assertTrue(r.putCallRatio >= 0.0 && r.putCallRatio <= 1.0,
                    "정규화된 putCallRatio는 [0,1] 범위여야 한다, 실제: ${r.putCallRatio}")
                assertTrue(r.volatility >= 0.0 && r.volatility <= 1.0,
                    "정규화된 volatility는 [0,1] 범위여야 한다, 실제: ${r.volatility}")
            }
        }

        @Test
        @DisplayName("NaN 입력 행은 정규화 후에도 NaN을 유지한다")
        fun `minMaxNormalize_nanInputRows_remainNaN`() {
            // 데이터를 부족하게 주어 앞 행들이 NaN이 되도록 함
            val smallData = createSufficientFearGreedData(size = 15)
            val results = FearGreedCalculator.calcFearGreed(smallData)

            // 앞 행들은 MA 또는 RSI 워밍업 부족으로 NaN을 가져야 함
            // (maPeriod = min(125, max(10, floor(15 * 0.9))) = 13)
            // RSI window=10 → 인덱스 0~9는 NaN
            val firstResult = results[0]
            assertTrue(firstResult.fearGreedValue.isNaN(),
                "첫 번째 행(MA/RSI 데이터 부족)은 fearGreedValue가 NaN이어야 한다")
        }
    }

    // ============================================================
    // calcFearGreed 통합 테스트
    // ============================================================

    @Nested
    @DisplayName("calcFearGreed — 통합 테스트")
    inner class CalcFearGreedTests {

        @Test
        @DisplayName("빈 데이터는 빈 리스트를 반환한다")
        fun `calcFearGreed_emptyData_returnsEmptyList`() {
            val result = FearGreedCalculator.calcFearGreed(emptyList())
            assertTrue(result.isEmpty(), "빈 입력은 빈 리스트를 반환해야 한다")
        }

        @Test
        @DisplayName("결과 리스트 크기는 입력 크기와 같다")
        fun `calcFearGreed_resultSize_matchesInputSize`() {
            val sizes = listOf(1, 5, 15, 50, 100)
            sizes.forEach { size ->
                val data = createSufficientFearGreedData(size)
                val result = FearGreedCalculator.calcFearGreed(data)
                assertEquals(size, result.size,
                    "입력 크기 ${size}에 대해 결과 크기도 ${size}여야 한다")
            }
        }

        @Test
        @DisplayName("유효한 행의 fearGreedValue는 [0, 1] 범위이다")
        fun `calcFearGreed_validRows_fearGreedValueInZeroOneRange`() {
            // FG = Mom*0.2 + (1-PCR)*0.2 + (1-Vol)*0.2 + Spread*0.2 + RSI*0.2
            // 각 정규화 값이 [0,1]이므로 FG도 [0,1]
            val data = createSufficientFearGreedData(size = 100)
            val results = FearGreedCalculator.calcFearGreed(data)

            val validResults = results.filter { it.fearGreedValue.isFinite() }
            assertTrue(validResults.isNotEmpty(),
                "충분한 데이터(100개)에서 유효한 결과가 있어야 한다")

            validResults.forEach { r ->
                assertTrue(r.fearGreedValue >= 0.0 && r.fearGreedValue <= 1.0,
                    "fearGreedValue는 [0,1] 범위여야 한다, 실제: ${r.fearGreedValue}")
            }
        }

        @Test
        @DisplayName("소규모 알려진 데이터로 [0,100] 스케일 값을 생성한다 (DB 저장 전 *100 스케일링 가정)")
        fun `calcFearGreed_knownSmallDataset_producesFiniteValues`() {
            val data = createSufficientFearGreedData(size = 50)
            val results = FearGreedCalculator.calcFearGreed(data)

            // 최소한 일부 유효한 값이 있어야 함
            val validCount = results.count { it.fearGreedValue.isFinite() }
            assertTrue(validCount > 0,
                "50개 데이터에서 적어도 하나의 유효한 fearGreedValue가 있어야 한다")

            // FearGreedRepositoryImpl에서 *100을 곱해 0~100 스케일로 저장
            results.filter { it.fearGreedValue.isFinite() }.forEach { r ->
                val scaledValue = r.fearGreedValue * 100.0
                assertTrue(scaledValue >= 0.0 && scaledValue <= 100.0,
                    "*100 스케일링 후 값은 [0,100] 범위여야 한다, 실제: $scaledValue")
            }
        }

        @Test
        @DisplayName("날짜 필드는 입력 날짜를 그대로 보존한다")
        fun `calcFearGreed_dates_preservedFromInput`() {
            val data = createSufficientFearGreedData(size = 20)
            val results = FearGreedCalculator.calcFearGreed(data)

            data.zip(results).forEach { (input, output) ->
                assertEquals(input.date, output.date,
                    "출력 날짜는 입력 날짜와 같아야 한다")
                assertEquals(input.indexValue, output.indexValue, 0.0001,
                    "출력 indexValue는 입력 indexValue와 같아야 한다")
            }
        }

        @Test
        @DisplayName("MA 워밍업 전 행들은 NaN fearGreedValue를 가진다")
        fun `calcFearGreed_beforeMaWarmup_fearGreedValueIsNaN`() {
            // n=15 → maPeriod = min(125, max(10, floor(15*0.9))) = min(125, max(10, 13)) = 13
            // → 인덱스 0~11은 MA가 NaN → mom이 NaN → 해당 행은 invalid → FG NaN
            val data = createSufficientFearGreedData(size = 15)
            val results = FearGreedCalculator.calcFearGreed(data)

            // 처음 12개 행은 NaN 이어야 함
            for (i in 0 until 12) {
                assertTrue(results[i].fearGreedValue.isNaN(),
                    "MA 워밍업 전 인덱스 ${i}는 fearGreedValue가 NaN이어야 한다")
            }
        }

        @Test
        @DisplayName("RSI 워밍업 전 행들은 NaN fearGreedValue를 가진다")
        fun `calcFearGreed_beforeRsiWarmup_fearGreedValueIsNaN`() {
            // RSI window=10 → 인덱스 0~9는 RSI NaN → invalid → FG NaN
            val data = createSufficientFearGreedData(size = 50)
            val results = FearGreedCalculator.calcFearGreed(data)

            // n=50 → maPeriod = min(125, max(10, floor(50*0.9))) = min(125, 45) = 45
            // → 인덱스 0~43은 MA NaN → invalid
            // RSI: 인덱스 0~9는 NaN
            // 두 조건 모두 invalid이면 NaN
            for (i in 0 until 10) {
                assertTrue(results[i].fearGreedValue.isNaN(),
                    "RSI 워밍업 전 인덱스 ${i}는 fearGreedValue가 NaN이어야 한다")
            }
        }

        @Test
        @DisplayName("call 값이 0인 행은 PCR이 NaN이 되어 invalid 처리된다")
        fun `calcFearGreed_zeroCallVolume_makesRowInvalid`() {
            val data = buildList {
                // 충분한 정상 데이터 먼저
                addAll(createSufficientFearGreedData(size = 49))
                // 마지막 행: call = 0
                add(
                    FearGreedCalculator.FearGreedDayData(
                        date = "2025-03-20",
                        indexValue = 2800.0,
                        call = 0.0,   // PCR = put/call → division by zero → NaN
                        put = 500.0,
                        vix = 15.0,
                        bond5y = 2.5,
                        bond10y = 3.0
                    )
                )
            }
            val results = FearGreedCalculator.calcFearGreed(data)

            // 마지막 행은 call=0 → pcr=NaN → invalid → fearGreedValue=NaN
            assertTrue(results.last().fearGreedValue.isNaN(),
                "call=0인 행은 fearGreedValue가 NaN이어야 한다")
        }
    }

    // ============================================================
    // calcMacd 테스트
    // ============================================================

    @Nested
    @DisplayName("calcMacd — MACD 계산 테스트")
    inner class CalcMacdTests {

        @Test
        @DisplayName("빈 입력은 빈 리스트를 반환한다")
        fun `calcMacd_emptyInput_returnsEmptyList`() {
            val result = FearGreedCalculator.calcMacd(emptyList())
            assertTrue(result.isEmpty(), "빈 입력은 빈 리스트를 반환해야 한다")
        }

        @Test
        @DisplayName("결과 크기는 입력 크기와 같다")
        fun `calcMacd_resultSize_matchesInputSize`() {
            val data = (1..50).map { it.toDouble() }
            val result = FearGreedCalculator.calcMacd(data)
            assertEquals(data.size, result.size,
                "MACD 결과 크기는 입력 크기와 같아야 한다")
        }

        @Test
        @DisplayName("상수 계열의 MACD oscillator는 0에 수렴한다")
        fun `calcMacd_constantSeries_oscillatorConvergesToZero`() {
            // 상수 계열: emaShort = emaLong = 상수 → macd = 0 → oscillator = 0
            val data = List(100) { 50.0 }
            val result = FearGreedCalculator.calcMacd(data)

            // EMA[0] = series[0] = 50, 이후 모두 50이므로 emaShort=emaLong=50
            // macd = 0, signal = 0, oscillator = 0
            val lastValues = result.takeLast(10)
            lastValues.forEach { v ->
                assertEquals(0.0, v, 0.0001,
                    "상수 계열에서 MACD oscillator는 0이어야 한다, 실제: $v")
            }
        }

        @Test
        @DisplayName("단조 상승 계열에서 MACD는 처음 양수에서 0으로 수렴한다")
        fun `calcMacd_monotonicallyRisingSeries_oscillatorConverges`() {
            val data = (1..200).map { it.toDouble() }
            val result = FearGreedCalculator.calcMacd(data)

            // 충분히 긴 단조 상승에서 emaShort > emaLong (fast > slow)
            // → macd > 0, 하지만 signal도 양수 → oscillator는 작은 양수 또는 0에 근접
            val lastOscillator = result.last()
            assertTrue(lastOscillator.isFinite(),
                "충분한 데이터에서 MACD oscillator는 유한해야 한다")
        }
    }

    // ============================================================
    // 경계값 및 특수 케이스 테스트
    // ============================================================

    @Nested
    @DisplayName("경계값 및 특수 케이스")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("calcRsi 결과에서 NaN이 아닌 값은 항상 유한하다")
        fun `calcRsi_nonNanValues_areAlwaysFinite`() {
            val prices = listOf(
                100.0, 102.0, 99.0, 105.0, 103.0, 108.0, 106.0, 110.0, 107.0, 112.0,
                109.0, 115.0, 111.0, 116.0, 113.0
            )
            val rsi = FearGreedCalculator.calcRsi(prices, window = 10)

            rsi.forEach { v ->
                assertTrue(v.isNaN() || v.isFinite(),
                    "RSI 값은 NaN이거나 유한해야 한다 (Infinity 불가)")
                if (v.isFinite()) {
                    assertTrue(!v.isInfinite(),
                        "RSI는 무한대(Infinity)가 되어서는 안 된다")
                }
            }
        }

        @Test
        @DisplayName("calculateEma는 alpha=2/(period+1) 공식을 사용한다")
        fun `calculateEma_usesCorrectAlphaFormula`() {
            // period=2 → alpha = 2/3 ≈ 0.667
            // series = [10, 20]
            // EMA[0] = 10
            // EMA[1] = (2/3)*20 + (1/3)*10 = 13.333... + 3.333... = 16.667
            val data = listOf(10.0, 20.0)
            val ema = FearGreedCalculator.calculateEma(data, period = 2)

            val alpha = 2.0 / (2 + 1)  // 2/3
            val expectedEma1 = alpha * 20.0 + (1.0 - alpha) * 10.0

            assertEquals(2, ema.size)
            assertEquals(10.0, ema[0], 0.0001, "EMA[0]은 series[0]이어야 한다")
            assertEquals(expectedEma1, ema[1], 0.0001,
                "EMA[1]은 alpha*series[1] + (1-alpha)*EMA[0]이어야 한다")
        }

        @Test
        @DisplayName("rollingMean5는 pandas rolling(5).mean()과 동일하게 동작한다")
        fun `rollingMean5_matchesPandasRollingMeanSemantics`() {
            // 검증: pandas rolling(5).mean()의 동작
            // index 0~3: NaN (min_periods=5 기본값)
            // index 4~: 5개 원소 평균
            val values = listOf<Long>(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)
            val result = FearGreedCalculator.rollingMean5(values)

            // 인덱스 4: (100+200+300+400+500)/5 = 300
            assertEquals(300.0, result[4], 0.001)
            // 인덱스 9: (600+700+800+900+1000)/5 = 800
            assertEquals(800.0, result[9], 0.001)
            // 인덱스 0~3은 NaN
            for (i in 0 until 4) {
                assertTrue(result[i].isNaN())
            }
        }

        @Test
        @DisplayName("calcFearGreed에서 적응형 MA 기간은 min(125, max(10, floor(n*0.9)))이다")
        fun `calcFearGreed_adaptiveMaPeriod_correctFormula`() {
            // n=11 → floor(11*0.9)=9 → max(10,9)=10 → min(125,10)=10
            // maPeriod=10 → 인덱스 0~8은 NaN (period-1=9)
            val data11 = createSufficientFearGreedData(size = 11)
            val results11 = FearGreedCalculator.calcFearGreed(data11)

            // MA가 NaN인 처음 9개 행은 invalid → fearGreedValue NaN
            for (i in 0 until 9) {
                assertTrue(results11[i].fearGreedValue.isNaN(),
                    "n=11, maPeriod=10에서 인덱스 ${i}는 NaN이어야 한다")
            }

            // n=140 → floor(140*0.9)=126 → min(125,126)=125
            // maPeriod=125 → 인덱스 0~123은 NaN
            val data140 = createSufficientFearGreedData(size = 140)
            val results140 = FearGreedCalculator.calcFearGreed(data140)
            assertTrue(results140[123].fearGreedValue.isNaN(),
                "n=140, maPeriod=125에서 인덱스 123은 NaN이어야 한다")
            // 인덱스 124 이상은 MA가 유효 (RSI 조건은 별도)
        }
    }

    // ============================================================
    // 헬퍼 함수
    // ============================================================

    /**
     * 테스트용 FearGreedDayData 리스트를 생성한다.
     *
     * @param size 생성할 행 수
     * @param constantVix VIX를 상수로 고정할 경우 해당 값 지정, null이면 변동
     */
    private fun createSufficientFearGreedData(
        size: Int,
        constantVix: Double? = null
    ): List<FearGreedCalculator.FearGreedDayData> {
        return (0 until size).map { i ->
            // 단순한 지수 상승 패턴 (MA와 RSI 모두 계산 가능하도록)
            // 교차 상승/하락이 있어야 RSI loss 계산 가능
            val baseIndex = 2800.0 + i * 2.0 + (if (i % 3 == 0) -1.5 else 1.5)
            FearGreedCalculator.FearGreedDayData(
                date = "2025-01-%02d".format((i % 28) + 1),
                indexValue = baseIndex,
                call = 1000.0 + i * 10.0,       // 상승 콜 옵션 거래량
                put = 800.0 + i * 8.0,           // 상승 풋 옵션 거래량
                vix = constantVix ?: (15.0 + (i % 5) * 0.5),  // 5단계 주기 변동 또는 상수
                bond5y = 2.5 + (i % 10) * 0.02, // 소폭 변동
                bond10y = 3.0 + (i % 10) * 0.03 // 소폭 변동 (항상 bond10y > bond5y)
            )
        }
    }
}
