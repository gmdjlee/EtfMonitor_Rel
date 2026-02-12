package com.etfmonitor.feature.analysis.di

import com.etfmonitor.core.analysis.CorrelationAnalyzer
import com.etfmonitor.core.database.*
import com.etfmonitor.core.network.ai.AIApiClientFactory
import com.etfmonitor.feature.analysis.data.internal.TimeSeriesAnalysisHelper
import com.etfmonitor.feature.analysis.data.repository.*
import com.etfmonitor.feature.analysis.domain.repository.*
import com.etfmonitor.feature.market.domain.repository.MarketIndexRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Analysis 기능 모듈 DI 설정
 *
 * Phase 7.4: Legacy repositories eliminated
 * All analysis repositories now use direct implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
object AnalysisModule {

    // ==================== Repository Bindings ====================

    @Provides
    @Singleton
    fun provideAIAnalysisRepository(
        aiApiClientFactory: AIApiClientFactory,
        marketIndexDao: MarketIndexDao,
        dailyEtfStatisticsDao: DailyEtfStatisticsDao,
        fearGreedDao: FearGreedDao,
        marketOscillatorDao: MarketOscillatorDao,
        marketDepositDao: MarketDepositDao
    ): AIAnalysisRepository {
        return AIAnalysisRepositoryImpl(
            aiApiClientFactory,
            marketIndexDao,
            dailyEtfStatisticsDao,
            fearGreedDao,
            marketOscillatorDao,
            marketDepositDao
        )
    }

    @Provides
    @Singleton
    fun provideCorrelationAnalysisRepository(
        correlationAnalyzer: CorrelationAnalyzer,
        correlationAnalysisDao: CorrelationAnalysisDao,
        aiAnalysisDao: AIAnalysisDao,
        dailyEtfStatisticsDao: DailyEtfStatisticsDao,
        marketIndexRepository: MarketIndexRepository,
        aiApiClientFactory: AIApiClientFactory
    ): CorrelationAnalysisRepository {
        return CorrelationAnalysisRepositoryImpl(
            correlationAnalyzer,
            correlationAnalysisDao,
            aiAnalysisDao,
            dailyEtfStatisticsDao,
            marketIndexRepository,
            aiApiClientFactory
        )
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        chatDao: AIChatDao,
        aiAnalysisDao: AIAnalysisDao,
        correlationAnalysisDao: CorrelationAnalysisDao,
        aiApiClientFactory: AIApiClientFactory
    ): ChatRepository {
        return ChatRepositoryImpl(
            chatDao,
            aiAnalysisDao,
            correlationAnalysisDao,
            aiApiClientFactory
        )
    }

    @Provides
    @Singleton
    fun provideStatisticsAnalysisRepository(
        etfDao: EtfDao,
        marketIndexDao: MarketIndexDao,
        dailyEtfStatisticsDao: DailyEtfStatisticsDao
    ): StatisticsAnalysisRepository {
        return StatisticsAnalysisRepositoryImpl(
            etfDao,
            marketIndexDao,
            dailyEtfStatisticsDao
        )
    }

    /**
     * StockIndicatorRepository
     * Phase 7.5: TimeSeriesAnalysisHelper로 마이그레이션 완료
     */
    @Provides
    @Singleton
    fun provideStockIndicatorRepository(
        timeSeriesHelper: TimeSeriesAnalysisHelper,
        stockIndicatorAIResultDao: StockIndicatorAIResultDao
    ): StockIndicatorRepository {
        return StockIndicatorRepositoryImpl(
            timeSeriesHelper,
            stockIndicatorAIResultDao
        )
    }

    @Provides
    @Singleton
    fun provideAdvancedAnalysisRepository(
        etfDao: EtfDao,
        stockDao: StockDao,
        stockAnalysisDao: StockAnalysisDao,
        marketDepositDao: MarketDepositDao,
        fearGreedDao: FearGreedDao,
        marketIndexDao: MarketIndexDao,
        sectorAnalysisDao: SectorAnalysisDao,
        etfCorrelationDao: EtfCorrelationDao,
        liquidityAnalysisDao: LiquidityAnalysisDao
    ): AdvancedAnalysisRepository {
        return AdvancedAnalysisRepositoryImpl(
            etfDao,
            stockDao,
            stockAnalysisDao,
            marketDepositDao,
            fearGreedDao,
            marketIndexDao,
            sectorAnalysisDao,
            etfCorrelationDao,
            liquidityAnalysisDao
        )
    }
}
