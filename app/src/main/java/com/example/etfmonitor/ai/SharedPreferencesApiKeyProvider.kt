package com.etfmonitor.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
    context: Context
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

    /**
     * AI 제공자별 키 이름 생성
     */
    private fun getKeyForProvider(provider: AIProvider): String {
        return when (provider) {
            AIProvider.CLAUDE -> KEY_API_KEY_CLAUDE
            AIProvider.GEMINI -> KEY_API_KEY_GEMINI
        }
    }

    companion object {
        private const val PREFS_NAME = "ai_api_prefs"
        private const val KEY_API_KEY_CLAUDE = "api_key_claude"
        private const val KEY_API_KEY_GEMINI = "api_key_gemini"
        private const val KEY_SELECTED_PROVIDER = "selected_provider"
    }
}
