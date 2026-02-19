package com.etfmonitor.feature.market.presentation.oscillator

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.service.CollectionState
import com.etfmonitor.core.ui.component.ChartLabelCalculator
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.feature.market.domain.model.MarketOscillator
import com.etfmonitor.feature.market.domain.repository.MarketOscillatorRepository
import com.etfmonitor.core.ui.theme.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 시장 과매수/과매도 화면 상태
 */
sealed class MarketOscillatorState {
    object Loading : MarketOscillatorState()
    data class Idle(val hasData: Boolean, val latestDate: String?) : MarketOscillatorState()
    data class Initializing(val message: String, val progress: Int) : MarketOscillatorState()
    data class Updating(val message: String) : MarketOscillatorState()
    data class Success(val message: String) : MarketOscillatorState()
    data class Error(val message: String) : MarketOscillatorState()
}

/**
 * Production Level MarketOscillatorViewModel with Hilt
 *
 * 최적화 포인트:
 * 1. @HiltViewModel: Hilt가 ViewModel 생명주기 자동 관리
 * 2. @Inject: 생성자 주입으로 의존성 명확화
 * 3. @ApplicationContext: Application Context 직접 주입
 * 4. Factory 패턴 제거: Hilt가 자동으로 ViewModel 생성
 *
 * 기존 문제점 해결:
 * - EtfMonitorApp.instance 제거: 메모리 누수 위험 제거
 * - 수동 Factory 제거: Hilt가 자동으로 관리하여 코드 간결화
 */
