package com.etfmonitor.feature.market.presentation.deposit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.core.analysis.OscillatorCalculator
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.service.CollectionState
import com.etfmonitor.core.ui.component.ChartLabelCalculator
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.feature.market.domain.model.MarketDepositData
import com.etfmonitor.feature.market.domain.repository.MarketDepositRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.etfmonitor.core.analysis.model.MarketDepositData as CoreMarketDepositData

sealed class MarketDepositState {
    data object Idle : MarketDepositState()
    data object Loading : MarketDepositState()
    data class Success(
        val data: MarketDepositData,
        val analysis: String
    ) : MarketDepositState()
    data class Error(val message: String) : MarketDepositState()
}

/**
 * Production Level MarketDepositViewModel with Hilt
 *
 * 최적화 포인트:
 * 1. @HiltViewModel: Hilt가 ViewModel 생명주기 자동 관리
 * 2. @Inject: 생성자 주입으로 의존성 명확화
 * 3. Factory 패턴 제거: Hilt가 자동으로 ViewModel 생성
 * 4. AndroidViewModel → ViewModel: Application 직접 주입 제거
 *
 * 기존 문제점 해결:
 * - EtfMonitorApp.instance 제거: 메모리 누수 위험 제거
 * - 수동 Factory 제거: Hilt가 자동으로 관리하여 코드 간결화
 */
@HiltViewModel
class MarketDepositViewModel @Inject constructor(
    private val repository: MarketDepositRepository
) : ViewModel() {

    companion object {
        private val logger = AppLogger.getLogger("MarketDepositViewModel")
    }

    private val _state = MutableStateFlow<MarketDepositState>(MarketDepositState.Loading)
    val state: StateFlow<MarketDepositState> = _state.asStateFlow()

    // 날짜 범위 선택 상태
    private val _selectedRange = MutableStateFlow(DateRangeOption.DEFAULT)
    val selectedRange: StateFlow<DateRangeOption> = _selectedRange.asStateFlow()

    init {
        // 날짜 범위 변경 관찰
        observeDateRangeChanges()
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
     * 데이터 수집 완료 상태를 관찰하여 자동 새로고침
     */
    private fun observeCollectionState() {
        viewModelScope.launch {
            CollectionState.isCollecting.collect { isCollecting ->
                // 수집이 완료되면 (false로 변경되면) 데이터 새로고침
                if (!isCollecting) {
                    logger.d("Data collection completed, triggering refresh")
                    // 현재 범위로 리로드 트리거
                    val currentRange = _selectedRange.value
                    _selectedRange.value = currentRange
                }
            }
        }
    }

    /**
     * 날짜 범위에 따른 데이터 로딩
     */
    private suspend fun loadDataByRange(range: DateRangeOption) {
        try {
            _state.value = MarketDepositState.Loading

            val (startDate, endDate) = ChartLabelCalculator.calculateDateRange(range)

            // 먼저 DB에서 데이터 확인 (필요시 자동 업데이트)
            repository.getOrUpdateMarketData(limit = 500)

            // 날짜 범위로 데이터 조회
            repository.getByDateRange(startDate, endDate)
                .collectLatest { deposits ->
                    if (deposits.isEmpty()) {
                        _state.value = MarketDepositState.Error("저장된 데이터가 없습니다. 설정에서 데이터를 업데이트해주세요.")
                        return@collectLatest
                    }

                    // MarketDeposit 리스트를 MarketDepositData로 변환
                    val marketData = MarketDepositData(
                        dates = deposits.map { it.date },
                        depositAmounts = deposits.map { it.depositAmount.toDouble() },
                        depositChanges = deposits.map { it.depositChange.toDouble() },
                        creditAmounts = deposits.map { it.creditAmount.toDouble() },
                        creditChanges = deposits.map { it.creditChange.toDouble() }
                    )

                    // 시장 분석 - Convert domain model to core model for OscillatorCalculator
                    val coreMarketData = CoreMarketDepositData(
                        dates = marketData.dates,
                        depositAmounts = marketData.depositAmounts,
                        depositChanges = marketData.depositChanges,
                        creditAmounts = marketData.creditAmounts,
                        creditChanges = marketData.creditChanges
                    )
                    val analysis = OscillatorCalculator.analyzeMarketDeposit(coreMarketData)

                    _state.value = MarketDepositState.Success(
                        data = marketData,
                        analysis = analysis
                    )
                }
        } catch (e: Exception) {
            _state.value = MarketDepositState.Error("데이터 로드 실패: ${e.message}")
        }
    }

    /**
     * 날짜 범위 변경
     */
    fun updateDateRange(option: DateRangeOption) {
        _selectedRange.value = option
        // observeDateRangeChanges가 자동으로 데이터 로드 트리거
    }

    fun refreshData() {
        // 현재 범위로 리로드 트리거
        val currentRange = _selectedRange.value
        _selectedRange.value = currentRange
    }
}
