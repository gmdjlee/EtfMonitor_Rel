package com.etfmonitor.feature.home.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.core.service.CollectionState
import com.etfmonitor.core.service.DataCollectionService
import com.etfmonitor.feature.home.domain.model.HomeState
import com.etfmonitor.feature.home.domain.model.HomeSummary
import com.etfmonitor.feature.home.domain.usecase.CheckDataStatusUseCase
import com.etfmonitor.feature.home.domain.usecase.CheckEtfDataUseCase
import com.etfmonitor.feature.home.domain.usecase.CheckFirstRunUseCase
import com.etfmonitor.feature.home.domain.usecase.GetDefaultDaysUseCase
import com.etfmonitor.feature.home.domain.usecase.GetHomeSummaryUseCase
import com.etfmonitor.feature.home.domain.usecase.SaveDialogDismissedUseCase
import com.etfmonitor.core.network.ai.AIProvider
import com.etfmonitor.core.network.ai.ApiKeyProvider
import com.etfmonitor.core.network.blood.FredApiKeyProvider
import com.etfmonitor.core.network.kis.KisApiKeyProvider
import com.etfmonitor.core.network.kiwoom.KiwoomApiKeyProvider
import com.etfmonitor.core.common.util.KrxConstants
import com.etfmonitor.feature.market.domain.repository.FearGreedRepository
import com.etfmonitor.feature.market.domain.repository.MarketDepositRepository
import com.etfmonitor.feature.market.domain.repository.MarketOscillatorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 홈 화면 ViewModel
 *
 * 앱의 메인 화면을 담당하는 ViewModel로, 전체적인 데이터 상태 관리와
 * 초기화/업데이트 작업을 조율합니다.
 *
 * ## 주요 기능
 * - 데이터 상태 확인 및 UI 상태 관리 ([state])
 * - 초기 데이터 수집 시작 ([initialize])
 * - 데이터 업데이트 ([update])
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
 * ## UseCase 사용
 * Clean Architecture에 따라 UseCase를 통해 비즈니스 로직을 실행합니다.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHomeSummaryUseCase: GetHomeSummaryUseCase,
    private val checkEtfDataUseCase: CheckEtfDataUseCase,
    private val checkFirstRunUseCase: CheckFirstRunUseCase,
    private val checkDataStatusUseCase: CheckDataStatusUseCase,
    private val getDefaultDaysUseCase: GetDefaultDaysUseCase,
    private val saveDialogDismissedUseCase: SaveDialogDismissedUseCase,
    // 개별 초기화를 위해 Repository 직접 사용 (복잡한 초기화 로직)
    private val fearGreedRepository: FearGreedRepository,
    private val marketOscillatorRepository: MarketOscillatorRepository,
    private val marketDepositRepository: MarketDepositRepository,
    private val kisApiKeyProvider: KisApiKeyProvider,
    private val fredApiKeyProvider: FredApiKeyProvider,
    private val aiApiKeyProvider: ApiKeyProvider,
    private val kiwoomApiKeyProvider: KiwoomApiKeyProvider,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private val KRX_RATE_LIMIT_COOLDOWN_MS = KrxConstants.KRX_RATE_LIMIT_COOLDOWN_MS
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

    // API 키 입력 다이얼로그 상태 (통합 초기화 전에 표시)
    private val _showApiKeyDialog = MutableStateFlow(false)
    val showApiKeyDialog: StateFlow<Boolean> = _showApiKeyDialog.asStateFlow()

    // 프로세스 종료로 중단된 수집 알림용 상태
    private val _showInterruptedCollectionBanner = MutableStateFlow(false)
    val showInterruptedCollectionBanner: StateFlow<Boolean> = _showInterruptedCollectionBanner.asStateFlow()

    private val _interruptedCollectionMessage = MutableStateFlow("")
    val interruptedCollectionMessage: StateFlow<String> = _interruptedCollectionMessage.asStateFlow()

    init {
        checkData()
        observeCollectionState()
        checkFirstRun()
        checkInterruptedCollection()
    }

    private fun checkFirstRun() {
        viewModelScope.launch {
            val shouldShow = checkFirstRunUseCase()
            if (shouldShow) {
                if (!kisApiKeyProvider.isConfigured()) {
                    // API 키 미설정 시 API 키 입력 다이얼로그를 먼저 표시
                    _showApiKeyDialog.value = true
                } else {
                    // API 키 설정 완료 시 통합 초기화 다이얼로그로 진행
                    _showUnifiedInitDialog.value = true
                }
            }
        }
    }

    /**
     * API 키 입력 확인 시 호출합니다.
     * 입력된 키를 각 Provider에 저장한 후 통합 초기화 다이얼로그로 진행합니다.
     */
    fun saveApiKeys(
        kisAppKey: String,
        kisAppSecret: String,
        fredApiKey: String,
        aiProvider: String?,
        aiApiKey: String?,
        kiwoomAppKey: String = "",
        kiwoomSecretKey: String = ""
    ) {
        if (kisAppKey.isNotBlank() && kisAppSecret.isNotBlank()) {
            kisApiKeyProvider.setAppKey(kisAppKey)
            kisApiKeyProvider.setAppSecret(kisAppSecret)
        }
        if (fredApiKey.isNotBlank()) {
            fredApiKeyProvider.saveApiKey(fredApiKey)
        }
        if (aiProvider != null && !aiApiKey.isNullOrBlank()) {
            val provider = AIProvider.fromString(aiProvider)
            aiApiKeyProvider.setApiKey(provider, aiApiKey)
            aiApiKeyProvider.setSelectedProvider(provider)
        }
        if (kiwoomAppKey.isNotBlank()) {
            kiwoomApiKeyProvider.setAppKey(kiwoomAppKey)
        }
        if (kiwoomSecretKey.isNotBlank()) {
            kiwoomApiKeyProvider.setSecretKey(kiwoomSecretKey)
        }
        _showApiKeyDialog.value = false
        _showUnifiedInitDialog.value = true
    }

    /**
     * API 키 입력 다이얼로그를 닫을 때 호출합니다 (나중에 설정 가능).
     * 다이얼로그를 닫고 통합 초기화 다이얼로그로 진행합니다.
     */
    fun dismissApiKeyDialog() {
        _showApiKeyDialog.value = false
        _showUnifiedInitDialog.value = true
    }

    fun onFirstRunDialogShown() {
        viewModelScope.launch {
            saveDialogDismissedUseCase.saveFirstRunCompleted()
            _showFirstRunDialog.value = false
        }
    }

    private fun checkMarketDepositFirstRun() {
        viewModelScope.launch {
            val dataStatus = checkDataStatusUseCase()
            if (!dataStatus.hasDepositData) {
                _showMarketDepositDialog.value = true
            }
        }
    }

    private fun checkFearGreedFirstRun() {
        viewModelScope.launch {
            val dataStatus = checkDataStatusUseCase()
            if (!dataStatus.hasFearGreedData) {
                _showFearGreedDialog.value = true
            }
        }
    }

    private fun checkMarketOscillatorFirstRun() {
        viewModelScope.launch {
            val dataStatus = checkDataStatusUseCase()
            if (!dataStatus.hasOscillatorData) {
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
            saveDialogDismissedUseCase.saveDialogDismissed(
                SaveDialogDismissedUseCase.KEY_MARKET_DEPOSIT_DISMISSED
            )
            _showMarketDepositDialog.value = false

            val result = marketDepositRepository.initializeDeposits(numPages) { message, progress ->
                _state.value = HomeState.Initializing(message, progress)
            }

            if (result.isSuccess) {
                _state.value = HomeState.Success("증시 자금 동향 데이터 수집 완료")
            } else {
                _state.value = HomeState.Error("증시 자금 동향 데이터 수집 실패: ${result.exceptionOrNull()?.message}")
            }

            checkData()
            checkFearGreedFirstRun()
        }
    }

    fun initializeFearGreed(days: Int) {
        viewModelScope.launch {
            saveDialogDismissedUseCase.saveDialogDismissed(
                SaveDialogDismissedUseCase.KEY_FEAR_GREED_DISMISSED
            )
            _showFearGreedDialog.value = false

            val result = fearGreedRepository.initializeFearGreed(days) { message, progress ->
                _state.value = HomeState.Initializing(message, progress)
            }

            if (result.isSuccess) {
                _state.value = HomeState.Success("Fear & Greed Index 데이터 수집 완료")
            } else {
                _state.value = HomeState.Error("Fear & Greed Index 데이터 수집 실패: ${result.exceptionOrNull()?.message}")
            }

            checkData()
            checkMarketOscillatorFirstRun()
        }
    }

    fun initializeMarketOscillator(days: Int) {
        viewModelScope.launch {
            saveDialogDismissedUseCase.saveDialogDismissed(
                SaveDialogDismissedUseCase.KEY_MARKET_OSCILLATOR_DISMISSED
            )
            _showMarketOscillatorDialog.value = false

            _state.value = HomeState.Initializing("과매수/과매도 데이터 수집 중...", 0)

            val kospiResult = marketOscillatorRepository.initializeMarketData("KOSPI", days) { message, progress ->
                _state.value = HomeState.Initializing(message, progress / 2)
            }

            // KRX rate limit 쿨다운 (KOSPI 수집 후 403 방지)
            _state.value = HomeState.Initializing("KRX 서버 대기 중...", 48)
            delay(KRX_RATE_LIMIT_COOLDOWN_MS)

            val kosdaqResult = marketOscillatorRepository.initializeMarketData("KOSDAQ", days) { message, progress ->
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
                    val wasInitializing = _state.value is HomeState.Initializing
                    val wasUpdating = _state.value is HomeState.Updating

                    if (wasInitializing || wasUpdating) {
                        checkData()
                    }
                }
            }
        }
    }

    private fun checkData() {
        viewModelScope.launch {
            val (hasData, lastDate) = checkEtfDataUseCase()
            val summary = if (hasData) getHomeSummaryUseCase() else null
            _state.value = HomeState.Idle(hasData, lastDate, summary)
        }
    }

    fun initialize(days: Int? = null) {
        viewModelScope.launch {
            val daysToUse = days ?: getDefaultDaysUseCase()
            // Race condition 방지: Service 시작 전에 CollectionState 먼저 설정
            CollectionState.startCollection(isInitialize = true, initialMessage = "초기화 준비 중...")
            DataCollectionService.startInitialize(context, daysToUse)
        }
    }

    fun update() {
        // Race condition 방지: Service 시작 전에 CollectionState 먼저 설정
        CollectionState.startCollection(isInitialize = false, initialMessage = "업데이트 준비 중...")
        DataCollectionService.startUpdate(context)
    }

    /**
     * 앱 재시작 시 이전 프로세스에서 수집이 중단됐는지 확인합니다.
     * CollectionState.wasInterrupted=true이면 배너 상태를 활성화합니다.
     */
    fun checkInterruptedCollection() {
        if (CollectionState.wasInterrupted.value) {
            val progress = CollectionState.interruptedProgress.value
            val savedMessage = CollectionState.interruptedMessage.value
            val detail = if (savedMessage.isNotBlank()) " ($savedMessage, ${progress}%)" else ""
            _interruptedCollectionMessage.value = "이전 데이터 수집이 중단되었습니다$detail"
            _showInterruptedCollectionBanner.value = true
        }
    }

    /**
     * 사용자가 중단 수집 배너를 닫을 때 호출합니다.
     */
    fun dismissInterruptedCollectionBanner() {
        _showInterruptedCollectionBanner.value = false
        _interruptedCollectionMessage.value = ""
        CollectionState.acknowledgeInterruption()
    }

    fun clearMessage() {
        if (_state.value is HomeState.Success || _state.value is HomeState.Error) {
            checkData()
        }
    }

    /**
     * 통합 초기화 - 모든 데이터 타입을 한 번에 초기화
     */
    fun initializeAll(
        etfDays: Int,
        depositPages: Int?,
        fearGreedDays: Int?,
        oscillatorDays: Int?,
        marketIndexDays: Int?,
        bloodIndicatorDays: Int? = null
    ) {
        viewModelScope.launch {
            saveDialogDismissedUseCase.saveAllDialogsDismissed()
            _showUnifiedInitDialog.value = false

            // Race condition 방지: Service 시작 전에 CollectionState 먼저 설정
            // 이렇게 하면 observeCollectionState()가 isCollecting=true를 감지하여
            // checkData()로 상태를 Idle로 되돌리지 않음
            CollectionState.startCollection(isInitialize = true, initialMessage = "통합 초기화 준비 중...")
            DataCollectionService.startInitializeAll(
                context = context,
                etfDays = etfDays,
                depositPages = depositPages,
                fearGreedDays = fearGreedDays,
                oscillatorDays = oscillatorDays,
                marketIndexDays = marketIndexDays,
                bloodIndicatorDays = bloodIndicatorDays
            )
        }
    }

    fun onUnifiedInitDialogDismiss() {
        viewModelScope.launch {
            saveDialogDismissedUseCase.saveFirstRunCompleted()
            _showUnifiedInitDialog.value = false
        }
    }
}
