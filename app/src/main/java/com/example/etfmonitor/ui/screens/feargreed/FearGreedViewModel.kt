package com.etfmonitor.ui.screens.feargreed

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.etfmonitor.EtfMonitorApp
import com.etfmonitor.database.entities.FearGreedIndex
import com.etfmonitor.repository.FearGreedRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Fear & Greed Index 화면 상태
 */
sealed class FearGreedState {
    object Loading : FearGreedState()
    data class Idle(val hasData: Boolean, val latestDate: String?) : FearGreedState()
    data class Initializing(val message: String, val progress: Int) : FearGreedState()
    data class Updating(val message: String) : FearGreedState()
    data class Success(val message: String) : FearGreedState()
    data class Error(val message: String) : FearGreedState()
}

class FearGreedViewModel(
    private val repository: FearGreedRepository,
    private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow<FearGreedState>(FearGreedState.Loading)
    val state: StateFlow<FearGreedState> = _state.asStateFlow()

    private val _selectedMarket = MutableStateFlow("KOSPI")
    val selectedMarket: StateFlow<String> = _selectedMarket.asStateFlow()

    private val _fearGreedData = MutableStateFlow<List<FearGreedIndex>>(emptyList())
    val fearGreedData: StateFlow<List<FearGreedIndex>> = _fearGreedData.asStateFlow()

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
            val isFirstRun = app.database.dao().getSetting("is_first_run_feargreed")
            val hasData = repository.getCountByMarket("KOSPI") > 0 ||
                         repository.getCountByMarket("KOSDAQ") > 0

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
                com.etfmonitor.database.entities.Setting("is_first_run_feargreed", "false")
            )
            _showFirstRunDialog.value = false
        }
    }

    private fun checkData() {
        viewModelScope.launch {
            val kospiCount = repository.getCountByMarket("KOSPI")
            val kosdaqCount = repository.getCountByMarket("KOSDAQ")
            val hasData = kospiCount > 0 || kosdaqCount > 0

            val latestDate = repository.getLatestDate(_selectedMarket.value)
            _state.value = FearGreedState.Idle(hasData, latestDate)
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            repository.getRecentByMarket(_selectedMarket.value, 365)
                .collect { data ->
                    _fearGreedData.value = data
                }
        }
    }

    fun setSelectedMarket(market: String) {
        _selectedMarket.value = market
        loadData()
        checkData()
    }

    fun initialize(days: Int = 365) {
        viewModelScope.launch {
            _state.value = FearGreedState.Initializing("Fear & Greed Index 데이터 수집 중...", 0)

            val result = repository.initializeFearGreed(days)

            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                _state.value = FearGreedState.Success("$count 개의 데이터를 수집했습니다")
                loadData()
                checkData()
            } else {
                val error = result.exceptionOrNull()
                _state.value = FearGreedState.Error("데이터 수집 실패: ${error?.message}")
            }
        }
    }

    fun update() {
        viewModelScope.launch {
            _state.value = FearGreedState.Updating("Fear & Greed Index 업데이트 중...")

            val result = repository.updateFearGreed()

            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                _state.value = FearGreedState.Success("$count 개의 데이터를 업데이트했습니다")
                loadData()
                checkData()
            } else {
                val error = result.exceptionOrNull()
                _state.value = FearGreedState.Error("업데이트 실패: ${error?.message}")
            }
        }
    }

    fun clearMessage() {
        if (_state.value is FearGreedState.Success || _state.value is FearGreedState.Error) {
            checkData()
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext as EtfMonitorApp
                return FearGreedViewModel(app.fearGreedRepository, context) as T
            }
        }
    }
}
