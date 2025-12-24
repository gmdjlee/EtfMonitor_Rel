package com.etfmonitor.feature.market.di

import com.chaquo.python.Python
import com.etfmonitor.database.FearGreedDao
import com.etfmonitor.database.MarketDepositDao
import com.etfmonitor.database.MarketIndexDao
import com.etfmonitor.database.MarketOscillatorDao
import com.etfmonitor.feature.market.data.datasource.FearGreedLocalDataSource
import com.etfmonitor.feature.market.data.datasource.MarketDepositLocalDataSource
import com.etfmonitor.feature.market.data.datasource.MarketIndexLocalDataSource
import com.etfmonitor.feature.market.data.datasource.MarketOscillatorLocalDataSource
import com.etfmonitor.feature.market.data.repository.FearGreedRepositoryImpl
import com.etfmonitor.feature.market.data.repository.MarketDepositRepositoryImpl
import com.etfmonitor.feature.market.data.repository.MarketIndexRepositoryImpl
import com.etfmonitor.feature.market.data.repository.MarketOscillatorRepositoryImpl
import com.etfmonitor.feature.market.domain.repository.FearGreedRepository
import com.etfmonitor.feature.market.domain.repository.MarketDepositRepository
import com.etfmonitor.feature.market.domain.repository.MarketIndexRepository
import com.etfmonitor.feature.market.domain.repository.MarketOscillatorRepository
import com.etfmonitor.feature.market.domain.usecase.CheckFearGreedDataStatusUseCase
import com.etfmonitor.feature.market.domain.usecase.CheckMarketDepositDataStatusUseCase
import com.etfmonitor.feature.market.domain.usecase.CheckMarketIndexDataStatusUseCase
import com.etfmonitor.feature.market.domain.usecase.CheckMarketOscillatorDataStatusUseCase
import com.etfmonitor.feature.market.domain.usecase.GetLatestMarketOscillatorUseCase
import com.etfmonitor.feature.market.domain.usecase.GetMarketIndexByDateUseCase
import com.etfmonitor.feature.market.domain.usecase.GetMarketIndexByRangeUseCase
import com.etfmonitor.feature.market.domain.usecase.GetOrUpdateMarketDepositUseCase
import com.etfmonitor.feature.market.domain.usecase.GetRecentFearGreedUseCase
import com.etfmonitor.feature.market.domain.usecase.GetRecentMarketDepositUseCase
import com.etfmonitor.feature.market.domain.usecase.GetRecentMarketIndexUseCase
import com.etfmonitor.feature.market.domain.usecase.GetRecentMarketOscillatorUseCase
import com.etfmonitor.feature.market.domain.usecase.InitializeFearGreedUseCase
import com.etfmonitor.feature.market.domain.usecase.InitializeMarketDepositUseCase
import com.etfmonitor.feature.market.domain.usecase.InitializeMarketIndexUseCase
import com.etfmonitor.feature.market.domain.usecase.InitializeMarketOscillatorUseCase
import com.etfmonitor.feature.market.domain.usecase.UpdateFearGreedUseCase
import com.etfmonitor.feature.market.domain.usecase.UpdateMarketDepositUseCase
import com.etfmonitor.feature.market.domain.usecase.UpdateMarketIndexUseCase
import com.etfmonitor.feature.market.domain.usecase.UpdateMarketOscillatorUseCase
import com.etfmonitor.core.network.python.MarketIndexPyClient
import com.etfmonitor.core.network.python.OscillatorPyClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Market Feature DI Module
 */
@Module
@InstallIn(SingletonComponent::class)
object MarketModule {

    // ==================== Data Sources ====================

    @Provides
    @Singleton
    fun provideFearGreedLocalDataSource(
        dao: FearGreedDao
    ): FearGreedLocalDataSource = FearGreedLocalDataSource(dao)

    @Provides
    @Singleton
    fun provideMarketOscillatorLocalDataSource(
        dao: MarketOscillatorDao
    ): MarketOscillatorLocalDataSource = MarketOscillatorLocalDataSource(dao)

    @Provides
    @Singleton
    fun provideMarketDepositLocalDataSource(
        dao: MarketDepositDao
    ): MarketDepositLocalDataSource = MarketDepositLocalDataSource(dao)

    @Provides
    @Singleton
    fun provideMarketIndexLocalDataSource(
        dao: MarketIndexDao
    ): MarketIndexLocalDataSource = MarketIndexLocalDataSource(dao)

    // ==================== Repositories ====================

    @Provides
    @Singleton
    fun provideFearGreedRepository(
        localDataSource: FearGreedLocalDataSource,
        python: Python
    ): FearGreedRepository = FearGreedRepositoryImpl(localDataSource, python)

    @Provides
    @Singleton
    fun provideMarketOscillatorRepository(
        localDataSource: MarketOscillatorLocalDataSource,
        pyClient: OscillatorPyClient
    ): MarketOscillatorRepository = MarketOscillatorRepositoryImpl(localDataSource, pyClient)

    @Provides
    @Singleton
    fun provideMarketDepositRepository(
        localDataSource: MarketDepositLocalDataSource,
        pyClient: OscillatorPyClient
    ): MarketDepositRepository = MarketDepositRepositoryImpl(localDataSource, pyClient)

