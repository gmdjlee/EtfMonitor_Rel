package com.etfmonitor.feature.etf.domain.usecase

import com.etfmonitor.feature.etf.domain.model.DataStatus
import com.etfmonitor.feature.etf.domain.repository.EtfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Check Data Status UseCase
 *
 * ETF 데이터 상태(데이터 유무, 최신 날짜)를 확인합니다.
 */
class CheckDataStatusUseCase @Inject constructor(
    private val repository: EtfRepository
) {
    /**
     * @return DataStatus 객체
     */
    suspend operator fun invoke(): DataStatus = withContext(Dispatchers.IO) {
        repository.getDataStatus()
    }

    /**
     * 데이터 존재 여부만 확인
     * @return 데이터가 있으면 true
     */
    suspend fun hasData(): Boolean = withContext(Dispatchers.IO) {
        repository.hasData()
    }
}
