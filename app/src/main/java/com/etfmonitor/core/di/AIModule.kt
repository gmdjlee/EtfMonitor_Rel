package com.etfmonitor.core.di

import android.content.Context
import com.etfmonitor.core.network.ai.*
import com.etfmonitor.core.analysis.Backtester
import com.etfmonitor.core.analysis.CorrelationAnalyzer
import com.etfmonitor.core.database.*
import com.etfmonitor.repository.TimeSeriesAnalysisRepository
import com.etfmonitor.core.network.python.OscillatorPyClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 모듈: AI 분석 컴포넌트 제공
 *
 * Phase 7.4: Analysis repositories migrated to feature layer
 * - AIAnalysisRepository, AIChatRepository 제거됨 (AnalysisModule로 이관)
 * - TimeSeriesAnalysisRepository 유지 (Phase 7.5에서 마이그레이션 예정)
 */
@Module
@InstallIn(SingletonComponent::class)
object AIModule {

    /**
     * API 키 제공자 (EncryptedSharedPreferences 기반)
     */
    @Provides
    @Singleton
    fun provideApiKeyProvider(
        @ApplicationContext context: Context
    ): ApiKeyProvider {
        return SharedPreferencesApiKeyProvider(context)
    }

    /**
     * Claude API 클라이언트
     */
    @Provides
    @Singleton
    fun provideClaudeApiClient(
        apiKeyProvider: ApiKeyProvider
    ): ClaudeApiClient {
        return ClaudeApiClient(apiKeyProvider)
    }

    /**
     * Gemini API 클라이언트
     */
    @Provides
    @Singleton
    fun provideGeminiApiClient(
        apiKeyProvider: ApiKeyProvider
    ): GeminiApiClient {
        return GeminiApiClient(apiKeyProvider)
    }

    /**
     * AI API 클라이언트 팩토리
     * 선택된 AI 제공자에 따라 적절한 클라이언트 반환
     */
    @Provides
    @Singleton
    fun provideAIApiClientFactory(
        apiKeyProvider: ApiKeyProvider,
        claudeApiClient: ClaudeApiClient,
        geminiApiClient: GeminiApiClient
    ): AIApiClientFactory {
        return AIApiClientFactory(apiKeyProvider, claudeApiClient, geminiApiClient)
    }

    // AIAnalysisRepository: Provided by AnalysisModule as AIAnalysisRepositoryImpl

    /**
     * Backtester
     * AI 신호의 과거 정확도 검증
     */
    @Provides
    @Singleton
    fun provideBacktester(
        marketIndexDao: MarketIndexDao
    ): Backtester {
        return Backtester(marketIndexDao)
    }

    /**
     * CorrelationAnalyzer
     * 로컬에서 상관관계 계산 수행
     */
    @Provides
    @Singleton
    fun provideCorrelationAnalyzer(
        marketIndexDao: MarketIndexDao,
        dailyEtfStatisticsDao: DailyEtfStatisticsDao,
        fearGreedDao: FearGreedDao,
        marketOscillatorDao: MarketOscillatorDao,
        marketDepositDao: MarketDepositDao
    ): CorrelationAnalyzer {
        return CorrelationAnalyzer(
            marketIndexDao,
            dailyEtfStatisticsDao,
            fearGreedDao,
            marketOscillatorDao,
            marketDepositDao
        )
    }

    // CorrelationAnalysisRepository: Provided by AnalysisModule as CorrelationAnalysisRepositoryImpl
    // AIChatRepository: Provided by AnalysisModule as ChatRepositoryImpl

    /**
     * TimeSeriesAnalysisRepository
     * 시계열 데이터 수집 및 분석 Repository
     */
    @Provides
    @Singleton
    fun provideTimeSeriesAnalysisRepository(
        marketIndexDao: MarketIndexDao,
        fearGreedDao: FearGreedDao,
        marketOscillatorDao: MarketOscillatorDao,
        marketDepositDao: MarketDepositDao,
        dailyEtfStatisticsDao: DailyEtfStatisticsDao,
        aiApiClientFactory: AIApiClientFactory,
        oscillatorPyClient: OscillatorPyClient,
        etfDao: EtfDao,
        stockIndicatorAIResultDao: StockIndicatorAIResultDao
    ): TimeSeriesAnalysisRepository {
        return TimeSeriesAnalysisRepository(
            marketIndexDao,
            fearGreedDao,
            marketOscillatorDao,
            marketDepositDao,
            dailyEtfStatisticsDao,
            aiApiClientFactory,
            oscillatorPyClient,
            etfDao,
            stockIndicatorAIResultDao
        )
    }
}
