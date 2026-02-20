package com.etfmonitor.core.analysis

import com.etfmonitor.MainDispatcherExtension
import com.krxkt.KrxIndex
import com.krxkt.KrxStock
import com.krxkt.model.IndexOhlcv
import com.krxkt.model.Market
import com.krxkt.model.MarketCap
import com.krxkt.model.StockOhlcvHistory
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
 * MarketOscillatorCalculator 단위 테스트
 *
 * 테스트 범위:
 * - analyze: KOSPI/KOSDAQ 라우팅, 성공 경로, 빈 데이터, 예외 처리
 * - calculateOscillator: analyze()를 통해 간접 검증 (all-up, all-down, stats)
 * - 엣지 케이스: zero components, CancellationException 전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("MarketOscillatorCalculator 테스트")
class MarketOscillatorCalculatorTest {

    private lateinit var krxIndex: KrxIndex
    private lateinit var krxStock: KrxStock
    private lateinit var calculator: MarketOscillatorCalculator

    // 테스트용 날짜 상수 (yyyyMMdd 형식)
    private val startDate = "20260101"
    private val endDate = "20260105"

    @BeforeEach
    fun setUp() {
        krxIndex = mockk()
        krxStock = mockk()
        calculator = MarketOscillatorCalculator(krxIndex, krxStock)
    }

    // ============================================================
    // 헬퍼 함수
    // ============================================================

    /**
     * IndexOhlcv 테스트 픽스처 생성 (yyyyMMdd 날짜 형식)
     */
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
     * MarketCap 테스트 픽스처 생성
     */
    private fun makeMarketCap(ticker: String, cap: Long = 1_000_000_000_000L): MarketCap =
        MarketCap(
            ticker = ticker,
            name = "테스트종목${ticker}",
            close = 50_000L,
            changeRate = 0.5,
            marketCap = cap,
            sharesOutstanding = 20_000_000L
        )

    /**
     * StockOhlcvHistory 테스트 픽스처 생성 (yyyyMMdd 날짜 형식)
     *
     * @param date 날짜 (yyyyMMdd)
     * @param close 종가 (Long)
     */
    private fun makeStockOhlcv(date: String, close: Long): StockOhlcvHistory =
        StockOhlcvHistory(
            date = date,
            open = close - 100L,
            high = close + 200L,
            low = close - 200L,
            close = close,
            volume = 500_000L,
            tradingValue = 25_000_000_000L,
            changeRate = 1.0
        )

    /**
     * 주어진 날짜 목록에 대해 지수/시가총액/OHLCV 목을 설정한다.
     *
     * @param market "KOSPI" 또는 "KOSDAQ"
     * @param dates yyyyMMdd 날짜 목록 (지수 날짜)
     * @param tickers 시가총액 상위 종목 코드 목록
     * @param closePrices 각 티커별 날짜별 종가 목록 (tickers × dates 크기)
     */
    private fun setupFullMocks(
        market: String,
        dates: List<String>,
        tickers: List<String>,
        closePrices: Map<String, List<Long>>
    ) {
        val indexData = dates.mapIndexed { i, d -> makeIndexOhlcv(d, 2800.0 + i * 10) }
        val latestDate = dates.last()
        val marketEnum = if (market.uppercase() == "KOSPI") Market.KOSPI else Market.KOSDAQ

        if (market.uppercase() == "KOSPI") {
            coEvery { krxIndex.getKospi(startDate, endDate) } returns indexData
        } else {
            coEvery { krxIndex.getKosdaq(startDate, endDate) } returns indexData
        }

        coEvery { krxStock.getMarketCap(latestDate, marketEnum) } returns
                tickers.mapIndexed { i, t -> makeMarketCap(t, 1_000_000_000_000L - i * 1_000_000L) }

        for (ticker in tickers) {
            val prices = closePrices[ticker] ?: dates.map { 50_000L }
            coEvery { krxStock.getOhlcvByTicker(startDate, endDate, ticker) } returns
                    dates.zip(prices).map { (d, p) -> makeStockOhlcv(d, p) }
        }
    }

    // ============================================================
    // AnalyzeTests: analyze() 메서드 라우팅 및 성공/실패 경로
    // ============================================================

