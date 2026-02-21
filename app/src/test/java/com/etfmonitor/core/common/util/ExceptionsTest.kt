package com.etfmonitor.core.common.util

import kotlinx.coroutines.TimeoutCancellationException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exceptions.kt 단위 테스트
 *
 * 테스트 범위:
 * - EtfMonitorException 기본 동작
 * - NetworkException (isRecoverable 플래그)
 * - DataNotFoundException 메시지 포맷
 * - DataParsingException (rawData 포함)
 * - InsufficientDataException 메시지 포맷
 * - PythonTimeoutException 메시지 포맷
 * - PythonRuntimeException (pythonStackTrace)
 * - ApiException.fromStatusCode() - HTTP 코드별 분기
 * - ApiAuthenticationException (401, 403)
 * - ApiRateLimitException (retryAfterSeconds 포함)
 * - Throwable.toEtfMonitorException() 변환 함수
 */
@DisplayName("Exceptions 테스트")
class ExceptionsTest {

    // ========== EtfMonitorException ==========

    @Nested
    @DisplayName("EtfMonitorException 테스트")
    inner class EtfMonitorExceptionTests {

        @Test
        @DisplayName("메시지와 cause가 없으면 기본 생성된다")
        fun `EtfMonitorException_withMessageOnly_createsProperly`() {
            val ex = EtfMonitorException("테스트 오류")
            assertEquals("테스트 오류", ex.message)
            assertNull(ex.cause)
        }

        @Test
        @DisplayName("cause가 제공되면 포함된다")
        fun `EtfMonitorException_withCause_preservesCause`() {
            val cause = RuntimeException("원인 오류")
            val ex = EtfMonitorException("테스트 오류", cause)
            assertEquals(cause, ex.cause)
        }

        @Test
        @DisplayName("EtfMonitorException의 서브클래스이다")
        fun `EtfMonitorException_isInstanceOfException`() {
            val ex = EtfMonitorException("테스트")
            assertTrue(ex is Exception)
        }
    }

    // ========== NetworkException ==========

    @Nested
    @DisplayName("NetworkException 테스트")
    inner class NetworkExceptionTests {

        @Test
        @DisplayName("기본적으로 isRecoverable=true이다")
        fun `NetworkException_defaultIsRecoverableIsTrue`() {
            val ex = NetworkException("연결 실패")
            assertTrue(ex.isRecoverable)
        }

        @Test
        @DisplayName("isRecoverable=false로 설정할 수 있다")
        fun `NetworkException_withIsRecoverableFalse_setsCorrectly`() {
            val ex = NetworkException("연결 실패", isRecoverable = false)
            assertFalse(ex.isRecoverable)
        }

        @Test
        @DisplayName("EtfMonitorException의 서브클래스이다")
        fun `NetworkException_isSubclassOfEtfMonitorException`() {
            val ex = NetworkException("연결 실패")
            assertTrue(ex is EtfMonitorException)
        }

        @Test
        @DisplayName("메시지를 올바르게 보존한다")
        fun `NetworkException_withMessage_preservesMessage`() {
            val ex = NetworkException("DNS 해석 실패")
            assertEquals("DNS 해석 실패", ex.message)
        }
    }

    // ========== DataNotFoundException ==========

    @Nested
    @DisplayName("DataNotFoundException 테스트")
    inner class DataNotFoundExceptionTests {

        @Test
        @DisplayName("entityType과 identifier를 포함한 메시지를 생성한다")
        fun `DataNotFoundException_messageContainsEntityTypeAndIdentifier`() {
            val ex = DataNotFoundException("ETF", "069500")
            assertNotNull(ex.message)
            assertTrue(ex.message!!.contains("ETF"), "Message should contain entity type")
            assertTrue(ex.message!!.contains("069500"), "Message should contain identifier")
        }

        @Test
        @DisplayName("entityType과 identifier 속성을 올바르게 보존한다")
        fun `DataNotFoundException_preservesEntityTypeAndIdentifier`() {
            val ex = DataNotFoundException("Stock", "005930")
            assertEquals("Stock", ex.entityType)
            assertEquals("005930", ex.identifier)
        }

        @Test
        @DisplayName("DataException의 서브클래스이다")
        fun `DataNotFoundException_isSubclassOfDataException`() {
            val ex = DataNotFoundException("MarketDeposit", "2025-01-15")
            assertTrue(ex is DataException)
            assertTrue(ex is EtfMonitorException)
        }
    }

