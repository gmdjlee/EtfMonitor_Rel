package com.krxkt.integration

import kotlinx.coroutines.runBlocking
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * 다양한 User-Agent 및 헤더 조합 테스트
 */
fun main() = runBlocking {
    println("=".repeat(60))
    println("KRX API 헤더 조합 테스트")
    println("=".repeat(60))

    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "python-requests/2.28.0"  // pykrx 기본 User-Agent
    )

    val otpUrl = "http://data.krx.co.kr/comm/fileDn/GenerateOTP/generate.cmd"
    val otpParams = mapOf(
        "mktId" to "ALL",
        "trdDd" to "20210122",
        "money" to "1",
        "csvxls_isNo" to "false",
        "name" to "fileDown",
        "url" to "dbms/MDC/STAT/standard/MDCSTAT01501"
    )

    try {
        for ((idx, ua) in userAgents.withIndex()) {
            println("\n[테스트 ${idx + 1}] User-Agent: ${ua.take(50)}...")

            val otpBody = FormBody.Builder().apply {
                otpParams.forEach { (k, v) -> add(k, v) }
            }.build()

            val request = Request.Builder()
                .url(otpUrl)
                .post(otpBody)
                .addHeader("Referer", "http://data.krx.co.kr/contents/MDC/MDI/mdiLoader")
                .addHeader("User-Agent", ua)
                .addHeader("Accept", "*/*")
                .addHeader("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .addHeader("Accept-Encoding", "gzip, deflate")
                .addHeader("Connection", "keep-alive")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()

            println("응답 코드: ${response.code}")
            println("응답: ${body.take(100)}")

            if (body != "LOGOUT" && body.isNotBlank()) {
                println("✅ 성공!")
                break
            }
        }

        // curl 스타일 테스트
        println("\n[curl 스타일 테스트]")
        println("수동으로 다음 curl 명령을 실행해보세요:")
        println("""
curl -X POST "http://data.krx.co.kr/comm/fileDn/GenerateOTP/generate.cmd" \
  -H "Referer: http://data.krx.co.kr/contents/MDC/MDI/mdiLoader" \
  -d "mktId=ALL&trdDd=20210122&money=1&csvxls_isNo=false&name=fileDown&url=dbms/MDC/STAT/standard/MDCSTAT01501"
        """.trimIndent())

    } catch (e: Exception) {
        println("\n오류: ${e.message}")
        e.printStackTrace()
    } finally {
        client.dispatcher.executorService.shutdown()
    }
}
