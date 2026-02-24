package com.etfmonitor.feature.etf.domain.usecase

import com.etfmonitor.feature.etf.domain.model.Etf
import com.etfmonitor.feature.etf.domain.repository.EtfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * Get ETF List UseCase
 *
 * 모든 ETF 목록을 조회합니다.
 */
class GetEtfListUseCase @Inject constructor(
    private val repository: EtfRepository
) {
    /**
     * @return 현재 키워드 설정 기반으로 필터링된 ETF 목록 Flow
     */
    operator fun invoke(): Flow<List<Etf>> =
        repository.getVisibleEtfs()
            .flowOn(Dispatchers.IO)
}
