package com.etfmonitor.feature.stock.data.mapper

import com.etfmonitor.core.database.entities.Stock as StockEntity
import com.etfmonitor.core.database.entities.StockAnalysisWithName
import com.etfmonitor.core.database.entities.HoldingTimeSeries as HoldingTimeSeriesEntity
import com.etfmonitor.core.database.entities.StockAmountRanking as StockAmountRankingEntity
import com.etfmonitor.core.database.entities.StockChangeInfo as StockChangeInfoEntity
import com.etfmonitor.core.database.entities.CashDepositTrend as CashDepositTrendEntity
import com.etfmonitor.core.database.StockSearchResult as StockSearchResultDb
import com.etfmonitor.feature.stock.domain.model.Stock
import com.etfmonitor.feature.stock.domain.model.StockAnalysis
import com.etfmonitor.feature.stock.domain.model.HoldingTimeSeries
import com.etfmonitor.feature.stock.domain.model.StockAmountRanking
import com.etfmonitor.feature.stock.domain.model.StockChangeInfo
import com.etfmonitor.feature.stock.domain.model.CashDepositTrend
import com.etfmonitor.feature.stock.domain.repository.StockSearchResult

/**
 * Stock Mapper
 *
 * Entity와 Domain Model 간의 변환을 담당합니다.
 */
object StockMapper {

    // ========== Stock ==========

    fun StockEntity.toDomain(): Stock = Stock(
        ticker = ticker,
        name = name,
        market = market,
        isEtfHolding = isEtfHolding,
        lastUpdated = lastUpdated
    )

    fun Stock.toEntity(): StockEntity = StockEntity(
        ticker = ticker,
        name = name,
        market = market,
        sector = "",
        isEtfHolding = isEtfHolding,
        lastUpdated = lastUpdated
    )

    fun List<StockEntity>.toDomain(): List<Stock> = map { it.toDomain() }

    // ========== StockAnalysis ==========

    fun StockAnalysisWithName.toDomain(): StockAnalysis = StockAnalysis(
        ticker = ticker,
        name = name,
        dates = dates,
        marketCap = marketCap,
        foreign5d = foreign5d,
        institution5d = institution5d
    )

    // ========== HoldingTimeSeries ==========

    fun HoldingTimeSeriesEntity.toDomain(): HoldingTimeSeries = HoldingTimeSeries(
        date = date,
        weight = weight,
        amount = amount
    )

    fun List<HoldingTimeSeriesEntity>.toTimeSeriesDomain(): List<HoldingTimeSeries> =
        map { it.toDomain() }

    // ========== StockAmountRanking ==========

    fun StockAmountRankingEntity.toDomain(): StockAmountRanking = StockAmountRanking(
        stockTicker = stockTicker,
        stockName = stockName,
        totalAmount = totalAmount,
        etfCount = etfCount,
        newEtfCount = newEtfCount,
        increasedEtfCount = increasedEtfCount,
        decreasedEtfCount = decreasedEtfCount,
        removedEtfCount = removedEtfCount
    )

    fun List<StockAmountRankingEntity>.toRankingDomain(): List<StockAmountRanking> =
        map { it.toDomain() }

    // ========== StockChangeInfo ==========

    fun StockChangeInfoEntity.toDomain(): StockChangeInfo = StockChangeInfo(
        stockTicker = stockTicker,
        stockName = stockName,
        etfTicker = etfTicker,
        etfName = etfName,
        weight = currentWeight,
        amount = currentAmount,
        previousWeight = previousWeight
    )

    fun List<StockChangeInfoEntity>.toChangeInfoDomain(): List<StockChangeInfo> =
        map { it.toDomain() }

    // ========== CashDepositTrend ==========

    fun CashDepositTrendEntity.toDomain(): CashDepositTrend = CashDepositTrend(
        date = date,
        totalAmount = totalAmount,
        etfCount = etfCount
    )

    fun List<CashDepositTrendEntity>.toCashDepositDomain(): List<CashDepositTrend> =
        map { it.toDomain() }

    // ========== StockSearchResult ==========

    fun StockSearchResultDb.toDomain(): StockSearchResult = StockSearchResult(
        stockTicker = stockTicker,
        stockName = stockName
    )

    fun List<StockSearchResultDb>.toSearchResultDomain(): List<StockSearchResult> =
        map { it.toDomain() }
}
