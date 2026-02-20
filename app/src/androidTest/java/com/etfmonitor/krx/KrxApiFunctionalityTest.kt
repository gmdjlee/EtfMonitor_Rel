package com.etfmonitor.krx

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.krxkt.KrxEtf
import com.krxkt.KrxIndex
import com.krxkt.KrxStock
import com.krxkt.api.KrxClient
import com.krxkt.model.Market
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * KRX API 및 kotlin_krx 기능 테스트
 *
 * 테스트 항목:
 * 1. kotlin_krx ETF 기능 - KrxEtf
 * 2. kotlin_krx 지수 기능 - KrxIndex
 * 3. kotlin_krx 주식 기능 - KrxStock
 *
 * 주의: 이 테스트는 실제 KRX API를 호출하므로 인터넷 연결 필요
 *
 * Note: Fear & Greed (feargreed.py) and Blood Indicator (blood_indicator.py) tests
 * removed — both migrated to native Kotlin (FearGreedCalculator, BloodIndicatorCalculator).
 */
@RunWith(AndroidJUnit4::class)
class KrxApiFunctionalityTest {

    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * Test 1: kotlin_krx ETF 기능
     */
    @Test
    fun test_kotlin_krx_etf() = runBlocking {
        println("\n========================================")
        println("TEST 1: kotlin_krx ETF Features")
        println("========================================")

        val krxEtf = KrxEtf(KrxClient())
        val date = LocalDate.now().minusDays(1).format(formatter)

        try {
            // Test 1.1: ETF List
            println("\n--- Test 1.1: ETF List ---")
            val etfList = withTimeout(30_000L) {
                krxEtf.getEtfTickerList(date)
            }

            assertNotNull("ETF list should not be null", etfList)
            assertTrue("ETF list should not be empty", etfList.isNotEmpty())
            println("✓ ETF list retrieved: ${etfList.size} ETFs")
            println("  Sample: ${etfList.take(3).map { it.ticker to it.name }}")

            // Test 1.2: ETF Portfolio (use first ETF)
            println("\n--- Test 1.2: ETF Portfolio ---")
            val sampleEtf = etfList.firstOrNull()
            if (sampleEtf != null) {
                val portfolio = withTimeout(30_000L) {
                    krxEtf.getPortfolio(date, sampleEtf.ticker)
                }

                assertNotNull("Portfolio should not be null", portfolio)
                println("✓ Portfolio retrieved for ${sampleEtf.name} (${sampleEtf.ticker})")
                println("  Holdings: ${portfolio.size} stocks")
                if (portfolio.isNotEmpty()) {
                    println("  Top 3 holdings:")
                    portfolio.take(3).forEach { holding ->
                        println("    - ${holding.name}: ${holding.weight}%")
                    }
                }
            }

            println("✓ kotlin_krx ETF features test passed")

        } catch (e: Exception) {
            println("✗ kotlin_krx ETF test failed: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            krxEtf.close()
        }
    }

    /**
     * Test 2: kotlin_krx 지수 기능
     */
    @Test
    fun test_kotlin_krx_index() = runBlocking {
        println("\n========================================")
        println("TEST 2: kotlin_krx Index Features")
        println("========================================")

        val krxIndex = KrxIndex(KrxClient())
        val endDate = LocalDate.now().minusDays(1)
        val startDate = endDate.minusDays(7)
        val startStr = startDate.format(formatter)
        val endStr = endDate.format(formatter)

        println("Test period: $startStr ~ $endStr")

        try {
            // Test 2.1: KOSPI Index
            println("\n--- Test 2.1: KOSPI Index ---")
            val kospiData = withTimeout(30_000L) {
                krxIndex.getKospi(startStr, endStr)
            }

            assertNotNull("KOSPI data should not be null", kospiData)
            assertTrue("KOSPI data should not be empty", kospiData.isNotEmpty())
            println("✓ KOSPI data retrieved: ${kospiData.size} records")
            println("  Latest: ${kospiData.lastOrNull()?.let { "${it.date} - Close: ${it.close}" }}")

            // Test 2.2: KOSDAQ Index
            println("\n--- Test 2.2: KOSDAQ Index ---")
            val kosdaqData = withTimeout(30_000L) {
                krxIndex.getKosdaq(startStr, endStr)
            }

            assertNotNull("KOSDAQ data should not be null", kosdaqData)
            assertTrue("KOSDAQ data should not be empty", kosdaqData.isNotEmpty())
            println("✓ KOSDAQ data retrieved: ${kosdaqData.size} records")
            println("  Latest: ${kosdaqData.lastOrNull()?.let { "${it.date} - Close: ${it.close}" }}")

            // Test 2.3: Index List
            println("\n--- Test 2.3: Index List ---")
            val indexList = withTimeout(30_000L) {
                krxIndex.getIndexList(endStr)
            }

            assertNotNull("Index list should not be null", indexList)
            assertTrue("Index list should not be empty", indexList.isNotEmpty())
            println("✓ Index list retrieved: ${indexList.size} indices")
            println("  Sample: ${indexList.take(5).map { it.ticker to it.name }}")

            println("✓ kotlin_krx Index features test passed")

        } catch (e: Exception) {
            println("✗ kotlin_krx Index test failed: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            krxIndex.close()
        }
    }

    /**
     * Test 3: kotlin_krx 주식 기능
     */
    @Test
    fun test_kotlin_krx_stock() = runBlocking {
        println("\n========================================")
        println("TEST 3: kotlin_krx Stock Features")
        println("========================================")

        val krxStock = KrxStock(KrxClient())
        val date = LocalDate.now().minusDays(1).format(formatter)
        val ticker = "005930"  // Samsung Electronics

        println("Test ticker: $ticker (Samsung Electronics)")
        println("Test date: $date")

        try {
            // Test 3.1: Market Cap
            println("\n--- Test 3.1: Market Cap ---")
            val marketCap = withTimeout(30_000L) {
                krxStock.getMarketCap(date, Market.ALL)
            }

            assertNotNull("Market cap should not be null", marketCap)
            assertTrue("Market cap should not be empty", marketCap.isNotEmpty())
            println("✓ Market cap retrieved: ${marketCap.size} records")
            marketCap.firstOrNull()?.let { cap ->
                println("  ${cap.name} (${cap.ticker}): ${cap.marketCap} 원")
            }

            // Test 3.2: Stock OHLCV
            println("\n--- Test 3.2: Stock OHLCV ---")
            val endDate = LocalDate.now().minusDays(1)
            val startDate = endDate.minusDays(7)
            val startStr = startDate.format(formatter)
            val endStr = endDate.format(formatter)

            val ohlcv = withTimeout(30_000L) {
                krxStock.getOhlcvByTicker(startStr, endStr, ticker)
            }

            assertNotNull("OHLCV data should not be null", ohlcv)
            assertTrue("OHLCV data should not be empty", ohlcv.isNotEmpty())
            println("✓ OHLCV data retrieved: ${ohlcv.size} records")
            ohlcv.lastOrNull()?.let { data ->
                println("  Latest (${data.date}): Close=${data.close}, Volume=${data.volume}")
            }

            // Test 3.3: Ticker List
            println("\n--- Test 3.3: Ticker List ---")
            val tickerList = withTimeout(30_000L) {
                krxStock.getTickerList(date, Market.KOSPI)
            }

            assertNotNull("Ticker list should not be null", tickerList)
            assertTrue("Ticker list should not be empty", tickerList.isNotEmpty())
            println("✓ KOSPI ticker list retrieved: ${tickerList.size} stocks")
            println("  Sample: ${tickerList.take(5)}")

            println("✓ kotlin_krx Stock features test passed")

        } catch (e: Exception) {
            println("✗ kotlin_krx Stock test failed: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            krxStock.close()
        }
    }
}
