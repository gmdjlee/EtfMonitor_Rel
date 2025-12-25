package com.etfmonitor.feature.stock.presentation.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.core.database.entities.CashDepositTrend
import com.etfmonitor.core.database.entities.SearchHistory
import com.etfmonitor.core.database.entities.SearchHistoryType
import com.etfmonitor.core.database.entities.Stock
import com.etfmonitor.core.database.entities.StockAmountRanking
import com.etfmonitor.core.database.entities.StockAnalysisResult
import com.etfmonitor.core.database.entities.StockChangeInfo
import com.etfmonitor.core.database.SearchHistoryDao
import com.etfmonitor.repository.DataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val repository: DataRepository,
    private val etfDao: com.etfmonitor.core.database.EtfDao,
    private val searchHistoryDao: SearchHistoryDao
) : ViewModel() {

    companion object {
        private const val QUICK_CHART_ANALYSIS_KEY = "quick_chart_analysis_enabled"
    }

    private val _dates = MutableStateFlow<Pair<String, String>?>(null)
    val dates: StateFlow<Pair<String, String>?> = _dates.asStateFlow()

    // 빠른 차트 분석 설정
    private val _quickChartAnalysisEnabled = MutableStateFlow(false)
    val quickChartAnalysisEnabled: StateFlow<Boolean> = _quickChartAnalysisEnabled.asStateFlow()

    private val _amountRanking = MutableStateFlow<List<StockAmountRanking>>(emptyList())
    val amountRanking: StateFlow<List<StockAmountRanking>> = _amountRanking.asStateFlow()

    // 원본 데이터 보관 (기본 정렬 복원용)
    private var originalAmountRanking: List<StockAmountRanking> = emptyList()

    // 다중 컬럼 정렬 지원
    private val _sortCriteria = MutableStateFlow<List<SortCriteria>>(emptyList())
    val sortCriteria: StateFlow<List<SortCriteria>> = _sortCriteria.asStateFlow()

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

    private val _searchResults = MutableStateFlow<List<com.etfmonitor.core.database.StockSearchResult>>(emptyList())
    val searchResults: StateFlow<List<com.etfmonitor.core.database.StockSearchResult>> = _searchResults.asStateFlow()

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

    private fun loadStatistics() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _dates.value = repository.getStatisticsDates()
                val ranking = repository.getStockAmountRanking()
                originalAmountRanking = ranking
                _amountRanking.value = ranking
                _newStocks.value = repository.getAllNewStocks()
                _removedStocks.value = repository.getAllRemovedStocks()
                _increasedStocks.value = repository.getAllIncreasedStocks()
                _decreasedStocks.value = repository.getAllDecreasedStocks()
                _cashDepositTrend.value = repository.getCashDepositTrend()
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
            try {
                val result = repository.analyzeStock(stockTicker)
                _analysisResult.value = result
                _searchQuery.value = ""
                _searchResults.value = emptyList()

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
    fun sortAmountRankingBy(column: SortColumn) {
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
            currentCriteria.add(SortCriteria(column, SortOrder.DESCENDING))
        }

        _sortCriteria.value = currentCriteria
        applySorting()
    }

    /**
     * 모든 정렬 초기화
     */
    fun clearAllSorting() {
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
        val comparator = criteria.fold<SortCriteria, Comparator<StockAmountRanking>?>(null) { acc, sortCriteria ->
            val columnComparator = createComparator(sortCriteria.column, sortCriteria.order)
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
     * 특정 컬럼의 현재 정렬 순서 가져오기
     */
    fun getSortOrder(column: SortColumn): SortOrder {
        return _sortCriteria.value.find { it.column == column }?.order ?: SortOrder.NONE
    }

    /**
     * 특정 컬럼의 정렬 우선순위 가져오기 (1부터 시작, 0이면 정렬 안 됨)
     */
    fun getSortPriority(column: SortColumn): Int {
        val index = _sortCriteria.value.indexOfFirst { it.column == column }
        return if (index >= 0) index + 1 else 0
    }
}

/**
 * 정렬 순서
 */
enum class SortOrder {
    NONE,       // 기본 정렬 (정렬 없음)
    ASCENDING,  // 오름차순
    DESCENDING  // 내림차순
}

/**
 * 정렬 기준 (컬럼 + 순서)
 */
data class SortCriteria(
    val column: SortColumn,
    val order: SortOrder
)

/**
 * 금액순위 정렬 기준 열
 */
enum class SortColumn {
    STOCK_NAME,           // 종목명
    TOTAL_AMOUNT,         // 금액
    ETF_COUNT,            // ETF수
    NEW_ETF_COUNT,        // 신규
    INCREASED_ETF_COUNT,  // 증가
    DECREASED_ETF_COUNT,  // 감소
    REMOVED_ETF_COUNT     // 제외
}
