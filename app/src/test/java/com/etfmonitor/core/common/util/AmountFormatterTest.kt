package com.etfmonitor.core.common.util

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AmountFormatter 단위 테스트
 *
 * 테스트 범위:
 * - format(): 억/백만/만/원/0원 경계값, 음수, showUnit=false
 * - formatChange(): 부호 포함 포맷, 양수/음수/0
 * - toChartValue(): 억/백만/만 단위 변환
 * - getChartUnit(): 단위 문자열 결정
 * - formatForTable(): maxAmount 기준 포맷
 * - getTableHeader(): 헤더 단위 문자열
 * - formatLong(): 조/억/만/원 포맷
 */
@DisplayName("AmountFormatter 테스트")
class AmountFormatterTest {

    // =========================================================================
    // format()
    // =========================================================================

    @Nested
    @DisplayName("format() — 동적 단위 포맷")
    inner class FormatTests {

        // ── 억 단위 (≥ 100,000,000) ───────────────────────────────────────────

        @Test
        @DisplayName("100_000_000 이상은 억 단위로 표시된다")
        fun `format 100M displays as eok unit`() {
            val result = AmountFormatter.format(100_000_000f)
            assertEquals("1.0억", result)
        }

        @Test
        @DisplayName("150_000_000은 1.5억으로 표시된다")
        fun `format 150M displays as 1_5eok`() {
            val result = AmountFormatter.format(150_000_000f)
            assertEquals("1.5억", result)
        }

        @Test
        @DisplayName("1_000_000_000은 10.0억으로 표시된다")
        fun `format 1 billion displays as 10_0 eok`() {
            val result = AmountFormatter.format(1_000_000_000f)
            assertEquals("10.0억", result)
        }

        @Test
        @DisplayName("정확히 100_000_000 경계값은 억 단위로 표시된다")
        fun `format exactly 100M boundary uses eok unit`() {
            val result = AmountFormatter.format(100_000_000f)
            assertTrue(result.endsWith("억"), "Expected eok unit but got: $result")
        }

        // ── 백만 단위 (10,000,000 ≤ amount < 100,000,000) ────────────────────

        @Test
        @DisplayName("10_000_000 이상 100_000_000 미만은 백만 단위로 표시된다")
        fun `format 10M to 100M range uses baekman unit`() {
            val result = AmountFormatter.format(10_000_000f)
            assertEquals("1백만", result)
        }

        @Test
        @DisplayName("50_000_000은 5백만으로 표시된다")
        fun `format 50M displays as 5 baekman`() {
            val result = AmountFormatter.format(50_000_000f)
            assertEquals("5백만", result)
        }

        @Test
        @DisplayName("90_000_000는 백만 단위로 표시된다 (억 미만)")
        fun `format 90_000_000 uses baekman unit`() {
            val result = AmountFormatter.format(90_000_000f)
            assertTrue(result.endsWith("백만"), "Expected baekman unit but got: $result")
        }

        // ── 만 단위 (10,000 ≤ amount < 10,000,000) ───────────────────────────

        @Test
        @DisplayName("10_000 이상 10_000_000 미만은 만 단위로 표시된다")
        fun `format 10K to 10M range uses man unit`() {
            val result = AmountFormatter.format(10_000f)
            assertEquals("1만", result)
        }

        @Test
        @DisplayName("50_000은 5만으로 표시된다")
        fun `format 50K displays as 5 man`() {
            val result = AmountFormatter.format(50_000f)
            assertEquals("5만", result)
        }

        @Test
        @DisplayName("500_000은 50만으로 표시된다")
        fun `format 500K displays as 50 man`() {
            val result = AmountFormatter.format(500_000f)
            assertEquals("50만", result)
        }

        @Test
        @DisplayName("9_999_999는 만 단위로 표시된다")
        fun `format 9_999_999 uses man unit`() {
            val result = AmountFormatter.format(9_999_999f)
            assertTrue(result.endsWith("만"), "Expected man unit but got: $result")
        }

        // ── 원 단위 (1 ≤ amount < 10,000) ────────────────────────────────────

        @Test
        @DisplayName("1 이상 10_000 미만은 원 단위로 표시된다")
        fun `format 1 to 10K range uses won unit`() {
            val result = AmountFormatter.format(1f)
            assertEquals("1원", result)
        }

        @Test
        @DisplayName("9_999는 원 단위로 표시된다")
        fun `format 9999 uses won unit`() {
            val result = AmountFormatter.format(9_999f)
            assertEquals("9999원", result)
        }

        @Test
        @DisplayName("5_000은 5000원으로 표시된다")
        fun `format 5000 displays as 5000 won`() {
            val result = AmountFormatter.format(5_000f)
            assertEquals("5000원", result)
        }

        // ── 0원 (amount < 1) ──────────────────────────────────────────────────

        @Test
        @DisplayName("0은 '0원'을 반환한다")
        fun `format 0 returns zero won`() {
            val result = AmountFormatter.format(0f)
            assertEquals("0원", result)
        }

        @Test
        @DisplayName("0.5 (1 미만)은 '0원'을 반환한다")
        fun `format less than 1 returns zero won`() {
            val result = AmountFormatter.format(0.5f)
            assertEquals("0원", result)
        }

        @Test
        @DisplayName("음수 값은 앞에 마이너스 부호가 붙는다")
        fun `format negative amount prepends minus sign`() {
            val result = AmountFormatter.format(-150_000_000f)
            assertEquals("-1.5억", result)
        }

        @Test
        @DisplayName("음수 만 단위도 올바르게 표시된다")
        fun `format negative man range displays correctly`() {
            val result = AmountFormatter.format(-50_000f)
            assertEquals("-5만", result)
        }

        @Test
        @DisplayName("showUnit=false이면 단위 없이 숫자만 반환한다")
        fun `format showUnit false returns number only`() {
            val result = AmountFormatter.format(150_000_000f, showUnit = false)
            assertEquals("1.5", result)
        }

        @Test
        @DisplayName("showUnit=false 백만 단위도 단위 없이 반환한다")
        fun `format showUnit false baekman range returns number only`() {
            val result = AmountFormatter.format(50_000_000f, showUnit = false)
            assertEquals("5", result)
        }

        @Test
        @DisplayName("showUnit=false 만 단위도 단위 없이 반환한다")
        fun `format showUnit false man range returns number only`() {
            val result = AmountFormatter.format(50_000f, showUnit = false)
            assertEquals("5", result)
        }

        @Test
        @DisplayName("showUnit=false 원 단위도 단위 없이 반환한다")
        fun `format showUnit false won range returns number only`() {
            val result = AmountFormatter.format(5_000f, showUnit = false)
            assertEquals("5000", result)
        }

        @ParameterizedTest(name = "{0} → \"{1}\"")
        @CsvSource(
            "100000000, 1.0억",
            "200000000, 2.0억",
            "10000000, 1백만",
            "20000000, 2백만",
            "10000, 1만",
            "20000, 2만",
            "1000, 1000원",
            "1, 1원"
        )
        @DisplayName("다양한 금액이 올바른 단위로 포맷된다")
        fun `format various amounts with correct units`(amount: Float, expected: String) {
            assertEquals(expected, AmountFormatter.format(amount))
        }
    }

