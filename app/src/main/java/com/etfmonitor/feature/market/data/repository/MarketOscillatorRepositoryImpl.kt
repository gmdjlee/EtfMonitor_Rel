package com.etfmonitor.feature.market.data.repository

import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDomain
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toOscillatorDomainList
import com.etfmonitor.feature.market.domain.model.MarketOscillator
import com.etfmonitor.feature.market.domain.repository.MarketOscillatorRepository
import com.etfmonitor.repository.MarketOscillatorRepository as LegacyMarketOscillatorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Market Oscillator Repository Implementation
 *
 * 기존 레거시 Repository를 래핑하여 Clean Architecture 패턴 적용
 * - 180초 타임아웃 설정 보존 (OscillatorPyClient에서 관리)
 * - 365일 기본 데이터 유지 기간 보존
 */
@Singleton
class MarketOscillatorRepositoryImpl @Inject constructor(
    private val legacyRepository: LegacyMarketOscillatorRepository
) : MarketOscillatorRepository {

    override fun getMarketData(market: String): Flow<List<MarketOscillator>> =
        legacyRepository.getMarketData(market).map { it.toOscillatorDomainList() }

    override fun getRecentData(market: String, limit: Int): Flow<List<MarketOscillator>> =
        legacyRepository.getRecentData(market, limit).map { it.toOscillatorDomainList() }

    override fun getDataByDateRange(
        market: String,
        startDate: String,
        endDate: String
    ): Flow<List<MarketOscillator>> =
        legacyRepository.getDataByDateRange(market, startDate, endDate)
            .map { it.toOscillatorDomainList() }

    override suspend fun getLatestData(market: String): MarketOscillator? =
        legacyRepository.getLatestData(market)?.toDomain()

    override suspend fun getDataCount(market: String): Int =
        legacyRepository.getDataCount(market)

    override suspend fun initializeMarketData(
        market: String,
        days: Int,
        onProgress: ((String, Int) -> Unit)?
    ): Result<Int> = legacyRepository.initializeMarketData(market, days, onProgress)

    override suspend fun updateMarketData(market: String): Result<Int> =
        legacyRepository.updateMarketData(market)

    override suspend fun deleteMarketData(market: String) =
        legacyRepository.deleteMarketData(market)

    override suspend fun deleteAll() =
        legacyRepository.deleteAll()
}
