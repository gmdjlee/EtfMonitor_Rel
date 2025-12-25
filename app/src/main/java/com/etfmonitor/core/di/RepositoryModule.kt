package com.etfmonitor.core.di

import com.etfmonitor.core.database.*
import com.etfmonitor.repository.StatisticsAnalysisRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 모듈: Core Repository 제공
 *
 * Note: Market repositories (FearGreed, MarketDeposit, MarketOscillator, MarketIndex)
 * are provided by MarketModule via @Binds pattern.
 *
 * Note: DataRepository has been migrated to EtfRepository (see EtfModule).
 *
 * 최적화 포인트:
 * - 모든 Repository를 Singleton으로 관리
 * - DAO와 Python Client 의존성을 명시적으로 주입
 * - 수동 싱글톤 패턴 제거로 메모리 누수 위험 감소
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    // PyKrxClient는 @Inject constructor를 사용하므로 수동 제공 불필요
    // Hilt가 자동으로 의존성을 주입합니다

    // DataRepository는 EtfRepository로 마이그레이션 완료 (EtfModule 참조)

    // StockRepository는 @Inject constructor를 사용하므로 수동 제공 불필요
    // StockAnalysisRepository는 @Inject constructor를 사용하므로 수동 제공 불필요
    // Hilt가 자동으로 의존성을 주입합니다

    /**
     * StatisticsAnalysisRepository 제공 (Singleton)
     * ETF 통계 분석 및 상관관계 계산
     */
    @Provides
    @Singleton
    fun provideStatisticsAnalysisRepository(
        etfDao: EtfDao,
        marketIndexDao: MarketIndexDao,
        dailyEtfStatisticsDao: DailyEtfStatisticsDao
    ): StatisticsAnalysisRepository {
        return StatisticsAnalysisRepository(etfDao, marketIndexDao, dailyEtfStatisticsDao)
    }
}