    @Provides
    @Singleton
    fun provideMarketIndexRepository(
        localDataSource: MarketIndexLocalDataSource,
        pyClient: MarketIndexPyClient
    ): MarketIndexRepository = MarketIndexRepositoryImpl(localDataSource, pyClient)

    // ==================== FearGreed UseCases ====================

    @Provides
    @Singleton
    fun provideGetRecentFearGreedUseCase(
        repository: FearGreedRepository
    ): GetRecentFearGreedUseCase = GetRecentFearGreedUseCase(repository)

    @Provides
    @Singleton
    fun provideInitializeFearGreedUseCase(
        repository: FearGreedRepository
    ): InitializeFearGreedUseCase = InitializeFearGreedUseCase(repository)

    @Provides
    @Singleton
    fun provideUpdateFearGreedUseCase(
        repository: FearGreedRepository
    ): UpdateFearGreedUseCase = UpdateFearGreedUseCase(repository)

    @Provides
    @Singleton
    fun provideCheckFearGreedDataStatusUseCase(
        repository: FearGreedRepository
    ): CheckFearGreedDataStatusUseCase = CheckFearGreedDataStatusUseCase(repository)

    // ==================== MarketOscillator UseCases ====================

    @Provides
    @Singleton
    fun provideGetRecentMarketOscillatorUseCase(
        repository: MarketOscillatorRepository
    ): GetRecentMarketOscillatorUseCase = GetRecentMarketOscillatorUseCase(repository)

    @Provides
    @Singleton
    fun provideGetLatestMarketOscillatorUseCase(
        repository: MarketOscillatorRepository
    ): GetLatestMarketOscillatorUseCase = GetLatestMarketOscillatorUseCase(repository)

    @Provides
    @Singleton
    fun provideInitializeMarketOscillatorUseCase(
        repository: MarketOscillatorRepository
    ): InitializeMarketOscillatorUseCase = InitializeMarketOscillatorUseCase(repository)

    @Provides
    @Singleton
    fun provideUpdateMarketOscillatorUseCase(
        repository: MarketOscillatorRepository
    ): UpdateMarketOscillatorUseCase = UpdateMarketOscillatorUseCase(repository)

    @Provides
    @Singleton
    fun provideCheckMarketOscillatorDataStatusUseCase(
        repository: MarketOscillatorRepository
    ): CheckMarketOscillatorDataStatusUseCase = CheckMarketOscillatorDataStatusUseCase(repository)

    // ==================== MarketDeposit UseCases ====================

    @Provides
    @Singleton
    fun provideGetRecentMarketDepositUseCase(
        repository: MarketDepositRepository
    ): GetRecentMarketDepositUseCase = GetRecentMarketDepositUseCase(repository)

    @Provides
    @Singleton
    fun provideGetOrUpdateMarketDepositUseCase(
        repository: MarketDepositRepository
    ): GetOrUpdateMarketDepositUseCase = GetOrUpdateMarketDepositUseCase(repository)

    @Provides
    @Singleton
    fun provideInitializeMarketDepositUseCase(
        repository: MarketDepositRepository
    ): InitializeMarketDepositUseCase = InitializeMarketDepositUseCase(repository)

    @Provides
    @Singleton
    fun provideUpdateMarketDepositUseCase(
        repository: MarketDepositRepository
    ): UpdateMarketDepositUseCase = UpdateMarketDepositUseCase(repository)

    @Provides
    @Singleton
    fun provideCheckMarketDepositDataStatusUseCase(
        repository: MarketDepositRepository
    ): CheckMarketDepositDataStatusUseCase = CheckMarketDepositDataStatusUseCase(repository)

    // ==================== MarketIndex UseCases ====================

    @Provides
    @Singleton
    fun provideGetRecentMarketIndexUseCase(
        repository: MarketIndexRepository
    ): GetRecentMarketIndexUseCase = GetRecentMarketIndexUseCase(repository)

    @Provides
    @Singleton
    fun provideGetMarketIndexByDateUseCase(
        repository: MarketIndexRepository
    ): GetMarketIndexByDateUseCase = GetMarketIndexByDateUseCase(repository)

    @Provides
    @Singleton
    fun provideGetMarketIndexByRangeUseCase(
        repository: MarketIndexRepository
    ): GetMarketIndexByRangeUseCase = GetMarketIndexByRangeUseCase(repository)

    @Provides
    @Singleton
    fun provideInitializeMarketIndexUseCase(
        repository: MarketIndexRepository
    ): InitializeMarketIndexUseCase = InitializeMarketIndexUseCase(repository)

    @Provides
    @Singleton
    fun provideUpdateMarketIndexUseCase(
        repository: MarketIndexRepository
    ): UpdateMarketIndexUseCase = UpdateMarketIndexUseCase(repository)

    @Provides
    @Singleton
    fun provideCheckMarketIndexDataStatusUseCase(
        repository: MarketIndexRepository
    ): CheckMarketIndexDataStatusUseCase = CheckMarketIndexDataStatusUseCase(repository)
}
