package com.krxkt.integration

import kotlinx.coroutines.runBlocking
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * pykrx 스타일 전체 파라미터 테스트
 */
fun main() = runBlocking {
    println("=".repeat(60))
    println("KRX API 전체 파라미터 테스트")
    println("=".repeat(60))

    val cookieJar = object : CookieJar {
        private val cookies = mutableListOf<Cookie>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies.addAll(cookies)
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies
    }

    val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    try {
        // Step 1: 메인 페이지 방문
        println("\n[Step 1] 메인 페이지 방문...")
        val initRequest = Request.Builder()
            .url("https://data.krx.co.kr/contents/MDC/MDI/mdiLoader/index.cmd?menuId=MDC0201020101")
            .get()
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        client.newCall(initRequest).execute().close()
        println("완료")

        // Step 2: pykrx 스타일 전체 파라미터로 API 호출
        println("\n[Step 2] 전체 파라미터로 API 호출...")

        // pykrx가 사용하는 모든 파라미터 포함
        val params = mapOf(
            "bld" to "dbms/MDC/STAT/standard/MDCSTAT01501",
            "locale" to "ko_KR",
            "mktId" to "ALL",
            "trdDd" to "20210122",
            "share" to "1",
            "money" to "1",
            "csvxls_is498No" to "false"
        )

        println("파라미터: $params")

        val formBody = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
        }.build()

        val apiRequest = Request.Builder()
            .url("https://data.krx.co.kr/comm/bldAttendant/getJsonData.cmd")
            .post(formBody)
            .addHeader("Referer", "https://data.krx.co.kr/contents/MDC/MDI/mdiLoader/index.cmd?menuId=MDC0201020101")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .build()

        val response = client.newCall(apiRequest).execute()
        val body = response.body?.string() ?: ""

        println("응답 코드: ${response.code}")

        when {
            body.trim() == "LOGOUT" -> {
                println("\n❌ LOGOUT 응답")

                // 대체 시도: 다른 엔드포인트
                println("\n[Step 3] 대체 엔드포인트 시도 (ETF)...")
                val etfParams = mapOf(
                    "bld" to "dbms/MDC/STAT/standard/MDCSTAT04301",
                    "locale" to "ko_KR",
                    "trdDd" to "20210122",
                    "share" to "1",
                    "money" to "1",
                    "csvxls_isNo" to "false"
                )

                val etfBody = FormBody.Builder().apply {
                    etfParams.forEach { (k, v) -> add(k, v) }
                }.build()

                val etfRequest = Request.Builder()
                    .url("https://data.krx.co.kr/comm/bldAttendant/getJsonData.cmd")
                    .post(etfBody)
                    .addHeader("Referer", "https://data.krx.co.kr/contents/MDC/MDI/mdiLoader/index.cmd?menuId=MDC0201020101")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .addHeader("Accept", "application/json")
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .build()

                val etfResponse = client.newCall(etfRequest).execute()
                val etfBodyStr = etfResponse.body?.string() ?: ""
                println("ETF 응답 코드: ${etfResponse.code}")
                println("ETF 응답: ${etfBodyStr.take(300)}")
            }
            body.contains("OutBlock_1") -> {
                println("\n✅ 성공!")
                println("응답 (처음 500자): ${body.take(500)}")
            }
            else -> {
                println("\n응답: ${body.take(500)}")
            }
        }

    } catch (e: Exception) {
        println("\n오류: ${e.message}")
        e.printStackTrace()
    } finally {
        client.dispatcher.executorService.shutdown()
    }
}
