package com.etfmonitor.feature.stock.domain.model

import kotlinx.serialization.Serializable

enum class RealtimeSupplySignal {
    STRONG_BUY, BUY, NEUTRAL, SELL, STRONG_SELL
}

data class RealtimeSupplyData(
    val ticker: String,
    val name: String,
    val currentPrice: Long,
    val netBuyAmount: Long,      // 순매수금액 (백만원)
    val buyAmount: Long,         // 매수금액 (백만원)
    val sellAmount: Long,        // 매도금액 (백만원)
    val netBuyQuantity: Long,    // 순매수수량
    val accumulatedVolume: Long, // 누적거래량
    val fetchedAt: Long          // epoch millis
) {
    val netBuyRatio: Double
        get() {
            val total = buyAmount + sellAmount
            return if (total > 0) netBuyAmount.toDouble() / total else 0.0
        }

    val signal: RealtimeSupplySignal
        get() = when {
            netBuyRatio > 0.3 -> RealtimeSupplySignal.STRONG_BUY
            netBuyRatio > 0.1 -> RealtimeSupplySignal.BUY
            netBuyRatio < -0.3 -> RealtimeSupplySignal.STRONG_SELL
            netBuyRatio < -0.1 -> RealtimeSupplySignal.SELL
            else -> RealtimeSupplySignal.NEUTRAL
        }

    val netBuyAmountBillion: Double get() = netBuyAmount / 100.0
    val buyAmountBillion: Double get() = buyAmount / 100.0
    val sellAmountBillion: Double get() = sellAmount / 100.0
}

data class RealtimeSupplySummary(
    val data: RealtimeSupplyData,
    val signal: RealtimeSupplySignal,
    val signalDescription: String,
    val isTradingHours: Boolean
)

@Serializable
data class CachedRealtimeSupplyData(
    val ticker: String,
    val name: String,
    val currentPrice: Long,
    val netBuyAmount: Long,
    val buyAmount: Long,
    val sellAmount: Long,
    val netBuyQuantity: Long,
    val accumulatedVolume: Long,
    val fetchedAt: Long
)

object TradingHours {
    private const val OPEN_HOUR = 9
    private const val OPEN_MINUTE = 0
    private const val CLOSE_HOUR = 15
    private const val CLOSE_MINUTE = 30

    fun isTradingHours(): Boolean {
        val now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"))
        val dayOfWeek = now.dayOfWeek
        if (dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY) return false
        val time = now.toLocalTime()
        val open = java.time.LocalTime.of(OPEN_HOUR, OPEN_MINUTE)
        val close = java.time.LocalTime.of(CLOSE_HOUR, CLOSE_MINUTE)
        return time in open..close
    }

    fun getTradingHoursString(): String = "09:00 - 15:30"
}
