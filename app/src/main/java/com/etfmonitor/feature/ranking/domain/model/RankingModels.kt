package com.etfmonitor.feature.ranking.domain.model

enum class MarketType(val code: String, val displayName: String) {
    ALL("000", "전체"),
    KOSPI("001", "KOSPI"),
    KOSDAQ("101", "KOSDAQ")
}

enum class ExchangeType(val code: String, val displayName: String) {
    KRX("1", "KRX"),
    NXT("2", "NXT"),
    KRX_MOCK("3", "KRX")
}

enum class RankingType(val displayName: String, val apiId: String) {
    ORDER_BOOK_SURGE("호가잔량급증", "ka10021"),
    VOLUME_SURGE("거래량급증", "ka10023"),
    DAILY_VOLUME_TOP("당일거래량상위", "ka10030"),
    CREDIT_RATIO_TOP("신용비율상위", "ka10033"),
    FOREIGN_INSTITUTION_TOP("외국인/기관상위", "ka90009")
}

enum class OrderBookDirection(val code: String, val displayName: String) {
    BUY("1", "매수"),
    SELL("2", "매도")
}

enum class ItemCount(val value: Int) {
    FIVE(5),
    TEN(10),
    TWENTY(20),
    THIRTY(30)
}

enum class InvestorType(val displayName: String) {
    FOREIGN("외국인"),
    INSTITUTION("기관"),
    ALL("전체")
}

enum class TradeDirection(val displayName: String) {
    NET_BUY("순매수"),
    NET_SELL("순매도")
}

enum class ValueType(val code: String, val displayName: String) {
    AMOUNT("1", "금액"),
    QUANTITY("2", "수량")
}

data class RankingItem(
    val rank: Int,
    val ticker: String,
    val name: String,
    val currentPrice: Long,
    val priceChange: Long,
    val priceChangeSign: String,
    val changeRate: Double,
    val volume: Long? = null,
    val surgeRate: Double? = null,
    val surgeQuantity: Long? = null,
    val creditRatio: Double? = null,
    val foreignNetBuy: Long? = null,
    val institutionNetBuy: Long? = null,
    val foreignNetSell: Long? = null,
    val institutionNetSell: Long? = null,
    val totalBuyQuantity: Long? = null,
    val totalSellQuantity: Long? = null,
    val netValue: Long? = null
)

data class RankingResult(
    val rankingType: RankingType,
    val marketType: MarketType,
    val exchangeType: ExchangeType,
    val items: List<RankingItem>,
    val fetchedAt: java.time.LocalDateTime = java.time.LocalDateTime.now(),
    val orderBookDirection: OrderBookDirection? = null,
    val investorType: InvestorType? = null,
    val tradeDirection: TradeDirection? = null,
    val valueType: ValueType? = null
)
