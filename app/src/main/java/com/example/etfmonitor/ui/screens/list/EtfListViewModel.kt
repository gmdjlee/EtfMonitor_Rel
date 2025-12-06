package com.etfmonitor.ui.screens.list

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.database.entities.Etf
import com.etfmonitor.repository.DataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Production Level EtfListViewModel with Hilt
 *
 * 최적화 포인트:
 * 1. @HiltViewModel: Hilt가 ViewModel 생명주기 자동 관리
 * 2. @Inject: 생성자 주입으로 의존성 명확화
 * 3. Factory 패턴 제거: Hilt가 자동으로 ViewModel 생성
 *
 * 기존 문제점 해결:
 * - EtfMonitorApp.instance 제거: 메모리 누수 위험 제거
 * - 수동 Factory 제거: Hilt가 자동으로 관리하여 코드 간결화
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EtfListViewModel @Inject constructor(
    private val repository: DataRepository
) : ViewModel() {

    companion object {
        private const val TAG = "EtfListViewModel"
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _state = MutableStateFlow<ListState>(ListState.Loading)
    val state: StateFlow<ListState> = _state.asStateFlow()

    init {
        loadEtfs()
    }

    private fun loadEtfs() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        repository.getAllEtfs()
                    } else {
                        repository.searchEtfs(query)
                    }
                }
                .catch { e ->
                    Log.e(TAG, "Error loading ETF list", e)
                    _state.value = ListState.Error(e.message ?: "오류 발생")
                }
                .collect { etfs ->
                    _state.value = if (etfs.isEmpty()) {
                        ListState.Empty
                    } else {
                        ListState.Success(etfs)
                    }
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onClearSearch() {
        _searchQuery.value = ""
    }
}

sealed class ListState {
    object Loading : ListState()
    data class Success(val etfs: List<Etf>) : ListState()
    object Empty : ListState()
    data class Error(val message: String) : ListState()
}