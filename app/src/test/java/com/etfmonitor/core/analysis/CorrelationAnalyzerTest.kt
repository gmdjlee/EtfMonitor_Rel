package com.etfmonitor.core.analysis

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.DailyEtfStatisticsDao
import com.etfmonitor.core.database.FearGreedDao
import com.etfmonitor.core.database.MarketDepositDao
import com.etfmonitor.core.database.MarketIndexDao
import com.etfmonitor.core.database.MarketOscillatorDao
import com.etfmonitor.core.database.entities.DailyEtfStatistics
import com.etfmonitor.core.database.entities.MarketIndex
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * CorrelationAnalyzer 테스트
 *
 * 테스트 범위:
 * - Pearson 상관계수 계산 (analyze()를 통한 간접 검증)
 * - 종합 신호 생성
 * - 데이터 부족 시 오류 처리
 * - 날짜 계산 (DAO 호출 인자 검증)
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
    @DisplayName("Pearson 상관계수 계산 테스트 — analyze()를 통한 검증")
    inner class PearsonCorrelationTests {

        /**
         * 지수가 교번 상승/하락하고(홀수날 큰 상승, 짝수날 작은 하락),
         * etfIncreased 도 홀수날 높고 짝수날 낮은 패턴이면 → 양의 상관관계.
         *
         * 설계 원칙:
         * - CorrelationAnalyzer 는 indexReturn[i] = (close[i]-close[i-1])/close[i-1] 를 계산
         * - ETF stats 는 prevDate(i-1) 기준으로 indexReturn[i] 와 매칭
         * - 따라서 ETF stats[day] 와 return[day+1] 이 같은 방향이어야 양의 상관관계
         */
        @Test
        @DisplayName("ETF 통계가 다음날 지수 등락과 같은 방향 → etfIncreasedCorrelation 양수")
        fun `analyze_etfStatsAlignedWithNextDayReturn_producesPositiveCorrelation`() = runTest {
            // Given:
            // 가격 패턴: day1=2800, day2=2900(+100), day3=2850(-50), day4=2980(+130), day5=2920(-60)...
            // 교번 패턴으로 returns 에 분산이 생기도록 설계
            // ETF increasedStockCount: 당일 높으면 다음날 지수도 높을 것으로 설계
            val n = 25
            // 가격: 홀수 인덱스에서 크게 상승, 짝수 인덱스에서 소폭 하락
            val prices = (1..n).map { day ->
                2800.0 + if (day % 2 == 1) day * 15.0 else day * 5.0
            }
            val marketIndices = (1..n).map { day ->
                createMarketIndex("KOSPI", "2025-01-${String.format("%02d", day)}", prices[day - 1])
            }
            // etfIncreased: prevDate(day i-1) 기준, return on day i 와 정렬
            // return[i] = prices[i] - prices[i-1] > 0 when i is odd (day 2,4,6... → i=2,4,6 → i%2==0)
            // prevDate for return[i] is date[i-1]
            // So etfStats[i-1].increasedStockCount 이 커야 return[i] 가 양수일 때
            val etfStats = (1..n).map { day ->
                // return on next day = prices[day] - prices[day-1] (if day < n)
                val nextDayPositive = if (day < n) prices[day] > prices[day - 1] else true
                createEtfStatistics(
                    date = "2025-01-${String.format("%02d", day)}",
                    increasedStockCount = if (nextDayPositive) 20 else 5,
                    decreasedStockCount = if (nextDayPositive) 5 else 20
                )
            }

            coEvery { marketIndexDao.getByMarketAndDateRangeSuspend(any(), any(), any()) } returns marketIndices
            coEvery { dailyEtfStatisticsDao.getByDateRangeSuspend(any(), any()) } returns etfStats
            every { fearGreedDao.getByMarketAndDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketOscillatorDao.getDataByDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketDepositDao.getAllDeposits() } returns flowOf(emptyList())

            // When
            val result = analyzer.analyze("KOSPI", "2025-01-25", 30)

            // Then: 분석이 성공하고 etfIncreasedCorrelation 이 양수여야 한다
            assertTrue(result.isSuccess, "충분한 데이터로 분석이 성공해야 한다")
            val analysisResult = result.getOrNull()!!
            assertTrue(
                analysisResult.etfIncreasedCorrelation > 0.0,
                "etfIncreased 가 다음날 지수 상승과 정렬되면 양의 상관관계여야 한다: ${analysisResult.etfIncreasedCorrelation}"
            )
        }

        /**
         * ETF 감소 종목 수가 많은 날 다음날 지수가 하락하고,
         * ETF 감소 종목 수가 적은 날 다음날 지수가 상승하면 → 음의 상관관계.
         */
        @Test
        @DisplayName("ETF 감소 통계가 다음날 지수 등락과 역방향 → etfDecreasedCorrelation 음수")
        fun `analyze_etfDecreasedOppositeToNextDayReturn_producesNegativeCorrelation`() = runTest {
            // Given: 교번 가격 패턴 (홀수날 상승, 짝수날 하락)
            val n = 25
            val prices = (1..n).map { day ->
                2800.0 + if (day % 2 == 1) day * 15.0 else day * 5.0
            }
            val marketIndices = (1..n).map { day ->
                createMarketIndex("KOSPI", "2025-01-${String.format("%02d", day)}", prices[day - 1])
            }
            // etfDecreased 는 다음날 return 과 반대 방향
            val etfStats = (1..n).map { day ->
                val nextDayPositive = if (day < n) prices[day] > prices[day - 1] else true
                createEtfStatistics(
                    date = "2025-01-${String.format("%02d", day)}",
                    increasedStockCount = 5,
                    decreasedStockCount = if (nextDayPositive) 5 else 20  // 다음날 상승이면 감소 종목 적음
                )
            }

            coEvery { marketIndexDao.getByMarketAndDateRangeSuspend(any(), any(), any()) } returns marketIndices
            coEvery { dailyEtfStatisticsDao.getByDateRangeSuspend(any(), any()) } returns etfStats
            every { fearGreedDao.getByMarketAndDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketOscillatorDao.getDataByDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketDepositDao.getAllDeposits() } returns flowOf(emptyList())

            // When
            val result = analyzer.analyze("KOSPI", "2025-01-25", 30)

            // Then
            assertTrue(result.isSuccess, "충분한 데이터로 분석이 성공해야 한다")
            val analysisResult = result.getOrNull()!!
            assertTrue(
                analysisResult.etfDecreasedCorrelation < 0.0,
                "ETF 감소 종목 수가 다음날 지수와 역방향이면 음의 상관관계여야 한다: ${analysisResult.etfDecreasedCorrelation}"
            )
        }

        /**
         * ETF 순유입(newStock - removedStock)이 다음날 지수 상승과 같은 방향으로 움직이면
         * etfNetFlowCorrelation 은 양수여야 한다.
         */
        @Test
        @DisplayName("ETF 순유입이 다음날 지수 상승과 같은 방향 → etfNetFlowCorrelation 양수")
        fun `analyze_netFlowAlignedWithNextDayReturn_producesPositiveNetFlowCorrelation`() = runTest {
            // Given: 교번 가격 패턴
            val n = 25
            val prices = (1..n).map { day ->
                2800.0 + if (day % 2 == 1) day * 15.0 else day * 5.0
            }
            val marketIndices = (1..n).map { day ->
                createMarketIndex("KOSPI", "2025-01-${String.format("%02d", day)}", prices[day - 1])
            }
            // netFlow = newStock - removedStock
            // 다음날 상승이면 순유입 양수(큰 값), 다음날 하락이면 순유입 음수(작은 값)
            val etfStats = (1..n).map { day ->
                val nextDayPositive = if (day < n) prices[day] > prices[day - 1] else true
                createEtfStatistics(
                    date = "2025-01-${String.format("%02d", day)}",
                    newStockCount = if (nextDayPositive) 15 else 3,
                    removedStockCount = if (nextDayPositive) 3 else 15
                )
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
            val analysisResult = result.getOrNull()!!
            assertTrue(
                analysisResult.etfNetFlowCorrelation > 0.0,
                "순유입이 다음날 지수 상승과 정렬되면 etfNetFlowCorrelation 은 양수여야 한다: ${analysisResult.etfNetFlowCorrelation}"
            )
        }

        /**
         * 지수는 상승하지만 ETF 순유입은 무작위 패턴 → 낮은 절대 상관관계 기대
         * (상관계수 절대값은 1.0 이하여야 한다는 불변 조건)
         */
        @Test
        @DisplayName("모든 상관계수는 [-1, 1] 범위여야 한다")
        fun `analyze_allCorrelations_areBetweenMinusOneAndOne`() = runTest {
            // Given: 다양한 패턴의 ETF 통계
            val n = 25
            val marketIndices = (1..n).map { day ->
                createMarketIndex("KOSPI", "2025-01-${String.format("%02d", day)}", 2800.0 + day * 3.0)
            }
            val etfStats = (1..n).map { day ->
                createEtfStatistics(
                    date = "2025-01-${String.format("%02d", day)}",
                    newStockCount = if (day % 3 == 0) day else 5,
                    removedStockCount = if (day % 2 == 0) 3 else 1,
                    increasedStockCount = day % 7,
                    decreasedStockCount = (n - day) % 5
                )
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
            val r = result.getOrNull()!!
            val correlations = listOf(
                r.etfNewStockCorrelation,
                r.etfRemovedStockCorrelation,
                r.etfIncreasedCorrelation,
                r.etfDecreasedCorrelation,
                r.etfNetFlowCorrelation,
                r.cashDepositCorrelation
            )
            correlations.forEach { corr ->
                assertTrue(
                    corr >= -1.0 && corr <= 1.0,
                    "상관계수 ${corr}는 [-1, 1] 범위여야 한다"
                )
            }
        }

        /**
         * ETF 통계 데이터가 없으면 etfNetFlowCorrelation 이 0.0 을 반환해야 한다.
         * (calculatePearsonCorrelation 에서 x.size < 2 → 0.0)
         */
        @Test
        @DisplayName("ETF 데이터 없음 → 기본 상관계수 0.0 반환")
        fun `analyze_noEtfStatistics_returnsZeroCorrelation`() = runTest {
            // Given: 지수 데이터 충분하지만 ETF 통계 없음
            val n = 25
            val marketIndices = (1..n).map { day ->
                createMarketIndex("KOSPI", "2025-01-${String.format("%02d", day)}", 2800.0 + day * 5.0)
            }

            coEvery { marketIndexDao.getByMarketAndDateRangeSuspend(any(), any(), any()) } returns marketIndices
            // ETF 통계: 지수와 날짜가 겹치지 않음 → 공통 날짜 없음 → 실패
            coEvery { dailyEtfStatisticsDao.getByDateRangeSuspend(any(), any()) } returns emptyList()
            every { fearGreedDao.getByMarketAndDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketOscillatorDao.getDataByDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketDepositDao.getAllDeposits() } returns flowOf(emptyList())

            // When
            val result = analyzer.analyze("KOSPI", "2025-01-25", 30)

            // Then: 공통 날짜가 없으면 indexReturns 비어 있어 상관계수 = 0
            // (실패 또는 성공 모두 가능 — 지수 데이터 수 기준으로 MIN_DATA_POINTS 통과)
            // 지수는 25개 → MIN_DATA_POINTS(20) 통과 → 성공
            assertTrue(result.isSuccess)
            val r = result.getOrNull()!!
            assertEquals(0.0, r.etfNetFlowCorrelation, 0.001,
                "ETF 통계와 공통 날짜가 없으면 etfNetFlowCorrelation 은 0.0 이어야 한다")
        }

        /**
         * 지수 데이터가 20개 미만이면 Failure 를 반환해야 한다.
         * MIN_DATA_POINTS = 20 경계 조건 테스트.
         */
        @Test
        @DisplayName("지수 데이터 19개(MIN_DATA_POINTS 미만) → 실패 반환")
        fun `analyze_belowMinDataPoints_returnsFailure`() = runTest {
            // Given: 19개 데이터 (MIN_DATA_POINTS=20 미만)
            val marketIndices = (1..19).map { day ->
                createMarketIndex("KOSPI", "2025-01-${String.format("%02d", day)}", 2800.0 + day)
            }

            coEvery { marketIndexDao.getByMarketAndDateRangeSuspend(any(), any(), any()) } returns marketIndices
            coEvery { dailyEtfStatisticsDao.getByDateRangeSuspend(any(), any()) } returns emptyList()
            every { fearGreedDao.getByMarketAndDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketOscillatorDao.getDataByDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketDepositDao.getAllDeposits() } returns flowOf(emptyList())

            // When
            val result = analyzer.analyze("KOSPI", "2025-01-19", 30)

            // Then
            assertTrue(result.isFailure, "19개 데이터는 MIN_DATA_POINTS(20) 미만이므로 실패해야 한다")
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

        /**
         * analyze() 가 내부적으로 calculateStartDate(endDate, periodDays) 를 호출해
         * DAO 에 올바른 startDate 를 전달하는지 DAO 인자를 캡처해서 검증한다.
         */
        @Test
        @DisplayName("30일 기간 → DAO 호출 시 startDate 가 endDate 에서 30일 이전")
        fun `analyze_30DayPeriod_passesCorrectStartDateToDao`() = runTest {
            // Given
            val endDate = "2025-01-31"
            val periodDays = 30

            val startDateSlot = slot<String>()
            coEvery {
                marketIndexDao.getByMarketAndDateRangeSuspend(any(), capture(startDateSlot), any())
            } returns emptyList()
            coEvery { dailyEtfStatisticsDao.getByDateRangeSuspend(any(), any()) } returns emptyList()
            every { fearGreedDao.getByMarketAndDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketOscillatorDao.getDataByDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketDepositDao.getAllDeposits() } returns flowOf(emptyList())

            // When
            analyzer.analyze("KOSPI", endDate, periodDays)

            // Then: startDate = "2025-01-01" (31 - 30일)
            assertEquals("2025-01-01", startDateSlot.captured,
                "30일 기간에서 2025-01-31의 startDate는 2025-01-01이어야 한다")
        }

        @Test
        @DisplayName("14일 기간 → DAO 호출 시 startDate 가 endDate 에서 14일 이전")
        fun `analyze_14DayPeriod_passesCorrectStartDateToDao`() = runTest {
            // Given
            val endDate = "2025-03-15"
            val periodDays = 14

            val startDateSlot = slot<String>()
            coEvery {
                marketIndexDao.getByMarketAndDateRangeSuspend(any(), capture(startDateSlot), any())
            } returns emptyList()
            coEvery { dailyEtfStatisticsDao.getByDateRangeSuspend(any(), any()) } returns emptyList()
            every { fearGreedDao.getByMarketAndDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketOscillatorDao.getDataByDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketDepositDao.getAllDeposits() } returns flowOf(emptyList())

            // When
            analyzer.analyze("KOSPI", endDate, periodDays)

            // Then: startDate = "2025-03-01" (15 - 14일)
            assertEquals("2025-03-01", startDateSlot.captured,
                "14일 기간에서 2025-03-15의 startDate는 2025-03-01이어야 한다")
        }

        @Test
        @DisplayName("365일 기간 → DAO 호출 시 endDate 가 그대로 전달된다")
        fun `analyze_365DayPeriod_passesCorrectEndDateToDao`() = runTest {
            // Given
            val endDate = "2025-01-15"
            val periodDays = 365

            val endDateSlot = slot<String>()
            coEvery {
                marketIndexDao.getByMarketAndDateRangeSuspend(any(), any(), capture(endDateSlot))
            } returns emptyList()
            coEvery { dailyEtfStatisticsDao.getByDateRangeSuspend(any(), any()) } returns emptyList()
            every { fearGreedDao.getByMarketAndDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketOscillatorDao.getDataByDateRange(any(), any(), any()) } returns flowOf(emptyList())
            every { marketDepositDao.getAllDeposits() } returns flowOf(emptyList())

            // When
            analyzer.analyze("KOSPI", endDate, periodDays)

            // Then: endDate 는 변환 없이 그대로 DAO 에 전달되어야 한다
            assertEquals(endDate, endDateSlot.captured,
                "endDate '${endDate}'는 그대로 DAO에 전달되어야 한다")
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

    private fun createEtfStatistics(
        date: String,
        newStockCount: Int = 5,
        removedStockCount: Int = 3,
        increasedStockCount: Int = 10,
        decreasedStockCount: Int = 8
    ): DailyEtfStatistics {
        return DailyEtfStatistics(
            date = date,
            newStockCount = newStockCount,
            newStockAmount = 1000000L,
            removedStockCount = removedStockCount,
            removedStockAmount = 500000L,
            increasedStockCount = increasedStockCount,
            increasedStockAmount = 2000000L,
            decreasedStockCount = decreasedStockCount,
            decreasedStockAmount = 1500000L,
            cashDepositAmount = 5000000L,
            cashDepositChange = 100000L,
            cashDepositChangeRate = 2.0,
            totalEtfCount = 50,
            totalHoldingAmount = 10000000L
        )
    }
}
