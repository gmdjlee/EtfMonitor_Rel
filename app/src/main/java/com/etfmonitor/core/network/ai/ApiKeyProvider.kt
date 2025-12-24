package com.etfmonitor.core.network.ai

/**
 * AI API 키 제공 인터페이스
 *
 * API 키의 저장소를 추상화하여 다양한 구현 가능
 * (SharedPreferences, EncryptedSharedPreferences, Keystore 등)
 *
 * 여러 AI 제공자(Claude, Gemini 등)를 지원
 */
interface ApiKeyProvider {
    /**
     * 특정 AI 제공자의 API 키 조회
     * @param provider AI 제공자 (CLAUDE, GEMINI 등)
     * @return API 키 또는 null (미설정 시)
     */
    fun getApiKey(provider: AIProvider = AIProvider.CLAUDE): String?

    /**
     * 특정 AI 제공자의 API 키 저장
     * @param provider AI 제공자
     * @param apiKey 저장할 API 키
     */
    fun setApiKey(provider: AIProvider, apiKey: String)

    /**
     * 특정 AI 제공자의 API 키 삭제
     * @param provider AI 제공자
     */
    fun removeApiKey(provider: AIProvider)

    /**
     * 특정 AI 제공자의 API 키 설정 여부 확인
     * @param provider AI 제공자
     * @return true if API key is configured
     */
    fun hasApiKey(provider: AIProvider): Boolean

    /**
     * 현재 선택된 AI 제공자 조회
     * @return 선택된 AI 제공자
     */
    fun getSelectedProvider(): AIProvider

    /**
     * 선택된 AI 제공자 설정
     * @param provider 선택할 AI 제공자
     */
    fun setSelectedProvider(provider: AIProvider)

    /**
     * 특정 AI 제공자의 선택된 모델 ID 조회
     * @param provider AI 제공자
     * @return 선택된 모델 ID 또는 null (미설정 시)
     */
    fun getSelectedModel(provider: AIProvider): String?

    /**
     * 특정 AI 제공자의 선택된 모델 ID 설정
     * @param provider AI 제공자
     * @param modelId 모델 ID
     */
    fun setSelectedModel(provider: AIProvider, modelId: String)

    /**
     * 특정 AI 제공자의 선택된 모델 ID 삭제
     * @param provider AI 제공자
     */
    fun removeSelectedModel(provider: AIProvider)

    // 하위 호환성을 위한 기본 메서드 (deprecated)
    @Deprecated("Use getApiKey(AIProvider) instead", ReplaceWith("getApiKey(AIProvider.CLAUDE)"))
    fun getApiKey(): String? = getApiKey(AIProvider.CLAUDE)

    @Deprecated("Use setApiKey(AIProvider, String) instead", ReplaceWith("setApiKey(AIProvider.CLAUDE, apiKey)"))
    fun setApiKey(apiKey: String) = setApiKey(AIProvider.CLAUDE, apiKey)

    @Deprecated("Use removeApiKey(AIProvider) instead", ReplaceWith("removeApiKey(AIProvider.CLAUDE)"))
    fun removeApiKey() = removeApiKey(AIProvider.CLAUDE)

    @Deprecated("Use hasApiKey(AIProvider) instead", ReplaceWith("hasApiKey(AIProvider.CLAUDE)"))
    fun hasApiKey(): Boolean = hasApiKey(AIProvider.CLAUDE)
}
