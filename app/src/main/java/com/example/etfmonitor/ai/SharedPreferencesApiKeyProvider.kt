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

    override fun getApiKey(): String? {
        return sharedPreferences.getString(KEY_API_KEY, null)
    }

    override fun setApiKey(apiKey: String) {
        sharedPreferences.edit()
            .putString(KEY_API_KEY, apiKey)
            .apply()
    }

    override fun removeApiKey() {
        sharedPreferences.edit()
            .remove(KEY_API_KEY)
            .apply()
    }

    override fun hasApiKey(): Boolean {
        return getApiKey() != null
    }

    companion object {
        private const val PREFS_NAME = "claude_api_prefs"
        private const val KEY_API_KEY = "api_key"
    }
}
