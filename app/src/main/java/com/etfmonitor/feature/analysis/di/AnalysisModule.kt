package com.etfmonitor.feature.analysis.di

import com.etfmonitor.database.AIAnalysisDao
import com.etfmonitor.database.AIChatDao
import com.etfmonitor.database.CorrelationAnalysisDao
import com.etfmonitor.database.EtfCorrelationDao
import com.etfmonitor.database.LiquidityAnalysisDao
import com.etfmonitor.database.SectorAnalysisDao
import com.etfmonitor.database.StockIndicatorAIResultDao
import com.etfmonitor.feature.analysis.data.repository.AdvancedAnalysisRepositoryImpl
import com.etfmonitor.feature.analysis.data.repository.ChatRepositoryImpl
import com.etfmonitor.feature.analysis.data.repository.CorrelationAnalysisRepositoryImpl
import com.etfmonitor.feature.analysis.data.repository.StockIndicatorRepositoryImpl
import com.etfmonitor.feature.analysis.domain.repository.AdvancedAnalysisRepository
import com.etfmonitor.feature.analysis.domain.repository.ChatRepository
import com.etfmonitor.feature.analysis.domain.repository.CorrelationAnalysisRepository
import com.etfmonitor.feature.analysis.domain.repository.StockIndicatorRepository
import com.etfmonitor.repository.AIChatRepository as LegacyChatRepo
import com.etfmonitor.repository.AdvancedAnalysisRepository as LegacyAdvancedRepo
import com.etfmonitor.repository.CorrelationAnalysisRepository as LegacyCorrelationRepo
import com.etfmonitor.repository.TimeSeriesAnalysisRepository as LegacyTimeSeriesRepo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Analysis 기능 모듈 DI 설정
 */
@Module
@InstallIn(SingletonComponent::class)
object AnalysisModule {

    // ==================== Repository Bindings ====================

    @Provides
    @Singleton
    fun provideCorrelationAnalysisRepository(
        legacyRepository: LegacyCorrelationRepo,
        correlationAnalysisDao: CorrelationAnalysisDao,
        aiAnalysisDao: AIAnalysisDao
    ): CorrelationAnalysisRepository {
        return CorrelationAnalysisRepositoryImpl(
            legacyRepository,
            correlationAnalysisDao,
            aiAnalysisDao
        )
    }

    @Provides
    @Singleton
    fun provideAdvancedAnalysisRepository(
        legacyRepository: LegacyAdvancedRepo,
        liquidityAnalysisDao: LiquidityAnalysisDao,
        sectorAnalysisDao: SectorAnalysisDao,
        etfCorrelationDao: EtfCorrelationDao
    ): AdvancedAnalysisRepository {
        return AdvancedAnalysisRepositoryImpl(
            legacyRepository,
            liquidityAnalysisDao,
            sectorAnalysisDao,
            etfCorrelationDao
        )
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        legacyRepository: LegacyChatRepo,
        chatDao: AIChatDao
    ): ChatRepository {
        return ChatRepositoryImpl(legacyRepository, chatDao)
    }

    @Provides
    @Singleton
    fun provideStockIndicatorRepository(
        legacyRepository: LegacyTimeSeriesRepo,
        stockIndicatorAIResultDao: StockIndicatorAIResultDao
    ): StockIndicatorRepository {
        return StockIndicatorRepositoryImpl(legacyRepository, stockIndicatorAIResultDao)
    }
}
