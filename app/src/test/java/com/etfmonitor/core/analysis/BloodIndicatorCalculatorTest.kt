package com.etfmonitor.core.analysis

import com.etfmonitor.core.network.blood.BloodIndicatorClient
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * BloodIndicatorCalculator 단위 테스트
 *
 * 테스트 범위:
 * - getWeekEndingFriday: 요일별 금요일 계산
 * - resampleWeeklyFriday: 일별 → 주별(금요일) 리샘플링
 * - forwardFillToIndex: 희소 데이터 전방 채움
 * - rollingMean: 롤링 평균 (NaN 처리 포함)
 * - calcSignal: SMA 대비 신호 생성 (RISK_ON / RISK_OFF / NEUTRAL)
 * - calculate: 종단간 Blood Indicator 계산
 */
@DisplayName("BloodIndicatorCalculator 테스트")
class BloodIndicatorCalculatorTest {

    // ── 헬퍼 함수 ────────────────────────────────────────────────────────────

    private fun point(date: LocalDate, value: Double) =
        BloodIndicatorClient.DailyDataPoint(date = date, value = value)

    private fun point(dateStr: String, value: Double) =
        point(LocalDate.parse(dateStr), value)

    // =========================================================================
    // getWeekEndingFriday
    // =========================================================================

    @Nested
    @DisplayName("getWeekEndingFriday — 요일별 금요일 계산")
    inner class GetWeekEndingFridayTests {

        @Test
        fun `월요일은 같은 주 금요일을 반환한다`() {
            // 2026-02-16 은 월요일 → 같은 주 금요일 2026-02-20
            val monday = LocalDate.of(2026, 2, 16)
            val friday = BloodIndicatorCalculator.getWeekEndingFriday(monday)

            assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
            assertEquals(LocalDate.of(2026, 2, 20), friday)
        }

        @Test
        fun `금요일은 자기 자신을 반환한다`() {
            val friday = LocalDate.of(2026, 2, 20)
            val result = BloodIndicatorCalculator.getWeekEndingFriday(friday)

            assertEquals(friday, result)
        }

        @Test
        fun `토요일은 다음 주 금요일을 반환한다`() {
            // 2026-02-21 은 토요일 → 다음 금요일 2026-02-27
            val saturday = LocalDate.of(2026, 2, 21)
            val result = BloodIndicatorCalculator.getWeekEndingFriday(saturday)

            assertEquals(DayOfWeek.FRIDAY, result.dayOfWeek)
            assertEquals(LocalDate.of(2026, 2, 27), result)
        }

        @Test
        fun `일요일은 다음 주 금요일을 반환한다`() {
            // 2026-02-22 은 일요일 → 다음 금요일 2026-02-27
            val sunday = LocalDate.of(2026, 2, 22)
            val result = BloodIndicatorCalculator.getWeekEndingFriday(sunday)

            assertEquals(DayOfWeek.FRIDAY, result.dayOfWeek)
            assertEquals(LocalDate.of(2026, 2, 27), result)
        }

        @Test
        fun `수요일은 같은 주 금요일을 반환한다`() {
            // 2026-02-18 은 수요일 → 같은 주 금요일 2026-02-20
            val wednesday = LocalDate.of(2026, 2, 18)
            val result = BloodIndicatorCalculator.getWeekEndingFriday(wednesday)

            assertEquals(DayOfWeek.FRIDAY, result.dayOfWeek)
            assertEquals(LocalDate.of(2026, 2, 20), result)
        }
    }

    // =========================================================================
    // resampleWeeklyFriday
    // =========================================================================

