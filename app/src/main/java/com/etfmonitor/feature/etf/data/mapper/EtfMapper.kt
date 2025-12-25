package com.etfmonitor.feature.etf.data.mapper

import com.etfmonitor.core.database.entities.Etf as EtfEntity
import com.etfmonitor.core.database.entities.Holding as HoldingEntity
import com.etfmonitor.core.database.entities.HoldingWithComparison as HoldingWithComparisonEntity
import com.etfmonitor.core.database.entities.HoldingStatus as HoldingStatusEntity
import com.etfmonitor.feature.etf.domain.model.Etf
import com.etfmonitor.feature.etf.domain.model.HoldingWithComparison
import com.etfmonitor.feature.etf.domain.model.HoldingStatus

/**
 * ETF Mapper
 *
 * Database Entity ↔ Domain Model 변환을 담당합니다.
 *
 * ## Holding 특수 처리
 * Entity의 압축 저장(weightBps, amountMillion)을 Domain Model의 Float 값으로 변환합니다.
 */
object EtfMapper {

    // ========== Etf ==========

    /**
     * Entity → Domain
     */
    fun EtfEntity.toDomain(): Etf = Etf(
        ticker = ticker,
        name = name
    )

    /**
     * Entity List → Domain List
     */
    fun List<EtfEntity>.toDomain(): List<Etf> = map { it.toDomain() }

    // ========== HoldingStatus ==========

    /**
     * Entity HoldingStatus → Domain HoldingStatus
     */
    fun HoldingStatusEntity.toDomain(): HoldingStatus = when (this) {
        HoldingStatusEntity.NEW -> HoldingStatus.NEW
        HoldingStatusEntity.INCREASE -> HoldingStatus.INCREASE
        HoldingStatusEntity.DECREASE -> HoldingStatus.DECREASE
        HoldingStatusEntity.MAINTAIN -> HoldingStatus.MAINTAIN
        HoldingStatusEntity.REMOVED -> HoldingStatus.REMOVED
    }

    /**
     * Domain HoldingStatus → Entity HoldingStatus
     */
    fun HoldingStatus.toEntity(): HoldingStatusEntity = when (this) {
        HoldingStatus.NEW -> HoldingStatusEntity.NEW
        HoldingStatus.INCREASE -> HoldingStatusEntity.INCREASE
        HoldingStatus.DECREASE -> HoldingStatusEntity.DECREASE
        HoldingStatus.MAINTAIN -> HoldingStatusEntity.MAINTAIN
        HoldingStatus.REMOVED -> HoldingStatusEntity.REMOVED
    }

    // ========== HoldingWithComparison ==========

    /**
     * Entity HoldingWithComparison → Domain HoldingWithComparison
     *
     * Entity의 Float 속성은 이미 변환된 값이므로 직접 사용합니다.
     */
    fun HoldingWithComparisonEntity.toDomain(): HoldingWithComparison = HoldingWithComparison(
        stockTicker = stockTicker,
        stockName = stockName,
        previousWeight = previousWeight,
        currentWeight = currentWeight,
        change = change,
        currentAmount = currentAmount,
        status = status.toDomain()
    )

    /**
     * Entity List → Domain List
     */
    fun List<HoldingWithComparisonEntity>.toDomainComparisons(): List<HoldingWithComparison> =
        map { it.toDomain() }
}
