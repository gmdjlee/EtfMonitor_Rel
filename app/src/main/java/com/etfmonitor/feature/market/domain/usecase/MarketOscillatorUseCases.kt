package com.etfmonitor.feature.market.domain.usecase

import com.etfmonitor.feature.market.domain.model.MarketOscillator
import com.etfmonitor.feature.market.domain.repository.MarketOscillatorRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 시장 오실레이터 데이터 조회 UseCase
 */
class GetMarketOscillatorDataUseCase @Inject constructor(
    private val repository: MarketOscillatorRepository
) {
    operator fun invoke(market: String): Flow<List<MarketOscillator>> =
        repository.getMarketData(market)
}

/**
 * 최근 시장 오실레이터 데이터 조회 UseCase
 */
class GetRecentMarketOscillatorUseCase @Inject constructor(
    private val repository: MarketOscillatorRepository
) {
    operator fun invoke(market: String, limit: Int = 15): Flow<List<MarketOscillator>> =
        repository.getRecentData(market, limit)
}

/**
 * 날짜 범위별 시장 오실레이터 데이터 조회 UseCase
 */
class GetMarketOscillatorByDateRangeUseCase @Inject constructor(
    private val repository: MarketOscillatorRepository
) {
    operator fun invoke(
        market: String,
        startDate: String,
        endDate: String
    ): Flow<List<MarketOscillator>> =
        repository.getDataByDateRange(market, startDate, endDate)
}

/**
 * 최신 시장 오실레이터 데이터 조회 UseCase
 */
class GetLatestMarketOscillatorUseCase @Inject constructor(
    private val repository: MarketOscillatorRepository
) {
    suspend operator fun invoke(market: String): MarketOscillator? =
        repository.getLatestData(market)
}

/**
 * 시장 오실레이터 데이터 개수 조회 UseCase
 */
class GetMarketOscillatorCountUseCase @Inject constructor(
    private val repository: MarketOscillatorRepository
) {
    suspend operator fun invoke(market: String): Int =
        repository.getDataCount(market)
}

/**
 * 시장 오실레이터 데이터 초기화 UseCase
 */
class InitializeMarketOscillatorUseCase @Inject constructor(
    private val repository: MarketOscillatorRepository
) {
    suspend operator fun invoke(
        market: String,
        days: Int = 365,
        onProgress: ((String, Int) -> Unit)? = null
    ): Result<Int> = repository.initializeMarketData(market, days, onProgress)
}

/**
 * 시장 오실레이터 데이터 업데이트 UseCase
 */
class UpdateMarketOscillatorUseCase @Inject constructor(
    private val repository: MarketOscillatorRepository
) {
    suspend operator fun invoke(market: String): Result<Int> =
        repository.updateMarketData(market)
}

/**
 * 시장 오실레이터 데이터 삭제 UseCase
 */
class DeleteMarketOscillatorUseCase @Inject constructor(
    private val repository: MarketOscillatorRepository
) {
    suspend operator fun invoke(market: String) = repository.deleteMarketData(market)

    suspend fun deleteAll() = repository.deleteAll()
}

/**
 * 시장 오실레이터 데이터 상태 확인 UseCase
 */
class CheckMarketOscillatorDataStatusUseCase @Inject constructor(
    private val repository: MarketOscillatorRepository
) {
    suspend operator fun invoke(market: String): DataStatus {
        val count = repository.getDataCount(market)
        val latest = repository.getLatestData(market)

        return DataStatus(
            hasData = count > 0,
            recordCount = count,
            latestDate = latest?.date,
            lastUpdateTime = latest?.lastUpdated
        )
    }
}
