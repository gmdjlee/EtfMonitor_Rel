package com.etfmonitor.ai

/**
 * Claude API 키 제공 인터페이스
 *
 * API 키의 저장소를 추상화하여 다양한 구현 가능
 * (SharedPreferences, EncryptedSharedPreferences, Keystore 등)
 */
interface ApiKeyProvider {
    /**
     * API 키 조회
     * @return API 키 또는 null (미설정 시)
     */
    fun getApiKey(): String?

    /**
     * API 키 저장
     * @param apiKey 저장할 API 키
     */
    fun setApiKey(apiKey: String)

    /**
     * API 키 삭제
     */
    fun removeApiKey()

    /**
     * API 키 설정 여부 확인
     * @return true if API key is configured
     */
    fun hasApiKey(): Boolean
}
