package com.etfmonitor.feature.settings.di

import android.content.Context
import com.etfmonitor.core.network.ai.ApiKeyProvider
import com.etfmonitor.core.ui.theme.ThemeManager
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.feature.etf.domain.repository.EtfRepository
import com.etfmonitor.feature.settings.data.repository.SettingsRepositoryImpl
import com.etfmonitor.feature.settings.domain.repository.SettingsRepository
import com.etfmonitor.repository.AIAnalysisRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Settings Feature DI Module
 *
 * Provides dependencies for the Settings feature following Clean Architecture.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    /**
     * Provides SettingsRepository implementation
     */
    @Provides
    @Singleton
    fun provideSettingsRepository(
        etfRepository: EtfRepository,
        aiAnalysisRepository: AIAnalysisRepository,
        apiKeyProvider: ApiKeyProvider,
        etfDao: EtfDao,
        themeManager: ThemeManager,
        @ApplicationContext context: Context
    ): SettingsRepository = SettingsRepositoryImpl(
        etfRepository = etfRepository,
        aiAnalysisRepository = aiAnalysisRepository,
        apiKeyProvider = apiKeyProvider,
        etfDao = etfDao,
        themeManager = themeManager,
        context = context
    )
}
