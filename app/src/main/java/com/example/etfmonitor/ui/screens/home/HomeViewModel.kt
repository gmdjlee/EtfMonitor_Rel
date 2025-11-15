package com.etfmonitor.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.etfmonitor.EtfMonitorApp
import com.etfmonitor.repository.DataRepository
import com.etfmonitor.service.CollectionState
import com.etfmonitor.service.DataCollectionService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: DataRepository,
    private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val _showFirstRunDialog = MutableStateFlow(false)
    val showFirstRunDialog: StateFlow<Boolean> = _showFirstRunDialog.asStateFlow()

    private val _showFearGreedDialog = MutableStateFlow(false)
    val showFearGreedDialog: StateFlow<Boolean> = _showFearGreedDialog.asStateFlow()

    private val _etfInitializationCompleted = MutableStateFlow(false)

    init {
        checkData()
        observeCollectionState()  // ✅ 전역 상태 구독
        checkFirstRun()  // ✅ 첫 실행 체크
    }

    private fun checkFirstRun() {
        viewModelScope.launch {
            val app = EtfMonitorApp.instance
            val isFirstRun = app.database.dao().getSetting("is_first_run")
            val hasData = repository.hasData()

            // 첫 실행이고 데이터가 없으면 다이얼로그 표시
            if ((isFirstRun == null || isFirstRun == "true") && !hasData) {
                _showFirstRunDialog.value = true
            }
        }
    }

    fun onFirstRunDialogShown() {
        viewModelScope.launch {
            val app = EtfMonitorApp.instance
            app.database.dao().saveSetting(
                com.etfmonitor.database.entities.Setting("is_first_run", "false")
            )
            _showFirstRunDialog.value = false
        }
    }

    private fun checkFearGreedFirstRun() {
        viewModelScope.launch {
            val app = EtfMonitorApp.instance
            val fearGreedRepository = app.fearGreedRepository
            val dialogDismissed = app.database.dao().getSetting("fear_greed_dialog_dismissed")
            val hasData = fearGreedRepository.getCountByMarket("KOSPI") > 0 ||
                         fearGreedRepository.getCountByMarket("KOSDAQ") > 0

            // Fear & Greed 데이터가 없고 다이얼로그를 본 적이 없으면 표시
            if (!hasData && dialogDismissed != "true") {
                _showFearGreedDialog.value = true
            }
        }
    }

    fun onFearGreedDialogShown() {
        _showFearGreedDialog.value = false
    }

    fun initializeFearGreed(days: Int) {
        viewModelScope.launch {
            val app = EtfMonitorApp.instance
            val fearGreedRepository = app.fearGreedRepository

            // 다이얼로그를 더 이상 표시하지 않음
            app.database.dao().saveSetting(
                com.etfmonitor.database.entities.Setting("fear_greed_dialog_dismissed", "true")
            )
            _showFearGreedDialog.value = false

            // Fear & Greed 데이터 수집
            _state.value = HomeState.Initializing("Fear & Greed Index 데이터 수집 중...", 0)
            val result = fearGreedRepository.initializeFearGreed(days)

            if (result.isSuccess) {
                _state.value = HomeState.Success("Fear & Greed Index 데이터 수집 완료")
            } else {
                _state.value = HomeState.Error("Fear & Greed Index 데이터 수집 실패: ${result.exceptionOrNull()?.message}")
            }

            checkData()
        }
    }

    // ✅ 전역 수집 상태 관찰
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

                        // ETF 초기화가 완료되었고 첫 실행인 경우 Fear & Greed 다이얼로그 표시
                        if (wasInitializing && _etfInitializationCompleted.value) {
                            checkFearGreedFirstRun()
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
            _state.value = HomeState.Idle(hasData, lastDate)
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

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    EtfMonitorApp.instance.repository,
                    EtfMonitorApp.instance.applicationContext
                ) as T
            }
        }
    }
}

sealed class HomeState {
    object Loading : HomeState()
    data class Idle(val hasData: Boolean, val lastDate: String?) : HomeState()
    data class Initializing(val message: String, val progress: Int) : HomeState()
    data class Updating(val message: String, val progress: Int) : HomeState()
    data class Success(val message: String) : HomeState()
    data class Error(val message: String) : HomeState()
}