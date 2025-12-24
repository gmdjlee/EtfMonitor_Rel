package com.etfmonitor.feature.market.domain.usecase

import com.etfmonitor.feature.market.domain.model.MarketDeposit
import com.etfmonitor.feature.market.domain.model.MarketDepositTrend
import com.etfmonitor.feature.market.domain.repository.MarketDepositRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 증시 자금 최근 데이터 조회 UseCase
 */
class GetRecentMarketDepositUseCase @Inject constructor(
    private val repository: MarketDepositRepository
) {
    operator fun invoke(limit: Int = 100): Flow<List<MarketDeposit>> =
        repository.getRecentDeposits(limit)
}

/**
 * 증시 자금 데이터 (스마트 업데이트) 조회 UseCase
 */
class GetOrUpdateMarketDepositUseCase @Inject constructor(
    private val repository: MarketDepositRepository
) {
    suspend operator fun invoke(limit: Int = 100): MarketDepositTrend? =
        repository.getOrUpdateMarketData(limit)
}

/**
 * 증시 자금 데이터 초기화 UseCase
 */
class InitializeMarketDepositUseCase @Inject constructor(
    private val repository: MarketDepositRepository
) {
    suspend operator fun invoke(
        numPages: Int = 10,
        onProgress: ((String, Int) -> Unit)? = null
    ): Result<Int> = repository.initializeDeposits(numPages, onProgress)
}

/**
 * 증시 자금 데이터 업데이트 UseCase
 */
class UpdateMarketDepositUseCase @Inject constructor(
    private val repository: MarketDepositRepository
) {
    suspend operator fun invoke(numPages: Int = 10): Result<Int> =
        repository.updateDeposits(numPages)
}

/**
 * 증시 자금 데이터 상태 확인 UseCase
 */
class CheckMarketDepositDataStatusUseCase @Inject constructor(
    private val repository: MarketDepositRepository
) {
    suspend operator fun invoke(): MarketDepositDataStatus {
        val count = repository.getDepositCount()
        return MarketDepositDataStatus(hasData = count > 0)
    }
}

/**
 * 증시 자금 데이터 상태
 */
data class MarketDepositDataStatus(
    val hasData: Boolean
)
