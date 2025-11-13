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

                // DB에서 데이터 가져오기 (필요시 자동 업데이트)
                val marketData = repository.getOrUpdateMarketData(limit = 100)

                if (marketData == null) {
                    _state.value = MarketDepositState.Error("저장된 데이터가 없습니다. 설정에서 데이터를 업데이트해주세요.")
                    return@launch
                }

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
                // Use singleton repository from EtfMonitorApp for optimized memory usage
                return MarketDepositViewModel(
                    application = app,
                    repository = app.marketDepositRepository
                ) as T
            }
        }
    }
}