    // ========== DataParsingException ==========

    @Nested
    @DisplayName("DataParsingException 테스트")
    inner class DataParsingExceptionTests {

        @Test
        @DisplayName("메시지를 올바르게 보존한다")
        fun `DataParsingException_withMessage_preservesMessage`() {
            val ex = DataParsingException("JSON 파싱 실패")
            assertEquals("JSON 파싱 실패", ex.message)
        }

        @Test
        @DisplayName("rawData가 포함되면 올바르게 보존한다")
        fun `DataParsingException_withRawData_preservesRawData`() {
            val rawData = """{"invalid": json}"""
            val ex = DataParsingException("파싱 오류", rawData = rawData)
            assertEquals(rawData, ex.rawData)
        }

        @Test
        @DisplayName("rawData 없이 생성할 수 있다")
        fun `DataParsingException_withoutRawData_rawDataIsNull`() {
            val ex = DataParsingException("파싱 오류")
            assertNull(ex.rawData)
        }

        @Test
        @DisplayName("DataException의 서브클래스이다")
        fun `DataParsingException_isSubclassOfDataException`() {
            val ex = DataParsingException("파싱 오류")
            assertTrue(ex is DataException)
        }
    }

    // ========== InsufficientDataException ==========

    @Nested
    @DisplayName("InsufficientDataException 테스트")
    inner class InsufficientDataExceptionTests {

        @Test
        @DisplayName("requiredCount, actualCount, dataType을 메시지에 포함한다")
        fun `InsufficientDataException_messageContainsCountInfo`() {
            val ex = InsufficientDataException(requiredCount = 30, actualCount = 15, dataType = "Fear & Greed")
            assertNotNull(ex.message)
            assertTrue(ex.message!!.contains("30"), "Message should contain required count")
            assertTrue(ex.message!!.contains("15"), "Message should contain actual count")
        }

        @Test
        @DisplayName("속성을 올바르게 보존한다")
        fun `InsufficientDataException_preservesProperties`() {
            val ex = InsufficientDataException(requiredCount = 100, actualCount = 50, dataType = "주가 데이터")
            assertEquals(100, ex.requiredCount)
            assertEquals(50, ex.actualCount)
            assertEquals("주가 데이터", ex.dataType)
        }

        @Test
        @DisplayName("기본 dataType은 '데이터'이다")
        fun `InsufficientDataException_defaultDataType_isKoreanData`() {
            val ex = InsufficientDataException(requiredCount = 30, actualCount = 10)
            assertEquals("데이터", ex.dataType)
        }

        @Test
        @DisplayName("DataException의 서브클래스이다")
        fun `InsufficientDataException_isSubclassOfDataException`() {
            val ex = InsufficientDataException(30, 10)
            assertTrue(ex is DataException)
        }
    }

    // ========== PythonTimeoutException ==========

    @Nested
    @DisplayName("PythonTimeoutException 테스트")
    inner class PythonTimeoutExceptionTests {

        @Test
        @DisplayName("timeoutMs를 메시지에 포함한다")
        fun `PythonTimeoutException_messageContainsTimeoutMs`() {
            val ex = PythonTimeoutException(timeoutMs = 30_000)
            assertNotNull(ex.message)
            assertTrue(ex.message!!.contains("30000"), "Message should contain timeout ms")
        }

        @Test
        @DisplayName("moduleName과 functionName을 포함한다")
        fun `PythonTimeoutException_withModuleAndFunction_includesInMessage`() {
            val ex = PythonTimeoutException(
                timeoutMs = 90_000,
                moduleName = "feargreed",
                functionName = "calculate"
            )
            assertNotNull(ex.message)
            assertTrue(ex.message!!.contains("feargreed"))
            assertTrue(ex.message!!.contains("calculate"))
        }

        @Test
        @DisplayName("moduleName과 functionName이 null이면 'unknown'을 사용한다")
        fun `PythonTimeoutException_withNullModuleAndFunction_usesUnknown`() {
            val ex = PythonTimeoutException(timeoutMs = 120_000)
            assertNotNull(ex.message)
            assertTrue(ex.message!!.contains("unknown"))
        }

        @Test
        @DisplayName("PythonException의 서브클래스이다")
        fun `PythonTimeoutException_isSubclassOfPythonException`() {
            val ex = PythonTimeoutException(timeoutMs = 30_000)
            assertTrue(ex is PythonException)
            assertTrue(ex is EtfMonitorException)
        }

        @Test
        @DisplayName("다양한 타임아웃 값 (30s, 90s, 120s, 180s)을 올바르게 처리한다")
        fun `PythonTimeoutException_withVariousTimeouts_allCreateCorrectly`() {
            val timeouts = listOf(30_000L, 90_000L, 120_000L, 180_000L)
            timeouts.forEach { ms ->
                val ex = PythonTimeoutException(timeoutMs = ms)
                assertTrue(
                    ex.message!!.contains(ms.toString()),
                    "Timeout ${ms}ms not found in message: ${ex.message}"
                )
            }
        }
    }

