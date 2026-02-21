package com.etfmonitor.core.data.krx.adapter

import com.krxkt.error.KrxError
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * KrxErrorMapper 단위 테스트
 *
 * 테스트 범위:
 * - KrxError.NetworkError → Exception (재시도 가능한 일반 Exception)
 * - KrxError.ParseError → Exception (파싱 오류)
 * - KrxError.InvalidDateError → IllegalArgumentException (입력값 오류)
 * - cause 체인 보존 여부
 * - 메시지 포맷 확인
 */
@DisplayName("KrxErrorMapper 테스트")
class KrxErrorMapperTest {

    // =========================================================================
    // NetworkError 매핑 테스트
    // =========================================================================

    @Nested
    @DisplayName("KrxError.NetworkError → Exception 매핑")
    inner class NetworkErrorMappingTests {

        @Test
        @DisplayName("NetworkError는 Exception으로 매핑된다")
        fun `toException maps NetworkError to Exception`() {
            val krxError = KrxError.NetworkError("Connection refused")

            val result = KrxErrorMapper.toException(krxError)

            assertTrue(result is Exception)
        }

        @Test
        @DisplayName("NetworkError 메시지가 'Network error:' 접두사와 함께 포함된다")
        fun `toException NetworkError message contains network error prefix`() {
            val krxError = KrxError.NetworkError("Connection refused")

            val result = KrxErrorMapper.toException(krxError)

            assertTrue(
                result.message?.contains("Network error:") == true,
                "Expected 'Network error:' prefix but got: ${result.message}"
            )
        }

        @Test
        @DisplayName("NetworkError의 원본 메시지가 결과 Exception 메시지에 포함된다")
        fun `toException NetworkError preserves original message`() {
            val originalMessage = "Connection refused to api.krx.co.kr"
            val krxError = KrxError.NetworkError(originalMessage)

            val result = KrxErrorMapper.toException(krxError)

            assertTrue(
                result.message?.contains(originalMessage) == true,
                "Expected original message in result but got: ${result.message}"
            )
        }

        @Test
        @DisplayName("NetworkError는 cause로 원본 KrxError를 보존한다")
        fun `toException NetworkError preserves krx error as cause`() {
            val krxError = KrxError.NetworkError("Timeout")

            val result = KrxErrorMapper.toException(krxError)

            assertEquals(krxError, result.cause)
        }

        @Test
        @DisplayName("NetworkError with cause는 cause가 보존된다")
        fun `toException NetworkError with cause preserves cause chain`() {
            val rootCause = RuntimeException("root cause")
            val krxError = KrxError.NetworkError("Network failure", rootCause)

            val result = KrxErrorMapper.toException(krxError)

            // result.cause == krxError (krxError.cause == rootCause)
            assertEquals(krxError, result.cause)
        }
    }

    // =========================================================================
    // ParseError 매핑 테스트
    // =========================================================================

    @Nested
    @DisplayName("KrxError.ParseError → Exception 매핑")
    inner class ParseErrorMappingTests {

        @Test
        @DisplayName("ParseError는 Exception으로 매핑된다")
        fun `toException maps ParseError to Exception`() {
            val krxError = KrxError.ParseError("Invalid JSON format")

            val result = KrxErrorMapper.toException(krxError)

            assertTrue(result is Exception)
        }

        @Test
        @DisplayName("ParseError 메시지가 'Data parsing error:' 접두사와 함께 포함된다")
        fun `toException ParseError message contains parsing error prefix`() {
            val krxError = KrxError.ParseError("Unexpected token at position 5")

            val result = KrxErrorMapper.toException(krxError)

            assertTrue(
                result.message?.contains("Data parsing error:") == true,
                "Expected 'Data parsing error:' prefix but got: ${result.message}"
            )
        }

        @Test
        @DisplayName("ParseError의 원본 메시지가 결과 Exception 메시지에 포함된다")
        fun `toException ParseError preserves original message`() {
            val originalMessage = "Missing required field: COMPST_ISU_CD"
            val krxError = KrxError.ParseError(originalMessage)

            val result = KrxErrorMapper.toException(krxError)

            assertTrue(
                result.message?.contains(originalMessage) == true,
                "Expected original message in result but got: ${result.message}"
            )
        }

        @Test
        @DisplayName("ParseError는 cause로 원본 KrxError를 보존한다")
        fun `toException ParseError preserves krx error as cause`() {
            val krxError = KrxError.ParseError("Parse failed")

            val result = KrxErrorMapper.toException(krxError)

            assertEquals(krxError, result.cause)
        }

        @Test
        @DisplayName("ParseError는 IllegalArgumentException이 아닌 일반 Exception이다")
        fun `toException ParseError is not IllegalArgumentException`() {
            val krxError = KrxError.ParseError("Invalid data")

            val result = KrxErrorMapper.toException(krxError)

            // IllegalArgumentException이어서는 안 됨 (ParseError는 일반 Exception)
            assertTrue(result !is IllegalArgumentException)
        }
    }

