package com.etfmonitor.ui.screens.oscillator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chaquo.python.Python
import com.etfmonitor.EtfMonitorApp
import com.etfmonitor.database.entities.Stock
import com.etfmonitor.oscillator.calculator.OscillatorCalculator
import com.etfmonitor.oscillator.model.*
import com.etfmonitor.oscillator.python.OscillatorPyClient
import com.etfmonitor.repository.StockRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

class OscillatorViewModel(
    application: Application,
    private val pyClient: OscillatorPyClient,
    private val stockRepository: StockRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<OscillatorState>(OscillatorState.Idle)
    val state: StateFlow<OscillatorState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _suggestions = MutableStateFlow<List<Stock>>(emptyList())
    val suggestions: StateFlow<List<Stock>> = _suggestions.asStateFlow()

    private var searchJob: Job? = null

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
        viewModelScope.launch {
            try {
                stockRepository.searchStocks(query).collect { stocks ->
                    _suggestions.value = stocks.take(10) // 최대 10개만 표시
                }
            } catch (e: Exception) {
                android.util.Log.e("OscillatorViewModel", "Error searching stocks", e)
                _suggestions.value = emptyList()
            }
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

                // 2. 종목 데이터 수집
                val stockData = pyClient.getStockAnalysis(ticker, days)
                if (stockData == null) {
                    _state.value = OscillatorState.Error("데이터를 가져올 수 없습니다")
                    return@launch
                }

                // 3. 오실레이터 계산
                val oscillatorResult = OscillatorCalculator.calculate(stockData)

                // 4. 신호 분석
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

                // 종목 데이터 수집
                val stockData = pyClient.getStockAnalysis(ticker, days)
                if (stockData == null) {
                    _state.value = OscillatorState.Error("데이터를 가져올 수 없습니다")
                    return@launch
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EtfMonitorApp
                val python = app.python
                val pyClient = OscillatorPyClient(python)
                val stockRepository = StockRepository(
                    stockDao = app.database.stockDao(),
                    python = python
                )
                OscillatorViewModel(app, pyClient, stockRepository)
            }
        }
    }
}