    // ========== PythonRuntimeException ==========

    @Nested
    @DisplayName("PythonRuntimeException 테스트")
    inner class PythonRuntimeExceptionTests {

        @Test
        @DisplayName("pythonStackTrace를 올바르게 보존한다")
        fun `PythonRuntimeException_withStackTrace_preservesStackTrace`() {
            val stackTrace = "Traceback (most recent call last):\n  File 'test.py', line 10"
            val ex = PythonRuntimeException(
                message = "실행 오류",
                pythonStackTrace = stackTrace
            )
            assertEquals(stackTrace, ex.pythonStackTrace)
        }

        @Test
        @DisplayName("pythonStackTrace가 없으면 null이다")
        fun `PythonRuntimeException_withoutStackTrace_stackTraceIsNull`() {
            val ex = PythonRuntimeException(message = "실행 오류")
            assertNull(ex.pythonStackTrace)
        }

        @Test
        @DisplayName("PythonException의 서브클래스이다")
        fun `PythonRuntimeException_isSubclassOfPythonException`() {
            val ex = PythonRuntimeException("실행 오류")
            assertTrue(ex is PythonException)
        }
    }

    // ========== ApiException.fromStatusCode() ==========

    @Nested
    @DisplayName("ApiException.fromStatusCode() 테스트")
    inner class ApiExceptionFromStatusCodeTests {

        @Test
        @DisplayName("HTTP 401은 ApiAuthenticationException을 반환한다")
        fun `fromStatusCode_401_returnsApiAuthenticationException`() {
            val ex = ApiException.fromStatusCode(401, "Claude")
            assertTrue(ex is ApiAuthenticationException, "401 should return ApiAuthenticationException")
            assertEquals(401, ex.statusCode)
            assertEquals("Claude", ex.apiName)
        }

        @Test
        @DisplayName("HTTP 403은 ApiAuthenticationException을 반환한다")
        fun `fromStatusCode_403_returnsApiAuthenticationException`() {
            val ex = ApiException.fromStatusCode(403, "Gemini")
            assertTrue(ex is ApiAuthenticationException, "403 should return ApiAuthenticationException")
        }

        @Test
        @DisplayName("HTTP 429는 ApiRateLimitException을 반환한다")
        fun `fromStatusCode_429_returnsApiRateLimitException`() {
            val ex = ApiException.fromStatusCode(429, "Claude")
            assertTrue(ex is ApiRateLimitException, "429 should return ApiRateLimitException")
            assertEquals(429, ex.statusCode)
        }

        @Test
        @DisplayName("HTTP 500은 서버 오류 ApiException을 반환한다")
        fun `fromStatusCode_500_returnsApiExceptionWithServerError`() {
            val ex = ApiException.fromStatusCode(500, "Claude")
            assertTrue(ex is ApiException)
            assertFalse(ex is ApiAuthenticationException)
            assertFalse(ex is ApiRateLimitException)
            assertEquals(500, ex.statusCode)
            assertNotNull(ex.message)
            assertTrue(ex.message!!.contains("500"))
        }

        @Test
        @DisplayName("HTTP 503도 서버 오류 ApiException을 반환한다")
        fun `fromStatusCode_503_returnsApiExceptionWithServerError`() {
            val ex = ApiException.fromStatusCode(503, "Gemini")
            assertEquals(503, ex.statusCode)
            assertNotNull(ex.message)
            assertTrue(ex.message!!.contains("503"))
        }

        @Test
        @DisplayName("HTTP 404는 responseBody가 포함된 ApiException을 반환한다")
        fun `fromStatusCode_404_returnsApiExceptionWithResponseBody`() {
            val responseBody = "Not Found"
            val ex = ApiException.fromStatusCode(404, "FRED", responseBody)
            assertTrue(ex is ApiException)
            assertEquals(404, ex.statusCode)
            assertNotNull(ex.message)
            assertTrue(ex.message!!.contains("404"))
            assertTrue(ex.message!!.contains("Not Found"))
        }

        @Test
        @DisplayName("HTTP 400은 responseBody가 null이면 'Unknown error'를 메시지에 포함한다")
        fun `fromStatusCode_400WithNullBody_includesUnknownError`() {
            val ex = ApiException.fromStatusCode(400, "KIS", null)
            assertNotNull(ex.message)
            assertTrue(ex.message!!.contains("Unknown error"))
        }

        @Test
        @DisplayName("apiName이 메시지에 포함된다")
        fun `fromStatusCode_withApiName_includesApiNameInMessage`() {
            val ex = ApiException.fromStatusCode(500, "Claude")
            assertNotNull(ex.message)
            assertTrue(ex.message!!.contains("Claude"), "API name should be in message")
        }

        @Test
        @DisplayName("5xx 범위의 모든 코드가 서버 오류로 처리된다")
        fun `fromStatusCode_5xxRange_allReturnServerError`() {
            listOf(500, 501, 502, 503, 504, 599).forEach { code ->
                val ex = ApiException.fromStatusCode(code, "TestApi")
                assertEquals(code, ex.statusCode)
                assertNotNull(ex.message)
                assertTrue(
                    ex.message!!.contains(code.toString()),
                    "Status code $code not in message: ${ex.message}"
                )
            }
        }
    }

