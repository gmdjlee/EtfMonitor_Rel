package com.etfmonitor.feature.stock.di

import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.StockAnalysisDao
import com.etfmonitor.core.database.StockDao
import com.etfmonitor.core.network.python.OscillatorPyClient
import com.etfmonitor.feature.stock.data.datasource.StockAnalysisLocalDataSource
import com.etfmonitor.feature.stock.data.datasource.StockLocalDataSource
import com.etfmonitor.feature.stock.data.datasource.StockStatisticsLocalDataSource
import com.etfmonitor.feature.stock.data.repository.StockAnalysisRepositoryImpl
import com.etfmonitor.feature.stock.data.repository.StockRepositoryImpl
import com.etfmonitor.feature.stock.data.repository.StockStatisticsRepositoryImpl
import com.etfmonitor.feature.stock.data.repository.StockTrendRepositoryImpl
import com.etfmonitor.feature.stock.domain.repository.StockAnalysisRepository
import com.etfmonitor.feature.stock.domain.repository.StockRepository
import com.etfmonitor.feature.stock.domain.repository.StockStatisticsRepository
import com.etfmonitor.feature.stock.domain.repository.StockTrendRepository
import com.etfmonitor.feature.stock.domain.usecase.AnalyzeStockUseCase
import com.etfmonitor.feature.stock.domain.usecase.GetCashDepositTrendUseCase
import com.etfmonitor.feature.stock.domain.usecase.GetStatisticsDatesUseCase
import com.etfmonitor.feature.stock.domain.usecase.GetStockAnalysisUseCase
import com.etfmonitor.feature.stock.domain.usecase.GetStockChangesUseCase
import com.etfmonitor.feature.stock.domain.usecase.GetStockRankingUseCase
import com.etfmonitor.feature.stock.domain.usecase.GetStockTrendUseCase
import com.etfmonitor.feature.stock.domain.usecase.InitializeStocksUseCase
import com.etfmonitor.feature.stock.domain.usecase.SearchStocksUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Stock Feature Hilt Module
 *
 * Stock Feature에 필요한 의존성을 제공합니다.
 *
 * ## 제공되는 의존성
 * - Data Sources: StockLocalDataSource, StockAnalysisLocalDataSource, StockStatisticsLocalDataSource
 * - Repositories: StockRepository, StockAnalysisRepository, StockTrendRepository, StockStatisticsRepository
 * - Use Cases: All stock-related use cases
 */
@Module
@InstallIn(SingletonComponent::class)
object StockModule {

    // ========== Data Sources ==========

    @Provides
    @Singleton
    fun provideStockLocalDataSource(
        stockDao: StockDao
    ): StockLocalDataSource = StockLocalDataSource(stockDao)

    @Provides
    @Singleton
    fun provideStockAnalysisLocalDataSource(
        stockAnalysisDao: StockAnalysisDao
    ): StockAnalysisLocalDataSource = StockAnalysisLocalDataSource(stockAnalysisDao)

    @Provides
    @Singleton
    fun provideStockStatisticsLocalDataSource(
        etfDao: EtfDao
    ): StockStatisticsLocalDataSource = StockStatisticsLocalDataSource(etfDao)

    // ========== Repositories ==========

    @Provides
    @Singleton
    fun provideStockRepository(
        localDataSource: StockLocalDataSource,
        pyClient: OscillatorPyClient
    ): StockRepository = StockRepositoryImpl(localDataSource, pyClient)

    @Provides
    @Singleton
    fun provideStockAnalysisRepository(
        analysisLocalDataSource: StockAnalysisLocalDataSource,
        stockLocalDataSource: StockLocalDataSource,
        pyClient: OscillatorPyClient
    ): StockAnalysisRepository = StockAnalysisRepositoryImpl(
        analysisLocalDataSource,
        stockLocalDataSource,
        pyClient
    )

    @Provides
    @Singleton
    fun provideStockTrendRepository(
        localDataSource: StockStatisticsLocalDataSource
    ): StockTrendRepository = StockTrendRepositoryImpl(localDataSource)

    @Provides
    @Singleton
    fun provideStockStatisticsRepository(
        localDataSource: StockStatisticsLocalDataSource
    ): StockStatisticsRepository = StockStatisticsRepositoryImpl(localDataSource)

    // ========== Use Cases ==========

    @Provides
    @Singleton
    fun provideSearchStocksUseCase(
        repository: StockRepository
    ): SearchStocksUseCase = SearchStocksUseCase(repository)

    @Provides
    @Singleton
    fun provideGetStockTrendUseCase(
        repository: StockTrendRepository
    ): GetStockTrendUseCase = GetStockTrendUseCase(repository)

    @Provides
    @Singleton
    fun provideGetStockAnalysisUseCase(
        repository: StockAnalysisRepository
    ): GetStockAnalysisUseCase = GetStockAnalysisUseCase(repository)

    @Provides
    @Singleton
    fun provideGetStockRankingUseCase(
        repository: StockStatisticsRepository
    ): GetStockRankingUseCase = GetStockRankingUseCase(repository)

    @Provides
    @Singleton
    fun provideGetStockChangesUseCase(
        repository: StockStatisticsRepository
    ): GetStockChangesUseCase = GetStockChangesUseCase(repository)

    @Provides
    @Singleton
    fun provideAnalyzeStockUseCase(
        repository: StockStatisticsRepository
    ): AnalyzeStockUseCase = AnalyzeStockUseCase(repository)

    @Provides
    @Singleton
    fun provideGetStatisticsDatesUseCase(
        repository: StockStatisticsRepository
    ): GetStatisticsDatesUseCase = GetStatisticsDatesUseCase(repository)

    @Provides
    @Singleton
    fun provideGetCashDepositTrendUseCase(
        repository: StockStatisticsRepository
    ): GetCashDepositTrendUseCase = GetCashDepositTrendUseCase(repository)

    @Provides
    @Singleton
    fun provideInitializeStocksUseCase(
        repository: StockRepository
    ): InitializeStocksUseCase = InitializeStocksUseCase(repository)
}
