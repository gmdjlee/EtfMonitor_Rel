package com.etfmonitor.feature.ranking.domain.model

interface RankingParams {
    val marketType: MarketType
    val exchangeType: ExchangeType
    fun toRequestBody(): Map<String, String>
}

data class OrderBookSurgeParams(
    override val marketType: MarketType,
    override val exchangeType: ExchangeType,
    val tradeType: String = "1",
    val sortType: String = "1",
    val timeType: String = "30",
    val volumeType: String = "1",
    val stockCondition: String = "0"
) : RankingParams {
    override fun toRequestBody(): Map<String, String> = mapOf(
        "mrkt_tp" to marketType.code,
        "trde_tp" to tradeType,
        "sort_tp" to sortType,
        "tm_tp" to timeType,
        "trde_qty_tp" to volumeType,
        "stk_cnd" to stockCondition,
        "stex_tp" to exchangeType.code
    )
}

data class VolumeSurgeParams(
    override val marketType: MarketType,
    override val exchangeType: ExchangeType,
    val sortType: String = "1",
    val timeType: String = "2",
    val volumeType: String = "5",
    val time: String = "",
    val stockCondition: String = "0",
    val priceType: String = "0"
) : RankingParams {
    override fun toRequestBody(): Map<String, String> = mapOf(
        "mrkt_tp" to marketType.code,
        "sort_tp" to sortType,
        "tm_tp" to timeType,
        "trde_qty_tp" to volumeType,
        "tm" to time,
        "stk_cnd" to stockCondition,
        "pric_tp" to priceType,
        "stex_tp" to exchangeType.code
    )
}

data class DailyVolumeTopParams(
    override val marketType: MarketType,
    override val exchangeType: ExchangeType,
    val sortType: String = "1",
    val managedStockInclude: String = "0",
    val creditType: String = "0",
    val volumeType: String = "0",
    val priceType: String = "0",
    val amountType: String = "0",
    val marketOpenType: String = "0"
) : RankingParams {
    override fun toRequestBody(): Map<String, String> = mapOf(
        "mrkt_tp" to marketType.code,
        "sort_tp" to sortType,
        "mang_stk_incls" to managedStockInclude,
        "crd_tp" to creditType,
        "trde_qty_tp" to volumeType,
        "pric_tp" to priceType,
        "trde_prica_tp" to amountType,
        "mrkt_open_tp" to marketOpenType,
        "stex_tp" to exchangeType.code
    )
}

data class CreditRatioTopParams(
    override val marketType: MarketType,
    override val exchangeType: ExchangeType,
    val volumeType: String = "0",
    val stockCondition: String = "0",
    val upDownInclude: String = "1",
    val creditCondition: String = "0"
) : RankingParams {
    override fun toRequestBody(): Map<String, String> = mapOf(
        "mrkt_tp" to marketType.code,
        "trde_qty_tp" to volumeType,
        "stk_cnd" to stockCondition,
        "updown_incls" to upDownInclude,
        "crd_cnd" to creditCondition,
        "stex_tp" to exchangeType.code
    )
}

data class ForeignInstitutionTopParams(
    override val marketType: MarketType,
    override val exchangeType: ExchangeType,
    val amountQtyType: String = "1",
    val queryDateType: String = "1",
    val date: String? = null,
    val investorType: InvestorType = InvestorType.FOREIGN,
    val tradeDirection: TradeDirection = TradeDirection.NET_BUY
) : RankingParams {
    override fun toRequestBody(): Map<String, String> = buildMap {
        put("mrkt_tp", marketType.code)
        put("amt_qty_tp", amountQtyType)
        put("qry_dt_tp", queryDateType)
        date?.let { put("date", it) }
        put("stex_tp", exchangeType.code)
    }
}
