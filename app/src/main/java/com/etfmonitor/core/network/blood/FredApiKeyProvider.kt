package com.etfmonitor.core.network.blood

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FRED API key provider using EncryptedSharedPreferences.
 *
 * Stores the FRED API key (used for Blood Indicator high yield spread data)
 * in AES256-GCM encrypted storage via Android Keystore, identical to the
 * pattern used by KisApiKeyProvider and SharedPreferencesApiKeyProvider.
 *
 * Get a free FRED API key at: https://fred.stlouisfed.org/docs/api/api_key.html
 */
@Singleton
class FredApiKeyProvider @Inject constructor(
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

    fun saveApiKey(key: String) {
        sharedPreferences.edit().putString(KEY_FRED_API_KEY, key).apply()
    }

    fun getApiKey(): String? {
        val key = sharedPreferences.getString(KEY_FRED_API_KEY, null)
        return if (key.isNullOrBlank()) null else key
    }

    fun isConfigured(): Boolean = getApiKey() != null

    fun clearApiKey() {
        sharedPreferences.edit().remove(KEY_FRED_API_KEY).apply()
    }

    companion object {
        const val PREFS_NAME = "fred_api_prefs"
        private const val KEY_FRED_API_KEY = "fred_api_key"
    }
}
