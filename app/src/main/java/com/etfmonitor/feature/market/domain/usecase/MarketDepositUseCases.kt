package com.etfmonitor.feature.market.domain.usecase

import com.etfmonitor.feature.market.domain.model.MarketDeposit
import com.etfmonitor.feature.market.domain.model.MarketDepositData
import com.etfmonitor.feature.market.domain.repository.MarketDepositRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 모든 예탁금 데이터 조회 UseCase
 */
class GetAllDepositsUseCase @Inject constructor(
    private val repository: MarketDepositRepository
) {
    operator fun invoke(): Flow<List<MarketDeposit>> = repository.getAllDeposits()
}

/**
 * 최근 예탁금 데이터 조회 UseCase
 */
class GetRecentDepositsUseCase @Inject constructor(
    private val repository: MarketDepositRepository
) {
    operator fun invoke(limit: Int = 100): Flow<List<MarketDeposit>> =
        repository.getRecentDeposits(limit)
}

/**
 * 특정 날짜 예탁금 데이터 조회 UseCase
 */
class GetDepositByDateUseCase @Inject constructor(
    private val repository: MarketDepositRepository
) {
    suspend operator fun invoke(date: String): MarketDeposit? =
        repository.getDepositByDate(date)
}

/**
 * 예탁금 데이터 개수 조회 UseCase
 */
class GetDepositCountUseCase @Inject constructor(
    private val repository: MarketDepositRepository
) {
    suspend operator fun invoke(): Int = repository.getDepositCount()
}

/**
 * 예탁금 데이터 초기화 UseCase
 */
class InitializeDepositsUseCase @Inject constructor(
    private val repository: MarketDepositRepository
) {
    suspend operator fun invoke(
        numPages: Int = 10,
        onProgress: ((String, Int) -> Unit)? = null
    ): Result<Int> = repository.initializeDeposits(numPages, onProgress)
}

/**
 * 예탁금 데이터 업데이트 UseCase
 */
class UpdateDepositsUseCase @Inject constructor(
    private val repository: MarketDepositRepository
) {
    suspend operator fun invoke(numPages: Int = 10): Result<Int> =
        repository.updateDeposits(numPages)
}

/**
 * 예탁금 데이터 스마트 조회 UseCase
 *
 * 12시간 캐싱 전략 적용:
 * - 캐시가 유효하면 DB에서 반환
 * - 캐시가 만료되었거나 없으면 Python으로 갱신 후 반환
 */
class GetOrUpdateMarketDepositUseCase @Inject constructor(
    private val repository: MarketDepositRepository
) {
    suspend operator fun invoke(limit: Int = 100): MarketDepositData? =
        repository.getOrUpdateMarketData(limit)
}

/**
 * 예탁금 데이터 상태 확인 UseCase
 */
class CheckDepositDataStatusUseCase @Inject constructor(
    private val repository: MarketDepositRepository
) {
    suspend operator fun invoke(): DataStatus {
        val count = repository.getDepositCount()
        val lastUpdate = repository.getLastUpdateTime()

        return DataStatus(
            hasData = count > 0,
            recordCount = count,
            latestDate = null, // MarketDeposit은 latestDate 조회 메서드가 없음
            lastUpdateTime = lastUpdate
        )
    }
}
