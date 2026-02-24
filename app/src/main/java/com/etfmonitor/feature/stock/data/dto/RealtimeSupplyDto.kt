package com.etfmonitor.feature.stock.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RealtimeSupplyResponse(
    @SerialName("return_code") val returnCode: Int = 0,
    @SerialName("return_msg") val returnMsg: String? = null,
    @SerialName("opmr_invsr_trde") val items: List<RealtimeSupplyItemDto>? = null
)

@Serializable
data class RealtimeSupplyItemDto(
    @SerialName("stk_cd") val stkCd: String? = null,
    @SerialName("stk_nm") val stkNm: String? = null,
    @SerialName("cur_prc") val currentPrice: String? = null,
    @SerialName("acc_trde_qty") val accumulatedVolume: String? = null,
    @SerialName("netprps_amt") val netBuyAmount: String? = null,
    @SerialName("buy_amt") val buyAmount: String? = null,
    @SerialName("sell_amt") val sellAmount: String? = null,
    @SerialName("netprps_qty") val netBuyQuantity: String? = null
)
