package com.etfmonitor.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.etfmonitor.EtfMonitorApp
import com.etfmonitor.repository.ComparisonResult
import com.etfmonitor.repository.DataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(
    private val etfTicker: String,
    private val repository: DataRepository
) : ViewModel() {

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state.asStateFlow()

    // ✅ ETF 이름 추가
    private val _etfName = MutableStateFlow<String>("")
    val etfName: StateFlow<String> = _etfName.asStateFlow()

    init {
        loadComparison()
    }

    private fun loadComparison() {
        viewModelScope.launch {
            try {
                // ✅ ETF 이름 가져오기
                val etf = repository.getEtf(etfTicker)
                _etfName.value = etf?.name ?: etfTicker

                val comparison = repository.getComparison(etfTicker)
                _state.value = if (comparison != null) {
                    DetailState.Success(comparison)
                } else {
                    DetailState.Error("데이터를 찾을 수 없습니다")
                }
            } catch (e: Exception) {
                _state.value = DetailState.Error(e.message ?: "오류 발생")
            }
        }
    }

    companion object {
        fun factory(etfTicker: String): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DetailViewModel(
                        etfTicker,
                        EtfMonitorApp.instance.repository
                    ) as T
                }
            }
        }
    }
}

sealed class DetailState {
    object Loading : DetailState()
    data class Success(val comparison: ComparisonResult) : DetailState()
    data class Error(val message: String) : DetailState()
}