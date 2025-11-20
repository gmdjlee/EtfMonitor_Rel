package com.etfmonitor.di

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 모듈: WorkManager 통합
 *
 * 최적화 포인트:
 * - HiltWorkerFactory를 사용하여 Worker에 의존성 주입
 * - WorkManager를 Hilt가 관리하도록 구성
 * - Worker 생성 시 자동으로 Repository 주입
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkerModule {

    /**
     * WorkManager 제공 (Singleton)
     *
     * Production 최적화:
     * - Application Context에서 WorkManager 인스턴스 획득
     * - Singleton으로 단일 인스턴스 보장
     */
    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context
    ): WorkManager {
        return WorkManager.getInstance(context)
    }
}
