package com.etfmonitor.core.di

import android.content.Context
import com.etfmonitor.core.network.ai.*
import com.etfmonitor.core.analysis.Backtester
import com.etfmonitor.core.analysis.CorrelationAnalyzer
import com.etfmonitor.database.*
import com.etfmonitor.repository.AIAnalysisRepository
import com.etfmonitor.repository.AIChatRepository
import com.etfmonitor.repository.MarketIndexRepository
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
 * Phase 3: AI 신호 생성 기능
 * - Claude API 통합
 * - Gemini API 통합
 * - 시장 분석 및 신호 생성
 * - 백테스팅
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

    /**
     * AI 분석 Repository
     * 시장 분석 및 신호 생성의 핵심 로직
     */
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
        return AIAnalysisRepository(
            aiApiClientFactory,
            marketIndexDao,
            dailyEtfStatisticsDao,
            fearGreedDao,
            marketOscillatorDao,
            marketDepositDao
        )
    }

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

    // CorrelationAnalysisRepository is auto-injected via @Inject constructor
    // (removed explicit provider to avoid DI duplicate with AnalysisModule)

    /**
     * AIChatRepository
     * AI 채팅 기능 Repository
     */
    @Provides
    @Singleton
    fun provideAIChatRepository(
        chatDao: AIChatDao,
        aiAnalysisDao: AIAnalysisDao,
        correlationAnalysisDao: CorrelationAnalysisDao,
        aiApiClientFactory: AIApiClientFactory
    ): AIChatRepository {
        return AIChatRepository(
            chatDao,
            aiAnalysisDao,
            correlationAnalysisDao,
            aiApiClientFactory
        )
    }

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