    // =========================================================================
    // InvalidDateError 매핑 테스트
    // =========================================================================

    @Nested
    @DisplayName("KrxError.InvalidDateError → IllegalArgumentException 매핑")
    inner class InvalidDateErrorMappingTests {

        @Test
        @DisplayName("InvalidDateError는 IllegalArgumentException으로 매핑된다")
        fun `toException maps InvalidDateError to IllegalArgumentException`() {
            val krxError = KrxError.InvalidDateError("2026-02-19")

            val result = KrxErrorMapper.toException(krxError)

            assertTrue(result is IllegalArgumentException)
        }

        @Test
        @DisplayName("InvalidDateError 메시지에 'Invalid date:' 접두사가 포함된다")
        fun `toException InvalidDateError message contains invalid date prefix`() {
            val krxError = KrxError.InvalidDateError("2026-02-19")

            val result = KrxErrorMapper.toException(krxError)

            assertTrue(
                result.message?.contains("Invalid date:") == true,
                "Expected 'Invalid date:' prefix but got: ${result.message}"
            )
        }

        @Test
        @DisplayName("InvalidDateError 메시지에 원본 날짜 문자열이 포함된다")
        fun `toException InvalidDateError message contains original date string`() {
            val badDate = "2026-02-19"
            val krxError = KrxError.InvalidDateError(badDate)

            val result = KrxErrorMapper.toException(krxError)

            assertTrue(
                result.message?.contains(badDate) == true,
                "Expected date '$badDate' in message but got: ${result.message}"
            )
        }

        @Test
        @DisplayName("InvalidDateError는 cause로 원본 KrxError를 보존한다")
        fun `toException InvalidDateError preserves krx error as cause`() {
            val krxError = KrxError.InvalidDateError("invalid-date")

            val result = KrxErrorMapper.toException(krxError)

            assertEquals(krxError, result.cause)
        }

        @Test
        @DisplayName("빈 날짜 문자열의 InvalidDateError도 올바르게 매핑된다")
        fun `toException InvalidDateError with empty date string maps correctly`() {
            val krxError = KrxError.InvalidDateError("")

            val result = KrxErrorMapper.toException(krxError)

            assertTrue(result is IllegalArgumentException)
        }

        @Test
        @DisplayName("잘못된 형식(ISO 형식) 날짜의 InvalidDateError도 올바르게 매핑된다")
        fun `toException InvalidDateError with ISO format date maps correctly`() {
            val krxError = KrxError.InvalidDateError("2026-02-19")

            val result = KrxErrorMapper.toException(krxError)

            assertTrue(result is IllegalArgumentException)
            assertTrue(result.message?.contains("2026-02-19") == true)
        }
    }

    // =========================================================================
    // 타입 구분 테스트
    // =========================================================================

    @Nested
    @DisplayName("에러 타입 구분 테스트")
    inner class ErrorTypeDistinctionTests {

        @Test
        @DisplayName("NetworkError와 ParseError는 모두 일반 Exception이다 (IllegalArgumentException 아님)")
        fun `NetworkError and ParseError map to plain Exception not IllegalArgumentException`() {
            val networkEx = KrxErrorMapper.toException(KrxError.NetworkError("net"))
            val parseEx = KrxErrorMapper.toException(KrxError.ParseError("parse"))

            assertTrue(networkEx !is IllegalArgumentException)
            assertTrue(parseEx !is IllegalArgumentException)
        }

        @Test
        @DisplayName("InvalidDateError만 IllegalArgumentException으로 매핑된다")
        fun `only InvalidDateError maps to IllegalArgumentException`() {
            val dateEx = KrxErrorMapper.toException(KrxError.InvalidDateError("20261399"))

            assertTrue(dateEx is IllegalArgumentException)
        }

        @Test
        @DisplayName("세 가지 에러 타입이 모두 Exception의 인스턴스이다")
        fun `all three error types map to Exception subtype`() {
            val networkEx = KrxErrorMapper.toException(KrxError.NetworkError("n"))
            val parseEx = KrxErrorMapper.toException(KrxError.ParseError("p"))
            val dateEx = KrxErrorMapper.toException(KrxError.InvalidDateError("d"))

            assertTrue(networkEx is Exception)
            assertTrue(parseEx is Exception)
            assertTrue(dateEx is Exception)
        }
    }
}
