package com.etfmonitor.core.analysis

import com.etfmonitor.MainDispatcherExtension
import com.krxkt.KrxIndex
import com.krxkt.KrxStock
import com.krxkt.model.IndexOhlcv
import com.krxkt.model.Market
import com.krxkt.model.MarketOhlcv
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MarketOscillatorCalculator 단위 테스트 (pykrx 알고리즘)
 *
 * 테스트 범위:
 * - 공식 검증: vol+pts 가중 + 비선형 변환
 * - 구성종목 필터링 (KOSPI200/KOSDAQ150)
 * - 올바른 인덱스 티커 호출
 * - 빈 구성종목 → null 반환
 * - 출력 범위: [-100,-50] ∪ (50,100] (중간대 없음)
 * - CancellationException 전파
 * - 개별 날짜 실패 시 skip & continue
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("MarketOscillatorCalculator 테스트 (pykrx)")
class MarketOscillatorCalculatorTest {

    private lateinit var krxIndex: KrxIndex
    private lateinit var krxStock: KrxStock
    private lateinit var calculator: MarketOscillatorCalculator

    private val startDate = "20260101"
    private val endDate = "20260110"

    // KOSPI200 구성종목 (테스트용 3종목)
    private val kospi200Tickers = listOf("005930", "000660", "035420")

    @BeforeEach
    fun setUp() {
        krxIndex = mockk()
        krxStock = mockk()
        calculator = MarketOscillatorCalculator(krxIndex, krxStock)
    }

    // ============================================================
    // 헬퍼 함수
    // ============================================================

    private fun makeIndexOhlcv(date: String, close: Double = 2800.0): IndexOhlcv =
        IndexOhlcv(
            date = date,
            open = close - 10.0,
            high = close + 20.0,
            low = close - 20.0,
            close = close,
            volume = 100_000L,
            tradingValue = 5_000_000L,
            changeType = 1,
            change = 5.0
        )

    /**
     * MarketOhlcv 테스트 픽스처 생성
     *
     * @param tickerRates ticker to (changeRate, volume) 맵
     */
    private fun makeMarketOhlcvList(tickerRates: List<Triple<String, Double, Long>>): List<MarketOhlcv> =
        tickerRates.map { (ticker, rate, volume) ->
            MarketOhlcv(
                ticker = ticker,
                name = "종목$ticker",
                open = 50000L,
                high = 51000L,
                low = 49000L,
                close = 50000L,
                volume = volume,
                tradingValue = 5_000_000_000L,
                changeRate = rate
            )
        }

    /**
     * 간단한 MarketOhlcv 리스트 (구성종목만, 동일 거래량)
     */
    private fun makeComponentOhlcv(changeRates: List<Double>, volume: Long = 100_000L): List<MarketOhlcv> =
        changeRates.mapIndexed { idx, rate ->
            MarketOhlcv(
                ticker = kospi200Tickers.getOrElse(idx) { String.format("%06d", idx + 1) },
                name = "종목${idx + 1}",
                open = 50000L,
                high = 51000L,
                low = 49000L,
                close = 50000L,
                volume = volume,
                tradingValue = 5_000_000_000L,
                changeRate = rate
            )
        }

    /**
     * 기본 mock 설정: 지수 데이터 + 구성종목
     */
    private fun setupBasicMocks(
        market: String,
        dates: List<String>,
        componentTickers: List<String> = kospi200Tickers
    ) {
        val indexData = dates.mapIndexed { i, d -> makeIndexOhlcv(d, 2800.0 + i * 10) }

        if (market.uppercase() == "KOSPI") {
            coEvery { krxIndex.getKospi(startDate, endDate) } returns indexData
        } else {
            coEvery { krxIndex.getKosdaq(startDate, endDate) } returns indexData
        }

        // 구성종목 반환 (최신 거래일 기준)
        val latestDate = dates.last()
        val indexTicker = if (market.uppercase() == "KOSPI") "1028" else "2203"
        coEvery { krxIndex.getIndexPortfolioTickers(latestDate, indexTicker) } returns componentTickers
    }