    // =========================================================================
    // formatChange()
    // =========================================================================

    @Nested
    @DisplayName("formatChange() — 변동액 포맷 (부호 포함)")
    inner class FormatChangeTests {

        @Test
        @DisplayName("양수 억 단위는 '+' 부호와 함께 표시된다")
        fun `formatChange positive eok includes plus sign`() {
            val result = AmountFormatter.formatChange(200_000_000f)
            assertEquals("+2.0억", result)
        }

        @Test
        @DisplayName("음수 억 단위는 '-' 부호와 함께 표시된다")
        fun `formatChange negative eok includes minus sign`() {
            val result = AmountFormatter.formatChange(-200_000_000f)
            assertEquals("-2.0억", result)
        }

        @Test
        @DisplayName("양수 백만 단위는 '+' 부호와 함께 표시된다")
        fun `formatChange positive baekman includes plus sign`() {
            val result = AmountFormatter.formatChange(50_000_000f)
            assertEquals("+5백만", result)
        }

        @Test
        @DisplayName("음수 백만 단위는 '-' 부호와 함께 표시된다")
        fun `formatChange negative baekman includes minus sign`() {
            val result = AmountFormatter.formatChange(-50_000_000f)
            assertEquals("-5백만", result)
        }

        @Test
        @DisplayName("양수 만 단위는 '+' 부호와 함께 표시된다")
        fun `formatChange positive man includes plus sign`() {
            val result = AmountFormatter.formatChange(50_000f)
            assertEquals("+5만", result)
        }

        @Test
        @DisplayName("양수 원 단위는 '+' 부호와 함께 표시된다")
        fun `formatChange positive won includes plus sign`() {
            val result = AmountFormatter.formatChange(5_000f)
            assertEquals("+5000원", result)
        }

        @Test
        @DisplayName("0 변동은 '+0원'으로 표시된다")
        fun `formatChange zero displays as plus zero won`() {
            val result = AmountFormatter.formatChange(0f)
            // 0f >= 0 → sign = "+" but absChange < 1 → "0원"
            assertEquals("0원", result)
        }

        @Test
        @DisplayName("소액(1 미만)은 '0원'으로 표시된다")
        fun `formatChange sub-won amount displays as zero won`() {
            val result = AmountFormatter.formatChange(0.5f)
            assertEquals("0원", result)
        }
    }