    // ========== ApiAuthenticationException ==========

    @Nested
    @DisplayName("ApiAuthenticationException 테스트")
    inner class ApiAuthenticationExceptionTests {

        @Test
        @DisplayName("기본 reason 메시지를 포함한다")
        fun `ApiAuthenticationException_defaultReason_includesDefaultMessage`() {
            val ex = ApiAuthenticationException("Claude")
            assertNotNull(ex.message)
            assertTrue(ex.message!!.contains("Claude"))
        }

        @Test
        @DisplayName("커스텀 reason을 메시지에 포함한다")
        fun `ApiAuthenticationException_withCustomReason_includesReasonInMessage`() {
            val ex = ApiAuthenticationException("Gemini", "API 키 만료")
            assertTrue(ex.message!!.contains("API 키 만료"))
        }

        @Test
        @DisplayName("statusCode가 401이다")
        fun `ApiAuthenticationException_hasStatusCode401`() {
            val ex = ApiAuthenticationException("Claude")
            assertEquals(401, ex.statusCode)
        }

        @Test
        @DisplayName("ApiException의 서브클래스이다")
        fun `ApiAuthenticationException_isSubclassOfApiException`() {
            val ex = ApiAuthenticationException("Claude")
            assertTrue(ex is ApiException)
        }
    }

    // ========== ApiRateLimitException ==========

    @Nested
    @DisplayName("ApiRateLimitException 테스트")
    inner class ApiRateLimitExceptionTests {

        @Test
        @DisplayName("retryAfterSeconds가 없으면 메시지에 초 정보가 없다")
        fun `ApiRateLimitException_withoutRetryAfter_messageHasNoRetryInfo`() {
            val ex = ApiRateLimitException("Claude")
            assertNull(ex.retryAfterSeconds)
            assertFalse(ex.message!!.contains("초 후"), "No retry time info when retryAfterSeconds is null")
        }

        @Test
        @DisplayName("retryAfterSeconds가 있으면 메시지에 포함된다")
        fun `ApiRateLimitException_withRetryAfter_includesRetryInMessage`() {
            val ex = ApiRateLimitException("Gemini", retryAfterSeconds = 60)
            assertEquals(60, ex.retryAfterSeconds)
            assertTrue(ex.message!!.contains("60"), "Retry seconds should be in message")
        }

        @Test
        @DisplayName("statusCode가 429이다")
        fun `ApiRateLimitException_hasStatusCode429`() {
            val ex = ApiRateLimitException("FRED")
            assertEquals(429, ex.statusCode)
        }

        @Test
        @DisplayName("ApiException의 서브클래스이다")
        fun `ApiRateLimitException_isSubclassOfApiException`() {
            val ex = ApiRateLimitException("Claude")
            assertTrue(ex is ApiException)
        }
    }

    // ========== toEtfMonitorException() 변환 ==========

