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
                    if (_state.value is HomeState.Initializing || _state.value is HomeState.Updating) {
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
            _state.value = HomeState.Idle(hasData, lastDate)
        }
    }

    fun initialize(days: Int? = null) {
        viewModelScope.launch {
            val daysToUse = days ?: repository.getDefaultDays()
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