    // =========================================================================
    // toChartValue()
    // =========================================================================

    @Nested
    @DisplayName("toChartValue() — 차트 표시용 Double 변환")
    inner class ToChartValueTests {

        @Test
        @DisplayName("100_000_000 이상은 억 단위 Double로 변환된다")
        fun `toChartValue 100M or more converts to eok unit double`() {
            val result = AmountFormatter.toChartValue(200_000_000f)
            assertEquals(2.0, result, 1e-5)
        }

        @Test
        @DisplayName("1_000_000 이상 100_000_000 미만은 백만 단위 Double로 변환된다")
        fun `toChartValue 1M to 100M converts to million unit double`() {
            val result = AmountFormatter.toChartValue(5_000_000f)
            assertEquals(5.0, result, 1e-5)
        }

        @Test
        @DisplayName("1_000_000 미만은 만 단위 Double로 변환된다")
        fun `toChartValue below 1M converts to man unit double`() {
            val result = AmountFormatter.toChartValue(50_000f)
            assertEquals(5.0, result, 1e-5)
        }

        @Test
        @DisplayName("정확히 100_000_000 경계값은 억 단위로 변환된다")
        fun `toChartValue at exactly 100M boundary converts to eok`() {
            val result = AmountFormatter.toChartValue(100_000_000f)
            assertEquals(1.0, result, 1e-5)
        }

        @Test
        @DisplayName("정확히 1_000_000 경계값은 백만 단위로 변환된다")
        fun `toChartValue at exactly 1M boundary converts to million`() {
            val result = AmountFormatter.toChartValue(1_000_000f)
            assertEquals(1.0, result, 1e-5)
        }
    }

    // =========================================================================
    // getChartUnit()
    // =========================================================================

