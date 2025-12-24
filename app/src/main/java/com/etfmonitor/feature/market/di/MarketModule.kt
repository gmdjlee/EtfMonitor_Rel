package com.etfmonitor.feature.market.di

import com.etfmonitor.feature.market.data.repository.FearGreedRepositoryImpl
import com.etfmonitor.feature.market.data.repository.MarketDepositRepositoryImpl
import com.etfmonitor.feature.market.data.repository.MarketIndexRepositoryImpl
import com.etfmonitor.feature.market.data.repository.MarketOscillatorRepositoryImpl
import com.etfmonitor.feature.market.domain.repository.FearGreedRepository
import com.etfmonitor.feature.market.domain.repository.MarketDepositRepository
import com.etfmonitor.feature.market.domain.repository.MarketIndexRepository
import com.etfmonitor.feature.market.domain.repository.MarketOscillatorRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Market Feature DI Module
 *
 * Provides:
 * - FearGreedRepository (3x data collection for MA loss)
 * - MarketDepositRepository (12h smart caching)
 * - MarketOscillatorRepository (180s timeout for 200+ stocks)
 * - MarketIndexRepository
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MarketModule {

    @Binds
    @Singleton
    abstract fun bindFearGreedRepository(
        impl: FearGreedRepositoryImpl
    ): FearGreedRepository

    @Binds
    @Singleton
    abstract fun bindMarketDepositRepository(
        impl: MarketDepositRepositoryImpl
    ): MarketDepositRepository

    @Binds
    @Singleton
    abstract fun bindMarketOscillatorRepository(
        impl: MarketOscillatorRepositoryImpl
    ): MarketOscillatorRepository

    @Binds
    @Singleton
    abstract fun bindMarketIndexRepository(
        impl: MarketIndexRepositoryImpl
    ): MarketIndexRepository
}
