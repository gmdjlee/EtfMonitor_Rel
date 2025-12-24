package com.etfmonitor.feature.settings.domain.usecase

import com.etfmonitor.feature.settings.domain.model.AIConfiguration
import com.etfmonitor.feature.settings.domain.model.AIModelInfo
import com.etfmonitor.feature.settings.domain.model.AIProviderType
import com.etfmonitor.feature.settings.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * AI Settings UseCases
 */

/**
 * Get AI configuration
 */
class GetAIConfigurationUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(): AIConfiguration = repository.getAIConfiguration()
}

/**
 * Set selected AI provider
 */
class SetSelectedProviderUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(provider: AIProviderType) = repository.setSelectedProvider(provider)
}

/**
 * Set API key for a provider
 */
class SetApiKeyUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(provider: AIProviderType, apiKey: String): Result<Unit> {
        return if (apiKey.isBlank()) {
            Result.failure(IllegalArgumentException("API 키를 입력해주세요"))
        } else {
            repository.setApiKey(provider, apiKey)
            Result.success(Unit)
        }
    }
}

/**
 * Remove API key for a provider
 */
class RemoveApiKeyUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(provider: AIProviderType) = repository.removeApiKey(provider)
}

/**
 * Test API connection
 */
class TestApiConnectionUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(): Result<Unit> = repository.testApiConnection()
}

/**
 * Get available models for a provider
 */
class GetModelsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(provider: AIProviderType): Result<List<AIModelInfo>> {
        return repository.getModels(provider)
    }
}

/**
 * Set selected model for a provider
 */
class SetSelectedModelUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(provider: AIProviderType, modelId: String) {
        repository.setSelectedModel(provider, modelId)
    }
}

/**
 * Set Claude API key
 */
class SetClaudeApiKeyUseCase @Inject constructor(
    private val setApiKeyUseCase: SetApiKeyUseCase
) {
    suspend operator fun invoke(apiKey: String): Result<Unit> {
        return setApiKeyUseCase(AIProviderType.CLAUDE, apiKey)
    }
}

/**
 * Set Gemini API key
 */
class SetGeminiApiKeyUseCase @Inject constructor(
    private val setApiKeyUseCase: SetApiKeyUseCase
) {
    suspend operator fun invoke(apiKey: String): Result<Unit> {
        return setApiKeyUseCase(AIProviderType.GEMINI, apiKey)
    }
}

/**
 * Clear Claude API key
 */
class ClearClaudeApiKeyUseCase @Inject constructor(
    private val removeApiKeyUseCase: RemoveApiKeyUseCase
) {
    suspend operator fun invoke() = removeApiKeyUseCase(AIProviderType.CLAUDE)
}

/**
 * Clear Gemini API key
 */
class ClearGeminiApiKeyUseCase @Inject constructor(
    private val removeApiKeyUseCase: RemoveApiKeyUseCase
) {
    suspend operator fun invoke() = removeApiKeyUseCase(AIProviderType.GEMINI)
}

/**
 * Get Claude models
 */
class GetClaudeModelsUseCase @Inject constructor(
    private val getModelsUseCase: GetModelsUseCase
) {
    suspend operator fun invoke(): Result<List<AIModelInfo>> {
        return getModelsUseCase(AIProviderType.CLAUDE)
    }
}

/**
 * Get Gemini models
 */
class GetGeminiModelsUseCase @Inject constructor(
    private val getModelsUseCase: GetModelsUseCase
) {
    suspend operator fun invoke(): Result<List<AIModelInfo>> {
        return getModelsUseCase(AIProviderType.GEMINI)
    }
}

/**
 * Set Claude model
 */
class SetClaudeModelUseCase @Inject constructor(
    private val setSelectedModelUseCase: SetSelectedModelUseCase
) {
    suspend operator fun invoke(modelId: String) {
        setSelectedModelUseCase(AIProviderType.CLAUDE, modelId)
    }
}

/**
 * Set Gemini model
 */
class SetGeminiModelUseCase @Inject constructor(
    private val setSelectedModelUseCase: SetSelectedModelUseCase
) {
    suspend operator fun invoke(modelId: String) {
        setSelectedModelUseCase(AIProviderType.GEMINI, modelId)
    }
}