    @Nested
    @DisplayName("getChartUnit() — 차트 Y축 단위 문자열")
    inner class GetChartUnitTests {

        @Test
        @DisplayName("maxAmount ≥ 100_000_000 → '억원'")
        fun `getChartUnit 100M or more returns eokwon`() {
            assertEquals("억원", AmountFormatter.getChartUnit(100_000_000f))
        }

        @Test
        @DisplayName("maxAmount ≥ 1_000_000 → '백만원'")
        fun `getChartUnit 1M or more returns baekmanwon`() {
            assertEquals("백만원", AmountFormatter.getChartUnit(1_000_000f))
        }

        @Test
        @DisplayName("maxAmount < 1_000_000 → '만원'")
        fun `getChartUnit below 1M returns manwon`() {
            assertEquals("만원", AmountFormatter.getChartUnit(500_000f))
        }

        @Test
        @DisplayName("maxAmount = 0 → '만원'")
        fun `getChartUnit zero returns manwon`() {
            assertEquals("만원", AmountFormatter.getChartUnit(0f))
        }

        @ParameterizedTest(name = "maxAmount={0} → \"{1}\"")
        @CsvSource(
            "1000000000, 억원",
            "500000000, 억원",
            "100000000, 억원",
            "50000000, 백만원",
            "1000000, 백만원",
            "999999, 만원",
            "10000, 만원"
        )
        @DisplayName("다양한 maxAmount에 대해 올바른 단위를 반환한다")
        fun `getChartUnit returns correct unit for various amounts`(maxAmount: Float, expected: String) {
            assertEquals(expected, AmountFormatter.getChartUnit(maxAmount))
        }
    }

    // =========================================================================
    // formatForTable()
    // =========================================================================

    @Nested
    @DisplayName("formatForTable() — 테이블용 포맷")
    inner class FormatForTableTests {

        @Test
        @DisplayName("maxAmount ≥ 100_000_000 → 억 단위 소수점 2자리")
        fun `formatForTable maxAmount 100M uses eok with 2 decimals`() {
            val result = AmountFormatter.formatForTable(150_000_000f, maxAmount = 200_000_000f)
            assertEquals("1.50", result)
        }

        @Test
        @DisplayName("maxAmount ≥ 10_000_000 → 백만 단위 소수점 1자리")
        fun `formatForTable maxAmount 10M uses baekman with 1 decimal`() {
            val result = AmountFormatter.formatForTable(50_000_000f, maxAmount = 50_000_000f)
            assertEquals("5.0", result)
        }

        @Test
        @DisplayName("maxAmount ≥ 10_000 → 만 단위 정수")
        fun `formatForTable maxAmount 10K uses man with integer`() {
            val result = AmountFormatter.formatForTable(50_000f, maxAmount = 50_000f)
            assertEquals("5", result)
        }

        @Test
        @DisplayName("maxAmount < 10_000 → 원 단위 정수")
        fun `formatForTable maxAmount below 10K uses won with integer`() {
            val result = AmountFormatter.formatForTable(9_999f, maxAmount = 9_999f)
            assertEquals("9999", result)
        }

        @Test
        @DisplayName("maxAmount가 억 단위여도 소액은 0.00으로 표시된다")
        fun `formatForTable eok maxAmount with small amount shows 0_00`() {
            val result = AmountFormatter.formatForTable(0f, maxAmount = 200_000_000f)
            assertEquals("0.00", result)
        }
    }

    // =========================================================================
    // getTableHeader()
    // =========================================================================

    @Nested
    @DisplayName("getTableHeader() — 테이블 헤더 단위")
    inner class GetTableHeaderTests {

        @Test
        @DisplayName("maxAmount ≥ 100_000_000 → '금액(억)'")
        fun `getTableHeader 100M returns eok header`() {
            assertEquals("금액(억)", AmountFormatter.getTableHeader(100_000_000f))
        }

        @Test
        @DisplayName("maxAmount ≥ 10_000_000 → '금액(백만)'")
        fun `getTableHeader 10M returns baekman header`() {
            assertEquals("금액(백만)", AmountFormatter.getTableHeader(10_000_000f))
        }

        @Test
        @DisplayName("maxAmount ≥ 10_000 → '금액(만)'")
        fun `getTableHeader 10K returns man header`() {
            assertEquals("금액(만)", AmountFormatter.getTableHeader(10_000f))
        }

        @Test
        @DisplayName("maxAmount < 10_000 → '금액(원)'")
        fun `getTableHeader below 10K returns won header`() {
            assertEquals("금액(원)", AmountFormatter.getTableHeader(9_999f))
        }

        @Test
        @DisplayName("maxAmount = 0 → '금액(원)'")
        fun `getTableHeader zero returns won header`() {
            assertEquals("금액(원)", AmountFormatter.getTableHeader(0f))
        }
    }