    @Nested
    @DisplayName("analyze — 분석 실행 테스트")
    inner class AnalyzeTests {

        @Test
        @DisplayName("성공적인 분석은 올바른 필드를 가진 OscillatorResult를 반환한다")
        fun `analyze_withValidData_returnsOscillatorResultWithCorrectFields`() = runTest {
            val dates = listOf("20260101", "20260102", "20260105")
            val tickers = listOf("005930", "000660")
            val prices = mapOf(
                "005930" to listOf(50_000L, 51_000L, 52_000L),
                "000660" to listOf(80_000L, 81_000L, 82_000L)
            )
            setupFullMocks("KOSPI", dates, tickers, prices)

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result)
            assertEquals("KOSPI", result.market)
            assertEquals(dates.size, result.dates.size)
            assertEquals(dates.size, result.indexValues.size)
            assertEquals(dates.size, result.oscillator.size)
            assertNotNull(result.stats)
        }

        @Test
        @DisplayName("빈 지수 데이터는 null을 반환한다")
        fun `analyze_withEmptyIndexData_returnsNull`() = runTest {
            coEvery { krxIndex.getKospi(startDate, endDate) } returns emptyList()

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNull(result)
        }

        @Test
        @DisplayName("KrxIndex가 예외를 던지면 null을 반환한다")
        fun `analyze_withKrxIndexThrowingException_returnsNull`() = runTest {
            coEvery { krxIndex.getKospi(startDate, endDate) } throws RuntimeException("네트워크 오류")

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNull(result)
        }

        @Test
        @DisplayName("KOSPI 시장은 krxIndex.getKospi()로 라우팅된다")
        fun `analyze_kospiMarket_routesToGetKospi`() = runTest {
            val dates = listOf("20260101", "20260102")
            setupFullMocks("KOSPI", dates, listOf("005930"),
                mapOf("005930" to listOf(50_000L, 51_000L)))

            calculator.analyze("KOSPI", startDate, endDate)

            coVerify(exactly = 1) { krxIndex.getKospi(startDate, endDate) }
            coVerify(exactly = 0) { krxIndex.getKosdaq(any(), any()) }
        }

