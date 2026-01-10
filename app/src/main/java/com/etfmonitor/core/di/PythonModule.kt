package com.etfmonitor.core.di

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.etfmonitor.core.network.python.MarketIndexPyClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 모듈: Python 엔진 및 관련 클라이언트 제공
 *
 * 모든 Python 클라이언트를 명시적으로 제공하여 DI 일관성 유지
 */
@Module
@InstallIn(SingletonComponent::class)
object PythonModule {

    /**
     * Python 인스턴스 제공 (Singleton)
     * - Python.isStarted() 체크로 중복 초기화 방지
     * - AndroidPlatform 초기화를 Application Context로 수행
     */
    @Provides
    @Singleton
    fun providePython(@ApplicationContext context: Context): Python {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        return Python.getInstance()
    }

    // OscillatorPyClient는 @Inject constructor를 사용하므로 수동 제공 불필요
    // Hilt가 자동으로 의존성을 주입합니다

    /**
     * MarketIndexPyClient 제공 (Singleton)
     * KOSPI/KOSDAQ 시장 지수 수집용 Python 클라이언트
     */
    @Provides
    @Singleton
    fun provideMarketIndexPyClient(@ApplicationContext context: Context): MarketIndexPyClient {
        return MarketIndexPyClient(context)
    }
}
