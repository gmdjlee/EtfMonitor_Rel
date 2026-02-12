package com.krxkt.integration

import com.krxkt.KrxEtf
import com.krxkt.KrxStock
import kotlinx.coroutines.runBlocking

/**
 * 간단한 API 접근 테스트
 */
fun main() = runBlocking {
    println("=".repeat(60))
    println("KRX API 접근 테스트")
    println("=".repeat(60))

    val krxStock = KrxStock()
    val krxEtf = KrxEtf()

    try {
        // 1. Stock API 테스트
        println("\n[1] Stock 티커 리스트 조회...")
        val stockTickers = krxStock.getTickerList("20210122")
        println("Stock 종목 수: ${stockTickers.size}")
        if (stockTickers.isNotEmpty()) {
            println("첫 번째 종목: ${stockTickers.first()}")
        }

        // 2. ETF API 테스트
        println("\n[2] ETF 티커 리스트 조회...")
        val etfTickers = krxEtf.getEtfTickerList("20210122")
        println("ETF 종목 수: ${etfTickers.size}")
        if (etfTickers.isNotEmpty()) {
            val kodex200 = etfTickers.find { it.ticker == "069500" }
            if (kodex200 != null) {
                println("KODEX 200 정보:")
                println("  티커: ${kodex200.ticker}")
                println("  이름: ${kodex200.name}")
                println("  ISIN: ${kodex200.isinCode}")
            }
        }

        println("\n" + "=".repeat(60))
        println("테스트 완료!")

    } catch (e: Exception) {
        println("\n오류 발생: ${e.message}")
        e.printStackTrace()
    } finally {
        krxStock.close()
        krxEtf.close()
    }
}
