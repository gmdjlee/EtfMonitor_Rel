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
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.domain.usecase.krx.GetTrendSignalDataUseCase
import com.etfmonitor.core.domain.usecase.krx.GetElderImpulseDataUseCase
import com.etfmonitor.core.domain.usecase.krx.GetDemarkTDDataUseCase
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
import java.time.format.DateTimeFormatter
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
 * T-012 MIGRATION (pykrx → kotlin_krx):
 * - Replaced OscillatorPyClient with 3 kotlin_krx UseCases
 * - searchStock: Uses existing stockRepository.searchStocks() (DB-based)
 * - getTrendSignalData: GetTrendSignalDataUseCase
 * - getElderImpulseData: GetElderImpulseDataUseCase
 * - getDemarkTDData: GetDemarkTDDataUseCase
 *
 * 기존 문제점 해결:
 * - EtfMonitorApp.instance 제거: 메모리 누수 위험 제거
 * - 수동 Factory 제거: Hilt가 자동으로 관리하여 코드 간결화
 */
@HiltViewModel
class OscillatorViewModel @Inject constructor(
    private val getTrendSignalDataUseCase: GetTrendSignalDataUseCase,
    private val getElderImpulseDataUseCase: GetElderImpulseDataUseCase,
    private val getDemarkTDDataUseCase: GetDemarkTDDataUseCase,
    private val stockRepository: StockRepository,
    private val stockAnalysisRepository: StockAnalysisRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val etfDao: EtfDao
) : ViewModel() {

    companion object {
        private const val QUICK_CHART_ANALYSIS_KEY = "quick_chart_analysis_enabled"
        private val logger = AppLogger.getLogger("OscillatorViewModel")
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
    private var fullTrendSignalData: TrendSignalData? = null
    private var fullElderImpulseData: ElderImpulseData? = null
    private var fullDemarkTDData: DemarkTDData? = null

    // 최대 조회 일수 (전체 기간)
    // WORKAROUND: Reduced from 730 to 365 due to kotlin_krx date chunking bug
    // TODO: Increase back to 730 after fixing kotlin_krx multi-chunk support
    private val maxDays = 365

    // ============================================================
    // 날짜 오름차순 정렬 헬퍼 (kotlin_krx는 역순 반환)
    // filterStockDataByRange 등 필터 함수가 오름차순을 전제하므로,
    // 캐시 시점에 한 번만 정렬하여 모든 필터가 정상 동작하도록 함.
    // ============================================================

    private fun StockData.sortedByDateAsc(): StockData {
        if (dates.size <= 1 || dates.first() <= dates.last()) return this
        return StockData(
            ticker = ticker, name = name,
            dates = dates.reversed(),
            marketCap = marketCap.reversed(),
            foreign5d = foreign5d.reversed(),
            institution5d = institution5d.reversed()
        )
    }

    private fun TrendSignalData.sortedByDateAsc(): TrendSignalData {
        if (dates.size <= 1 || dates.first() <= dates.last()) return this
        return TrendSignalData(
            ticker = ticker, name = name, interval = interval,
            dates = dates.reversed(), open = open.reversed(),
            high = high.reversed(), low = low.reversed(),
            close = close.reversed(), volume = volume.reversed(),
            ma = ma.reversed(), cmf = cmf.reversed(),
            fearGreed = fearGreed.reversed(),
            buySignal = buySignal.reversed(), auxBuySignal = auxBuySignal.reversed(),
            sellSignal = sellSignal.reversed(), auxSellSignal = auxSellSignal.reversed()
        )
    }

    private fun ElderImpulseData.sortedByDateAsc(): ElderImpulseData {
        if (dates.size <= 1 || dates.first() <= dates.last()) return this
        return ElderImpulseData(
            ticker = ticker, name = name, interval = interval,
            dates = dates.reversed(), close = close.reversed(),
            marketCap = marketCap.reversed(), ema = ema.reversed(),
            macd = macd.reversed(), macdSignal = macdSignal.reversed(),
            macdHist = macdHist.reversed(), impulse = impulse.reversed()
        )
    }

    private fun DemarkTDData.sortedByDateAsc(): DemarkTDData {
        if (dates.size <= 1 || dates.first() <= dates.last()) return this
        return DemarkTDData(
            ticker = ticker, name = name, interval = interval,
            intervalName = intervalName,
            dates = dates.reversed(), close = close.reversed(),
            marketCap = marketCap.reversed(),
            tdSell = tdSell.reversed(), tdBuy = tdBuy.reversed()
        )
    }

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
            } catch (e: CancellationException) {
                throw e
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
                logger.e("Error loading search history", e)
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

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Error saving search history", e)
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Error searching stocks", e)
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

                // 1. 종목 검색 (DB-based via stockRepository)
                val stocks = stockRepository.searchStocks(query)
                    .flowOn(Dispatchers.IO)
                    .first()

                val stock = stocks.firstOrNull { it.ticker.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true) }
                    ?: if (query.length == 6 && query.all { it.isDigit() }) {
                        // Fallback: assume query is direct ticker
                        stocks.firstOrNull { it.ticker == query }
                    } else null

                if (stock == null) {
                    _state.value = OscillatorState.Error("종목을 찾을 수 없습니다")
                    return@launch
                }

                val ticker = stock.ticker
                _currentTicker.value = ticker

                // 2. 종목 데이터 수집 (DB 캐시 활용) - 항상 최대 일수로 조회
                val stockData = stockAnalysisRepository.getStockAnalysis(ticker, fetchDays)
                if (stockData == null) {
                    _state.value = OscillatorState.Error("데이터를 가져올 수 없습니다")
                    return@launch
                }

                // ========== CHECKPOINT 4: ViewModel INPUT (from Repository) ==========
                AppLogger.getLogger("OscillatorViewModel").d("========== CHECKPOINT 4: ViewModel INPUT ==========")
                AppLogger.getLogger("OscillatorViewModel").d("  Received StockData:")
                AppLogger.getLogger("OscillatorViewModel").d("    ticker: ${stockData.ticker}")
                AppLogger.getLogger("OscillatorViewModel").d("    name: ${stockData.name}")
                AppLogger.getLogger("OscillatorViewModel").d("    dates: ${stockData.dates.size} records")
                AppLogger.getLogger("OscillatorViewModel").d("    marketCap sample (first 3): ${stockData.marketCap.take(3)}")
                AppLogger.getLogger("OscillatorViewModel").d("    foreign5d sample (first 3): ${stockData.foreign5d.take(3)}")
                AppLogger.getLogger("OscillatorViewModel").d("    institution5d sample (first 3): ${stockData.institution5d.take(3)}")

                // 전체 데이터 캐시 (오름차순 정렬)
                val sortedStockData = stockData.sortedByDateAsc()
                fullStockData = sortedStockData

                // 3. 검색 히스토리에 저장 (already have stock from step 1)
                saveToHistory(stock.ticker, stock.name, stock.market)

                // 선택된 기간으로 필터링
                val filteredData = filterStockDataByRange(sortedStockData, _selectedRange.value)

                // ========== CHECKPOINT 5: Filtered Data (for Calculator) ==========
                AppLogger.getLogger("OscillatorViewModel").d("========== CHECKPOINT 5: Filtered Data ==========")
                AppLogger.getLogger("OscillatorViewModel").d("  Range: ${_selectedRange.value}")
                AppLogger.getLogger("OscillatorViewModel").d("  Filtered dates: ${filteredData.dates.size} records")
                AppLogger.getLogger("OscillatorViewModel").d("  Filtered marketCap sample (first 3): ${filteredData.marketCap.take(3)}")

                // 4. 오실레이터 계산 (필터링된 데이터 사용)
                val oscillatorResult = OscillatorCalculator.calculate(filteredData)

                // ========== CHECKPOINT 6: Calculator OUTPUT ==========
                AppLogger.getLogger("OscillatorViewModel").d("========== CHECKPOINT 6: Calculator OUTPUT ==========")
                AppLogger.getLogger("OscillatorViewModel").d("  Oscillator values (first 3): ${oscillatorResult.oscillator.take(3)}")
                AppLogger.getLogger("OscillatorViewModel").d("  EMA values (first 3): ${oscillatorResult.ema.take(3)}")
                AppLogger.getLogger("OscillatorViewModel").d("  MACD values (first 3): ${oscillatorResult.macd.take(3)}")

                // 5. 신호 분석
                val signalAnalysis = OscillatorCalculator.analyzeSignal(oscillatorResult)

                // 전체 데이터 기반 결과도 캐시
                val fullResult = OscillatorCalculator.calculate(sortedStockData)
                fullOscillatorResult = fullResult
                fullSignalAnalysis = OscillatorCalculator.analyzeSignal(fullResult)

                // 6. 추세 시그널 데이터 수집 (선택된 인터벌) - kotlin_krx
                val trendSignalData = try {
                    getTrendSignalDataUseCase(ticker, days = 365, interval = _trendSignalInterval.value)
                        ?.sortedByDateAsc()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e("Trend signal error", e)
                    null
                }

                // 7. 추세 시그널 분석
                val trendSignalAnalysis = trendSignalData?.let {
                    TrendSignalCalculator.analyze(it)
                }

                // 8. Elder Impulse 데이터 수집 (선택된 인터벌) - kotlin_krx
                val elderImpulseData = try {
                    getElderImpulseDataUseCase(ticker, interval = _elderImpulseInterval.value)
                        ?.sortedByDateAsc()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e("Elder Impulse error", e)
                    null
                }

                // 9. DeMark TD 데이터 수집 (현재 선택된 인터벌) - kotlin_krx
                val demarkTDData = try {
                    getDemarkTDDataUseCase(ticker, interval = _demarkTDInterval.value)
                        ?.sortedByDateAsc()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e("DeMark TD error", e)
                    null
                }

                // 추가 차트 데이터도 캐시 (이미 오름차순 정렬됨)
                fullTrendSignalData = trendSignalData
                fullElderImpulseData = elderImpulseData
                fullDemarkTDData = demarkTDData

                _state.value = OscillatorState.Success(
                    stockData = filteredData,
                    oscillatorResult = oscillatorResult,
                    signalAnalysis = signalAnalysis,
                    trendSignalData = trendSignalData,
                    trendSignalAnalysis = trendSignalAnalysis,
                    elderImpulseData = elderImpulseData,
                    demarkTDData = demarkTDData
                )

            } catch (e: CancellationException) {
                throw e
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

                // 전체 데이터 캐시 (오름차순 정렬)
                val sortedStockData = stockData.sortedByDateAsc()
                fullStockData = sortedStockData

                // 검색 히스토리에 저장 (FAB 네비게이션 시에는 저장하지 않음)
                if (saveHistory) {
                    val stockItem = stockRepository.searchStocks(ticker)
                        .flowOn(Dispatchers.IO)
                        .first()
                        .firstOrNull()
                    if (stockItem != null) {
                        saveToHistory(stockItem.ticker, stockItem.name, stockItem.market)
                    }
                }

                // 선택된 기간으로 필터링
                val filteredData = filterStockDataByRange(sortedStockData, _selectedRange.value)

                // 오실레이터 계산 (필터링된 데이터 사용)
                val oscillatorResult = OscillatorCalculator.calculate(filteredData)

                // 신호 분석
                val signalAnalysis = OscillatorCalculator.analyzeSignal(oscillatorResult)

                // 전체 데이터 기반 결과도 캐시
                val fullResult = OscillatorCalculator.calculate(sortedStockData)
                fullOscillatorResult = fullResult
                fullSignalAnalysis = OscillatorCalculator.analyzeSignal(fullResult)

                // 추세 시그널 데이터 수집 (선택된 인터벌) - kotlin_krx
                val trendSignalData = try {
                    getTrendSignalDataUseCase(ticker, days = 365, interval = _trendSignalInterval.value)
                        ?.sortedByDateAsc()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e("Trend signal error", e)
                    null
                }

                // 추세 시그널 분석
                val trendSignalAnalysis = trendSignalData?.let {
                    TrendSignalCalculator.analyze(it)
                }

                // Elder Impulse 데이터 수집 (선택된 인터벌) - kotlin_krx
                val elderImpulseData = try {
                    getElderImpulseDataUseCase(ticker, interval = _elderImpulseInterval.value)
                        ?.sortedByDateAsc()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e("Elder Impulse error", e)
                    null
                }

                // DeMark TD 데이터 수집 (현재 선택된 인터벌) - kotlin_krx
                val demarkTDData = try {
                    getDemarkTDDataUseCase(ticker, interval = _demarkTDInterval.value)
                        ?.sortedByDateAsc()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e("DeMark TD error", e)
                    null
                }

                // 추가 차트 데이터도 캐시 (이미 오름차순 정렬됨)
                fullTrendSignalData = trendSignalData
                fullElderImpulseData = elderImpulseData
                fullDemarkTDData = demarkTDData

                _state.value = OscillatorState.Success(
                    stockData = filteredData,
                    oscillatorResult = oscillatorResult,
                    signalAnalysis = signalAnalysis,
                    trendSignalData = trendSignalData,
                    trendSignalAnalysis = trendSignalAnalysis,
                    elderImpulseData = elderImpulseData,
                    demarkTDData = demarkTDData
                )

            } catch (e: CancellationException) {
                throw e
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
                getTrendSignalDataUseCase(ticker, days = 365, interval = interval)
                    ?.sortedByDateAsc()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Trend signal error", e)
                null
            }

            val trendSignalAnalysis = trendSignalData?.let {
                TrendSignalCalculator.analyze(it)
            }

            // 데이터 가져오기 성공한 경우에만 업데이트
            if (trendSignalData != null) {
                fullTrendSignalData = trendSignalData
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
                getElderImpulseDataUseCase(ticker, interval = interval)
                    ?.sortedByDateAsc()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Elder Impulse error", e)
                null
            }

            // 데이터 가져오기 성공한 경우에만 업데이트
            if (elderImpulseData != null) {
                fullElderImpulseData = elderImpulseData
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
                getDemarkTDDataUseCase(ticker, interval = interval)
                    ?.sortedByDateAsc()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("DeMark TD error", e)
                null
            }

            // 데이터 가져오기 성공한 경우에만 업데이트 (null이면 이전 데이터 유지)
            if (demarkTDData != null) {
                fullDemarkTDData = demarkTDData
                val updatedState = currentState.copy(demarkTDData = demarkTDData)
                _state.value = updatedState
            }
        }
    }

    /**
     * StockData를 날짜 범위로 필터링
     */
    private fun filterStockDataByRange(data: StockData, option: DateRangeOption): StockData {
        val log = AppLogger.getLogger("OscillatorVM.Filter")
        log.d("========== filterStockDataByRange ==========")
        log.d("  option: ${option.name} (${option.label}, days=${option.days})")
        log.d("  input dates: ${data.dates.size} records")
        log.d("  input dates range: ${data.dates.firstOrNull()} ~ ${data.dates.lastOrNull()}")
        log.d("  input dates sample (first 3): ${data.dates.take(3)}")

        // 전체 기간이면 필터링 없이 반환
        if (option.days == -1 || data.dates.isEmpty()) {
            log.d("  → No filtering (days=-1 or empty). Returning all ${data.dates.size} records.")
            return data
        }

        val cutoffDate = LocalDate.now().minusDays(option.days.toLong())
        val cutoffStr = cutoffDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        log.d("  cutoffDate: $cutoffDate → cutoffStr: '$cutoffStr'")

        val startIndex = data.dates.indexOfFirst { it >= cutoffStr }
        log.d("  startIndex: $startIndex (first date >= cutoffStr)")

        if (startIndex < 0) {
            log.d("  → No date >= cutoffStr found. Returning all ${data.dates.size} records (no filtering).")
            return data
        }

        val filteredSize = data.dates.size - startIndex
        log.d("  → Filtering: dropping $startIndex records, keeping $filteredSize records")
        log.d("  → Filtered dates range: ${data.dates[startIndex]} ~ ${data.dates.lastOrNull()}")

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
     * TrendSignalData를 날짜 범위로 필터링
     */
    private fun filterTrendSignalDataByRange(data: TrendSignalData?, option: DateRangeOption): TrendSignalData? {
        if (data == null) return null
        if (option.days == -1 || data.dates.isEmpty()) return data

        val cutoffDate = LocalDate.now().minusDays(option.days.toLong())
        val cutoffStr = cutoffDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

        val startIndex = data.dates.indexOfFirst { it >= cutoffStr }
        if (startIndex < 0) return data

        return TrendSignalData(
            ticker = data.ticker,
            name = data.name,
            interval = data.interval,
            dates = data.dates.drop(startIndex),
            open = data.open.drop(startIndex),
            high = data.high.drop(startIndex),
            low = data.low.drop(startIndex),
            close = data.close.drop(startIndex),
            volume = data.volume.drop(startIndex),
            ma = data.ma.drop(startIndex),
            cmf = data.cmf.drop(startIndex),
            fearGreed = data.fearGreed.drop(startIndex),
            buySignal = data.buySignal.drop(startIndex),
            auxBuySignal = data.auxBuySignal.drop(startIndex),
            sellSignal = data.sellSignal.drop(startIndex),
            auxSellSignal = data.auxSellSignal.drop(startIndex)
        )
    }

    /**
     * ElderImpulseData를 날짜 범위로 필터링
     */
    private fun filterElderImpulseDataByRange(data: ElderImpulseData?, option: DateRangeOption): ElderImpulseData? {
        if (data == null) return null
        if (option.days == -1 || data.dates.isEmpty()) return data

        val cutoffDate = LocalDate.now().minusDays(option.days.toLong())
        val cutoffStr = cutoffDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

        val startIndex = data.dates.indexOfFirst { it >= cutoffStr }
        if (startIndex < 0) return data

        return ElderImpulseData(
            ticker = data.ticker,
            name = data.name,
            interval = data.interval,
            dates = data.dates.drop(startIndex),
            close = data.close.drop(startIndex),
            marketCap = data.marketCap.drop(startIndex),
            ema = data.ema.drop(startIndex),
            macd = data.macd.drop(startIndex),
            macdSignal = data.macdSignal.drop(startIndex),
            macdHist = data.macdHist.drop(startIndex),
            impulse = data.impulse.drop(startIndex)
        )
    }

    /**
     * DemarkTDData를 날짜 범위로 필터링
     */
    private fun filterDemarkTDDataByRange(data: DemarkTDData?, option: DateRangeOption): DemarkTDData? {
        if (data == null) return null
        if (option.days == -1 || data.dates.isEmpty()) return data

        val cutoffDate = LocalDate.now().minusDays(option.days.toLong())
        val cutoffStr = cutoffDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

        val startIndex = data.dates.indexOfFirst { it >= cutoffStr }
        if (startIndex < 0) return data

        return DemarkTDData(
            ticker = data.ticker,
            name = data.name,
            interval = data.interval,
            intervalName = data.intervalName,
            dates = data.dates.drop(startIndex),
            close = data.close.drop(startIndex),
            marketCap = data.marketCap.drop(startIndex),
            tdSell = data.tdSell.drop(startIndex),
            tdBuy = data.tdBuy.drop(startIndex)
        )
    }

    /**
     * 캐시된 데이터에 날짜 범위 필터 적용
     * 로딩 상태 전환 없이 클라이언트 사이드 필터링
     */
    private fun applyDateRangeFilter() {
        val log = AppLogger.getLogger("OscillatorVM.ApplyFilter")
        log.d("========== applyDateRangeFilter ==========")

        val currentState = _state.value
        val cachedData = fullStockData

        if (currentState is OscillatorState.Success && cachedData != null) {
            log.d("  selectedRange: ${_selectedRange.value.name} (${_selectedRange.value.label}, days=${_selectedRange.value.days})")
            log.d("  cachedData: ${cachedData.dates.size} records, range: ${cachedData.dates.firstOrNull()} ~ ${cachedData.dates.lastOrNull()}")

            // 클라이언트 사이드 필터링 - 데이터 리로드 없음
            val filteredData = filterStockDataByRange(cachedData, _selectedRange.value)
            val oscillatorResult = OscillatorCalculator.calculate(filteredData)

            log.d("  [시가총액 & 수급 오실레이터 / MACD] After filter+calculate:")
            log.d("    filteredData.dates: ${filteredData.dates.size} records")
            log.d("    filteredData.marketCap: ${filteredData.marketCap.size} records, sample(first 3): ${filteredData.marketCap.take(3)}")
            log.d("    filteredData.foreign5d: ${filteredData.foreign5d.size} records, sample(first 3): ${filteredData.foreign5d.take(3)}")
            log.d("    filteredData.institution5d: ${filteredData.institution5d.size} records, sample(first 3): ${filteredData.institution5d.take(3)}")
            log.d("    oscillatorResult.dates: ${oscillatorResult.dates.size}")
            log.d("    oscillatorResult.oscillator: ${oscillatorResult.oscillator.size} values, sample(last 3): ${oscillatorResult.oscillator.takeLast(3)}")
            log.d("    oscillatorResult.macd: ${oscillatorResult.macd.size} values, sample(last 3): ${oscillatorResult.macd.takeLast(3)}")
            log.d("    oscillatorResult.signal: ${oscillatorResult.signal.size} values, sample(last 3): ${oscillatorResult.signal.takeLast(3)}")

            val signalAnalysis = OscillatorCalculator.analyzeSignal(oscillatorResult)

            // 추가 차트 데이터도 필터링
            val filteredTrendSignalData = filterTrendSignalDataByRange(fullTrendSignalData, _selectedRange.value)
            val filteredTrendSignalAnalysis = filteredTrendSignalData?.let {
                TrendSignalCalculator.analyze(it)
            }
            val filteredElderImpulseData = filterElderImpulseDataByRange(fullElderImpulseData, _selectedRange.value)
            val filteredDemarkTDData = filterDemarkTDDataByRange(fullDemarkTDData, _selectedRange.value)

            log.d("  State updated. filteredData=${filteredData.dates.size}, oscillator=${oscillatorResult.oscillator.size}, macd=${oscillatorResult.macd.size}")

            _state.value = currentState.copy(
                stockData = filteredData,
                oscillatorResult = oscillatorResult,
                signalAnalysis = signalAnalysis,
                trendSignalData = filteredTrendSignalData,
                trendSignalAnalysis = filteredTrendSignalAnalysis,
                elderImpulseData = filteredElderImpulseData,
                demarkTDData = filteredDemarkTDData
            )
        } else {
            log.d("  → Cannot apply filter: isSuccess=${currentState is OscillatorState.Success}, hasCachedData=${cachedData != null}")
        }
    }

    /**
     * 날짜 범위 변경 - 클라이언트 사이드 필터링 사용
     */
    fun updateDateRange(option: DateRangeOption) {
        val log = AppLogger.getLogger("OscillatorVM.DateRange")
        log.d("========== updateDateRange ==========")
        log.d("  requested: ${option.name} (${option.label}, days=${option.days})")
        log.d("  current: ${_selectedRange.value.name} (${_selectedRange.value.label})")

        if (option == _selectedRange.value) {
            log.d("  → Same as current. Skipping.")
            return
        }
        _selectedRange.value = option

        val hasCachedData = fullStockData != null
        val isSuccess = _state.value is OscillatorState.Success
        log.d("  hasCachedData: $hasCachedData, isSuccessState: $isSuccess")
        log.d("  cachedData dates: ${fullStockData?.dates?.size ?: 0} records")

        // 캐시된 데이터가 있으면 클라이언트 사이드 필터링
        if (hasCachedData && isSuccess) {
            log.d("  → Applying client-side filter (applyDateRangeFilter)")
            applyDateRangeFilter()
        } else {
            // 캐시가 없으면 전체 데이터 로드 필요
            log.d("  → No cache. Reloading data for ticker: ${_currentTicker.value}")
            _currentTicker.value?.let { ticker ->
                analyzeStock(ticker, saveHistory = false)
            }
        }
    }
}
