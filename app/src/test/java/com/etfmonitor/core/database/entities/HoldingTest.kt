package com.etfmonitor.core.database.entities

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Holding.create() 팩토리 메서드 및 SnapshotType 테스트
 *
 * 테스트 범위:
 * - weightBps 압축/복원 (Float → Short 변환)
 * - amountMillion 압축/복원 (Float → Int 변환)
 * - 오버플로우/언더플로우 경계 처리
 * - 필드 직접 대입 (etfTicker, stockTicker, name, date)
 * - SnapshotType.fromValue() 매핑
 */
@DisplayName("Holding 엔티티 테스트")
class HoldingTest {

    // =====================================================================
    // 공통 테스트 픽스처
    // =====================================================================

    private val etfTicker = "069500"
    private val stockTicker = "005930"
    private val stockName = "삼성전자"
    private val date = "2026-02-19"

    private fun createHolding(
        weight: Float,
        amount: Float,
        snapshotType: SnapshotType = SnapshotType.DAILY
    ): Holding = Holding.create(
        etfTicker = etfTicker,
        stockTicker = stockTicker,
        stockName = stockName,
        date = date,
        weight = weight,
        amount = amount,
        snapshotType = snapshotType
    )

    // =====================================================================
    // weightBps 변환 테스트
    // =====================================================================

    @Nested
    @DisplayName("weightBps 변환 테스트 (Float → Short)")
    inner class WeightBpsConversionTests {

        @Test
        @DisplayName("비중 0.25 (25%)를 2500 bps로 변환한다")
        fun `create converts weight 0_25 to 2500 bps`() {
            val holding = createHolding(weight = 0.25f, amount = 0f)

            assertEquals(2500.toShort(), holding.weightBps)
        }

        @Test
        @DisplayName("비중 0.0001 (0.01%)를 1 bps로 변환한다")
        fun `create converts weight 0_0001 to 1 bps`() {
            val holding = createHolding(weight = 0.0001f, amount = 0f)

            assertEquals(1.toShort(), holding.weightBps)
        }

        @Test
        @DisplayName("비중 0을 0 bps로 변환한다")
        fun `create converts weight 0 to 0 bps`() {
            val holding = createHolding(weight = 0f, amount = 0f)

            assertEquals(0.toShort(), holding.weightBps)
        }

        @Test
        @DisplayName("음수 비중은 0 bps로 클램핑된다")
        fun `create with negative weight clamps to 0 bps`() {
            val holding = createHolding(weight = -0.5f, amount = 0f)

            assertEquals(0.toShort(), holding.weightBps)
        }

        @Test
        @DisplayName("Short.MAX_VALUE를 초과하는 비중은 Short.MAX_VALUE로 클램핑된다")
        fun `create with weight exceeding Short MAX_VALUE clamps`() {
            // Short.MAX_VALUE = 32767 bps → 3.2767f (327.67%)
            // 비중이 그 이상이면 최대값으로 클램핑되어야 한다
            val holding = createHolding(weight = 10.0f, amount = 0f) // 10 = 100000 bps → 클램핑

            assertEquals(Short.MAX_VALUE, holding.weightBps)
        }

        @ParameterizedTest(name = "비중 {0} → {1} bps")
        @CsvSource(
            "0.01, 100",
            "0.05, 500",
            "0.1025, 1025",
            "0.5, 5000",
            "1.0, 10000"
        )
        @DisplayName("다양한 비중값이 올바른 bps로 변환된다")
        fun `create converts various weights to correct bps`(weightInput: Float, expectedBps: Int) {
            val holding = createHolding(weight = weightInput, amount = 0f)

            assertEquals(expectedBps.toShort(), holding.weightBps)
        }
    }

    // =====================================================================
    // amountMillion 변환 테스트
    // =====================================================================

