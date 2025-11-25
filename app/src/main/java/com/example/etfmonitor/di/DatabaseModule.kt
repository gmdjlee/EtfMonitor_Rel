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
                MIGRATION_9_10
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
}
