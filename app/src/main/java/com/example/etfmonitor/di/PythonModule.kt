package com.etfmonitor.di

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
 * 최적화 포인트:
 * - Python 인스턴스를 Singleton으로 관리
 * - Thread-safe 초기화 보장
 * - Chaquopy 플랫폼 초기화를 Hilt가 관리
 */
@Module
@InstallIn(SingletonComponent::class)
object PythonModule {

    /**
     * Python 인스턴스 제공 (Singleton)
     *
     * Production 최적화:
     * - Python.isStarted() 체크로 중복 초기화 방지
     * - AndroidPlatform 초기화를 Application Context로 수행
     * - Singleton으로 단일 인스턴스 보장하여 메모리 최적화
     */
    @Provides
    @Singleton
    fun providePython(
        @ApplicationContext context: Context
    ): Python {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        return Python.getInstance()
    }
}
