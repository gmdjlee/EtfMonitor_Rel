package com.etfmonitor.ai

/**
 * AI API 클라이언트 공통 인터페이스
 * Claude, Gemini 등 다양한 AI API를 지원
 */
interface AIApiClient {
    /**
     * API 제공자 타입
     */
    val provider: AIProvider

    /**
     * 시장 분석 수행
     * @param prompt 분석 프롬프트
     * @param temperature 창의성 조절 (0.0 ~ 1.0)
     * @return 시장 신호 분석 결과
     */
    suspend fun analyzeMarket(
        prompt: String,
        temperature: Double = 0.7
    ): Result<MarketSignal>

    /**
     * API 사용 가능 여부 확인
     * @return API 키가 설정되어 있으면 true
     */
    suspend fun isApiAvailable(): Boolean

    /**
     * API 키 유효성 테스트
     * @return 테스트 성공 시 true
     */
    suspend fun testApiKey(): Result<Boolean>
}
