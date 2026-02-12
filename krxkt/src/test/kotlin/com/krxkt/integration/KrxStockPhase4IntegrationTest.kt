package com.krxkt.integration

import com.krxkt.KrxStock
import com.krxkt.model.AskBidType
import com.krxkt.model.Market
import com.krxkt.model.TradingValueType
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 4 통합 테스트 (투자자별 거래실적, 공매도)
 *
 * 실제 KRX API를 호출하므로 CI에서는 @Ignore 처리
 * 로컬에서 수동 실행: ./gradlew test --tests "*KrxStockPhase4IntegrationTest*"
 *
 * 주의: 한국 네트워크 또는 VPN 필요
 */
@Ignore("Integration test - requires network access to KRX")
class KrxStockPhase4IntegrationTest {

    private val krxStock = KrxStock()

    // 테스트 기준 데이터
    private val testDate = "20210122"
    private val testStartDate = "20210118"
    private val testEndDate = "20210122"
    private val testTicker = "005930" // 삼성전자

    // ============================================================
    // 투자자별 거래실적 테스트
    // ============================================================

    @Test
    fun `getMarketTradingByInvestor should return market-wide investor trading`() = runTest {
        val result = krxStock.getMarketTradingByInvestor(
            testStartDate,
            testEndDate,
            Market.KOSPI,
            TradingValueType.VALUE,
            AskBidType.NET_BUY
        )

        assertTrue(result.isNotEmpty(), "Should have trading data")

        val firstDay = result.first()
        assertTrue(firstDay.date.length == 8, "Date should be yyyyMMdd format")
        // 투자자별 합계 검증 (기관 + 개인 + 외국인 + 기타법인 ≈ 전체)
        assertNotNull(firstDay.institutionalTotal)
        assertNotNull(firstDay.individual)
        assertNotNull(firstDay.foreigner)
    }

    @Test
    fun `getMarketTradingByInvestor with volume type should work`() = runTest {
        val result = krxStock.getMarketTradingByInvestor(
            testStartDate,
            testEndDate,
            Market.KOSPI,
            TradingValueType.VOLUME,
            AskBidType.NET_BUY
        )

        assertTrue(result.isNotEmpty(), "Should have trading data")
    }

    @Test
    fun `getTradingByInvestor should return ticker investor trading`() = runTest {
        val result = krxStock.getTradingByInvestor(
            testStartDate,
            testEndDate,
            testTicker,
            TradingValueType.VALUE,
            AskBidType.NET_BUY
        )

        assertTrue(result.isNotEmpty(), "Should have trading data for Samsung")

        val firstDay = result.first()
        assertTrue(firstDay.date.length == 8, "Date should be yyyyMMdd format")
    }

    @Test
    fun `getTradingByInvestor should return empty for invalid ticker`() = runTest {
        val result = krxStock.getTradingByInvestor(
            testStartDate,
            testEndDate,
            "999999",
            TradingValueType.VALUE,
            AskBidType.NET_BUY
        )

        assertTrue(result.isEmpty(), "Should return empty for invalid ticker")
    }

    // ============================================================
    // 공매도 테스트
    // ============================================================

    @Test
    fun `getShortSellingAll should return all tickers short selling`() = runTest {
        val result = krxStock.getShortSellingAll(testDate, Market.KOSPI)

        assertTrue(result.isNotEmpty(), "Should have short selling data")

        // 삼성전자 확인
        val samsung = result.find { it.ticker == testTicker }
        assertNotNull(samsung, "Samsung should have short selling data")
        assertTrue(samsung.shortVolume >= 0, "Short volume should be non-negative")
        assertTrue(samsung.totalVolume > 0, "Total volume should be positive")
    }

    @Test
    fun `getShortSellingByTicker should return ticker short selling history`() = runTest {
        val result = krxStock.getShortSellingByTicker(testStartDate, testEndDate, testTicker)

        assertTrue(result.isNotEmpty(), "Should have short selling history")

        result.forEach { history ->
            assertTrue(history.date.length == 8, "Date should be yyyyMMdd format")
            assertTrue(history.shortVolume >= 0, "Short volume should be non-negative")
            assertTrue(history.totalVolume >= 0, "Total volume should be non-negative")
        }
    }

    @Test
    fun `getShortSellingByTicker should return empty for invalid ticker`() = runTest {
        val result = krxStock.getShortSellingByTicker(testStartDate, testEndDate, "999999")

        assertTrue(result.isEmpty(), "Should return empty for invalid ticker")
    }

    @Test
    fun `getShortBalanceAll should return all tickers short balance`() = runTest {
        val result = krxStock.getShortBalanceAll(testDate, Market.KOSPI)

        assertTrue(result.isNotEmpty(), "Should have short balance data")

        // 삼성전자 확인
        val samsung = result.find { it.ticker == testTicker }
        assertNotNull(samsung, "Samsung should have short balance data")
        assertTrue(samsung.balanceQuantity >= 0, "Balance quantity should be non-negative")
        assertTrue(samsung.listedShares > 0, "Listed shares should be positive")
    }

    @Test
    fun `getShortBalanceByTicker should return ticker short balance history`() = runTest {
        val result = krxStock.getShortBalanceByTicker(testStartDate, testEndDate, testTicker)

        assertTrue(result.isNotEmpty(), "Should have short balance history")

        result.forEach { history ->
            assertTrue(history.date.length == 8, "Date should be yyyyMMdd format")
            assertTrue(history.balanceQuantity >= 0, "Balance quantity should be non-negative")
            assertTrue(history.listedShares > 0, "Listed shares should be positive")
        }
    }

    @Test
    fun `getShortBalanceByTicker should return empty for invalid ticker`() = runTest {
        val result = krxStock.getShortBalanceByTicker(testStartDate, testEndDate, "999999")

        assertTrue(result.isEmpty(), "Should return empty for invalid ticker")
    }

    @Test
    fun `verify short selling volume ratio calculation`() = runTest {
        val result = krxStock.getShortSellingAll(testDate, Market.KOSPI)

        assertTrue(result.isNotEmpty())

        val samsungData = result.find { it.ticker == testTicker }
        assertNotNull(samsungData)

        // API에서 받은 비율과 계산된 비율 비교
        if (samsungData.volumeRatio != null && samsungData.totalVolume > 0) {
            val diff = kotlin.math.abs(samsungData.volumeRatio!! - samsungData.calculatedVolumeRatio)
            assertTrue(diff < 0.1, "Volume ratio should match calculated value")
        }
    }
}
