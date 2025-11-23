package com.etfmonitor.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.database.EtfDao
import com.etfmonitor.repository.DataRepository
import com.etfmonitor.repository.FearGreedRepository
import com.etfmonitor.repository.MarketOscillatorRepository
import com.etfmonitor.repository.MarketDepositRepository
import com.etfmonitor.service.CollectionState
import com.etfmonitor.service.DataCollectionService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Production Level HomeViewModel
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
class HomeViewModel @Inject constructor(
    private val repository: DataRepository,
    private val fearGreedRepository: FearGreedRepository,
    private val marketOscillatorRepository: MarketOscillatorRepository,
    private val marketDepositRepository: MarketDepositRepository,
    private val etfDao: EtfDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val _showFirstRunDialog = MutableStateFlow(false)
    val showFirstRunDialog: StateFlow<Boolean> = _showFirstRunDialog.asStateFlow()

    private val _showMarketDepositDialog = MutableStateFlow(false)
    val showMarketDepositDialog: StateFlow<Boolean> = _showMarketDepositDialog.asStateFlow()

    private val _showFearGreedDialog = MutableStateFlow(false)
    val showFearGreedDialog: StateFlow<Boolean> = _showFearGreedDialog.asStateFlow()

    private val _showMarketOscillatorDialog = MutableStateFlow(false)
    val showMarketOscillatorDialog: StateFlow<Boolean> = _showMarketOscillatorDialog.asStateFlow()

    private val _etfInitializationCompleted = MutableStateFlow(false)

    init {
        checkData()
        observeCollectionState()  // ✅ 전역 상태 구독
        checkFirstRun()  // ✅ 첫 실행 체크
    }

    private fun checkFirstRun() {
        viewModelScope.launch {
            val isFirstRun = etfDao.getSetting("is_first_run")
            val hasData = repository.hasData()

            // 첫 실행이고 데이터가 없으면 다이얼로그 표시
            if ((isFirstRun == null || isFirstRun == "true") && !hasData) {
                _showFirstRunDialog.value = true
            }
        }
    }

    fun onFirstRunDialogShown() {
        viewModelScope.launch {
            etfDao.saveSetting(
                com.etfmonitor.database.entities.Setting("is_first_run", "false")
            )
            _showFirstRunDialog.value = false
        }
    }

    private fun checkMarketDepositFirstRun() {
        viewModelScope.launch {
            val dialogDismissed = etfDao.getSetting("market_deposit_dialog_dismissed")
            val hasData = marketDepositRepository.getDepositCount() > 0

            // 증시 자금 동향 데이터가 없고 다이얼로그를 본 적이 없으면 표시
            if (!hasData && dialogDismissed != "true") {
                _showMarketDepositDialog.value = true
            }
        }
    }

    private fun checkFearGreedFirstRun() {
        viewModelScope.launch {
            val dialogDismissed = etfDao.getSetting("fear_greed_dialog_dismissed")
            val hasData = fearGreedRepository.getCountByMarket("KOSPI") > 0 ||
                         fearGreedRepository.getCountByMarket("KOSDAQ") > 0

            // Fear & Greed 데이터가 없고 다이얼로그를 본 적이 없으면 표시
            if (!hasData && dialogDismissed != "true") {
                _showFearGreedDialog.value = true
            }
        }
    }

    private fun checkMarketOscillatorFirstRun() {
        viewModelScope.launch {
            val dialogDismissed = etfDao.getSetting("market_oscillator_dialog_dismissed")
            val hasData = marketOscillatorRepository.getDataCount("KOSPI") > 0 ||
                         marketOscillatorRepository.getDataCount("KOSDAQ") > 0

            // 과매수/과매도 데이터가 없고 다이얼로그를 본 적이 없으면 표시
            if (!hasData && dialogDismissed != "true") {
                _showMarketOscillatorDialog.value = true
            }
        }
    }

    fun onMarketDepositDialogShown() {
        _showMarketDepositDialog.value = false
    }

    fun onFearGreedDialogShown() {
        _showFearGreedDialog.value = false
    }

    fun onMarketOscillatorDialogShown() {
        _showMarketOscillatorDialog.value = false
    }

    fun initializeMarketDeposit(numPages: Int) {
        viewModelScope.launch {
            // 다이얼로그를 더 이상 표시하지 않음
            etfDao.saveSetting(
                com.etfmonitor.database.entities.Setting("market_deposit_dialog_dismissed", "true")
            )
            _showMarketDepositDialog.value = false

            // 증시 자금 동향 데이터 수집
            val result = marketDepositRepository.initializeDeposits(numPages) { message, progress ->
                _state.value = HomeState.Initializing(message, progress)
            }

            if (result.isSuccess) {
                _state.value = HomeState.Success("증시 자금 동향 데이터 수집 완료")
            } else {
                _state.value = HomeState.Error("증시 자금 동향 데이터 수집 실패: ${result.exceptionOrNull()?.message}")
            }

            checkData()

            // 성공 여부와 관계없이 Fear & Greed 다이얼로그 표시
            checkFearGreedFirstRun()
        }
    }

    fun initializeFearGreed(days: Int) {
        viewModelScope.launch {
            // 다이얼로그를 더 이상 표시하지 않음
            etfDao.saveSetting(
                com.etfmonitor.database.entities.Setting("fear_greed_dialog_dismissed", "true")
            )
            _showFearGreedDialog.value = false

            // Fear & Greed 데이터 수집
            val result = fearGreedRepository.initializeFearGreed(days) { message, progress ->
                _state.value = HomeState.Initializing(message, progress)
            }

            if (result.isSuccess) {
                _state.value = HomeState.Success("Fear & Greed Index 데이터 수집 완료")
            } else {
                _state.value = HomeState.Error("Fear & Greed Index 데이터 수집 실패: ${result.exceptionOrNull()?.message}")
            }

            checkData()

            // 성공 여부와 관계없이 과매수/과매도 다이얼로그 표시
            checkMarketOscillatorFirstRun()
        }
    }

    fun initializeMarketOscillator(days: Int) {
        viewModelScope.launch {
            // 다이얼로그를 더 이상 표시하지 않음
            etfDao.saveSetting(
                com.etfmonitor.database.entities.Setting("market_oscillator_dialog_dismissed", "true")
            )
            _showMarketOscillatorDialog.value = false

            // 과매수/과매도 데이터 수집
            _state.value = HomeState.Initializing("과매수/과매도 데이터 수집 중...", 0)

            val kospiResult = marketOscillatorRepository.initializeMarketData("KOSPI", days) { message, progress ->
                // KOSPI 진행 상황: 0-50%
                _state.value = HomeState.Initializing(message, progress / 2)
            }

            val kosdaqResult = marketOscillatorRepository.initializeMarketData("KOSDAQ", days) { message, progress ->
                // KOSDAQ 진행 상황: 50-100%
                _state.value = HomeState.Initializing(message, 50 + progress / 2)
            }

            if (kospiResult.isSuccess && kosdaqResult.isSuccess) {
                val totalCount = (kospiResult.getOrNull() ?: 0) + (kosdaqResult.getOrNull() ?: 0)
                _state.value = HomeState.Success("과매수/과매도 데이터 수집 완료 ($totalCount 개)")
            } else {
                _state.value = HomeState.Error("과매수/과매도 데이터 수집 실패")
            }

            checkData()
        }
    }

    // ✅ 전역 수집 상태 관찰 (DataCollectionService - ETF 데이터만 처리)
    private fun observeCollectionState() {
        viewModelScope.launch {
            combine(
                CollectionState.isCollecting,
                CollectionState.isInitializing,
                CollectionState.currentMessage,
                CollectionState.currentProgress
            ) { isCollecting, isInitializing, message, progress ->
                when {
                    !isCollecting -> null
                    isInitializing -> HomeState.Initializing(message, progress)
                    else -> HomeState.Updating(message, progress)
                }
            }.collect { newState ->
                if (newState != null) {
                    _state.value = newState
                } else {
                    // 수집이 완료되면 데이터 상태 확인
                    val wasInitializing = _state.value is HomeState.Initializing
                    val wasUpdating = _state.value is HomeState.Updating

                    if (wasInitializing || wasUpdating) {
                        checkData()

                        // ETF 초기화가 완료된 경우 증시 자금 동향 다이얼로그 표시
                        if (wasInitializing && _etfInitializationCompleted.value) {
                            _etfInitializationCompleted.value = false  // 리셋
                            checkMarketDepositFirstRun()
                        }
                    }
                }
            }
        }
    }

    private fun checkData() {
        viewModelScope.launch {
            val hasData = repository.hasData()
            val lastDate = repository.getLatestDate()
            val summary = if (hasData) loadSummaryData() else null
            _state.value = HomeState.Idle(hasData, lastDate, summary)
        }
    }

    private suspend fun loadSummaryData(): HomeSummary? {
        return try {
            // 증시 자금 동향 - 최근 데이터
            val recentDeposits = marketDepositRepository.getRecentDeposits(2).first()
            val latestDeposit = recentDeposits.firstOrNull()

            // Fear & Greed Index - KOSPI, KOSDAQ 최근 값
            val kospiFearGreed = fearGreedRepository.getRecentByMarket("KOSPI", 1).first().firstOrNull()
            val kosdaqFearGreed = fearGreedRepository.getRecentByMarket("KOSDAQ", 1).first().firstOrNull()

            // 시장 과매수/과매도 - KOSPI, KOSDAQ 최근 상태
            val kospiOscillator = marketOscillatorRepository.getLatestData("KOSPI")
            val kosdaqOscillator = marketOscillatorRepository.getLatestData("KOSDAQ")

            HomeSummary(
                depositChange = latestDeposit?.depositChange,
                creditChange = latestDeposit?.creditChange,
                kospiFearGreed = kospiFearGreed?.indexValue,
                kosdaqFearGreed = kosdaqFearGreed?.indexValue,
                kospiOscillator = kospiOscillator?.oscillator,
                kospiStatus = kospiOscillator?.let { calculateOscillatorStatus(it.oscillator) },
                kosdaqOscillator = kosdaqOscillator?.oscillator,
                kosdaqStatus = kosdaqOscillator?.let { calculateOscillatorStatus(it.oscillator) }
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateOscillatorStatus(oscillatorValue: Double): String {
        return when {
            oscillatorValue >= 70.0 -> "Overbought"
            oscillatorValue <= -70.0 -> "Oversold"
            else -> "Neutral"
        }
    }

    fun initialize(days: Int? = null) {
        viewModelScope.launch {
            val daysToUse = days ?: repository.getDefaultDays()
            _etfInitializationCompleted.value = true  // ETF 초기화 시작 표시
            DataCollectionService.startInitialize(context, daysToUse)
        }
    }

    fun update() {
        DataCollectionService.startUpdate(context)
    }

    fun clearMessage() {
        if (_state.value is HomeState.Success || _state.value is HomeState.Error) {
            checkData()
        }
    }
}

sealed class HomeState {
    object Loading : HomeState()
    data class Idle(
        val hasData: Boolean,
        val lastDate: String?,
        val summary: HomeSummary? = null
    ) : HomeState()
    data class Initializing(val message: String, val progress: Int) : HomeState()
    data class Updating(val message: String, val progress: Int) : HomeState()
    data class Success(val message: String) : HomeState()
    data class Error(val message: String) : HomeState()
}

/**
 * 홈 화면 요약 데이터
 */
data class HomeSummary(
    // 증시 자금 동향
    val depositChange: Double?,  // 고객예탁금 증감
    val creditChange: Double?,   // 신용잔고 증감

    // Fear & Greed Index
    val kospiFearGreed: Double?,     // KOSPI F&G 값
    val kosdaqFearGreed: Double?,    // KOSDAQ F&G 값

    // 시장 과매수/과매도
    val kospiOscillator: Double?,    // KOSPI 오실레이터 값
    val kospiStatus: String?,        // KOSPI 상태 (Overbought/Neutral/Oversold)
    val kosdaqOscillator: Double?,   // KOSDAQ 오실레이터 값
    val kosdaqStatus: String?        // KOSDAQ 상태
)