package com.krxkt.model

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InvestorTradingTest {

    @Test
    fun `fromJson should parse valid investor trading response`() {
        // 실제 KRX API 응답 형식 (일별 추이)
        val json = """
            {
                "TRD_DD": "2021/01/22",
                "TRDVAL1": "1,234,567,890",
                "TRDVAL2": "234,567,890",
                "TRDVAL3": "345,678,901",
                "TRDVAL4": "456,789,012",
                "TRDVAL5": "567,890,123",
                "TRDVAL6": "678,901,234",
                "TRDVAL7": "789,012,345",
                "TRDVAL8": "4,307,407,395",
                "TRDVAL9": "890,123,456",
                "TRDVAL10": "-5,678,901,234",
                "TRDVAL11": "987,654,321",
                "TRDVAL_TOT": "506,283,938"
            }
        """.trimIndent()

        val jsonObject = JsonParser.parseString(json).asJsonObject
        val result = InvestorTrading.fromJson(jsonObject)

        assertNotNull(result)
        assertEquals("20210122", result.date)
        assertEquals(1234567890L, result.financialInvestment)
        assertEquals(234567890L, result.insurance)
        assertEquals(345678901L, result.investmentTrust)
        assertEquals(456789012L, result.privateEquity)
        assertEquals(567890123L, result.bank)
        assertEquals(678901234L, result.otherFinance)
        assertEquals(789012345L, result.pensionFund)
        assertEquals(4307407395L, result.institutionalTotal)
        assertEquals(890123456L, result.otherCorporation)
        assertEquals(-5678901234L, result.individual)
        assertEquals(987654321L, result.foreigner)
        assertEquals(506283938L, result.total)
    }

    @Test
    fun `fromJson should handle negative values`() {
        val json = """
            {
                "TRD_DD": "2021/01/22",
                "TRDVAL1": "-1,000,000",
                "TRDVAL2": "-500,000",
                "TRDVAL3": "0",
                "TRDVAL4": "0",
                "TRDVAL5": "0",
                "TRDVAL6": "0",
                "TRDVAL7": "0",
                "TRDVAL8": "-1,500,000",
                "TRDVAL9": "0",
                "TRDVAL10": "2,000,000",
                "TRDVAL11": "-500,000",
                "TRDVAL_TOT": "0"
            }
        """.trimIndent()

        val jsonObject = JsonParser.parseString(json).asJsonObject
        val result = InvestorTrading.fromJson(jsonObject)

        assertNotNull(result)
        assertEquals(-1000000L, result.financialInvestment)
        assertEquals(-1500000L, result.institutionalTotal)
        assertEquals(2000000L, result.individual)
        assertEquals(-500000L, result.foreigner)
    }

    @Test
    fun `fromJson should return null for missing date`() {
        val json = """
            {
                "TRDVAL1": "1,000,000",
                "TRDVAL10": "2,000,000"
            }
        """.trimIndent()

        val jsonObject = JsonParser.parseString(json).asJsonObject
        val result = InvestorTrading.fromJson(jsonObject)

        assertNull(result)
    }

    @Test
    fun `fromJson should handle empty and missing fields`() {
        val json = """
            {
                "TRD_DD": "2021/01/22",
                "TRDVAL1": "",
                "TRDVAL2": "-",
                "TRDVAL10": "1,000,000"
            }
        """.trimIndent()

        val jsonObject = JsonParser.parseString(json).asJsonObject
        val result = InvestorTrading.fromJson(jsonObject)

        assertNotNull(result)
        assertEquals(0L, result.financialInvestment)
        assertEquals(0L, result.insurance)
        assertEquals(1000000L, result.individual)
    }

    @Test
    fun `InvestorType should map correctly`() {
        assertEquals(InvestorType.FINANCIAL_INVESTMENT, InvestorType.fromCode(1000))
        assertEquals(InvestorType.INDIVIDUAL, InvestorType.fromCode(8000))
        assertEquals(InvestorType.FOREIGNER, InvestorType.fromCode(9000))
        assertEquals(InvestorType.INSTITUTIONAL_TOTAL, InvestorType.fromCode(7050))
        assertNull(InvestorType.fromCode(9999999))
    }

    @Test
    fun `InvestorType fromName should work`() {
        assertEquals(InvestorType.FINANCIAL_INVESTMENT, InvestorType.fromName("금융투자"))
        assertEquals(InvestorType.INDIVIDUAL, InvestorType.fromName("개인"))
        assertEquals(InvestorType.FOREIGNER, InvestorType.fromName("외국인"))
    }

    @Test
    fun `TradingValueType should have correct codes`() {
        assertEquals("1", TradingValueType.VOLUME.code)
        assertEquals("2", TradingValueType.VALUE.code)
    }

    @Test
    fun `AskBidType should have correct codes`() {
        assertEquals("1", AskBidType.SELL.code)
        assertEquals("2", AskBidType.BUY.code)
        assertEquals("3", AskBidType.NET_BUY.code)
    }
}
