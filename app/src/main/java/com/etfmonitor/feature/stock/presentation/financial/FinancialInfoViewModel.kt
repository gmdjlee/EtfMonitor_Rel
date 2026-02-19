package com.etfmonitor.feature.stock.presentation.financial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.feature.stock.domain.model.financial.FinancialState
import com.etfmonitor.feature.stock.domain.model.financial.FinancialTab
import com.etfmonitor.feature.stock.domain.usecase.GetFinancialSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FinancialInfoViewModel @Inject constructor(
    private val getFinancialSummaryUseCase: GetFinancialSummaryUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<FinancialState>(FinancialState.NoStock)
    val state: StateFlow<FinancialState> = _state.asStateFlow()

    private val _selectedTab = MutableStateFlow(FinancialTab.PROFITABILITY)
    val selectedTab: StateFlow<FinancialTab> = _selectedTab.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var currentTicker: String? = null
    private var currentName: String? = null

    fun selectTab(tab: FinancialTab) {
        _selectedTab.value = tab
    }

    fun loadForStock(ticker: String, name: String) {
        if (ticker == currentTicker && _state.value is FinancialState.Success) return
        currentTicker = ticker
        currentName = name
        loadFinancialData(ticker, name, useCache = true)
    }

    fun refresh() {
        val ticker = currentTicker ?: return
        val name = currentName ?: return
        _isRefreshing.value = true
        viewModelScope.launch {
            val result = getFinancialSummaryUseCase.refresh(ticker, name)
            handleResult(result)
            _isRefreshing.value = false
        }
    }

    fun retry() {
        val ticker = currentTicker ?: return
        val name = currentName ?: return
        loadFinancialData(ticker, name, useCache = false)
    }

    fun clearStock() {
        currentTicker = null
        currentName = null
        _state.value = FinancialState.NoStock
    }

    private fun loadFinancialData(ticker: String, name: String, useCache: Boolean) {
        viewModelScope.launch {
            _state.value = FinancialState.Loading
            val result = if (useCache) {
                getFinancialSummaryUseCase(ticker, name, useCache = true)
            } else {
                getFinancialSummaryUseCase.refresh(ticker, name)
            }
            handleResult(result)
        }
    }

    private fun handleResult(result: Result<com.etfmonitor.feature.stock.domain.model.financial.FinancialSummary>) {
        _state.value = result.fold(
            onSuccess = { summary ->
                if (summary.periods.isEmpty()) {
                    FinancialState.Error("재무정보를 찾을 수 없습니다.")
                } else {
                    FinancialState.Success(summary)
                }
            },
            onFailure = { error ->
                when {
                    error.message?.contains("API key") == true -> FinancialState.NoApiKey
                    error.message?.contains("network", ignoreCase = true) == true ->
                        FinancialState.Error("네트워크 연결을 확인해주세요.")
                    else -> FinancialState.Error(
                        error.message ?: "알 수 없는 오류가 발생했습니다."
                    )
                }
            }
        )
    }
}
