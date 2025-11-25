package com.etfmonitor.di

import android.content.Context
import com.etfmonitor.ai.ApiKeyProvider
import com.etfmonitor.ai.ClaudeApiClient
import com.etfmonitor.ai.SharedPreferencesApiKeyProvider
import com.etfmonitor.analysis.Backtester
import com.etfmonitor.database.*
import com.etfmonitor.repository.AIAnalysisRepository
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
 * - 시장 분석 및 신호 생성
 * - 백테스팅
 */
@Module
@InstallIn(SingletonComponent::class)
object AIModule {

    /**
     * API 키 제공자 (SharedPreferences 기반)
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
     * AI 분석 Repository
     * 시장 분석 및 신호 생성의 핵심 로직
     */
    @Provides
    @Singleton
    fun provideAIAnalysisRepository(
        claudeApiClient: ClaudeApiClient,
        marketIndexDao: MarketIndexDao,
        dailyEtfStatisticsDao: DailyEtfStatisticsDao,
        fearGreedDao: FearGreedDao,
        marketOscillatorDao: MarketOscillatorDao,
        marketDepositDao: MarketDepositDao
    ): AIAnalysisRepository {
        return AIAnalysisRepository(
            claudeApiClient,
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
}
