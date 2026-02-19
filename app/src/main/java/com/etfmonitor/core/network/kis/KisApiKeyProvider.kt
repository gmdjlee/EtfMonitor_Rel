package com.etfmonitor.core.network.kis

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
class KisApiKeyProvider @Inject constructor(
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

    val configFlow: Flow<KisApiKeyConfig> = _configFlow.asStateFlow()

    fun getConfig(): KisApiKeyConfig = _configFlow.value

    fun setAppKey(appKey: String) {
        sharedPreferences.edit().putString(KEY_APP_KEY, appKey).apply()
        _configFlow.value = loadConfig()
    }

    fun setAppSecret(appSecret: String) {
        sharedPreferences.edit().putString(KEY_APP_SECRET, appSecret).apply()
        _configFlow.value = loadConfig()
    }

    fun setInvestmentMode(mode: InvestmentMode) {
        sharedPreferences.edit().putString(KEY_INVESTMENT_MODE, mode.name).apply()
        _configFlow.value = loadConfig()
    }

    fun isConfigured(): Boolean = getConfig().isValid()

    fun clearAll() {
        sharedPreferences.edit().clear().apply()
        _configFlow.value = KisApiKeyConfig()
    }

    private fun loadConfig(): KisApiKeyConfig {
        val appKey = sharedPreferences.getString(KEY_APP_KEY, "") ?: ""
        val appSecret = sharedPreferences.getString(KEY_APP_SECRET, "") ?: ""
        val modeName = sharedPreferences.getString(KEY_INVESTMENT_MODE, InvestmentMode.MOCK.name)
        val mode = try {
            InvestmentMode.valueOf(modeName ?: InvestmentMode.MOCK.name)
        } catch (_: IllegalArgumentException) {
            InvestmentMode.MOCK
        }
        return KisApiKeyConfig(appKey, appSecret, mode)
    }

    companion object {
        private const val PREFS_NAME = "kis_api_prefs"
        private const val KEY_APP_KEY = "kis_app_key"
        private const val KEY_APP_SECRET = "kis_app_secret"
        private const val KEY_INVESTMENT_MODE = "kis_investment_mode"
    }
}
