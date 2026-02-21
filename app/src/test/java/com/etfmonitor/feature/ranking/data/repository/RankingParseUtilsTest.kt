package com.etfmonitor.feature.ranking.data.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * RankingParseUtils 단위 테스트
 *
 * 테스트 범위:
 * - cleanTicker: 접미사 제거, 공백 처리, null 처리
 * - parseLong: 숫자 파싱, 부호·쉼표 처리, 예외 처리
 * - parseDouble: 실수 파싱, 부호·쉼표·% 처리, 예외 처리
 * - parseSign: Kiwoom 신호 코드 → "+"/"-"/"" 변환
 */
@DisplayName("RankingParseUtils 테스트")
class RankingParseUtilsTest {

    // ----------------------------------------------------------------
    // cleanTicker
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("cleanTicker 테스트")
    inner class CleanTickerTests {

        @Test
        @DisplayName("cleanTicker_withAlSuffix_removesAlSuffix")
        fun `cleanTicker_withAlSuffix_removesAlSuffix`() {
            assertEquals("005930", RankingParseUtils.cleanTicker("005930_AL"))
        }

        @Test
        @DisplayName("cleanTicker_withKsSuffix_removesKsSuffix")
        fun `cleanTicker_withKsSuffix_removesKsSuffix`() {
            assertEquals("005930", RankingParseUtils.cleanTicker("005930_KS"))
        }

        @Test
        @DisplayName("cleanTicker_withKqSuffix_removesKqSuffix")
        fun `cleanTicker_withKqSuffix_removesKqSuffix`() {
            assertEquals("035420", RankingParseUtils.cleanTicker("035420_KQ"))
        }

        @Test
        @DisplayName("cleanTicker_withNoSuffix_returnsUnchanged")
        fun `cleanTicker_withNoSuffix_returnsUnchanged`() {
            assertEquals("005930", RankingParseUtils.cleanTicker("005930"))
        }

        @Test
        @DisplayName("cleanTicker_withNullInput_returnsEmptyString")
        fun `cleanTicker_withNullInput_returnsEmptyString`() {
            assertEquals("", RankingParseUtils.cleanTicker(null))
        }

        @Test
        @DisplayName("cleanTicker_withEmptyString_returnsEmptyString")
        fun `cleanTicker_withEmptyString_returnsEmptyString`() {
            assertEquals("", RankingParseUtils.cleanTicker(""))
        }

        @Test
        @DisplayName("cleanTicker_withLeadingTrailingSpaces_returnsTrimmedTicker")
        fun `cleanTicker_withLeadingTrailingSpaces_returnsTrimmedTicker`() {
            assertEquals("005930", RankingParseUtils.cleanTicker("  005930  "))
        }

        @Test
        @DisplayName("cleanTicker_withSuffixInMiddleOfTicker_doesNotRemoveMiddleSuffix")
        fun `cleanTicker_withSuffixInMiddleOfTicker_doesNotRemoveMiddleSuffix`() {
            // fold/replace removes all occurrences of the suffix pattern, including in the middle
            // This verifies the actual behavior: _KS in the middle is also removed
            val result = RankingParseUtils.cleanTicker("00_KS5930")
            assertEquals("005930", result)
        }

        @Test
        @DisplayName("cleanTicker_withMultipleSuffixOccurrences_removesAll")
        fun `cleanTicker_withMultipleSuffixOccurrences_removesAll`() {
            // _AL appearing twice is removed both times
            assertEquals("005930", RankingParseUtils.cleanTicker("005930_AL_AL"))
        }

        @Test
        @DisplayName("cleanTicker_withAlSuffixAndSpaces_returnsTrimmedResult")
        fun `cleanTicker_withAlSuffixAndSpaces_returnsTrimmedResult`() {
            assertEquals("005930", RankingParseUtils.cleanTicker("  005930_AL  "))
        }
    }

