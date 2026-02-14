package com.etfmonitor.core.di

import com.krxkt.KrxEtf
import com.krxkt.KrxIndex
import com.krxkt.KrxStock
import com.krxkt.api.KrxClient
import com.krxkt.cache.TickerCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt DI module for kotlin_krx integration.
 *
 * Provides:
 * - KrxClient: Native Kotlin KRX data fetcher
 * - TickerCache: In-memory ticker symbol cache
 * - KrxStock: Stock data operations
 * - KrxEtf: ETF data operations
 * - KrxIndex: Index data operations
 * - @KrxOkHttp OkHttpClient: Dedicated HTTP client with 30s timeout
 */
@Module
@InstallIn(SingletonComponent::class)
object KrxModule {

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class KrxOkHttp

    @Provides
    @Singleton
    @KrxOkHttp
    fun provideKrxOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideTickerCache(): TickerCache = TickerCache()

    @Provides
    @Singleton
    fun provideKrxClient(
        @KrxOkHttp okHttpClient: OkHttpClient
    ): KrxClient = KrxClient(okHttpClient = okHttpClient)

    @Provides
    @Singleton
    fun provideKrxStock(
        client: KrxClient,
        cache: TickerCache
    ): KrxStock = KrxStock(client, cache)

    @Provides
    @Singleton
    fun provideKrxEtf(
        client: KrxClient,
        cache: TickerCache
    ): KrxEtf = KrxEtf(client, cache)

    @Provides
    @Singleton
    fun provideKrxIndex(client: KrxClient): KrxIndex = KrxIndex(client)
}
