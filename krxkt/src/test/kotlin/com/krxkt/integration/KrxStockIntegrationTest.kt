package com.krxkt.integration

import com.krxkt.KrxStock
import com.krxkt.error.KrxError
import com.krxkt.model.Market
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Tag
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * KRX API 통합 테스트
 *
 * 실제 KRX API를 호출하여 데이터 파싱 검증
 * 기준 날짜: 2021-01-22 (금요일, 정상 거래일)
 *
 * 실행 방법:
 * ```
 * ./gradlew integrationTest
 * ```
 *
 * 주의사항:
 * - KRX API는 한국 내 네트워크에서만 정상 동작
 * - 해외 IP에서 접근 시 "LOGOUT" 응답 반환
 * - 실패 시 VPN 사용 필요
 * - CI 환경에서는 네트워크 접근 제한으로 자동 스킵
 */
@Tag("integration")
class KrxStockIntegrationTest {
    private lateinit var krxStock: KrxStock

    companion object {
        const val TEST_DATE = "20210122"
        const val HOLIDAY_DATE = "20210101"
        const val SAMSUNG_TICKER = "005930"
    }

    @BeforeTest
    fun setup() {
        krxStock = KrxStock()
    }

    @AfterTest
    fun teardown() {
        krxStock.close()
    }

    /**
     * KRX API 접근 가능 여부 확인
     * 네트워크 에러 시 테스트 스킵
     */
    private suspend fun assumeApiAccessible() {
        try {
            // 간단한 API 호출로 접근 가능 여부 확인
            krxStock.getTickerList(TEST_DATE)
        } catch (e: KrxError.NetworkError) {
            Assumptions.assumeTrue(false,
                "KRX API not accessible (${e.message}). " +
                "Tests skipped. Try from Korean network or use VPN.")
        }
    }

    @Test
    fun `getMarketOhlcv should return stock data for valid date`() = runTest {
        assumeApiAccessible()

        val result = krxStock.getMarketOhlcv(TEST_DATE)

        println("Total stocks: ${result.size}")
        assertTrue(result.isNotEmpty(), "Should have stock data for $TEST_DATE")

        // 삼성전자 확인
        val samsung = result.find { it.ticker == SAMSUNG_TICKER }
        assertTrue(samsung != null, "Samsung should be in the list")
        println("Samsung (005930): close=${samsung?.close}, volume=${samsung?.volume}")

        // 기본 데이터 검증
        assertTrue(samsung!!.close > 0, "Close price should be positive")
        assertTrue(samsung.volume > 0, "Volume should be positive")
    }

    @Test
    fun `getMarketOhlcv should return empty for holiday`() = runTest {
        assumeApiAccessible()

        val result = krxStock.getMarketOhlcv(HOLIDAY_DATE)

        println("Stocks on holiday: ${result.size}")
        // 공휴일에는 빈 리스트 또는 매우 적은 데이터
        assertTrue(result.size < 100, "Should have minimal data on holiday")
    }

    @Test
    fun `getMarketOhlcv should filter by market`() = runTest {
        assumeApiAccessible()

        val kospiResult = krxStock.getMarketOhlcv(TEST_DATE, Market.KOSPI)
        val kosdaqResult = krxStock.getMarketOhlcv(TEST_DATE, Market.KOSDAQ)
        val allResult = krxStock.getMarketOhlcv(TEST_DATE, Market.ALL)

        println("KOSPI: ${kospiResult.size}, KOSDAQ: ${kosdaqResult.size}, ALL: ${allResult.size}")

        assertTrue(kospiResult.isNotEmpty(), "KOSPI should have data")
        assertTrue(kosdaqResult.isNotEmpty(), "KOSDAQ should have data")
        assertTrue(allResult.size >= kospiResult.size, "ALL should have at least KOSPI count")
    }

    @Test
    fun `getMarketCap should return market cap data`() = runTest {
        assumeApiAccessible()

        val result = krxStock.getMarketCap(TEST_DATE)

        println("Total market cap entries: ${result.size}")
        assertTrue(result.isNotEmpty(), "Should have market cap data")

        val samsung = result.find { it.ticker == SAMSUNG_TICKER }
        assertTrue(samsung != null, "Samsung should be in the list")
        println("Samsung market cap: ${samsung?.marketCap}")

        assertTrue(samsung!!.marketCap > 0, "Market cap should be positive")
        assertTrue(samsung.sharesOutstanding > 0, "Shares outstanding should be positive")
    }

    @Test
    fun `getMarketFundamental should return fundamental data`() = runTest {
        assumeApiAccessible()

        val result = krxStock.getMarketFundamental(TEST_DATE)

        println("Total fundamental entries: ${result.size}")
        assertTrue(result.isNotEmpty(), "Should have fundamental data")

        val samsung = result.find { it.ticker == SAMSUNG_TICKER }
        assertTrue(samsung != null, "Samsung should be in the list")
        println("Samsung PER: ${samsung?.per}, PBR: ${samsung?.pbr}")

        // PER/PBR은 0일 수 있음 (적자 기업 등)
        assertTrue(samsung!!.close > 0, "Close price should be positive")
    }

    @Test
    fun `getTickerList should return ticker info`() = runTest {
        assumeApiAccessible()

        val result = krxStock.getTickerList(TEST_DATE)

        println("Total tickers: ${result.size}")
        assertTrue(result.isNotEmpty(), "Should have ticker list")

        val samsung = result.find { it.ticker == SAMSUNG_TICKER }
        assertTrue(samsung != null, "Samsung should be in the list")
        println("Samsung: name=${samsung?.name}, market=${samsung?.marketName}, isin=${samsung?.isinCode}")

        assertTrue(samsung!!.name.isNotEmpty(), "Name should not be empty")
        assertTrue(samsung.isinCode.startsWith("KR"), "ISIN should start with KR")
    }

    @Test
    fun `getOhlcvByTicker should return history for Samsung`() = runTest {
        assumeApiAccessible()

        val result = krxStock.getOhlcvByTicker("20210118", "20210122", SAMSUNG_TICKER)

        println("Samsung history entries: ${result.size}")
        assertTrue(result.isNotEmpty(), "Should have history data")

        // 5 거래일 중 실제 영업일만 반환
        result.forEach { entry ->
            println("Date: ${entry.date}, Close: ${entry.close}, Volume: ${entry.volume}")
            assertTrue(entry.close > 0, "Close should be positive")
        }
    }
}
