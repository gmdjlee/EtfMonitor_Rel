package com.etfmonitor.core.di

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 모듈: Python 엔진 제공
 *
 * Python 클라이언트들(PyKrxClient, OscillatorPyClient, MarketIndexPyClient)은
 * @Inject constructor와 @Singleton을 사용하므로 Hilt가 자동으로 의존성을 주입합니다.
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

    // PyKrxClient, OscillatorPyClient, MarketIndexPyClient는
    // @Inject constructor를 사용하므로 수동 제공 불필요
    // Hilt가 자동으로 의존성을 주입합니다
}
