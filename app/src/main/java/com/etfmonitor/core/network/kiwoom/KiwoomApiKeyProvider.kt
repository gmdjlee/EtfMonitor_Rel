package com.etfmonitor.core.network.kiwoom

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KiwoomApiKeyProvider @Inject constructor(
    @ApplicationContext context: Context
) {
    private val sharedPreferences: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _configFlow = MutableStateFlow(loadConfig())

    val configFlow: Flow<KiwoomApiKeyConfig> = _configFlow.asStateFlow()

    fun getConfig(): KiwoomApiKeyConfig = _configFlow.value

    fun setAppKey(appKey: String) {
        sharedPreferences.edit().putString(KEY_APP_KEY, appKey).apply()
        _configFlow.value = loadConfig()
    }

    fun setSecretKey(secretKey: String) {
        sharedPreferences.edit().putString(KEY_SECRET_KEY, secretKey).apply()
        _configFlow.value = loadConfig()
    }

    fun setInvestmentMode(mode: KiwoomInvestmentMode) {
        sharedPreferences.edit().putString(KEY_INVESTMENT_MODE, mode.name).apply()
        _configFlow.value = loadConfig()
    }

    fun isConfigured(): Boolean = getConfig().isValid()

    fun clearAll() {
        sharedPreferences.edit().clear().apply()
        _configFlow.value = KiwoomApiKeyConfig()
    }

    private fun loadConfig(): KiwoomApiKeyConfig {
        val appKey = sharedPreferences.getString(KEY_APP_KEY, "") ?: ""
        val secretKey = sharedPreferences.getString(KEY_SECRET_KEY, "") ?: ""
        val modeName = sharedPreferences.getString(KEY_INVESTMENT_MODE, KiwoomInvestmentMode.MOCK.name)
        val mode = try {
            KiwoomInvestmentMode.valueOf(modeName ?: KiwoomInvestmentMode.MOCK.name)
        } catch (_: IllegalArgumentException) {
            KiwoomInvestmentMode.MOCK
        }
        return KiwoomApiKeyConfig(appKey, secretKey, mode)
    }

    companion object {
        private const val PREFS_NAME = "kiwoom_api_prefs"
        private const val KEY_APP_KEY = "kiwoom_app_key"
        private const val KEY_SECRET_KEY = "kiwoom_secret_key"
        private const val KEY_INVESTMENT_MODE = "kiwoom_investment_mode"
    }
}
