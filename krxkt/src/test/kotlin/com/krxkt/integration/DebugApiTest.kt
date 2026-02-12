package com.krxkt.integration

import kotlinx.coroutines.runBlocking
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * KRX API 디버그 테스트
 * 원시 HTTP 요청/응답 확인
 */
fun main() = runBlocking {
    println("=".repeat(60))
    println("KRX API 디버그 테스트")
    println("=".repeat(60))

    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    try {
        // STOCK_OHLCV_ALL 테스트 (전종목 시세)
        val params = mapOf(
            "bld" to "dbms/MDC/STAT/standard/MDCSTAT01501",
            "mktId" to "ALL",
            "trdDd" to "20210122"
        )

        println("\n[요청 정보]")
        println("URL: http://data.krx.co.kr/comm/bldAttendant/getJsonData.cmd")
        println("파라미터: $params")

        val formBody = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
        }.build()

        val request = Request.Builder()
            .url("http://data.krx.co.kr/comm/bldAttendant/getJsonData.cmd")
            .post(formBody)
            .addHeader("Referer", "http://data.krx.co.kr/contents/MDC/MDI/mdiLoader/index.cmd?menuId=MDC0201020101")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .build()

        println("\n[요청 헤더]")
        request.headers.forEach { println("${it.first}: ${it.second}") }

        val response = client.newCall(request).execute()

        println("\n[응답 정보]")
        println("응답 코드: ${response.code}")
        println("응답 메시지: ${response.message}")

        println("\n[응답 헤더]")
        response.headers.forEach { println("${it.first}: ${it.second}") }

        val body = response.body?.string() ?: "(empty)"
        println("\n[응답 본문 (처음 500자)]")
        println(body.take(500))

    } catch (e: Exception) {
        println("\n오류: ${e.message}")
        e.printStackTrace()
    } finally {
        client.dispatcher.executorService.shutdown()
    }
}
