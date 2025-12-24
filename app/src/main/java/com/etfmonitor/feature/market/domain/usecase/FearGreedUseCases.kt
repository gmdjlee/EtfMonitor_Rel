package com.etfmonitor.feature.market.domain.usecase

import com.etfmonitor.feature.market.domain.model.FearGreed
import com.etfmonitor.feature.market.domain.repository.FearGreedRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Fear & Greed 최근 데이터 조회 UseCase
 */
class GetRecentFearGreedUseCase @Inject constructor(
    private val repository: FearGreedRepository
) {
    operator fun invoke(market: String, limit: Int = 365): Flow<List<FearGreed>> =
        repository.getRecentByMarket(market, limit)
}

/**
 * Fear & Greed 데이터 초기화 UseCase
 */
class InitializeFearGreedUseCase @Inject constructor(
    private val repository: FearGreedRepository
) {
    suspend operator fun invoke(
        days: Int = 365,
        onProgress: ((String, Int) -> Unit)? = null
    ): Result<Int> = repository.initializeFearGreed(days, onProgress)
}

/**
 * Fear & Greed 데이터 업데이트 UseCase
 */
class UpdateFearGreedUseCase @Inject constructor(
    private val repository: FearGreedRepository
) {
    suspend operator fun invoke(): Result<Int> = repository.updateFearGreed()
}

/**
 * Fear & Greed 데이터 상태 확인 UseCase
 */
class CheckFearGreedDataStatusUseCase @Inject constructor(
    private val repository: FearGreedRepository
) {
    suspend operator fun invoke(market: String): FearGreedDataStatus {
        val count = repository.getCountByMarket(market)
        val latestDate = repository.getLatestDate(market)
        return FearGreedDataStatus(
            hasData = count > 0,
            latestDate = latestDate
        )
    }
}

/**
 * Fear & Greed 데이터 상태
 */
data class FearGreedDataStatus(
    val hasData: Boolean,
    val latestDate: String?
)
