package com.etfmonitor.feature.backup.di

import android.content.Context
import com.etfmonitor.core.database.BackupDao
import com.etfmonitor.feature.backup.data.repository.BackupRepositoryImpl
import com.etfmonitor.feature.backup.domain.repository.BackupRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 백업 기능 DI 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
object BackupModule {

    @Provides
    @Singleton
    fun provideBackupRepository(
        @ApplicationContext context: Context,
        backupDao: BackupDao
    ): BackupRepository {
        return BackupRepositoryImpl(context, backupDao)
    }
}
