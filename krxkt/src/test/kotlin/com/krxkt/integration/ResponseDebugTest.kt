package com.krxkt.integration

import com.krxkt.api.KrxClient
import com.krxkt.api.KrxEndpoints
import kotlinx.coroutines.runBlocking

/**
 * 실제 응답 내용 디버깅
 */
fun main() = runBlocking {
    println("=".repeat(60))
    println("KRX API 응답 디버깅")
    println("=".repeat(60))

    val client = KrxClient()

    try {
        // ETF 티커 리스트 요청
        val params = mapOf(
            "bld" to KrxEndpoints.Bld.ETF_TICKER_LIST,
            "trdDd" to "20210122"
        )

        println("\n요청 파라미터: $params")
        println("Referer: ${KrxEndpoints.REFERER}")

        val response = client.post(params)

        println("\n응답 길이: ${response.length}")
        println("응답 (처음 500자):")
        println(response.take(500))

        // JSON 파싱 테스트
        println("\n\nJSON 파싱 테스트:")
        val root = com.google.gson.JsonParser.parseString(response).asJsonObject
        val output = root.getAsJsonArray("output")
        println("output 배열 크기: ${output?.size() ?: 0}")

        if (output != null && output.size() > 0) {
            println("\n첫 번째 항목:")
            println(output[0])
        }

    } catch (e: Exception) {
        println("\n오류: ${e.message}")
        e.printStackTrace()
    } finally {
        client.close()
    }
}
