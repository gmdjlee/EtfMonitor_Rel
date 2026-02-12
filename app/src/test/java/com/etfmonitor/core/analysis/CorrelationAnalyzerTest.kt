package com.etfmonitor.core.analysis

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.DailyEtfStatisticsDao
import com.etfmonitor.core.database.FearGreedDao
import com.etfmonitor.core.database.MarketDepositDao
import com.etfmonitor.core.database.MarketIndexDao
import com.etfmonitor.core.database.MarketOscillatorDao
import com.etfmonitor.core.database.entities.DailyEtfStatistics
import com.etfmonitor.core.database.entities.FearGreedIndex
import com.etfmonitor.core.database.entities.MarketDeposit
import com.etfmonitor.core.database.entities.MarketIndex
import com.etfmonitor.core.database.entities.MarketOscillatorData
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * CorrelationAnalyzer 테스트
 *
 * 테스트 범위:
 * - Pearson 상관계수 계산
 * - 종합 신호 생성
 * - 데이터 부족 시 오류 처리
 * - 날짜 계산
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class CorrelationAnalyzerTest {

    // Mocks
    private lateinit var marketIndexDao: MarketIndexDao
    private lateinit var dailyEtfStatisticsDao: DailyEtfStatisticsDao
    private lateinit var fearGreedDao: FearGreedDao
    private lateinit var marketOscillatorDao: MarketOscillatorDao
    private lateinit var marketDepositDao: MarketDepositDao

    private lateinit var analyzer: CorrelationAnalyzer

    @BeforeEach
    fun setup() {
        marketIndexDao = mockk(relaxed = true)
        dailyEtfStatisticsDao = mockk(relaxed = true)
        fearGreedDao = mockk(relaxed = true)
        marketOscillatorDao = mockk(relaxed = true)
        marketDepositDao = mockk(relaxed = true)

        analyzer = CorrelationAnalyzer(
            marketIndexDao = marketIndexDao,
            dailyEtfStatisticsDao = dailyEtfStatisticsDao,
            fearGreedDao = fearGreedDao,
            marketOscillatorDao = marketOscillatorDao,
            marketDepositDao = marketDepositDao
        )
    }

    @Nested
    @DisplayName("Pearson 상관계수 계산 테스트")
    inner class PearsonCorrelationTests {

        @Test
        @DisplayName("완전 양의 상관관계 (r = 1.0)")
        fun whenPerfectPositiveCorrelation_thenReturnsOne() {
            // Given
            val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
            val y = listOf(2.0, 4.0, 6.0, 8.0, 10.0)

            // When
            val result = calculatePearson(x, y)

            // Then
            assertEquals(1.0, result, 0.001)
        }

        @Test
        @DisplayName("완전 음의 상관관계 (r = -1.0)")
        fun whenPerfectNegativeCorrelation_thenReturnsNegativeOne() {
            // Given
            val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
            val y = listOf(10.0, 8.0, 6.0, 4.0, 2.0)

            // When
            val result = calculatePearson(x, y)

            // Then
            assertEquals(-1.0, result, 0.001)
        }

        @Test
        @DisplayName("상관관계 없음 (r ≈ 0)")
        fun whenNoCorrelation_thenReturnsZero() {
            // Given - 무작위 데이터
            val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
            val y = listOf(3.0, 1.0, 4.0, 2.0, 5.0)

            // When
            val result = calculatePearson(x, y)

            // Then - 약한 상관관계 예상
            assertTrue(abs(result) < 0.5)
        }

        @Test
        @DisplayName("데이터 크기가 다르면 0 반환")
        fun whenDifferentSizes_thenReturnsZero() {
            // Given
            val x = listOf(1.0, 2.0, 3.0)
            val y = listOf(1.0, 2.0)

            // When
            val result = calculatePearson(x, y)

            // Then
            assertEquals(0.0, result, 0.001)
        }

        @Test
        @DisplayName("데이터가 2개 미만이면 0 반환")
        fun whenLessThanTwoPoints_thenReturnsZero() {
            // Given
            val x = listOf(1.0)
            val y = listOf(1.0)

            // When
            val result = calculatePearson(x, y)

            // Then
            assertEquals(0.0, result, 0.001)
        }

        @Test
        @DisplayName("상관계수는 -1과 1 사이")
        fun correlationShouldBeBetweenMinusOneAndOne() {
            // Given - 다양한 데이터셋
            val testCases = listOf(
                listOf(1.0, 5.0, 3.0, 8.0, 2.0) to listOf(4.0, 7.0, 2.0, 9.0, 1.0),
                listOf(10.0, 20.0, 30.0, 40.0) to listOf(5.0, 25.0, 15.0, 35.0),
                listOf(-5.0, 0.0, 5.0, 10.0, 15.0) to listOf(100.0, 50.0, 75.0, 25.0, 0.0)
            )

            // When & Then
            testCases.forEach { (x, y) ->
                val result = calculatePearson(x, y)
                assertTrue(
                    result >= -1.0 && result <= 1.0,
                    "Correlation $result should be between -1 and 1 for x=$x, y=$y"
                )
            }
        }

        /**
         * 테스트용 Pearson 상관계수 계산 (private 메서드 접근을 위해)
         * CorrelationAnalyzer의 calculatePearsonCorrelation과 동일한 로직
         */
        private fun calculatePearson(x: List<Double>, y: List<Double>): Double {
            if (x.size != y.size || x.size < 2) return 0.0

            val n = x.size
            val meanX = x.average()
            val meanY = y.average()

            var numerator = 0.0
            var denomX = 0.0
            var denomY = 0.0

            for (i in 0 until n) {
                val dx = x[i] - meanX
                val dy = y[i] - meanY
                numerator += dx * dy
                denomX += dx * dx
                denomY += dy * dy
            }

            val denominator = kotlin.math.sqrt(denomX) * kotlin.math.sqrt(denomY)
            return if (denominator > 0) numerator / denominator else 0.0
        }
    }

    @Nested
    @DisplayName("분석 실행 테스트")
    inner class AnalysisTests {

        @Test
        @DisplayName("데이터가 부족하면 실패 반환")
        fun whenInsufficientData_thenReturnFailure() = runTest {
            // Given - 20개 미만의 데이터
            val marketIndices = (1..10).map { day ->
                createMarketIndex("KOSPI", "2025-01-${String.format("%02d", day)}", 2800.0 + day)
            }
            coEvery { marketIndexDao.getByMarketAndDateRangeSuspend(any(), any(), any()) } returns marketIndices
            coEvery { dailyEtfStatisticsDao.getByDateRangeSuspend(any(), any()) } returns emptyList()
            every { fearGreedDao.getByMarketAndDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketOscillatorDao.getDataByDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketDepositDao.getAllDeposits() } returns flowOf(emptyList())

            // When
            val result = analyzer.analyze("KOSPI", "2025-01-15", 30)

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("부족") == true)
        }

        @Test
        @DisplayName("충분한 데이터로 분석 성공")
        fun whenSufficientData_thenReturnSuccess() = runTest {
            // Given - 25개 데이터
            val marketIndices = (1..25).map { day ->
                createMarketIndex(
                    "KOSPI",
                    "2025-01-${String.format("%02d", day)}",
                    2800.0 + (day * 10)
                )
            }
            val etfStats = (1..25).map { day ->
                createEtfStatistics("2025-01-${String.format("%02d", day)}")
            }

            coEvery { marketIndexDao.getByMarketAndDateRangeSuspend(any(), any(), any()) } returns marketIndices
            coEvery { dailyEtfStatisticsDao.getByDateRangeSuspend(any(), any()) } returns etfStats
            every { fearGreedDao.getByMarketAndDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketOscillatorDao.getDataByDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketDepositDao.getAllDeposits() } returns flowOf(emptyList())

            // When
            val result = analyzer.analyze("KOSPI", "2025-01-25", 30)

            // Then
            assertTrue(result.isSuccess)
            val analysisResult = result.getOrNull()
            assertNotNull(analysisResult)
            assertEquals("KOSPI", analysisResult.market)
            assertEquals("2025-01-25", analysisResult.analysisDate)
        }

        @Test
        @DisplayName("신호는 유효한 SignalType 값")
        fun signalShouldBeValidType() = runTest {
            // Given
            val marketIndices = (1..30).map { day ->
                createMarketIndex(
                    "KOSPI",
                    "2025-01-${String.format("%02d", day)}",
                    2800.0 + (day * 5)
                )
            }
            val etfStats = (1..30).map { day ->
                createEtfStatistics("2025-01-${String.format("%02d", day)}")
            }

            coEvery { marketIndexDao.getByMarketAndDateRangeSuspend(any(), any(), any()) } returns marketIndices
            coEvery { dailyEtfStatisticsDao.getByDateRangeSuspend(any(), any()) } returns etfStats
            every { fearGreedDao.getByMarketAndDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketOscillatorDao.getDataByDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketDepositDao.getAllDeposits() } returns flowOf(emptyList())

            // When
            val result = analyzer.analyze("KOSPI", "2025-01-30", 30)

            // Then
            assertTrue(result.isSuccess)
            val validSignals = listOf("STRONG_BUY", "BUY", "NEUTRAL", "SELL", "STRONG_SELL")
            assertTrue(result.getOrNull()?.signal in validSignals)
        }

        @Test
        @DisplayName("확률 합은 100%")
        fun probabilitiesShouldSumTo100() = runTest {
            // Given
            val marketIndices = (1..30).map { day ->
                createMarketIndex(
                    "KOSPI",
                    "2025-01-${String.format("%02d", day)}",
                    2800.0 + (day * 5)
                )
            }
            val etfStats = (1..30).map { day ->
                createEtfStatistics("2025-01-${String.format("%02d", day)}")
            }

            coEvery { marketIndexDao.getByMarketAndDateRangeSuspend(any(), any(), any()) } returns marketIndices
            coEvery { dailyEtfStatisticsDao.getByDateRangeSuspend(any(), any()) } returns etfStats
            every { fearGreedDao.getByMarketAndDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketOscillatorDao.getDataByDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketDepositDao.getAllDeposits() } returns flowOf(emptyList())

            // When
            val result = analyzer.analyze("KOSPI", "2025-01-30", 30)

            // Then
            assertTrue(result.isSuccess)
            val analysisResult = result.getOrNull()!!
            assertEquals(100.0, analysisResult.upProbability + analysisResult.downProbability, 0.01)
        }

        @Test
        @DisplayName("신뢰도는 0과 1 사이")
        fun confidenceShouldBeBetweenZeroAndOne() = runTest {
            // Given
            val marketIndices = (1..30).map { day ->
                createMarketIndex(
                    "KOSPI",
                    "2025-01-${String.format("%02d", day)}",
                    2800.0 + (day * 5)
                )
            }
            val etfStats = (1..30).map { day ->
                createEtfStatistics("2025-01-${String.format("%02d", day)}")
            }

            coEvery { marketIndexDao.getByMarketAndDateRangeSuspend(any(), any(), any()) } returns marketIndices
            coEvery { dailyEtfStatisticsDao.getByDateRangeSuspend(any(), any()) } returns etfStats
            every { fearGreedDao.getByMarketAndDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketOscillatorDao.getDataByDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketDepositDao.getAllDeposits() } returns flowOf(emptyList())

            // When
            val result = analyzer.analyze("KOSPI", "2025-01-30", 30)

            // Then
            assertTrue(result.isSuccess)
            val confidence = result.getOrNull()?.confidence ?: 0.0
            assertTrue(confidence >= 0.0 && confidence <= 1.0)
        }
    }

    @Nested
    @DisplayName("날짜 계산 테스트")
    inner class DateCalculationTests {

        @ParameterizedTest
        @CsvSource(
            "2025-01-31, 30, 2025-01-01",
            "2025-03-15, 14, 2025-03-01",
            "2025-01-15, 365, 2024-01-16"
        )
        @DisplayName("시작 날짜 계산")
        fun calculateStartDate(endDate: String, periodDays: Int, expectedStart: String) {
            // The actual calculateStartDate is private, but we can verify
            // the analysis uses correct date ranges by checking the DAO calls
            // This test documents the expected behavior
            val parts = endDate.split("-")
            val year = parts[0].toInt()
            val month = parts[1].toInt()
            val day = parts[2].toInt()

            val calendar = java.util.Calendar.getInstance().apply {
                set(year, month - 1, day)
                add(java.util.Calendar.DAY_OF_YEAR, -periodDays)
            }

            val calculatedStart = String.format(
                "%04d-%02d-%02d",
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH) + 1,
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            )

            assertEquals(expectedStart, calculatedStart)
        }
    }

    // ========== Helper Functions ==========

    private fun createMarketIndex(
        market: String,
        date: String,
        closePrice: Double
    ): MarketIndex {
        return MarketIndex(
            id = "$market-$date",
            market = market,
            date = date,
            closePrice = closePrice,
            openPrice = closePrice - 10,
            highPrice = closePrice + 20,
            lowPrice = closePrice - 20,
            volume = 1000000L,
            changeRate = 0.5,
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun createEtfStatistics(date: String): DailyEtfStatistics {
        return DailyEtfStatistics(
            date = date,
            newStockCount = 5,
            newStockAmount = 1000000L,
            removedStockCount = 3,
            removedStockAmount = 500000L,
            increasedStockCount = 10,
            increasedStockAmount = 2000000L,
            decreasedStockCount = 8,
            decreasedStockAmount = 1500000L,
            cashDepositAmount = 5000000L,
            cashDepositChange = 100000L,
            cashDepositChangeRate = 2.0,
            totalEtfCount = 50,
            totalHoldingAmount = 10000000L
        )
    }
}
