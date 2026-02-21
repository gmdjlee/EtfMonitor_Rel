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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

data class KiwoomTokenInfo(
    val token: String,
    val expiresAt: LocalDateTime,
    val tokenType: String = "bearer"
) {
    val bearer: String get() = "Bearer $token"

    fun isExpired(): Boolean {
        return LocalDateTime.now() >= expiresAt.minusMinutes(1)
    }
}

@Singleton
class KiwoomTokenManager @Inject constructor(
    @KiwoomOkHttp private val httpClient: OkHttpClient,
    private val json: Json
) {
    private val tokenCache = mutableMapOf<String, KiwoomTokenInfo>()
    private val tokenMutex = Mutex()

    suspend fun getToken(
        appKey: String,
        secretKey: String,
        baseUrl: String
    ): Result<KiwoomTokenInfo> = tokenMutex.withLock {
        val cacheKey = "$baseUrl:${appKey.hashCode()}"

        val cachedToken = tokenCache[cacheKey]
        if (cachedToken != null && !cachedToken.isExpired()) {
            return@withLock Result.success(cachedToken)
        }

        return@withLock fetchToken(appKey, secretKey, baseUrl).also { result ->
            result.onSuccess { token ->
                tokenCache[cacheKey] = token
            }
        }
    }

    private suspend fun fetchToken(
        appKey: String,
        secretKey: String,
        baseUrl: String
    ): Result<KiwoomTokenInfo> = withContext(Dispatchers.IO) {
        var lastError: KiwoomApiError? = null

        for (attempt in 0..MAX_RETRIES) {
            val result = fetchTokenOnce(appKey, secretKey, baseUrl)

            result.fold(
                onSuccess = { return@withContext Result.success(it) },
                onFailure = { error ->
                    lastError = error as? KiwoomApiError
                        ?: KiwoomApiError.AuthError(error.message ?: "알 수 없는 오류")

                    val isRetriable = error is KiwoomApiError.NetworkError || error is KiwoomApiError.TimeoutError
                    if (isRetriable && attempt < MAX_RETRIES) {
                        val delayMs = RETRY_DELAYS_MS.getOrElse(attempt) { 4000L }
                        logger.w("Token fetch failed (attempt ${attempt + 1}/${MAX_RETRIES + 1}), retrying in ${delayMs}ms: ${error.message}")
                        delay(delayMs)
                    } else {
                        return@withContext Result.failure(error)
                    }
                }
            )
        }

        Result.failure(lastError ?: KiwoomApiError.AuthError("토큰 발급 실패"))
    }

    private suspend fun fetchTokenOnce(
        appKey: String,
        secretKey: String,
        baseUrl: String
    ): Result<KiwoomTokenInfo> {
        try {
            val requestBody = json.encodeToString(mapOf(
                "grant_type" to "client_credentials",
                "appkey" to appKey,
                "secretkey" to secretKey
            ))

            val request = Request.Builder()
                .url("$baseUrl/oauth2/token")
                .addHeader("api-id", "au10001")
                .addHeader("Content-Type", "application/json;charset=UTF-8")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val (responseBody, responseCode, isSuccessful) = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    Triple(response.body?.string(), response.code, response.isSuccessful)
                }
            }

            if (!isSuccessful || responseBody == null) {
                logger.e("Token fetch failed: $responseCode")
                return Result.failure(
                    KiwoomApiError.AuthError("토큰 발급 실패: HTTP $responseCode")
                )
            }

            val tokenResponse = json.decodeFromString<KiwoomTokenResponse>(responseBody)

            if (tokenResponse.returnCode != 0) {
                return Result.failure(
                    KiwoomApiError.AuthError(tokenResponse.returnMsg ?: "토큰 발급 실패")
                )
            }

            val token = tokenResponse.token
            val expiresDt = tokenResponse.expiresDt

            if (token == null || expiresDt == null) {
                return Result.failure(
                    KiwoomApiError.AuthError("토큰 응답이 올바르지 않습니다")
                )
            }

            val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            val expiresAt = LocalDateTime.parse(expiresDt, formatter)

            return Result.success(KiwoomTokenInfo(token, expiresAt, tokenResponse.tokenType ?: "bearer"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e("Token fetch exception", e)
            return Result.failure(
                when (e) {
                    is java.net.UnknownHostException -> KiwoomApiError.NetworkError("네트워크 연결을 확인해주세요")
                    is java.net.SocketTimeoutException -> KiwoomApiError.TimeoutError("요청 시간이 초과되었습니다")
                    else -> KiwoomApiError.AuthError("토큰 발급 중 오류 발생: ${e.message}")
                }
            )
        }
    }

    suspend fun refreshToken(
        appKey: String,
        secretKey: String,
        baseUrl: String
    ): Result<KiwoomTokenInfo> = tokenMutex.withLock {
        val cacheKey = "$baseUrl:${appKey.hashCode()}"
        tokenCache.remove(cacheKey)

        return@withLock fetchToken(appKey, secretKey, baseUrl).also { result ->
            result.onSuccess { token ->
                tokenCache[cacheKey] = token
            }
        }
    }

    companion object {
        private val logger = AppLogger.getLogger("KiwoomTokenManager")
        private const val MAX_RETRIES = 3
        private val RETRY_DELAYS_MS = listOf(1000L, 2000L, 4000L)
    }
}
