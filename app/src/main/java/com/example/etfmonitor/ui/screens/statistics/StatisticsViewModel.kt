package com.etfmonitor.ui.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.etfmonitor.EtfMonitorApp
import com.etfmonitor.database.entities.CashDepositTrend
import com.etfmonitor.database.entities.StockAmountRanking
import com.etfmonitor.database.entities.StockChangeInfo
import com.etfmonitor.repository.DataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StatisticsViewModel(
    private val repository: DataRepository
) : ViewModel() {

    private val _dates = MutableStateFlow<Pair<String, String>?>(null)
    val dates: StateFlow<Pair<String, String>?> = _dates.asStateFlow()

    private val _amountRanking = MutableStateFlow<List<StockAmountRanking>>(emptyList())
    val amountRanking: StateFlow<List<StockAmountRanking>> = _amountRanking.asStateFlow()

    private val _newStocks = MutableStateFlow<List<StockChangeInfo>>(emptyList())
    val newStocks: StateFlow<List<StockChangeInfo>> = _newStocks.asStateFlow()

    private val _removedStocks = MutableStateFlow<List<StockChangeInfo>>(emptyList())
    val removedStocks: StateFlow<List<StockChangeInfo>> = _removedStocks.asStateFlow()

    private val _increasedStocks = MutableStateFlow<List<StockChangeInfo>>(emptyList())
    val increasedStocks: StateFlow<List<StockChangeInfo>> = _increasedStocks.asStateFlow()

    // ✅ 비중 감소 종목 추가
    private val _decreasedStocks = MutableStateFlow<List<StockChangeInfo>>(emptyList())
    val decreasedStocks: StateFlow<List<StockChangeInfo>> = _decreasedStocks.asStateFlow()

    // ✅ 원화예금 추이 추가
    private val _cashDepositTrend = MutableStateFlow<List<CashDepositTrend>>(emptyList())
    val cashDepositTrend: StateFlow<List<CashDepositTrend>> = _cashDepositTrend.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadStatistics()
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _dates.value = repository.getStatisticsDates()
                _amountRanking.value = repository.getStockAmountRanking()
                _newStocks.value = repository.getAllNewStocks()
                _removedStocks.value = repository.getAllRemovedStocks()
                _increasedStocks.value = repository.getAllIncreasedStocks()
                _decreasedStocks.value = repository.getAllDecreasedStocks()  // ✅ 추가
                _cashDepositTrend.value = repository.getCashDepositTrend()  // ✅ 추가
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sortAmountRanking(ascending: Boolean) {
        _amountRanking.value = if (ascending) {
            _amountRanking.value.sortedBy { it.totalAmount }
        } else {
            _amountRanking.value.sortedByDescending { it.totalAmount }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StatisticsViewModel(
                    EtfMonitorApp.instance.repository
                ) as T
            }
        }
    }
}