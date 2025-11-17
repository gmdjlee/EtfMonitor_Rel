package com.etfmonitor.ui.screens.marketoscillator

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.etfmonitor.EtfMonitorApp
import com.etfmonitor.database.entities.MarketOscillatorData
import com.etfmonitor.repository.MarketOscillatorRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

class MarketOscillatorViewModel(
    private val repository: MarketOscillatorRepository,
    private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow<MarketOscillatorState>(MarketOscillatorState.Loading)
    val state: StateFlow<MarketOscillatorState> = _state.asStateFlow()

    // 선택된 시장 (KOSPI/KOSDAQ)
    private val _selectedMarket = MutableStateFlow("KOSPI")
    val selectedMarket: StateFlow<String> = _selectedMarket.asStateFlow()

    // 시장 데이터
    private val _marketData = MutableStateFlow<List<MarketOscillatorData>>(emptyList())
    val marketData: StateFlow<List<MarketOscillatorData>> = _marketData.asStateFlow()

    // 표시할 데이터 개수 (기본 15일)
    private val _displayDays = MutableStateFlow(15)
    val displayDays: StateFlow<Int> = _displayDays.asStateFlow()

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
        loadData()
        checkFirstRun()
    }

    private fun checkFirstRun() {
        viewModelScope.launch {
            val app = EtfMonitorApp.instance
            val dialogDismissed = app.database.dao().getSetting("market_oscillator_dialog_dismissed")
            val hasData = repository.getDataCount("KOSPI") > 0 ||
                         repository.getDataCount("KOSDAQ") > 0

            // 데이터가 없고 다이얼로그를 본 적이 없으면 표시
            if (!hasData && dialogDismissed != "true") {
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
            val app = EtfMonitorApp.instance
            app.database.dao().saveSetting(
                com.etfmonitor.database.entities.Setting("market_oscillator_dialog_dismissed", "true")
            )
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

    private fun loadData() {
        viewModelScope.launch {
            repository.getRecentData(_selectedMarket.value, _displayDays.value)
                .collect { data ->
                    _marketData.value = data
                }
        }
    }

    fun setSelectedMarket(market: String) {
        _selectedMarket.value = market
        loadData()
        checkData()
    }

    fun setDisplayDays(days: Int) {
        _displayDays.value = days
        loadData()
    }

    fun setOverboughtThreshold(threshold: Double) {
        _overboughtThreshold.value = threshold
    }

    fun setOversoldThreshold(threshold: Double) {
        _oversoldThreshold.value = threshold
    }

    /**
     * 초기 데이터 수집 (12개월)
     */
    fun initialize(days: Int = 365) {
        viewModelScope.launch {
            _state.value = MarketOscillatorState.Initializing("시장 데이터 수집 중...", 0)

            // KOSPI 수집
            _state.value = MarketOscillatorState.Initializing("KOSPI 데이터 수집 중...", 25)
            val kospiResult = repository.initializeMarketData("KOSPI", days)

            // KOSDAQ 수집
            _state.value = MarketOscillatorState.Initializing("KOSDAQ 데이터 수집 중...", 50)
            val kosdaqResult = repository.initializeMarketData("KOSDAQ", days)

            if (kospiResult.isSuccess && kosdaqResult.isSuccess) {
                val kospiCount = kospiResult.getOrNull() ?: 0
                val kosdaqCount = kosdaqResult.getOrNull() ?: 0
                _state.value = MarketOscillatorState.Success(
                    "KOSPI: $kospiCount, KOSDAQ: $kosdaqCount 개의 데이터를 수집했습니다"
                )
                loadData()
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

            val kospiResult = repository.updateMarketData("KOSPI")
            val kosdaqResult = repository.updateMarketData("KOSDAQ")

            if (kospiResult.isSuccess && kosdaqResult.isSuccess) {
                val kospiCount = kospiResult.getOrNull() ?: 0
                val kosdaqCount = kosdaqResult.getOrNull() ?: 0
                _state.value = MarketOscillatorState.Success(
                    "KOSPI: $kospiCount, KOSDAQ: $kosdaqCount 개의 데이터를 업데이트했습니다"
                )
                loadData()
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

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext as EtfMonitorApp
                return MarketOscillatorViewModel(app.marketOscillatorRepository, context) as T
            }
        }
    }
}
