package com.krxkt.integration

import com.krxkt.KrxEtf
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * KrxEtf 통합 테스트
 *
 * 실제 KRX API를 호출하므로 CI에서는 @Ignore 처리
 * 로컬에서 수동 실행: ./gradlew test --tests "*KrxEtfIntegrationTest*"
 *
 * 주의: 한국 네트워크 또는 VPN 필요
 */
@Ignore("Integration test - requires network access to KRX")
class KrxEtfIntegrationTest {

    private val krxEtf = KrxEtf()

    // 테스트 기준 데이터
    private val testDate = "20210122"
    private val testTicker = "069500" // KODEX 200
    private val testStartDate = "20210101"
    private val testEndDate = "20210131"

    @Test
    fun `getEtfPrice should return ETF list for valid date`() = runTest {
        val result = krxEtf.getEtfPrice(testDate)

        assertTrue(result.isNotEmpty(), "ETF list should not be empty")

        // KODEX 200 확인
        val kodex200 = result.find { it.ticker == testTicker }
        assertNotNull(kodex200, "KODEX 200 should exist")
        assertEquals("KODEX 200", kodex200.name)
        assertTrue(kodex200.close > 0, "Close price should be positive")
        assertTrue(kodex200.volume > 0, "Volume should be positive")
        assertNotNull(kodex200.nav, "NAV should exist")
    }

    @Test
    fun `getEtfPrice should return empty list for holiday`() = runTest {
        // 2021년 1월 1일은 신정 (휴장일)
        val result = krxEtf.getEtfPrice("20210101")

        assertTrue(result.isEmpty(), "Should return empty list for holiday")
    }

    @Test
    fun `getOhlcvByTicker should return history for valid ticker`() = runTest {
        val result = krxEtf.getOhlcvByTicker(testStartDate, testEndDate, testTicker)

        assertTrue(result.isNotEmpty(), "History should not be empty")

        // 날짜 형식 검증
        result.forEach { history ->
            assertTrue(history.date.length == 8, "Date should be yyyyMMdd format")
            assertTrue(history.close > 0, "Close price should be positive")
        }

        // 영업일 수 검증 (1월에 약 18~20 영업일)
        assertTrue(result.size >= 15, "Should have at least 15 trading days")
        assertTrue(result.size <= 25, "Should have at most 25 days")
    }

    @Test
    fun `getOhlcvByTicker should return empty list for invalid ticker`() = runTest {
        val result = krxEtf.getOhlcvByTicker(testStartDate, testEndDate, "999999")

        assertTrue(result.isEmpty(), "Should return empty list for invalid ticker")
    }

    @Test
    fun `getEtfTickerList should return ticker list`() = runTest {
        val result = krxEtf.getEtfTickerList(testDate)

        assertTrue(result.isNotEmpty(), "Ticker list should not be empty")

        // KODEX 200 확인
        val kodex200 = result.find { it.ticker == testTicker }
        assertNotNull(kodex200, "KODEX 200 should exist")
        assertEquals("KR7069500007", kodex200.isinCode)
    }

    @Test
    fun `getEtfName should return correct name`() = runTest {
        val name = krxEtf.getEtfName(testTicker, testDate)

        assertNotNull(name, "Name should not be null")
        assertEquals("KODEX 200", name)
    }

    @Test
    fun `getEtfName should return null for invalid ticker`() = runTest {
        val name = krxEtf.getEtfName("999999", testDate)

        assertEquals(null, name)
    }

    @Test
    fun `verify KODEX 200 data consistency`() = runTest {
        // 전종목 시세에서 조회
        val priceList = krxEtf.getEtfPrice(testDate)
        val priceData = priceList.find { it.ticker == testTicker }

        // 기간 조회에서 조회
        val historyList = krxEtf.getOhlcvByTicker(testDate, testDate, testTicker)

        assertNotNull(priceData, "Price data should exist")
        assertTrue(historyList.isNotEmpty(), "History should exist")

        val historyData = historyList.first()

        // 동일 날짜의 종가 비교
        assertEquals(priceData.close, historyData.close, "Close prices should match")
        assertEquals(priceData.volume, historyData.volume, "Volumes should match")
    }
}