    // ============================================================
    // FormulaTests: pykrx _calc() 공식 검증
    // ============================================================

    @Nested
    @DisplayName("공식 검증 — vol+pts 가중 + 비선형 변환")
    inner class FormulaTests {

        @Test
        @DisplayName("전종목 상승: volRatio=1, ptsRatio=1, avg=1 → oscillator=100")
        fun `allUp_oscillator100`() = runTest {
            val dates = listOf("20260101")
            setupBasicMocks("KOSPI", dates)

            // 3 구성종목 모두 상승
            val ohlcv = makeComponentOhlcv(listOf(2.0, 1.5, 3.0))
            coEvery { krxStock.getMarketOhlcv("20260101", Market.KOSPI) } returns ohlcv

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result)
            assertEquals(100.0, result.oscillator[0], 0.001)
        }

        @Test
        @DisplayName("전종목 하락: volRatio=0, ptsRatio=0, avg=0 → oscillator=-100")
        fun `allDown_oscillatorMinus100`() = runTest {
            val dates = listOf("20260101")
            setupBasicMocks("KOSPI", dates)

            val ohlcv = makeComponentOhlcv(listOf(-2.0, -1.5, -3.0))
            coEvery { krxStock.getMarketOhlcv("20260101", Market.KOSPI) } returns ohlcv

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result)
            // avg = (0 + 0) / 2 = 0 → avg - 1.0 = -1.0 → ×100 = -100
            assertEquals(-100.0, result.oscillator[0], 0.001)
        }

        @Test
        @DisplayName("50:50 상승/하락 (동일 거래량, 동일 등락률): avg=0.5 → oscillator=-50")
        fun `halfAndHalf_oscillatorMinus50`() = runTest {
            val dates = listOf("20260101")
            setupBasicMocks("KOSPI", dates, listOf("005930", "000660"))

            // 1 상승 +2%, 1 하락 -2% (동일 거래량 100K, 동일 절대값)
            val ohlcv = makeComponentOhlcv(listOf(2.0, -2.0))
            coEvery { krxStock.getMarketOhlcv("20260101", Market.KOSPI) } returns ohlcv

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result)
            // volRatio = 100K / 200K = 0.5
            // ptsRatio = 2.0 / 4.0 = 0.5
            // avg = 0.5 → NOT > 0.5 → avg - 1.0 = -0.5 → ×100 = -50
            assertEquals(-50.0, result.oscillator[0], 0.001)
        }

        @Test
        @DisplayName("비대칭 거래량: 상승 거래량 비중이 높으면 oscillator 양수 영역")
        fun `asymmetricVolume_oscillatorPositive`() = runTest {
            val dates = listOf("20260101")
            setupBasicMocks("KOSPI", dates, listOf("005930", "000660"))

            // 005930: 상승 +2%, volume=300K
            // 000660: 하락 -2%, volume=100K
            val ohlcv = listOf(
                Triple("005930", 2.0, 300_000L),
                Triple("000660", -2.0, 100_000L)
            ).let { makeMarketOhlcvList(it) }
            coEvery { krxStock.getMarketOhlcv("20260101", Market.KOSPI) } returns ohlcv

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result)
            // volRatio = 300K / 400K = 0.75
            // ptsRatio = 2.0 / 4.0 = 0.5
            // avg = (0.75 + 0.5) / 2 = 0.625 > 0.5 → oscillator = 0.625 × 100 = 62.5
            assertEquals(62.5, result.oscillator[0], 0.001)
        }

        @Test
        @DisplayName("보합 종목만: totalVol=0, totalPts=0 → 기본값 0.5/0.5 → avg=0.5 → -50")
        fun `allFlat_oscillatorMinus50`() = runTest {
            val dates = listOf("20260101")
            setupBasicMocks("KOSPI", dates)

            val ohlcv = makeComponentOhlcv(listOf(0.0, 0.0, 0.0))
            coEvery { krxStock.getMarketOhlcv("20260101", Market.KOSPI) } returns ohlcv

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result)
            // 보합만: upVol=0, downVol=0, gained=0, lost=0
            // volRatio = 0.5 (default), ptsRatio = 0.5 (default)
            // avg = 0.5 → NOT > 0.5 → -0.5 × 100 = -50
            assertEquals(-50.0, result.oscillator[0], 0.001)
        }
    }

    // ============================================================
    // ComponentFilterTests: 구성종목 필터링 검증
    // ============================================================

    @Nested
    @DisplayName("구성종목 필터링 검증")
    inner class ComponentFilterTests {

        @Test
        @DisplayName("비구성종목은 oscillator 계산에서 제외된다")
        fun `nonComponentStocks_areExcluded`() = runTest {
            val dates = listOf("20260101")
            // 구성종목은 005930만
            setupBasicMocks("KOSPI", dates, listOf("005930"))

            // 005930(구성): 상승 +3%, 999999(비구성): 하락 -5%
            val ohlcv = listOf(
                Triple("005930", 3.0, 100_000L),
                Triple("999999", -5.0, 500_000L)  // 비구성종목 — 무시해야 함
            ).let { makeMarketOhlcvList(it) }
            coEvery { krxStock.getMarketOhlcv("20260101", Market.KOSPI) } returns ohlcv

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result)
            // 구성종목(005930)만: 상승 100%
            // volRatio = 1.0, ptsRatio = 1.0, avg = 1.0 → oscillator = 100
            assertEquals(100.0, result.oscillator[0], 0.001)
        }

        @Test
        @DisplayName("구성종목이 시장 데이터에 없으면 해당 날짜 skip")
        fun `noComponentsInMarketData_dateSkipped`() = runTest {
            val dates = listOf("20260101")
            setupBasicMocks("KOSPI", dates, listOf("005930"))

            // 시장 데이터에 구성종목이 없음
            val ohlcv = listOf(
                Triple("999999", 3.0, 100_000L)
            ).let { makeMarketOhlcvList(it) }
            coEvery { krxStock.getMarketOhlcv("20260101", Market.KOSPI) } returns ohlcv

            val result = calculator.analyze("KOSPI", startDate, endDate)

            // 유효 데이터 없음 → null
            assertNull(result)
        }
    }

    // ============================================================
    // IndexTickerTests: 올바른 인덱스 티커 호출 확인
    // ============================================================

    @Nested
    @DisplayName("인덱스 티커 라우팅 검증")
    inner class IndexTickerTests {

        @Test
        @DisplayName("KOSPI → getIndexPortfolioTickers(date, '1028') 호출")
        fun `kospi_usesTickerKospi200`() = runTest {
            val dates = listOf("20260101")
            setupBasicMocks("KOSPI", dates)
            coEvery { krxStock.getMarketOhlcv(any(), Market.KOSPI) } returns makeComponentOhlcv(listOf(1.0, -1.0, 2.0))

            calculator.analyze("KOSPI", startDate, endDate)

            coVerify(exactly = 1) { krxIndex.getIndexPortfolioTickers("20260101", "1028") }
        }

        @Test
        @DisplayName("KOSDAQ → getIndexPortfolioTickers(date, '2203') 호출")
        fun `kosdaq_usesTickerKosdaq150`() = runTest {
            val dates = listOf("20260101")
            setupBasicMocks("KOSDAQ", dates)
            coEvery { krxStock.getMarketOhlcv(any(), Market.KOSDAQ) } returns makeComponentOhlcv(listOf(1.0, -1.0, 2.0))

            calculator.analyze("KOSDAQ", startDate, endDate)

            coVerify(exactly = 1) { krxIndex.getIndexPortfolioTickers("20260101", "2203") }
        }

        @Test
        @DisplayName("KOSPI 시장은 krxIndex.getKospi()로 라우팅된다")
        fun `kospiMarket_routesToGetKospi`() = runTest {
            val dates = listOf("20260101")
            setupBasicMocks("KOSPI", dates)
            coEvery { krxStock.getMarketOhlcv(any(), Market.KOSPI) } returns makeComponentOhlcv(listOf(1.0))

            calculator.analyze("KOSPI", startDate, endDate)

            coVerify(exactly = 1) { krxIndex.getKospi(startDate, endDate) }
            coVerify(exactly = 0) { krxIndex.getKosdaq(any(), any()) }
        }

        @Test
        @DisplayName("KOSDAQ 시장은 krxIndex.getKosdaq()으로 라우팅된다")
        fun `kosdaqMarket_routesToGetKosdaq`() = runTest {
            val dates = listOf("20260101")
            setupBasicMocks("KOSDAQ", dates)
            coEvery { krxStock.getMarketOhlcv(any(), Market.KOSDAQ) } returns makeComponentOhlcv(listOf(1.0))

            calculator.analyze("KOSDAQ", startDate, endDate)

            coVerify(exactly = 1) { krxIndex.getKosdaq(startDate, endDate) }
            coVerify(exactly = 0) { krxIndex.getKospi(any(), any()) }
        }
    }

    // ============================================================
    // EmptyDataTests: 빈 데이터 처리
    // ============================================================

    @Nested
    @DisplayName("빈 데이터 처리")
    inner class EmptyDataTests {

        @Test
        @DisplayName("빈 지수 데이터는 null을 반환한다")
        fun `emptyIndexData_returnsNull`() = runTest {
            coEvery { krxIndex.getKospi(startDate, endDate) } returns emptyList()

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNull(result)
        }

        @Test
        @DisplayName("빈 구성종목 → null 반환")
        fun `emptyComponentSet_returnsNull`() = runTest {
            val dates = listOf("20260101")
            val indexData = dates.map { makeIndexOhlcv(it) }
            coEvery { krxIndex.getKospi(startDate, endDate) } returns indexData
            coEvery { krxIndex.getIndexPortfolioTickers("20260101", "1028") } returns emptyList()

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNull(result)
        }

        @Test
        @DisplayName("getMarketOhlcv가 모든 날짜에서 빈 목록 → null 반환")
        fun `allEmptyMarketOhlcv_returnsNull`() = runTest {
            val dates = listOf("20260101", "20260102")
            setupBasicMocks("KOSPI", dates)
            coEvery { krxStock.getMarketOhlcv(any(), Market.KOSPI) } returns emptyList()

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNull(result)
        }
    }

    // ============================================================
    // OutputRangeTests: 출력 범위 검증
    // ============================================================

    @Nested
    @DisplayName("출력 범위 검증")
    inner class OutputRangeTests {

        @Test
        @DisplayName("모든 oscillator 값이 [-100,-50] 또는 (50,100] 범위이다 (중간대 없음)")
        fun `oscillatorValues_noMiddleGap`() = runTest {
            val dates = listOf("20260101", "20260102", "20260105")
            setupBasicMocks("KOSPI", dates)

            coEvery {
                krxStock.getMarketOhlcv("20260101", Market.KOSPI)
            } returns makeComponentOhlcv(listOf(1.0, -1.0, 2.0))  // 2상승1하락

            coEvery {
                krxStock.getMarketOhlcv("20260102", Market.KOSPI)
            } returns makeComponentOhlcv(listOf(-1.0, -2.0, -0.5))  // 전하락

            coEvery {
                krxStock.getMarketOhlcv("20260105", Market.KOSPI)
            } returns makeComponentOhlcv(listOf(3.0, 2.0, 1.0))  // 전상승

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result)
            result.oscillator.forEachIndexed { i, value ->
                val inValidRange = value <= -50.0 || value > 50.0
                assertTrue(
                    inValidRange,
                    "인덱스 ${i}의 oscillator($value)는 [-100,-50] 또는 (50,100] 범위여야 한다"
                )
            }
        }
    }

    // ============================================================
    // CancellationExceptionTests: CE 전파 검증
    // ============================================================

    @Nested
    @DisplayName("CancellationException 전파 검증")
    inner class CancellationExceptionTests {

        @Test
        @DisplayName("getMarketOhlcv에서 CE 발생 시 rethrow")
        fun `getMarketOhlcv_CE_rethrows`() = runTest {
            val dates = listOf("20260101")
            setupBasicMocks("KOSPI", dates)
            coEvery {
                krxStock.getMarketOhlcv("20260101", Market.KOSPI)
            } throws CancellationException("코루틴 취소됨")

            assertThrows<CancellationException> {
                calculator.analyze("KOSPI", startDate, endDate)
            }
        }

        @Test
        @DisplayName("getIndexPortfolioTickers에서 CE 발생 시 rethrow")
        fun `getIndexPortfolioTickers_CE_rethrows`() = runTest {
            val dates = listOf("20260101")
            val indexData = dates.map { makeIndexOhlcv(it) }
            coEvery { krxIndex.getKospi(startDate, endDate) } returns indexData
            coEvery {
                krxIndex.getIndexPortfolioTickers("20260101", "1028")
            } throws CancellationException("코루틴 취소됨")

            assertThrows<CancellationException> {
                calculator.analyze("KOSPI", startDate, endDate)
            }
        }

        @Test
        @DisplayName("getKospi에서 CE 발생 시 rethrow")
        fun `getKospi_CE_rethrows`() = runTest {
            coEvery {
                krxIndex.getKospi(startDate, endDate)
            } throws CancellationException("코루틴 취소됨")

            assertThrows<CancellationException> {
                calculator.analyze("KOSPI", startDate, endDate)
            }
        }
    }

    // ============================================================
    // ErrorRecoveryTests: 개별 날짜 실패 및 복구
    // ============================================================

    @Nested
    @DisplayName("개별 날짜 실패 및 복구")
    inner class ErrorRecoveryTests {

        @Test
        @DisplayName("개별 날짜 실패 시 해당 날짜는 건너뛰고 계속 진행한다")
        fun `individualDateFails_continuesWithOtherDates`() = runTest {
            val dates = listOf("20260101", "20260102", "20260105")
            setupBasicMocks("KOSPI", dates)

            // 첫 날짜 실패
            coEvery {
                krxStock.getMarketOhlcv("20260101", Market.KOSPI)
            } throws RuntimeException("일시적 오류")

            // 나머지 날짜 성공
            coEvery {
                krxStock.getMarketOhlcv("20260102", Market.KOSPI)
            } returns makeComponentOhlcv(listOf(1.0, 2.0, -1.0))

            coEvery {
                krxStock.getMarketOhlcv("20260105", Market.KOSPI)
            } returns makeComponentOhlcv(listOf(-1.0, -2.0, 3.0))

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result, "개별 날짜 실패 시에도 나머지 날짜로 계산해야 한다")
            assertEquals(2, result.dates.size, "실패한 날짜를 제외한 2일 데이터만 포함")
        }

        @Test
        @DisplayName("KrxIndex가 예외를 던지면 null을 반환한다")
        fun `krxIndexThrowsException_returnsNull`() = runTest {
            coEvery { krxIndex.getKospi(startDate, endDate) } throws RuntimeException("네트워크 오류")

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNull(result)
        }
    }

    // ============================================================
    // StatsTests: OscillatorStats 검증
    // ============================================================

    @Nested
    @DisplayName("OscillatorStats 검증")
    inner class StatsTests {

        @Test
        @DisplayName("OscillatorStats의 mean, max, min, latest 필드가 올바르게 계산된다")
        fun `oscillatorStats_areCorrect`() = runTest {
            val dates = listOf("20260101", "20260102", "20260105")
            setupBasicMocks("KOSPI", dates)

            coEvery {
                krxStock.getMarketOhlcv("20260101", Market.KOSPI)
            } returns makeComponentOhlcv(listOf(1.0, 2.0, 3.0))  // 전상승

            coEvery {
                krxStock.getMarketOhlcv("20260102", Market.KOSPI)
            } returns makeComponentOhlcv(listOf(-1.0, -2.0, -3.0))  // 전하락

            coEvery {
                krxStock.getMarketOhlcv("20260105", Market.KOSPI)
            } returns makeComponentOhlcv(listOf(1.0, -1.0, 2.0))  // 2상승1하락

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result)
            val osc = result.oscillator
            val stats = result.stats

            assertEquals(osc.average(), stats.mean, 0.001, "mean이 올바르지 않다")
            assertEquals(osc.max(), stats.max, 0.001, "max가 올바르지 않다")
            assertEquals(osc.min(), stats.min, 0.001, "min이 올바르지 않다")
            assertEquals(osc.last(), stats.latest, 0.001, "latest가 올바르지 않다")
        }
    }

    // ============================================================
    // ResultFieldTests: 결과 필드 검증
    // ============================================================

    @Nested
    @DisplayName("결과 필드 검증")
    inner class ResultFieldTests {

        @Test
        @DisplayName("성공적인 분석은 올바른 필드를 가진 OscillatorResult를 반환한다")
        fun `validData_returnsCorrectFields`() = runTest {
            val dates = listOf("20260101", "20260102")
            setupBasicMocks("KOSPI", dates)
            coEvery { krxStock.getMarketOhlcv(any(), Market.KOSPI) } returns makeComponentOhlcv(listOf(2.0, 1.5, -0.5))

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result)
            assertEquals("KOSPI", result.market)
            assertEquals(dates.size, result.dates.size)
            assertEquals(dates.size, result.indexValues.size)
            assertEquals(dates.size, result.oscillator.size)
            assertNotNull(result.stats)
        }

        @Test
        @DisplayName("결과의 indexValues는 유효 날짜에 해당하는 지수 종가를 담는다")
        fun `resultIndexValues_matchValidDates`() = runTest {
            val dates = listOf("20260101", "20260102")
            val closePrices = listOf(2800.0, 2810.0)
            val indexData = dates.zip(closePrices).map { (d, c) -> makeIndexOhlcv(d, c) }
            coEvery { krxIndex.getKospi(startDate, endDate) } returns indexData
            coEvery { krxIndex.getIndexPortfolioTickers("20260102", "1028") } returns kospi200Tickers
            coEvery { krxStock.getMarketOhlcv(any(), Market.KOSPI) } returns makeComponentOhlcv(listOf(1.0, -1.0, 2.0))

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result)
            assertEquals(2800.0, result.indexValues[0], 0.001)
            assertEquals(2810.0, result.indexValues[1], 0.001)
        }

        @Test
        @DisplayName("결과 날짜 목록은 유효한 데이터가 있는 날짜만 포함한다")
        fun `resultDates_onlyContainValidDates`() = runTest {
            val dates = listOf("20260101", "20260102", "20260105")
            setupBasicMocks("KOSPI", dates)

            coEvery {
                krxStock.getMarketOhlcv("20260101", Market.KOSPI)
            } returns makeComponentOhlcv(listOf(1.0, -1.0, 2.0))

            coEvery {
                krxStock.getMarketOhlcv("20260102", Market.KOSPI)
            } returns emptyList()  // 빈 목록 → skip

            coEvery {
                krxStock.getMarketOhlcv("20260105", Market.KOSPI)
            } returns makeComponentOhlcv(listOf(2.0, -2.0, 1.0))

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result)
            assertEquals(listOf("20260101", "20260105"), result.dates)
        }
    }
}
