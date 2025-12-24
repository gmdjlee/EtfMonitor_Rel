package com.etfmonitor.feature.etf.di

import com.etfmonitor.database.EtfDao
import com.etfmonitor.feature.etf.data.datasource.EtfLocalDataSource
import com.etfmonitor.feature.etf.data.repository.EtfRepositoryImpl
import com.etfmonitor.feature.etf.domain.repository.EtfRepository
import com.etfmonitor.feature.etf.domain.usecase.CheckDataStatusUseCase
import com.etfmonitor.feature.etf.domain.usecase.GetEtfComparisonUseCase
import com.etfmonitor.feature.etf.domain.usecase.GetEtfDetailUseCase
import com.etfmonitor.feature.etf.domain.usecase.GetEtfListUseCase
import com.etfmonitor.feature.etf.domain.usecase.SearchEtfsUseCase
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
 * - GetEtfListUseCase
 * - SearchEtfsUseCase
 * - GetEtfDetailUseCase
 * - GetEtfComparisonUseCase
 * - CheckDataStatusUseCase
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
        localDataSource: EtfLocalDataSource
    ): EtfRepository = EtfRepositoryImpl(localDataSource)

    // ========== Use Cases ==========

    @Provides
    @Singleton
    fun provideGetEtfListUseCase(
        repository: EtfRepository
    ): GetEtfListUseCase = GetEtfListUseCase(repository)

    @Provides
    @Singleton
    fun provideSearchEtfsUseCase(
        repository: EtfRepository
    ): SearchEtfsUseCase = SearchEtfsUseCase(repository)

    @Provides
    @Singleton
    fun provideGetEtfDetailUseCase(
        repository: EtfRepository
    ): GetEtfDetailUseCase = GetEtfDetailUseCase(repository)

    @Provides
    @Singleton
    fun provideGetEtfComparisonUseCase(
        repository: EtfRepository
    ): GetEtfComparisonUseCase = GetEtfComparisonUseCase(repository)

    @Provides
    @Singleton
    fun provideCheckDataStatusUseCase(
        repository: EtfRepository
    ): CheckDataStatusUseCase = CheckDataStatusUseCase(repository)
}
