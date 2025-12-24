package com.etfmonitor.feature.market.data.mapper

import com.etfmonitor.database.entities.FearGreedIndex as FearGreedEntity
import com.etfmonitor.database.entities.MarketOscillatorData as MarketOscillatorEntity
import com.etfmonitor.database.entities.MarketDeposit as MarketDepositEntity
import com.etfmonitor.database.entities.MarketIndex as MarketIndexEntity
import com.etfmonitor.feature.market.domain.model.FearGreed
import com.etfmonitor.feature.market.domain.model.MarketOscillator
import com.etfmonitor.feature.market.domain.model.MarketDeposit
import com.etfmonitor.feature.market.domain.model.MarketDepositTrend
import com.etfmonitor.feature.market.domain.model.MarketIndex

/**
 * Market 모듈 Entity <-> Domain 매퍼
 */
object MarketMapper {

    // ==================== Fear & Greed ====================

    fun FearGreedEntity.toDomain(): FearGreed = FearGreed(
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

    fun FearGreed.toEntity(): FearGreedEntity = FearGreedEntity(
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

    fun List<FearGreedEntity>.toDomainFearGreed(): List<FearGreed> = map { it.toDomain() }

    // ==================== Market Oscillator ====================

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

    fun List<MarketOscillatorEntity>.toDomainOscillator(): List<MarketOscillator> = map { it.toDomain() }

    // ==================== Market Deposit ====================

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

    fun List<MarketDepositEntity>.toDomainDeposit(): List<MarketDeposit> = map { it.toDomain() }

    /**
     * MarketDeposit 리스트를 MarketDepositTrend로 변환
     */
    fun List<MarketDeposit>.toTrend(): MarketDepositTrend {
        if (isEmpty()) return MarketDepositTrend.EMPTY

        // 날짜순 정렬 (오래된 것부터)
        val sorted = sortedBy { it.date }

        return MarketDepositTrend(
            dates = sorted.map { it.date },
            depositAmounts = sorted.map { it.depositAmount },
            depositChanges = sorted.map { it.depositChange },
            creditAmounts = sorted.map { it.creditAmount },
            creditChanges = sorted.map { it.creditChange }
        )
    }

    // ==================== Market Index ====================

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

    fun List<MarketIndexEntity>.toDomainIndex(): List<MarketIndex> = map { it.toDomain() }
}
