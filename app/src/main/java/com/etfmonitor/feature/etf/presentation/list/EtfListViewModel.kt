package com.etfmonitor.feature.etf.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.core.service.CollectionState
import com.etfmonitor.feature.etf.domain.usecase.GetEtfListUseCase
import com.etfmonitor.feature.etf.domain.usecase.SearchEtfsUseCase
import com.etfmonitor.core.common.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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
 * - 빈 쿼리(초기 로드)는 즉시 처리, 검색어 입력 시에만 300ms debounce 적용
 * - flatMapLatest로 최신 검색어만 처리
 *
 * ## 데이터 수집 완료 감지
 * - CollectionState를 관찰하여 데이터 수집 완료 시 자동 새로고침
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
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

    // 데이터 새로고침 트리거
    private val _refreshTrigger = MutableSharedFlow<Unit>(replay = 1)

    init {
        _refreshTrigger.tryEmit(Unit)
        loadEtfs()
        observeCollectionState()
    }

    private fun loadEtfs() {
        viewModelScope.launch {
            // 검색어와 새로고침 트리거를 결합
            combine(
                _searchQuery,
                _refreshTrigger.onStart { emit(Unit) }
            ) { query, _ -> query }
                .debounce { query ->
                    // 빈 쿼리(초기 로드/새로고침)는 즉시, 검색어는 300ms debounce
                    if (query.isBlank()) 0L else 300L
                }
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

    /**
     * 데이터 수집 완료 상태를 관찰하여 자동 새로고침
     */
    private fun observeCollectionState() {
        viewModelScope.launch {
            CollectionState.isCollecting.collect { isCollecting ->
                // 수집이 완료되면 (false로 변경되면) 데이터 새로고침
                if (!isCollecting) {
                    logger.d("Data collection completed, triggering refresh")
                    _refreshTrigger.emit(Unit)
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

    /**
     * 수동 데이터 새로고침
     */
    fun refresh() {
        viewModelScope.launch {
            _refreshTrigger.emit(Unit)
        }
    }
}