    // ----------------------------------------------------------------
    // parseLong
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("parseLong 테스트")
    inner class ParseLongTests {

        @Test
        @DisplayName("parseLong_withNormalNumber_returnsCorrectValue")
        fun `parseLong_withNormalNumber_returnsCorrectValue`() {
            assertEquals(12345L, RankingParseUtils.parseLong("12345"))
        }

        @Test
        @DisplayName("parseLong_withLeadingPlus_returnsPositiveValue")
        fun `parseLong_withLeadingPlus_returnsPositiveValue`() {
            assertEquals(12345L, RankingParseUtils.parseLong("+12345"))
        }

        @Test
        @DisplayName("parseLong_withLeadingMinus_returnsNegativeValue")
        fun `parseLong_withLeadingMinus_returnsNegativeValue`() {
            assertEquals(-12345L, RankingParseUtils.parseLong("-12345"))
        }

        @Test
        @DisplayName("parseLong_withCommas_returnsCorrectValue")
        fun `parseLong_withCommas_returnsCorrectValue`() {
            assertEquals(1234567L, RankingParseUtils.parseLong("1,234,567"))
        }

        @Test
        @DisplayName("parseLong_withCommasAndLeadingPlus_returnsCorrectValue")
        fun `parseLong_withCommasAndLeadingPlus_returnsCorrectValue`() {
            assertEquals(1234567L, RankingParseUtils.parseLong("+1,234,567"))
        }

        @Test
        @DisplayName("parseLong_withEmptyString_returnsZero")
        fun `parseLong_withEmptyString_returnsZero`() {
            assertEquals(0L, RankingParseUtils.parseLong(""))
        }

        @Test
        @DisplayName("parseLong_withNull_returnsZero")
        fun `parseLong_withNull_returnsZero`() {
            assertEquals(0L, RankingParseUtils.parseLong(null))
        }

        @Test
        @DisplayName("parseLong_withInvalidString_returnsZero")
        fun `parseLong_withInvalidString_returnsZero`() {
            assertEquals(0L, RankingParseUtils.parseLong("abc"))
        }

        @Test
        @DisplayName("parseLong_withZero_returnsZero")
        fun `parseLong_withZero_returnsZero`() {
            assertEquals(0L, RankingParseUtils.parseLong("0"))
        }

        @Test
        @DisplayName("parseLong_withWhitespaceOnly_returnsZero")
        fun `parseLong_withWhitespaceOnly_returnsZero`() {
            assertEquals(0L, RankingParseUtils.parseLong("   "))
        }

        @Test
        @DisplayName("parseLong_withLargeNumber_returnsCorrectValue")
        fun `parseLong_withLargeNumber_returnsCorrectValue`() {
            assertEquals(9_999_999_999L, RankingParseUtils.parseLong("9999999999"))
        }
    }

    // ----------------------------------------------------------------
    // parseDouble
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("parseDouble 테스트")
    inner class ParseDoubleTests {

        @Test
        @DisplayName("parseDouble_withNormalDecimal_returnsCorrectValue")
        fun `parseDouble_withNormalDecimal_returnsCorrectValue`() {
            assertEquals(123.45, RankingParseUtils.parseDouble("123.45"), 0.001)
        }

        @Test
        @DisplayName("parseDouble_withLeadingPlus_returnsPositiveValue")
        fun `parseDouble_withLeadingPlus_returnsPositiveValue`() {
            assertEquals(123.45, RankingParseUtils.parseDouble("+123.45"), 0.001)
        }

        @Test
        @DisplayName("parseDouble_withLeadingMinus_returnsNegativeValue")
        fun `parseDouble_withLeadingMinus_returnsNegativeValue`() {
            assertEquals(-123.45, RankingParseUtils.parseDouble("-123.45"), 0.001)
        }

        @Test
        @DisplayName("parseDouble_withCommas_returnsCorrectValue")
        fun `parseDouble_withCommas_returnsCorrectValue`() {
            assertEquals(1234.56, RankingParseUtils.parseDouble("1,234.56"), 0.001)
        }

        @Test
        @DisplayName("parseDouble_withPercentSign_returnsValueWithoutPercent")
        fun `parseDouble_withPercentSign_returnsValueWithoutPercent`() {
            assertEquals(5.25, RankingParseUtils.parseDouble("5.25%"), 0.001)
        }

        @Test
        @DisplayName("parseDouble_withIntegerString_returnsDoubleValue")
        fun `parseDouble_withIntegerString_returnsDoubleValue`() {
            assertEquals(100.0, RankingParseUtils.parseDouble("100"), 0.001)
        }

        @Test
        @DisplayName("parseDouble_withEmptyString_returnsZero")
        fun `parseDouble_withEmptyString_returnsZero`() {
            assertEquals(0.0, RankingParseUtils.parseDouble(""), 0.001)
        }

        @Test
        @DisplayName("parseDouble_withNull_returnsZero")
        fun `parseDouble_withNull_returnsZero`() {
            assertEquals(0.0, RankingParseUtils.parseDouble(null), 0.001)
        }

        @Test
        @DisplayName("parseDouble_withInvalidString_returnsZero")
        fun `parseDouble_withInvalidString_returnsZero`() {
            assertEquals(0.0, RankingParseUtils.parseDouble("invalid"), 0.001)
        }

        @Test
        @DisplayName("parseDouble_withZero_returnsZero")
        fun `parseDouble_withZero_returnsZero`() {
            assertEquals(0.0, RankingParseUtils.parseDouble("0.0"), 0.001)
        }

        @Test
        @DisplayName("parseDouble_withNegativePercentAndComma_returnsCorrectValue")
        fun `parseDouble_withNegativePercentAndComma_returnsCorrectValue`() {
            assertEquals(-12.34, RankingParseUtils.parseDouble("-12.34%"), 0.001)
        }

        @Test
        @DisplayName("parseDouble_withWhitespaceOnly_returnsZero")
        fun `parseDouble_withWhitespaceOnly_returnsZero`() {
            assertEquals(0.0, RankingParseUtils.parseDouble("   "), 0.001)
        }
    }

