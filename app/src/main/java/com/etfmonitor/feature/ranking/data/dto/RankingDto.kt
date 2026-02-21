package com.etfmonitor.feature.ranking.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RankingItemDto(
    @SerialName("stk_cd") val stkCd: String? = null,
    @SerialName("stk_nm") val stkNm: String? = null,
    @SerialName("cur_prc") val curPrc: String? = null,
    @SerialName("pred_pre_sig") val predPreSig: String? = null,
    @SerialName("pred_pre") val predPre: String? = null,
    @SerialName("flu_rt") val fluRt: String? = null,
    @SerialName("trde_qty") val trdeQty: String? = null,
    @SerialName("now_trde_qty") val nowTrdeQty: String? = null,
    @SerialName("prev_trde_qty") val prevTrdeQty: String? = null,
    @SerialName("sdnin_qty") val sdninQty: String? = null,
    @SerialName("sdnin_rt") val sdninRt: String? = null,
    @SerialName("pred_rt") val predRt: String? = null,
    @SerialName("tot_buy_qty") val totBuyQty: String? = null,
    @SerialName("tot_sel_req") val totSelReq: String? = null,
    @SerialName("tot_buy_req") val totBuyReq: String? = null,
    @SerialName("now") val now: String? = null,
    @SerialName("int") val baseRate: String? = null,
    @SerialName("crd_rt") val crdRt: String? = null,
    @SerialName("sel_req") val selReq: String? = null,
    @SerialName("buy_req") val buyReq: String? = null,
    @SerialName("rank") val rank: String? = null,
    @SerialName("now_rank") val nowRank: String? = null
)

@Serializable
data class OrderBookSurgeResponse(
    @SerialName("return_code") val returnCode: Int = 0,
    @SerialName("return_msg") val returnMsg: String? = null,
    @SerialName("bid_req_sdnin") val items: List<RankingItemDto>? = null
)

@Serializable
data class VolumeSurgeResponse(
    @SerialName("return_code") val returnCode: Int = 0,
    @SerialName("return_msg") val returnMsg: String? = null,
    @SerialName("trde_qty_sdnin") val items: List<RankingItemDto>? = null
)

@Serializable
data class DailyVolumeTopResponse(
    @SerialName("return_code") val returnCode: Int = 0,
    @SerialName("return_msg") val returnMsg: String? = null,
    @SerialName("tdy_trde_qty_top") val items: List<RankingItemDto>? = null
)

@Serializable
data class CreditRatioTopResponse(
    @SerialName("return_code") val returnCode: Int = 0,
    @SerialName("return_msg") val returnMsg: String? = null,
    @SerialName("crd_rt_top") val items: List<RankingItemDto>? = null
)

@Serializable
data class ForeignInstitutionTopResponse(
    @SerialName("return_code") val returnCode: Int = 0,
    @SerialName("return_msg") val returnMsg: String? = null,
    @SerialName("frgnr_orgn_trde_upper") val items: List<ForeignInstitutionItemDto>? = null
)

@Serializable
data class ForeignInstitutionItemDto(
    @SerialName("for_netslmt_stk_cd") val forNetslmtStkCd: String? = null,
    @SerialName("for_netslmt_stk_nm") val forNetslmtStkNm: String? = null,
    @SerialName("for_netslmt_amt") val forNetslmtAmt: String? = null,
    @SerialName("for_netslmt_qty") val forNetslmtQty: String? = null,
    @SerialName("for_netprps_stk_cd") val forNetprpsStkCd: String? = null,
    @SerialName("for_netprps_stk_nm") val forNetprpsStkNm: String? = null,
    @SerialName("for_netprps_amt") val forNetprpsAmt: String? = null,
    @SerialName("for_netprps_qty") val forNetprpsQty: String? = null,
    @SerialName("orgn_netslmt_stk_cd") val orgnNetslmtStkCd: String? = null,
    @SerialName("orgn_netslmt_stk_nm") val orgnNetslmtStkNm: String? = null,
    @SerialName("orgn_netslmt_amt") val orgnNetslmtAmt: String? = null,
    @SerialName("orgn_netslmt_qty") val orgnNetslmtQty: String? = null,
    @SerialName("orgn_netprps_stk_cd") val orgnNetprpsStkCd: String? = null,
    @SerialName("orgn_netprps_stk_nm") val orgnNetprpsStkNm: String? = null,
    @SerialName("orgn_netprps_amt") val orgnNetprpsAmt: String? = null,
    @SerialName("orgn_netprps_qty") val orgnNetprpsQty: String? = null
)
