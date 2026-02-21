package com.etfmonitor.feature.ranking.domain.usecase

import com.etfmonitor.feature.ranking.domain.model.*
import com.etfmonitor.feature.ranking.domain.repository.RankingRepository
import javax.inject.Inject

class GetRankingUseCase @Inject constructor(
    private val repository: RankingRepository
) {
    suspend operator fun invoke(
        rankingType: RankingType,
        marketType: MarketType,
        exchangeType: ExchangeType,
        itemCount: ItemCount = ItemCount.TEN,
        orderBookDirection: OrderBookDirection = OrderBookDirection.BUY,
        investorType: InvestorType = InvestorType.FOREIGN,
        tradeDirection: TradeDirection = TradeDirection.NET_BUY,
        valueType: ValueType = ValueType.AMOUNT
    ): Result<RankingResult> {
        val result = when (rankingType) {
            RankingType.ORDER_BOOK_SURGE -> repository.getOrderBookSurge(
                OrderBookSurgeParams(
                    marketType = marketType,
                    exchangeType = exchangeType,
                    tradeType = orderBookDirection.code
                )
            )
            RankingType.VOLUME_SURGE -> repository.getVolumeSurge(
                VolumeSurgeParams(
                    marketType = marketType,
                    exchangeType = exchangeType
                )
            )
            RankingType.DAILY_VOLUME_TOP -> repository.getDailyVolumeTop(
                DailyVolumeTopParams(
                    marketType = marketType,
                    exchangeType = exchangeType
                )
            )
            RankingType.CREDIT_RATIO_TOP -> repository.getCreditRatioTop(
                CreditRatioTopParams(
                    marketType = marketType,
                    exchangeType = exchangeType
                )
            )
            RankingType.FOREIGN_INSTITUTION_TOP -> repository.getForeignInstitutionTop(
                ForeignInstitutionTopParams(
                    marketType = marketType,
                    exchangeType = exchangeType,
                    amountQtyType = valueType.code,
                    investorType = investorType,
                    tradeDirection = tradeDirection
                )
            )
        }

        return result.map { rankingResult ->
            rankingResult.copy(
                items = rankingResult.items.take(itemCount.value)
            )
        }
    }
}
