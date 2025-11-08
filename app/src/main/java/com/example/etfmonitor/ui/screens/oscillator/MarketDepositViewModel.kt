package com.etfmonitor.ui.screens.oscillator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chaquo.python.Python
import com.example.etfmonitor.oscillator.calculator.OscillatorCalculator
import com.example.etfmonitor.oscillator.model.*
import com.example.etfmonitor.oscillator.python.OscillatorPyClient
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
    private val pyClient: OscillatorPyClient
) : ViewModel() {

    private val _state = MutableStateFlow<MarketDepositState>(MarketDepositState.Idle)
    val state: StateFlow<MarketDepositState> = _state.asStateFlow()

    fun analyzeMarket(numPages: Int = 5) {
        viewModelScope.launch {
            try {
                _state.value = MarketDepositState.Loading

                // 증시 자금 동향 데이터 수집
                val marketData = pyClient.getMarketDepositData(numPages)
                if (marketData == null) {
                    _state.value = MarketDepositState.Error("데이터를 가져올 수 없습니다")
                    return@launch
                }

                // 시장 분석
                val analysis = OscillatorCalculator.analyzeMarketDeposit(marketData)

                _state.value = MarketDepositState.Success(
                    data = marketData,
                    analysis = analysis
                )

            } catch (e: Exception) {
                _state.value = MarketDepositState.Error("오류 발생: ${e.message}")
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val python = Python.getInstance()
                val pyClient = OscillatorPyClient(python)
                MarketDepositViewModel(pyClient)
            }
        }
    }
}
