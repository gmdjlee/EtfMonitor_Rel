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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 홈 화면 ViewModel
 *
 * 앱의 메인 화면을 담당하는 ViewModel로, 전체적인 데이터 상태 관리와
 * 초기화/업데이트 작업을 조율합니다.
 *
 * ## 주요 기능
 * - 데이터 상태 확인 및 UI 상태 관리 ([state])
 * - 초기 데이터 수집 시작 ([startForegroundCollection])
 * - 데이터 업데이트 ([updateData])
 * - 시장 요약 정보 로딩 ([HomeSummary])
 *
 * ## 상태 관리
 * [HomeState]를 통해 다음 상태들을 관리합니다:
 * - [HomeState.Loading]: 초기 로딩 중
 * - [HomeState.Idle]: 데이터 대기 상태 (요약 정보 포함)
 * - [HomeState.Initializing]: 초기 데이터 수집 중
 * - [HomeState.Updating]: 데이터 업데이트 중
 * - [HomeState.Success]: 작업 성공
 * - [HomeState.Error]: 오류 발생
 *
 * ## 다이얼로그 상태
 * 첫 실행 및 데이터 수집 다이얼로그 상태를 관리합니다:
 * - [showUnifiedInitDialog]: 통합 초기화 다이얼로그
 * - [showMarketDepositDialog]: 시장 예탁금 다이얼로그
 * - [showFearGreedDialog]: 공포탐욕지수 다이얼로그
 * - [showMarketOscillatorDialog]: 시장 오실레이터 다이얼로그
 *
 * ## 의존성
 * @property repository ETF 데이터 Repository
 * @property fearGreedRepository 공포탐욕지수 Repository
 * @property marketOscillatorRepository 시장 오실레이터 Repository
 * @property marketDepositRepository 시장 예탁금 Repository
 * @property etfDao ETF DAO (설정 저장용)
 * @property context 애플리케이션 Context
 *
 * @see HomeState
 * @see HomeSummary
 * @see DataCollectionService
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

    companion object {
        // Oscillator threshold constants
        private const val OSCILLATOR_OVERBOUGHT_THRESHOLD = 70.0
        private const val OSCILLATOR_OVERSOLD_THRESHOLD = -70.0
    }

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

    // 통합 초기화 다이얼로그용 상태
    private val _showUnifiedInitDialog = MutableStateFlow(false)
    val showUnifiedInitDialog: StateFlow<Boolean> = _showUnifiedInitDialog.asStateFlow()

    init {
        checkData()
        observeCollectionState()  // ✅ 전역 상태 구독
        checkFirstRun()  // ✅ 첫 실행 체크
    }

    private fun checkFirstRun() {
        viewModelScope.launch {
            val isFirstRun = etfDao.getSetting("is_first_run")
            val hasEtfData = repository.hasData()
            val hasDepositData = marketDepositRepository.getDepositCount() > 0
            val hasFearGreedData = fearGreedRepository.getCountByMarket("KOSPI") > 0 ||
                                   fearGreedRepository.getCountByMarket("KOSDAQ") > 0
            val hasOscillatorData = marketOscillatorRepository.getDataCount("KOSPI") > 0 ||
                                    marketOscillatorRepository.getDataCount("KOSDAQ") > 0

            // 첫 실행이거나 ETF 데이터가 없으면 통합 다이얼로그 표시
            if ((isFirstRun == null || isFirstRun == "true") && !hasEtfData) {
                _showUnifiedInitDialog.value = true
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

    // ✅ 전역 수집 상태 관찰 (DataCollectionService가 모든 초기화 처리)
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
                        // Service에서 모든 초기화를 처리하므로 여기서는 데이터 상태만 확인
                        checkData()
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
        return withContext(Dispatchers.IO) {
            try {
                // 증시 자금 동향 - 최근 데이터
                val recentDeposits = marketDepositRepository.getRecentDeposits(2)
                    .flowOn(Dispatchers.IO)
                    .first()
                val latestDeposit = recentDeposits.firstOrNull()

                // 디버깅: 예탁금 데이터 확인
                android.util.Log.d("HomeViewModel", "Market deposit data - count: ${recentDeposits.size}, latest: $latestDeposit")

                // Fear & Greed Index - KOSPI, KOSDAQ 최근 값 (oscillator 사용)
                val kospiFearGreed = fearGreedRepository.getRecentByMarket("KOSPI", 1)
                    .flowOn(Dispatchers.IO)
                    .first()
                    .firstOrNull()
                val kosdaqFearGreed = fearGreedRepository.getRecentByMarket("KOSDAQ", 1)
                    .flowOn(Dispatchers.IO)
                    .first()
                    .firstOrNull()

            // 디버깅: Fear & Greed 데이터 확인
            android.util.Log.d("HomeViewModel", "Fear & Greed - KOSPI: ${kospiFearGreed?.oscillator}, KOSDAQ: ${kosdaqFearGreed?.oscillator}")

            // 시장 과매수/과매도 - KOSPI, KOSDAQ 최근 상태
            val kospiOscillator = marketOscillatorRepository.getLatestData("KOSPI")
            val kosdaqOscillator = marketOscillatorRepository.getLatestData("KOSDAQ")

                HomeSummary(
                    depositChange = latestDeposit?.depositChange,
                    creditChange = latestDeposit?.creditChange,
                    kospiFearGreed = kospiFearGreed?.oscillator,  // oscillator 값 사용
                    kosdaqFearGreed = kosdaqFearGreed?.oscillator,  // oscillator 값 사용
                    kospiOscillator = kospiOscillator?.oscillator,
                    kospiStatus = kospiOscillator?.let { calculateOscillatorStatus(it.oscillator) },
                    kosdaqOscillator = kosdaqOscillator?.oscillator,
                    kosdaqStatus = kosdaqOscillator?.let { calculateOscillatorStatus(it.oscillator) }
                )
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Error loading summary data", e)
                null
            }
        }
    }

    private fun calculateOscillatorStatus(oscillatorValue: Double): String {
        return when {
            oscillatorValue >= OSCILLATOR_OVERBOUGHT_THRESHOLD -> "과매수"
            oscillatorValue <= OSCILLATOR_OVERSOLD_THRESHOLD -> "과매도"
            else -> "중립"
        }
    }

    fun initialize(days: Int? = null) {
        viewModelScope.launch {
            val daysToUse = days ?: repository.getDefaultDays()
            // DataCollectionService가 WakeLock을 사용하여 백그라운드에서도 안전하게 동작
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

    /**
     * 통합 초기화 - 모든 데이터 타입을 한 번에 초기화
     *
     * 이 함수는 DataCollectionService.startInitializeAll()을 호출하여
     * 모든 초기화 작업을 Foreground Service에서 처리합니다.
     * 화면이 꺼지거나 앱이 백그라운드로 가도 안전하게 동작합니다.
     */
    fun initializeAll(
        etfDays: Int,
        depositPages: Int?,
        fearGreedDays: Int?,
        oscillatorDays: Int?
    ) {
        viewModelScope.launch {
            // 모든 다이얼로그 설정 플래그 저장 (다시 보이지 않도록)
            etfDao.saveSetting(com.etfmonitor.database.entities.Setting("is_first_run", "false"))
            etfDao.saveSetting(com.etfmonitor.database.entities.Setting("market_deposit_dialog_dismissed", "true"))
            etfDao.saveSetting(com.etfmonitor.database.entities.Setting("fear_greed_dialog_dismissed", "true"))
            etfDao.saveSetting(com.etfmonitor.database.entities.Setting("market_oscillator_dialog_dismissed", "true"))

            _showUnifiedInitDialog.value = false

            // 모든 초기화를 Foreground Service에서 처리 (WakeLock 포함)
            // 화면이 꺼지거나 앱이 백그라운드로 가도 안전하게 동작
            _state.value = HomeState.Initializing("데이터 수집 시작...", 0)
            DataCollectionService.startInitializeAll(
                context = context,
                etfDays = etfDays,
                depositPages = depositPages,
                fearGreedDays = fearGreedDays,
                oscillatorDays = oscillatorDays
            )
        }
    }

    fun onUnifiedInitDialogDismiss() {
        viewModelScope.launch {
            etfDao.saveSetting(com.etfmonitor.database.entities.Setting("is_first_run", "false"))
            _showUnifiedInitDialog.value = false
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

    // Fear & Greed Index (MACD Oscillator 값)
    val kospiFearGreed: Double?,     // KOSPI Fear & Greed Oscillator 값
    val kosdaqFearGreed: Double?,    // KOSDAQ Fear & Greed Oscillator 값

    // 시장 과매수/과매도
    val kospiOscillator: Double?,    // KOSPI 오실레이터 값
    val kospiStatus: String?,        // KOSPI 상태 (과매수/중립/과매도)
    val kosdaqOscillator: Double?,   // KOSDAQ 오실레이터 값
    val kosdaqStatus: String?        // KOSDAQ 상태
)