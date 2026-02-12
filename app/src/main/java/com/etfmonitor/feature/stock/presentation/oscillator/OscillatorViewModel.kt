package com.etfmonitor.feature.stock.presentation.oscillator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.SearchHistoryDao
import com.etfmonitor.core.database.entities.SearchHistory
import com.etfmonitor.core.database.entities.SearchHistoryType
import com.etfmonitor.core.analysis.OscillatorCalculator
import com.etfmonitor.core.analysis.TrendSignalCalculator
import com.etfmonitor.core.analysis.model.*
import com.etfmonitor.core.analysis.model.ElderImpulseData
import com.etfmonitor.core.analysis.model.DemarkTDData
import com.etfmonitor.core.network.krx.StockDataClient
import com.etfmonitor.core.analysis.TrendSignalNativeCalculator
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.feature.stock.domain.model.Stock
import com.etfmonitor.feature.stock.domain.repository.StockAnalysisRepository
import com.etfmonitor.feature.stock.domain.repository.StockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed class OscillatorState {
    data object Idle : OscillatorState()
    data object Loading : OscillatorState()
    data class Success(
        val stockData: StockData,
        val oscillatorResult: OscillatorResult,
        val signalAnalysis: SignalAnalysis,
        val trendSignalData: TrendSignalData? = null,        // 추세 시그널 데이터
        val trendSignalAnalysis: TrendSignalAnalysis? = null, // 추세 시그널 분석
        val elderImpulseData: ElderImpulseData? = null,      // Elder Impulse 데이터
        val demarkTDData: DemarkTDData? = null               // DeMark TD 데이터
    ) : OscillatorState()
    data class Error(val message: String) : OscillatorState()
}

/**
 * Production Level OscillatorViewModel with Hilt
 *
 * 최적화 포인트:
 * 1. @HiltViewModel: Hilt가 ViewModel 생명주기 자동 관리
 * 2. @Inject: 생성자 주입으로 의존성 명확화
 * 3. Factory 패턴 제거: Hilt가 자동으로 ViewModel 생성
 * 4. AndroidViewModel → ViewModel: Application 직접 주입 제거
 *
 * 기존 문제점 해결:
 * - EtfMonitorApp.instance 제거: 메모리 누수 위험 제거
 * - 수동 Factory 제거: Hilt가 자동으로 관리하여 코드 간결화
 */
