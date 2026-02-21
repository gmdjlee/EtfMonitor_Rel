package com.etfmonitor.feature.stock.presentation.statistics

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.SearchHistoryDao
import com.etfmonitor.core.service.CollectionState
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.core.ui.component.statistics.SortColumn
import com.etfmonitor.core.ui.component.statistics.SortOrder
import com.etfmonitor.feature.stock.domain.model.CashDepositTrend
import com.etfmonitor.feature.stock.domain.model.StockAmountRanking
import com.etfmonitor.feature.stock.domain.model.StockAnalysisResult
import com.etfmonitor.feature.stock.domain.model.StockChangeInfo
import com.etfmonitor.feature.stock.domain.repository.StockSearchResult
import com.etfmonitor.feature.stock.domain.repository.StockStatisticsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * StatisticsViewModel unit tests.
 *
 * Coverage:
 * - Initial data loading (isLoading state, amountRanking, dates)
 * - Date range selection changes
 * - Stock search and analysis
 * - Multi-column sort cycling (NONE -> DESCENDING -> ASCENDING -> NONE)
 * - clearAllSorting() restores original order
 * - getSortOrder() and getSortPriority() return correct values
 * - clearAnalysis() clears analysis result and search state
 * - CollectionState completion triggers data reload
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class StatisticsViewModelTest {

    private lateinit var repository: StockStatisticsRepository
    private lateinit var etfDao: EtfDao
    private lateinit var searchHistoryDao: SearchHistoryDao

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        etfDao = mockk(relaxed = true)
        searchHistoryDao = mockk(relaxed = true)

        // Default stubs
        coEvery { repository.getStatisticsDatesInRange(any(), any()) } returns null
        coEvery { repository.getStockAmountRankingInRange(any(), any()) } returns emptyList()
        coEvery { repository.getAllNewStocksInRange(any(), any()) } returns emptyList()
        coEvery { repository.getAllRemovedStocksInRange(any(), any()) } returns emptyList()
        coEvery { repository.getAllIncreasedStocksInRange(any(), any()) } returns emptyList()
        coEvery { repository.getAllDecreasedStocksInRange(any(), any()) } returns emptyList()
        coEvery { repository.getCashDepositTrend() } returns emptyList()
        coEvery { repository.searchStocks(any()) } returns emptyList()
        coEvery { repository.analyzeStock(any()) } returns null
        coEvery { etfDao.getSetting(any()) } returns null
        every { searchHistoryDao.getRecentSearchesByType(any(), any()) } returns flowOf(emptyList())

        CollectionState.reset()
    }

    private fun createViewModel(): StatisticsViewModel = StatisticsViewModel(
        repository = repository,
        etfDao = etfDao,
        searchHistoryDao = searchHistoryDao
    )

    // Helper factories

    private fun makeRanking(
        ticker: String = "005930",
        name: String = "삼성전자",
        totalAmount: Float = 1_000_000f,
        etfCount: Int = 10,
        newEtfCount: Int = 1,
        increasedEtfCount: Int = 2,
        decreasedEtfCount: Int = 1,
        removedEtfCount: Int = 0
    ) = StockAmountRanking(
        stockTicker = ticker,
        stockName = name,
        totalAmount = totalAmount,
        etfCount = etfCount,
        newEtfCount = newEtfCount,
        increasedEtfCount = increasedEtfCount,
        decreasedEtfCount = decreasedEtfCount,
        removedEtfCount = removedEtfCount
    )

    private fun makeChangeInfo(ticker: String, name: String) = StockChangeInfo(
        stockTicker = ticker,
        stockName = name,
        etfTicker = "ETF001",
        etfName = "테스트 ETF",
        currentWeight = 1.5f,
        currentAmount = 500_000f
    )

    private fun makeAnalysisResult(ticker: String = "005930") = StockAnalysisResult(
        stockTicker = ticker,
        stockName = "삼성전자",
        etfDetails = emptyList(),
        totalAmount = 1_000_000f,
        currentEtfCount = 5
    )

    // ---------------------------------------------------------------
    // Initial state tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("초기 상태 테스트")
    inner class InitialStateTests {

        @Test
        @DisplayName("초기화 후 isLoading false")
        fun onInit_isLoadingFalse() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.isLoading.test {
                assertFalse(awaitItem())
            }
        }

        @Test
        @DisplayName("초기 selectedRange 는 MONTH")
        fun onInit_selectedRangeIsMonth() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.selectedRange.test {
                assertEquals(DateRangeOption.MONTH, awaitItem())
            }
        }

        @Test
        @DisplayName("데이터 없을 때 amountRanking 빈 목록")
        fun onInit_noData_amountRankingEmpty() = runTest {
            coEvery { repository.getStatisticsDatesInRange(any(), any()) } returns null

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.amountRanking.test {
                assertEquals(emptyList(), awaitItem())
            }
        }

        @Test
        @DisplayName("dates 데이터 있을 때 amountRanking 목록 로드")
        fun onInit_withDates_loadsAmountRanking() = runTest {
            val rankings = listOf(makeRanking("005930"), makeRanking("000660", "SK하이닉스"))
            coEvery { repository.getStatisticsDatesInRange(any(), any()) } returns
                Pair("2025-01-15", "2024-12-15")
            coEvery { repository.getStockAmountRankingInRange("2025-01-15", "2024-12-15") } returns rankings

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.amountRanking.test {
                assertEquals(2, awaitItem().size)
            }
        }

        @Test
        @DisplayName("초기 searchQuery 는 빈 문자열")
        fun onInit_searchQueryIsEmpty() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.searchQuery.test {
                assertEquals("", awaitItem())
            }
        }

        @Test
        @DisplayName("초기 analysisResult 는 null")
        fun onInit_analysisResultIsNull() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.analysisResult.test {
                assertNull(awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // Date range selection tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("날짜 범위 선택 테스트")
    inner class DateRangeTests {

        @Test
        @DisplayName("updateDateRange() 새로운 범위 선택 시 selectedRange 업데이트")
        fun updateDateRange_newOption_updatesSelectedRange() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.updateDateRange(DateRangeOption.THREE_MONTHS)
            advanceUntilIdle()

            viewModel.selectedRange.test {
                assertEquals(DateRangeOption.THREE_MONTHS, awaitItem())
            }
        }

        @Test
        @DisplayName("updateDateRange() 같은 범위 재선택 시 repository 추가 호출 없음")
        fun updateDateRange_sameOption_doesNotReload() = runTest {
            var callCount = 0
            coEvery { repository.getStatisticsDatesInRange(any(), any()) } coAnswers {
                callCount++
                null
            }

            val viewModel = createViewModel()
            advanceUntilIdle()
            val callsAfterInit = callCount

            // Re-select same option (MONTH is default)
            viewModel.updateDateRange(DateRangeOption.MONTH)
            advanceUntilIdle()

            assertEquals(callsAfterInit, callCount, "Should not reload on same range selection")
        }

        @Test
        @DisplayName("updateDateRange() 범위 변경 후 데이터 재로드")
        fun updateDateRange_newOption_reloadsData() = runTest {
            val rankingsMonth = listOf(makeRanking("005930"))
            val rankingsThreeMonths = listOf(makeRanking("005930"), makeRanking("000660", "SK하이닉스"))

            coEvery { repository.getStatisticsDatesInRange(any(), any()) } returns
                Pair("2025-01-15", "2024-12-15")
            coEvery { repository.getStockAmountRankingInRange(any(), any()) } returns rankingsMonth

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Update stub for THREE_MONTHS
            coEvery { repository.getStockAmountRankingInRange(any(), any()) } returns rankingsThreeMonths
            viewModel.updateDateRange(DateRangeOption.THREE_MONTHS)
            advanceUntilIdle()

            viewModel.amountRanking.test {
                assertEquals(2, awaitItem().size)
            }
        }
    }

    // ---------------------------------------------------------------
    // Stock search tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("종목 검색 테스트")
    inner class SearchTests {

        @Test
        @DisplayName("updateSearchQuery() 2자 이상 입력 시 repository 검색 호출")
        fun updateSearchQuery_twoOrMoreChars_callsRepository() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.updateSearchQuery("삼성")
            advanceUntilIdle()

            coVerify { repository.searchStocks("삼성") }
        }

        @Test
        @DisplayName("updateSearchQuery() 1자 미만 입력 시 searchResults 비움")
        fun updateSearchQuery_lessThanTwoChars_clearsResults() = runTest {
            coEvery { repository.searchStocks(any()) } returns listOf(
                StockSearchResult("005930", "삼성전자")
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.updateSearchQuery("삼")
            advanceUntilIdle()

            viewModel.searchResults.test {
                assertEquals(emptyList(), awaitItem())
            }
        }

        @Test
        @DisplayName("updateSearchQuery() 검색 결과 반영")
        fun updateSearchQuery_withResults_updatesSearchResults() = runTest {
            val searchResults = listOf(
                StockSearchResult("005930", "삼성전자"),
                StockSearchResult("000660", "SK하이닉스")
            )
            coEvery { repository.searchStocks("삼성") } returns searchResults

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.updateSearchQuery("삼성")
            advanceUntilIdle()

            viewModel.searchResults.test {
                assertEquals(2, awaitItem().size)
            }
        }

        @Test
        @DisplayName("updateSearchQuery() 검색어 상태 업데이트")
        fun updateSearchQuery_updatesSearchQuery() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.updateSearchQuery("삼성전자")
            advanceUntilIdle()

            viewModel.searchQuery.test {
                assertEquals("삼성전자", awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // Stock analysis tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("종목 분석 테스트")
    inner class AnalysisTests {

        @Test
        @DisplayName("analyzeStock() 성공 시 analysisResult 설정")
        fun analyzeStock_success_setsAnalysisResult() = runTest {
            val result = makeAnalysisResult("005930")
            coEvery { repository.analyzeStock("005930") } returns result

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.analyzeStock("005930")
            advanceUntilIdle()

            viewModel.analysisResult.test {
                val analysis = awaitItem()
                assertNotNull(analysis)
                assertEquals("005930", analysis.stockTicker)
            }
        }

        @Test
        @DisplayName("analyzeStock() 완료 후 isAnalyzing = false")
        fun analyzeStock_completion_setsAnalyzingFalse() = runTest {
            coEvery { repository.analyzeStock(any()) } returns makeAnalysisResult()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.analyzeStock("005930")
            advanceUntilIdle()

            viewModel.isAnalyzing.test {
                assertFalse(awaitItem())
            }
        }

        @Test
        @DisplayName("analyzeStock() 실패(null) 시 analysisResult null")
        fun analyzeStock_nullResult_setsAnalysisResultNull() = runTest {
            coEvery { repository.analyzeStock(any()) } returns null

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.analyzeStock("INVALID")
            advanceUntilIdle()

            viewModel.analysisResult.test {
                assertNull(awaitItem())
            }
        }

        @Test
        @DisplayName("analyzeStock() 검색어와 결과 초기화")
        fun analyzeStock_clearsSearchQueryAndResults() = runTest {
            coEvery { repository.analyzeStock("005930") } returns makeAnalysisResult()
            coEvery { repository.searchStocks(any()) } returns listOf(
                StockSearchResult("005930", "삼성전자")
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.updateSearchQuery("삼성전자")
            advanceUntilIdle()

            viewModel.analyzeStock("005930")
            advanceUntilIdle()

            viewModel.searchQuery.test {
                assertEquals("", awaitItem())
            }
        }

        @Test
        @DisplayName("clearAnalysis() 호출 시 analysisResult null")
        fun clearAnalysis_clearsResult() = runTest {
            coEvery { repository.analyzeStock(any()) } returns makeAnalysisResult()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.analyzeStock("005930")
            advanceUntilIdle()

            viewModel.clearAnalysis()

            viewModel.analysisResult.test {
                assertNull(awaitItem())
            }
        }

        @Test
        @DisplayName("clearAnalysis() 호출 시 searchQuery 초기화")
        fun clearAnalysis_clearsSearchQuery() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.updateSearchQuery("삼성")
            advanceUntilIdle()

            viewModel.clearAnalysis()

            viewModel.searchQuery.test {
                assertEquals("", awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // Sorting tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("다중 컬럼 정렬 테스트")
    inner class SortingTests {

        @Test
        @DisplayName("sortAmountRankingBy() 처음 호출 시 DESCENDING 정렬 시작")
        fun sortAmountRankingBy_firstCall_setsDescending() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.sortAmountRankingBy(SortColumn.TOTAL_AMOUNT)

            assertEquals(SortOrder.DESCENDING, viewModel.getSortOrder(SortColumn.TOTAL_AMOUNT))
        }

        @Test
        @DisplayName("sortAmountRankingBy() 두 번 호출 시 ASCENDING 전환")
        fun sortAmountRankingBy_secondCall_setsAscending() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.sortAmountRankingBy(SortColumn.TOTAL_AMOUNT)
            viewModel.sortAmountRankingBy(SortColumn.TOTAL_AMOUNT)

            assertEquals(SortOrder.ASCENDING, viewModel.getSortOrder(SortColumn.TOTAL_AMOUNT))
        }

        @Test
        @DisplayName("sortAmountRankingBy() 세 번 호출 시 NONE (정렬 해제)")
        fun sortAmountRankingBy_thirdCall_setsNone() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.sortAmountRankingBy(SortColumn.TOTAL_AMOUNT)
            viewModel.sortAmountRankingBy(SortColumn.TOTAL_AMOUNT)
            viewModel.sortAmountRankingBy(SortColumn.TOTAL_AMOUNT)

            assertEquals(SortOrder.NONE, viewModel.getSortOrder(SortColumn.TOTAL_AMOUNT))
        }

        @Test
        @DisplayName("getSortOrder() 정렬 안 된 컬럼은 NONE")
        fun getSortOrder_unsortedColumn_returnsNone() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(SortOrder.NONE, viewModel.getSortOrder(SortColumn.ETF_COUNT))
        }

        @Test
        @DisplayName("getSortPriority() 정렬 안 된 컬럼은 0")
        fun getSortPriority_unsortedColumn_returnsZero() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(0, viewModel.getSortPriority(SortColumn.ETF_COUNT))
        }

        @Test
        @DisplayName("다중 컬럼 정렬 시 sortCriteria 목록에 모두 포함")
        fun multiColumnSort_addsBothColumnsToSortCriteria() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.sortAmountRankingBy(SortColumn.TOTAL_AMOUNT)
            viewModel.sortAmountRankingBy(SortColumn.ETF_COUNT)

            viewModel.sortCriteria.test {
                val criteria = awaitItem()
                assertEquals(2, criteria.size)
            }
        }

        @Test
        @DisplayName("getSortPriority() 첫 번째 정렬 컬럼은 우선순위 1")
        fun getSortPriority_firstSortedColumn_returnsOne() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.sortAmountRankingBy(SortColumn.TOTAL_AMOUNT)
            viewModel.sortAmountRankingBy(SortColumn.ETF_COUNT)

            assertEquals(1, viewModel.getSortPriority(SortColumn.TOTAL_AMOUNT))
            assertEquals(2, viewModel.getSortPriority(SortColumn.ETF_COUNT))
        }

        @Test
        @DisplayName("clearAllSorting() 호출 시 sortCriteria 비워짐")
        fun clearAllSorting_clearsSortCriteria() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.sortAmountRankingBy(SortColumn.TOTAL_AMOUNT)
            viewModel.sortAmountRankingBy(SortColumn.ETF_COUNT)

            viewModel.clearAllSorting()

            viewModel.sortCriteria.test {
                assertEquals(emptyList(), awaitItem())
            }
        }

        @Test
        @DisplayName("clearAllSorting() 호출 시 원본 순서로 복원")
        fun clearAllSorting_restoresOriginalOrder() = runTest {
            val rankings = listOf(
                makeRanking("005930", "삼성전자", totalAmount = 1_000_000f),
                makeRanking("000660", "SK하이닉스", totalAmount = 500_000f),
                makeRanking("035420", "NAVER", totalAmount = 300_000f)
            )
            coEvery { repository.getStatisticsDatesInRange(any(), any()) } returns
                Pair("2025-01-15", "2024-12-15")
            coEvery { repository.getStockAmountRankingInRange(any(), any()) } returns rankings

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Sort descending (SK하이닉스 < 삼성전자 -> ascending by name would put NAVER first)
            viewModel.sortAmountRankingBy(SortColumn.STOCK_NAME)
            viewModel.clearAllSorting()

            viewModel.amountRanking.test {
                val list = awaitItem()
                assertEquals("005930", list.first().stockTicker)
            }
        }

        @Test
        @DisplayName("TOTAL_AMOUNT DESCENDING 정렬 시 큰 금액이 먼저")
        fun sortByTotalAmount_descending_largestFirst() = runTest {
            val rankings = listOf(
                makeRanking("A", totalAmount = 100f),
                makeRanking("B", totalAmount = 500f),
                makeRanking("C", totalAmount = 300f)
            )
            coEvery { repository.getStatisticsDatesInRange(any(), any()) } returns
                Pair("2025-01-15", "2024-12-15")
            coEvery { repository.getStockAmountRankingInRange(any(), any()) } returns rankings

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.sortAmountRankingBy(SortColumn.TOTAL_AMOUNT)
            advanceUntilIdle()

            viewModel.amountRanking.test {
                val sorted = awaitItem()
                assertEquals("B", sorted[0].stockTicker)
                assertEquals("C", sorted[1].stockTicker)
                assertEquals("A", sorted[2].stockTicker)
            }
        }
    }

    // ---------------------------------------------------------------
    // CollectionState tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("CollectionState 감지 테스트")
    inner class CollectionStateTests {

        @Test
        @DisplayName("수집 완료 시 데이터 재로드")
        fun collectionComplete_triggersReload() = runTest {
            var callCount = 0
            coEvery { repository.getStatisticsDatesInRange(any(), any()) } coAnswers {
                callCount++
                null
            }
            CollectionState.reset()

            val viewModel = createViewModel()
            advanceUntilIdle()
            val callsAfterInit = callCount

            CollectionState.startCollection(isInitialize = false)
            advanceUntilIdle()

            CollectionState.complete("done")
            advanceUntilIdle()

            assertTrue(callCount > callsAfterInit, "Expected data reload after collection completed")
        }
    }
}
