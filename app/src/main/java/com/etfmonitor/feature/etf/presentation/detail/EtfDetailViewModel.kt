package com.etfmonitor.feature.etf.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.feature.etf.domain.usecase.GetEtfComparisonUseCase
import com.etfmonitor.feature.etf.domain.usecase.GetEtfDetailUseCase
import com.etfmonitor.core.common.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 */
@HiltViewModel
class EtfDetailViewModel @Inject constructor(
    private val getEtfDetailUseCase: GetEtfDetailUseCase,
    private val getEtfComparisonUseCase: GetEtfComparisonUseCase,
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

    init {
        loadComparison()
    }

    private fun loadComparison() {
        viewModelScope.launch {
            try {
                // ETF 이름 가져오기
                val etf = getEtfDetailUseCase(etfTicker)
                _etfName.value = etf?.name ?: etfTicker

                // 비교 데이터 가져오기
                val comparison = getEtfComparisonUseCase(etfTicker)
                _state.value = if (comparison != null) {
                    EtfDetailState.Success(comparison)
                } else {
                    EtfDetailState.Error("데이터를 찾을 수 없습니다")
                }
            } catch (e: Exception) {
                logger.e("Error loading comparison for ticker: $etfTicker", e)
                _state.value = EtfDetailState.Error(e.message ?: "오류 발생")
            }
        }
    }
}