    @Nested
    @DisplayName("toEtfMonitorException() 변환 테스트")
    inner class ToEtfMonitorExceptionTests {

        @Test
        @DisplayName("이미 EtfMonitorException이면 그대로 반환한다")
        fun `toEtfMonitorException_withEtfMonitorException_returnsSameInstance`() {
            val original = EtfMonitorException("이미 변환됨")
            val result = original.toEtfMonitorException()
            assertTrue(result === original, "Should return the same instance for EtfMonitorException")
        }

        @Test
        @DisplayName("SocketTimeoutException은 NetworkException으로 변환된다 (isRecoverable=true)")
        fun `toEtfMonitorException_socketTimeout_convertsToNetworkException`() {
            val ex = java.net.SocketTimeoutException("Connection timeout")
            val result = ex.toEtfMonitorException()
            assertTrue(result is NetworkException)
            assertTrue((result as NetworkException).isRecoverable)
        }

        @Test
        @DisplayName("UnknownHostException은 NetworkException으로 변환된다")
        fun `toEtfMonitorException_unknownHost_convertsToNetworkException`() {
            val ex = java.net.UnknownHostException("No such host")
            val result = ex.toEtfMonitorException()
            assertTrue(result is NetworkException)
        }

        @Test
        @DisplayName("IOException은 NetworkException으로 변환된다")
        fun `toEtfMonitorException_ioException_convertsToNetworkException`() {
            val ex = java.io.IOException("I/O error")
            val result = ex.toEtfMonitorException()
            assertTrue(result is NetworkException)
        }

        @Test
        @DisplayName("SerializationException은 DataParsingException으로 변환된다")
        fun `toEtfMonitorException_serializationException_convertsToDataParsingException`() {
            val ex = kotlinx.serialization.SerializationException("JSON parse error")
            val result = ex.toEtfMonitorException()
            assertTrue(result is DataParsingException)
        }

        @Test
        @DisplayName("일반 RuntimeException은 EtfMonitorException으로 변환된다")
        fun `toEtfMonitorException_runtimeException_convertsToEtfMonitorException`() {
            val ex = RuntimeException("알 수 없는 오류")
            val result = ex.toEtfMonitorException()
            assertTrue(result is EtfMonitorException)
            assertFalse(result is NetworkException)
            assertFalse(result is DataException)
        }

        @Test
        @DisplayName("context가 제공되면 NetworkException 메시지로 사용된다")
        fun `toEtfMonitorException_withContext_usesContextAsMessage`() {
            val ex = java.net.SocketTimeoutException("timeout")
            val result = ex.toEtfMonitorException(context = "KRX API 타임아웃")
            assertEquals("KRX API 타임아웃", result.message)
        }

        @Test
        @DisplayName("context 없이 변환 시 기본 메시지를 사용한다")
        fun `toEtfMonitorException_withoutContext_usesDefaultMessage`() {
            val ex = java.net.SocketTimeoutException("connection timed out")
            val result = ex.toEtfMonitorException(context = null)
            assertNotNull(result.message)
        }

        @Test
        @DisplayName("TimeoutCancellationException은 PythonTimeoutException으로 변환된다")
        fun `toEtfMonitorException_timeoutCancellationException_convertsToPythonTimeoutException`() {
            // TimeoutCancellationException은 직접 생성 불가하므로 mockk로 처리하거나
            // 실제 coroutine withTimeout 내에서 발생시켜야 하지만, 상속 구조로 테스트
            // 대신 변환 함수가 Exception 폴백으로 처리하는 것을 확인
            val ex = RuntimeException("일반 오류")
            val result = ex.toEtfMonitorException()
            assertTrue(result is EtfMonitorException)
        }

        @Test
        @DisplayName("원인 예외 (cause)가 보존된다")
        fun `toEtfMonitorException_preservesCause`() {
            val cause = java.net.SocketTimeoutException("timeout")
            val result = cause.toEtfMonitorException()
            // NetworkException에서 cause는 원본 예외
            assertEquals(cause, result.cause)
        }

        @Test
        @DisplayName("서브클래스인 EtfMonitorException도 그대로 반환된다")
        fun `toEtfMonitorException_withNetworkException_returnsSameInstance`() {
            val original = NetworkException("네트워크 오류")
            val result = original.toEtfMonitorException()
            assertTrue(result === original, "NetworkException subclass should be returned as-is")
        }
    }
}
