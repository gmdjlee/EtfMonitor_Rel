package com.etfmonitor.feature.home.domain.usecase

import com.etfmonitor.feature.home.domain.model.DataStatus
import com.etfmonitor.feature.home.domain.repository.HomeRepository
import javax.inject.Inject

/**
 * 데이터 상태 확인 UseCase
 *
 * ETF, 증시 자금, Fear & Greed, 과매수/과매도 데이터의 존재 여부를 확인합니다.
 */
class CheckDataStatusUseCase @Inject constructor(
    private val repository: HomeRepository
) {
    /**
     * 데이터 상태 확인
     *
     * @return DataStatus 각 데이터 타입의 존재 여부
     */
    suspend operator fun invoke(): DataStatus {
        return repository.getDataStatus()
    }
}

/**
 * ETF 데이터 존재 여부 및 마지막 날짜 확인 UseCase
 */
class CheckEtfDataUseCase @Inject constructor(
    private val repository: HomeRepository
) {
    /**
     * ETF 데이터 존재 여부 및 마지막 날짜 확인
     *
     * @return Pair(hasData, lastDate)
     */
    suspend operator fun invoke(): Pair<Boolean, String?> {
        val hasData = repository.hasEtfData()
        val lastDate = repository.getLatestDate()
        return hasData to lastDate
    }
}
