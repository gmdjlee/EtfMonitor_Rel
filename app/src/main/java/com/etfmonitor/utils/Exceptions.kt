package com.etfmonitor.utils

/**
 * ETF Monitor 앱의 커스텀 예외 클래스들
 *
 * 예외 계층 구조:
 * - EtfMonitorException (기본 예외)
 *   ├── NetworkException (네트워크 관련)
 *   ├── DataException (데이터 관련)
 *   │   ├── DataNotFoundException
 *   │   ├── DataParsingException
 *   │   └── InsufficientDataException
 *   ├── PythonException (Python 연동 관련)
 *   │   ├── PythonTimeoutException
 *   │   └── PythonRuntimeException
 *   └── ApiException (외부 API 관련)
 *       ├── ApiAuthenticationException
 *       └── ApiRateLimitException
 */

/**
 * ETF Monitor 앱의 기본 예외 클래스
 *
 * 모든 커스텀 예외의 부모 클래스입니다.
 *
 * @param message 예외 메시지
 * @param cause 원인 예외 (선택적)
 */
open class EtfMonitorException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

// ==================== 네트워크 예외 ====================

/**
 * 네트워크 통신 관련 예외
 *
 * 네트워크 연결 실패, 타임아웃, DNS 오류 등에 사용됩니다.
 *
 * @param message 예외 메시지
 * @param cause 원인 예외
 * @param isRecoverable 재시도로 복구 가능한지 여부
 */
class NetworkException(
    message: String,
    cause: Throwable? = null,
    val isRecoverable: Boolean = true
) : EtfMonitorException(message, cause)

// ==================== 데이터 예외 ====================

/**
 * 데이터 관련 기본 예외
 *
 * @param message 예외 메시지
 * @param cause 원인 예외
 */
open class DataException(
    message: String,
    cause: Throwable? = null
) : EtfMonitorException(message, cause)

/**
 * 데이터를 찾을 수 없을 때 발생하는 예외
 *
 * @param entityType 엔티티 타입 (예: "ETF", "Stock", "MarketDeposit")
 * @param identifier 식별자 (예: 티커, 날짜)
 */
class DataNotFoundException(
    val entityType: String,
    val identifier: String
) : DataException("$entityType 데이터를 찾을 수 없습니다: $identifier")

/**
 * 데이터 파싱 실패 시 발생하는 예외
 *
 * JSON 파싱 오류, 타입 변환 오류 등에 사용됩니다.
 *
 * @param message 예외 메시지
 * @param rawData 파싱 실패한 원본 데이터 (디버깅용)
 * @param cause 원인 예외
 */
class DataParsingException(
    message: String,
    val rawData: String? = null,
    cause: Throwable? = null
) : DataException(message, cause)

/**
 * 분석에 필요한 최소 데이터가 부족할 때 발생하는 예외
 *
 * @param requiredCount 필요한 최소 데이터 수
 * @param actualCount 실제 데이터 수
 * @param dataType 데이터 타입 설명
 */
class InsufficientDataException(
    val requiredCount: Int,
    val actualCount: Int,
    val dataType: String = "데이터"
) : DataException("$dataType 분석에 필요한 최소 데이터가 부족합니다 ($actualCount/$requiredCount)")

// ==================== Python 연동 예외 ====================

/**
 * Python 연동 관련 기본 예외
 *
 * @param message 예외 메시지
 * @param moduleName Python 모듈 이름
 * @param functionName Python 함수 이름
 * @param cause 원인 예외
 */
open class PythonException(
    message: String,
    val moduleName: String? = null,
    val functionName: String? = null,
    cause: Throwable? = null
) : EtfMonitorException(message, cause)

/**
 * Python 호출 타임아웃 예외
 *
 * @param timeoutMs 타임아웃 시간 (밀리초)
 * @param moduleName Python 모듈 이름
 * @param functionName Python 함수 이름
 */
class PythonTimeoutException(
    val timeoutMs: Long,
    moduleName: String? = null,
    functionName: String? = null
) : PythonException(
    "Python 호출 타임아웃 (${timeoutMs}ms): ${moduleName ?: "unknown"}.${functionName ?: "unknown"}",
    moduleName,
    functionName
)

