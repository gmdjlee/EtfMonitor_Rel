package com.krxkt.integration

import kotlinx.coroutines.runBlocking
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * HTTPS 및 OTP 파라미터 테스트
 */
fun main() = runBlocking {
    println("=".repeat(60))
    println("KRX API HTTPS + OTP 테스트")
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
        // Step 1: 메인 페이지 방문 (HTTPS)
        println("\n[Step 1] HTTPS 메인 페이지 방문...")
        val initRequest = Request.Builder()
            .url("https://data.krx.co.kr/contents/MDC/MDI/mdiLoader/index.cmd?menuId=MDC0201020101")
            .get()
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        val initResponse = client.newCall(initRequest).execute()
        println("초기화 응답: ${initResponse.code}")
        initResponse.close()

        // Step 2: Generate OTP
        println("\n[Step 2] OTP 생성...")
        val otpParams = mapOf(
            "bld" to "dbms/comm/finder/finder_stkisu",
            "name" to "form",
            "mktsel" to "ALL"
        )

        val otpBody = FormBody.Builder().apply {
            otpParams.forEach { (k, v) -> add(k, v) }
        }.build()

        val otpRequest = Request.Builder()
            .url("https://data.krx.co.kr/comm/bldAttendant/getJsonData.cmd")
            .post(otpBody)
            .addHeader("Referer", "https://data.krx.co.kr/contents/MDC/MDI/mdiLoader/index.cmd?menuId=MDC0201020101")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .build()

        val otpResponse = client.newCall(otpRequest).execute()
        val otpBody2 = otpResponse.body?.string() ?: ""
        println("OTP 응답 코드: ${otpResponse.code}")
        println("OTP 응답 (처음 200자): ${otpBody2.take(200)}")
        otpResponse.close()

        // Step 3: HTTPS API 호출
        println("\n[Step 3] HTTPS API 호출...")
        val params = mapOf(
            "bld" to "dbms/MDC/STAT/standard/MDCSTAT01501",
            "mktId" to "ALL",
            "trdDd" to "20210122"
        )

        val formBody = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
        }.build()

        val apiRequest = Request.Builder()
            .url("https://data.krx.co.kr/comm/bldAttendant/getJsonData.cmd")
            .post(formBody)
            .addHeader("Referer", "https://data.krx.co.kr/contents/MDC/MDI/mdiLoader/index.cmd?menuId=MDC0201020101")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .build()

        val apiResponse = client.newCall(apiRequest).execute()
        println("API 응답 코드: ${apiResponse.code}")

        val body = apiResponse.body?.string() ?: "(empty)"

        if (body.trim() == "LOGOUT") {
            println("\n⚠️ 여전히 LOGOUT 응답")
        } else if (body.contains("OutBlock_1")) {
            println("\n✅ API 호출 성공!")
            println("응답 (처음 500자): ${body.take(500)}")
        } else {
            println("\n응답 본문: ${body.take(500)}")
        }

    } catch (e: Exception) {
        println("\n오류: ${e.message}")
        e.printStackTrace()
    } finally {
        client.dispatcher.executorService.shutdown()
    }
}
