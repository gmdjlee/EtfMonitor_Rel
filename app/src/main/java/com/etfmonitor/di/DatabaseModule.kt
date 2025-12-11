package com.etfmonitor.di

import android.content.Context
import androidx.room.Room
import com.etfmonitor.database.*
import com.etfmonitor.database.entities.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 모듈: Room Database와 DAO 제공
 *
 * 최적화 포인트:
 * - @Singleton으로 Database 인스턴스 단일화
 * - Lazy initialization 제거하고 Hilt가 생명주기 관리
 * - 모든 DAO를 명시적으로 제공하여 의존성 명확화
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * AppDatabase 제공 (Singleton)
     *
     * Production 최적화:
     * - 모든 마이그레이션을 명시적으로 추가하여 데이터 무결성 보장
     * - exportSchema는 버전 관리를 위해 false 유지
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "etf_monitor.db"
        )
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16
            )
            .build()
    }

    /**
     * ETF DAO 제공
     * ETF 목록, 보유종목, 설정 등을 관리하는 DAO
     */
    @Provides
    @Singleton
    fun provideEtfDao(database: AppDatabase): EtfDao {
        return database.dao()
    }

    /**
     * Stock DAO 제공
     * 종목 정보를 관리하는 DAO
     */
    @Provides
    @Singleton
    fun provideStockDao(database: AppDatabase): StockDao {
        return database.stockDao()
    }

    /**
     * Market Deposit DAO 제공
     * 시장 예탁금 데이터를 관리하는 DAO
     */
    @Provides
    @Singleton
    fun provideMarketDepositDao(database: AppDatabase): MarketDepositDao {
        return database.marketDepositDao()
    }

    /**
     * Stock Analysis DAO 제공
     * 종목 분석 데이터를 관리하는 DAO
     */
    @Provides
    @Singleton
    fun provideStockAnalysisDao(database: AppDatabase): StockAnalysisDao {
        return database.stockAnalysisDao()
    }

    /**
     * Search History DAO 제공
     * 검색 기록을 관리하는 DAO
     */
    @Provides
    @Singleton
    fun provideSearchHistoryDao(database: AppDatabase): SearchHistoryDao {
        return database.searchHistoryDao()
    }

    /**
     * Fear & Greed DAO 제공
     * 공포 탐욕 지수 데이터를 관리하는 DAO
     */
    @Provides
    @Singleton
    fun provideFearGreedDao(database: AppDatabase): FearGreedDao {
        return database.fearGreedDao()
    }

    /**
     * Market Oscillator DAO 제공
     * 과매수/과매도 데이터를 관리하는 DAO
     */
    @Provides
    @Singleton
    fun provideMarketOscillatorDao(database: AppDatabase): MarketOscillatorDao {
        return database.marketOscillatorDao()
    }

    /**
     * Market Index DAO 제공
     * 시장 지수 데이터를 관리하는 DAO
     */
    @Provides
    @Singleton
    fun provideMarketIndexDao(database: AppDatabase): MarketIndexDao {
        return database.marketIndexDao()
    }

    /**
     * Daily ETF Statistics DAO 제공
     * 일별 ETF 통계 데이터를 관리하는 DAO
     */
    @Provides
    @Singleton
    fun provideDailyEtfStatisticsDao(database: AppDatabase): DailyEtfStatisticsDao {
        return database.dailyEtfStatisticsDao()
    }

    /**
     * Correlation Analysis DAO 제공
     * 상관관계 분석 결과를 관리하는 DAO
     */
    @Provides
    @Singleton
    fun provideCorrelationAnalysisDao(database: AppDatabase): CorrelationAnalysisDao {
        return database.correlationAnalysisDao()
    }

    /**
     * AI Analysis DAO 제공
     * AI 분석 결과를 관리하는 DAO
     */
    @Provides
    @Singleton
    fun provideAIAnalysisDao(database: AppDatabase): AIAnalysisDao {
        return database.aiAnalysisDao()
    }

    /**
     * AI Chat DAO 제공
     * AI 채팅 세션 및 메시지를 관리하는 DAO
     */
    @Provides
    @Singleton
    fun provideAIChatDao(database: AppDatabase): AIChatDao {
        return database.aiChatDao()
    }

    /**
     * Stock Prediction DAO 제공
     * ML 모델 기반 주가 예측 결과를 관리하는 DAO
     */
    @Provides
    @Singleton
    fun provideStockPredictionDao(database: AppDatabase): StockPredictionDao {
        return database.stockPredictionDao()
    }

    /**
     * Sector Analysis DAO 제공
     * 섹터별 Fear & Greed 분석 결과를 관리하는 DAO
     */
    @Provides
    @Singleton
    fun provideSectorAnalysisDao(database: AppDatabase): SectorAnalysisDao {
        return database.sectorAnalysisDao()
    }

    /**
     * ETF Correlation DAO 제공
     * ETF 간 상관관계 캐시 데이터를 관리하는 DAO
     */
    @Provides
    @Singleton
    fun provideEtfCorrelationDao(database: AppDatabase): EtfCorrelationDao {
        return database.etfCorrelationDao()
    }

    /**
     * Liquidity Analysis DAO 제공
     * 시장 유동성 분석 결과를 관리하는 DAO
     */
    @Provides
    @Singleton
    fun provideLiquidityAnalysisDao(database: AppDatabase): LiquidityAnalysisDao {
        return database.liquidityAnalysisDao()
    }

    /**
     * Price Cache DAO 제공
     * ML 예측용 가격 캐시 데이터를 관리하는 DAO
     */
    @Provides
    @Singleton
    fun providePriceCacheDao(database: AppDatabase): PriceCacheDao {
        return database.priceCacheDao()
    }

    /**
     * Enhanced Prediction DAO 제공
     * 28개 Feature 기반 향상된 예측 결과를 관리하는 DAO
     */
    @Provides
    @Singleton
    fun provideEnhancedPredictionDao(database: AppDatabase): EnhancedPredictionDao {
        return database.enhancedPredictionDao()
    }

    /**
     * Stock Indicator AI Result DAO 제공
     * 종목-지표 상관관계 AI 분석 결과를 관리하는 DAO
     */
    @Provides
    @Singleton
    fun provideStockIndicatorAIResultDao(database: AppDatabase): StockIndicatorAIResultDao {
        return database.stockIndicatorAIResultDao()
    }
}
