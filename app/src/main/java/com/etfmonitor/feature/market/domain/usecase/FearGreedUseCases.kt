package com.etfmonitor.feature.market.domain.usecase

import com.etfmonitor.feature.market.domain.model.FearGreedIndex
import com.etfmonitor.feature.market.domain.repository.FearGreedRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Fear & Greed 데이터 조회 UseCase
 */
class GetFearGreedDataUseCase @Inject constructor(
    private val repository: FearGreedRepository
) {
    operator fun invoke(market: String): Flow<List<FearGreedIndex>> =
        repository.getAllByMarket(market)
}

/**
 * 최근 Fear & Greed 데이터 조회 UseCase
 */
class GetRecentFearGreedUseCase @Inject constructor(
    private val repository: FearGreedRepository
) {
    operator fun invoke(market: String, limit: Int = 365): Flow<List<FearGreedIndex>> =
        repository.getRecentByMarket(market, limit)
}

/**
 * 날짜 범위별 Fear & Greed 데이터 조회 UseCase
 */
class GetFearGreedByDateRangeUseCase @Inject constructor(
    private val repository: FearGreedRepository
) {
    operator fun invoke(
        market: String,
        startDate: String,
        endDate: String
    ): Flow<List<FearGreedIndex>> =
        repository.getByMarketAndDateRange(market, startDate, endDate)
}

/**
 * 특정 날짜 Fear & Greed 데이터 조회 UseCase
 */
class GetFearGreedByDateUseCase @Inject constructor(
    private val repository: FearGreedRepository
) {
    suspend operator fun invoke(market: String, date: String): FearGreedIndex? =
        repository.getByMarketAndDate(market, date)
}

/**
 * Fear & Greed 데이터 개수 조회 UseCase
 */
class GetFearGreedCountUseCase @Inject constructor(
    private val repository: FearGreedRepository
) {
    suspend operator fun invoke(market: String): Int =
        repository.getCountByMarket(market)
}

/**
 * Fear & Greed 최신 날짜 조회 UseCase
 */
class GetFearGreedLatestDateUseCase @Inject constructor(
    private val repository: FearGreedRepository
) {
    suspend operator fun invoke(market: String): String? =
        repository.getLatestDate(market)
}

/**
 * Fear & Greed 데이터 초기화 UseCase
 *
 * 주의: 실제로 요청한 일수의 3배를 수집합니다 (MA 데이터 손실 보상)
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
    suspend operator fun invoke(market: String): DataStatus {
        val count = repository.getCountByMarket(market)
        val latestDate = repository.getLatestDate(market)
        val lastUpdate = repository.getLastUpdateTime(market)

        return DataStatus(
            hasData = count > 0,
            recordCount = count,
            latestDate = latestDate,
            lastUpdateTime = lastUpdate
        )
    }
}

/**
 * 데이터 상태 정보
 */
data class DataStatus(
    val hasData: Boolean,
    val recordCount: Int,
    val latestDate: String?,
    val lastUpdateTime: Long?
)
