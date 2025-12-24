package com.etfmonitor.feature.market.presentation.feargreed

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.database.EtfDao
import com.etfmonitor.database.entities.Setting
import com.etfmonitor.feature.market.domain.model.FearGreed
import com.etfmonitor.feature.market.domain.model.FearGreedViewState
import com.etfmonitor.feature.market.domain.usecase.CheckFearGreedDataStatusUseCase
import com.etfmonitor.feature.market.domain.usecase.GetRecentFearGreedUseCase
import com.etfmonitor.feature.market.domain.usecase.InitializeFearGreedUseCase
import com.etfmonitor.feature.market.domain.usecase.UpdateFearGreedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fear & Greed ViewModel (Clean Architecture)
 *
 * UseCase 기반으로 리팩토링:
 * - GetRecentFearGreedUseCase: 최근 데이터 조회
 * - InitializeFearGreedUseCase: 데이터 초기화
 * - UpdateFearGreedUseCase: 데이터 업데이트
 * - CheckFearGreedDataStatusUseCase: 데이터 상태 확인
 */
@HiltViewModel
class FearGreedViewModel @Inject constructor(
    private val getRecentFearGreedUseCase: GetRecentFearGreedUseCase,
    private val initializeFearGreedUseCase: InitializeFearGreedUseCase,
    private val updateFearGreedUseCase: UpdateFearGreedUseCase,
    private val checkFearGreedDataStatusUseCase: CheckFearGreedDataStatusUseCase,
    private val etfDao: EtfDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow<FearGreedViewState>(FearGreedViewState.Loading)
    val state: StateFlow<FearGreedViewState> = _state.asStateFlow()

    private val _selectedMarket = MutableStateFlow("KOSPI")
    val selectedMarket: StateFlow<String> = _selectedMarket.asStateFlow()

    private val _fearGreedData = MutableStateFlow<List<FearGreed>>(emptyList())
    val fearGreedData: StateFlow<List<FearGreed>> = _fearGreedData.asStateFlow()

    private val _showFirstRunDialog = MutableStateFlow(false)
    val showFirstRunDialog: StateFlow<Boolean> = _showFirstRunDialog.asStateFlow()

    init {
        checkData()
        loadData()
        checkFirstRun()
    }

    private fun checkFirstRun() {
        viewModelScope.launch {
            val dialogDismissed = etfDao.getSetting("fear_greed_dialog_dismissed")
            val kospiStatus = checkFearGreedDataStatusUseCase("KOSPI")
            val kosdaqStatus = checkFearGreedDataStatusUseCase("KOSDAQ")

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
            etfDao.saveSetting(Setting("fear_greed_dialog_dismissed", "true"))
            _showFirstRunDialog.value = false
        }
    }

    private fun checkData() {
        viewModelScope.launch {
            val status = checkFearGreedDataStatusUseCase(_selectedMarket.value)
            _state.value = FearGreedViewState.Idle(status.hasData, status.latestDate)
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            getRecentFearGreedUseCase(_selectedMarket.value, 365)
                .collect { data ->
                    _fearGreedData.value = data
                }
        }
    }

    fun onSelectedMarketChanged(market: String) {
        _selectedMarket.value = market
        loadData()
        checkData()
    }

    fun initialize(days: Int = 365) {
        viewModelScope.launch {
            _state.value = FearGreedViewState.Initializing("Fear & Greed Index 데이터 수집 중...", 0)

            val result = initializeFearGreedUseCase(days)

            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                _state.value = FearGreedViewState.Success("$count 개의 데이터를 수집했습니다")
                loadData()
                checkData()
            } else {
                val error = result.exceptionOrNull()
                _state.value = FearGreedViewState.Error("데이터 수집 실패: ${error?.message}")
            }
        }
    }

    fun update() {
        viewModelScope.launch {
            _state.value = FearGreedViewState.Updating("Fear & Greed Index 업데이트 중...")

            val result = updateFearGreedUseCase()

            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                _state.value = FearGreedViewState.Success("$count 개의 데이터를 업데이트했습니다")
                loadData()
                checkData()
            } else {
                val error = result.exceptionOrNull()
                _state.value = FearGreedViewState.Error("업데이트 실패: ${error?.message}")
            }
        }
    }

    fun clearMessage() {
        if (_state.value is FearGreedViewState.Success || _state.value is FearGreedViewState.Error) {
            checkData()
        }
    }
}
