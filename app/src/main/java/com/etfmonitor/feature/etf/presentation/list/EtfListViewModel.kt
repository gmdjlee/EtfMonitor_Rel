package com.etfmonitor.feature.etf.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.feature.etf.domain.usecase.GetEtfListUseCase
import com.etfmonitor.feature.etf.domain.usecase.SearchEtfsUseCase
import com.etfmonitor.core.common.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ETF List ViewModel
 *
 * Clean Architecture 패턴을 따라 UseCase를 통해 비즈니스 로직에 접근합니다.
 *
 * ## 최적화 포인트
 * - @HiltViewModel: Hilt가 ViewModel 생명주기 자동 관리
 * - @Inject: 생성자 주입으로 의존성 명확화
 * - UseCase를 통한 비즈니스 로직 분리
 *
 * ## 검색 로직
 * - 300ms debounce로 타이핑 중 불필요한 검색 방지
 * - flatMapLatest로 최신 검색어만 처리
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EtfListViewModel @Inject constructor(
    private val getEtfListUseCase: GetEtfListUseCase,
    private val searchEtfsUseCase: SearchEtfsUseCase
) : ViewModel() {

    companion object {
        private val logger = AppLogger.getLogger("EtfListViewModel")
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _state = MutableStateFlow<EtfListState>(EtfListState.Loading)
    val state: StateFlow<EtfListState> = _state.asStateFlow()

    init {
        loadEtfs()
    }

    private fun loadEtfs() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        getEtfListUseCase()
                    } else {
                        searchEtfsUseCase(query)
                    }
                }
                .catch { e ->
                    logger.e("Error loading ETF list", e)
                    _state.value = EtfListState.Error(e.message ?: "오류 발생")
                }
                .collect { etfs ->
                    _state.value = if (etfs.isEmpty()) {
                        EtfListState.Empty
                    } else {
                        EtfListState.Success(etfs)
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
