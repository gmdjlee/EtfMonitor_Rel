package com.etfmonitor.feature.settings.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Settings Feature DI Module
 *
 * Note: SettingsViewModel directly uses repositories from other feature modules
 * (EtfRepository, StockRepository, MarketDepositRepository, etc.)
 * No additional bindings required for the settings feature.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule
