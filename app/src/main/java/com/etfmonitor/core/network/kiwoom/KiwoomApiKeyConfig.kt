package com.etfmonitor.core.network.kiwoom

data class KiwoomApiKeyConfig(
    val appKey: String = "",
    val secretKey: String = "",
    val investmentMode: KiwoomInvestmentMode = KiwoomInvestmentMode.MOCK
) {
    fun isValid(): Boolean = appKey.isNotBlank() && secretKey.isNotBlank()

    override fun toString() = "KiwoomApiKeyConfig(appKey=*****, secretKey=*****, investmentMode=$investmentMode)"

    fun getBaseUrl(): String = when (investmentMode) {
        KiwoomInvestmentMode.MOCK -> "https://mockapi.kiwoom.com"
        KiwoomInvestmentMode.PRODUCTION -> "https://api.kiwoom.com"
    }
}

enum class KiwoomInvestmentMode(val displayName: String, val description: String) {
    MOCK("모의투자", "테스트용 모의투자 환경"),
    PRODUCTION("실전투자", "실제 거래가 이루어지는 환경")
}
