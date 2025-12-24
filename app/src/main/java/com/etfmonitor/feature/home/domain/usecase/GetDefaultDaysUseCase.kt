package com.etfmonitor.feature.home.domain.usecase

import com.etfmonitor.feature.home.domain.repository.HomeRepository
import javax.inject.Inject

/**
 * 기본 수집 일수 조회 UseCase
 */
class GetDefaultDaysUseCase @Inject constructor(
    private val repository: HomeRepository
) {
    /**
     * 기본 수집 일수 조회
     *
     * @return 기본 수집 일수
     */
    suspend operator fun invoke(): Int {
        return repository.getDefaultDays()
    }
}
