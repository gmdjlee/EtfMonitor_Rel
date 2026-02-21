package com.etfmonitor.core.network.kiwoom

import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * KiwoomApiClient 단위 테스트
 *
 * normalizeJsonNumbers, isAuthenticationError, mapException 은 private 메서드이므로
 * Java 리플렉션을 통해 직접 테스트한다.
 *
 * 테스트 범위:
 * - normalizeJsonNumbers: JSON 문자열의 "+" 접두사 제거
 * - isAuthenticationError: HTTP 401/403 및 AuthError 감지
 * - mapException: 다양한 예외 타입을 KiwoomApiError로 매핑
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("KiwoomApiClient 단위 테스트")
class KiwoomApiClientTest {

    private lateinit var client: KiwoomApiClient

    // 리플렉션으로 접근할 private 메서드
    private lateinit var normalizeJsonNumbersMethod: Method
    private lateinit var isAuthenticationErrorMethod: Method
    private lateinit var mapExceptionMethod: Method

    @BeforeEach
    fun setUp() {
        val tokenManager = mockk<KiwoomTokenManager>(relaxed = true)
        val okHttpClient = mockk<okhttp3.OkHttpClient>(relaxed = true)
        val json = Json { ignoreUnknownKeys = true }

        client = KiwoomApiClient(tokenManager, okHttpClient, json)

        // private 메서드 접근 설정
        normalizeJsonNumbersMethod = KiwoomApiClient::class.java
            .getDeclaredMethod("normalizeJsonNumbers", String::class.java)
            .also { it.isAccessible = true }

        isAuthenticationErrorMethod = KiwoomApiClient::class.java
            .getDeclaredMethod("isAuthenticationError", Throwable::class.java)
            .also { it.isAccessible = true }

        mapExceptionMethod = KiwoomApiClient::class.java
            .getDeclaredMethod("mapException", Exception::class.java)
            .also { it.isAccessible = true }
    }

    // ================================================================
    // normalizeJsonNumbers 테스트
    // ================================================================

