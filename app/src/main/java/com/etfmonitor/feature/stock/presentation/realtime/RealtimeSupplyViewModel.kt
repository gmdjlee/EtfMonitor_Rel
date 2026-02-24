package com.etfmonitor.feature.stock.presentation.realtime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.feature.stock.domain.model.RealtimeSupplySummary
import com.etfmonitor.feature.stock.domain.model.TradingHours
import com.etfmonitor.feature.stock.domain.usecase.GetRealtimeSupplyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class RealtimeSupplyState {
    data object Idle : RealtimeSupplyState()
    data object Loading : RealtimeSupplyState()
    data class Success(val summary: RealtimeSupplySummary) : RealtimeSupplyState()
    data class Error(val message: String) : RealtimeSupplyState()
}

@HiltViewModel
class RealtimeSupplyViewModel @Inject constructor(
    private val getRealtimeSupplyUseCase: GetRealtimeSupplyUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<RealtimeSupplyState>(RealtimeSupplyState.Idle)
    val state: StateFlow<RealtimeSupplyState> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _autoRefreshEnabled = MutableStateFlow(true)
    val autoRefreshEnabled: StateFlow<Boolean> = _autoRefreshEnabled.asStateFlow()

    private var currentTicker: String? = null
    private var autoRefreshJob: Job? = null

    fun loadForStock(ticker: String) {
        if (ticker == currentTicker && _state.value is RealtimeSupplyState.Success) return
        currentTicker = ticker
        fetchData(ticker, useCache = true)
        startAutoRefresh(ticker)
    }

    fun refresh() {
        val ticker = currentTicker ?: return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                val result = getRealtimeSupplyUseCase(ticker, useCache = false)
                handleResult(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Refresh error: ${e.message}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun toggleAutoRefresh() {
        _autoRefreshEnabled.value = !_autoRefreshEnabled.value
        val ticker = currentTicker ?: return
        if (_autoRefreshEnabled.value) {
            startAutoRefresh(ticker)
        } else {
            stopAutoRefresh()
        }
    }

    fun clearStock() {
        currentTicker = null
        stopAutoRefresh()
        _state.value = RealtimeSupplyState.Idle
    }

    private fun fetchData(ticker: String, useCache: Boolean) {
        viewModelScope.launch {
            _state.value = RealtimeSupplyState.Loading
            try {
                val result = getRealtimeSupplyUseCase(ticker, useCache = useCache)
                handleResult(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = RealtimeSupplyState.Error(
                    e.message ?: "알 수 없는 오류가 발생했습니다."
                )
            }
        }
    }

    private fun handleResult(result: Result<RealtimeSupplySummary>) {
        _state.value = result.fold(
            onSuccess = { summary -> RealtimeSupplyState.Success(summary) },
            onFailure = { error ->
                when {
                    error.message?.contains("API key", ignoreCase = true) == true ||
                    error.message?.contains("NoApiKeyError", ignoreCase = true) == true ->
                        RealtimeSupplyState.Error("키움 API 키가 설정되지 않았습니다.\n설정 화면에서 키움 API 키를 입력해주세요.")
                    error.message?.contains("network", ignoreCase = true) == true ->
                        RealtimeSupplyState.Error("네트워크 연결을 확인해주세요.")
                    else -> RealtimeSupplyState.Error(
                        error.message ?: "알 수 없는 오류가 발생했습니다."
                    )
                }
            }
        )
    }

    private fun startAutoRefresh(ticker: String) {
        stopAutoRefresh()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                if (_autoRefreshEnabled.value && TradingHours.isTradingHours()) {
                    try {
                        val result = getRealtimeSupplyUseCase(ticker, useCache = false)
                        handleResult(result)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.e("Auto-refresh error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoRefresh()
    }

    companion object {
        private const val AUTO_REFRESH_INTERVAL_MS = 60_000L
        private val logger = AppLogger.getLogger("RealtimeSupplyVM")
    }
}