        @Test
        @DisplayName("KOSDAQ 시장은 krxIndex.getKosdaq()으로 라우팅된다")
        fun `analyze_kosdaqMarket_routesToGetKosdaq`() = runTest {
            val dates = listOf("20260101", "20260102")
            setupFullMocks("KOSDAQ", dates, listOf("247540"),
                mapOf("247540" to listOf(15_000L, 15_500L)))

            calculator.analyze("KOSDAQ", startDate, endDate)

            coVerify(exactly = 1) { krxIndex.getKosdaq(startDate, endDate) }
            coVerify(exactly = 0) { krxIndex.getKospi(any(), any()) }
        }
    }

    // ============================================================
    // OscillatorCalculationTests: 오실레이터 계산 로직 검증
    // ============================================================

    @Nested
    @DisplayName("calculateOscillator — 오실레이터 계산 (analyze() 통해 간접 검증)")
    inner class OscillatorCalculationTests {

        @Test
        @DisplayName("모든 종목이 상승하면 oscillator는 양수이다")
        fun `analyze_allStocksUp_oscillatorIsPositive`() = runTest {
            // 3일 데이터: 1일차 → 2일차 상승, 2일차 → 3일차 상승
            val dates = listOf("20260101", "20260102", "20260105")
            val tickers = listOf("005930", "000660", "035420")
            // 모든 종목 단조 상승
            val prices = mapOf(
                "005930" to listOf(50_000L, 55_000L, 60_000L),
                "000660" to listOf(80_000L, 85_000L, 90_000L),
                "035420" to listOf(70_000L, 75_000L, 80_000L)
            )
            setupFullMocks("KOSPI", dates, tickers, prices)

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result)
            // 인덱스 0은 항상 0.0 (첫날 변화율 계산 불가)
            assertEquals(0.0, result.oscillator[0], 0.001)
            // 인덱스 1, 2는 양수여야 함 (모든 종목 상승 → upVolume > 0, gainedPoints > 0)
            for (i in 1 until result.oscillator.size) {
                assertTrue(
                    result.oscillator[i] > 0.0,
                    "인덱스 ${i}의 oscillator는 양수여야 한다. 실제: ${result.oscillator[i]}"
                )
            }
        }

        @Test
        @DisplayName("모든 종목이 하락하면 oscillator는 음수이다")
        fun `analyze_allStocksDown_oscillatorIsNegative`() = runTest {
            // 3일 데이터: 모든 종목 단조 하락
            val dates = listOf("20260101", "20260102", "20260105")
            val tickers = listOf("005930", "000660")
            val prices = mapOf(
                "005930" to listOf(60_000L, 55_000L, 50_000L),
                "000660" to listOf(90_000L, 85_000L, 80_000L)
            )
            setupFullMocks("KOSPI", dates, tickers, prices)

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result)
            // 인덱스 0은 항상 0.0
            assertEquals(0.0, result.oscillator[0], 0.001)
            // 인덱스 1, 2는 음수여야 함 (모든 종목 하락 → upVolume = 0, gainedPoints = 0)
            for (i in 1 until result.oscillator.size) {
                assertTrue(
                    result.oscillator[i] < 0.0,
                    "인덱스 ${i}의 oscillator는 음수여야 한다. 실제: ${result.oscillator[i]}"
                )
            }
        }

        @Test
        @DisplayName("OscillatorStats의 mean, max, min, latest 필드가 올바르게 계산된다")
        fun `analyze_withValidData_oscillatorStatsAreCorrect`() = runTest {
            val dates = listOf("20260101", "20260102", "20260105")
            val tickers = listOf("005930")
            // 상승 → 하락 시나리오: i=1 상승, i=2 하락
            val prices = mapOf(
                "005930" to listOf(50_000L, 55_000L, 52_000L)
            )
            setupFullMocks("KOSPI", dates, tickers, prices)

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result)
            val osc = result.oscillator
            val stats = result.stats

            // mean = 전체 평균
            val expectedMean = osc.average()
            assertEquals(expectedMean, stats.mean, 0.001, "mean이 올바르지 않다")

            // max = 최대값
            val expectedMax = osc.maxOrNull()!!
            assertEquals(expectedMax, stats.max, 0.001, "max가 올바르지 않다")

            // min = 최소값
            val expectedMin = osc.minOrNull()!!
            assertEquals(expectedMin, stats.min, 0.001, "min이 올바르지 않다")

            // latest = 마지막값
            val expectedLatest = osc.last()
            assertEquals(expectedLatest, stats.latest, 0.001, "latest가 올바르지 않다")
        }

        @Test
        @DisplayName("첫 번째 oscillator 값은 항상 0.0이다 (이전 데이터 없음)")
        fun `analyze_firstOscillatorValue_isAlwaysZero`() = runTest {
            val dates = listOf("20260101", "20260102", "20260105")
            val tickers = listOf("005930", "000660")
            val prices = mapOf(
                "005930" to listOf(50_000L, 55_000L, 60_000L),
                "000660" to listOf(80_000L, 85_000L, 90_000L)
            )
            setupFullMocks("KOSPI", dates, tickers, prices)

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result)
            assertEquals(
                0.0, result.oscillator[0], 0.0,
                "첫 번째 oscillator 값은 항상 0.0이어야 한다"
            )
        }

        @Test
        @DisplayName("oscillator는 백분율로 변환된다 — 결과값에 100 곱해진 형태")
        fun `analyze_oscillatorIsScaledByHundred`() = runTest {
            // 모든 종목 상승: volumeRatio=1.0, pointRatio=1.0 → avg=1.0
            // avg > 0.5 이므로 oscillator = avg = 1.0, pct = 100.0
            val dates = listOf("20260101", "20260102")
            val tickers = listOf("005930")
            val prices = mapOf("005930" to listOf(50_000L, 60_000L))
            setupFullMocks("KOSPI", dates, tickers, prices)

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result)
            // 날짜가 2개이므로 oscillator 크기는 2
            assertEquals(2, result.oscillator.size)
            // 인덱스 1: 단일 종목 100% 상승 → volumeRatio=1.0, pointRatio=1.0
            // avg=1.0 > 0.5 → oscillator=1.0 → pct=100.0
            assertEquals(100.0, result.oscillator[1], 0.001,
                "단일 종목 상승에서 oscillator는 100.0이어야 한다")
        }
    }

    // ============================================================
    // EdgeCaseTests: 경계 조건 및 특수 케이스
    // ============================================================

    @Nested
    @DisplayName("경계 조건 및 특수 케이스")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("시가총액 조회가 빈 목록을 반환하면 null을 반환한다")
        fun `analyze_zeroComponents_returnsNull`() = runTest {
            val dates = listOf("20260101", "20260102")
            val indexData = dates.mapIndexed { i, d -> makeIndexOhlcv(d, 2800.0 + i * 10) }
            coEvery { krxIndex.getKospi(startDate, endDate) } returns indexData
            // 시가총액 조회가 빈 목록 → tickers 비어있음 → component 없음
            coEvery { krxStock.getMarketCap(dates.last(), Market.KOSPI) } returns emptyList()

            val result = calculator.analyze("KOSPI", startDate, endDate)

            // 구성종목 없음 → closePrices/volumes 비어있음 → calculateOscillator 빈 리스트 → null
            assertNull(result)
        }

        @Test
        @DisplayName("KrxStock.getOhlcvByTicker에서 CancellationException이 발생하면 재throw한다")
        fun `analyze_cancellationExceptionFromKrxStock_rethrows`() = runTest {
            // CE 가드가 적용되어 CancellationException은 catch되지 않고 재throw된다.
            val dates = listOf("20260101", "20260102")
            val indexData = dates.mapIndexed { i, d -> makeIndexOhlcv(d, 2800.0 + i * 10) }
            coEvery { krxIndex.getKospi(startDate, endDate) } returns indexData
            coEvery { krxStock.getMarketCap(dates.last(), Market.KOSPI) } returns
                    listOf(makeMarketCap("005930"))
            coEvery {
                krxStock.getOhlcvByTicker(startDate, endDate, "005930")
            } throws CancellationException("코루틴 취소됨")

            assertThrows<CancellationException> {
                calculator.analyze("KOSPI", startDate, endDate)
            }
        }

        @Test
        @DisplayName("개별 종목 OHLCV 조회 실패 시 해당 종목은 건너뛰고 계속 진행한다")
        fun `analyze_individualTickerFails_continuesWithOtherTickers`() = runTest {
            val dates = listOf("20260101", "20260102", "20260105")
            val indexData = dates.mapIndexed { i, d -> makeIndexOhlcv(d, 2800.0 + i * 10) }
            coEvery { krxIndex.getKospi(startDate, endDate) } returns indexData

            val tickers = listOf("005930", "000660")
            coEvery { krxStock.getMarketCap(dates.last(), Market.KOSPI) } returns
                    tickers.map { makeMarketCap(it) }

            // 첫 번째 종목은 IOException 발생 (CancellationException 아님)
            coEvery {
                krxStock.getOhlcvByTicker(startDate, endDate, "005930")
            } throws RuntimeException("종목 데이터 조회 실패")

            // 두 번째 종목은 정상 반환
            coEvery {
                krxStock.getOhlcvByTicker(startDate, endDate, "000660")
            } returns dates.map { makeStockOhlcv(it, 80_000L + dates.indexOf(it) * 1_000L) }

            // 두 번째 종목 데이터가 있으므로 null이 아닌 결과를 반환해야 한다
            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result, "개별 종목 실패 시에도 다른 종목으로 계산해야 한다")
        }

        @Test
        @DisplayName("결과 날짜 목록은 지수 데이터의 날짜와 일치한다")
        fun `analyze_resultDates_matchIndexDates`() = runTest {
            val dates = listOf("20260101", "20260102", "20260105")
            val tickers = listOf("005930")
            val prices = mapOf("005930" to listOf(50_000L, 51_000L, 52_000L))
            setupFullMocks("KOSPI", dates, tickers, prices)

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result)
            assertEquals(dates, result.dates, "결과 날짜 목록은 지수 데이터 날짜와 일치해야 한다")
        }

        @Test
        @DisplayName("결과의 indexValues는 지수 종가를 담는다")
        fun `analyze_resultIndexValues_containIndexClosePrices`() = runTest {
            val dates = listOf("20260101", "20260102", "20260105")
            val closePrices = listOf(2800.0, 2810.0, 2820.0)
            val indexData = dates.zip(closePrices).map { (d, c) -> makeIndexOhlcv(d, c) }
            coEvery { krxIndex.getKospi(startDate, endDate) } returns indexData

            coEvery { krxStock.getMarketCap(dates.last(), Market.KOSPI) } returns
                    listOf(makeMarketCap("005930"))
            coEvery {
                krxStock.getOhlcvByTicker(startDate, endDate, "005930")
            } returns dates.zip(listOf(50_000L, 51_000L, 52_000L)).map { (d, p) ->
                makeStockOhlcv(d, p)
            }

            val result = calculator.analyze("KOSPI", startDate, endDate)

            assertNotNull(result)
            closePrices.zip(result.indexValues).forEachIndexed { i, (expected, actual) ->
                assertEquals(expected, actual, 0.001, "인덱스 ${i}의 indexValue가 올바르지 않다")
            }
        }
    }
}
