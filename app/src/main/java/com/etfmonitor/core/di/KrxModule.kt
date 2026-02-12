package com.etfmonitor.core.di

import com.krxkt.KrxEtf
import com.krxkt.KrxIndex
import com.krxkt.KrxStock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 모듈: kotlin_krx 라이브러리 인스턴스 제공
 *
 * PythonModule을 대체하여 네이티브 Kotlin KRX API 클라이언트를 제공합니다.
 * KrxDataClient, MarketIndexClient, StockDataClient 등은 @Inject constructor를
 * 사용하므로 Hilt가 자동으로 의존성을 주입합니다.
 */
@Module
@InstallIn(SingletonComponent::class)
object KrxModule {

    @Provides
    @Singleton
    fun provideKrxStock(): KrxStock = KrxStock()

    @Provides
    @Singleton
    fun provideKrxEtf(): KrxEtf = KrxEtf()

    @Provides
    @Singleton
    fun provideKrxIndex(): KrxIndex = KrxIndex()
}
