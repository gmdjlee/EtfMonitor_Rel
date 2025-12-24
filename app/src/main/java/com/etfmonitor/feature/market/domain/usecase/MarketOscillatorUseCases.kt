package com.etfmonitor.feature.market.domain.usecase

import com.etfmonitor.feature.market.domain.model.MarketOscillator
import com.etfmonitor.feature.market.domain.repository.MarketOscillatorRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 시장 과매수/과매도 최근 데이터 조회 UseCase
 */
class GetRecentMarketOscillatorUseCase @Inject constructor(
    private val repository: MarketOscillatorRepository
) {
    operator fun invoke(market: String, limit: Int = 15): Flow<List<MarketOscillator>> =
        repository.getRecentData(market, limit)
}

/**
 * 시장 과매수/과매도 최신 데이터 조회 UseCase
 */
class GetLatestMarketOscillatorUseCase @Inject constructor(
    private val repository: MarketOscillatorRepository
) {
    suspend operator fun invoke(market: String): MarketOscillator? =
        repository.getLatestData(market)
}

/**
 * 시장 과매수/과매도 데이터 초기화 UseCase
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
 * 시장 과매수/과매도 데이터 업데이트 UseCase
 */
class UpdateMarketOscillatorUseCase @Inject constructor(
    private val repository: MarketOscillatorRepository
) {
    suspend operator fun invoke(market: String): Result<Int> =
        repository.updateMarketData(market)
}

/**
 * 시장 과매수/과매도 데이터 상태 확인 UseCase
 */
class CheckMarketOscillatorDataStatusUseCase @Inject constructor(
    private val repository: MarketOscillatorRepository
) {
    suspend operator fun invoke(market: String): MarketOscillatorDataStatus {
        val count = repository.getDataCount(market)
        val latestData = repository.getLatestData(market)
        return MarketOscillatorDataStatus(
            hasData = count > 0,
            latestDate = latestData?.date
        )
    }
}

/**
 * 시장 과매수/과매도 데이터 상태
 */
data class MarketOscillatorDataStatus(
    val hasData: Boolean,
    val latestDate: String?
)