    @Nested
    @DisplayName("normalizeJsonNumbers — JSON 숫자 정규화")
    inner class NormalizeJsonNumbersTests {

        @Test
        @DisplayName("normalizeJsonNumbers_quotedPlusNumber_stripsPlus")
        fun `normalizeJsonNumbers_quotedPlusNumber_stripsPlus`() {
            // Given: 따옴표로 감싼 + 접두사 숫자 ("+12345" → "12345")
            val input = """{"value":"+12345"}"""
            val expected = """{"value":"12345"}"""

            // When
            val result = invokeNormalize(input)

            // Then
            assertEquals(expected, result)
        }

        @Test
        @DisplayName("normalizeJsonNumbers_unquotedPlusNumber_stripsPlus")
        fun `normalizeJsonNumbers_unquotedPlusNumber_stripsPlus`() {
            // Given: 따옴표 없는 + 접두사 (콜론 뒤 +숫자 → 숫자)
            val input = """{"count":+999}"""
            val expected = """{"count":999}"""

            // When
            val result = invokeNormalize(input)

            // Then
            assertEquals(expected, result)
        }

        @Test
        @DisplayName("normalizeJsonNumbers_multiplePlusNumbers_stripsAllPlus")
        fun `normalizeJsonNumbers_multiplePlusNumbers_stripsAllPlus`() {
            // Given: 여러 개의 + 접두사 숫자
            val input = """{"a":"+100","b":"+200","c":"+300"}"""
            val expected = """{"a":"100","b":"200","c":"300"}"""

            // When
            val result = invokeNormalize(input)

            // Then
            assertEquals(expected, result)
        }

        @Test
        @DisplayName("normalizeJsonNumbers_noPlus_unchanged")
        fun `normalizeJsonNumbers_noPlus_unchanged`() {
            // Given: + 없는 JSON은 변경 없음
            val input = """{"value":"12345","count":999}"""

            // When
            val result = invokeNormalize(input)

            // Then
            assertEquals(input, result)
        }

        @Test
        @DisplayName("normalizeJsonNumbers_negativeNumbers_unchanged")
        fun `normalizeJsonNumbers_negativeNumbers_unchanged`() {
            // Given: 음수는 변경 없음
            val input = """{"value":"-500","count":-300}"""

            // When
            val result = invokeNormalize(input)

            // Then
            assertEquals(input, result)
        }

        @Test
        @DisplayName("normalizeJsonNumbers_emptyString_returnsEmpty")
        fun `normalizeJsonNumbers_emptyString_returnsEmpty`() {
            // Given
            val input = ""

            // When
            val result = invokeNormalize(input)

            // Then
            assertEquals("", result)
        }

        @Test
        @DisplayName("normalizeJsonNumbers_commaBeforePlusNumber_stripsPlus")
        fun `normalizeJsonNumbers_commaBeforePlusNumber_stripsPlus`() {
            // Given: 쉼표 뒤 + 접두사 (배열 내 숫자 등)
            // UNQUOTED_PLUS_REGEX: ([,:]) \+ (\d+) → \1\2
            val input = """{"a":1,"b":+456}"""
            val expected = """{"a":1,"b":456}"""

            // When
            val result = invokeNormalize(input)

            // Then
            assertEquals(expected, result)
        }

        @Test
        @DisplayName("normalizeJsonNumbers_mixedPlusAndNormal_onlyStripsPlusPrefixed")
        fun `normalizeJsonNumbers_mixedPlusAndNormal_onlyStripsPlusPrefixed`() {
            // Given: + 있는 것과 없는 것 혼재
            val input = """{"surge":"+1500","volume":"3000","rate":"+12"}"""
            val expected = """{"surge":"1500","volume":"3000","rate":"12"}"""

            // When
            val result = invokeNormalize(input)

            // Then
            assertEquals(expected, result)
        }

        @Test
        @DisplayName("normalizeJsonNumbers_realWorldKiwoomResponse_normalizesIntegerPlusPrefixes")
        fun `normalizeJsonNumbers_realWorldKiwoomResponse_normalizesIntegerPlusPrefixes`() {
            // Given: 실제 키움 API 응답 형태의 데이터
            // QUOTED_PLUS_REGEX = "\"\\+(\\d+)\"" — 정수 + 접두사만 처리 (소수점 없음)
            // 따라서 "+70000" → "70000" (정수: 처리됨), "+1.45" → "+1.45" (소수: 처리 안 됨)
            val input = """{"return_code":0,"trde_qty_sdnin":[{"stk_cd":"005930","cur_prc":"+70000","flu_rt":"+1.45"}]}"""
            val expected = """{"return_code":0,"trde_qty_sdnin":[{"stk_cd":"005930","cur_prc":"70000","flu_rt":"+1.45"}]}"""

            // When
            val result = invokeNormalize(input)

            // Then: 정수 + 접두사만 제거되고, 소수 + 접두사는 그대로 유지
            assertEquals(expected, result)
        }

        @Test
        @DisplayName("normalizeJsonNumbers_zeroWithPlus_stripsPlus")
        fun `normalizeJsonNumbers_zeroWithPlus_stripsPlus`() {
            // Given: "+0" 케이스
            val input = """{"val":"+0"}"""
            val expected = """{"val":"0"}"""

            // When
            val result = invokeNormalize(input)

            // Then
            assertEquals(expected, result)
        }
    }

    // ================================================================
    // isAuthenticationError 테스트
    // ================================================================

