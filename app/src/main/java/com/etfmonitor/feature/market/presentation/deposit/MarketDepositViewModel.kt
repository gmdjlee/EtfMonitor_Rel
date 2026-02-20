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
import kotlinx.coroutines.CancellationException
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
    data class Success(val message: String) : MarketDepositState()
    data class Error(val message: String) : MarketDepositState()
}

/**
 * Production Level MarketDepositViewModel with Hilt
 *
 * FearGreedViewModel 패턴을 따름:
 * - 데이터를 별도 StateFlow로 관리 (depositData)
 * - 상태 변화에도 데이터 유지
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

    // 데이터를 별도 StateFlow로 관리 (FearGreedViewModel 패턴)
    private val _depositData = MutableStateFlow(MarketDepositData.empty())
    val depositData: StateFlow<MarketDepositData> = _depositData.asStateFlow()

    // 시장 분석 결과
    private val _analysis = MutableStateFlow("")
    val analysis: StateFlow<String> = _analysis.asStateFlow()

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
            val updateResult = repository.getOrUpdateMarketData(limit = 500)
            if (updateResult == null) {
                // 네트워크 오류 등으로 업데이트 실패, 캐시된 데이터로 진행
                AppLogger.getLogger("MarketDepositVM").w("Failed to update market data, using cached data")
            }

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

                    // 별도 StateFlow에 데이터 저장 (상태 변화에도 유지)
                    _depositData.value = marketData

                    // 시장 분석 - Convert domain model to core model for OscillatorCalculator
                    val coreMarketData = CoreMarketDepositData(
                        dates = marketData.dates,
                        depositAmounts = marketData.depositAmounts,
                        depositChanges = marketData.depositChanges,
                        creditAmounts = marketData.creditAmounts,
                        creditChanges = marketData.creditChanges
                    )
                    _analysis.value = OscillatorCalculator.analyzeMarketDeposit(coreMarketData)

                    _state.value = MarketDepositState.Success("데이터 로드 완료")
                }
        } catch (e: CancellationException) {
            throw e
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

    /**
     * Success 메시지 클리어 (자동 숨김용)
     */
    fun clearMessage() {
        if (_state.value is MarketDepositState.Success) {
            _state.value = MarketDepositState.Idle
        }
    }
}
