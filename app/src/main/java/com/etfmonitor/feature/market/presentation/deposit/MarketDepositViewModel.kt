package com.etfmonitor.feature.market.presentation.deposit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.feature.market.domain.model.MarketDepositTrend
import com.etfmonitor.feature.market.domain.model.MarketDepositViewState
import com.etfmonitor.feature.market.domain.usecase.GetOrUpdateMarketDepositUseCase
import com.etfmonitor.oscillator.calculator.OscillatorCalculator
import com.etfmonitor.oscillator.model.MarketDepositData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 증시 자금 동향 ViewModel (Clean Architecture)
 *
 * UseCase 기반으로 리팩토링:
 * - GetOrUpdateMarketDepositUseCase: 스마트 업데이트 데이터 조회
 */
@HiltViewModel
class MarketDepositViewModel @Inject constructor(
    private val getOrUpdateMarketDepositUseCase: GetOrUpdateMarketDepositUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<MarketDepositViewState>(MarketDepositViewState.Loading)
    val state: StateFlow<MarketDepositViewState> = _state.asStateFlow()

    init {
        loadMarketData()
    }

    private fun loadMarketData() {
        viewModelScope.launch {
            try {
                _state.value = MarketDepositViewState.Loading

                // UseCase를 통해 데이터 가져오기 (필요시 자동 업데이트)
                val marketData = getOrUpdateMarketDepositUseCase(limit = 100)

                if (marketData == null || marketData.isEmpty) {
                    _state.value = MarketDepositViewState.Error("저장된 데이터가 없습니다. 설정에서 데이터를 업데이트해주세요.")
                    return@launch
                }

                // OscillatorCalculator를 위해 기존 모델로 변환
                val legacyData = MarketDepositData(
                    dates = marketData.dates,
                    depositAmounts = marketData.depositAmounts,
                    depositChanges = marketData.depositChanges,
                    creditAmounts = marketData.creditAmounts,
                    creditChanges = marketData.creditChanges
                )

                // 시장 분석
                val analysis = OscillatorCalculator.analyzeMarketDeposit(legacyData)

                _state.value = MarketDepositViewState.Success(
                    data = marketData,
                    analysis = analysis
                )

            } catch (e: Exception) {
                _state.value = MarketDepositViewState.Error("데이터 로드 실패: ${e.message}")
            }
        }
    }

    fun refreshData() {
        loadMarketData()
    }
}
