package com.etfmonitor.core.network.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedPreferences 기반 API 키 제공자
 *
 * EncryptedSharedPreferences를 사용하여 API 키를 안전하게 저장
 * Android Keystore를 통해 암호화 키 관리
 *
 * 여러 AI 제공자(Claude, Gemini 등)의 API 키를 각각 저장
 */
@Singleton
class SharedPreferencesApiKeyProvider @Inject constructor(
    @ApplicationContext context: Context
) : ApiKeyProvider {

    private val sharedPreferences: SharedPreferences = run {
        // MasterKey 생성 (Android Keystore 기반)
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // EncryptedSharedPreferences 생성
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun getApiKey(provider: AIProvider): String? {
        val key = getKeyForProvider(provider)
        return sharedPreferences.getString(key, null)
    }

    override fun setApiKey(provider: AIProvider, apiKey: String) {
        val key = getKeyForProvider(provider)
        // apply()는 비동기적으로 저장하지만, Android의 내부 메커니즘이
        // 앱 종료 전 pending writes를 처리합니다.
        // SharedPreferences는 메모리에 즉시 반영되므로 후속 읽기는 안전합니다.
        sharedPreferences.edit()
            .putString(key, apiKey)
            .apply()
    }

    override fun removeApiKey(provider: AIProvider) {
        val key = getKeyForProvider(provider)
        sharedPreferences.edit()
            .remove(key)
            .apply()
    }

    override fun hasApiKey(provider: AIProvider): Boolean {
        return getApiKey(provider) != null
    }

    override fun getSelectedProvider(): AIProvider {
        val providerName = sharedPreferences.getString(KEY_SELECTED_PROVIDER, AIProvider.CLAUDE.name)
        return AIProvider.fromString(providerName ?: AIProvider.CLAUDE.name)
    }

    override fun setSelectedProvider(provider: AIProvider) {
        sharedPreferences.edit()
            .putString(KEY_SELECTED_PROVIDER, provider.name)
            .apply()
    }

    override fun getSelectedModel(provider: AIProvider): String? {
        val key = getModelKeyForProvider(provider)
        return sharedPreferences.getString(key, null)
    }

    override fun setSelectedModel(provider: AIProvider, modelId: String) {
        val key = getModelKeyForProvider(provider)
        sharedPreferences.edit()
            .putString(key, modelId)
            .apply()
    }

    override fun removeSelectedModel(provider: AIProvider) {
        val key = getModelKeyForProvider(provider)
        sharedPreferences.edit()
            .remove(key)
            .apply()
    }

    /**
     * AI 제공자별 API 키 이름 생성
     */
    private fun getKeyForProvider(provider: AIProvider): String {
        return when (provider) {
            AIProvider.CLAUDE -> KEY_API_KEY_CLAUDE
            AIProvider.GEMINI -> KEY_API_KEY_GEMINI
        }
    }

    /**
     * AI 제공자별 모델 키 이름 생성
     */
    private fun getModelKeyForProvider(provider: AIProvider): String {
        return when (provider) {
            AIProvider.CLAUDE -> KEY_MODEL_CLAUDE
            AIProvider.GEMINI -> KEY_MODEL_GEMINI
        }
    }

    companion object {
        private const val PREFS_NAME = "ai_api_prefs"
        private const val KEY_API_KEY_CLAUDE = "api_key_claude"
        private const val KEY_API_KEY_GEMINI = "api_key_gemini"
        private const val KEY_SELECTED_PROVIDER = "selected_provider"
        private const val KEY_MODEL_CLAUDE = "model_claude"
        private const val KEY_MODEL_GEMINI = "model_gemini"
    }
}
