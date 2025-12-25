package com.etfmonitor.feature.market.presentation.feargreed

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.core.database.entities.FearGreedIndex
import com.etfmonitor.repository.FearGreedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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

/**
 * Production Level FearGreedViewModel with Hilt
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
class FearGreedViewModel @Inject constructor(
    private val repository: FearGreedRepository,
    @ApplicationContext private val context: Context
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
            val dialogDismissed = repository.isDialogDismissed()
            val hasData = repository.getCountByMarket("KOSPI") > 0 ||
                         repository.getCountByMarket("KOSDAQ") > 0

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

    fun onSelectedMarketChanged(market: String) {
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
}
