package com.etfmonitor.ui.screens.oscillator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.etfmonitor.EtfMonitorApp
import com.etfmonitor.oscillator.calculator.OscillatorCalculator
import com.etfmonitor.oscillator.model.*
import com.etfmonitor.repository.MarketDepositRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class MarketDepositState {
    data object Idle : MarketDepositState()
    data object Loading : MarketDepositState()
    data class Success(
        val data: MarketDepositData,
        val analysis: String
    ) : MarketDepositState()
    data class Error(val message: String) : MarketDepositState()
}

class MarketDepositViewModel(
    application: Application,
    private val repository: MarketDepositRepository
) : AndroidViewModel(application) {

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

                // DB에서 최근 100개 데이터 가져오기
                val deposits = repository.getRecentDeposits(100).first()

                if (deposits.isEmpty()) {
                    _state.value = MarketDepositState.Error("저장된 데이터가 없습니다. 설정에서 데이터를 업데이트해주세요.")
                    return@launch
                }

                // MarketDeposit 리스트를 MarketDepositData로 변환
                val marketData = MarketDepositData(
                    dates = deposits.map { it.date }.reversed(), // 오래된 순서로 정렬
                    depositAmounts = deposits.map { it.depositAmount }.reversed(),
                    depositChanges = deposits.map { it.depositChange }.reversed(),
                    creditAmounts = deposits.map { it.creditAmount }.reversed(),
                    creditChanges = deposits.map { it.creditChange }.reversed()
                )

                // 시장 분석
                val analysis = OscillatorCalculator.analyzeMarketDeposit(marketData)

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

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = EtfMonitorApp.instance
                return MarketDepositViewModel(
                    application = app,
                    repository = MarketDepositRepository(
                        marketDepositDao = app.database.marketDepositDao(),
                        python = app.python
                    )
                ) as T
            }
        }
    }
}