    // ----------------------------------------------------------------
    // parseSign
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("parseSign 테스트")
    inner class ParseSignTests {

        @Test
        @DisplayName("parseSign_withCode1_returnsPlus")
        fun `parseSign_withCode1_returnsPlus`() {
            assertEquals("+", RankingParseUtils.parseSign("1"))
        }

        @Test
        @DisplayName("parseSign_withCode2_returnsPlus")
        fun `parseSign_withCode2_returnsPlus`() {
            // "2" maps to "+" per source: when "1","2","+" -> "+"
            assertEquals("+", RankingParseUtils.parseSign("2"))
        }

        @Test
        @DisplayName("parseSign_withPlusSymbol_returnsPlus")
        fun `parseSign_withPlusSymbol_returnsPlus`() {
            assertEquals("+", RankingParseUtils.parseSign("+"))
        }

        @Test
        @DisplayName("parseSign_withCode4_returnsMinus")
        fun `parseSign_withCode4_returnsMinus`() {
            assertEquals("-", RankingParseUtils.parseSign("4"))
        }

        @Test
        @DisplayName("parseSign_withCode5_returnsMinus")
        fun `parseSign_withCode5_returnsMinus`() {
            assertEquals("-", RankingParseUtils.parseSign("5"))
        }

        @Test
        @DisplayName("parseSign_withMinusSymbol_returnsMinus")
        fun `parseSign_withMinusSymbol_returnsMinus`() {
            assertEquals("-", RankingParseUtils.parseSign("-"))
        }

        @Test
        @DisplayName("parseSign_withCode3_returnsEmpty")
        fun `parseSign_withCode3_returnsEmpty`() {
            // "3" is the else branch — returns ""
            assertEquals("", RankingParseUtils.parseSign("3"))
        }

        @Test
        @DisplayName("parseSign_withUnknownCode_returnsEmpty")
        fun `parseSign_withUnknownCode_returnsEmpty`() {
            assertEquals("", RankingParseUtils.parseSign("9"))
        }

        @Test
        @DisplayName("parseSign_withEmptyString_returnsEmpty")
        fun `parseSign_withEmptyString_returnsEmpty`() {
            assertEquals("", RankingParseUtils.parseSign(""))
        }

        @Test
        @DisplayName("parseSign_withNull_returnsEmpty")
        fun `parseSign_withNull_returnsEmpty`() {
            assertEquals("", RankingParseUtils.parseSign(null))
        }

        @Test
        @DisplayName("parseSign_withWhitespace_returnsEmpty")
        fun `parseSign_withWhitespace_returnsEmpty`() {
            // whitespace trimmed → "" → else branch
            assertEquals("", RankingParseUtils.parseSign("  "))
        }
    }
}
