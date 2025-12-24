package com.etfmonitor.feature.etf.domain.usecase

import com.etfmonitor.feature.etf.domain.model.Etf
import com.etfmonitor.feature.etf.domain.repository.EtfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * Search ETFs UseCase
 *
 * ETF를 검색합니다.
 */
class SearchEtfsUseCase @Inject constructor(
    private val repository: EtfRepository
) {
    /**
     * @param query 검색어 (ticker 또는 name)
     * @return 검색 결과 Flow
     */
    operator fun invoke(query: String): Flow<List<Etf>> =
        repository.searchEtfs(query)
            .flowOn(Dispatchers.IO)
}
