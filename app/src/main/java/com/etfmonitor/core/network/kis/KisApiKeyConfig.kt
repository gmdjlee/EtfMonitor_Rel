package com.etfmonitor.core.network.kis

data class KisApiKeyConfig(
    val appKey: String = "",
    val appSecret: String = "",
    val investmentMode: InvestmentMode = InvestmentMode.MOCK
) {
    fun isValid(): Boolean = appKey.isNotBlank() && appSecret.isNotBlank()

    fun getBaseUrl(): String = when (investmentMode) {
        InvestmentMode.MOCK -> "https://openapivts.koreainvestment.com:29443"
        InvestmentMode.PRODUCTION -> "https://openapi.koreainvestment.com:9443"
    }
}

enum class InvestmentMode(val displayName: String, val description: String) {
    MOCK("모의투자", "테스트용 모의투자 환경"),
    PRODUCTION("실전투자", "실제 거래가 이루어지는 환경")
}
