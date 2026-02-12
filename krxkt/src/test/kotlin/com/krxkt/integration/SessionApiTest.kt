package com.krxkt.integration

import kotlinx.coroutines.runBlocking
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * KRX API 세션 테스트
 * 메인 페이지 방문 후 세션 쿠키로 API 호출
 */
fun main() = runBlocking {
    println("=".repeat(60))
    println("KRX API 세션 테스트")
    println("=".repeat(60))

    // 쿠키 저장소가 있는 클라이언트
    val cookieJar = object : CookieJar {
        private val cookies = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies.addAll(cookies)
            println("[Cookie 저장] ${cookies.map { "${it.name}=${it.value.take(20)}..." }}")
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookies
        }
    }

    val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    try {
        // Step 1: 메인 페이지 방문 (세션 초기화)
        println("\n[Step 1] 메인 페이지 방문...")
        val initRequest = Request.Builder()
            .url("http://data.krx.co.kr/contents/MDC/MDI/mdiLoader/index.cmd?menuId=MDC0201020101")
            .get()
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        val initResponse = client.newCall(initRequest).execute()
        println("초기화 응답: ${initResponse.code}")
        initResponse.close()

        // Step 2: API 호출
        println("\n[Step 2] API 호출...")
        val params = mapOf(
            "bld" to "dbms/MDC/STAT/standard/MDCSTAT01501",
            "mktId" to "ALL",
            "trdDd" to "20210122"
        )

        val formBody = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
        }.build()

        val apiRequest = Request.Builder()
            .url("http://data.krx.co.kr/comm/bldAttendant/getJsonData.cmd")
            .post(formBody)
            .addHeader("Referer", "http://data.krx.co.kr/contents/MDC/MDI/mdiLoader/index.cmd?menuId=MDC0201020101")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .build()

        val apiResponse = client.newCall(apiRequest).execute()
        println("API 응답 코드: ${apiResponse.code}")

        val body = apiResponse.body?.string() ?: "(empty)"
        println("\n[응답 본문 (처음 1000자)]")
        println(body.take(1000))

        if (body.trim() == "LOGOUT") {
            println("\n⚠️ 세션이 여전히 유효하지 않습니다.")
            println("KRX API는 한국 내 네트워크 또는 VPN이 필요할 수 있습니다.")
        } else if (body.contains("OutBlock_1")) {
            println("\n✅ API 호출 성공!")
        }

    } catch (e: Exception) {
        println("\n오류: ${e.message}")
        e.printStackTrace()
    } finally {
        client.dispatcher.executorService.shutdown()
    }
}
