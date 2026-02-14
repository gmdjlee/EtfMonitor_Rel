package com.etfmonitor.krx

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
// PyKrxClient removed - migrated to kotlin_krx (GetKrxBusinessDaysUseCase)
import com.krxkt.KrxEtf
import com.krxkt.KrxIndex
import com.krxkt.KrxStock
import com.krxkt.api.KrxClient
import com.krxkt.model.Market
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * KRX API 및 kotlin_krx 기능 테스트
 *
 * 테스트 항목:
 * 1. Fear & Greed Index (feargreed.py) - KRX API 직접 호출
 * 2. kotlin_krx ETF 기능 - KrxEtf
 * 3. kotlin_krx 지수 기능 - KrxIndex
 * 4. kotlin_krx 주식 기능 - KrxStock
 *
 * 주의: 이 테스트는 실제 KRX API를 호출하므로 인터넷 연결 필요
 */
@RunWith(AndroidJUnit4::class)
class KrxApiFunctionalityTest {

    private lateinit var python: Python
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        python = Python.getInstance()
    }

    /**
     * Test 1: Fear & Greed Index - KRX API 직접 호출
     */
    @Test
    fun test_feargreed_krx_api() = runBlocking {
        println("\n========================================")
        println("TEST 1: Fear & Greed Index (KRX API)")
        println("========================================")

        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(30)
        val startStr = startDate.format(formatter)
        val endStr = endDate.format(formatter)

        println("Test period: $startStr ~ $endStr")

        try {
            val module = python.getModule("feargreed")
            assertNotNull(module, "feargreed module should be loaded")
            println("✓ Module loaded successfully")

            // Test 1.1: Data collection (combine)
            println("\n--- Test 1.1: Data Collection (combine) ---")
            val combineFunc = module["combine"]
            assertNotNull(combineFunc, "combine function should exist")

            val combinedData = withTimeout(90_000L) {
                combineFunc.call(startStr, endStr)
            }

            if (combinedData != null && combinedData.toString() != "None") {
                println("✓ Combined data retrieved successfully")

                // Test 1.2: Fear & Greed calculation (analyze)
                println("\n--- Test 1.2: Fear & Greed Calculation (analyze) ---")
                val analyzeFunc = module["analyze"]
                assertNotNull(analyzeFunc, "analyze function should exist")

                val result = withTimeout(90_000L) {
                    analyzeFunc.call(combinedData)
                }

                assertNotNull(result, "Analysis result should not be null")

                val resultList = result.asList()
                assertNotNull(resultList, "Result should be convertible to list")
                assertTrue(resultList.size >= 2, "Result should have 2 elements (KOSPI, KOSDAQ)")

                // Check KOSPI result
                val kospiDf = resultList.getOrNull(0)
                if (kospiDf != null && kospiDf.toString() != "None") {
                    val kospiRecords = kospiDf["to_dict"]?.call("records")?.asList()
                    val kospiCount = kospiRecords?.size ?: 0
                    println("✓ KOSPI Fear & Greed: $kospiCount records")
                    assertTrue(kospiCount > 0, "KOSPI should have data")
                } else {
                    println("⚠ KOSPI data is None")
                }

                // Check KOSDAQ result
                val kosdaqDf = resultList.getOrNull(1)
                if (kosdaqDf != null && kosdaqDf.toString() != "None") {
                    val kosdaqRecords = kosdaqDf["to_dict"]?.call("records")?.asList()
                    val kosdaqCount = kosdaqRecords?.size ?: 0
                    println("✓ KOSDAQ Fear & Greed: $kosdaqCount records")
                    assertTrue(kosdaqCount > 0, "KOSDAQ should have data")
                } else {
                    println("⚠ KOSDAQ data is None")
                }

                println("✓ Fear & Greed Index calculation successful")
            } else {
                println("⚠ No combined data returned (may be non-business day or KRX API issue)")
            }

        } catch (e: Exception) {
            println("✗ Fear & Greed test failed: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Test 2: kotlin_krx ETF 기능
     */
    @Test
    fun test_kotlin_krx_etf() = runBlocking {
        println("\n========================================")
        println("TEST 2: kotlin_krx ETF Features")
        println("========================================")

        val krxEtf = KrxEtf(KrxClient())
        val date = LocalDate.now().minusDays(1).format(formatter)  // Use previous day to ensure data exists

        try {
            // Test 2.1: ETF List
            println("\n--- Test 2.1: ETF List ---")
            val etfList = withTimeout(30_000L) {
                krxEtf.getEtfList(date)
            }

            assertNotNull(etfList, "ETF list should not be null")
            assertTrue(etfList.isNotEmpty(), "ETF list should not be empty")
            println("✓ ETF list retrieved: ${etfList.size} ETFs")
            println("  Sample: ${etfList.take(3).map { it.ticker to it.name }}")

            // Test 2.2: ETF Portfolio (use first ETF)
            println("\n--- Test 2.2: ETF Portfolio ---")
            val sampleEtf = etfList.firstOrNull()
            if (sampleEtf != null) {
                val portfolio = withTimeout(30_000L) {
                    krxEtf.getPortfolio(date, sampleEtf.ticker)
                }

                assertNotNull(portfolio, "Portfolio should not be null")
                println("✓ Portfolio retrieved for ${sampleEtf.name} (${sampleEtf.ticker})")
                println("  Holdings: ${portfolio.holdings.size} stocks")
                if (portfolio.holdings.isNotEmpty()) {
                    println("  Top 3 holdings:")
                    portfolio.holdings.take(3).forEach { holding ->
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
     * Test 3: kotlin_krx 지수 기능
     */
    @Test
    fun test_kotlin_krx_index() = runBlocking {
        println("\n========================================")
        println("TEST 3: kotlin_krx Index Features")
        println("========================================")

        val krxIndex = KrxIndex(KrxClient())
        val endDate = LocalDate.now().minusDays(1)
        val startDate = endDate.minusDays(7)
        val startStr = startDate.format(formatter)
        val endStr = endDate.format(formatter)

        println("Test period: $startStr ~ $endStr")

        try {
            // Test 3.1: KOSPI Index
            println("\n--- Test 3.1: KOSPI Index ---")
            val kospiData = withTimeout(30_000L) {
                krxIndex.getKospi(startStr, endStr)
            }

            assertNotNull(kospiData, "KOSPI data should not be null")
            assertTrue(kospiData.isNotEmpty(), "KOSPI data should not be empty")
            println("✓ KOSPI data retrieved: ${kospiData.size} records")
            println("  Latest: ${kospiData.lastOrNull()?.let { "${it.date} - Close: ${it.close}" }}")

            // Test 3.2: KOSDAQ Index
            println("\n--- Test 3.2: KOSDAQ Index ---")
            val kosdaqData = withTimeout(30_000L) {
                krxIndex.getKosdaq(startStr, endStr)
            }

            assertNotNull(kosdaqData, "KOSDAQ data should not be null")
            assertTrue(kosdaqData.isNotEmpty(), "KOSDAQ data should not be empty")
            println("✓ KOSDAQ data retrieved: ${kosdaqData.size} records")
            println("  Latest: ${kosdaqData.lastOrNull()?.let { "${it.date} - Close: ${it.close}" }}")

            // Test 3.3: Index List
            println("\n--- Test 3.3: Index List ---")
            val indexList = withTimeout(30_000L) {
                krxIndex.getIndexList(endStr)
            }

            assertNotNull(indexList, "Index list should not be null")
            assertTrue(indexList.isNotEmpty(), "Index list should not be empty")
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
     * Test 4: kotlin_krx 주식 기능
     */
    @Test
    fun test_kotlin_krx_stock() = runBlocking {
        println("\n========================================")
        println("TEST 4: kotlin_krx Stock Features")
        println("========================================")

        val krxStock = KrxStock(KrxClient())
        val date = LocalDate.now().minusDays(1).format(formatter)
        val ticker = "005930"  // Samsung Electronics

        println("Test ticker: $ticker (Samsung Electronics)")
        println("Test date: $date")

        try {
            // Test 4.1: Market Cap
            println("\n--- Test 4.1: Market Cap ---")
            val marketCap = withTimeout(30_000L) {
                krxStock.getMarketCap(date, Market.ALL, ticker)
            }

            assertNotNull(marketCap, "Market cap should not be null")
            assertTrue(marketCap.isNotEmpty(), "Market cap should not be empty")
            println("✓ Market cap retrieved: ${marketCap.size} records")
            marketCap.firstOrNull()?.let { cap ->
                println("  ${cap.name} (${cap.ticker}): ${cap.marketCap} 원")
            }

            // Test 4.2: Stock OHLCV
            println("\n--- Test 4.2: Stock OHLCV ---")
            val endDate = LocalDate.now().minusDays(1)
            val startDate = endDate.minusDays(7)
            val startStr = startDate.format(formatter)
            val endStr = endDate.format(formatter)

            val ohlcv = withTimeout(30_000L) {
                krxStock.getOhlcvByTicker(startStr, endStr, ticker)
            }

            assertNotNull(ohlcv, "OHLCV data should not be null")
            assertTrue(ohlcv.isNotEmpty(), "OHLCV data should not be empty")
            println("✓ OHLCV data retrieved: ${ohlcv.size} records")
            ohlcv.lastOrNull()?.let { data ->
                println("  Latest (${data.date}): Close=${data.close}, Volume=${data.volume}")
            }

            // Test 4.3: Ticker List
            println("\n--- Test 4.3: Ticker List ---")
            val tickerList = withTimeout(30_000L) {
                krxStock.getTickerList(date, Market.KOSPI)
            }

            assertNotNull(tickerList, "Ticker list should not be null")
            assertTrue(tickerList.isNotEmpty(), "Ticker list should not be empty")
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

    /**
     * Test 5: PyKrxClient - REMOVED
     *
     * PyKrxClient has been fully migrated to kotlin_krx.
     * Business days functionality now provided by GetKrxBusinessDaysUseCase.
     *
     * Migration: pykrx → kotlin_krx (Phase A complete)
     */
    // @Test - Removed (PyKrxClient deleted)
}
