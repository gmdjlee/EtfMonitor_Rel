package com.krxkt.integration

import com.krxkt.KrxIndex
import com.krxkt.model.IndexMarket
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * KrxIndex 통합 테스트
 *
 * 실제 KRX API를 호출하므로 CI에서는 @Ignore 처리
 * 로컬에서 수동 실행: ./gradlew test --tests "*KrxIndexIntegrationTest*"
 *
 * 주의: 한국 네트워크 또는 VPN 필요
 */
@Ignore("Integration test - requires network access to KRX")
class KrxIndexIntegrationTest {

    private val krxIndex = KrxIndex()

    // 테스트 기준 데이터
    private val testStartDate = "20210101"
    private val testEndDate = "20210131"
    private val testDate = "20210122"

    @Test
    fun `getOhlcvByTicker should return KOSPI 200 data`() = runTest {
        val result = krxIndex.getOhlcvByTicker(testStartDate, testEndDate, KrxIndex.TICKER_KOSPI_200)

        assertTrue(result.isNotEmpty(), "KOSPI 200 data should not be empty")

        // 날짜 형식 검증
        result.forEach { ohlcv ->
            assertTrue(ohlcv.date.length == 8, "Date should be yyyyMMdd format")
            assertTrue(ohlcv.close > 0, "Close should be positive")
            assertTrue(ohlcv.open > 0, "Open should be positive")
        }

        // 영업일 수 검증 (1월에 약 18~20 영업일)
        assertTrue(result.size >= 15, "Should have at least 15 trading days")
    }

    @Test
    fun `getKospi should return KOSPI index data`() = runTest {
        val result = krxIndex.getKospi(testStartDate, testEndDate)

        assertTrue(result.isNotEmpty(), "KOSPI data should not be empty")

        val firstDay = result.first()
        assertTrue(firstDay.close > 2000, "KOSPI should be above 2000")
    }

    @Test
    fun `getKospi200 should return KOSPI 200 index data`() = runTest {
        val result = krxIndex.getKospi200(testStartDate, testEndDate)

        assertTrue(result.isNotEmpty(), "KOSPI 200 data should not be empty")

        val firstDay = result.first()
        assertTrue(firstDay.close > 200, "KOSPI 200 should be above 200")
    }

    @Test
    fun `getKosdaq should return KOSDAQ index data`() = runTest {
        val result = krxIndex.getKosdaq(testStartDate, testEndDate)

        assertTrue(result.isNotEmpty(), "KOSDAQ data should not be empty")

        val firstDay = result.first()
        assertTrue(firstDay.close > 500, "KOSDAQ should be above 500")
    }

    @Test
    fun `getKosdaq150 should return KOSDAQ 150 index data`() = runTest {
        val result = krxIndex.getKosdaq150(testStartDate, testEndDate)

        assertTrue(result.isNotEmpty(), "KOSDAQ 150 data should not be empty")
    }

    @Test
    fun `getIndexList should return index list`() = runTest {
        val result = krxIndex.getIndexList(testDate)

        assertTrue(result.isNotEmpty(), "Index list should not be empty")

        // KOSPI 확인
        val kospi = result.find { it.ticker == KrxIndex.TICKER_KOSPI }
        assertNotNull(kospi, "KOSPI should exist")
        assertEquals("1", kospi.typeCode)

        // KOSPI 200 확인
        val kospi200 = result.find { it.ticker == KrxIndex.TICKER_KOSPI_200 }
        assertNotNull(kospi200, "KOSPI 200 should exist")
    }

    @Test
    fun `getIndexList with KOSPI filter should return only KOSPI indices`() = runTest {
        val result = krxIndex.getIndexList(testDate, IndexMarket.KOSPI)

        assertTrue(result.isNotEmpty(), "KOSPI index list should not be empty")

        // 모든 결과가 KOSPI 타입인지 확인
        result.forEach { index ->
            assertTrue(index.isKospi, "All indices should be KOSPI type")
            assertEquals("1", index.typeCode)
        }
    }

    @Test
    fun `getIndexList with KOSDAQ filter should return only KOSDAQ indices`() = runTest {
        val result = krxIndex.getIndexList(testDate, IndexMarket.KOSDAQ)

        assertTrue(result.isNotEmpty(), "KOSDAQ index list should not be empty")

        // 모든 결과가 KOSDAQ 타입인지 확인
        result.forEach { index ->
            assertTrue(index.isKosdaq, "All indices should be KOSDAQ type")
            assertEquals("2", index.typeCode)
        }
    }

    @Test
    fun `getIndexName should return correct name`() = runTest {
        val name = krxIndex.getIndexName(KrxIndex.TICKER_KOSPI_200, testDate)

        assertNotNull(name, "Name should not be null")
        assertTrue(name.contains("200") || name.contains("코스피"), "Should be KOSPI 200 related name")
    }

    @Test
    fun `getIndexName should return null for invalid ticker`() = runTest {
        val name = krxIndex.getIndexName("9999", testDate)

        assertEquals(null, name)
    }

    @Test
    fun `verify index data has volume and trading value`() = runTest {
        val result = krxIndex.getKospi200(testStartDate, testEndDate)

        assertTrue(result.isNotEmpty())

        val tradingDay = result.first()
        assertTrue(tradingDay.volume > 0, "Volume should be positive")
        assertTrue(tradingDay.tradingValue > 0, "Trading value should be positive")
    }

    @Test
    fun `verify change type indicators`() = runTest {
        val result = krxIndex.getKospi200(testStartDate, testEndDate)

        assertTrue(result.isNotEmpty())

        // 최소 하나 이상의 거래일이 상승 또는 하락해야 함
        val hasUpOrDown = result.any { it.isUp || it.isDown }
        assertTrue(hasUpOrDown, "Should have at least one up or down day")
    }
}
