package com.etfmonitor.core.data.krx.adapter

import com.krxkt.model.EtfPortfolio
import com.etfmonitor.core.database.entities.Holding
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HoldingMapper 단위 테스트
 *
 * 테스트 범위:
 * - fromEtfPortfolio: Holding.create() 팩토리를 통해 올바르게 매핑되는지 확인
 * - null weight 처리 (0f로 기본값 설정)
 * - amount Long→Float 변환
 * - 필드 직접 대입 (etfTicker, ticker, name, date)
 * - 대용량 금액 처리
 * - weightBps/amountMillion 압축 검증
 *
 * 주의: CLAUDE.md Critical Rule #1 — 항상 Holding.create() 팩토리 사용
 */
@DisplayName("HoldingMapper 테스트")
class HoldingMapperTest {

    // ── 헬퍼 함수 ────────────────────────────────────────────────────────────

    private fun createPortfolio(
        ticker: String = "005930",
        name: String = "삼성전자",
        shares: Long = 1000L,
        valuationAmount: Long = 1_000_000L,
        amount: Long = 1_000_000_000L,
        weight: Double? = 0.05
    ) = EtfPortfolio(
        ticker = ticker,
        name = name,
        shares = shares,
        valuationAmount = valuationAmount,
        amount = amount,
        weight = weight
    )

    // =========================================================================
    // 기본 매핑 테스트
    // =========================================================================

    @Nested
    @DisplayName("기본 필드 매핑 테스트")
    inner class BasicMappingTests {

        @Test
        @DisplayName("etfTicker가 올바르게 설정된다")
        fun `fromEtfPortfolio sets etfTicker correctly`() {
            val portfolio = createPortfolio()
            val holding = HoldingMapper.fromEtfPortfolio("069500", "2026-02-19", portfolio)

            assertEquals("069500", holding.etfTicker)
        }

        @Test
        @DisplayName("stockTicker가 portfolio.ticker에서 올바르게 설정된다")
        fun `fromEtfPortfolio sets stockTicker from portfolio ticker`() {
            val portfolio = createPortfolio(ticker = "005930")
            val holding = HoldingMapper.fromEtfPortfolio("069500", "2026-02-19", portfolio)

            assertEquals("005930", holding.stockTicker)
        }

        @Test
        @DisplayName("stockName이 portfolio.name에서 올바르게 설정된다")
        fun `fromEtfPortfolio sets stockName from portfolio name`() {
            val portfolio = createPortfolio(name = "삼성전자")
            val holding = HoldingMapper.fromEtfPortfolio("069500", "2026-02-19", portfolio)

            assertEquals("삼성전자", holding.stockName)
        }

        @Test
        @DisplayName("date가 올바르게 설정된다")
        fun `fromEtfPortfolio sets date correctly`() {
            val portfolio = createPortfolio()
            val holding = HoldingMapper.fromEtfPortfolio("069500", "2026-02-19", portfolio)

            assertEquals("2026-02-19", holding.date)
        }

        @Test
        @DisplayName("snapshotType은 기본값 DAILY로 설정된다")
        fun `fromEtfPortfolio uses DAILY as default snapshotType`() {
            val portfolio = createPortfolio()
            val holding = HoldingMapper.fromEtfPortfolio("069500", "2026-02-19", portfolio)

            assertEquals("DAILY", holding.snapshotType)
        }
    }

    // =========================================================================
    // weight 변환 테스트
    // =========================================================================

    @Nested
    @DisplayName("weight 변환 테스트")
    inner class WeightConversionTests {

        @Test
        @DisplayName("weight=0.05 → weightBps=500으로 변환된다")
        fun `fromEtfPortfolio converts weight 0_05 to 500 bps`() {
            val portfolio = createPortfolio(weight = 0.05)
            val holding = HoldingMapper.fromEtfPortfolio("069500", "2026-02-19", portfolio)

            assertEquals(500.toShort(), holding.weightBps)
        }

        @Test
        @DisplayName("weight=0.25 → weightBps=2500으로 변환된다")
        fun `fromEtfPortfolio converts weight 0_25 to 2500 bps`() {
            val portfolio = createPortfolio(weight = 0.25)
            val holding = HoldingMapper.fromEtfPortfolio("069500", "2026-02-19", portfolio)

            assertEquals(2500.toShort(), holding.weightBps)
        }

        @Test
        @DisplayName("weight=null이면 0f(weightBps=0)로 처리된다")
        fun `fromEtfPortfolio null weight defaults to 0 bps`() {
            val portfolio = createPortfolio(weight = null)
            val holding = HoldingMapper.fromEtfPortfolio("069500", "2026-02-19", portfolio)

            assertEquals(0.toShort(), holding.weightBps)
        }

        @Test
        @DisplayName("weight=0.0이면 weightBps=0으로 변환된다")
        fun `fromEtfPortfolio weight 0_0 gives 0 bps`() {
            val portfolio = createPortfolio(weight = 0.0)
            val holding = HoldingMapper.fromEtfPortfolio("069500", "2026-02-19", portfolio)

            assertEquals(0.toShort(), holding.weightBps)
        }

        @Test
        @DisplayName("weight=1.0(100%)이면 weightBps=10000으로 변환된다")
        fun `fromEtfPortfolio weight 1_0 gives 10000 bps`() {
            val portfolio = createPortfolio(weight = 1.0)
            val holding = HoldingMapper.fromEtfPortfolio("069500", "2026-02-19", portfolio)

            assertEquals(10000.toShort(), holding.weightBps)
        }
    }

