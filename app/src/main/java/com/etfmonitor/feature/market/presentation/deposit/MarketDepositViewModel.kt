package com.etfmonitor.feature.market.presentation.deposit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.core.analysis.OscillatorCalculator
import com.etfmonitor.feature.market.domain.model.MarketDepositData
import com.etfmonitor.feature.market.domain.repository.MarketDepositRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _state = MutableStateFlow<MarketDepositState>(MarketDepositState.Loading)
    val state: StateFlow<MarketDepositState> = _state.asStateFlow()

    init {
        // 초기화 시 자동으로 DB에서 데이터 로드
        loadMarketDataFromDB()
    }

    private fun loadMarketDataFromDB() {
        viewModelScope.launch {
            try {
                _state.value = MarketDepositState.Loading

                // DB에서 데이터 가져오기 (필요시 자동 업데이트)
                val marketData = repository.getOrUpdateMarketData(limit = 100)

                if (marketData == null) {
                    _state.value = MarketDepositState.Error("저장된 데이터가 없습니다. 설정에서 데이터를 업데이트해주세요.")
                    return@launch
                }

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

            } catch (e: Exception) {
                _state.value = MarketDepositState.Error("데이터 로드 실패: ${e.message}")
            }
        }
    }

    fun refreshData() {
        loadMarketDataFromDB()
    }
}
