package com.etfmonitor.feature.ranking.di

import com.etfmonitor.feature.ranking.data.repository.RankingRepositoryImpl
import com.etfmonitor.feature.ranking.domain.repository.RankingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RankingModule {
    @Binds
    @Singleton
    abstract fun bindRankingRepository(impl: RankingRepositoryImpl): RankingRepository
}
