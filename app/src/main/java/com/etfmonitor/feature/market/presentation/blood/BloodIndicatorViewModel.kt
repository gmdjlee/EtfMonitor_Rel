package com.etfmonitor.feature.market.presentation.blood

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.service.CollectionState
import com.etfmonitor.core.ui.component.ChartLabelCalculator
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.feature.market.domain.model.BloodIndicator
import com.etfmonitor.feature.market.domain.repository.BloodIndicatorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Blood Indicator 화면 상태
 */
sealed class BloodIndicatorState {
    object Loading : BloodIndicatorState()
    data class Idle(val hasData: Boolean, val latestDate: String?) : BloodIndicatorState()
    data class Initializing(val message: String, val progress: Int) : BloodIndicatorState()
    data class Updating(val message: String) : BloodIndicatorState()
    data class Success(val message: String) : BloodIndicatorState()
    data class Error(val message: String) : BloodIndicatorState()
}

/**
 * Blood Indicator ViewModel
 *
 * US Treasury 기반 시장 건강도 지표 화면 관리
 *
 * BLOOD = IRX (3M T-Bill) / (HYG Yield - 10Y Treasury)
 * - 상승 추세 (RISK_ON): 시장이 건강하고 위험 자산 선호
 * - 하락 추세 (RISK_OFF): 시장 스트레스, 안전 자산 선호
 */
@HiltViewModel
class BloodIndicatorViewModel @Inject constructor(
    private val repository: BloodIndicatorRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private val logger = AppLogger.getLogger("BloodIndicatorViewModel")
    }

    private val _state = MutableStateFlow<BloodIndicatorState>(BloodIndicatorState.Loading)
    val state: StateFlow<BloodIndicatorState> = _state.asStateFlow()

    // 날짜 범위 선택 상태 (Blood Indicator 기본값: 5년)
    private val _selectedRange = MutableStateFlow(DateRangeOption.FIVE_YEARS)
    val selectedRange: StateFlow<DateRangeOption> = _selectedRange.asStateFlow()

    private val _bloodData = MutableStateFlow<List<BloodIndicator>>(emptyList())
    val bloodData: StateFlow<List<BloodIndicator>> = _bloodData.asStateFlow()

    private val _showFirstRunDialog = MutableStateFlow(false)
    val showFirstRunDialog: StateFlow<Boolean> = _showFirstRunDialog.asStateFlow()

    init {
        checkData()
        observeDateRangeChanges()
        checkFirstRun()
        observeCollectionState()
    }

    /**
     * 날짜 범위 변경을 관찰하여 데이터 로딩
     */
    private fun observeDateRangeChanges() {
        viewModelScope.launch {
            _selectedRange.collectLatest { range ->
                loadDataByRange(range)
            }
        }
    }

    /**
     * 날짜 범위에 따른 데이터 로딩
     */
    private suspend fun loadDataByRange(range: DateRangeOption) {
        val (startDate, endDate) = if (range == DateRangeOption.ALL) {
            // ALL 옵션: 실제 DB의 가장 이른 날짜부터 최근 날짜까지
            val earliest = repository.getEarliestDate() ?: "2015-01-01"
            val latest = repository.getLatestDate() ?: java.time.LocalDate.now().toString()
            Pair(earliest, latest)
        } else {
            ChartLabelCalculator.calculateDateRange(range)
        }
        repository.getByDateRange(startDate, endDate)
            .collect { data ->
                _bloodData.value = data
            }
    }

    /**
     * 데이터 수집 완료 상태를 관찰하여 자동 새로고침
     */
    private fun observeCollectionState() {
        viewModelScope.launch {
            CollectionState.isCollecting.collect { isCollecting ->
                if (!isCollecting) {
                    logger.d("Data collection completed, triggering refresh")
                    val currentRange = _selectedRange.value
                    _selectedRange.value = currentRange
                    checkData()
                }
            }
        }
    }

    private fun checkFirstRun() {
        viewModelScope.launch {
            val dialogDismissed = repository.isDialogDismissed()
            val hasData = repository.getCount() > 0

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
            val count = repository.getCount()
            val hasData = count > 0
            val latestDate = repository.getLatestDate()
            _state.value = BloodIndicatorState.Idle(hasData, latestDate)
        }
    }

    /**
     * 날짜 범위 변경
     */
    fun updateDateRange(option: DateRangeOption) {
        _selectedRange.value = option
    }

    fun initialize(days: Int = 1825) {
        viewModelScope.launch {
            _state.value = BloodIndicatorState.Initializing("Blood Indicator 데이터 수집 중...", 0)

            val result = repository.initializeBloodIndicator(days) { message, progress ->
                _state.value = BloodIndicatorState.Initializing(message, progress)
            }

            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                _state.value = BloodIndicatorState.Success("$count 개의 데이터를 수집했습니다")
                // 데이터 수집 후 현재 범위로 리로드 트리거
                val currentRange = _selectedRange.value
                _selectedRange.value = currentRange
                checkData()
            } else {
                val error = result.exceptionOrNull()
                _state.value = BloodIndicatorState.Error("데이터 수집 실패: ${error?.message}")
            }
        }
    }

    fun update() {
        viewModelScope.launch {
            _state.value = BloodIndicatorState.Updating("Blood Indicator 업데이트 중...")

            val result = repository.updateBloodIndicator()

            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                _state.value = BloodIndicatorState.Success("$count 개의 데이터를 업데이트했습니다")
                // 데이터 업데이트 후 현재 범위로 리로드 트리거
                val currentRange = _selectedRange.value
                _selectedRange.value = currentRange
                checkData()
            } else {
                val error = result.exceptionOrNull()
                _state.value = BloodIndicatorState.Error("업데이트 실패: ${error?.message}")
            }
        }
    }

    fun clearMessage() {
        if (_state.value is BloodIndicatorState.Success || _state.value is BloodIndicatorState.Error) {
            checkData()
        }
    }
}
