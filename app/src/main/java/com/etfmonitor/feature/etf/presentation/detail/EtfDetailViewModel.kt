package com.etfmonitor.feature.etf.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.core.service.CollectionState
import com.etfmonitor.core.ui.component.ChartLabelCalculator
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.feature.etf.domain.usecase.GetAvailableDatesUseCase
import com.etfmonitor.feature.etf.domain.usecase.GetComparisonInRangeUseCase
import com.etfmonitor.feature.etf.domain.usecase.GetEtfComparisonUseCase
import com.etfmonitor.feature.etf.domain.usecase.GetEtfDetailUseCase
import com.etfmonitor.core.common.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * ETF Detail ViewModel
 *
 * Clean Architecture 패턴을 따라 UseCase를 통해 비즈니스 로직에 접근합니다.
 *
 * ## 최적화 포인트
 * - @HiltViewModel: Hilt가 ViewModel 생명주기 자동 관리
 * - @Inject: 생성자 주입으로 의존성 명확화
 * - SavedStateHandle: Navigation arguments 자동 주입
 * - UseCase를 통한 비즈니스 로직 분리
 *
 * ## 기간 선택 기능
 * - DateRangeOption을 통해 사용자가 비교 기간을 선택
 * - 선택된 기간 내 가장 최신과 가장 오래된 데이터 비교
 */
@HiltViewModel
class EtfDetailViewModel @Inject constructor(
    private val getEtfDetailUseCase: GetEtfDetailUseCase,
    private val getEtfComparisonUseCase: GetEtfComparisonUseCase,
    private val getComparisonInRangeUseCase: GetComparisonInRangeUseCase,
    private val getAvailableDatesUseCase: GetAvailableDatesUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private val logger = AppLogger.getLogger("EtfDetailViewModel")
    }

    private val etfTicker: String = savedStateHandle.get<String>("ticker")
        ?: throw IllegalArgumentException("ticker is required")

    private val _state = MutableStateFlow<EtfDetailState>(EtfDetailState.Loading)
    val state: StateFlow<EtfDetailState> = _state.asStateFlow()

    private val _etfName = MutableStateFlow<String>("")
    val etfName: StateFlow<String> = _etfName.asStateFlow()

    private val _selectedRange = MutableStateFlow(DateRangeOption.MONTH)
    val selectedRange: StateFlow<DateRangeOption> = _selectedRange.asStateFlow()

    private val _availableDates = MutableStateFlow<List<String>>(emptyList())
    val availableDates: StateFlow<List<String>> = _availableDates.asStateFlow()

    init {
        loadAvailableDates()
        loadComparison()
        observeCollectionState()
    }

    /**
     * 사용 가능한 날짜 목록 로드
     */
    private fun loadAvailableDates() {
        viewModelScope.launch {
            try {
                val dates = getAvailableDatesUseCase()
                _availableDates.value = dates
                logger.d("Available dates loaded: ${dates.size}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Error loading available dates", e)
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
                    loadAvailableDates()
                    loadComparison()
                }
            }
        }
    }

    /**
     * 날짜 범위 선택 변경
     */
    fun updateDateRange(option: DateRangeOption) {
        if (option == _selectedRange.value) return
        _selectedRange.value = option
        logger.d("Date range changed to: ${option.label}")
        loadComparison()
    }

    private fun loadComparison() {
        viewModelScope.launch {
            _state.value = EtfDetailState.Loading

            try {
                // ETF 이름 가져오기
                val etf = getEtfDetailUseCase(etfTicker)
                _etfName.value = etf?.name ?: etfTicker

                // 선택된 기간에 따른 날짜 범위 계산
                val (startDate, endDate) = ChartLabelCalculator.calculateDateRange(
                    option = _selectedRange.value,
                    endDate = LocalDate.now()
                )

                logger.d("Loading comparison for $etfTicker in range: $startDate ~ $endDate")

                // 날짜 범위 내 비교 데이터 가져오기
                val comparison = getComparisonInRangeUseCase(etfTicker, startDate, endDate)

                _state.value = if (comparison != null) {
                    EtfDetailState.Success(comparison)
                } else {
                    EtfDetailState.Error("선택한 기간에 데이터가 없습니다")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Error loading comparison for ticker: $etfTicker", e)
                _state.value = EtfDetailState.Error(e.message ?: "오류 발생")
            }
        }
    }
}
