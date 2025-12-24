package com.etfmonitor.feature.etf.domain.usecase

import com.etfmonitor.feature.etf.domain.model.ComparisonResult
import com.etfmonitor.feature.etf.domain.repository.EtfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Get ETF Comparison UseCase
 *
 * ETF 보유 종목의 비교 분석 결과를 조회합니다.
 * 최근 2일간의 보유 종목 변화를 비교하여 신규/증가/감소/유지/제외 상태를 분석합니다.
 */
class GetEtfComparisonUseCase @Inject constructor(
    private val repository: EtfRepository
) {
    /**
     * @param etfTicker ETF 종목코드
     * @return 비교 결과 또는 null (데이터가 없는 경우)
     */
    suspend operator fun invoke(etfTicker: String): ComparisonResult? = withContext(Dispatchers.IO) {
        repository.getComparison(etfTicker)
    }
}
