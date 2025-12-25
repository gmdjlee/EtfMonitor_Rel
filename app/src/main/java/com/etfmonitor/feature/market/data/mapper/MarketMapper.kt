package com.etfmonitor.feature.market.data.mapper

import com.etfmonitor.core.database.entities.FearGreedIndex as FearGreedEntity
import com.etfmonitor.core.database.entities.MarketDeposit as MarketDepositEntity
import com.etfmonitor.core.database.entities.MarketOscillatorData as MarketOscillatorEntity
import com.etfmonitor.core.database.entities.MarketIndex as MarketIndexEntity
import com.etfmonitor.feature.market.domain.model.FearGreedIndex
import com.etfmonitor.feature.market.domain.model.MarketDeposit
import com.etfmonitor.feature.market.domain.model.MarketDepositData
import com.etfmonitor.feature.market.domain.model.MarketOscillator
import com.etfmonitor.feature.market.domain.model.MarketIndex
import com.etfmonitor.oscillator.model.MarketDepositData as LegacyMarketDepositData

/**
 * Market 관련 Entity <-> Domain Model 변환 Mapper
 */
object MarketMapper {

    // ==================== FearGreedIndex ====================

    fun FearGreedEntity.toDomain(): FearGreedIndex = FearGreedIndex(
        id = id,
        market = market,
        date = date,
        indexValue = indexValue,
        fearGreedValue = fearGreedValue,
        oscillator = oscillator,
        rsi = rsi,
        momentum = momentum,
        putCallRatio = putCallRatio,
        volatility = volatility,
        spread = spread,
        lastUpdated = lastUpdated
    )

    fun FearGreedIndex.toEntity(): FearGreedEntity = FearGreedEntity(
        id = id,
        market = market,
        date = date,
        indexValue = indexValue,
        fearGreedValue = fearGreedValue,
        oscillator = oscillator,
        rsi = rsi,
        momentum = momentum,
        putCallRatio = putCallRatio,
        volatility = volatility,
        spread = spread,
        lastUpdated = lastUpdated
    )

    fun List<FearGreedEntity>.toFearGreedDomainList(): List<FearGreedIndex> = map { it.toDomain() }

    // ==================== MarketDeposit ====================

    fun MarketDepositEntity.toDomain(): MarketDeposit = MarketDeposit(
        date = date,
        depositAmount = depositAmount,
        depositChange = depositChange,
        creditAmount = creditAmount,
        creditChange = creditChange,
        lastUpdated = lastUpdated
    )

    fun MarketDeposit.toEntity(): MarketDepositEntity = MarketDepositEntity(
        date = date,
        depositAmount = depositAmount,
        depositChange = depositChange,
        creditAmount = creditAmount,
        creditChange = creditChange,
        lastUpdated = lastUpdated
    )

    fun List<MarketDepositEntity>.toDepositDomainList(): List<MarketDeposit> = map { it.toDomain() }

    /**
     * Legacy MarketDepositData to Domain MarketDepositData
     */
    fun LegacyMarketDepositData.toDomain(): MarketDepositData = MarketDepositData(
        dates = dates,
        depositAmounts = depositAmounts,
        depositChanges = depositChanges,
        creditAmounts = creditAmounts,
        creditChanges = creditChanges
    )

    // ==================== MarketOscillator ====================

    fun MarketOscillatorEntity.toDomain(): MarketOscillator = MarketOscillator(
        id = id,
        market = market,
        date = date,
        indexValue = indexValue,
        oscillator = oscillator,
        lastUpdated = lastUpdated
    )

    fun MarketOscillator.toEntity(): MarketOscillatorEntity = MarketOscillatorEntity(
        id = id,
        market = market,
        date = date,
        indexValue = indexValue,
        oscillator = oscillator,
        lastUpdated = lastUpdated
    )

    fun List<MarketOscillatorEntity>.toOscillatorDomainList(): List<MarketOscillator> = map { it.toDomain() }

    // ==================== MarketIndex ====================

    fun MarketIndexEntity.toDomain(): MarketIndex = MarketIndex(
        id = id,
        market = market,
        date = date,
        closePrice = closePrice,
        openPrice = openPrice,
        highPrice = highPrice,
        lowPrice = lowPrice,
        volume = volume,
        changeRate = changeRate,
        lastUpdated = lastUpdated
    )

    fun MarketIndex.toEntity(): MarketIndexEntity = MarketIndexEntity(
        id = id,
        market = market,
        date = date,
        closePrice = closePrice,
        openPrice = openPrice,
        highPrice = highPrice,
        lowPrice = lowPrice,
        volume = volume,
        changeRate = changeRate,
        lastUpdated = lastUpdated
    )

    fun List<MarketIndexEntity>.toIndexDomainList(): List<MarketIndex> = map { it.toDomain() }
}