    @Nested
    @DisplayName("amountMillion 변환 테스트 (Float → Int)")
    inner class AmountMillionConversionTests {

        @Test
        @DisplayName("500억원(50,000,000,000)을 50000 million으로 변환한다")
        fun `create converts 50 billion won amount to 50000 million`() {
            val holding = createHolding(weight = 0f, amount = 50_000_000_000f)

            assertEquals(50_000, holding.amountMillion)
        }

        @Test
        @DisplayName("1,000,000원 미만의 소액은 0 million으로 변환된다")
        fun `create converts small amount under 1M to 0 million`() {
            val holding = createHolding(weight = 0f, amount = 500_000f) // 50만원

            assertEquals(0, holding.amountMillion)
        }

        @Test
        @DisplayName("0원은 0 million으로 변환된다")
        fun `create converts zero amount to 0 million`() {
            val holding = createHolding(weight = 0f, amount = 0f)

            assertEquals(0, holding.amountMillion)
        }

        @Test
        @DisplayName("정확히 1,000,000원은 1 million으로 변환된다")
        fun `create converts exactly 1M to 1 million`() {
            val holding = createHolding(weight = 0f, amount = 1_000_000f)

            assertEquals(1, holding.amountMillion)
        }

        @Test
        @DisplayName("1,234,567,890원은 1234 million으로 변환된다 (소수 버림)")
        fun `create converts 1_234_567_890 to 1234 million`() {
            val holding = createHolding(weight = 0f, amount = 1_234_567_890f)

            assertEquals(1234, holding.amountMillion)
        }
    }

    // =====================================================================
    // 역변환(복원) 프로퍼티 테스트
    // =====================================================================

    @Nested
    @DisplayName("압축/복원 라운드트립 테스트")
    inner class RoundTripTests {

        @Test
        @DisplayName("weight 프로퍼티가 weightBps에서 올바른 Float을 반환한다")
        fun `weight property returns correct float from bps round-trip`() {
            // 0.25 → 2500 bps → 2500 / 10000f = 0.25f
            val holding = createHolding(weight = 0.25f, amount = 0f)

            assertEquals(0.25f, holding.weight, 0.0001f)
        }

        @Test
        @DisplayName("amount 프로퍼티가 amountMillion에서 올바른 Float을 반환한다")
        fun `amount property returns correct float from million round-trip`() {
            // 5,000,000,000 → 5000 million → 5000 * 1_000_000f = 5,000,000,000f
            val holding = createHolding(weight = 0f, amount = 5_000_000_000f)

            assertEquals(5_000_000_000f, holding.amount, 1f)
        }

        @Test
        @DisplayName("weight 역변환 오차는 ±0.0001 이내다 (1 bps 해상도)")
        fun `weight round-trip precision is within 1 bps resolution`() {
            // 최소 단위: 1 bps = 0.0001
            val inputWeight = 0.1357f // 1357 bps
            val holding = createHolding(weight = inputWeight, amount = 0f)
            val restoredWeight = holding.weight

            // 1 bps = 0.0001 이내 오차 허용
            assertTrue(
                kotlin.math.abs(inputWeight - restoredWeight) < 0.0001f,
                "Round-trip error: input=$inputWeight, restored=$restoredWeight"
            )
        }

        @Test
        @DisplayName("amount 역변환 오차는 ±1,000,000원 이내다 (1 million 해상도)")
        fun `amount round-trip precision is within 1 million won resolution`() {
            // 최소 단위: 1 million = 1,000,000원
            val inputAmount = 7_890_000_000f
            val holding = createHolding(weight = 0f, amount = inputAmount)
            val restoredAmount = holding.amount

            assertTrue(
                kotlin.math.abs(inputAmount - restoredAmount) < 1_000_000f,
                "Round-trip error: input=$inputAmount, restored=$restoredAmount"
            )
        }
    }

    // =====================================================================
    // 필드 직접 대입 테스트
    // =====================================================================

    @Nested
    @DisplayName("필드 직접 대입 테스트")
    inner class FieldAssignmentTests {

        @Test
        @DisplayName("create가 etfTicker, stockTicker, stockName, date를 올바르게 설정한다")
        fun `create sets etfTicker, stockTicker, name, date correctly`() {
            val holding = Holding.create(
                etfTicker = "069500",
                stockTicker = "005930",
                stockName = "삼성전자",
                date = "2026-02-19",
                weight = 0.1f,
                amount = 1_000_000_000f
            )

            assertEquals("069500", holding.etfTicker)
            assertEquals("005930", holding.stockTicker)
            assertEquals("삼성전자", holding.stockName)
            assertEquals("2026-02-19", holding.date)
        }

        @Test
        @DisplayName("snapshotType 기본값은 DAILY다")
        fun `create uses DAILY as default snapshotType`() {
            val holding = Holding.create(
                etfTicker = etfTicker,
                stockTicker = stockTicker,
                stockName = stockName,
                date = date,
                weight = 0.1f,
                amount = 1_000_000_000f
                // snapshotType 생략 → 기본값 사용
            )

            assertEquals(SnapshotType.DAILY.value, holding.snapshotType)
        }

        @Test
        @DisplayName("snapshotType WEEKLY가 올바르게 저장된다")
        fun `create with WEEKLY snapshotType stores correct value`() {
            val holding = createHolding(weight = 0.1f, amount = 1_000_000f, snapshotType = SnapshotType.WEEKLY)

            assertEquals("WEEKLY", holding.snapshotType)
        }

        @Test
        @DisplayName("snapshotType MONTHLY가 올바르게 저장된다")
        fun `create with MONTHLY snapshotType stores correct value`() {
            val holding = createHolding(weight = 0.1f, amount = 1_000_000f, snapshotType = SnapshotType.MONTHLY)

            assertEquals("MONTHLY", holding.snapshotType)
        }
    }

