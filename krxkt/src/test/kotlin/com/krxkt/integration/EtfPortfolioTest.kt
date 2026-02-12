package com.krxkt.integration

import com.krxkt.KrxEtf
import kotlinx.coroutines.runBlocking

/**
 * ETF 구성종목 테스트https://github.com/sharebook-kr/pykrx
 *
 * 471040 ETF의 구성종목 정보를 출력합니다.
 */
fun main() = runBlocking {
    val krxEtf = KrxEtf()

    try {
        val ticker = "069500" // KODEX 200
        val date = "20210122" // 테스트 기준일

        println("=" .repeat(80))
        println("ETF 구성종목 조회: $ticker")
        println("조회일: $date")
        println("=" .repeat(80))

        // 먼저 ETF 정보 확인
        val etfInfo = krxEtf.getEtfTickerList(date).find { it.ticker == ticker }
        if (etfInfo != null) {
            println("\n[ETF 기본 정보]")
            println("티커: ${etfInfo.ticker}")
            println("종목명: ${etfInfo.name}")
            println("ISIN: ${etfInfo.isinCode}")
            println("기초지수: ${etfInfo.indexName ?: "N/A"}")
            println("총보수: ${etfInfo.totalFee?.let { "${it}%" } ?: "N/A"}")
        } else {
            println("ETF 정보를 찾을 수 없습니다: $ticker")
            return@runBlocking
        }

        // 구성종목 조회
        println("\n[구성종목 목록]")
        println("-".repeat(80))
        println(String.format("%-8s %-20s %15s %15s %10s",
            "종목코드", "종목명", "주식수", "평가금액", "비중(%)"))
        println("-".repeat(80))

        val portfolio = krxEtf.getPortfolio(date, ticker)

        if (portfolio.isEmpty()) {
            println("구성종목 데이터가 없습니다.")
        } else {
            portfolio.sortedByDescending { it.weight ?: 0.0 }.forEach { item ->
                println(String.format("%-8s %-20s %,15d %,15d %10.2f",
                    item.ticker,
                    item.name.take(18),
                    item.shares,
                    item.valuationAmount,
                    item.weight ?: 0.0
                ))
            }

            println("-".repeat(80))
            println("총 ${portfolio.size}개 종목")
            println("총 비중: ${portfolio.mapNotNull { it.weight }.sum()}%")
        }

    } catch (e: Exception) {
        println("오류 발생: ${e.message}")
        e.printStackTrace()
    } finally {
        krxEtf.close()
    }
}
