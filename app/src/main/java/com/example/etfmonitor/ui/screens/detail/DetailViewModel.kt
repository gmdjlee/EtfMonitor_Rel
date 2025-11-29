package com.etfmonitor.ui.screens.detail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.repository.ComparisonResult
import com.etfmonitor.repository.DataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Production Level DetailViewModel with Hilt
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
class DetailViewModel @Inject constructor(
    private val repository: DataRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "DetailViewModel"
    }

    private val etfTicker: String = savedStateHandle.get<String>("ticker")
        ?: throw IllegalArgumentException("ticker is required")

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
                Log.e(TAG, "Error loading comparison for ticker: $etfTicker", e)
                _state.value = DetailState.Error(e.message ?: "오류 발생")
            }
        }
    }
}

sealed class DetailState {
    object Loading : DetailState()
    data class Success(val comparison: ComparisonResult) : DetailState()
    data class Error(val message: String) : DetailState()
}