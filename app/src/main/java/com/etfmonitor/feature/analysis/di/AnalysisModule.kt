package com.etfmonitor.feature.analysis.di

import com.etfmonitor.core.database.AIAnalysisDao
import com.etfmonitor.core.database.AIChatDao
import com.etfmonitor.core.database.CorrelationAnalysisDao
import com.etfmonitor.core.database.EtfCorrelationDao
import com.etfmonitor.core.database.LiquidityAnalysisDao
import com.etfmonitor.core.database.SectorAnalysisDao
import com.etfmonitor.core.database.StockIndicatorAIResultDao
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
