package com.etfmonitor.feature.stock.presentation.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.database.entities.SearchHistory
import com.etfmonitor.core.database.entities.SearchHistoryType
import com.etfmonitor.core.database.entities.Stock
import com.etfmonitor.core.database.SearchHistoryDao
import com.etfmonitor.core.service.CollectionState
import com.etfmonitor.core.ui.component.ChartLabelCalculator
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.core.ui.component.statistics.SortColumn
import com.etfmonitor.core.ui.component.statistics.SortController
import com.etfmonitor.core.ui.component.statistics.SortCriterion
import com.etfmonitor.core.ui.component.statistics.SortOrder
import com.etfmonitor.feature.stock.domain.model.CashDepositTrend
import com.etfmonitor.feature.stock.domain.model.StockAmountRanking
import com.etfmonitor.feature.stock.domain.model.StockAnalysisResult
import com.etfmonitor.feature.stock.domain.model.StockChangeInfo
import com.etfmonitor.feature.stock.domain.repository.StockSearchResult
import com.etfmonitor.feature.stock.domain.repository.StockStatisticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

/**
 * Production Level StatisticsViewModel with Hilt
 *
 * 최적화 포인트:
 * 1. @HiltViewModel: Hilt가 ViewModel 생명주기 자동 관리
 * 2. @Inject: 생성자 주입으로 의존성 명확화
 * 3. Factory 패턴 제거: Hilt가 자동으로 ViewModel 생성
 *
 * 기존 문제점 해결:
 * - EtfMonitorApp.instance 제거: 메모리 누수 위험 제거
 * - 수동 Factory 제거: Hilt가 자동으로 관리하여 코드 간결화
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val repository: StockStatisticsRepository,
    private val etfDao: com.etfmonitor.core.database.EtfDao,
    private val searchHistoryDao: SearchHistoryDao
) : ViewModel(), SortController {

    companion object {
        private const val QUICK_CHART_ANALYSIS_KEY = "quick_chart_analysis_enabled"
        private val logger = AppLogger.getLogger("StatisticsViewModel")
    }

    private val _dates = MutableStateFlow<Pair<String, String>?>(null)
    val dates: StateFlow<Pair<String, String>?> = _dates.asStateFlow()

    // 기간 선택 상태
    private val _selectedRange = MutableStateFlow(DateRangeOption.MONTH)
    val selectedRange: StateFlow<DateRangeOption> = _selectedRange.asStateFlow()

    // 빠른 차트 분석 설정
    private val _quickChartAnalysisEnabled = MutableStateFlow(false)
    val quickChartAnalysisEnabled: StateFlow<Boolean> = _quickChartAnalysisEnabled.asStateFlow()

    private val _amountRanking = MutableStateFlow<List<StockAmountRanking>>(emptyList())
    val amountRanking: StateFlow<List<StockAmountRanking>> = _amountRanking.asStateFlow()

    // 원본 데이터 보관 (기본 정렬 복원용)
    private var originalAmountRanking: List<StockAmountRanking> = emptyList()

    // 다중 컬럼 정렬 지원 (SortController 인터페이스 구현)
    private val _sortCriteria = MutableStateFlow<List<SortCriterion>>(emptyList())
    override val sortCriteria: StateFlow<List<SortCriterion>> = _sortCriteria.asStateFlow()

    private val _newStocks = MutableStateFlow<List<StockChangeInfo>>(emptyList())
    val newStocks: StateFlow<List<StockChangeInfo>> = _newStocks.asStateFlow()

    private val _removedStocks = MutableStateFlow<List<StockChangeInfo>>(emptyList())
    val removedStocks: StateFlow<List<StockChangeInfo>> = _removedStocks.asStateFlow()

    private val _increasedStocks = MutableStateFlow<List<StockChangeInfo>>(emptyList())
    val increasedStocks: StateFlow<List<StockChangeInfo>> = _increasedStocks.asStateFlow()

    // 비중 감소 종목
    private val _decreasedStocks = MutableStateFlow<List<StockChangeInfo>>(emptyList())
    val decreasedStocks: StateFlow<List<StockChangeInfo>> = _decreasedStocks.asStateFlow()

    // 원화예금 추이
    private val _cashDepositTrend = MutableStateFlow<List<CashDepositTrend>>(emptyList())
    val cashDepositTrend: StateFlow<List<CashDepositTrend>> = _cashDepositTrend.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 종목 분석 상태
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<StockSearchResult>>(emptyList())
    val searchResults: StateFlow<List<StockSearchResult>> = _searchResults.asStateFlow()

    private val _analysisResult = MutableStateFlow<StockAnalysisResult?>(null)
    val analysisResult: StateFlow<StockAnalysisResult?> = _analysisResult.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    // 검색 히스토리 (최근 20개) - STATISTICS 타입만
    val searchHistory: Flow<List<SearchHistory>> = searchHistoryDao.getRecentSearchesByType(
        SearchHistoryType.STATISTICS, 20
    )

    init {
        loadStatistics()
        loadQuickChartAnalysisSetting()
        observeCollectionState()
    }

    /**
     * 데이터 수집 완료 상태를 관찰하여 자동 새로고침
     */
    private fun observeCollectionState() {
        viewModelScope.launch {
            CollectionState.isCollecting.collect { isCollecting ->
                // 수집이 완료되면 (false로 변경되면) 데이터 새로고침
                if (!isCollecting) {
                    logger.d("Data collection completed, triggering refresh")
                    loadStatistics()
                }
            }
        }
    }

    /**
     * 빠른 차트 분석 설정 로드
     */
    private fun loadQuickChartAnalysisSetting() {
        viewModelScope.launch {
            try {
                val enabled = etfDao.getSetting(QUICK_CHART_ANALYSIS_KEY) == "true"
                _quickChartAnalysisEnabled.value = enabled
            } catch (e: Exception) {
                // Ignore error, keep default value
            }
        }
    }

    /**
     * 날짜 범위 선택 변경
     */
    fun updateDateRange(option: DateRangeOption) {
        if (option == _selectedRange.value) return
        _selectedRange.value = option
        logger.d("Date range changed to: ${option.label}")
        loadStatistics()
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    // 선택된 기간에 따른 날짜 범위 계산
                    val (startDate, endDate) = ChartLabelCalculator.calculateDateRange(
                        option = _selectedRange.value,
                        endDate = LocalDate.now()
                    )

                    logger.d("Loading statistics for range: $startDate ~ $endDate")

                    // 날짜 범위 내 통계 날짜 조회
                    val datesInRange = repository.getStatisticsDatesInRange(startDate, endDate)
                    _dates.value = datesInRange

                    if (datesInRange != null) {
                        val currentDate = datesInRange.first
                        val previousDate = datesInRange.second

                        val ranking = repository.getStockAmountRankingInRange(currentDate, previousDate)
                        originalAmountRanking = ranking
                        _amountRanking.value = ranking
                        _newStocks.value = repository.getAllNewStocksInRange(currentDate, previousDate)
                        _removedStocks.value = repository.getAllRemovedStocksInRange(currentDate, previousDate)
                        _increasedStocks.value = repository.getAllIncreasedStocksInRange(currentDate, previousDate)
                        _decreasedStocks.value = repository.getAllDecreasedStocksInRange(currentDate, previousDate)
                    } else {
                        // 범위 내 데이터가 없으면 빈 목록
                        originalAmountRanking = emptyList()
                        _amountRanking.value = emptyList()
                        _newStocks.value = emptyList()
                        _removedStocks.value = emptyList()
                        _increasedStocks.value = emptyList()
                        _decreasedStocks.value = emptyList()
                    }

                    // 원화예금 추이는 전체 기간 표시
                    _cashDepositTrend.value = repository.getCashDepositTrend()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    // 종목 검색
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.length >= 2) {
            viewModelScope.launch {
                _searchResults.value = repository.searchStocks(query)
            }
        } else {
            _searchResults.value = emptyList()
        }
    }

    // 종목 분석
    fun analyzeStock(stockTicker: String, saveHistory: Boolean = true) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            // Clear previous analysis to ensure clean loading state
            _analysisResult.value = null
            _searchQuery.value = ""
            _searchResults.value = emptyList()
            try {
                val result = repository.analyzeStock(stockTicker)
                _analysisResult.value = result

                // 분석 성공 시 검색 히스토리에 저장 (FAB 네비게이션 시에는 저장하지 않음)
                if (saveHistory) {
                    result?.let {
                        try {
                            val market = Stock.inferMarket(it.stockTicker)
                            searchHistoryDao.insertSearch(
                                SearchHistory(
                                    ticker = it.stockTicker,
                                    name = it.stockName,
                                    market = market,
                                    historyType = SearchHistoryType.STATISTICS
                                )
                            )
                            searchHistoryDao.deleteOldSearchesByType(SearchHistoryType.STATISTICS, 20)
                        } catch (e: Exception) {
                            // 히스토리 저장 실패 무시
                        }
                    }
                }
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    // 검색 후 분석 (종목명 또는 티커로 검색하여 분석)
    fun searchAndAnalyze(query: String) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            try {
                // 먼저 검색 수행
                val results = repository.searchStocks(query)

                if (results.isNotEmpty()) {
                    // 첫 번째 결과 (가장 관련성 높은 결과)의 티커로 분석
                    _analysisResult.value = repository.analyzeStock(results.first().stockTicker)
                } else {
                    // 검색 결과 없으면 입력값을 직접 티커로 간주하고 시도
                    _analysisResult.value = repository.analyzeStock(query)
                }

                _searchQuery.value = ""
                _searchResults.value = emptyList()
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    // 분석 결과 초기화
    fun clearAnalysis() {
        _analysisResult.value = null
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    /**
     * 다중 컬럼 정렬 - 3가지 상태 (기본 → 내림차순 → 오름차순 → 기본)
     * 여러 컬럼에 대해 순차적으로 정렬 적용 가능
     */
    override fun sortAmountRankingBy(column: SortColumn) {
        val currentCriteria = _sortCriteria.value.toMutableList()
        val existingIndex = currentCriteria.indexOfFirst { it.column == column }

        if (existingIndex >= 0) {
            // 이미 정렬 중인 컬럼 - 상태 순환: 내림차순 → 오름차순 → 기본(제거)
            val existing = currentCriteria[existingIndex]
            when (existing.order) {
                SortOrder.DESCENDING -> {
                    // 내림차순 → 오름차순
                    currentCriteria[existingIndex] = existing.copy(order = SortOrder.ASCENDING)
                }
                SortOrder.ASCENDING -> {
                    // 오름차순 → 기본 (정렬 기준에서 제거)
                    currentCriteria.removeAt(existingIndex)
                }
                else -> {
                    // 기본 → 내림차순 (일반적으로 여기 도달하지 않음)
                    currentCriteria[existingIndex] = existing.copy(order = SortOrder.DESCENDING)
                }
            }
        } else {
            // 새 컬럼 추가 - 내림차순으로 시작
            currentCriteria.add(SortCriterion(column, SortOrder.DESCENDING))
        }

        _sortCriteria.value = currentCriteria
        applySorting()
    }

    /**
     * 모든 정렬 초기화
     */
    override fun clearAllSorting() {
        _sortCriteria.value = emptyList()
        _amountRanking.value = originalAmountRanking
    }

    /**
     * 현재 정렬 기준에 따라 데이터 정렬 적용
     */
    private fun applySorting() {
        val criteria = _sortCriteria.value

        if (criteria.isEmpty()) {
            // 정렬 기준 없으면 원본 데이터로 복원
            _amountRanking.value = originalAmountRanking
            return
        }

        // 다중 컬럼 정렬을 위한 Comparator 체인 생성
        val comparator = criteria.fold<SortCriterion, Comparator<StockAmountRanking>?>(null) { acc, sortCriterion ->
            val columnComparator = createComparator(sortCriterion.column, sortCriterion.order)
            if (acc == null) {
                columnComparator
            } else {
                acc.then(columnComparator)
            }
        }

        _amountRanking.value = if (comparator != null) {
            originalAmountRanking.sortedWith(comparator)
        } else {
            originalAmountRanking
        }
    }

    /**
     * 컬럼과 정렬 순서에 따른 Comparator 생성
     */
    private fun createComparator(column: SortColumn, order: SortOrder): Comparator<StockAmountRanking> {
        val baseComparator: Comparator<StockAmountRanking> = when (column) {
            SortColumn.STOCK_NAME -> compareBy { it.stockName }
            SortColumn.TOTAL_AMOUNT -> compareBy { it.totalAmount }
            SortColumn.ETF_COUNT -> compareBy { it.etfCount }
            SortColumn.NEW_ETF_COUNT -> compareBy { it.newEtfCount }
            SortColumn.INCREASED_ETF_COUNT -> compareBy { it.increasedEtfCount }
            SortColumn.DECREASED_ETF_COUNT -> compareBy { it.decreasedEtfCount }
            SortColumn.REMOVED_ETF_COUNT -> compareBy { it.removedEtfCount }
        }

        return when (order) {
            SortOrder.ASCENDING -> baseComparator
            SortOrder.DESCENDING -> baseComparator.reversed()
            SortOrder.NONE -> baseComparator // 실제로는 사용되지 않음
        }
    }

    /**
     * 특정 컬럼의 현재 정렬 순서 가져오기 (SortController 인터페이스 구현)
     */
    override fun getSortOrder(column: SortColumn): SortOrder {
        return _sortCriteria.value.find { it.column == column }?.order ?: SortOrder.NONE
    }

    /**
     * 특정 컬럼의 정렬 우선순위 가져오기 (SortController 인터페이스 구현)
     * @return 1부터 시작하는 우선순위, 0이면 정렬 안 됨
     */
    override fun getSortPriority(column: SortColumn): Int {
        val index = _sortCriteria.value.indexOfFirst { it.column == column }
        return if (index >= 0) index + 1 else 0
    }
}
