package com.etfmonitor.feature.market.data.repository

import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDomain
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toFearGreedDomainList
import com.etfmonitor.feature.market.domain.model.FearGreedIndex
import com.etfmonitor.feature.market.domain.repository.FearGreedRepository
import com.etfmonitor.repository.FearGreedRepository as LegacyFearGreedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fear & Greed Repository Implementation
 *
 * 기존 레거시 Repository를 래핑하여 Clean Architecture 패턴 적용
 * - 3x 데이터 요청 로직 유지
 * - Python 분석 로직 보존
 */
@Singleton
class FearGreedRepositoryImpl @Inject constructor(
    private val legacyRepository: LegacyFearGreedRepository
) : FearGreedRepository {

    override fun getAllByMarket(market: String): Flow<List<FearGreedIndex>> =
        legacyRepository.getAllByMarket(market).map { it.toFearGreedDomainList() }

    override fun getRecentByMarket(market: String, limit: Int): Flow<List<FearGreedIndex>> =
        legacyRepository.getRecentByMarket(market, limit).map { it.toFearGreedDomainList() }

    override fun getByMarketAndDateRange(
        market: String,
        startDate: String,
        endDate: String
    ): Flow<List<FearGreedIndex>> =
        legacyRepository.getByMarketAndDateRange(market, startDate, endDate)
            .map { it.toFearGreedDomainList() }

    override suspend fun getByMarketAndDate(market: String, date: String): FearGreedIndex? =
        legacyRepository.getByMarketAndDate(market, date)?.toDomain()

    override suspend fun getCountByMarket(market: String): Int =
        legacyRepository.getCountByMarket(market)

    override suspend fun getLatestDate(market: String): String? =
        legacyRepository.getLatestDate(market)

    override suspend fun getLastUpdateTime(market: String): Long? =
        legacyRepository.getLastUpdateTime(market)

    override suspend fun initializeFearGreed(
        days: Int,
        onProgress: ((String, Int) -> Unit)?
    ): Result<Int> = legacyRepository.initializeFearGreed(days, onProgress)

    override suspend fun updateFearGreed(): Result<Int> =
        legacyRepository.updateFearGreed()
}
