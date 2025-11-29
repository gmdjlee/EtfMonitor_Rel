package com.etfmonitor.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI API 클라이언트 팩토리
 *
 * 선택된 AI 제공자에 따라 적절한 클라이언트를 반환
 */
@Singleton
class AIApiClientFactory @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider,
    private val claudeApiClient: ClaudeApiClient,
    private val geminiApiClient: GeminiApiClient
) {
    /**
     * 현재 선택된 AI 제공자의 클라이언트 반환
     */
    fun getClient(): AIApiClient {
        val selectedProvider = apiKeyProvider.getSelectedProvider()
        return getClient(selectedProvider)
    }

    /**
     * 특정 AI 제공자의 클라이언트 반환
     */
    fun getClient(provider: AIProvider): AIApiClient {
        return when (provider) {
            AIProvider.CLAUDE -> claudeApiClient
            AIProvider.GEMINI -> geminiApiClient
        }
    }

    /**
     * 사용 가능한 모든 클라이언트 목록
     */
    fun getAllClients(): List<AIApiClient> {
        return listOf(claudeApiClient, geminiApiClient)
    }

    /**
     * 사용 가능한 모든 AI 제공자 목록
     */
    fun getAvailableProviders(): List<AIProvider> {
        return AIProvider.values().toList()
    }

    /**
     * 선택된 AI 제공자의 모델명 반환
     */
    fun getSelectedModel(provider: AIProvider): String {
        return apiKeyProvider.getSelectedModel(provider) ?: getDefaultModel(provider)
    }

    /**
     * 기본 모델명 반환
     */
    private fun getDefaultModel(provider: AIProvider): String {
        return when (provider) {
            AIProvider.CLAUDE -> "claude-3-5-sonnet-20241022"
            AIProvider.GEMINI -> "gemini-2.0-flash-exp"
        }
    }
}
