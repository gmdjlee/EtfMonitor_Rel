package com.etfmonitor.core.network.kiwoom

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KiwoomApiResponse(
    @SerialName("return_code") val returnCode: Int = 0,
    @SerialName("return_msg") val returnMsg: String? = null
)

@Serializable
data class KiwoomTokenResponse(
    @SerialName("return_code") val returnCode: Int = 0,
    @SerialName("return_msg") val returnMsg: String? = null,
    @SerialName("token") val token: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_dt") val expiresDt: String? = null
)

/**
 * ka10001 - 주식 기본정보 응답
 */
@Serializable
data class StockBasicInfoResponse(
    @SerialName("return_code") val returnCode: Int = 0,
    @SerialName("return_msg") val returnMsg: String? = null,
    @SerialName("stk_nm") val stkNm: String? = null,
    @SerialName("flo_stk") val floStk: String? = null   // 유통주식수 (천주 단위)
)

sealed class KiwoomApiError(override val message: String) : Exception(message) {
    class AuthError(msg: String) : KiwoomApiError(msg)
    class NetworkError(msg: String) : KiwoomApiError(msg)
    class RateLimitError(msg: String) : KiwoomApiError(msg)
    class ApiCallError(val code: Int, msg: String) : KiwoomApiError("[$code] $msg")
    class ParseError(msg: String) : KiwoomApiError(msg)
    class TimeoutError(msg: String) : KiwoomApiError(msg)
    class NoApiKeyError(
        msg: String = "키움 API 키가 설정되지 않았습니다. 설정에서 API 키를 입력해주세요."
    ) : KiwoomApiError(msg)
}
