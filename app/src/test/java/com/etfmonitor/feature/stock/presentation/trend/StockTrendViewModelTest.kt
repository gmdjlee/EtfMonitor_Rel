package com.etfmonitor.feature.stock.presentation.trend

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.feature.stock.domain.model.HoldingTimeSeries
import com.etfmonitor.feature.stock.domain.model.StockTrend
import com.etfmonitor.feature.stock.domain.usecase.GetStockTrendUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * StockTrendViewModel 단위 테스트
 *
 * 테스트 범위:
 * - 초기 상태 (Loading → Success/Error)
 * - SavedStateHandle 파라미터(etfTicker, stockTicker) 처리
 * - 트렌드 데이터 로딩 성공/실패
 * - 날짜 범위 필터링 (applyDateRangeFilter)
 * - 전체 기간 필터 (DateRangeOption.ALL)
 * - quickChartAnalysisEnabled 설정 로딩
 * - updateDateRange() 동작
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class StockTrendViewModelTest {

    private lateinit var getStockTrendUseCase: GetStockTrendUseCase
    private lateinit var etfDao: EtfDao

    private val testEtfTicker = "KODEX200"
    private val testStockTicker = "005930"

    @BeforeEach
    fun setup() {
        getStockTrendUseCase = mockk(relaxed = true)
        etfDao = mockk(relaxed = true)

        // Default: return valid trend data
        coEvery { getStockTrendUseCase(testEtfTicker, testStockTicker) } returns makeSampleTrend()

        // Default: setting not configured
        coEvery { etfDao.getSetting(any()) } returns null
    }

    private fun createViewModel(
        etfTicker: String = testEtfTicker,
        stockTicker: String = testStockTicker
    ): StockTrendViewModel {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "etfTicker" to etfTicker,
                "stockTicker" to stockTicker
            )
        )
        return StockTrendViewModel(
            getStockTrendUseCase = getStockTrendUseCase,
            etfDao = etfDao,
            savedStateHandle = savedStateHandle
        )
    }

    // --- helpers ---

    private fun makeTimeSeries(daysAgo: Int): HoldingTimeSeries {
        val date = LocalDate.now().minusDays(daysAgo.toLong())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        return HoldingTimeSeries(date = date, weight = 25.5f, amount = 1_500_000f)
    }

    private fun makeSampleTrend(
        etfTicker: String = testEtfTicker,
        stockTicker: String = testStockTicker
    ) = StockTrend(
        etfTicker = etfTicker,
        stockTicker = stockTicker,
        stockName = "삼성전자",
        timeSeries = listOf(
            makeTimeSeries(365),   // ~1 year ago
            makeTimeSeries(180),   // ~6 months ago
            makeTimeSeries(30),    // ~1 month ago
            makeTimeSeries(7),     // ~1 week ago
            makeTimeSeries(0)      // today
        )
    )

    // ---------------------------------------------------------------
    // 초기 상태 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("초기 상태 테스트")
    inner class InitialStateTests {

        @Test
        @DisplayName("트렌드 데이터 있을 때 초기화 후 Success 상태")
        fun hasTrend_initialState_isSuccess() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<StockTrendState.Success>(awaitItem())
            }
        }

        @Test
        @DisplayName("초기 selectedRange 는 YEAR")
        fun initialSelectedRange_isYear() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectedRange.test {
                assertEquals(DateRangeOption.YEAR, awaitItem())
            }
        }

        @Test
        @DisplayName("초기 quickChartAnalysisEnabled 는 false")
        fun initialQuickChartAnalysis_isFalse() = runTest {
            coEvery { etfDao.getSetting(any()) } returns null

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.quickChartAnalysisEnabled.test {
                assertFalse(awaitItem())
            }
        }

        @Test
        @DisplayName("설정에서 quick_chart_analysis_enabled=true 시 true 반환")
        fun settingEnabled_quickChartAnalysisIsTrue() = runTest {
            coEvery { etfDao.getSetting("quick_chart_analysis_enabled") } returns "true"

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.quickChartAnalysisEnabled.test {
                assertTrue(awaitItem())
            }
        }

        @Test
        @DisplayName("stockTicker SavedStateHandle에서 올바르게 읽음")
        fun stockTicker_isReadFromSavedStateHandle() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(testStockTicker, viewModel.stockTicker)
        }
    }

    // ---------------------------------------------------------------
    // 트렌드 데이터 로딩 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("트렌드 데이터 로딩 테스트")
    inner class TrendLoadingTests {

        @Test
        @DisplayName("트렌드 데이터 없을 때 Error 상태")
        fun nullTrend_producesErrorState() = runTest {
            coEvery { getStockTrendUseCase(any(), any()) } returns null

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<StockTrendState.Error>(state)
                assertTrue(state.message.isNotEmpty())
            }
        }

        @Test
        @DisplayName("useCase 예외 발생 시 Error 상태")
        fun exception_producesErrorState() = runTest {
            coEvery { getStockTrendUseCase(any(), any()) } throws RuntimeException("DB 오류")

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<StockTrendState.Error>(state)
                assertTrue(state.message.contains("DB 오류"))
            }
        }

        @Test
        @DisplayName("Success 상태에 StockTrend 데이터 포함")
        fun success_containsTrendData() = runTest {
            val trend = makeSampleTrend()
            coEvery { getStockTrendUseCase(testEtfTicker, testStockTicker) } returns trend

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<StockTrendState.Success>(state)
                assertEquals(testEtfTicker, state.trend.etfTicker)
                assertEquals(testStockTicker, state.trend.stockTicker)
                assertEquals("삼성전자", state.trend.stockName)
            }
        }

        @Test
        @DisplayName("etfTicker + stockTicker 조합으로 useCase 호출")
        fun useCaseCalledWithCorrectArgs() = runTest {
            val viewModel = createViewModel(
                etfTicker = "TIGER200",
                stockTicker = "000660"
            )
            coEvery { getStockTrendUseCase("TIGER200", "000660") } returns makeSampleTrend("TIGER200", "000660")
            advanceUntilIdle()

            io.mockk.coVerify { getStockTrendUseCase("TIGER200", "000660") }
        }
    }

    // ---------------------------------------------------------------
    // 날짜 범위 필터링 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("날짜 범위 필터링 테스트")
    inner class DateRangeFilterTests {

        @Test
        @DisplayName("updateDateRange() 호출 시 selectedRange 업데이트")
        fun updateDateRange_updatesSelectedRange() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.updateDateRange(DateRangeOption.MONTH)
            advanceUntilIdle()

            viewModel.selectedRange.test {
                assertEquals(DateRangeOption.MONTH, awaitItem())
            }
        }

        @Test
        @DisplayName("같은 범위로 updateDateRange() 시 selectedRange 유지")
        fun updateDateRange_sameOption_noChange() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Update with YEAR (the default) — selectedRange should stay YEAR
            viewModel.updateDateRange(DateRangeOption.YEAR)
            advanceUntilIdle()

            viewModel.selectedRange.test {
                assertEquals(DateRangeOption.YEAR, awaitItem())
            }
        }

        @Test
        @DisplayName("MONTH 범위 설정 시 30일 이내 데이터만 포함")
        fun monthRange_filtersToRecentData() = runTest {
            val trend = makeSampleTrend()
            coEvery { getStockTrendUseCase(any(), any()) } returns trend

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.updateDateRange(DateRangeOption.MONTH)
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<StockTrendState.Success>(state)
                // Only recent data (within 30 days) should be in filtered trend
                state.trend.timeSeries.forEach { point ->
                    val date = LocalDate.parse(point.date)
                    val cutoff = LocalDate.now().minusDays(DateRangeOption.MONTH.days.toLong())
                    assertTrue(date >= cutoff, "Date $date should be after cutoff $cutoff")
                }
            }
        }

        @Test
        @DisplayName("ALL 범위 설정 시 전체 데이터 반환")
        fun allRange_returnsAllData() = runTest {
            val trend = makeSampleTrend()
            coEvery { getStockTrendUseCase(any(), any()) } returns trend

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.updateDateRange(DateRangeOption.ALL)
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<StockTrendState.Success>(state)
                // All 5 time series points should be present when using ALL range
                assertEquals(trend.timeSeries.size, state.trend.timeSeries.size)
            }
        }
    }
}
