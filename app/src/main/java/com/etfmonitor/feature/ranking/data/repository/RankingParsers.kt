package com.etfmonitor.feature.ranking.data.repository

import com.etfmonitor.feature.ranking.data.dto.ForeignInstitutionTopResponse
import com.etfmonitor.feature.ranking.data.dto.RankingItemDto
import com.etfmonitor.feature.ranking.domain.model.*
import java.time.LocalDateTime

internal object RankingParsers {

    fun parseOrderBookSurgeItems(
        dtoItems: List<RankingItemDto>,
        params: OrderBookSurgeParams,
        orderBookDirection: OrderBookDirection
    ): RankingResult {
        val items = dtoItems.mapIndexed { index, dto ->
            dto.toBaseRankingItem(index).copy(
                changeRate = 0.0,
                surgeQuantity = RankingParseUtils.parseLong(dto.sdninQty),
                surgeRate = RankingParseUtils.parseDouble(dto.sdninRt),
                totalBuyQuantity = RankingParseUtils.parseLong(dto.totBuyQty)
            )
        }
        return RankingResult(
            rankingType = RankingType.ORDER_BOOK_SURGE,
            marketType = params.marketType,
            exchangeType = params.exchangeType,
            items = items,
            fetchedAt = LocalDateTime.now(),
            orderBookDirection = orderBookDirection
        )
    }

    fun parseVolumeSurgeItems(
        dtoItems: List<RankingItemDto>,
        params: VolumeSurgeParams
    ): RankingResult {
        val items = dtoItems.mapIndexed { index, dto ->
            dto.toBaseRankingItem(index).copy(
                changeRate = RankingParseUtils.parseDouble(dto.fluRt),
                volume = RankingParseUtils.parseLong(dto.nowTrdeQty),
                surgeQuantity = RankingParseUtils.parseLong(dto.sdninQty),
                surgeRate = RankingParseUtils.parseDouble(dto.sdninRt)
            )
        }
        return RankingResult(
            rankingType = RankingType.VOLUME_SURGE,
            marketType = params.marketType,
            exchangeType = params.exchangeType,
            items = items,
            fetchedAt = LocalDateTime.now()
        )
    }

    fun parseDailyVolumeTopItems(
        dtoItems: List<RankingItemDto>,
        params: DailyVolumeTopParams
    ): RankingResult {
        val items = dtoItems.mapIndexed { index, dto ->
            dto.toBaseRankingItem(index).copy(
                changeRate = RankingParseUtils.parseDouble(dto.fluRt),
                volume = RankingParseUtils.parseLong(dto.trdeQty)
            )
        }
        return RankingResult(
            rankingType = RankingType.DAILY_VOLUME_TOP,
            marketType = params.marketType,
            exchangeType = params.exchangeType,
            items = items,
            fetchedAt = LocalDateTime.now()
        )
    }

    fun parseCreditRatioTopItems(
        dtoItems: List<RankingItemDto>,
        params: CreditRatioTopParams
    ): RankingResult {
        val items = dtoItems.mapIndexed { index, dto ->
            dto.toBaseRankingItem(index).copy(
                changeRate = RankingParseUtils.parseDouble(dto.fluRt),
                creditRatio = RankingParseUtils.parseDouble(dto.crdRt),
                volume = RankingParseUtils.parseLong(dto.nowTrdeQty)
            )
        }
        return RankingResult(
            rankingType = RankingType.CREDIT_RATIO_TOP,
            marketType = params.marketType,
            exchangeType = params.exchangeType,
            items = items,
            fetchedAt = LocalDateTime.now()
        )
    }

    private fun RankingItemDto.toBaseRankingItem(index: Int) = RankingItem(
        rank = index + 1,
        ticker = RankingParseUtils.cleanTicker(stkCd),
        name = stkNm ?: "",
        currentPrice = RankingParseUtils.parseLong(curPrc),
        priceChange = RankingParseUtils.parseLong(predPre),
        priceChangeSign = RankingParseUtils.parseSign(predPreSig),
        changeRate = 0.0
    )

    fun parseForeignInstitutionTopResponse(
        response: ForeignInstitutionTopResponse,
        params: ForeignInstitutionTopParams
    ): RankingResult {
        val items = mutableListOf<RankingItem>()
        val dtoItems = response.items ?: emptyList()
        val isAmount = params.amountQtyType == "1"

        for ((index, dto) in dtoItems.withIndex()) {
            val item = when (params.investorType) {
                InvestorType.FOREIGN -> ForeignInstitutionExtractor.extractForeignData(
                    dto, index, isAmount, params.tradeDirection
                )
                InvestorType.INSTITUTION -> ForeignInstitutionExtractor.extractInstitutionData(
                    dto, index, isAmount, params.tradeDirection
                )
                InvestorType.ALL -> ForeignInstitutionExtractor.extractAllInvestorsData(
                    dto, index, isAmount, params.tradeDirection
                )
            }
            item?.let { items.add(it) }
        }

        return RankingResult(
            rankingType = RankingType.FOREIGN_INSTITUTION_TOP,
            marketType = params.marketType,
            exchangeType = params.exchangeType,
            items = items.filter { it.ticker.isNotEmpty() },
            fetchedAt = LocalDateTime.now(),
            investorType = params.investorType,
            tradeDirection = params.tradeDirection,
            valueType = if (isAmount) ValueType.AMOUNT else ValueType.QUANTITY
        )
    }
}
