package com.etfmonitor.feature.home.di

import com.etfmonitor.feature.home.data.repository.HomeRepositoryImpl
import com.etfmonitor.feature.home.domain.repository.HomeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Home Feature DI Module
 *
 * Home 기능에 필요한 의존성을 Hilt에 등록합니다.
 * UseCase들은 @Inject constructor로 자동 주입됩니다.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {

    /**
     * HomeRepository 인터페이스에 HomeRepositoryImpl 바인딩
     */
    @Binds
    @Singleton
    abstract fun bindHomeRepository(
        homeRepositoryImpl: HomeRepositoryImpl
    ): HomeRepository
}
