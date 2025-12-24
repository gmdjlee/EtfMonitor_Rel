package com.etfmonitor.feature.etf.domain.usecase

import com.etfmonitor.feature.etf.domain.model.Etf
import com.etfmonitor.feature.etf.domain.repository.EtfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Get ETF Detail UseCase
 *
 * 특정 ETF의 상세 정보를 조회합니다.
 */
class GetEtfDetailUseCase @Inject constructor(
    private val repository: EtfRepository
) {
    /**
     * @param ticker ETF 종목코드
     * @return ETF 정보 또는 null
     */
    suspend operator fun invoke(ticker: String): Etf? = withContext(Dispatchers.IO) {
        repository.getEtf(ticker)
    }
}
