package com.etfmonitor.feature.market.domain.usecase

import com.etfmonitor.feature.market.domain.model.MarketIndex
import com.etfmonitor.feature.market.domain.repository.MarketIndexRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 시장 지수 최근 데이터 조회 UseCase
 */
class GetRecentMarketIndexUseCase @Inject constructor(
    private val repository: MarketIndexRepository
) {
    operator fun invoke(market: String, limit: Int): Flow<List<MarketIndex>> =
        repository.getRecentByMarket(market, limit)
}

/**
 * 시장 지수 특정 날짜 데이터 조회 UseCase
 */
class GetMarketIndexByDateUseCase @Inject constructor(
    private val repository: MarketIndexRepository
) {
    suspend operator fun invoke(market: String, date: String): MarketIndex? =
        repository.getByMarketAndDate(market, date)
}

/**
 * 시장 지수 기간별 데이터 조회 UseCase
 */
class GetMarketIndexByRangeUseCase @Inject constructor(
    private val repository: MarketIndexRepository
) {
    suspend operator fun invoke(
        market: String,
        startDate: String,
        endDate: String
    ): List<MarketIndex> = repository.getByMarketAndDateRangeSuspend(market, startDate, endDate)
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
 * 시장 지수 데이터 상태 확인 UseCase
 */
class CheckMarketIndexDataStatusUseCase @Inject constructor(
    private val repository: MarketIndexRepository
) {
    suspend operator fun invoke(market: String): MarketIndexDataStatus {
        val count = repository.getCountByMarket(market)
        val latestDate = repository.getLatestDate(market)
        return MarketIndexDataStatus(
            hasData = count > 0,
            latestDate = latestDate
        )
    }
}

/**
 * 시장 지수 데이터 상태
 */
data class MarketIndexDataStatus(
    val hasData: Boolean,
    val latestDate: String?
)
