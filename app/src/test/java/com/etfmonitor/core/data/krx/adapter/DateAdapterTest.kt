package com.etfmonitor.core.data.krx.adapter

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.test.assertEquals

/**
 * DateAdapter 테스트
 *
 * 테스트 범위:
 * - toKrxFormat: LocalDate → "yyyyMMdd" 문자열
 * - fromKrxFormat: "yyyyMMdd" 문자열 → LocalDate
 * - 라운드트립 보존
 * - 잘못된 형식 예외 처리
 */
@DisplayName("DateAdapter 테스트")
class DateAdapterTest {

    // =====================================================================
    // toKrxFormat 테스트
    // =====================================================================

    @Nested
    @DisplayName("toKrxFormat (LocalDate → yyyyMMdd) 테스트")
    inner class ToKrxFormatTests {

        @Test
        @DisplayName("LocalDate를 yyyyMMdd 형식 문자열로 변환한다")
        fun `toKrxFormat converts LocalDate to yyyyMMdd string`() {
            val date = LocalDate.of(2026, 2, 19)

            val result = DateAdapter.toKrxFormat(date)

            assertEquals("20260219", result)
        }

        @Test
        @DisplayName("월과 일이 한 자리인 경우 0으로 패딩된다")
        fun `toKrxFormat pads single-digit month and day with zero`() {
            val date = LocalDate.of(2025, 1, 5)

            val result = DateAdapter.toKrxFormat(date)

            assertEquals("20250105", result)
        }

        @Test
        @DisplayName("연말(12월 31일)을 올바르게 변환한다")
        fun `toKrxFormat converts year-end date correctly`() {
            val date = LocalDate.of(2025, 12, 31)

            val result = DateAdapter.toKrxFormat(date)

            assertEquals("20251231", result)
        }

        @Test
        @DisplayName("연초(1월 1일)를 올바르게 변환한다")
        fun `toKrxFormat converts year-start date correctly`() {
            val date = LocalDate.of(2026, 1, 1)

            val result = DateAdapter.toKrxFormat(date)

            assertEquals("20260101", result)
        }

        @ParameterizedTest(name = "LocalDate({0}-{1}-{2}) → \"{3}\"")
        @CsvSource(
            "2024, 2, 29, 20240229",  // 윤년
            "2025, 6, 15, 20250615",
            "2023, 11, 30, 20231130",
            "2026, 7, 4,  20260704"
        )
        @DisplayName("다양한 날짜가 올바른 yyyyMMdd 문자열로 변환된다")
        fun `toKrxFormat converts various dates correctly`(
            year: Int,
            month: Int,
            day: Int,
            expected: String
        ) {
            val date = LocalDate.of(year, month, day)

            val result = DateAdapter.toKrxFormat(date)

            assertEquals(expected.trim(), result)
        }
    }

    // =====================================================================
    // fromKrxFormat 테스트
    // =====================================================================

    @Nested
    @DisplayName("fromKrxFormat (yyyyMMdd → LocalDate) 테스트")
    inner class FromKrxFormatTests {

        @Test
        @DisplayName("\"20260219\" 문자열을 LocalDate(2026, 2, 19)로 변환한다")
        fun `fromKrxFormat with valid date 20260219`() {
            val result = DateAdapter.fromKrxFormat("20260219")

            assertEquals(LocalDate.of(2026, 2, 19), result)
        }

        @Test
        @DisplayName("yyyyMMdd 문자열을 LocalDate로 변환한다")
        fun `fromKrxFormat converts yyyyMMdd string to LocalDate`() {
            val result = DateAdapter.fromKrxFormat("20250615")

            assertEquals(LocalDate.of(2025, 6, 15), result)
        }

        @Test
        @DisplayName("월/일이 한 자리인 날짜 \"20250105\"를 올바르게 변환한다")
        fun `fromKrxFormat converts zero-padded month and day correctly`() {
            val result = DateAdapter.fromKrxFormat("20250105")

            assertEquals(LocalDate.of(2025, 1, 5), result)
        }

        @Test
        @DisplayName("윤년 날짜 \"20240229\"를 올바르게 변환한다")
        fun `fromKrxFormat converts leap day correctly`() {
            val result = DateAdapter.fromKrxFormat("20240229")

            assertEquals(LocalDate.of(2024, 2, 29), result)
        }

        @Test
        @DisplayName("잘못된 형식의 문자열은 예외를 발생시킨다")
        fun `fromKrxFormat with invalid string throws exception`() {
            assertThrows<DateTimeParseException> {
                DateAdapter.fromKrxFormat("2026-02-19") // ISO 형식 (하이픈 포함)은 거부
            }
        }

        @Test
        @DisplayName("빈 문자열은 예외를 발생시킨다")
        fun `fromKrxFormat with empty string throws exception`() {
            assertThrows<DateTimeParseException> {
                DateAdapter.fromKrxFormat("")
            }
        }

        @Test
        @DisplayName("자릿수가 부족한 문자열은 예외를 발생시킨다")
        fun `fromKrxFormat with short string throws exception`() {
            assertThrows<DateTimeParseException> {
                DateAdapter.fromKrxFormat("2026021") // 7자리 (정상 8자리)
            }
        }

        @Test
        @DisplayName("문자가 포함된 문자열은 예외를 발생시킨다")
        fun `fromKrxFormat with non-numeric string throws exception`() {
            assertThrows<DateTimeParseException> {
                DateAdapter.fromKrxFormat("2026AB19")
            }
        }

        @ParameterizedTest(name = "\"{0}\" → LocalDate({1}-{2}-{3})")
        @CsvSource(
            "20251231, 2025, 12, 31",
            "20260101, 2026,  1,  1",
            "20230630, 2023,  6, 30"
        )
        @DisplayName("다양한 yyyyMMdd 문자열이 올바른 LocalDate로 변환된다")
        fun `fromKrxFormat converts various strings correctly`(
            input: String,
            year: Int,
            month: Int,
            day: Int
        ) {
            val result = DateAdapter.fromKrxFormat(input)

            assertEquals(LocalDate.of(year, month, day), result)
        }
    }

    // =====================================================================
    // 라운드트립 테스트
    // =====================================================================

    @Nested
    @DisplayName("라운드트립 테스트 (LocalDate ↔ yyyyMMdd)")
    inner class RoundTripTests {

        @Test
        @DisplayName("LocalDate → yyyyMMdd → LocalDate 라운드트립이 날짜를 보존한다")
        fun `round-trip preserves date`() {
            val original = LocalDate.of(2026, 2, 19)

            val krxString = DateAdapter.toKrxFormat(original)
            val restored = DateAdapter.fromKrxFormat(krxString)

            assertEquals(original, restored)
        }

        @Test
        @DisplayName("yyyyMMdd → LocalDate → yyyyMMdd 역방향 라운드트립도 문자열을 보존한다")
        fun `reverse round-trip preserves string`() {
            val original = "20260219"

            val localDate = DateAdapter.fromKrxFormat(original)
            val restored = DateAdapter.toKrxFormat(localDate)

            assertEquals(original, restored)
        }

        @ParameterizedTest(name = "날짜 {0} 라운드트립")
        @CsvSource(
            "20250101",
            "20250615",
            "20251231",
            "20240229",  // 윤년
            "20261130"
        )
        @DisplayName("다양한 날짜에서 라운드트립이 보존된다")
        fun `round-trip preserves various dates`(krxDateStr: String) {
            val localDate = DateAdapter.fromKrxFormat(krxDateStr)
            val restored = DateAdapter.toKrxFormat(localDate)

            assertEquals(krxDateStr, restored)
        }
    }
}
