package com.etfmonitor.core.common.model

enum class SharesType(val displayName: String, val description: String) {
    FLOATING("유통주식수", "실제 거래 가능한 주식수 (Kiwoom API)"),
    OUTSTANDING("상장주식수", "시장에 상장된 전체 주식수 (KRX)")
}
