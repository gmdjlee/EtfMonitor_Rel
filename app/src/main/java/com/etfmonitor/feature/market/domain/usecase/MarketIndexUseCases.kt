package com.etfmonitor.feature.market.domain.usecase

import com.etfmonitor.feature.market.domain.model.MarketIndex
import com.etfmonitor.feature.market.domain.repository.MarketIndexRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 시장 지수 데이터 조회 UseCase
 */
class GetMarketIndexDataUseCase @Inject constructor(
    private val repository: MarketIndexRepository
) {
    operator fun invoke(market: String): Flow<List<MarketIndex>> =
        repository.getAllByMarket(market)
}

/**
 * 최근 시장 지수 데이터 조회 UseCase
 */
class GetRecentMarketIndexUseCase @Inject constructor(
    private val repository: MarketIndexRepository
) {
    operator fun invoke(market: String, limit: Int): Flow<List<MarketIndex>> =
        repository.getRecentByMarket(market, limit)
}

/**
 * 날짜 범위별 시장 지수 데이터 조회 UseCase
 */
class GetMarketIndexByDateRangeUseCase @Inject constructor(
    private val repository: MarketIndexRepository
) {
    operator fun invoke(
        market: String,
        startDate: String,
        endDate: String
    ): Flow<List<MarketIndex>> =
        repository.getByMarketAndDateRange(market, startDate, endDate)
}

/**
 * 특정 날짜 시장 지수 데이터 조회 UseCase
 */
class GetMarketIndexByDateUseCase @Inject constructor(
    private val repository: MarketIndexRepository
) {
    suspend operator fun invoke(market: String, date: String): MarketIndex? =
        repository.getByMarketAndDate(market, date)

    suspend fun getAllMarkets(date: String): List<MarketIndex> =
        repository.getByDate(date)
}

/**
 * 시장 지수 데이터 개수 조회 UseCase
 */
class GetMarketIndexCountUseCase @Inject constructor(
    private val repository: MarketIndexRepository
) {
    suspend operator fun invoke(market: String): Int =
        repository.getCountByMarket(market)
}

/**
 * 시장 지수 최신 날짜 조회 UseCase
 */
class GetMarketIndexLatestDateUseCase @Inject constructor(
    private val repository: MarketIndexRepository
) {
    suspend operator fun invoke(market: String): String? =
        repository.getLatestDate(market)
}

/**
 * 시장 지수 데이터 존재 확인 UseCase
 */
class HasMarketIndexDataUseCase @Inject constructor(
    private val repository: MarketIndexRepository
) {
    suspend operator fun invoke(market: String): Boolean =
        repository.hasData(market)
}

/**
 * 시장 지수 데이터 초기화 UseCase
 */
class InitializeMarketIndexUseCase @Inject constructor(
    private val repository: MarketIndexRepository
) {
    suspend operator fun invoke(days: Int = 30): Result<Int> =
        repository.initializeMarketIndex(days)
}

/**
 * 시장 지수 데이터 업데이트 UseCase
 */
class UpdateMarketIndexUseCase @Inject constructor(
    private val repository: MarketIndexRepository
) {
    suspend operator fun invoke(days: Int = 30): Result<Int> =
        repository.updateMarketIndex(days)
}

/**
 * 시장 지수 데이터 삭제 UseCase
 */
class DeleteMarketIndexUseCase @Inject constructor(
    private val repository: MarketIndexRepository
) {
    suspend operator fun invoke(market: String) = repository.deleteByMarket(market)

    suspend fun deleteAll() = repository.deleteAll()
}

/**
 * 시장 지수 데이터 상태 확인 UseCase
 */
class CheckMarketIndexDataStatusUseCase @Inject constructor(
    private val repository: MarketIndexRepository
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
