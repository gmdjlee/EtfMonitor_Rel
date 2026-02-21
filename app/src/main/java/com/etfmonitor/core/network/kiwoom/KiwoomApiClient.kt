package com.etfmonitor.core.network.kiwoom

import com.etfmonitor.core.common.util.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

private class CategoryRateLimiter(private val minInterval: Long = 500L) {
    private var lastCallTime = 0L
    private val mutex = Mutex()

    suspend fun waitForRateLimit() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastCallTime
            if (elapsed < minInterval) {
                delay(minInterval - elapsed)
            }
            lastCallTime = System.currentTimeMillis()
        }
    }
}

@Singleton
class KiwoomApiClient @Inject constructor(
    private val tokenManager: KiwoomTokenManager,
    @KiwoomOkHttp private val httpClient: OkHttpClient,
    private val json: Json
) {
    private val rankingRateLimiter = CategoryRateLimiter(500L)

    suspend fun <T> call(
        apiId: String,
        url: String,
        body: Map<String, String>,
        appKey: String,
        secretKey: String,
        baseUrl: String,
        parser: (String) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        val result = callOnce(apiId, url, body, appKey, secretKey, baseUrl, parser)

        result.fold(
            onSuccess = { return@withContext Result.success(it) },
            onFailure = { error ->
                if (isAuthenticationError(error)) {
                    logger.w("Auth error detected, refreshing token and retrying: ${error.message}")

                    val refreshResult = tokenManager.refreshToken(appKey, secretKey, baseUrl)
                    if (refreshResult.isFailure) {
                        return@withContext Result.failure(
                            KiwoomApiError.AuthError("토큰 갱신 실패: ${refreshResult.exceptionOrNull()?.message}")
                        )
                    }

                    return@withContext callOnce(apiId, url, body, appKey, secretKey, baseUrl, parser)
                }

                return@withContext Result.failure(error)
            }
        )
    }

    private suspend fun <T> callOnce(
        apiId: String,
        url: String,
        body: Map<String, String>,
        appKey: String,
        secretKey: String,
        baseUrl: String,
        parser: (String) -> T
    ): Result<T> {
        try {
            rankingRateLimiter.waitForRateLimit()

            val tokenResult = tokenManager.getToken(appKey, secretKey, baseUrl)
            val token = tokenResult.getOrElse { error ->
                return Result.failure(error)
            }

            val requestBodyJson = json.encodeToString(body)

            val request = Request.Builder()
                .url("$baseUrl$url")
                .addHeader("api-id", apiId)
                .addHeader("authorization", token.bearer)
                .addHeader("Content-Type", "application/json;charset=UTF-8")
                .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            logger.d("API call: $apiId -> $url")

            val (responseBody, responseCode, isSuccessful) = httpClient.newCall(request).execute().use { response ->
                Triple(response.body?.string(), response.code, response.isSuccessful)
            }

            if (!isSuccessful || responseBody == null) {
                logger.e("API call failed: $responseCode")
                return Result.failure(
                    KiwoomApiError.ApiCallError(responseCode, "HTTP $responseCode")
                )
            }

            val normalizedBody = normalizeJsonNumbers(responseBody)

            val apiResponse = json.decodeFromString<KiwoomApiResponse>(normalizedBody)
            if (apiResponse.returnCode != 0) {
                return Result.failure(
                    KiwoomApiError.ApiCallError(apiResponse.returnCode, apiResponse.returnMsg ?: "API 오류")
                )
            }

            val parsed = parser(normalizedBody)
            return Result.success(parsed)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return Result.failure(mapException(e))
        }
    }

    private fun isAuthenticationError(error: Throwable): Boolean {
        return when {
            error is KiwoomApiError.AuthError -> true
            error is KiwoomApiError.ApiCallError -> {
                error.code == 401 || error.code == 403 ||
                    error.message?.contains("인증", ignoreCase = true) == true ||
                    error.message?.contains("토큰", ignoreCase = true) == true ||
                    error.message?.contains("권한", ignoreCase = true) == true
            }
            else -> false
        }
    }

    private fun mapException(e: Exception): KiwoomApiError {
        logger.e("API call exception: ${e.javaClass.simpleName} - ${e.message}")
        return when (e) {
            is java.net.UnknownHostException -> KiwoomApiError.NetworkError("네트워크 연결을 확인해주세요")
            is java.net.SocketTimeoutException -> KiwoomApiError.TimeoutError("요청 시간이 초과되었습니다")
            is kotlinx.serialization.SerializationException -> KiwoomApiError.ParseError("응답 파싱 오류: ${e.message}")
            is KiwoomApiError -> e
            else -> KiwoomApiError.ApiCallError(0, e.message ?: "알 수 없는 오류")
        }
    }

    private fun normalizeJsonNumbers(json: String): String {
        var result = QUOTED_PLUS_REGEX.replace(json) { "\"${it.groupValues[1]}\"" }
        result = UNQUOTED_PLUS_REGEX.replace(result) { "${it.groupValues[1]}${it.groupValues[2]}" }
        return result
    }

    companion object {
        private val logger = AppLogger.getLogger("KiwoomApiClient")
        private val QUOTED_PLUS_REGEX = Regex("\"\\+(\\d+)\"")
        private val UNQUOTED_PLUS_REGEX = Regex("([,:])\\s*\\+(\\d+)")
    }
}
