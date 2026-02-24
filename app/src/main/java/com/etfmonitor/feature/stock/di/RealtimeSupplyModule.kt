package com.etfmonitor.feature.stock.di

import com.etfmonitor.feature.stock.data.repository.RealtimeSupplyRepositoryImpl
import com.etfmonitor.feature.stock.domain.repository.RealtimeSupplyRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RealtimeSupplyModule {
    @Binds
    @Singleton
    abstract fun bindRealtimeSupplyRepository(
        impl: RealtimeSupplyRepositoryImpl
    ): RealtimeSupplyRepository
}