    @Nested
    @DisplayName("resampleWeeklyFriday — 주별 리샘플링")
    inner class ResampleWeeklyFridayTests {

        @Test
        fun `일별 데이터를 금요일 버킷으로 올바르게 그룹화한다`() {
            // 2026-02-16(월), 2026-02-17(화), 2026-02-18(수) → 모두 2026-02-20(금) 버킷
            val dailyData = listOf(
                point("2026-02-16", 1.0),
                point("2026-02-17", 2.0),
                point("2026-02-18", 3.0)
            )

            val result = BloodIndicatorCalculator.resampleWeeklyFriday(dailyData)

            assertEquals(1, result.size)
            assertEquals(LocalDate.of(2026, 2, 20), result[0].first)
        }

        @Test
        fun `각 주에서 마지막 날짜의 값을 취한다`() {
            // 월(1.0), 화(2.0), 수(3.0) → 마지막 = 3.0
            val dailyData = listOf(
                point("2026-02-16", 1.0),
                point("2026-02-17", 2.0),
                point("2026-02-18", 3.0)
            )

            val result = BloodIndicatorCalculator.resampleWeeklyFriday(dailyData)

            assertEquals(3.0, result[0].second, 1e-10)
        }

        @Test
        fun `빈 입력은 빈 리스트를 반환한다`() {
            val result = BloodIndicatorCalculator.resampleWeeklyFriday(emptyList())

            assertTrue(result.isEmpty())
        }

        @Test
        fun `단일 데이터 포인트는 하나의 항목을 반환한다`() {
            val dailyData = listOf(point("2026-02-18", 5.5))

            val result = BloodIndicatorCalculator.resampleWeeklyFriday(dailyData)

            assertEquals(1, result.size)
            assertEquals(5.5, result[0].second, 1e-10)
        }

        @Test
        fun `여러 주에 걸친 데이터를 정렬된 순서로 반환한다`() {
            // 첫 번째 주: 2026-02-16(월), 2026-02-17(화) → 버킷 2026-02-20(금)
            // 두 번째 주: 2026-02-23(월), 2026-02-24(화) → 버킷 2026-02-27(금)
            val dailyData = listOf(
                point("2026-02-16", 10.0),
                point("2026-02-17", 11.0),
                point("2026-02-23", 20.0),
                point("2026-02-24", 21.0)
            )

            val result = BloodIndicatorCalculator.resampleWeeklyFriday(dailyData)

            assertEquals(2, result.size)
            // 날짜 오름차순 정렬
            assertTrue(result[0].first < result[1].first)
            // 각 주의 마지막 값
            assertEquals(11.0, result[0].second, 1e-10)
            assertEquals(21.0, result[1].second, 1e-10)
        }

        @Test
        fun `금요일 데이터 자체도 올바른 버킷에 들어간다`() {
            val dailyData = listOf(
                point("2026-02-20", 7.7),  // 금요일 자체
                point("2026-02-27", 8.8)   // 다음 주 금요일
            )

            val result = BloodIndicatorCalculator.resampleWeeklyFriday(dailyData)

            assertEquals(2, result.size)
            assertEquals(LocalDate.of(2026, 2, 20), result[0].first)
            assertEquals(LocalDate.of(2026, 2, 27), result[1].first)
        }
    }

    // =========================================================================
    // forwardFillToIndex
    // =========================================================================

    @Nested
    @DisplayName("forwardFillToIndex — 전방 채움")
    inner class ForwardFillToIndexTests {

        @Test
        fun `대상 날짜 사이의 갭을 마지막 알려진 값으로 채운다`() {
            // 데이터: 1월 1일(1.0), 1월 10일(2.0)
            // 타깃: 1월 1일, 1월 5일, 1월 10일
            // → 1월 5일은 1.0(전방 채움), 1월 10일은 2.0
            val data = listOf(
                Pair(LocalDate.of(2026, 1, 1), 1.0),
                Pair(LocalDate.of(2026, 1, 10), 2.0)
            )
            val targets = listOf(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 10)
            )

            val result = BloodIndicatorCalculator.forwardFillToIndex(data, targets)

            assertEquals(3, result.size)
            assertEquals(1.0, result[LocalDate.of(2026, 1, 1)]!!, 1e-10)
            assertEquals(1.0, result[LocalDate.of(2026, 1, 5)]!!, 1e-10)
            assertEquals(2.0, result[LocalDate.of(2026, 1, 10)]!!, 1e-10)
        }

