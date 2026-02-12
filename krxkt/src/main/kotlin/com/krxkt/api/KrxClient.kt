package com.krxkt.api

import com.krxkt.error.KrxError
import kotlinx.coroutines.delay
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * KRX API HTTP 클라이언트
 *
 * 특징:
 * - OkHttp 기반 POST 요청
 * - application/x-www-form-urlencoded 인코딩
 * - 필수 Referer 헤더 포함
 * - Exponential backoff 재시도 (3회: 1s/2s/4s)
 * - 쿠키 자동 관리 (세션 유지)
 *
 * 참고: KRX API는 세션 기반 인증을 사용하며 일부 환경에서
 * "LOGOUT" 응답이 반환될 수 있음. 이 경우:
 * 1. 한국 내 네트워크에서 접속
 * 2. VPN 사용
 * 3. pykrx와 같은 세션 초기화 로직 추가 필요
 *
 * @param okHttpClient 커스텀 OkHttpClient (테스트용)
 * @param baseUrl 기본 API URL (테스트/프록시용)
 */
class KrxClient(
    private val okHttpClient: OkHttpClient = createDefaultClient(),
    private val baseUrl: String = KrxEndpoints.BASE_URL
) {
    companion object {
        private const val MAX_RETRIES = 3
        private val RETRY_DELAYS_MS = listOf(1000L, 2000L, 4000L)

        /** Maximum response body size: 50MB (KRX full-market responses can be large) */
        private const val MAX_RESPONSE_SIZE_BYTES = 50L * 1024 * 1024

        /**
         * 기본 OkHttpClient 생성
         * - 연결 타임아웃: 30초
         * - 읽기 타임아웃: 30초
         * - 쓰기 타임아웃: 30초
         */
        fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * KRX API POST 요청 실행
     *
     * @param params 요청 파라미터 (bld, mktId, trdDd 등)
     * @return JSON 응답 문자열
     * @throws KrxError.NetworkError 네트워크 에러 (재시도 후에도 실패)
     */
    suspend fun post(params: Map<String, String>): String {
        var lastException: Exception? = null

        repeat(MAX_RETRIES) { attempt ->
            try {
                return executeRequest(params)
            } catch (e: CancellationException) {
                throw e // Coroutine 취소는 재시도하지 않음
            } catch (e: IOException) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    delay(RETRY_DELAYS_MS[attempt])
                }
            }
        }

        throw KrxError.NetworkError(
            "Failed after $MAX_RETRIES attempts: ${lastException?.message}",
            lastException
        )
    }

    /**
     * 실제 HTTP 요청 실행
     */
    private fun executeRequest(params: Map<String, String>): String {
        val formBody = FormBody.Builder().apply {
            params.forEach { (key, value) ->
                add(key, value)
            }
        }.build()

        val bld = params["bld"] ?: "unknown"

        val request = Request.Builder()
            .url(baseUrl)
            .post(formBody)
            .addHeader("Referer", KrxEndpoints.REFERER)
            .addHeader("User-Agent", KrxEndpoints.USER_AGENT)
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
            // Accept-Encoding은 OkHttp가 자동 처리 (gzip 압축 해제 포함)
            .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("Origin", "https://data.krx.co.kr")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = try { response.body?.string()?.take(500) } catch (_: Exception) { null }
                throw IOException("Unexpected response code: ${response.code} for bld=$bld, params=${params.keys}, body=$errorBody")
            }

            // Check Content-Length header for early rejection of oversized responses
            val contentLength = response.body?.contentLength() ?: -1L
            if (contentLength > MAX_RESPONSE_SIZE_BYTES) {
                throw IOException("Response too large: $contentLength bytes exceeds limit of $MAX_RESPONSE_SIZE_BYTES bytes")
            }

            val body = response.body?.string()
                ?: throw IOException("Empty response body")

            // Post-read size check (Content-Length may be absent with chunked encoding)
            if (body.length > MAX_RESPONSE_SIZE_BYTES) {
                throw IOException("Response too large: ${body.length} bytes exceeds limit of $MAX_RESPONSE_SIZE_BYTES bytes")
            }

            // KRX API returns "LOGOUT" when session is invalid
            if (body.trim() == "LOGOUT") {
                throw IOException("KRX session expired or access denied. Try from Korean network or use VPN.")
            }

            return body
        }
    }

    /**
     * 리소스 정리 (OkHttpClient 연결 풀)
     */
    fun close() {
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
    }
}