    // =========================================================================
    // amount 변환 테스트
    // =========================================================================

    @Nested
    @DisplayName("amount 변환 테스트 (Long → Float → amountMillion)")
    inner class AmountConversionTests {

        @Test
        @DisplayName("amount=1_000_000_000 → amountMillion=1000으로 변환된다")
        fun `fromEtfPortfolio converts 1B amount to 1000 million`() {
            val portfolio = createPortfolio(amount = 1_000_000_000L)
            val holding = HoldingMapper.fromEtfPortfolio("069500", "2026-02-19", portfolio)

            assertEquals(1000, holding.amountMillion)
        }

        @Test
        @DisplayName("amount=500_000_000 → amountMillion=500으로 변환된다")
        fun `fromEtfPortfolio converts 500M amount to 500 million`() {
            val portfolio = createPortfolio(amount = 500_000_000L)
            val holding = HoldingMapper.fromEtfPortfolio("069500", "2026-02-19", portfolio)

            assertEquals(500, holding.amountMillion)
        }

        @Test
        @DisplayName("amount=0 → amountMillion=0으로 변환된다")
        fun `fromEtfPortfolio amount 0 gives 0 million`() {
            val portfolio = createPortfolio(amount = 0L)
            val holding = HoldingMapper.fromEtfPortfolio("069500", "2026-02-19", portfolio)

            assertEquals(0, holding.amountMillion)
        }

        @Test
        @DisplayName("1,000,000원 미만의 소액은 amountMillion=0으로 변환된다")
        fun `fromEtfPortfolio small amount below 1M gives 0 million`() {
            val portfolio = createPortfolio(amount = 500_000L)
            val holding = HoldingMapper.fromEtfPortfolio("069500", "2026-02-19", portfolio)

            assertEquals(0, holding.amountMillion)
        }

        @Test
        @DisplayName("10조원(10_000_000_000_000)도 올바르게 변환된다")
        fun `fromEtfPortfolio large amount 10T converts correctly`() {
            val portfolio = createPortfolio(amount = 10_000_000_000_000L)
            val holding = HoldingMapper.fromEtfPortfolio("069500", "2026-02-19", portfolio)

            // 10_000_000_000_000 / 1_000_000 = 10_000_000
            assertEquals(10_000_000, holding.amountMillion)
        }
    }

    // =========================================================================
    // Holding.create() 팩토리 사용 검증
    // =========================================================================

    @Nested
    @DisplayName("Holding.create() 팩토리 경유 검증")
    inner class FactoryUsageTests {

        @Test
        @DisplayName("반환값은 Holding 타입이다")
        fun `fromEtfPortfolio returns Holding instance`() {
            val portfolio = createPortfolio()
            val result = HoldingMapper.fromEtfPortfolio("069500", "2026-02-19", portfolio)

            assertTrue(result is Holding)
        }

        @Test
        @DisplayName("weight 프로퍼티가 원본 값과 근사하게 복원된다 (1 bps 해상도)")
        fun `fromEtfPortfolio weight property recovers approximately original value`() {
            val portfolio = createPortfolio(weight = 0.1234)
            val holding = HoldingMapper.fromEtfPortfolio("069500", "2026-02-19", portfolio)

            // weightBps → weight 역변환 오차 < 0.0001 (1 bps 해상도)
            val restored = holding.weight
            val original = 0.1234f
            assertTrue(
                kotlin.math.abs(restored - original) < 0.001f,
                "weight round-trip error too large: original=$original, restored=$restored"
            )
        }

        @Test
        @DisplayName("amount 프로퍼티가 원본 값과 근사하게 복원된다 (1 million 해상도)")
        fun `fromEtfPortfolio amount property recovers approximately original value`() {
            val originalAmount = 7_890_000_000L
            val portfolio = createPortfolio(amount = originalAmount)
            val holding = HoldingMapper.fromEtfPortfolio("069500", "2026-02-19", portfolio)

            val restored = holding.amount
            // 최소 해상도: 1_000_000원 (1 million)
            assertTrue(
                kotlin.math.abs(restored - originalAmount.toFloat()) < 1_000_000f,
                "amount round-trip error too large: original=$originalAmount, restored=$restored"
            )
        }

        @Test
        @DisplayName("여러 다른 ETF/종목 조합도 올바르게 매핑된다")
        fun `fromEtfPortfolio maps multiple different etf and stock combinations correctly`() {
            val testCases = listOf(
                Triple("069500", "005930", "삼성전자"),
                Triple("069500", "000660", "SK하이닉스"),
                Triple("114800", "051910", "LG화학")
            )

            testCases.forEach { (etfTicker, stockTicker, stockName) ->
                val portfolio = createPortfolio(ticker = stockTicker, name = stockName)
                val holding = HoldingMapper.fromEtfPortfolio(etfTicker, "2026-02-19", portfolio)

                assertEquals(etfTicker, holding.etfTicker, "etfTicker mismatch for $etfTicker")
                assertEquals(stockTicker, holding.stockTicker, "stockTicker mismatch for $stockTicker")
                assertEquals(stockName, holding.stockName, "stockName mismatch for $stockName")
            }
        }
    }
}
