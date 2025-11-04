package com.etfmonitor.ui.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.etfmonitor.EtfMonitorApp
import com.etfmonitor.database.entities.Etf
import com.etfmonitor.repository.DataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class EtfListViewModel(
    private val repository: DataRepository
) : ViewModel() {

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

    fun clearSearch() {
        _searchQuery.value = ""
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EtfListViewModel(EtfMonitorApp.instance.repository) as T
            }
        }
    }
}

sealed class ListState {
    object Loading : ListState()
    data class Success(val etfs: List<Etf>) : ListState()
    object Empty : ListState()
    data class Error(val message: String) : ListState()
}