    // =====================================================================
    // SnapshotType.fromValue() 테스트
    // =====================================================================

    @Nested
    @DisplayName("SnapshotType.fromValue() 매핑 테스트")
    inner class SnapshotTypeFromValueTests {

        @Test
        @DisplayName("fromValue(\"DAILY\")는 DAILY를 반환한다")
        fun `SnapshotType fromValue DAILY maps correctly`() {
            assertEquals(SnapshotType.DAILY, SnapshotType.fromValue("DAILY"))
        }

        @Test
        @DisplayName("fromValue(\"WEEKLY\")는 WEEKLY를 반환한다")
        fun `SnapshotType fromValue WEEKLY maps correctly`() {
            assertEquals(SnapshotType.WEEKLY, SnapshotType.fromValue("WEEKLY"))
        }

        @Test
        @DisplayName("fromValue(\"MONTHLY\")는 MONTHLY를 반환한다")
        fun `SnapshotType fromValue MONTHLY maps correctly`() {
            assertEquals(SnapshotType.MONTHLY, SnapshotType.fromValue("MONTHLY"))
        }

        @Test
        @DisplayName("fromValue에 알 수 없는 값이 오면 DAILY를 반환한다")
        fun `SnapshotType fromValue unknown returns DAILY`() {
            assertEquals(SnapshotType.DAILY, SnapshotType.fromValue("UNKNOWN"))
            assertEquals(SnapshotType.DAILY, SnapshotType.fromValue(""))
            assertEquals(SnapshotType.DAILY, SnapshotType.fromValue("daily")) // 소문자
        }

        @ParameterizedTest(name = "fromValue(\"{0}\") → {1}")
        @CsvSource(
            "DAILY, DAILY",
            "WEEKLY, WEEKLY",
            "MONTHLY, MONTHLY"
        )
        @DisplayName("유효한 값들이 올바른 SnapshotType으로 매핑된다")
        fun `SnapshotType fromValue maps all valid values`(input: String, expected: String) {
            val result = SnapshotType.fromValue(input)

            assertEquals(expected, result.value)
        }
    }

    // =====================================================================
    // 경계값 테스트
    // =====================================================================

    @Nested
    @DisplayName("경계값(Edge Case) 테스트")
    inner class BoundaryTests {

        @Test
        @DisplayName("정확히 Short.MAX_VALUE bps에 해당하는 비중은 클램핑되지 않는다")
        fun `weight exactly at Short MAX_VALUE boundary is not clamped`() {
            // Short.MAX_VALUE = 32767 bps = 3.2767f
            val exactWeight = Short.MAX_VALUE.toFloat() / 10000f // 3.2767f
            val holding = createHolding(weight = exactWeight, amount = 0f)

            assertEquals(Short.MAX_VALUE, holding.weightBps)
        }

        @Test
        @DisplayName("정확히 0 bps 초과의 최솟값(0.0001)은 1 bps로 변환된다")
        fun `minimum non-zero weight 0_0001 converts to 1 bps`() {
            val holding = createHolding(weight = 0.0001f, amount = 0f)

            assertEquals(1.toShort(), holding.weightBps)
        }

        @Test
        @DisplayName("weightBps와 amountMillion이 동시에 올바르게 변환된다")
        fun `create converts weight and amount simultaneously and correctly`() {
            val holding = Holding.create(
                etfTicker = "069500",
                stockTicker = "005930",
                stockName = "삼성전자",
                date = "2026-02-19",
                weight = 0.05f,  // 5% → 500 bps
                amount = 10_000_000_000f  // 100억 → 10000 million
            )

            assertEquals(500.toShort(), holding.weightBps)
            assertEquals(10_000, holding.amountMillion)
        }
    }
}
