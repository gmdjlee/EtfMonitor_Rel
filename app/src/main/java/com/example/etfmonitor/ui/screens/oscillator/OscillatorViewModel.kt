package com.etfmonitor.ui.screens.oscillator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.database.EtfDao
import com.etfmonitor.database.SearchHistoryDao
import com.etfmonitor.database.entities.SearchHistory
import com.etfmonitor.database.entities.Stock
import com.etfmonitor.oscillator.calculator.OscillatorCalculator
import com.etfmonitor.oscillator.model.*
import com.etfmonitor.oscillator.python.OscillatorPyClient
import com.etfmonitor.repository.StockAnalysisRepository
import com.etfmonitor.repository.StockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class OscillatorState {
    data object Idle : OscillatorState()
    data object Loading : OscillatorState()
    data class Success(
        val stockData: StockData,
        val oscillatorResult: OscillatorResult,
        val signalAnalysis: SignalAnalysis
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
    private val pyClient: OscillatorPyClient,
    private val stockRepository: StockRepository,
    private val stockAnalysisRepository: StockAnalysisRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val etfDao: EtfDao
) : ViewModel() {

    private val _state = MutableStateFlow<OscillatorState>(OscillatorState.Idle)
    val state: StateFlow<OscillatorState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _suggestions = MutableStateFlow<List<Stock>>(emptyList())
    val suggestions: StateFlow<List<Stock>> = _suggestions.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<SearchHistory>>(emptyList())
    val searchHistory: StateFlow<List<SearchHistory>> = _searchHistory.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadSearchHistory()
    }

    /**
     * 검색 히스토리 로드
     */
    private fun loadSearchHistory() {
        viewModelScope.launch {
            try {
                // 설정에서 히스토리 개수 가져오기 (기본값: 15)
                val limitStr = etfDao.getSetting("search_history_limit")
                val limit = limitStr?.toIntOrNull() ?: 15

                searchHistoryDao.getRecentSearches(limit).collect { history ->
                    _searchHistory.value = history
                }
            } catch (e: Exception) {
                android.util.Log.e("OscillatorViewModel", "Error loading search history", e)
            }
        }
    }

    /**
     * 검색 히스토리에 저장
     */
    private suspend fun saveToHistory(ticker: String, name: String, market: String) {
        try {
            val limitStr = etfDao.getSetting("search_history_limit")
            val limit = limitStr?.toIntOrNull() ?: 15

            // 기존 동일 종목 삭제 (중복 방지)
            searchHistoryDao.deleteByTicker(ticker)

            // 새 히스토리 추가
            searchHistoryDao.insertSearch(
                SearchHistory(
                    ticker = ticker,
                    name = name,
                    market = market
                )
            )

            // 오래된 히스토리 삭제 (limit 개수 초과분)
            searchHistoryDao.deleteOldSearches(limit)

        } catch (e: Exception) {
            android.util.Log.e("OscillatorViewModel", "Error saving search history", e)
        }
    }

    fun updateSearchQuery(query: String) {
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

    fun clearSuggestions() {
        _suggestions.value = emptyList()
    }

    fun searchAndAnalyze(query: String, days: Int = 180) {
        viewModelScope.launch {
            try {
                _state.value = OscillatorState.Loading

                // 1. 종목 검색
                val searchResult = pyClient.searchStock(query)
                if (searchResult == null) {
                    _state.value = OscillatorState.Error("종목을 찾을 수 없습니다")
                    return@launch
                }

                val (ticker, _) = searchResult

                // 2. 종목 데이터 수집 (DB 캐시 활용)
                val stockData = stockAnalysisRepository.getStockAnalysis(ticker, days)
                if (stockData == null) {
                    _state.value = OscillatorState.Error("데이터를 가져올 수 없습니다")
                    return@launch
                }

                // 3. 검색 히스토리에 저장
                val stock = stockRepository.searchStocks(ticker)
                    .flowOn(Dispatchers.IO)
                    .first()
                    .firstOrNull()
                if (stock != null) {
                    saveToHistory(stock.ticker, stock.name, stock.market)
                }

                // 4. 오실레이터 계산
                val oscillatorResult = OscillatorCalculator.calculate(stockData)

                // 5. 신호 분석
                val signalAnalysis = OscillatorCalculator.analyzeSignal(oscillatorResult)

                _state.value = OscillatorState.Success(
                    stockData = stockData,
                    oscillatorResult = oscillatorResult,
                    signalAnalysis = signalAnalysis
                )

            } catch (e: Exception) {
                _state.value = OscillatorState.Error("오류 발생: ${e.message}")
            }
        }
    }

    fun analyzeStock(ticker: String, days: Int = 180) {
        viewModelScope.launch {
            try {
                _state.value = OscillatorState.Loading

                // 종목 데이터 수집 (DB 캐시 활용)
                val stockData = stockAnalysisRepository.getStockAnalysis(ticker, days)
                if (stockData == null) {
                    _state.value = OscillatorState.Error("데이터를 가져올 수 없습니다")
                    return@launch
                }

                // 검색 히스토리에 저장
                val stock = stockRepository.searchStocks(ticker)
                    .flowOn(Dispatchers.IO)
                    .first()
                    .firstOrNull()
                if (stock != null) {
                    saveToHistory(stock.ticker, stock.name, stock.market)
                }

                // 오실레이터 계산
                val oscillatorResult = OscillatorCalculator.calculate(stockData)

                // 신호 분석
                val signalAnalysis = OscillatorCalculator.analyzeSignal(oscillatorResult)

                _state.value = OscillatorState.Success(
                    stockData = stockData,
                    oscillatorResult = oscillatorResult,
                    signalAnalysis = signalAnalysis
                )

            } catch (e: Exception) {
                _state.value = OscillatorState.Error("오류 발생: ${e.message}")
            }
        }
    }
}