/**
 * Python 런타임 에러
 *
 * Python 스크립트 실행 중 발생한 오류에 사용됩니다.
 *
 * @param message 예외 메시지
 * @param moduleName Python 모듈 이름
 * @param functionName Python 함수 이름
 * @param pythonStackTrace Python 스택 트레이스 (있는 경우)
 * @param cause 원인 예외
 */
class PythonRuntimeException(
    message: String,
    moduleName: String? = null,
    functionName: String? = null,
    val pythonStackTrace: String? = null,
    cause: Throwable? = null
) : PythonException(message, moduleName, functionName, cause)

// ==================== API 예외 ====================

/**
 * 외부 API 관련 기본 예외
 *
 * @param message 예외 메시지
 * @param statusCode HTTP 상태 코드 (있는 경우)
 * @param apiName API 이름 (예: "Claude", "Gemini")
 * @param cause 원인 예외
 */
open class ApiException(
    message: String,
    val statusCode: Int? = null,
    val apiName: String? = null,
    cause: Throwable? = null
) : EtfMonitorException(message, cause) {

    companion object {
        /**
         * HTTP 상태 코드에 따른 적절한 ApiException 생성
         */
        fun fromStatusCode(
            statusCode: Int,
            apiName: String,
            responseBody: String? = null
        ): ApiException = when (statusCode) {
            401 -> ApiAuthenticationException(apiName)
            403 -> ApiAuthenticationException(apiName, "API 키가 유효하지 않거나 권한이 없습니다")
            429 -> ApiRateLimitException(apiName)
            in 500..599 -> ApiException(
                "$apiName 서버 오류 (HTTP $statusCode)",
                statusCode,
                apiName
            )
            else -> ApiException(
                "$apiName API 오류 (HTTP $statusCode): ${responseBody ?: "Unknown error"}",
                statusCode,
                apiName
            )
        }
    }
}

/**
 * API 인증 실패 예외
 *
 * API 키가 유효하지 않거나 만료된 경우 사용됩니다.
 *
 * @param apiName API 이름
 * @param reason 인증 실패 사유
 */
class ApiAuthenticationException(
    apiName: String,
    reason: String = "API 키 인증에 실패했습니다"
) : ApiException("$apiName $reason", 401, apiName)

/**
 * API 요청 횟수 제한 초과 예외
 *
 * @param apiName API 이름
 * @param retryAfterSeconds 다시 시도할 수 있는 시간 (초)
 */
class ApiRateLimitException(
    apiName: String,
    val retryAfterSeconds: Int? = null
) : ApiException(
    "$apiName API 요청 한도 초과${retryAfterSeconds?.let { " (${it}초 후 재시도)" } ?: ""}",
    429,
    apiName
)

// ==================== 유틸리티 확장 함수 ====================

/**
 * 일반 Exception을 적절한 EtfMonitorException으로 변환
 *
 * @param context 예외 발생 컨텍스트 설명
 * @return 변환된 EtfMonitorException
 */
fun Throwable.toEtfMonitorException(context: String? = null): EtfMonitorException {
    return when (this) {
        is EtfMonitorException -> this
        is java.net.SocketTimeoutException -> NetworkException(
            context ?: "네트워크 타임아웃",
            this,
            isRecoverable = true
        )
        is java.net.UnknownHostException -> NetworkException(
            context ?: "네트워크 연결 실패 (호스트를 찾을 수 없음)",
            this,
            isRecoverable = true
        )
        is java.io.IOException -> NetworkException(
            context ?: "네트워크 I/O 오류",
            this,
            isRecoverable = true
        )
        is kotlinx.serialization.SerializationException -> DataParsingException(
            context ?: "데이터 파싱 오류",
            cause = this
        )
        is kotlinx.coroutines.TimeoutCancellationException -> PythonTimeoutException(
            timeoutMs = 0,  // 실제 타임아웃 값은 알 수 없음
            moduleName = null,
            functionName = null
        )
        else -> EtfMonitorException(
            context ?: this.message ?: "알 수 없는 오류",
            this
        )
    }
}
