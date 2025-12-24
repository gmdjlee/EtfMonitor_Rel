package com.etfmonitor.feature.market.data.repository

import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDomain
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toIndexDomainList
import com.etfmonitor.feature.market.domain.model.MarketIndex
import com.etfmonitor.feature.market.domain.repository.MarketIndexRepository
import com.etfmonitor.repository.MarketIndexRepository as LegacyMarketIndexRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Market Index Repository Implementation
 *
 * 기존 레거시 Repository를 래핑하여 Clean Architecture 패턴 적용
 */
@Singleton
class MarketIndexRepositoryImpl @Inject constructor(
    private val legacyRepository: LegacyMarketIndexRepository
) : MarketIndexRepository {

    override fun getAllByMarket(market: String): Flow<List<MarketIndex>> =
        legacyRepository.getAllByMarket(market).map { it.toIndexDomainList() }

    override fun getRecentByMarket(market: String, limit: Int): Flow<List<MarketIndex>> =
        legacyRepository.getRecentByMarket(market, limit).map { it.toIndexDomainList() }

    override fun getByMarketAndDateRange(
        market: String,
        startDate: String,
        endDate: String
    ): Flow<List<MarketIndex>> =
        legacyRepository.getByMarketAndDateRange(market, startDate, endDate)
            .map { it.toIndexDomainList() }

    override suspend fun getByMarketAndDate(market: String, date: String): MarketIndex? =
        legacyRepository.getByMarketAndDate(market, date)?.toDomain()

    override suspend fun getByDate(date: String): List<MarketIndex> =
        legacyRepository.getByDate(date).toIndexDomainList()

    override suspend fun getCountByMarket(market: String): Int =
        legacyRepository.getCountByMarket(market)

    override suspend fun getLatestDate(market: String): String? =
        legacyRepository.getLatestDate(market)

    override suspend fun getLastUpdateTime(market: String): Long? =
        legacyRepository.getLastUpdateTime(market)

    override suspend fun hasData(market: String): Boolean =
        legacyRepository.hasData(market)

    override suspend fun initializeMarketIndex(days: Int): Result<Int> =
        legacyRepository.initializeMarketIndex(days)

    override suspend fun updateMarketIndex(days: Int): Result<Int> =
        legacyRepository.updateMarketIndex(days)

    override suspend fun deleteByMarket(market: String) =
        legacyRepository.deleteByMarket(market)

    override suspend fun deleteAll() =
        legacyRepository.deleteAll()
}
