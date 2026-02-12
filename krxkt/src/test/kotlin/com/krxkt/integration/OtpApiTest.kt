package com.krxkt.integration

import kotlinx.coroutines.runBlocking
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * KRX OTP 인증 방식 테스트
 */
fun main() = runBlocking {
    println("=".repeat(60))
    println("KRX OTP 인증 테스트")
    println("=".repeat(60))

    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    try {
        // Step 1: OTP 생성
        println("\n[Step 1] OTP 생성...")
        val otpUrl = "http://data.krx.co.kr/comm/fileDn/GenerateOTP/generate.cmd"

        val otpParams = mapOf(
            "mktId" to "ALL",
            "trdDd" to "20210122",
            "money" to "1",
            "csvxls_isNo" to "false",
            "name" to "fileDown",
            "url" to "dbms/MDC/STAT/standard/MDCSTAT01501"
        )

        println("OTP 파라미터: $otpParams")

        val otpBody = FormBody.Builder().apply {
            otpParams.forEach { (k, v) -> add(k, v) }
        }.build()

        val otpRequest = Request.Builder()
            .url(otpUrl)
            .post(otpBody)
            .addHeader("Referer", "http://data.krx.co.kr/contents/MDC/MDI/mdiLoader")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val otpResponse = client.newCall(otpRequest).execute()
        val otpCode = otpResponse.body?.string() ?: ""
        otpResponse.close()

        println("OTP 응답 코드: ${otpResponse.code}")
        println("OTP 코드: ${otpCode.take(50)}...")

        if (otpCode.isBlank() || otpCode == "LOGOUT") {
            println("\n❌ OTP 생성 실패")
            return@runBlocking
        }

        // Step 2: OTP로 데이터 요청
        println("\n[Step 2] OTP로 데이터 요청...")
        val downloadUrl = "http://data.krx.co.kr/comm/fileDn/download_csv/download.cmd"

        val downloadBody = FormBody.Builder()
            .add("code", otpCode)
            .build()

        val downloadRequest = Request.Builder()
            .url(downloadUrl)
            .post(downloadBody)
            .addHeader("Referer", "http://data.krx.co.kr/contents/MDC/MDI/mdiLoader")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val downloadResponse = client.newCall(downloadRequest).execute()
        val data = downloadResponse.body?.string() ?: ""
        downloadResponse.close()

        println("다운로드 응답 코드: ${downloadResponse.code}")
        println("Content-Type: ${downloadResponse.header("Content-Type")}")

        if (data.isNotBlank() && data != "LOGOUT") {
            println("\n✅ 데이터 수신 성공!")
            println("데이터 크기: ${data.length} bytes")
            println("\n데이터 (처음 1000자):")
            println(data.take(1000))
        } else {
            println("\n❌ 데이터 수신 실패: $data")
        }

    } catch (e: Exception) {
        println("\n오류: ${e.message}")
        e.printStackTrace()
    } finally {
        client.dispatcher.executorService.shutdown()
    }
}