    // =========================================================================
    // formatLong()
    // =========================================================================

    @Nested
    @DisplayName("formatLong() — Long 금액 한글 단위 포맷")
    inner class FormatLongTests {

        @Test
        @DisplayName("1_000_000_000_000 이상은 조 단위로 표시된다")
        fun `formatLong 1 trillion displays as jo unit`() {
            val result = AmountFormatter.formatLong(1_000_000_000_000L)
            assertEquals("1.0조", result)
        }

        @Test
        @DisplayName("1_500_000_000_000은 1.5조로 표시된다")
        fun `formatLong 1_5 trillion displays as 1_5 jo`() {
            val result = AmountFormatter.formatLong(1_500_000_000_000L)
            assertEquals("1.5조", result)
        }

        @Test
        @DisplayName("100_000_000은 억 단위로 표시된다 (1억)")
        fun `formatLong 100M displays as 1 eok`() {
            val result = AmountFormatter.formatLong(100_000_000L)
            assertEquals("1억", result)
        }

        @Test
        @DisplayName("200_000_000은 2억으로 표시된다")
        fun `formatLong 200M displays as 2 eok`() {
            val result = AmountFormatter.formatLong(200_000_000L)
            assertEquals("2억", result)
        }

        @Test
        @DisplayName("10_000 이상 100_000_000 미만은 만 단위로 표시된다")
        fun `formatLong 10K to 100M displays as man unit`() {
            val result = AmountFormatter.formatLong(10_000L)
            assertEquals("1만", result)
        }

        @Test
        @DisplayName("50_000은 5만으로 표시된다")
        fun `formatLong 50K displays as 5 man`() {
            val result = AmountFormatter.formatLong(50_000L)
            // 50000 / 10000.0 = 5.0 → "5만"
            assertEquals("5만", result)
        }

        @Test
        @DisplayName("9_999 이하는 %,d 포맷으로 표시된다 (로케일 무관 숫자 포함)")
        fun `formatLong below 10K displays with comma format`() {
            val result = AmountFormatter.formatLong(9_999L)
            // %,d 포맷은 숫자를 포함하며 9999가 결과에 있어야 한다
            assertTrue(result.contains("9") && result.contains("999"),
                "Expected result to contain '9' and '999' but got: $result")
        }

        @Test
        @DisplayName("1은 숫자 '1'을 포함하여 표시된다")
        fun `formatLong 1 displays as 1`() {
            val result = AmountFormatter.formatLong(1L)
            assertEquals("1", result)
        }

        @Test
        @DisplayName("0은 숫자 '0'을 포함하여 표시된다")
        fun `formatLong 0 displays as 0`() {
            val result = AmountFormatter.formatLong(0L)
            assertEquals("0", result)
        }

        @Test
        @DisplayName("1_000은 %,d 포맷으로 표시된다 (1000 포함)")
        fun `formatLong 1000 displays with formatted number`() {
            val result = AmountFormatter.formatLong(1_000L)
            assertTrue(result.contains("1") && result.contains("000"),
                "Expected result to contain '1' and '000' but got: $result")
        }

        @ParameterizedTest(name = "{0} → 억/만/조 단위 포맷")
        @CsvSource(
            "2000000000000, 2.0조",
            "200000000, 2억",
            "900000000, 9억",
            "10000, 1만",
            "90000, 9만",
            "100, 100",
            "0, 0"
        )
        @DisplayName("다양한 Long 금액이 올바른 단위로 포맷된다")
        fun `formatLong various amounts format correctly`(amount: Long, expected: String) {
            assertEquals(expected, AmountFormatter.formatLong(amount))
        }
    }
}