@HiltViewModel
class MarketOscillatorViewModel @Inject constructor(
    private val repository: MarketOscillatorRepository,
    private val themeManager: ThemeManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private val logger = AppLogger.getLogger("MarketOscillatorViewModel")
    }

    // Body 폰트 스케일
    val bodyScale: StateFlow<Float> = themeManager.fontScaleSettings
        .map { it.bodyScale }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    private val _state = MutableStateFlow<MarketOscillatorState>(MarketOscillatorState.Loading)
    val state: StateFlow<MarketOscillatorState> = _state.asStateFlow()

    // 선택된 시장 (KOSPI/KOSDAQ)
    private val _selectedMarket = MutableStateFlow("KOSPI")
    val selectedMarket: StateFlow<String> = _selectedMarket.asStateFlow()

    // 날짜 범위 선택 상태
    private val _selectedRange = MutableStateFlow(DateRangeOption.DEFAULT)
    val selectedRange: StateFlow<DateRangeOption> = _selectedRange.asStateFlow()

    // 시장 데이터
    private val _marketData = MutableStateFlow<List<MarketOscillator>>(emptyList())
    val marketData: StateFlow<List<MarketOscillator>> = _marketData.asStateFlow()

    // 과매수 기준 (기본 80%)
    private val _overboughtThreshold = MutableStateFlow(80.0)
    val overboughtThreshold: StateFlow<Double> = _overboughtThreshold.asStateFlow()

    // 과매도 기준 (기본 -80%)
    private val _oversoldThreshold = MutableStateFlow(-80.0)
    val oversoldThreshold: StateFlow<Double> = _oversoldThreshold.asStateFlow()

    // 첫 실행 다이얼로그 표시 여부
    private val _showFirstRunDialog = MutableStateFlow(false)
    val showFirstRunDialog: StateFlow<Boolean> = _showFirstRunDialog.asStateFlow()

    init {
        checkData()
        observeDateRangeChanges()
        checkFirstRun()
        observeCollectionState()
    }

    /**
     * 시장 및 날짜 범위 변경을 관찰하여 데이터 로딩
     */
    private fun observeDateRangeChanges() {
        viewModelScope.launch {
            combine(_selectedMarket, _selectedRange) { market, range ->
                Pair(market, range)
            }.collectLatest { (market, range) ->
                loadDataByRange(market, range)
            }
        }
    }

    /**
     * 날짜 범위에 따른 데이터 로딩
     */
    private suspend fun loadDataByRange(market: String, range: DateRangeOption) {
        val (startDate, endDate) = ChartLabelCalculator.calculateDateRange(range)
        repository.getDataByDateRange(market, startDate, endDate)
            .collect { data ->
                _marketData.value = data
            }
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
                    // observeDateRangeChanges가 자동으로 데이터를 다시 로드하도록 트리거
                    val currentRange = _selectedRange.value
                    _selectedRange.value = currentRange
                    checkData()
                }
            }
        }
    }

    private fun checkFirstRun() {
        viewModelScope.launch {
            val dialogDismissed = repository.isDialogDismissed()
            val hasData = repository.getDataCount("KOSPI") > 0 ||
                         repository.getDataCount("KOSDAQ") > 0

            // 데이터가 없고 다이얼로그를 본 적이 없으면 표시
            if (!hasData && !dialogDismissed) {
                _showFirstRunDialog.value = true
            }
        }
    }

    fun onFirstRunDialogShown() {
        // "나중에"를 클릭한 경우: 다이얼로그만 닫기
        _showFirstRunDialog.value = false
    }

    fun onFirstRunDialogConfirmed() {
        // "수집 시작"을 클릭한 경우: 다이얼로그 닫고 더 이상 표시하지 않음
        viewModelScope.launch {
            repository.saveDialogDismissed()
            _showFirstRunDialog.value = false
        }
    }

    private fun checkData() {
        viewModelScope.launch {
            val kospiCount = repository.getDataCount("KOSPI")
            val kosdaqCount = repository.getDataCount("KOSDAQ")
            val hasData = kospiCount > 0 || kosdaqCount > 0

            val latestData = repository.getLatestData(_selectedMarket.value)
            _state.value = MarketOscillatorState.Idle(hasData, latestData?.date)
        }
    }

    fun onSelectedMarketChanged(market: String) {
        _selectedMarket.value = market
        // observeDateRangeChanges가 자동으로 데이터 로드 트리거
        checkData()
    }

    /**
     * 날짜 범위 변경
     */
    fun updateDateRange(option: DateRangeOption) {
        _selectedRange.value = option
        // observeDateRangeChanges가 자동으로 데이터 로드 트리거
    }

    fun onOverboughtThresholdChanged(threshold: Double) {
        _overboughtThreshold.value = threshold
    }

    fun onOversoldThresholdChanged(threshold: Double) {
        _oversoldThreshold.value = threshold
    }

    /**
     * 초기 데이터 수집 (12개월)
     */
    fun initialize(days: Int = 365) {
        viewModelScope.launch {
            _state.value = MarketOscillatorState.Initializing("시장 데이터 수집 중...", 0)

            // NonCancellable: 사용자가 화면을 나가도 데이터 수집 완료 보장
            val (kospiResult, kosdaqResult) = withContext(NonCancellable) {
                _state.value = MarketOscillatorState.Initializing("KOSPI 데이터 수집 중...", 25)
                val kospi = repository.initializeMarketData("KOSPI", days)

                _state.value = MarketOscillatorState.Initializing("KOSDAQ 데이터 수집 중...", 50)
                val kosdaq = repository.initializeMarketData("KOSDAQ", days)
                Pair(kospi, kosdaq)
            }

            if (kospiResult.isSuccess && kosdaqResult.isSuccess) {
                val kospiCount = kospiResult.getOrNull() ?: 0
                val kosdaqCount = kosdaqResult.getOrNull() ?: 0
                _state.value = MarketOscillatorState.Success(
                    "KOSPI: $kospiCount, KOSDAQ: $kosdaqCount 개의 데이터를 수집했습니다"
                )
                // 데이터 수집 후 현재 범위로 리로드 트리거
                val currentRange = _selectedRange.value
                _selectedRange.value = currentRange
                checkData()
            } else {
                val error = kospiResult.exceptionOrNull() ?: kosdaqResult.exceptionOrNull()
                _state.value = MarketOscillatorState.Error("데이터 수집 실패: ${error?.message}")
            }
        }
    }

    /**
     * 데이터 업데이트 (최근 30일)
     */
    fun update() {
        viewModelScope.launch {
            _state.value = MarketOscillatorState.Updating("시장 데이터 업데이트 중...")

            // NonCancellable: 사용자가 화면을 나가도 업데이트 완료 보장
            val (kospiResult, kosdaqResult) = withContext(NonCancellable) {
                val kospi = repository.updateMarketData("KOSPI")
                val kosdaq = repository.updateMarketData("KOSDAQ")
                Pair(kospi, kosdaq)
            }

            if (kospiResult.isSuccess && kosdaqResult.isSuccess) {
                val kospiCount = kospiResult.getOrNull() ?: 0
                val kosdaqCount = kosdaqResult.getOrNull() ?: 0
                _state.value = MarketOscillatorState.Success(
                    "KOSPI: $kospiCount, KOSDAQ: $kosdaqCount 개의 데이터를 업데이트했습니다"
                )
                // 데이터 업데이트 후 현재 범위로 리로드 트리거
                val currentRange = _selectedRange.value
                _selectedRange.value = currentRange
                checkData()
            } else {
                val error = kospiResult.exceptionOrNull() ?: kosdaqResult.exceptionOrNull()
                _state.value = MarketOscillatorState.Error("업데이트 실패: ${error?.message}")
            }
        }
    }

    fun clearMessage() {
        if (_state.value is MarketOscillatorState.Success || _state.value is MarketOscillatorState.Error) {
            checkData()
        }
    }
}
