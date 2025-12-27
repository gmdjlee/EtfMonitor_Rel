package com.etfmonitor.feature.etf.domain.usecase

import com.etfmonitor.feature.etf.domain.repository.EtfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Get Available Dates UseCase
 *
 * 데이터베이스에 저장된 모든 날짜 목록을 조회합니다.
 */
class GetAvailableDatesUseCase @Inject constructor(
    private val repository: EtfRepository
) {
    /**
     * @param limit 최대 조회 개수
     * @return 날짜 목록 (내림차순)
     */
    suspend operator fun invoke(limit: Int = 100): List<String> = withContext(Dispatchers.IO) {
        repository.getAvailableDates(limit)
    }
}
