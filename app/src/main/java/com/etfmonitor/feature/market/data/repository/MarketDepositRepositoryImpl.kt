package com.etfmonitor.feature.market.data.repository

import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDomain
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDepositDomainList
import com.etfmonitor.feature.market.domain.model.MarketDeposit
import com.etfmonitor.feature.market.domain.model.MarketDepositData
import com.etfmonitor.feature.market.domain.repository.MarketDepositRepository
import com.etfmonitor.repository.MarketDepositRepository as LegacyMarketDepositRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Market Deposit Repository Implementation
 *
 * 기존 레거시 Repository를 래핑하여 Clean Architecture 패턴 적용
 * - 12시간 캐싱 로직 유지
 * - 스마트 업데이트 로직 보존
 */
@Singleton
class MarketDepositRepositoryImpl @Inject constructor(
    private val legacyRepository: LegacyMarketDepositRepository
) : MarketDepositRepository {

    override fun getAllDeposits(): Flow<List<MarketDeposit>> =
        legacyRepository.getAllDeposits().map { it.toDepositDomainList() }

    override fun getRecentDeposits(limit: Int): Flow<List<MarketDeposit>> =
        legacyRepository.getRecentDeposits(limit).map { it.toDepositDomainList() }

    override suspend fun getDepositByDate(date: String): MarketDeposit? =
        legacyRepository.getDepositByDate(date)?.toDomain()

    override suspend fun getDepositCount(): Int =
        legacyRepository.getDepositCount()

    override suspend fun getLastUpdateTime(): Long? =
        legacyRepository.getLastUpdateTime()

    override suspend fun initializeDeposits(
        numPages: Int,
        onProgress: ((String, Int) -> Unit)?
    ): Result<Int> = legacyRepository.initializeDeposits(numPages, onProgress)

    override suspend fun updateDeposits(numPages: Int): Result<Int> =
        legacyRepository.updateDeposits(numPages)

    override suspend fun getOrUpdateMarketData(limit: Int): MarketDepositData? =
        legacyRepository.getOrUpdateMarketData(limit)?.toDomain()
}