@HiltViewModel
class OscillatorViewModel @Inject constructor(
    private val stockDataClient: StockDataClient,
    private val trendSignalCalc: TrendSignalNativeCalculator,
    private val stockRepository: StockRepository,
    private val stockAnalysisRepository: StockAnalysisRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val etfDao: EtfDao
) : ViewModel() {

    companion object {
        private const val QUICK_CHART_ANALYSIS_KEY = "quick_chart_analysis_enabled"
    }

    private val _state = MutableStateFlow<OscillatorState>(OscillatorState.Idle)
    val state: StateFlow<OscillatorState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _suggestions = MutableStateFlow<List<Stock>>(emptyList())
    val suggestions: StateFlow<List<Stock>> = _suggestions.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<SearchHistory>>(emptyList())
    val searchHistory: StateFlow<List<SearchHistory>> = _searchHistory.asStateFlow()

    // DeMark TD 인터벌 선택
    private val _demarkTDInterval = MutableStateFlow("w")
    val demarkTDInterval: StateFlow<String> = _demarkTDInterval.asStateFlow()

    // 추세 시그널 인터벌 선택 (d: 일봉, w: 주봉)
    private val _trendSignalInterval = MutableStateFlow("w")
    val trendSignalInterval: StateFlow<String> = _trendSignalInterval.asStateFlow()

    // Elder Impulse 인터벌 선택 (d: 일봉, w: 주봉)
    private val _elderImpulseInterval = MutableStateFlow("w")
    val elderImpulseInterval: StateFlow<String> = _elderImpulseInterval.asStateFlow()

    // 날짜 범위 선택 상태
    private val _selectedRange = MutableStateFlow(DateRangeOption.SIX_MONTHS)
    val selectedRange: StateFlow<DateRangeOption> = _selectedRange.asStateFlow()

    // 현재 분석 중인 종목 ticker (기간 변경 시 재분석용) - StateFlow로 노출하여 UI에서 사용 가능
    private val _currentTicker = MutableStateFlow<String?>(null)
    val currentTicker: StateFlow<String?> = _currentTicker.asStateFlow()

    // 빠른 차트 분석 설정 (FAB 표시용)
    private val _quickChartAnalysisEnabled = MutableStateFlow(false)
    val quickChartAnalysisEnabled: StateFlow<Boolean> = _quickChartAnalysisEnabled.asStateFlow()

    private var searchJob: Job? = null

    // 전체 데이터 캐시 (클라이언트 사이드 필터링용)
    private var fullStockData: StockData? = null
    private var fullOscillatorResult: OscillatorResult? = null
    private var fullSignalAnalysis: SignalAnalysis? = null

    // 최대 조회 일수 (전체 기간)
    private val maxDays = 730

    init {
        loadSearchHistory()
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

    /**
     * 검색 히스토리 로드 - STOCK 타입만
     */
    private fun loadSearchHistory() {
        viewModelScope.launch {
            try {
                // 설정에서 히스토리 개수 가져오기 (기본값: 15)
                val limitStr = etfDao.getSetting("search_history_limit")
                val limit = limitStr?.toIntOrNull() ?: 15

                searchHistoryDao.getRecentSearchesByType(SearchHistoryType.STOCK, limit).collect { history ->
                    _searchHistory.value = history
                }
            } catch (e: CancellationException) {
                // Expected when ViewModel is cleared, rethrow to propagate cancellation
                throw e
            } catch (e: Exception) {
                android.util.Log.e("OscillatorViewModel", "Error loading search history", e)
            }
        }
    }

    /**
     * 검색 히스토리에 저장 - STOCK 타입으로
     */
    private suspend fun saveToHistory(ticker: String, name: String, market: String) {
        try {
            val limitStr = etfDao.getSetting("search_history_limit")
            val limit = limitStr?.toIntOrNull() ?: 15

            // 기존 동일 종목 + 타입 삭제 (중복 방지)
            searchHistoryDao.deleteByTickerAndType(ticker, SearchHistoryType.STOCK)

            // 새 히스토리 추가
            searchHistoryDao.insertSearch(
                SearchHistory(
                    ticker = ticker,
                    name = name,
                    market = market,
                    historyType = SearchHistoryType.STOCK
                )
            )

            // 오래된 히스토리 삭제 (limit 개수 초과분) - STOCK 타입만
            searchHistoryDao.deleteOldSearchesByType(SearchHistoryType.STOCK, limit)

        } catch (e: Exception) {
            android.util.Log.e("OscillatorViewModel", "Error saving search history", e)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query

        // 검색어가 비어있으면 제안 초기화
        if (query.isBlank()) {
            _suggestions.value = emptyList()
            return
        }

        // 이전 검색 작업 취소
        searchJob?.cancel()

        // Debounce: 300ms 후에 검색 실행
        searchJob = viewModelScope.launch {
            delay(300)
            searchStockSuggestions(query)
        }
    }

    private suspend fun searchStockSuggestions(query: String) {
        try {
            val stocks = stockRepository.searchStocks(query)
                .flowOn(Dispatchers.IO)
                .first()
            _suggestions.value = stocks.take(10) // 최대 10개만 표시
        } catch (e: Exception) {
            android.util.Log.e("OscillatorViewModel", "Error searching stocks", e)
            _suggestions.value = emptyList()
        }
    }

    fun onClearSuggestions() {
        _suggestions.value = emptyList()
    }

    fun searchAndAnalyze(query: String, days: Int? = null) {
        // 항상 최대 일수로 조회하여 캐시 (클라이언트 사이드 필터링용)
        val fetchDays = days ?: maxDays

        viewModelScope.launch {
            try {
                _state.value = OscillatorState.Loading

                // 1. 종목 검색
                val searchResult = stockDataClient.searchStock(query)
                if (searchResult == null) {
                    _state.value = OscillatorState.Error("종목을 찾을 수 없습니다")
                    return@launch
                }

                val (ticker, _) = searchResult
                _currentTicker.value = ticker

                // 2. 종목 데이터 수집 (DB 캐시 활용) - 항상 최대 일수로 조회
                val stockData = stockAnalysisRepository.getStockAnalysis(ticker, fetchDays)
                if (stockData == null) {
                    _state.value = OscillatorState.Error("데이터를 가져올 수 없습니다")
                    return@launch
                }

                // 전체 데이터 캐시
                fullStockData = stockData

                // 3. 검색 히스토리에 저장
                val stock = stockRepository.searchStocks(ticker)
                    .flowOn(Dispatchers.IO)
                    .first()
                    .firstOrNull()
                if (stock != null) {
                    saveToHistory(stock.ticker, stock.name, stock.market)
                }

                // 선택된 기간으로 필터링
                val filteredData = filterStockDataByRange(stockData, _selectedRange.value)

                // 4. 오실레이터 계산 (필터링된 데이터 사용)
                val oscillatorResult = OscillatorCalculator.calculate(filteredData)

                // 5. 신호 분석
                val signalAnalysis = OscillatorCalculator.analyzeSignal(oscillatorResult)

                // 전체 데이터 기반 결과도 캐시
                val fullResult = OscillatorCalculator.calculate(stockData)
                fullOscillatorResult = fullResult
                fullSignalAnalysis = OscillatorCalculator.analyzeSignal(fullResult)

                // 6. 추세 시그널 데이터 수집 (선택된 인터벌)
                val trendSignalData = try {
                    trendSignalCalc.calculateTrendSignal(ticker, days = 365, interval = _trendSignalInterval.value)
                } catch (e: Exception) {
                    android.util.Log.e("OscillatorViewModel", "Trend signal error", e)
                    null
                }

                // 7. 추세 시그널 분석
                val trendSignalAnalysis = trendSignalData?.let {
                    TrendSignalCalculator.analyze(it)
                }

                // 8. Elder Impulse 데이터 수집 (선택된 인터벌)
                val elderImpulseData = try {
                    trendSignalCalc.calculateElderImpulse(ticker, interval = _elderImpulseInterval.value)
                } catch (e: Exception) {
                    android.util.Log.e("OscillatorViewModel", "Elder Impulse error", e)
                    null
                }

                // 9. DeMark TD 데이터 수집 (현재 선택된 인터벌)
                val demarkTDData = try {
                    trendSignalCalc.calculateDemarkTD(ticker, interval = _demarkTDInterval.value)
                } catch (e: Exception) {
                    android.util.Log.e("OscillatorViewModel", "DeMark TD error", e)
                    null
                }

                _state.value = OscillatorState.Success(
                    stockData = filteredData,
                    oscillatorResult = oscillatorResult,
                    signalAnalysis = signalAnalysis,
                    trendSignalData = trendSignalData,
                    trendSignalAnalysis = trendSignalAnalysis,
                    elderImpulseData = elderImpulseData,
                    demarkTDData = demarkTDData
                )

            } catch (e: Exception) {
                _state.value = OscillatorState.Error("오류 발생: ${e.message}")
            }
        }
    }

    fun analyzeStock(ticker: String, days: Int? = null, saveHistory: Boolean = true) {
        // 현재 종목 저장
        _currentTicker.value = ticker

        // 항상 최대 일수로 조회하여 캐시 (클라이언트 사이드 필터링용)
        val fetchDays = days ?: maxDays

        viewModelScope.launch {
            try {
                _state.value = OscillatorState.Loading

                // 종목 데이터 수집 (DB 캐시 활용) - 항상 최대 일수로 조회
                val stockData = stockAnalysisRepository.getStockAnalysis(ticker, fetchDays)
                if (stockData == null) {
                    _state.value = OscillatorState.Error("데이터를 가져올 수 없습니다")
                    return@launch
                }

                // 전체 데이터 캐시
                fullStockData = stockData

                // 검색 히스토리에 저장 (FAB 네비게이션 시에는 저장하지 않음)
                if (saveHistory) {
                    val stock = stockRepository.searchStocks(ticker)
                        .flowOn(Dispatchers.IO)
                        .first()
                        .firstOrNull()
                    if (stock != null) {
                        saveToHistory(stock.ticker, stock.name, stock.market)
                    }
                }

                // 선택된 기간으로 필터링
                val filteredData = filterStockDataByRange(stockData, _selectedRange.value)

                // 오실레이터 계산 (필터링된 데이터 사용)
                val oscillatorResult = OscillatorCalculator.calculate(filteredData)

                // 신호 분석
                val signalAnalysis = OscillatorCalculator.analyzeSignal(oscillatorResult)

                // 전체 데이터 기반 결과도 캐시
                val fullResult = OscillatorCalculator.calculate(stockData)
                fullOscillatorResult = fullResult
                fullSignalAnalysis = OscillatorCalculator.analyzeSignal(fullResult)

                // 추세 시그널 데이터 수집 (선택된 인터벌)
                val trendSignalData = try {
                    trendSignalCalc.calculateTrendSignal(ticker, days = 365, interval = _trendSignalInterval.value)
                } catch (e: Exception) {
                    android.util.Log.e("OscillatorViewModel", "Trend signal error", e)
                    null
                }

                // 추세 시그널 분석
                val trendSignalAnalysis = trendSignalData?.let {
                    TrendSignalCalculator.analyze(it)
                }

                // Elder Impulse 데이터 수집 (선택된 인터벌)
                val elderImpulseData = try {
                    trendSignalCalc.calculateElderImpulse(ticker, interval = _elderImpulseInterval.value)
                } catch (e: Exception) {
                    android.util.Log.e("OscillatorViewModel", "Elder Impulse error", e)
                    null
                }

                // DeMark TD 데이터 수집 (현재 선택된 인터벌)
                val demarkTDData = try {
                    trendSignalCalc.calculateDemarkTD(ticker, interval = _demarkTDInterval.value)
                } catch (e: Exception) {
                    android.util.Log.e("OscillatorViewModel", "DeMark TD error", e)
                    null
                }

                _state.value = OscillatorState.Success(
                    stockData = filteredData,
                    oscillatorResult = oscillatorResult,
                    signalAnalysis = signalAnalysis,
                    trendSignalData = trendSignalData,
                    trendSignalAnalysis = trendSignalAnalysis,
                    elderImpulseData = elderImpulseData,
                    demarkTDData = demarkTDData
                )

            } catch (e: Exception) {
                _state.value = OscillatorState.Error("오류 발생: ${e.message}")
            }
        }
    }

    /**
     * 추세 시그널 인터벌 변경
     */
    fun changeTrendSignalInterval(interval: String) {
        val currentState = _state.value
        if (currentState !is OscillatorState.Success) return
        if (interval == _trendSignalInterval.value) return

        val ticker = currentState.stockData.ticker
        _trendSignalInterval.value = interval

        viewModelScope.launch {
            val trendSignalData = try {
                trendSignalCalc.calculateTrendSignal(ticker, days = 365, interval = interval)
            } catch (e: Exception) {
                android.util.Log.e("OscillatorViewModel", "Trend signal error", e)
                null
            }

            val trendSignalAnalysis = trendSignalData?.let {
                TrendSignalCalculator.analyze(it)
            }

            // 데이터 가져오기 성공한 경우에만 업데이트
            if (trendSignalData != null) {
                val updatedState = currentState.copy(
                    trendSignalData = trendSignalData,
                    trendSignalAnalysis = trendSignalAnalysis
                )
                _state.value = updatedState
            }
        }
    }

    /**
     * Elder Impulse 인터벌 변경
     */
    fun changeElderImpulseInterval(interval: String) {
        val currentState = _state.value
        if (currentState !is OscillatorState.Success) return
        if (interval == _elderImpulseInterval.value) return

        val ticker = currentState.stockData.ticker
        _elderImpulseInterval.value = interval

        viewModelScope.launch {
            val elderImpulseData = try {
                trendSignalCalc.calculateElderImpulse(ticker, interval = interval)
            } catch (e: Exception) {
                android.util.Log.e("OscillatorViewModel", "Elder Impulse error", e)
                null
            }

            // 데이터 가져오기 성공한 경우에만 업데이트
            if (elderImpulseData != null) {
                val updatedState = currentState.copy(elderImpulseData = elderImpulseData)
                _state.value = updatedState
            }
        }
    }

    /**
     * DeMark TD 인터벌 변경
     */
    fun changeDemarkTDInterval(interval: String) {
        val currentState = _state.value
        if (currentState !is OscillatorState.Success) return
        if (interval == _demarkTDInterval.value) return

        val ticker = currentState.stockData.ticker
        _demarkTDInterval.value = interval

        viewModelScope.launch {
            val demarkTDData = try {
                trendSignalCalc.calculateDemarkTD(ticker, interval = interval)
            } catch (e: Exception) {
                android.util.Log.e("OscillatorViewModel", "DeMark TD error", e)
                null
            }

            // 데이터 가져오기 성공한 경우에만 업데이트 (null이면 이전 데이터 유지)
            if (demarkTDData != null) {
                val updatedState = currentState.copy(demarkTDData = demarkTDData)
                _state.value = updatedState
            }
        }
    }

    /**
     * StockData를 날짜 범위로 필터링
     */
    private fun filterStockDataByRange(data: StockData, option: DateRangeOption): StockData {
        // 전체 기간이면 필터링 없이 반환
        if (option.days == -1 || data.dates.isEmpty()) return data

        val cutoffDate = LocalDate.now().minusDays(option.days.toLong())
        val cutoffStr = cutoffDate.toString()

        val startIndex = data.dates.indexOfFirst { it >= cutoffStr }
        if (startIndex < 0) return data

        return StockData(
            ticker = data.ticker,
            name = data.name,
            dates = data.dates.drop(startIndex),
            marketCap = data.marketCap.drop(startIndex),
            foreign5d = data.foreign5d.drop(startIndex),
            institution5d = data.institution5d.drop(startIndex)
        )
    }

    /**
     * 캐시된 데이터에 날짜 범위 필터 적용
     * 로딩 상태 전환 없이 클라이언트 사이드 필터링
     */
    private fun applyDateRangeFilter() {
        val currentState = _state.value
        val cachedData = fullStockData

        if (currentState is OscillatorState.Success && cachedData != null) {
            // 클라이언트 사이드 필터링 - 데이터 리로드 없음
            val filteredData = filterStockDataByRange(cachedData, _selectedRange.value)
            val oscillatorResult = OscillatorCalculator.calculate(filteredData)
            val signalAnalysis = OscillatorCalculator.analyzeSignal(oscillatorResult)

            _state.value = currentState.copy(
                stockData = filteredData,
                oscillatorResult = oscillatorResult,
                signalAnalysis = signalAnalysis
                // trendSignalData, elderImpulseData, demarkTDData는 유지
            )
        }
    }

    /**
     * 날짜 범위 변경 - 클라이언트 사이드 필터링 사용
     */
    fun updateDateRange(option: DateRangeOption) {
        if (option == _selectedRange.value) return
        _selectedRange.value = option

        // 캐시된 데이터가 있으면 클라이언트 사이드 필터링
        if (fullStockData != null && _state.value is OscillatorState.Success) {
            applyDateRangeFilter()
        } else {
            // 캐시가 없으면 전체 데이터 로드 필요
            _currentTicker.value?.let { ticker ->
                analyzeStock(ticker, saveHistory = false)
            }
        }
    }
}
