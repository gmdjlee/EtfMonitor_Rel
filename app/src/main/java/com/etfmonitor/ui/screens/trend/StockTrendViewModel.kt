package com.etfmonitor.ui.screens.trend

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.repository.DataRepository
import com.etfmonitor.repository.StockTrend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Production Level StockTrendViewModel with Hilt
 *
 * 최적화 포인트:
 * 1. @HiltViewModel: Hilt가 ViewModel 생명주기 자동 관리
 * 2. @Inject: 생성자 주입으로 의존성 명확화
 * 3. SavedStateHandle: Navigation arguments 자동 주입
 * 4. Factory 패턴 제거: Hilt가 자동으로 ViewModel 생성
 *
 * 기존 문제점 해결:
 * - EtfMonitorApp.instance 제거: 메모리 누수 위험 제거
 * - 수동 Factory 제거: Hilt가 자동으로 관리하여 코드 간결화
 */
@HiltViewModel
class StockTrendViewModel @Inject constructor(
    private val repository: DataRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "StockTrendViewModel"
    }

    private val etfTicker: String = savedStateHandle.get<String>("etfTicker")
        ?: throw IllegalArgumentException("etfTicker is required")

    private val stockTicker: String = savedStateHandle.get<String>("stockTicker")
        ?: throw IllegalArgumentException("stockTicker is required")

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
                Log.e(TAG, "Error loading trend for ETF: $etfTicker, Stock: $stockTicker", e)
                _state.value = TrendState.Error(e.message ?: "오류 발생")
            }
        }
    }
}

sealed class TrendState {
    object Loading : TrendState()
    data class Success(val trend: StockTrend) : TrendState()
    data class Error(val message: String) : TrendState()
}