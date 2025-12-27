package com.etfmonitor.feature.etf.domain.usecase

import com.etfmonitor.feature.etf.domain.model.ComparisonResult
import com.etfmonitor.feature.etf.domain.repository.EtfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Get ETF Comparison In Range UseCase
 *
 * ETF 보유 종목의 비교 분석 결과를 지정된 날짜 범위 내에서 조회합니다.
 * 범위 내 가장 최근과 가장 오래된 데이터를 비교합니다.
 */
class GetComparisonInRangeUseCase @Inject constructor(
    private val repository: EtfRepository
) {
    /**
     * @param etfTicker ETF 종목코드
     * @param startDate 시작일 (yyyy-MM-dd)
     * @param endDate 종료일 (yyyy-MM-dd)
     * @return 비교 결과 또는 null (데이터가 없는 경우)
     */
    suspend operator fun invoke(
        etfTicker: String,
        startDate: String,
        endDate: String
    ): ComparisonResult? = withContext(Dispatchers.IO) {
        repository.getComparisonInRange(etfTicker, startDate, endDate)
    }
}
