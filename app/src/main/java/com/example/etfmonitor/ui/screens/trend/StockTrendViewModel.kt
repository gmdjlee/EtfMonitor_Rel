package com.etfmonitor.ui.screens.trend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.etfmonitor.EtfMonitorApp
import com.etfmonitor.repository.DataRepository
import com.etfmonitor.repository.StockTrend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StockTrendViewModel(
    private val etfTicker: String,
    private val stockTicker: String,
    private val repository: DataRepository
) : ViewModel() {

    private val _state = MutableStateFlow<TrendState>(TrendState.Loading)
    val state: StateFlow<TrendState> = _state.asStateFlow()

    init {
        loadTrend()
    }

    private fun loadTrend() {
        viewModelScope.launch {
            try {
                val trend = repository.getStockTrend(etfTicker, stockTicker)
                _state.value = if (trend != null) {
                    TrendState.Success(trend)
                } else {
                    TrendState.Error("추이 데이터를 찾을 수 없습니다")
                }
            } catch (e: Exception) {
                _state.value = TrendState.Error(e.message ?: "오류 발생")
            }
        }
    }

    companion object {
        fun factory(etfTicker: String, stockTicker: String): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return StockTrendViewModel(
                        etfTicker,
                        stockTicker,
                        EtfMonitorApp.instance.repository
                    ) as T
                }
            }
        }
    }
}

sealed class TrendState {
    object Loading : TrendState()
    data class Success(val trend: StockTrend) : TrendState()
    data class Error(val message: String) : TrendState()
}