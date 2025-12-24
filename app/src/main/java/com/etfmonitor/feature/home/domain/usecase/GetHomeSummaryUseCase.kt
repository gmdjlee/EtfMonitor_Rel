package com.etfmonitor.feature.home.domain.usecase

import com.etfmonitor.feature.home.domain.model.HomeSummary
import com.etfmonitor.feature.home.domain.repository.HomeRepository
import javax.inject.Inject

/**
 * 홈 화면 요약 데이터 조회 UseCase
 *
 * 시장 상태, Fear & Greed, 과매수/과매도 등 홈 화면에 표시할
 * 요약 데이터를 조회합니다.
 */
class GetHomeSummaryUseCase @Inject constructor(
    private val repository: HomeRepository
) {
    /**
     * 홈 화면 요약 데이터 조회
     *
     * @return HomeSummary 또는 null (데이터 없음)
     */
    suspend operator fun invoke(): HomeSummary? {
        return repository.getHomeSummary()
    }
}