    @Nested
    @DisplayName("isAuthenticationError — 인증 오류 감지")
    inner class IsAuthenticationErrorTests {

        @Test
        @DisplayName("isAuthenticationError_withAuthError_returnsTrue")
        fun `isAuthenticationError_withAuthError_returnsTrue`() {
            // Given
            val error = KiwoomApiError.AuthError("인증 토큰 만료")

            // When
            val result = invokeIsAuthError(error)

            // Then
            assertTrue(result)
        }

        @Test
        @DisplayName("isAuthenticationError_withApiCallError401_returnsTrue")
        fun `isAuthenticationError_withApiCallError401_returnsTrue`() {
            // Given: HTTP 401 Unauthorized
            val error = KiwoomApiError.ApiCallError(401, "Unauthorized")

            // When
            val result = invokeIsAuthError(error)

            // Then
            assertTrue(result)
        }

        @Test
        @DisplayName("isAuthenticationError_withApiCallError403_returnsTrue")
        fun `isAuthenticationError_withApiCallError403_returnsTrue`() {
            // Given: HTTP 403 Forbidden
            val error = KiwoomApiError.ApiCallError(403, "Forbidden")

            // When
            val result = invokeIsAuthError(error)

            // Then
            assertTrue(result)
        }

        @Test
        @DisplayName("isAuthenticationError_withApiCallErrorKoreanAuth_returnsTrue")
        fun `isAuthenticationError_withApiCallErrorKoreanAuth_returnsTrue`() {
            // Given: 메시지에 "인증" 포함 → 인증 오류로 간주
            val error = KiwoomApiError.ApiCallError(400, "인증이 필요합니다")

            // When
            val result = invokeIsAuthError(error)

            // Then
            assertTrue(result)
        }

        @Test
        @DisplayName("isAuthenticationError_withApiCallErrorKoreanToken_returnsTrue")
        fun `isAuthenticationError_withApiCallErrorKoreanToken_returnsTrue`() {
            // Given: 메시지에 "토큰" 포함
            val error = KiwoomApiError.ApiCallError(400, "토큰이 유효하지 않습니다")

            // When
            val result = invokeIsAuthError(error)

            // Then
            assertTrue(result)
        }

        @Test
        @DisplayName("isAuthenticationError_withApiCallErrorKoreanPermission_returnsTrue")
        fun `isAuthenticationError_withApiCallErrorKoreanPermission_returnsTrue`() {
            // Given: 메시지에 "권한" 포함
            val error = KiwoomApiError.ApiCallError(400, "접근 권한이 없습니다")

            // When
            val result = invokeIsAuthError(error)

            // Then
            assertTrue(result)
        }

        @Test
        @DisplayName("isAuthenticationError_withApiCallError500_returnsFalse")
        fun `isAuthenticationError_withApiCallError500_returnsFalse`() {
            // Given: HTTP 500은 인증 오류가 아님
            val error = KiwoomApiError.ApiCallError(500, "Internal Server Error")

            // When
            val result = invokeIsAuthError(error)

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("isAuthenticationError_withNetworkError_returnsFalse")
        fun `isAuthenticationError_withNetworkError_returnsFalse`() {
            // Given: 네트워크 오류는 인증 오류가 아님
            val error = KiwoomApiError.NetworkError("연결 실패")

            // When
            val result = invokeIsAuthError(error)

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("isAuthenticationError_withTimeoutError_returnsFalse")
        fun `isAuthenticationError_withTimeoutError_returnsFalse`() {
            // Given: 타임아웃은 인증 오류가 아님
            val error = KiwoomApiError.TimeoutError("요청 시간 초과")

            // When
            val result = invokeIsAuthError(error)

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("isAuthenticationError_withParseError_returnsFalse")
        fun `isAuthenticationError_withParseError_returnsFalse`() {
            // Given: 파싱 오류는 인증 오류가 아님
            val error = KiwoomApiError.ParseError("JSON 파싱 실패")

            // When
            val result = invokeIsAuthError(error)

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("isAuthenticationError_withGenericException_returnsFalse")
        fun `isAuthenticationError_withGenericException_returnsFalse`() {
            // Given: 일반 예외는 인증 오류가 아님
            val error = RuntimeException("일반 오류")

            // When
            val result = invokeIsAuthError(error)

            // Then
            assertFalse(result)
        }

        @Test
        @DisplayName("isAuthenticationError_withApiCallError200_returnsFalse")
        fun `isAuthenticationError_withApiCallError200_returnsFalse`() {
            // Given: 200 OK는 인증 오류가 아님 (API 레벨 오류)
            val error = KiwoomApiError.ApiCallError(200, "정상 응답이지만 오류")

            // When
            val result = invokeIsAuthError(error)

            // Then
            assertFalse(result)
        }
    }

    // ================================================================
    // mapException 테스트
    // ================================================================

    @Nested
    @DisplayName("mapException — 예외 타입 매핑")
    inner class MapExceptionTests {

        @Test
        @DisplayName("mapException_withUnknownHostException_returnsNetworkError")
        fun `mapException_withUnknownHostException_returnsNetworkError`() {
            // Given
            val exception = java.net.UnknownHostException("api.kiwoom.com")

            // When
            val result = invokeMapException(exception)

            // Then
            assertIs<KiwoomApiError.NetworkError>(result)
        }

        @Test
        @DisplayName("mapException_withSocketTimeoutException_returnsTimeoutError")
        fun `mapException_withSocketTimeoutException_returnsTimeoutError`() {
            // Given
            val exception = java.net.SocketTimeoutException("Read timed out")

            // When
            val result = invokeMapException(exception)

            // Then
            assertIs<KiwoomApiError.TimeoutError>(result)
        }

        @Test
        @DisplayName("mapException_withSerializationException_returnsParseError")
        fun `mapException_withSerializationException_returnsParseError`() {
            // Given
            val exception = kotlinx.serialization.SerializationException("Invalid JSON")

            // When
            val result = invokeMapException(exception)

            // Then
            assertIs<KiwoomApiError.ParseError>(result)
        }

        @Test
        @DisplayName("mapException_withParseError_containsOriginalMessage")
        fun `mapException_withParseError_containsOriginalMessage`() {
            // Given
            val exception = kotlinx.serialization.SerializationException("응답 파싱 오류")

            // When
            val result = invokeMapException(exception)

            // Then
            assertIs<KiwoomApiError.ParseError>(result)
            assertTrue(result.message.contains("응답 파싱 오류"),
                "파싱 오류 메시지가 원본 메시지를 포함해야 한다")
        }

        @Test
        @DisplayName("mapException_withKiwoomApiError_returnsItself")
        fun `mapException_withKiwoomApiError_returnsItself`() {
            // Given: KiwoomApiError 서브타입은 그대로 반환
            val exception = KiwoomApiError.AuthError("인증 실패")

            // When
            val result = invokeMapException(exception)

            // Then: 동일 인스턴스
            assertIs<KiwoomApiError.AuthError>(result)
            assertEquals("인증 실패", result.message)
        }

        @Test
        @DisplayName("mapException_withGenericException_returnsApiCallError")
        fun `mapException_withGenericException_returnsApiCallError`() {
            // Given: 매핑되지 않는 예외는 ApiCallError(0, ...) 반환
            val exception = RuntimeException("알 수 없는 오류")

            // When
            val result = invokeMapException(exception)

            // Then
            assertIs<KiwoomApiError.ApiCallError>(result)
            val error = result as KiwoomApiError.ApiCallError
            assertEquals(0, error.code)
        }

        @Test
        @DisplayName("mapException_withNullMessageException_handlesGracefully")
        fun `mapException_withNullMessageException_handlesGracefully`() {
            // Given: 메시지가 null인 예외
            val exception = RuntimeException(null as String?)

            // When
            val result = invokeMapException(exception)

            // Then: 오류 없이 처리되어야 함
            assertIs<KiwoomApiError.ApiCallError>(result)
        }

        @Test
        @DisplayName("mapException_withNetworkError_messageContainsKorean")
        fun `mapException_withNetworkError_messageContainsKorean`() {
            // Given
            val exception = java.net.UnknownHostException("mockapi.kiwoom.com")

            // When
            val result = invokeMapException(exception)

            // Then: 한국어 사용자 메시지 포함
            assertIs<KiwoomApiError.NetworkError>(result)
            assertTrue(result.message.isNotEmpty(), "네트워크 오류 메시지가 비어있지 않아야 한다")
        }

        @Test
        @DisplayName("mapException_withTimeoutError_messageContainsKorean")
        fun `mapException_withTimeoutError_messageContainsKorean`() {
            // Given
            val exception = java.net.SocketTimeoutException("connect timed out")

            // When
            val result = invokeMapException(exception)

            // Then: 한국어 사용자 메시지 포함
            assertIs<KiwoomApiError.TimeoutError>(result)
            assertTrue(result.message.isNotEmpty(), "타임아웃 오류 메시지가 비어있지 않아야 한다")
        }

        @Test
        @DisplayName("mapException_withNetworkError_codeIsZero")
        fun `mapException_withApiCallError_codeIsZero`() {
            // Given: 기타 예외 → ApiCallError(0, ...)
            val exception = IllegalStateException("상태 오류")

            // When
            val result = invokeMapException(exception)

            // Then
            assertIs<KiwoomApiError.ApiCallError>(result)
            assertEquals(0, (result as KiwoomApiError.ApiCallError).code)
        }
    }

    // ================================================================
    // KiwoomApiError 모델 테스트
    // ================================================================

    @Nested
    @DisplayName("KiwoomApiError 모델 테스트")
    inner class KiwoomApiErrorModelTests {

        @Test
        @DisplayName("NoApiKeyError_hasDefaultMessage")
        fun `NoApiKeyError_hasDefaultMessage`() {
            // Given
            val error = KiwoomApiError.NoApiKeyError()

            // Then
            assertTrue(error.message.isNotEmpty(), "NoApiKeyError는 기본 메시지를 가져야 한다")
        }

        @Test
        @DisplayName("NoApiKeyError_withCustomMessage_usesCustomMessage")
        fun `NoApiKeyError_withCustomMessage_usesCustomMessage`() {
            // Given
            val customMsg = "커스텀 API 키 오류"
            val error = KiwoomApiError.NoApiKeyError(customMsg)

            // Then
            assertEquals(customMsg, error.message)
        }

        @Test
        @DisplayName("ApiCallError_messageIncludesCodeAndMessage")
        fun `ApiCallError_messageIncludesCodeAndMessage`() {
            // Given: ApiCallError(code=500, msg="서버 오류") → message = "[500] 서버 오류"
            val error = KiwoomApiError.ApiCallError(500, "서버 오류")

            // Then
            assertTrue(error.message.contains("500"),
                "ApiCallError 메시지에 코드가 포함되어야 한다")
            assertTrue(error.message.contains("서버 오류"),
                "ApiCallError 메시지에 원본 메시지가 포함되어야 한다")
        }

        @Test
        @DisplayName("ApiCallError_codeIsAccessible")
        fun `ApiCallError_codeIsAccessible`() {
            // Given
            val error = KiwoomApiError.ApiCallError(403, "Forbidden")

            // Then
            assertEquals(403, error.code)
        }

        @Test
        @DisplayName("KiwoomApiError_isException")
        fun `KiwoomApiError_isException`() {
            // All KiwoomApiError subtypes must extend Exception
            val errors: List<KiwoomApiError> = listOf(
                KiwoomApiError.AuthError("auth"),
                KiwoomApiError.NetworkError("net"),
                KiwoomApiError.ApiCallError(0, "api"),
                KiwoomApiError.ParseError("parse"),
                KiwoomApiError.TimeoutError("timeout"),
                KiwoomApiError.NoApiKeyError(),
                KiwoomApiError.RateLimitError("rate")
            )

            errors.forEach { error ->
                assertIs<Exception>(error,
                    "${error::class.simpleName}은 Exception의 서브타입이어야 한다")
            }
        }
    }

    // ================================================================
    // 헬퍼 메서드 — 리플렉션 인보커
    // ================================================================

    private fun invokeNormalize(json: String): String {
        return normalizeJsonNumbersMethod.invoke(client, json) as String
    }

    private fun invokeIsAuthError(error: Throwable): Boolean {
        return isAuthenticationErrorMethod.invoke(client, error) as Boolean
    }

    private fun invokeMapException(exception: Exception): KiwoomApiError {
        return mapExceptionMethod.invoke(client, exception) as KiwoomApiError
    }
}
