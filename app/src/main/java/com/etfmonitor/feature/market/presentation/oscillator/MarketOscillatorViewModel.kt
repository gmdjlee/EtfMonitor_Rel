package com.etfmonitor.feature.market.presentation.oscillator

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.database.EtfDao
import com.etfmonitor.database.entities.Setting
import com.etfmonitor.feature.market.domain.model.MarketOscillator
import com.etfmonitor.feature.market.domain.model.MarketOscillatorViewState
import com.etfmonitor.feature.market.domain.usecase.CheckMarketOscillatorDataStatusUseCase
import com.etfmonitor.feature.market.domain.usecase.GetRecentMarketOscillatorUseCase
import com.etfmonitor.feature.market.domain.usecase.InitializeMarketOscillatorUseCase
import com.etfmonitor.feature.market.domain.usecase.UpdateMarketOscillatorUseCase
import com.etfmonitor.core.ui.theme.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 시장 과매수/과매도 ViewModel (Clean Architecture)
 *
 * UseCase 기반으로 리팩토링:
 * - GetRecentMarketOscillatorUseCase: 최근 데이터 조회
 * - InitializeMarketOscillatorUseCase: 데이터 초기화
 * - UpdateMarketOscillatorUseCase: 데이터 업데이트
 * - CheckMarketOscillatorDataStatusUseCase: 데이터 상태 확인
 */
@HiltViewModel
class MarketOscillatorViewModel @Inject constructor(
    private val getRecentMarketOscillatorUseCase: GetRecentMarketOscillatorUseCase,
    private val initializeMarketOscillatorUseCase: InitializeMarketOscillatorUseCase,
    private val updateMarketOscillatorUseCase: UpdateMarketOscillatorUseCase,
    private val checkMarketOscillatorDataStatusUseCase: CheckMarketOscillatorDataStatusUseCase,
    private val etfDao: EtfDao,
    private val themeManager: ThemeManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Body 폰트 스케일
    val bodyScale: StateFlow<Float> = themeManager.fontScaleSettings
        .map { it.bodyScale }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    private val _state = MutableStateFlow<MarketOscillatorViewState>(MarketOscillatorViewState.Loading)
    val state: StateFlow<MarketOscillatorViewState> = _state.asStateFlow()

    // 선택된 시장 (KOSPI/KOSDAQ)
    private val _selectedMarket = MutableStateFlow("KOSPI")
    val selectedMarket: StateFlow<String> = _selectedMarket.asStateFlow()

    // 시장 데이터
    private val _marketData = MutableStateFlow<List<MarketOscillator>>(emptyList())
    val marketData: StateFlow<List<MarketOscillator>> = _marketData.asStateFlow()

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
            val dialogDismissed = etfDao.getSetting("market_oscillator_dialog_dismissed")
            val kospiStatus = checkMarketOscillatorDataStatusUseCase("KOSPI")
            val kosdaqStatus = checkMarketOscillatorDataStatusUseCase("KOSDAQ")

            val hasData = kospiStatus.hasData || kosdaqStatus.hasData

            // 데이터가 없고 다이얼로그를 본 적이 없으면 표시
            if (!hasData && dialogDismissed != "true") {
                _showFirstRunDialog.value = true
            }
        }
    }

    fun onFirstRunDialogShown() {
        _showFirstRunDialog.value = false
    }

    fun onFirstRunDialogConfirmed() {
        viewModelScope.launch {
            etfDao.saveSetting(Setting("market_oscillator_dialog_dismissed", "true"))
            _showFirstRunDialog.value = false
        }
    }

    private fun checkData() {
        viewModelScope.launch {
            val kospiStatus = checkMarketOscillatorDataStatusUseCase("KOSPI")
            val kosdaqStatus = checkMarketOscillatorDataStatusUseCase("KOSDAQ")
            val hasData = kospiStatus.hasData || kosdaqStatus.hasData

            val latestDate = if (_selectedMarket.value == "KOSPI") {
                kospiStatus.latestDate
            } else {
                kosdaqStatus.latestDate
            }

            _state.value = MarketOscillatorViewState.Idle(hasData, latestDate)
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            getRecentMarketOscillatorUseCase(_selectedMarket.value, _displayDays.value)
                .collect { data ->
                    _marketData.value = data
                }
        }
    }

    fun onSelectedMarketChanged(market: String) {
        _selectedMarket.value = market
        loadData()
        checkData()
    }

    fun onDisplayDaysChanged(days: Int) {
        _displayDays.value = days
        loadData()
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
            _state.value = MarketOscillatorViewState.Initializing("시장 데이터 수집 중...", 0)

            // KOSPI 수집
            _state.value = MarketOscillatorViewState.Initializing("KOSPI 데이터 수집 중...", 25)
            val kospiResult = initializeMarketOscillatorUseCase("KOSPI", days)

            // KOSDAQ 수집
            _state.value = MarketOscillatorViewState.Initializing("KOSDAQ 데이터 수집 중...", 50)
            val kosdaqResult = initializeMarketOscillatorUseCase("KOSDAQ", days)

            if (kospiResult.isSuccess && kosdaqResult.isSuccess) {
                val kospiCount = kospiResult.getOrNull() ?: 0
                val kosdaqCount = kosdaqResult.getOrNull() ?: 0
                _state.value = MarketOscillatorViewState.Success(
                    "KOSPI: $kospiCount, KOSDAQ: $kosdaqCount 개의 데이터를 수집했습니다"
                )
                loadData()
                checkData()
            } else {
                val error = kospiResult.exceptionOrNull() ?: kosdaqResult.exceptionOrNull()
                _state.value = MarketOscillatorViewState.Error("데이터 수집 실패: ${error?.message}")
            }
        }
    }

    /**
     * 데이터 업데이트 (최근 30일)
     */
    fun update() {
        viewModelScope.launch {
            _state.value = MarketOscillatorViewState.Updating("시장 데이터 업데이트 중...")

            val kospiResult = updateMarketOscillatorUseCase("KOSPI")
            val kosdaqResult = updateMarketOscillatorUseCase("KOSDAQ")

            if (kospiResult.isSuccess && kosdaqResult.isSuccess) {
                val kospiCount = kospiResult.getOrNull() ?: 0
                val kosdaqCount = kosdaqResult.getOrNull() ?: 0
                _state.value = MarketOscillatorViewState.Success(
                    "KOSPI: $kospiCount, KOSDAQ: $kosdaqCount 개의 데이터를 업데이트했습니다"
                )
                loadData()
                checkData()
            } else {
                val error = kospiResult.exceptionOrNull() ?: kosdaqResult.exceptionOrNull()
                _state.value = MarketOscillatorViewState.Error("업데이트 실패: ${error?.message}")
            }
        }
    }

    fun clearMessage() {
        if (_state.value is MarketOscillatorViewState.Success || _state.value is MarketOscillatorViewState.Error) {
            checkData()
        }
    }
}
