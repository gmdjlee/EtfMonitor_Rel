package com.etfmonitor.feature.etf.di

import com.etfmonitor.core.database.DailyEtfStatisticsDao
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.StockDao
import com.etfmonitor.core.domain.usecase.krx.GetKrxBusinessDaysUseCase
import com.etfmonitor.core.domain.usecase.krx.GetKrxEtfHoldingsUseCase
import com.etfmonitor.core.domain.usecase.krx.GetKrxEtfListUseCase
import com.etfmonitor.feature.etf.data.datasource.EtfLocalDataSource
import com.etfmonitor.feature.etf.data.repository.EtfRepositoryImpl
import com.etfmonitor.feature.etf.domain.repository.EtfRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * ETF Feature Hilt Module
 *
 * ETF Feature에 필요한 의존성을 제공합니다.
 *
 * ## 제공되는 의존성
 * - EtfLocalDataSource
 * - EtfRepository (EtfRepositoryImpl)
 *
 * UseCases (GetEtfListUseCase, SearchEtfsUseCase, etc.) use @Inject constructor binding.
 */
@Module
@InstallIn(SingletonComponent::class)
object EtfModule {

    // ========== Data Sources ==========

    @Provides
    @Singleton
    fun provideEtfLocalDataSource(
        etfDao: EtfDao
    ): EtfLocalDataSource = EtfLocalDataSource(etfDao)

    // ========== Repositories ==========

    @Provides
    @Singleton
    fun provideEtfRepository(
        localDataSource: EtfLocalDataSource,
        etfDao: EtfDao,
        dailyEtfStatisticsDao: DailyEtfStatisticsDao,
        stockDao: StockDao,
        getKrxBusinessDaysUseCase: GetKrxBusinessDaysUseCase,
        getKrxEtfHoldingsUseCase: GetKrxEtfHoldingsUseCase,
        getKrxEtfListUseCase: GetKrxEtfListUseCase
    ): EtfRepository = EtfRepositoryImpl(
        localDataSource,
        etfDao,
        dailyEtfStatisticsDao,
        stockDao,
        getKrxBusinessDaysUseCase,
        getKrxEtfHoldingsUseCase,
        getKrxEtfListUseCase
    )

}