        @Test
        fun `첫 번째 데이터 포인트 이전 날짜는 결과에 포함되지 않는다`() {
            // 데이터: 1월 10일(5.0)
            // 타깃: 1월 5일(데이터 없음), 1월 10일(있음)
            val data = listOf(
                Pair(LocalDate.of(2026, 1, 10), 5.0)
            )
            val targets = listOf(
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 10)
            )

            val result = BloodIndicatorCalculator.forwardFillToIndex(data, targets)

            assertFalse(result.containsKey(LocalDate.of(2026, 1, 5)))
            assertEquals(5.0, result[LocalDate.of(2026, 1, 10)]!!, 1e-10)
        }

        @Test
        fun `빈 데이터는 빈 맵을 반환한다`() {
            val targets = listOf(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 5)
            )

            val result = BloodIndicatorCalculator.forwardFillToIndex(emptyList(), targets)

            assertTrue(result.isEmpty())
        }

        @Test
        fun `모든 대상 날짜가 데이터로 커버되면 정확한 값을 반환한다`() {
            val data = listOf(
                Pair(LocalDate.of(2026, 1, 1), 10.0),
                Pair(LocalDate.of(2026, 1, 8), 20.0),
                Pair(LocalDate.of(2026, 1, 15), 30.0)
            )
            val targets = listOf(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 8),
                LocalDate.of(2026, 1, 15)
            )

            val result = BloodIndicatorCalculator.forwardFillToIndex(data, targets)

            assertEquals(3, result.size)
            assertEquals(10.0, result[LocalDate.of(2026, 1, 1)]!!, 1e-10)
            assertEquals(20.0, result[LocalDate.of(2026, 1, 8)]!!, 1e-10)
            assertEquals(30.0, result[LocalDate.of(2026, 1, 15)]!!, 1e-10)
        }

        @Test
        fun `타깃이 비어있으면 빈 맵을 반환한다`() {
            val data = listOf(
                Pair(LocalDate.of(2026, 1, 1), 1.0)
            )

            val result = BloodIndicatorCalculator.forwardFillToIndex(data, emptyList())

            assertTrue(result.isEmpty())
        }
    }

    // =========================================================================
    // rollingMean
    // =========================================================================

    @Nested
    @DisplayName("rollingMean — 롤링 평균")
    inner class RollingMeanTests {

        @Test
        fun `window=3, 충분한 데이터로 올바른 평균을 계산한다`() {
            // 인덱스별 기대값:
            //   i=0: [1.0]        → avg=1.0  (minPeriods=1 기본값)
            //   i=1: [1.0, 2.0]   → avg=1.5
            //   i=2: [1.0,2.0,3.0]→ avg=2.0
            //   i=3: [2.0,3.0,4.0]→ avg=3.0
            //   i=4: [3.0,4.0,5.0]→ avg=4.0
            val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0)

            val result = BloodIndicatorCalculator.rollingMean(values, window = 3)

            assertEquals(5, result.size)
            assertEquals(1.0, result[0], 1e-10)
            assertEquals(1.5, result[1], 1e-10)
            assertEquals(2.0, result[2], 1e-10)
            assertEquals(3.0, result[3], 1e-10)
            assertEquals(4.0, result[4], 1e-10)
        }

        @Test
        fun `minPeriods 기본값 사용 시 첫 번째 항목부터 유효값 반환`() {
            // 기본 minPeriods=1 → 첫 번째 요소도 NaN이 아님
            val values = listOf(10.0, 20.0, 30.0)

            val result = BloodIndicatorCalculator.rollingMean(values, window = 3)

            assertFalse(result[0].isNaN(), "첫 번째 항목은 NaN이 아니어야 한다 (minPeriods=1)")
            assertEquals(10.0, result[0], 1e-10)
        }

        @Test
        fun `minPeriods=window 설정 시 윈도우 미달 구간은 NaN을 반환한다`() {
            // window=3, minPeriods=3 → i=0,1 은 NaN, i=2 부터 유효
            val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0)

            val result = BloodIndicatorCalculator.rollingMean(values, window = 3, minPeriods = 3)

            assertTrue(result[0].isNaN(), "i=0: 데이터 1개 → NaN (minPeriods=3)")
            assertTrue(result[1].isNaN(), "i=1: 데이터 2개 → NaN (minPeriods=3)")
            assertFalse(result[2].isNaN(), "i=2: 데이터 3개 → 유효값")
            assertEquals(2.0, result[2], 1e-10)
        }

        @Test
        fun `minPeriods=1 설정 시 첫 번째 값부터 평균을 반환한다`() {
            val values = listOf(5.0, 10.0, 15.0)

            val result = BloodIndicatorCalculator.rollingMean(values, window = 100, minPeriods = 1)

            // window=100 이지만 데이터가 3개뿐이므로 전체 데이터를 사용
            assertEquals(5.0, result[0], 1e-10)           // avg([5.0])
            assertEquals(7.5, result[1], 1e-10)           // avg([5.0, 10.0])
            assertEquals(10.0, result[2], 1e-10)          // avg([5.0, 10.0, 15.0])
        }

        @Test
        fun `NaN 값은 평균 계산에서 제외된다`() {
            // [1.0, NaN, 3.0, 4.0, 5.0], window=3
            // i=2: 윈도우=[1.0, NaN, 3.0] → NaN 제외 → avg(1.0, 3.0) = 2.0
            // i=3: 윈도우=[NaN, 3.0, 4.0] → NaN 제외 → avg(3.0, 4.0) = 3.5
            val values = listOf(1.0, Double.NaN, 3.0, 4.0, 5.0)

            val result = BloodIndicatorCalculator.rollingMean(values, window = 3, minPeriods = 1)

            assertEquals(1.0, result[0], 1e-10)
            assertEquals(1.0, result[1], 1e-10)      // NaN 제외 → avg([1.0]) = 1.0
            assertEquals(2.0, result[2], 1e-10)      // avg([1.0, 3.0]) = 2.0
            assertEquals(3.5, result[3], 1e-10)      // avg([3.0, 4.0]) = 3.5
            assertEquals(4.0, result[4], 1e-10)      // avg([3.0, 4.0, 5.0]) = 4.0
        }

        @Test
        fun `빈 입력은 빈 리스트를 반환한다`() {
            val result = BloodIndicatorCalculator.rollingMean(emptyList(), window = 3)

            assertTrue(result.isEmpty())
        }

        @Test
        fun `단일 값 입력은 단일 결과를 반환한다`() {
            val result = BloodIndicatorCalculator.rollingMean(listOf(42.0), window = 3, minPeriods = 1)

            assertEquals(1, result.size)
            assertEquals(42.0, result[0], 1e-10)
        }

        @Test
        fun `모든 값이 NaN이고 minPeriods=1이면 결과도 NaN이다`() {
            val values = listOf(Double.NaN, Double.NaN, Double.NaN)

            val result = BloodIndicatorCalculator.rollingMean(values, window = 3, minPeriods = 1)

            // 유효 값 0개 < minPeriods=1 → NaN
            assertTrue(result.all { it.isNaN() })
        }
    }

    // =========================================================================
    // calcSignal
    // =========================================================================

    @Nested
    @DisplayName("calcSignal — RISK_ON / RISK_OFF / NEUTRAL 신호 생성")
    inner class CalcSignalTests {

        @Test
        fun `혈액 값이 SMA보다 크면 RISK_ON과 green을 반환한다`() {
            val (signalType, signalColor) = BloodIndicatorCalculator.calcSignal(
                bloodValue = 1.5,
                smaValue = 1.0
            )

            assertEquals("RISK_ON", signalType)
            assertEquals("green", signalColor)
        }

        @Test
        fun `혈액 값이 SMA보다 작으면 RISK_OFF와 red를 반환한다`() {
            val (signalType, signalColor) = BloodIndicatorCalculator.calcSignal(
                bloodValue = 0.8,
                smaValue = 1.0
            )

            assertEquals("RISK_OFF", signalType)
            assertEquals("red", signalColor)
        }

        @Test
        fun `SMA가 NaN이면 NEUTRAL과 gray를 반환한다`() {
            val (signalType, signalColor) = BloodIndicatorCalculator.calcSignal(
                bloodValue = 1.0,
                smaValue = Double.NaN
            )

            assertEquals("NEUTRAL", signalType)
            assertEquals("gray", signalColor)
        }

        @Test
        fun `혈액 값이 SMA와 정확히 같으면 RISK_OFF와 red를 반환한다`() {
            // bloodValue > smaValue 가 아니므로 RISK_OFF
            val (signalType, signalColor) = BloodIndicatorCalculator.calcSignal(
                bloodValue = 1.0,
                smaValue = 1.0
            )

            assertEquals("RISK_OFF", signalType)
            assertEquals("red", signalColor)
        }

        @Test
        fun `음수 혈액 값과 더 작은 SMA에서도 RISK_ON을 반환한다`() {
            val (signalType, signalColor) = BloodIndicatorCalculator.calcSignal(
                bloodValue = -0.5,
                smaValue = -1.0
            )

            assertEquals("RISK_ON", signalType)
            assertEquals("green", signalColor)
        }
    }

    // =========================================================================
    // calculate — 종단간 통합 테스트
    // =========================================================================

    @Nested
    @DisplayName("calculate — 종단간 Blood Indicator 계산")
    inner class CalculateTests {

        /**
         * 소규모 알려진 데이터셋으로 예측 가능한 결과를 검증한다.
         *
         * 시나리오:
         * - IRX:    월요일 5개 → 금요일 버킷 5개 (주마다 1개 데이터)
         * - Spread: 각 금요일에 정확히 매핑 → forward fill 없이 직접 커버
         * - BLOOD = US03MY / HighYieldSpread
         * - 날짜 범위: 전체 포함
         *
         * 주간 데이터 (모두 월요일이므로 해당 주 금요일로 리샘플됨):
         *   주1: IRX=4.0, Spread=2.0 → Blood=2.0
         *   주2: IRX=5.0, Spread=2.5 → Blood=2.0
         *   주3: IRX=6.0, Spread=3.0 → Blood=2.0
         *   주4: IRX=3.0, Spread=1.5 → Blood=2.0
         *   주5: IRX=8.0, Spread=2.0 → Blood=4.0
         *
         * minPeriods=1이므로 SMA는 항상 계산된다.
         * 처음 4주: Blood=2.0, SMA=2.0 → 같음(RISK_OFF, red)
         * 5주차: Blood=4.0, SMA=avg(2.0,2.0,2.0,2.0,4.0)=2.4 → RISK_ON, green
         */
        @Test
        fun `알려진 소규모 데이터셋으로 예측 가능한 결과를 생성한다`() {
            // 5주치 데이터: 모두 월요일 (→ 각 주의 금요일 버킷으로 리샘플)
            val irxData = listOf(
                point("2026-01-05", 4.0),  // 월 → 금 2026-01-09
                point("2026-01-12", 5.0),  // 월 → 금 2026-01-16
                point("2026-01-19", 6.0),  // 월 → 금 2026-01-23
                point("2026-01-26", 3.0),  // 월 → 금 2026-01-30
                point("2026-02-02", 8.0)   // 월 → 금 2026-02-06
            )
            val spreadData = listOf(
                point("2026-01-05", 2.0),
                point("2026-01-12", 2.5),
                point("2026-01-19", 3.0),
                point("2026-01-26", 1.5),
                point("2026-02-02", 2.0)
            )

            val start = LocalDate.of(2026, 1, 1)
            val end = LocalDate.of(2026, 2, 28)

            val result = BloodIndicatorCalculator.calculate(
                irxDaily = irxData,
                spreadDaily = spreadData,
                spyDaily = null,
                requestedStart = start,
                requestedEnd = end
            )

            assertEquals(5, result.size, "5주치 데이터이므로 5개 결과 기대")

            // 모든 결과의 날짜는 금요일이어야 한다
            result.forEach { weekly ->
                assertEquals(
                    DayOfWeek.FRIDAY, weekly.date.dayOfWeek,
                    "날짜 ${weekly.date}는 금요일이어야 한다"
                )
            }

            // 처음 4주: Blood=2.0 (US03MY/Spread), SMA에 의해 결정
            // 5주차: Blood=4.0 → SMA보다 크므로 RISK_ON
            val lastWeek = result.last()
            assertEquals(4.0, lastWeek.bloodValue, 1e-10)
            assertEquals("RISK_ON", lastWeek.signalType)
            assertEquals("green", lastWeek.signalColor)
        }

        @Test
        fun `IRX 데이터가 비어있으면 빈 리스트를 반환한다`() {
            val spreadData = listOf(point("2026-01-05", 2.0))

            val result = BloodIndicatorCalculator.calculate(
                irxDaily = emptyList(),
                spreadDaily = spreadData,
                spyDaily = null,
                requestedStart = LocalDate.of(2026, 1, 1),
                requestedEnd = LocalDate.of(2026, 1, 31)
            )

            assertTrue(result.isEmpty())
        }

        @Test
        fun `Spread 데이터가 비어있으면 빈 리스트를 반환한다`() {
            val irxData = listOf(point("2026-01-05", 4.0))

            val result = BloodIndicatorCalculator.calculate(
                irxDaily = irxData,
                spreadDaily = emptyList(),
                spyDaily = null,
                requestedStart = LocalDate.of(2026, 1, 1),
                requestedEnd = LocalDate.of(2026, 1, 31)
            )

            assertTrue(result.isEmpty())
        }

        @Test
        fun `요청 날짜 범위 외 데이터는 결과에 포함되지 않는다`() {
            // 3주치 데이터를 생성하되 날짜 범위를 1주치만 커버
            val irxData = listOf(
                point("2026-01-05", 4.0),  // → 금 2026-01-09 (범위 밖)
                point("2026-01-12", 5.0),  // → 금 2026-01-16 (범위 안)
                point("2026-01-19", 6.0)   // → 금 2026-01-23 (범위 밖)
            )
            val spreadData = listOf(
                point("2026-01-05", 2.0),
                point("2026-01-12", 2.5),
                point("2026-01-19", 3.0)
            )

            // 범위: 2026-01-12 ~ 2026-01-18 → 버킷 2026-01-16(금)만 포함
            val result = BloodIndicatorCalculator.calculate(
                irxDaily = irxData,
                spreadDaily = spreadData,
                spyDaily = null,
                requestedStart = LocalDate.of(2026, 1, 12),
                requestedEnd = LocalDate.of(2026, 1, 18)
            )

            assertEquals(1, result.size)
            assertEquals(LocalDate.of(2026, 1, 16), result[0].date)
        }

        @Test
        fun `SPY 데이터가 제공되면 spyClose에 값이 채워진다`() {
            val irxData = listOf(point("2026-01-05", 4.0))
            val spreadData = listOf(point("2026-01-05", 2.0))
            val spyData = listOf(point("2026-01-05", 500.0))

            val result = BloodIndicatorCalculator.calculate(
                irxDaily = irxData,
                spreadDaily = spreadData,
                spyDaily = spyData,
                requestedStart = LocalDate.of(2026, 1, 1),
                requestedEnd = LocalDate.of(2026, 1, 31)
            )

            assertEquals(1, result.size)
            assertEquals(500.0, result[0].spyClose!!, 1e-10)
        }

        @Test
        fun `SPY 데이터가 null이면 spyClose도 null이다`() {
            val irxData = listOf(point("2026-01-05", 4.0))
            val spreadData = listOf(point("2026-01-05", 2.0))

            val result = BloodIndicatorCalculator.calculate(
                irxDaily = irxData,
                spreadDaily = spreadData,
                spyDaily = null,
                requestedStart = LocalDate.of(2026, 1, 1),
                requestedEnd = LocalDate.of(2026, 1, 31)
            )

            assertEquals(1, result.size)
            assertEquals(null, result[0].spyClose)
        }

        @Test
        fun `Spread가 0에 가까우면 해당 주는 결과에서 제외된다`() {
            // Spread = 0.005 < 0.01 → Blood = NaN → 해당 주 제외
            val irxData = listOf(
                point("2026-01-05", 4.0),  // Spread 거의 0 → NaN
                point("2026-01-12", 5.0)   // Spread 정상 → 포함
            )
            val spreadData = listOf(
                point("2026-01-05", 0.005),
                point("2026-01-12", 2.5)
            )

            val result = BloodIndicatorCalculator.calculate(
                irxDaily = irxData,
                spreadDaily = spreadData,
                spyDaily = null,
                requestedStart = LocalDate.of(2026, 1, 1),
                requestedEnd = LocalDate.of(2026, 1, 31)
            )

            // 첫 번째 주는 Blood=NaN으로 제외되어야 한다
            assertEquals(1, result.size)
            assertEquals(LocalDate.of(2026, 1, 16), result[0].date)
        }

        @Test
        fun `BloodWeeklyData 필드가 올바르게 채워진다`() {
            val irxData = listOf(point("2026-01-05", 4.0))
            val spreadData = listOf(point("2026-01-05", 2.0))

            val result = BloodIndicatorCalculator.calculate(
                irxDaily = irxData,
                spreadDaily = spreadData,
                spyDaily = null,
                requestedStart = LocalDate.of(2026, 1, 1),
                requestedEnd = LocalDate.of(2026, 1, 31)
            )

            assertEquals(1, result.size)
            val weekly = result[0]
            // us03my = 마지막 IRX 값 = 4.0
            assertEquals(4.0, weekly.us03my, 1e-10)
            // highYieldSpread = 마지막 Spread 값 = 2.0
            assertEquals(2.0, weekly.highYieldSpread, 1e-10)
            // bloodValue = 4.0 / 2.0 = 2.0
            assertEquals(2.0, weekly.bloodValue, 1e-10)
            // bloodSma는 NaN이 아니어야 한다 (minPeriods=1)
            assertFalse(weekly.bloodSma.isNaN())
            // signalType과 signalColor는 유효한 문자열
            assertTrue(weekly.signalType in listOf("RISK_ON", "RISK_OFF", "NEUTRAL"))
            assertTrue(weekly.signalColor in listOf("green", "red", "gray"))
        }

        @Test
        fun `100주 SMA 이후 데이터는 전체 윈도우 평균을 사용한다`() {
            // 105주치 데이터 생성: 처음 100주는 Blood=2.0, 이후 5주는 Blood=4.0
            // 101번째 주의 SMA = avg(2.0 * 100) = 2.0 → 이후 점진적 상승
            val irxData = mutableListOf<BloodIndicatorClient.DailyDataPoint>()
            val spreadData = mutableListOf<BloodIndicatorClient.DailyDataPoint>()

            val baseDate = LocalDate.of(2023, 1, 2)  // 월요일
            for (i in 0 until 100) {
                val date = baseDate.plusWeeks(i.toLong())
                irxData.add(point(date, 4.0))
                spreadData.add(point(date, 2.0))  // Blood = 2.0
            }
            for (i in 100 until 105) {
                val date = baseDate.plusWeeks(i.toLong())
                irxData.add(point(date, 8.0))
                spreadData.add(point(date, 2.0))  // Blood = 4.0
            }

            val start = LocalDate.of(2023, 1, 1)
            val end = LocalDate.of(2025, 12, 31)

            val result = BloodIndicatorCalculator.calculate(
                irxDaily = irxData,
                spreadDaily = spreadData,
                spyDaily = null,
                requestedStart = start,
                requestedEnd = end
            )

            assertTrue(result.size >= 105, "최소 105주 결과 기대")

            // 마지막 5주는 Blood=4.0 이고 SMA는 약 2.x (대부분 2.0에 가까움)
            // 따라서 마지막 5주는 모두 RISK_ON 이어야 한다
            val lastFive = result.takeLast(5)
            lastFive.forEach { weekly ->
                assertEquals(
                    "RISK_ON", weekly.signalType,
                    "Blood=4.0 > SMA(≈2.x) → RISK_ON 기대, 날짜=${weekly.date}"
                )
                assertEquals("green", weekly.signalColor)
            }
        }
    }
}
